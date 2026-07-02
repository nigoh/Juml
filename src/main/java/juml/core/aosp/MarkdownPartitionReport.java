// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@link AndroidBpModule} のリストを partition (配置) 別に集計した Markdown レポート。
 *
 * <p>{@link AndroidBpModule#getPartition()} (vendor / product_specific /
 * system_ext_specific / device_specific 等のスカラ属性から推定) を軸に、
 * partition ごとのモジュール数・種類内訳・主要モジュール (被依存数順) をまとめ、
 * 最後に partition を跨ぐ依存 (Treble 境界の確認対象) を列挙する。</p>
 */
public final class MarkdownPartitionReport {

    /** 代表的な partition の表示順。 */
    static final String[] PARTITION_ORDER = {
            "system", "system_ext", "product", "vendor", "odm", "recovery", "ramdisk"};

    /** partition ごとの主要モジュール表示件数。 */
    private static final int KEY_MODULE_LIMIT = 10;

    private MarkdownPartitionReport() {
    }

    public static String render(List<AndroidBpModule> modules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Partition Placement Report (Android.bp)\n\n");
        if (modules == null || modules.isEmpty()) {
            sb.append("(no Android.bp modules found)\n");
            return sb.toString();
        }
        Map<String, List<AndroidBpModule>> byPartition = groupByPartition(modules);
        SoongGraphAnalysis graph = SoongGraphAnalysis.of(modules);
        Map<String, Integer> dependents = dependentCounts(graph);

        sb.append("- Total modules: ").append(modules.size()).append('\n');
        for (Map.Entry<String, List<AndroidBpModule>> e : byPartition.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ")
                    .append(e.getValue().size()).append('\n');
        }
        sb.append('\n');
        sb.append("_partition は vendor / product_specific / system_ext_specific 等の"
                + " Soong 属性から推定 (既定は system)。Treble 境界 (system↔vendor) の"
                + "把握に使う。_\n\n");

        for (Map.Entry<String, List<AndroidBpModule>> e : byPartition.entrySet()) {
            appendPartitionSection(sb, e.getKey(), e.getValue(), dependents);
        }
        appendCrossPartitionDeps(sb, modules, graph);
        return sb.toString();
    }

    /** partition → モジュールリスト (代表 partition の固定順、空は除外)。 */
    static Map<String, List<AndroidBpModule>> groupByPartition(
            List<AndroidBpModule> modules) {
        Map<String, List<AndroidBpModule>> byPartition = new LinkedHashMap<>();
        for (String p : PARTITION_ORDER) {
            byPartition.put(p, new ArrayList<>());
        }
        for (AndroidBpModule m : modules) {
            byPartition.computeIfAbsent(m.getPartition(), k -> new ArrayList<>()).add(m);
        }
        byPartition.values().removeIf(List::isEmpty);
        return byPartition;
    }

    /** モジュール名 → ローカル被依存数 (正規化済みエッジから集計)。 */
    private static Map<String, Integer> dependentCounts(SoongGraphAnalysis graph) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : graph.reverseEdges().entrySet()) {
            counts.put(e.getKey(), e.getValue().size());
        }
        return counts;
    }

    /** 1 partition 分のセクション (種類内訳 + 主要モジュール)。 */
    private static void appendPartitionSection(StringBuilder sb, String partition,
                                               List<AndroidBpModule> mods,
                                               Map<String, Integer> dependents) {
        sb.append("## ").append(partition).append(" (")
                .append(mods.size()).append(" modules)\n\n");

        // 種類内訳 (件数降順)
        Map<String, Integer> typeCounts = new TreeMap<>();
        for (AndroidBpModule m : mods) {
            if (!m.getType().isEmpty()) {
                typeCounts.merge(m.getType(), 1, Integer::sum);
            }
        }
        sb.append("| Type | Count |\n|---|---|\n");
        List<Map.Entry<String, Integer>> rank = new ArrayList<>(typeCounts.entrySet());
        rank.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, Integer> e : rank) {
            sb.append("| `").append(e.getKey()).append("` | ")
                    .append(e.getValue()).append(" |\n");
        }
        sb.append('\n');

        // 主要モジュール (被依存数順)
        List<AndroidBpModule> named = new ArrayList<>();
        for (AndroidBpModule m : mods) {
            if (!m.getName().isEmpty()) {
                named.add(m);
            }
        }
        if (named.isEmpty()) {
            return;
        }
        named.sort(Comparator
                .<AndroidBpModule>comparingInt(
                        m -> -dependents.getOrDefault(m.getName(), 0))
                .thenComparing(AndroidBpModule::getName));
        sb.append("主要モジュール (被依存数順):\n\n");
        sb.append("| Module | Type | Dependents (被依存数) |\n|---|---|---|\n");
        int limit = Math.min(named.size(), KEY_MODULE_LIMIT);
        for (int i = 0; i < limit; i++) {
            AndroidBpModule m = named.get(i);
            sb.append("| `").append(m.getName()).append("` | `")
                    .append(m.getType()).append("` | ")
                    .append(dependents.getOrDefault(m.getName(), 0)).append(" |\n");
        }
        if (named.size() > limit) {
            sb.append("\n_(+").append(named.size() - limit).append(" more)_\n");
        }
        sb.append('\n');
    }

    /** partition を跨ぐローカル依存を列挙する。 */
    private static void appendCrossPartitionDeps(StringBuilder sb,
                                                 List<AndroidBpModule> modules,
                                                 SoongGraphAnalysis graph) {
        Map<String, AndroidBpModule> byName = new LinkedHashMap<>();
        for (AndroidBpModule m : modules) {
            if (!m.getName().isEmpty()) {
                byName.putIfAbsent(m.getName(), m);
            }
        }
        sb.append("## Cross-partition dependencies (パーティション跨ぎ依存)\n\n");
        List<String[]> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AndroidBpModule m : modules) {
            if (m.getName().isEmpty()) {
                continue;
            }
            for (String depRaw : m.getDeps()) {
                String dep = graph.canonical(depRaw);
                AndroidBpModule target = byName.get(dep);
                if (target == null || target.getPartition().equals(m.getPartition())) {
                    continue;
                }
                if (!seen.add(m.getName() + "->" + dep)) {
                    continue;
                }
                String kind = m.kindOf(depRaw);
                rows.add(new String[] {m.getName(), m.getPartition(),
                        dep, target.getPartition(), kind.isEmpty() ? "—" : kind});
            }
        }
        if (rows.isEmpty()) {
            sb.append("_パーティションを跨ぐ依存はありません。_\n");
            return;
        }
        sb.append("_Treble では system↔vendor を直接リンクせず、AIDL/HIDL"
                + " インタフェース経由にするのが原則。以下は確認対象の跨ぎ依存。_\n\n");
        sb.append("| From | From partition | To | To partition | Kind |\n");
        sb.append("|---|---|---|---|---|\n");
        for (String[] r : rows) {
            sb.append("| `").append(r[0]).append("` | ").append(r[1]).append(" | `")
                    .append(r[2]).append("` | ").append(r[3]).append(" | `")
                    .append(r[4]).append("` |\n");
        }
    }
}
