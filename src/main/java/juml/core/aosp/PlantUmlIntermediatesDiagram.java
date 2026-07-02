// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link IntermediatesInventory} を PlantUML コンポーネント図に整形する。
 *
 * <p>モジュールを成果物カテゴリ ({@code apk/java/native/aidl/other}) ごとの package に並べ、
 * 各コンポーネントにバリアント数と総ファイル数を併記する。モジュール数が多い場合は
 * ファイル数の多い上位 {@value #MODULE_LIMIT} 件のみ描画する。</p>
 */
public final class PlantUmlIntermediatesDiagram {

    private static final int MODULE_LIMIT = 80;

    private PlantUmlIntermediatesDiagram() {
    }

    public static String render(IntermediatesInventory inv) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Soong .intermediates Inventory\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");

        List<IntermediateModule> modules = inv == null
                ? new ArrayList<>() : inv.getModules();
        if (modules.isEmpty()) {
            sb.append("note as N\nNo .intermediates artifacts were found.\nend note\n");
            sb.append("@enduml\n");
            return sb.toString();
        }

        // ファイル数降順で上位 MODULE_LIMIT に絞る
        List<IntermediateModule> sorted = new ArrayList<>(modules);
        sorted.sort(Comparator.comparingLong(IntermediateModule::getTotalFiles).reversed()
                .thenComparing(IntermediateModule::getName));
        int shownCount = Math.min(sorted.size(), MODULE_LIMIT);
        List<IntermediateModule> shown = sorted.subList(0, shownCount);

        // カテゴリ別 package
        Map<String, List<IntermediateModule>> byCategory = new LinkedHashMap<>();
        for (IntermediateModule m : shown) {
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        }
        for (Map.Entry<String, List<IntermediateModule>> e : byCategory.entrySet()) {
            sb.append("package \"").append(e.getKey()).append("\" ")
                    .append(colorFor(e.getKey())).append(" {\n");
            for (IntermediateModule m : e.getValue()) {
                sb.append("  component \"").append(escape(m.getName()))
                        .append("\\n").append(m.getVariants().size()).append(" variant(s), ")
                        .append(m.getTotalFiles()).append(" file(s)\" as ")
                        .append(alias(m)).append('\n');
            }
            sb.append("}\n");
        }

        sb.append("note as Summary\n");
        sb.append("modules: ").append(modules.size()).append("\\n");
        sb.append("artifacts: ").append(inv.getTotalFiles()).append("\\n");
        sb.append("shown: ").append(shownCount).append(" / ").append(modules.size());
        if (inv.isTruncated()) {
            sb.append("\\n(scan truncated)");
        }
        sb.append("\nend note\n");
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static String colorFor(String category) {
        switch (category) {
            case "apk": return "#D5F5D0";
            case "java": return "#FFE8C8";
            case "native": return "#D7F0FF";
            case "aidl": return "#FFD5E8";
            default: return "#EEEEEE";
        }
    }

    private static String alias(IntermediateModule m) {
        String base = m.getModulePath() + "_" + m.getName();
        StringBuilder sb = new StringBuilder("im_");
        boolean replaced = false;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append('_');
                replaced |= c != '_';
            }
        }
        // "a/b"+"c" と "a"+"b_c" が同じ "im_a_b_c" に潰れて別モジュールが合成されないよう、
        // 置換が起きた名前には元名のハッシュを付けて一意化する。
        if (replaced) {
            sb.append('_').append(Integer.toHexString(base.hashCode() & 0xfffff));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
