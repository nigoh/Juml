// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link IntermediatesInventory} を Markdown レポートに整形する。
 *
 * <p>章構成: 概況 → 成果物種別別の合計 → モジュール×バリアント×種別の在庫表
 * (カテゴリ別)。</p>
 */
public final class MarkdownIntermediatesReport {

    private MarkdownIntermediatesReport() {
    }

    public static String render(IntermediatesInventory inv) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Soong .intermediates Inventory\n\n");
        List<IntermediateModule> modules = inv == null
                ? new ArrayList<>() : inv.getModules();
        if (modules.isEmpty()) {
            sb.append("(no .intermediates artifacts found)\n");
            return sb.toString();
        }

        sb.append("- Modules: ").append(modules.size()).append('\n');
        sb.append("- Total artifacts: ").append(inv.getTotalFiles()).append('\n');
        sb.append("- Total size: ").append(humanBytes(inv.getTotalBytes())).append('\n');
        if (inv.isTruncated()) {
            sb.append("\n> **Note**: scan was truncated at the file limit;"
                    + " figures are approximate.\n");
        }
        sb.append('\n');

        // 成果物種別別の合計
        Map<String, Integer> kindTotals = new TreeMap<>();
        Map<String, List<IntermediateModule>> byCategory = new java.util.LinkedHashMap<>();
        for (IntermediateModule m : modules) {
            for (Map.Entry<String, Integer> e : m.getKindCounts().entrySet()) {
                kindTotals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        }
        sb.append("## Artifact kinds\n\n");
        sb.append("| Kind | Count |\n|---|---|\n");
        List<Map.Entry<String, Integer>> kinds = new ArrayList<>(kindTotals.entrySet());
        kinds.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Integer> e : kinds) {
            sb.append("| `").append(e.getKey()).append("` | ")
                    .append(e.getValue()).append(" |\n");
        }
        sb.append('\n');

        // カテゴリ別モジュール在庫
        for (Map.Entry<String, List<IntermediateModule>> e : byCategory.entrySet()) {
            sb.append("## ").append(e.getKey()).append(" modules\n\n");
            sb.append("| Module | Path | Variants | Artifacts | Size | Kinds |\n");
            sb.append("|---|---|---|---|---|---|\n");
            List<IntermediateModule> sorted = new ArrayList<>(e.getValue());
            sorted.sort(Comparator.comparing(IntermediateModule::getName));
            for (IntermediateModule m : sorted) {
                sb.append("| `").append(m.getName()).append("` | `")
                        .append(m.getModulePath()).append("` | ")
                        .append(m.getVariants().size()).append(" | ")
                        .append(m.getTotalFiles()).append(" | ")
                        .append(humanBytes(m.getTotalBytes())).append(" | ")
                        .append(kindsSummary(m.getKindCounts())).append(" |\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String kindsSummary(Map<String, Integer> kinds) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(kinds.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(list.size(), 5);
        for (int i = 0; i < limit; i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(list.get(i).getKey()).append('×').append(list.get(i).getValue());
        }
        if (list.size() > limit) {
            sb.append(", …");
        }
        return sb.toString();
    }

    static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes / 1024.0;
        int u = 0;
        while (v >= 1024 && u < units.length - 1) {
            v /= 1024.0;
            u++;
        }
        return String.format("%.1f %s", v, units[u]);
    }
}
