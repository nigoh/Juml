// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code module-info.java} の解析結果 ({@link JavaClassInfo.Kind#MODULE}) から
 * PlantUML 形式のモジュール依存グラフを生成する。
 *
 * <p>{@code requires} ディレクティブをモジュール間の矢印として描画する。
 * {@code requires transitive} は二重矢印 ({@code ==>}) で区別する。
 * {@code exports} / {@code opens} はノードの note に列挙し、
 * {@code uses} / {@code provides} は凡例で説明する。</p>
 */
public final class PlantUmlModuleDiagram {

    /** 出力オプション。 */
    public static class Options {
        /** タイトル文字列 (null で省略)。 */
        public String title;
        /** 凡例ブロックをダイアグラムに追加する。 */
        public boolean includeLegend = true;
        /** exports / opens ディレクティブを各モジュールノードの note に表示する。 */
        public boolean showExportsOpens = true;
    }

    private PlantUmlModuleDiagram() {
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

        // Kind.MODULE のみ抽出
        List<JavaClassInfo> modules = new ArrayList<>();
        for (JavaClassInfo c : classes) {
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                modules.add(c);
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        out.append("top to bottom direction\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        }

        if (modules.isEmpty()) {
            out.append("note as N1\n  No module-info.java found\nend note\n");
            if (o.includeLegend) {
                emitLegend(out);
            }
            out.append("@enduml\n");
            return out.toString();
        }

        // モジュール名 → エイリアスのマッピング
        Map<String, String> aliasByName = new LinkedHashMap<>();
        int seq = 0;
        for (JavaClassInfo m : modules) {
            aliasByName.put(m.getSimpleName(), "M" + (seq++));
        }

        // コンポーネントノードを出力
        for (JavaClassInfo m : modules) {
            String alias = aliasByName.get(m.getSimpleName());
            boolean isOpen = m.getModifiers().contains("open");
            String label = isOpen ? m.getSimpleName() + "\\n<<open>>" : m.getSimpleName();
            out.append("component \"").append(escape(label)).append("\" as ").append(alias).append('\n');
        }

        // exports / opens を note で表示
        if (o.showExportsOpens) {
            for (JavaClassInfo m : modules) {
                String alias = aliasByName.get(m.getSimpleName());
                List<String> lines = new ArrayList<>();
                for (JavaModuleDirective d : m.getModuleDirectives()) {
                    if (d.getKind() == JavaModuleDirective.Kind.EXPORTS
                            || d.getKind() == JavaModuleDirective.Kind.OPENS) {
                        lines.add(d.toString());
                    }
                }
                if (!lines.isEmpty()) {
                    out.append("note bottom of ").append(alias).append('\n');
                    for (String l : lines) {
                        out.append("  ").append(escape(l)).append('\n');
                    }
                    out.append("end note\n");
                }
            }
        }

        // requires 矢印を出力（重複排除）
        Set<String> emitted = new LinkedHashSet<>();
        for (JavaClassInfo m : modules) {
            String srcAlias = aliasByName.get(m.getSimpleName());
            for (JavaModuleDirective d : m.getModuleDirectives()) {
                if (d.getKind() != JavaModuleDirective.Kind.REQUIRES) {
                    continue;
                }
                String targetName = d.getName();
                String dstAlias = aliasByName.get(targetName);
                boolean isTransitive = d.getModifiers().contains("transitive");
                String arrow = isTransitive ? "==>" : "-->";
                String label = isTransitive ? "requires transitive" : "requires";
                // 既知モジュールへは alias、未知（外部）は引用符付き名
                String dst = dstAlias != null ? dstAlias : "\"" + escape(targetName) + "\"";
                String key = srcAlias + arrow + dst;
                if (emitted.add(key)) {
                    out.append(srcAlias).append(' ').append(arrow).append(' ').append(dst)
                            .append(" : ").append(label).append('\n');
                }
            }
        }

        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== モジュール依存グラフ ==\n");
        out.append("component    module-info.java で宣言されたモジュール\n");
        out.append("A --> B      A requires B (通常依存)\n");
        out.append("A ==> B      A requires transitive B (推移的依存)\n");
        out.append("note         exports / opens ディレクティブ\n");
        out.append("<<open>>     open module 宣言 (全パッケージをリフレクション公開)\n");
        out.append("endlegend\n");
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
