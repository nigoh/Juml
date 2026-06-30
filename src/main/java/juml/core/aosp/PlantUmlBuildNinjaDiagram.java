// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link BuildNinjaGraph} の集約済み依存エッジを PlantUML コンポーネント図に整形する。
 *
 * <p>ノードは「モジュール/ディレクトリ」グループ ({@code .intermediates/<a>/<b>} 等)。
 * 巨大な build.ninja でも破綻しないよう、出現頻度 (degree) の高い上位 {@value #NODE_LIMIT}
 * グループのみ描画する。{@link PlantUmlSoongDependencyDiagram} と同じく edge は重複排除し
 * 件数を {@code : xN} で付記する。</p>
 */
public final class PlantUmlBuildNinjaDiagram {

    private static final int NODE_LIMIT = 60;

    private PlantUmlBuildNinjaDiagram() {
    }

    public static String render(BuildNinjaGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title build.ninja Target Dependencies (aggregated by module)\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");

        if (graph == null || graph.getGroupEdges().isEmpty()) {
            sb.append("note as N\nNo build.ninja dependency edges were found.\nend note\n");
            sb.append("@enduml\n");
            return sb.toString();
        }

        // エッジを件数降順に並べ、登場グループの degree を数える
        List<Map.Entry<String, Integer>> edges =
                new ArrayList<>(graph.getGroupEdges().entrySet());
        edges.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            if (c != 0) return c;
            return a.getKey().compareTo(b.getKey());
        });

        java.util.Map<String, Integer> degree = new java.util.HashMap<>();
        for (Map.Entry<String, Integer> e : edges) {
            String[] ft = splitEdge(e.getKey());
            degree.merge(ft[0], e.getValue(), Integer::sum);
            degree.merge(ft[1], e.getValue(), Integer::sum);
        }
        // degree 上位 NODE_LIMIT グループだけ残す
        List<String> topGroups = new ArrayList<>(degree.keySet());
        topGroups.sort(Comparator.<String>comparingInt(g -> degree.getOrDefault(g, 0))
                .reversed().thenComparing(Comparator.naturalOrder()));
        Set<String> kept = new LinkedHashSet<>();
        for (int i = 0; i < topGroups.size() && kept.size() < NODE_LIMIT; i++) {
            kept.add(topGroups.get(i));
        }

        // ノード宣言
        for (String g : kept) {
            sb.append("component \"").append(escape(g)).append("\" as ")
                    .append(alias(g)).append('\n');
        }

        // エッジ (両端が kept のものだけ)
        int shown = 0;
        for (Map.Entry<String, Integer> e : edges) {
            String[] ft = splitEdge(e.getKey());
            if (!kept.contains(ft[0]) || !kept.contains(ft[1])) {
                continue;
            }
            sb.append(alias(ft[0])).append(" --> ").append(alias(ft[1]));
            if (e.getValue() > 1) {
                sb.append(" : x").append(e.getValue());
            }
            sb.append('\n');
            shown++;
        }

        sb.append("note as Summary\n");
        sb.append("build statements: ").append(graph.getBuildStatements()).append("\\n");
        sb.append("rules: ").append(graph.getRules().size()).append("\\n");
        sb.append("groups shown: ").append(kept.size())
                .append(" / ").append(degree.size());
        if (graph.isTruncated()) {
            sb.append("\\n(input truncated)");
        }
        sb.append("\nend note\n");
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static String[] splitEdge(String key) {
        int arrow = key.indexOf(" -> ");
        if (arrow < 0) {
            return new String[]{key, ""};
        }
        return new String[]{key.substring(0, arrow), key.substring(arrow + 4)};
    }

    private static String alias(String name) {
        StringBuilder sb = new StringBuilder("g_");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
