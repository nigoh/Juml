// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ScreenTransition} のエッジ集合から「画面遷移ルート」(起点からの多段パス) を列挙する。
 *
 * <p>起点 = 入ってくるエッジが無いノード (ランチャー Activity / トップ Screen 等)。
 * 起点が無い (全ノードが循環で入次数を持つ) 場合は全ノードを起点候補にする。
 * 各起点から深さ優先でパスを辿り、同一ルート内でのノード再訪を禁止して循環を防ぐ。
 * 出力爆発を避けるため深さ・本数に上限を設ける。</p>
 */
public final class ScreenRouteBuilder {

    /** 1 ルートあたりの最大ホップ数。 */
    private static final int MAX_DEPTH = 15;
    /** 列挙するルートの最大本数。 */
    private static final int MAX_ROUTES = 300;

    private ScreenRouteBuilder() {
    }

    /** 遷移エッジから到達ルート (ノード単純名の並び) を列挙する。 */
    public static List<List<String>> routes(List<ScreenTransition> transitions) {
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        Set<String> nodes = new LinkedHashSet<>();
        Set<String> hasIncoming = new LinkedHashSet<>();
        for (ScreenTransition t : transitions) {
            String from = t.getFromSimpleName();
            String to = t.getTargetSimpleName();
            if (from.isEmpty() || to.isEmpty()) {
                continue;
            }
            nodes.add(from);
            nodes.add(to);
            if (!from.equals(to)) {
                adj.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
                hasIncoming.add(to);
            }
        }
        List<String> entries = new ArrayList<>();
        for (String n : nodes) {
            if (!hasIncoming.contains(n)) {
                entries.add(n);
            }
        }
        if (entries.isEmpty()) {
            entries.addAll(nodes);
        }
        List<List<String>> out = new ArrayList<>();
        for (String entry : entries) {
            if (out.size() >= MAX_ROUTES) {
                break;
            }
            Deque<String> path = new ArrayDeque<>();
            Set<String> onPath = new LinkedHashSet<>();
            dfs(entry, adj, path, onPath, out);
        }
        return out;
    }

    private static void dfs(String node, Map<String, Set<String>> adj,
                            Deque<String> path, Set<String> onPath,
                            List<List<String>> out) {
        path.addLast(node);
        onPath.add(node);
        Set<String> next = adj.get(node);
        List<String> targets = new ArrayList<>();
        if (next != null) {
            for (String t : next) {
                if (!onPath.contains(t)) {
                    targets.add(t);
                }
            }
        }
        if (targets.isEmpty() || path.size() >= MAX_DEPTH) {
            if (path.size() >= 2) {
                out.add(new ArrayList<>(path));
            }
        } else {
            for (String t : targets) {
                if (out.size() >= MAX_ROUTES) {
                    break;
                }
                dfs(t, adj, path, onPath, out);
            }
        }
        path.removeLast();
        onPath.remove(node);
    }
}
