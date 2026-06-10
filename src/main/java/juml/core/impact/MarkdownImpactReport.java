// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.impact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ImpactGraph} を Markdown レポート文字列に整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー: 起点シンボル、直接参照元数、推移閉包数</li>
 *   <li>直接参照元 (layer 1) 一覧</li>
 *   <li>推移参照元 (layer 2+) 一覧</li>
 *   <li>参照エッジ詳細 (caller / kind / 場所)</li>
 * </ol>
 */
public final class MarkdownImpactReport {

    private MarkdownImpactReport() {
    }

    public static String render(ImpactGraph graph) {
        if (graph == null) {
            return "# Impact Report\n\n(no data)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Impact Report\n\n");
        sb.append("- Target: `").append(graph.getTarget()).append("`\n");
        sb.append("- Direct callers: ").append(graph.directCallerCount()).append('\n');
        sb.append("- Transitive callers: ")
                .append(graph.transitiveCallerCount()).append('\n');

        List<ImpactGraph.Node> nodes = new ArrayList<>(graph.nodes());
        // 層 → スコア降順 → FQN: 各層の中で「先に確認すべき呼び出し元」が上に来る
        Collections.sort(nodes, new Comparator<ImpactGraph.Node>() {
            @Override
            public int compare(ImpactGraph.Node a, ImpactGraph.Node b) {
                int c = Integer.compare(a.getLayer(), b.getLayer());
                if (c != 0) return c;
                c = Double.compare(b.getScore(), a.getScore());
                if (c != 0) return c;
                return a.getId().compareTo(b.getId());
            }
        });

        // 層ごとに集計
        Map<Integer, List<ImpactGraph.Node>> byLayer = new LinkedHashMap<>();
        for (ImpactGraph.Node n : nodes) {
            byLayer.computeIfAbsent(n.getLayer(), k -> new ArrayList<>()).add(n);
        }

        for (Map.Entry<Integer, List<ImpactGraph.Node>> e : byLayer.entrySet()) {
            int layer = e.getKey();
            if (layer == 0) {
                continue;
            }
            sb.append("\n## Layer ").append(layer);
            if (layer == 1) {
                sb.append(" — Direct callers");
            } else {
                sb.append(" — Transitive callers");
            }
            sb.append("\n\n");
            sb.append("| Symbol | Score | Risk | Reason |\n");
            sb.append("|---|---|---|---|\n");
            for (ImpactGraph.Node n : e.getValue()) {
                sb.append("| `").append(n.getId()).append("` | ")
                        .append(String.format("%.2f", n.getScore())).append(" | ")
                        .append(n.getBreakageRisk()).append(" | ")
                        .append(n.getReason()).append(" |\n");
            }
        }

        sb.append("\n## Edges\n\n");
        if (graph.edges().isEmpty()) {
            sb.append("(no references found)\n");
        } else {
            sb.append("| Caller | Kind | At | File |\n");
            sb.append("|---|---|---|---|\n");
            for (ImpactGraph.Edge edge : graph.edges()) {
                String at = edge.getCallerMethod().isEmpty()
                        ? "(class header)" : "`" + edge.getCallerMethod() + "`";
                String file = edge.getFile().isEmpty() ? "" : "`" + edge.getFile() + "`";
                if (edge.getLineHint() > 0 && !edge.getFile().isEmpty()) {
                    file = file + ":" + edge.getLineHint();
                }
                sb.append("| `").append(edge.getFrom()).append("` → `")
                        .append(edge.getTo()).append("` | ")
                        .append(edge.getKind()).append(" | ")
                        .append(at).append(" | ").append(file).append(" |\n");
            }
        }
        return sb.toString();
    }
}
