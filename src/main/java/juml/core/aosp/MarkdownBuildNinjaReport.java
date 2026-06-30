// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * {@link BuildNinjaGraph} を Markdown レポートに整形する。
 *
 * <p>章構成: 概況 (build 文数 / 出力数 / rule 数) → rule 使用統計 (件数降順) →
 * 集約済み依存エッジ (件数降順, 上位のみ)。</p>
 */
public final class MarkdownBuildNinjaReport {

    private static final int EDGE_LIMIT = 80;

    private MarkdownBuildNinjaReport() {
    }

    public static String render(BuildNinjaGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("# build.ninja Build Graph Report\n\n");
        if (graph == null || graph.getBuildStatements() == 0) {
            sb.append("(no build.ninja statements found)\n");
            return sb.toString();
        }

        sb.append("- Build statements: ").append(graph.getBuildStatements()).append('\n');
        sb.append("- Output targets: ").append(graph.getOutputTargets()).append('\n');
        sb.append("- Rules: ").append(graph.getRules().size()).append('\n');
        sb.append("- Aggregated dependency edges: ")
                .append(graph.getGroupEdges().size()).append('\n');
        if (graph.isTruncated()) {
            sb.append("\n> **Note**: input was truncated at the read limit;"
                    + " figures are approximate.\n");
        }
        sb.append('\n');

        // rule 使用統計
        sb.append("## Rule usage (most-used)\n\n");
        sb.append("| Rule | Build count | Description |\n");
        sb.append("|---|---|---|\n");
        List<BuildNinjaRule> rules = new ArrayList<>(graph.getRules().values());
        rules.sort(Comparator.comparingInt(BuildNinjaRule::getBuildCount).reversed()
                .thenComparing(BuildNinjaRule::getName));
        for (BuildNinjaRule r : rules) {
            sb.append("| `").append(r.getName()).append("` | ")
                    .append(r.getBuildCount()).append(" | ")
                    .append(escapeCell(r.getDescription())).append(" |\n");
        }
        sb.append('\n');

        // 集約依存エッジ
        sb.append("## Aggregated dependency edges (most-frequent)\n\n");
        sb.append("| From (output group) | To (input group) | Count |\n");
        sb.append("|---|---|---|\n");
        List<Map.Entry<String, Integer>> edges =
                new ArrayList<>(graph.getGroupEdges().entrySet());
        edges.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            return a.getKey().compareTo(b.getKey());
        });
        int limit = Math.min(edges.size(), EDGE_LIMIT);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = edges.get(i);
            int arrow = e.getKey().indexOf(" -> ");
            String from = arrow >= 0 ? e.getKey().substring(0, arrow) : e.getKey();
            String to = arrow >= 0 ? e.getKey().substring(arrow + 4) : "";
            sb.append("| `").append(from).append("` | `").append(to).append("` | ")
                    .append(e.getValue()).append(" |\n");
        }
        if (edges.size() > limit) {
            sb.append("\n_(+").append(edges.size() - limit).append(" more edges)_\n");
        }
        return sb.toString();
    }

    private static String escapeCell(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("|", "\\|");
    }
}
