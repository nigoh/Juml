// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * クラス情報 ({@link JavaClassInfo}) のリストから PlantUML 形式の
 * パッケージ図テキストを生成する。
 *
 * <p>各パッケージを単一のノードとして表示し、パッケージに含まれるクラス数と、
 * 継承 / 実装 / フィールド型を経由した「パッケージ間の参照関係」を矢印で示す。
 * クラス図 ({@link PlantUmlClassDiagram}) と異なりクラス単位の中身は表示せず、
 * 大規模プロジェクトでもパッケージ依存の全体像を俯瞰しやすくする。</p>
 */
public final class PlantUmlPackageDiagram {

    /** 出力オプション。 */
    public static class Options {
        /** タイトル文字列 (null で省略)。 */
        public String title;
        /** 凡例ブロックをダイアグラムに追加する。 */
        public boolean includeLegend = true;
        /** パッケージ間の自己参照を抑止する (既定で true)。 */
        public boolean suppressSelfLoop = true;
    }

    private PlantUmlPackageDiagram() {
    }

    /** デフォルト Options で生成。 */
    public static String generate(List<JavaClassInfo> classes) {
        return generate(classes, null);
    }

    /** オプション付き生成。 */
    public static String generate(List<JavaClassInfo> classes, Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        Options o = opts != null ? opts : new Options();
        Context ctx = buildContext(classes, o);
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        }

        // パッケージノード (rectangle) を出力
        for (Map.Entry<String, Integer> e : ctx.classCount.entrySet()) {
            String pkg = e.getKey();
            String alias = ctx.aliasByPkg.get(pkg);
            String label = pkg.isEmpty() ? "(default)" : pkg;
            int n = e.getValue();
            out.append("package \"").append(escape(label))
                    .append("\\n").append(n)
                    .append(n == 1 ? " class" : " classes")
                    .append("\" as ").append(alias)
                    .append(" {\n}\n");
        }

        // パッケージ間の依存関係を集計する。
        // - 継承 (extends), 実装 (implements)
        // - フィールド型 (ジェネリクスの引数も含めて pickUsageTarget で 1 段だけ取り出す)
        // から、依存元パッケージ → 依存先パッケージのペアを LinkedHashSet で重複排除。
        // まず有向エッジ (srcAlias->dstAlias) を順序保持で収集してから出力する。
        // こうすることで逆向きペアの存在を判定でき、循環依存を双方向矢印 1 本にまとめられる。
        Set<String> edges = new LinkedHashSet<>();
        // フィールド型解決の接尾辞索引を 1 度だけ構築 (フィールドごとの全走査を避ける)。
        KnownTypeIndex knownIdx = new KnownTypeIndex(ctx.knownNames);
        for (JavaClassInfo c : classes) {
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                continue;
            }
            String srcPkg = ctx.pkgByQn.get(c.getQualifiedName());
            if (srcPkg == null) {
                continue;
            }
            addRef(edges, srcPkg, c.getSuperClass(), ctx);
            for (String iface : c.getInterfaces()) {
                addRef(edges, srcPkg, iface, ctx);
            }
            for (JavaFieldInfo f : c.getFields()) {
                String target = PlantUmlClassRelations.pickUsageTarget(
                        f.getType(), knownIdx);
                addRef(edges, srcPkg, target, ctx);
            }
        }
        emitDependencyEdges(out, edges);

        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /** クラス → パッケージ解決に必要な集計結果と表示時オプションを束ねた内部コンテキスト。 */
    private static final class Context {
        final Map<String, Integer> classCount = new LinkedHashMap<>();
        final Map<String, String> pkgByQn = new LinkedHashMap<>();
        final Map<String, String> pkgBySimple = new LinkedHashMap<>();
        final Map<String, String> aliasByPkg = new LinkedHashMap<>();
        final Set<String> knownNames = new HashSet<>();
        /**
         * 既知クラスの「単純名 (最後のドット以降)」集合。
         * {@code resolvePackage} で「その単純名を持つ既知クラスが存在するか」を
         * O(1) で判定するための索引 ({@code knownNames} 全走査の置き換え)。
         */
        final Set<String> knownSimpleNames = new HashSet<>();
        final Options options;

        Context(Options options) {
            this.options = options;
        }
    }

    /** クラス情報から集計を 1 パスで作成する。 */
    private static Context buildContext(List<JavaClassInfo> classes, Options o) {
        Context ctx = new Context(o);
        for (JavaClassInfo c : classes) {
            // module-info の宣言はパッケージ集計に含めない (パッケージを持たないため)。
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                continue;
            }
            String pkg = c.getPackageName() == null ? "" : c.getPackageName();
            ctx.classCount.merge(pkg, 1, Integer::sum);
            ctx.pkgByQn.put(c.getQualifiedName(), pkg);
            ctx.pkgBySimple.putIfAbsent(c.getSimpleName(), pkg);
            String qn = c.getQualifiedName();
            ctx.knownNames.add(qn);
            int qd = qn.lastIndexOf('.');
            ctx.knownSimpleNames.add(qd >= 0 ? qn.substring(qd + 1) : qn);
        }
        int aliasSeq = 0;
        for (String pkg : ctx.classCount.keySet()) {
            ctx.aliasByPkg.put(pkg, "P" + (aliasSeq++));
        }
        return ctx;
    }

    /**
     * 1 つの参照を有向エッジ {@code srcAlias->dstAlias} として {@code edges} に集める。
     * 既知のクラスでない参照 (外部ライブラリ等) はスキップする。重複は Set が排除する。
     */
    private static void addRef(Set<String> edges,
                                String srcPkg, String typeRef, Context ctx) {
        if (typeRef == null || typeRef.isEmpty()) {
            return;
        }
        String dstPkg = resolvePackage(typeRef, ctx);
        if (dstPkg == null) {
            return;
        }
        if (ctx.options.suppressSelfLoop && dstPkg.equals(srcPkg)) {
            return;
        }
        String srcAlias = ctx.aliasByPkg.get(srcPkg);
        String dstAlias = ctx.aliasByPkg.get(dstPkg);
        if (srcAlias == null || dstAlias == null) {
            return;
        }
        edges.add(srcAlias + "->" + dstAlias);
    }

    /**
     * 収集した有向エッジを出力する。逆向きエッジも存在するペア (循環依存) は
     * 双方向矢印 {@code <-->} 1 本にまとめ、視認性と重複描画を改善する。
     */
    private static void emitDependencyEdges(StringBuilder out, Set<String> edges) {
        Set<String> handled = new HashSet<>();
        for (String e : edges) {
            if (handled.contains(e)) {
                continue;
            }
            int sep = e.indexOf("->");
            String a = e.substring(0, sep);
            String b = e.substring(sep + 2);
            String reverse = b + "->" + a;
            if (!a.equals(b) && edges.contains(reverse)) {
                out.append(a).append(" <--> ").append(b).append('\n');
                handled.add(e);
                handled.add(reverse);
            } else {
                out.append(a).append(" --> ").append(b).append('\n');
                handled.add(e);
            }
        }
    }

    /**
     * 型参照を、既知のクラスを持つパッケージ名に解決する。解決できなければ null。
     * ジェネリック / 配列を取り除いてから、まず完全修飾名で、次に単純名で照合する。
     */
    private static String resolvePackage(String typeRef, Context ctx) {
        String base = stripDecorations(typeRef);
        if (base.isEmpty()) {
            return null;
        }
        // 完全修飾名で一致
        String pkg = ctx.pkgByQn.get(base);
        if (pkg != null) {
            return pkg;
        }
        // 単純名で一致
        int dot = base.lastIndexOf('.');
        String simple = dot >= 0 ? base.substring(dot + 1) : base;
        pkg = ctx.pkgBySimple.get(simple);
        // 既知クラスに同じ単純名が存在することを O(1) で確認する。
        // (旧実装の knownNames 全走査 anyMatch と同値: 最後のドット以降が simple と一致)
        if (pkg != null && ctx.knownSimpleNames.contains(simple)) {
            return pkg;
        }
        return null;
    }

    /** 配列 [] / ジェネリクス &lt;...&gt; を取り除いて、型のベース名のみを返す。 */
    private static String stripDecorations(String type) {
        String t = type.replaceAll("\\[\\]", "").trim();
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt).trim();
        }
        return t;
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private static void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== パッケージ依存グラフ ==\n");
        out.append("  各ボックスは Java パッケージを表す\n");
        out.append("  矢印はパッケージ間の参照を示す\n");
        out.append("  (継承 / 実装 / フィールド型)\n");
        out.append("endlegend\n");
    }
}
