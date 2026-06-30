// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * smali から復元したクラス構造を、クラスごとに人間が読める Markdown へ整形する。
 *
 * <p>{@link ApkSummaryReport} が統計中心なのに対し、こちらは「各クラスが何を継承し・
 * どんなフィールド/メソッドを持つか」を 1 クラス 1 ブロックで列挙する。生の smali を
 * 直接読むより構造を掴みやすく、grep もしやすい一覧を提供する。</p>
 */
public final class SmaliStructureReport {

    private SmaliStructureReport() {
    }

    /** 解析結果のクラス群を Markdown 構造一覧に変換する。 */
    public static String toMarkdown(ApkAnalysis a) {
        if (a == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        return toMarkdown(a.getClasses());
    }

    /** クラス一覧から Markdown 構造一覧を生成する (パッケージ順 → クラス名順)。 */
    public static String toMarkdown(List<SmaliClassInfo> classes) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# smali クラス構造\n\n");
        sb.append("クラス数: ").append(classes.size()).append("\n\n");

        Map<String, List<SmaliClassInfo>> byPkg = new TreeMap<>();
        for (SmaliClassInfo c : classes) {
            byPkg.computeIfAbsent(c.getPackageName(), k -> new ArrayList<>()).add(c);
        }
        for (Map.Entry<String, List<SmaliClassInfo>> e : byPkg.entrySet()) {
            String pkg = e.getKey().isEmpty() ? "(default)" : e.getKey();
            sb.append("## ").append(pkg).append("\n\n");
            List<SmaliClassInfo> list = e.getValue();
            list.sort((x, y) -> x.getSimpleName().compareTo(y.getSimpleName()));
            for (SmaliClassInfo c : list) {
                emitClass(sb, c);
            }
        }
        return sb.toString();
    }

    private static void emitClass(StringBuilder sb, SmaliClassInfo c) {
        sb.append("### ").append(c.umlKind()).append(' ')
                .append(c.getSimpleName()).append("\n\n");
        if (c.getSuperClass() != null && !c.getSuperClass().isEmpty()
                && !"java.lang.Object".equals(c.getSuperClass())) {
            sb.append("- extends `").append(c.getSuperClass()).append("`\n");
        }
        for (String iface : c.getInterfaces()) {
            sb.append("- implements `").append(iface).append("`\n");
        }
        if (c.getSourceFile() != null && !c.getSourceFile().isEmpty()) {
            sb.append("- source: `").append(c.getSourceFile()).append("`\n");
        }
        sb.append('\n');

        List<SmaliFieldInfo> fields = new ArrayList<>();
        for (SmaliFieldInfo f : c.getFields()) {
            if (!f.isSynthetic()) {
                fields.add(f);
            }
        }
        if (!fields.isEmpty()) {
            sb.append("fields:\n\n");
            sb.append("```\n");
            for (SmaliFieldInfo f : fields) {
                sb.append(f.visibilitySymbol()).append(' ');
                if (f.isStatic()) {
                    sb.append("static ");
                }
                if (f.isFinal()) {
                    sb.append("final ");
                }
                sb.append(f.getName()).append(": ").append(f.getType());
                if (f.getConstantValue() != null) {
                    sb.append(" = ").append(f.getConstantValue());
                }
                sb.append('\n');
            }
            sb.append("```\n\n");
        }

        List<SmaliMethodInfo> methods = new ArrayList<>();
        for (SmaliMethodInfo m : c.getMethods()) {
            if (!m.isSynthetic() && !m.isStaticInitializer()) {
                methods.add(m);
            }
        }
        if (!methods.isEmpty()) {
            sb.append("methods:\n\n");
            sb.append("```\n");
            for (SmaliMethodInfo m : methods) {
                sb.append(m.visibilitySymbol()).append(' ');
                if (m.isStatic()) {
                    sb.append("static ");
                }
                if (m.isAbstract()) {
                    sb.append("abstract ");
                }
                sb.append(m.displaySignature()).append('\n');
            }
            sb.append("```\n\n");
        }
    }
}
