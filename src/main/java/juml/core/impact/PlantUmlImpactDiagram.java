// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.impact;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ImpactGraph} を PlantUML 影響図 (依存風) に整形する。
 *
 * <p>ノードはコンポーネントとして表現し、層ごとに色分け:</p>
 * <ul>
 *   <li>layer 0 (target): 赤</li>
 *   <li>layer 1 (direct): オレンジ</li>
 *   <li>layer 2+ (transitive): 黄</li>
 * </ul>
 */
public final class PlantUmlImpactDiagram {

    private PlantUmlImpactDiagram() {
    }

    public static String render(ImpactGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");
        sb.append("title Impact: ").append(escape(graph.getTarget())).append('\n');

        Set<String> ids = new LinkedHashSet<>();
        for (ImpactGraph.Node node : graph.nodes()) {
            ids.add(node.getId());
            String alias = alias(node.getId());
            String color = colorFor(node.getLayer());
            sb.append("component \"").append(escape(node.getId())).append("\" as ")
                    .append(alias).append(' ').append(color).append('\n');
        }

        // edges
        List<ImpactGraph.Edge> edges = graph.edges();
        Set<String> emitted = new LinkedHashSet<>();
        for (ImpactGraph.Edge e : edges) {
            String key = e.getFrom() + "->" + e.getTo() + "/" + e.getKind();
            if (!emitted.add(key)) {
                continue;
            }
            if (!ids.contains(e.getFrom()) || !ids.contains(e.getTo())) {
                continue;
            }
            sb.append(alias(e.getFrom())).append(" --> ").append(alias(e.getTo()))
                    .append(" : ").append(e.getKind()).append('\n');
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static String colorFor(int layer) {
        switch (layer) {
            case 0: return "#FFAAAA";
            case 1: return "#FFD27A";
            default: return "#FFF2A8";
        }
    }

    /** PlantUML エイリアスとして安全な英数字に変換 (記号を _ に置換)。 */
    private static String alias(String id) {
        StringBuilder sb = new StringBuilder(id.length());
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return "n_" + sb;
    }

    /** PlantUML ラベル内で安全な文字列に変換。 */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
