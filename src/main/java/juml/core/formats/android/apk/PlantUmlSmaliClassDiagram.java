// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Apktool で逆コンパイルした {@code .smali} クラス群から PlantUML クラス図を生成する。
 *
 * <p>ソースコードが手元に無い APK でも、smali から取り出したクラス・継承・実装・
 * メンバ情報だけでクラス図を描けるのが狙い。クラスはパッケージごとにグループ化し、
 * スコープ内クラスへの {@code extends} / {@code implements} を矢印で結ぶ。直近の
 * スーパークラス/インタフェースがスコープ外 (フレームワーク等) の場合も、
 * {@code <<external>>} ノードとして文脈を残す (既定 ON、{@code java.lang.Object} は除外)。</p>
 */
public final class PlantUmlSmaliClassDiagram {

    /** 出力オプション。 */
    public static final class Options {
        public boolean includeFields = true;
        public boolean includeMethods = true;
        public boolean showExternalSupertypes = true;
        public boolean groupByPackage = true;
        public boolean includeLegend = true;
        /** 1 クラスあたりに描画するメンバ数の上限 (0 で無制限)。可読性のための間引き。 */
        public int maxMembersPerClass = 25;
        public String title;
    }

    private PlantUmlSmaliClassDiagram() {
    }

    /** デフォルトオプションで生成。 */
    public static String generate(List<SmaliClassInfo> classes) {
        return generate(classes, null);
    }

    /** {@link ApkAnalysis} のクラス一覧から生成。 */
    public static String generate(ApkAnalysis analysis, Options opts) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        return generate(analysis.getClasses(), opts);
    }

    /** オプション付き生成。 */
    public static String generate(List<SmaliClassInfo> classes, Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        Options o = opts != null ? opts : new Options();

        // スコープ内クラス FQN → エイリアス
        Map<String, String> alias = new LinkedHashMap<>();
        int seq = 0;
        for (SmaliClassInfo c : classes) {
            if (!alias.containsKey(c.getClassName())) {
                alias.put(c.getClassName(), "C" + (seq++));
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        }
        out.append("skinparam class {\n");
        out.append("  BackgroundColor<<external>> #F2F2F2\n");
        out.append("  BorderColor<<external>> #999999\n");
        out.append("}\n");
        out.append("set namespaceSeparator none\n");

        // 外部スーパータイプのノード (dedupe)。FQN → エイリアス
        Map<String, String> externalAlias = new LinkedHashMap<>();

        if (o.groupByPackage) {
            emitGroupedByPackage(out, classes, alias, o);
        } else {
            for (SmaliClassInfo c : classes) {
                emitClass(out, c, alias.get(c.getClassName()), o, "");
            }
        }

        // 関係 (継承・実装)。
        StringBuilder rel = new StringBuilder();
        for (SmaliClassInfo c : classes) {
            String childAlias = alias.get(c.getClassName());
            emitSupertype(rel, c.getSuperClass(), childAlias, true, alias, externalAlias, o);
            for (String iface : c.getInterfaces()) {
                emitSupertype(rel, iface, childAlias, false, alias, externalAlias, o);
            }
        }
        // 外部ノードの宣言を先に出す。
        for (Map.Entry<String, String> e : externalAlias.entrySet()) {
            out.append("class \"").append(escape(SmaliTypeDescriptor.simpleName(e.getKey())))
                    .append("\" as ").append(e.getValue()).append(" <<external>>\n");
        }
        out.append(rel);

        if (classes.isEmpty()) {
            out.append("note as N1\n  (no smali classes in scope)\nend note\n");
        }
        if (o.includeLegend) {
            emitLegend(out, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static void emitGroupedByPackage(StringBuilder out, List<SmaliClassInfo> classes,
                                             Map<String, String> alias, Options o) {
        Map<String, List<SmaliClassInfo>> byPkg = new TreeMap<>();
        for (SmaliClassInfo c : classes) {
            byPkg.computeIfAbsent(c.getPackageName(), k -> new ArrayList<>()).add(c);
        }
        for (Map.Entry<String, List<SmaliClassInfo>> e : byPkg.entrySet()) {
            String pkg = e.getKey().isEmpty() ? "(default)" : e.getKey();
            out.append("package \"").append(escape(pkg)).append("\" {\n");
            for (SmaliClassInfo c : e.getValue()) {
                emitClass(out, c, alias.get(c.getClassName()), o, "  ");
            }
            out.append("}\n");
        }
    }

    private static void emitClass(StringBuilder out, SmaliClassInfo c, String alias,
                                  Options o, String indent) {
        out.append(indent).append(c.umlKind()).append(" \"")
                .append(escape(c.getSimpleName())).append("\" as ").append(alias);
        if (c.isEnum()) {
            // umlKind が "enum" を返すので追加ステレオタイプは不要
            out.append(" ");
        }
        out.append(" {\n");
        int budget = o.maxMembersPerClass;
        int shown = 0;
        if (o.includeFields) {
            for (SmaliFieldInfo f : c.getFields()) {
                if (f.isSynthetic()) {
                    continue;
                }
                if (o.maxMembersPerClass > 0 && shown >= budget) {
                    break;
                }
                out.append(indent).append("  ").append(fieldLine(f)).append('\n');
                shown++;
            }
        }
        if (o.includeMethods) {
            for (SmaliMethodInfo m : c.getMethods()) {
                if (m.isSynthetic() || m.isStaticInitializer()) {
                    continue;
                }
                if (o.maxMembersPerClass > 0 && shown >= budget) {
                    out.append(indent).append("  .. more ..\n");
                    break;
                }
                out.append(indent).append("  ").append(methodLine(c, m)).append('\n');
                shown++;
            }
        }
        out.append(indent).append("}\n");
    }

    private static String fieldLine(SmaliFieldInfo f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.visibilitySymbol()).append(' ');
        if (f.isStatic()) {
            sb.append("{static} ");
        }
        sb.append(escape(f.getName())).append(": ")
                .append(escape(SmaliTypeDescriptor.simpleName(f.getType())));
        return sb.toString();
    }

    private static String methodLine(SmaliClassInfo c, SmaliMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.visibilitySymbol()).append(' ');
        if (m.isStatic()) {
            sb.append("{static} ");
        }
        if (m.isAbstract()) {
            sb.append("{abstract} ");
        }
        // <init> はクラスの単純名に置換してコンストラクタらしく見せる。
        if (m.isConstructor() && "<init>".equals(m.getName())) {
            StringBuilder params = new StringBuilder();
            for (int i = 0; i < m.getParameterTypes().size(); i++) {
                if (i > 0) {
                    params.append(", ");
                }
                params.append(SmaliTypeDescriptor.simpleName(m.getParameterTypes().get(i)));
            }
            sb.append(escape(c.getSimpleName())).append('(').append(escape(params.toString()))
                    .append(')');
        } else {
            sb.append(escape(m.displaySignature()));
        }
        return sb.toString();
    }

    private static void emitSupertype(StringBuilder rel, String superFqn, String childAlias,
                                      boolean isExtends, Map<String, String> alias,
                                      Map<String, String> externalAlias, Options o) {
        if (superFqn == null || superFqn.isEmpty() || childAlias == null) {
            return;
        }
        if ("java.lang.Object".equals(superFqn)) {
            return;
        }
        String arrow = isExtends ? " --|> " : " ..|> ";
        String targetAlias = alias.get(superFqn);
        if (targetAlias != null) {
            rel.append(childAlias).append(arrow).append(targetAlias).append('\n');
            return;
        }
        if (!o.showExternalSupertypes) {
            return;
        }
        String ext = externalAlias.get(superFqn);
        if (ext == null) {
            ext = "X" + externalAlias.size();
            externalAlias.put(superFqn, ext);
        }
        rel.append(childAlias).append(arrow).append(ext).append('\n');
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'");
    }

    private static void emitLegend(StringBuilder out, Options o) {
        out.append("legend top left\n");
        out.append("== APK (smali) クラス図 ==\n");
        out.append("class / interface / enum    smali から復元したクラス種別\n");
        out.append("A --|> B                     A extends B (継承)\n");
        out.append("A ..|> B                     A implements B (実装)\n");
        if (o.showExternalSupertypes) {
            out.append("<<external>>                 スコープ外 (framework 等) のスーパータイプ\n");
        }
        out.append("endlegend\n");
    }
}
