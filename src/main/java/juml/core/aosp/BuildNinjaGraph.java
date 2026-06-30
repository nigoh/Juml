// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code build.ninja} の解析結果。
 *
 * <ul>
 *   <li>{@link #getRules()}: rule 別の使用統計 (どの rule が何件の build 文で使われたか)。</li>
 *   <li>{@link #getGroupEdges()}: ターゲットの出力/入力パスを「モジュール/ディレクトリ」
 *       単位 ({@code .intermediates/<a>/<b>} 等) に畳み込んだ依存エッジと件数。
 *       巨大な build.ninja でも図が破綻しないよう集約した粒度で保持する。</li>
 * </ul>
 *
 * <p>{@link #isTruncated()} が true の場合、読み込み上限に達して途中で打ち切られている
 * (統計は概況値として扱う)。</p>
 */
public final class BuildNinjaGraph {

    /** name → rule。宣言順を保つため LinkedHashMap。 */
    private final Map<String, BuildNinjaRule> rules = new LinkedHashMap<>();

    /** "from-group -> to-group" → 件数。集約済みの依存エッジ。 */
    private final Map<String, Integer> groupEdges = new LinkedHashMap<>();

    private long buildStatements;
    private long outputTargets;
    private boolean truncated;

    public Map<String, BuildNinjaRule> getRules() { return rules; }
    public Map<String, Integer> getGroupEdges() { return groupEdges; }
    public long getBuildStatements() { return buildStatements; }
    public long getOutputTargets() { return outputTargets; }
    public boolean isTruncated() { return truncated; }

    BuildNinjaRule ruleFor(String name) {
        return rules.computeIfAbsent(name, BuildNinjaRule::new);
    }

    void addBuildStatement(long outputs) {
        buildStatements++;
        outputTargets += outputs;
    }

    void addGroupEdge(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        groupEdges.merge(from + " -> " + to, 1, Integer::sum);
    }

    void markTruncated() { this.truncated = true; }
}
