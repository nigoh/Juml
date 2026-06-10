// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.impact;

import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「シンボル X を消すと何が壊れるか」を {@link ReferenceIndex} 上の BFS で解析する。
 *
 * <p>起点シンボル (クラス FQN もしくは {@code FQN#method}) から逆方向に
 * 「呼び出し元」「継承元」「型参照元」を辿り、指定深さ {@code maxDepth} まで展開した
 * {@link ImpactGraph} を返す。</p>
 *
 * <p>影響度スコア (0.0〜1.0) は「参照種別の重み × 層減衰 × 多重参照ボーナス」。
 * 直接呼び出し (CALL) / 継承 (EXTENDS / IMPLEMENTS) は型参照や import より重く、
 * 浅い層・参照箇所が多い呼び出し元ほど高スコアになる。全件が同一スコアに
 * ならないため、修正時の確認優先順位付けにそのまま使える。</p>
 */
public final class ImpactAnalyzer {

    private final ReferenceIndex index;

    public ImpactAnalyzer(ReferenceIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index is null");
        }
        this.index = index;
    }

    /**
     * クラス FQN を起点に Impact Analysis を実行する。
     *
     * @param fqn クラスの完全修飾名
     * @param maxDepth BFS 深さ (1 以上推奨。0 なら起点ノードのみ)
     */
    public ImpactGraph analyzeClass(String fqn, int maxDepth) {
        ImpactGraph g = new ImpactGraph(fqn);
        if (fqn == null || fqn.isEmpty()) {
            return g;
        }
        List<ReferenceSite> direct = index.sitesForClass(fqn);
        runBfs(g, fqn, direct, maxDepth);
        return g;
    }

    /**
     * メソッド (FQN + 単純名) を起点に Impact Analysis を実行する。
     *
     * @param ownerFqn メソッドを宣言するクラスの FQN
     * @param methodName メソッド単純名 (引数なし)
     * @param maxDepth BFS 深さ
     */
    public ImpactGraph analyzeMethod(String ownerFqn, String methodName, int maxDepth) {
        String target = ownerFqn + "." + methodName;
        ImpactGraph g = new ImpactGraph(target);
        if (ownerFqn == null || methodName == null) {
            return g;
        }
        List<ReferenceSite> direct = index.sitesByMember(
                ReferenceKey.Kind.METHOD, ownerFqn, methodName);
        runBfs(g, target, direct, maxDepth);
        return g;
    }

    /** 直接参照リストから BFS で推移閉包を構築する。 */
    private void runBfs(ImpactGraph g, String targetLabel,
                         List<ReferenceSite> direct, int maxDepth) {
        // ノード: caller FQN (クラス単位で集約)
        // depth 0: target 自身
        Map<String, Integer> depthOf = new LinkedHashMap<>();
        depthOf.put(targetLabel, 0);
        g.addNode(targetLabel, 0, 1.0, "TARGET");

        Deque<String> frontier = new ArrayDeque<>();
        // depth 1: 直接参照元を caller 単位で集約してからノード化する
        // (同一 caller の複数参照は最強種別 + 件数ボーナスとして 1 ノードに畳む)
        Map<String, List<ReferenceSite>> byCaller = groupByCaller(direct, null);
        for (Map.Entry<String, List<ReferenceSite>> e : byCaller.entrySet()) {
            String caller = e.getKey();
            List<ReferenceSite> sites = e.getValue();
            if (!depthOf.containsKey(caller)) {
                depthOf.put(caller, 1);
                g.addNode(caller, 1, scoreFor(1, sites), reasonFor(sites));
                frontier.add(caller);
            }
            for (ReferenceSite site : sites) {
                g.addEdge(caller, targetLabel, site.getKind().name(),
                        site.getCallerMethod(), site.getFile(), site.getLineHint());
            }
        }
        // depth 2 以降 BFS
        while (!frontier.isEmpty()) {
            String current = frontier.poll();
            int currentDepth = depthOf.get(current);
            if (currentDepth >= maxDepth) {
                continue;
            }
            // current を参照しているノードを caller 単位で集約して辿る
            Map<String, List<ReferenceSite>> callers =
                    groupByCaller(index.sitesForClass(current), current);
            Set<String> seenAtThisStep = new LinkedHashSet<>();
            for (Map.Entry<String, List<ReferenceSite>> e : callers.entrySet()) {
                String caller = e.getKey();
                List<ReferenceSite> sites = e.getValue();
                if (!depthOf.containsKey(caller)) {
                    int nextDepth = currentDepth + 1;
                    depthOf.put(caller, nextDepth);
                    g.addNode(caller, nextDepth, scoreFor(nextDepth, sites),
                            reasonFor(sites));
                    frontier.add(caller);
                }
                for (ReferenceSite site : sites) {
                    if (seenAtThisStep.add(caller + "->" + current
                            + "/" + site.getKind())) {
                        g.addEdge(caller, current, site.getKind().name(),
                                site.getCallerMethod(), site.getFile(),
                                site.getLineHint());
                    }
                }
            }
        }
    }

    /** 参照サイトを caller FQN ごとに集約する ({@code skip} と空 caller は除外)。 */
    private static Map<String, List<ReferenceSite>> groupByCaller(
            List<ReferenceSite> sites, String skip) {
        Map<String, List<ReferenceSite>> byCaller = new LinkedHashMap<>();
        for (ReferenceSite site : sites) {
            String caller = site.getCallerFqn();
            if (caller == null || caller.isEmpty() || caller.equals(skip)) {
                continue;
            }
            byCaller.computeIfAbsent(caller, k -> new ArrayList<>()).add(site);
        }
        return byCaller;
    }

    /** 層ごとの減衰係数 (layer 1 → 1.0, 2 → 0.5, 3 → 0.25, ...)。 */
    private static double decayFor(int layer) {
        return Math.pow(0.5, layer - 1);
    }

    /**
     * 参照種別の重み。壊れ方が直接的なものほど高い:
     * 呼び出し/継承はシグネチャ変更で即コンパイルエラーになるが、
     * import や注釈参照は影響が間接的なことが多い。
     */
    private static double kindWeight(ReferenceSite.Kind kind) {
        switch (kind) {
            case CALL:
            case EXTENDS:
            case IMPLEMENTS:
                return 1.0;
            case TYPE_REFERENCE:
                return 0.7;
            case ANNOTATION:
                return 0.5;
            case IMPORT:
                return 0.4;
            default:
                return 0.6;
        }
    }

    /**
     * caller 1 件の影響度スコア:
     * {@code min(1.0, 最強参照種別の重み × 層減衰 × 件数ボーナス)}。
     * 件数ボーナスは参照箇所が 1 増えるごとに +5% (最大 +30%)。
     */
    private static double scoreFor(int layer, List<ReferenceSite> sites) {
        double maxWeight = 0.0;
        for (ReferenceSite site : sites) {
            maxWeight = Math.max(maxWeight, kindWeight(site.getKind()));
        }
        double bonus = 1.0 + 0.05 * Math.min(sites.size() - 1, 6);
        return Math.min(1.0, maxWeight * decayFor(layer) * bonus);
    }

    /** 最強の参照種別を代表ラベルにし、複数箇所なら件数を併記する。 */
    private static String reasonFor(List<ReferenceSite> sites) {
        ReferenceSite.Kind strongest = null;
        for (ReferenceSite site : sites) {
            if (strongest == null
                    || kindWeight(site.getKind()) > kindWeight(strongest)) {
                strongest = site.getKind();
            }
        }
        String label = strongest == null ? "" : kindLabel(strongest);
        return sites.size() > 1 ? label + " x" + sites.size() : label;
    }

    private static String kindLabel(ReferenceSite.Kind kind) {
        switch (kind) {
            case CALL: return "DIRECT_CALL";
            case EXTENDS: return "EXTENDS";
            case IMPLEMENTS: return "IMPLEMENTS";
            case TYPE_REFERENCE: return "TYPE_REFERENCE";
            case ANNOTATION: return "ANNOTATION";
            case IMPORT: return "IMPORT";
            default: return kind.name();
        }
    }
}
