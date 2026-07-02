// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link AndroidBpModule} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (モジュール数 / カテゴリ別件数)</li>
 *   <li>カテゴリ別モジュール一覧 (name / type / 依存数 / srcs 数 / 場所)</li>
 *   <li>外部依存 (このプロジェクト内で宣言されていないモジュール名) のランキング</li>
 * </ol>
 */
public final class MarkdownSoongReport {

    private MarkdownSoongReport() {
    }

    public static String render(List<AndroidBpModule> modules) {
        return render(modules, "Soong (Android.bp) Module Report",
                "(no Android.bp modules found)");
    }

    /**
     * タイトル・空メッセージを差し替えて描画する。{@code --android-mk} が
     * {@link AndroidMkParser} の結果 (同じ {@link AndroidBpModule} モデル) を
     * 同じ体裁で出力するために使う。
     */
    public static String render(List<AndroidBpModule> modules, String title,
                                String emptyNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        if (modules == null || modules.isEmpty()) {
            sb.append(emptyNote).append('\n');
            return sb.toString();
        }

        // カテゴリ別集計
        Map<String, List<AndroidBpModule>> byCategory = new LinkedHashMap<>();
        java.util.Set<String> localNames = new java.util.LinkedHashSet<>();
        for (AndroidBpModule m : modules) {
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
            if (!m.getName().isEmpty()) localNames.add(m.getName());
        }

        int testCount = 0;
        for (AndroidBpModule m : modules) {
            if (m.isTest()) testCount++;
        }
        sb.append("- Total modules: ").append(modules.size()).append('\n');
        for (Map.Entry<String, List<AndroidBpModule>> e : byCategory.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ")
                    .append(e.getValue().size()).append('\n');
        }
        if (testCount > 0) {
            sb.append("- test modules (テスト): ").append(testCount).append('\n');
        }
        sb.append('\n');

        appendTypeHistogram(sb, modules);
        appendPartitionBreakdown(sb, modules);
        appendAidlInterfaces(sb, modules);

        for (Map.Entry<String, List<AndroidBpModule>> e : byCategory.entrySet()) {
            sb.append("## ").append(e.getKey()).append(" modules\n\n");
            sb.append("| Name | Type | Partition | SDK | Deps | Srcs | Location |\n");
            sb.append("|---|---|---|---|---|---|---|\n");
            List<AndroidBpModule> sorted = new ArrayList<>(e.getValue());
            sorted.sort(Comparator.comparing(AndroidBpModule::getName));
            for (AndroidBpModule m : sorted) {
                String loc = m.getFile().isEmpty() ? ""
                        : "`" + m.getFile() + ":" + m.getLineHint() + "`";
                String sdk = m.scalar("min_sdk_version");
                if (sdk.isEmpty()) sdk = m.scalar("sdk_version");
                String nameCell = m.getName() + (m.isTest() ? " ⚙" : "");
                sb.append("| `").append(nameCell).append("` | `")
                        .append(m.getType()).append("` | ")
                        .append(m.getPartition()).append(" | ")
                        .append(sdk.isEmpty() ? "—" : sdk).append(" | ")
                        .append(m.getDeps().size()).append(" | ")
                        .append(m.getSrcs().size()).append(" | ")
                        .append(loc).append(" |\n");
            }
            sb.append('\n');
        }

        SoongGraphAnalysis graph = SoongGraphAnalysis.of(modules);

        // 外部依存ランキング (このプロジェクトに宣言されてない deps)。
        // AIDL 生成ライブラリ名 (foo-V3-java 等) はローカル aidl_interface へ畳み込む。
        Map<String, Integer> external = new TreeMap<>();
        for (AndroidBpModule m : modules) {
            for (String dep : m.getDeps()) {
                String canon = graph.canonical(dep);
                if (!localNames.contains(canon)) {
                    external.merge(canon, 1, Integer::sum);
                }
            }
        }
        if (!external.isEmpty()) {
            sb.append("## External dependencies (most-referenced)\n\n");
            sb.append("| Dependency | Reference count |\n");
            sb.append("|---|---|\n");
            List<Map.Entry<String, Integer>> rank = new ArrayList<>(external.entrySet());
            rank.sort((a, b) -> {
                int c = Integer.compare(b.getValue(), a.getValue());
                if (c != 0) return c;
                return a.getKey().compareTo(b.getKey());
            });
            int limit = Math.min(rank.size(), 50);
            for (int i = 0; i < limit; i++) {
                sb.append("| `").append(rank.get(i).getKey()).append("` | ")
                        .append(rank.get(i).getValue()).append(" |\n");
            }
            if (rank.size() > limit) {
                sb.append("\n_(+").append(rank.size() - limit).append(" more)_\n");
            }
        }

        // ローカル逆依存 (被依存) ランキングと循環依存
        appendReverseDeps(sb, graph);
        appendCycles(sb, graph);
        return sb.toString();
    }

    /** モジュール種別 (cc_library / android_app …) のヒストグラムを件数降順で出力する。 */
    private static void appendTypeHistogram(StringBuilder sb, List<AndroidBpModule> modules) {
        Map<String, Integer> counts = new TreeMap<>();
        for (AndroidBpModule m : modules) {
            if (!m.getType().isEmpty()) {
                counts.merge(m.getType(), 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return;
        }
        sb.append("## Module types (種別ヒストグラム)\n\n");
        sb.append("| Type | Count |\n|---|---|\n");
        List<Map.Entry<String, Integer>> rank = new ArrayList<>(counts.entrySet());
        rank.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, Integer> e : rank) {
            sb.append("| `").append(e.getKey()).append("` | ")
                    .append(e.getValue()).append(" |\n");
        }
        sb.append('\n');
    }

    /** 配置 (パーティション) 別のモジュール件数を出力する。 */
    private static void appendPartitionBreakdown(StringBuilder sb,
                                                 List<AndroidBpModule> modules) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        // 代表的なパーティションを宣言順に固定表示
        for (String p : new String[] {"system", "system_ext", "product",
                "vendor", "odm", "recovery", "ramdisk"}) {
            counts.put(p, 0);
        }
        for (AndroidBpModule m : modules) {
            counts.merge(m.getPartition(), 1, Integer::sum);
        }
        boolean anyNonSystem = false;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (!e.getKey().equals("system") && e.getValue() > 0) {
                anyNonSystem = true;
                break;
            }
        }
        if (!anyNonSystem) {
            // 全部 system のときは冗長なので出さない
            return;
        }
        sb.append("## Partition placement (配置)\n\n");
        sb.append("_モジュールがどのパーティション (system/vendor/product/...) に置かれるか。"
                + "Treble 境界 (system↔vendor) の把握に有用。_\n\n");
        sb.append("| Partition | Count |\n|---|---|\n");
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > 0) {
                sb.append("| ").append(e.getKey()).append(" | ")
                        .append(e.getValue()).append(" |\n");
            }
        }
        sb.append('\n');
    }

    /** {@code aidl_interface} モジュールの安定度・バージョン・バックエンドを一覧する。 */
    private static void appendAidlInterfaces(StringBuilder sb, List<AndroidBpModule> modules) {
        List<AndroidBpModule> aidls = new ArrayList<>();
        for (AndroidBpModule m : modules) {
            if (m.getType().contains("aidl_interface")) {
                aidls.add(m);
            }
        }
        if (aidls.isEmpty()) {
            return;
        }
        aidls.sort(Comparator.comparing(AndroidBpModule::getName));
        sb.append("## AIDL interfaces (AIDL インタフェース)\n\n");
        sb.append("_プロセス間通信 (Binder) の境界定義。stability=vintf は system↔vendor を"
                + "またぐ安定 API、unstable=true は凍結なし。backend は生成する言語結合。_\n\n");
        sb.append("| Interface | Stability | Versions | Latest | Backends |\n");
        sb.append("|---|---|---|---|---|\n");
        for (AndroidBpModule m : aidls) {
            String stability = m.boolProp("unstable") ? "unstable"
                    : (m.scalar("stability").isEmpty() ? "—" : m.scalar("stability"));
            String versions = m.scalar("versions_count").isEmpty()
                    ? "0" : m.scalar("versions_count");
            String latest = m.scalar("latest_version").isEmpty()
                    ? "—" : m.scalar("latest_version");
            String backends = m.scalar("backends").isEmpty()
                    ? "—" : m.scalar("backends");
            sb.append("| `").append(m.getName()).append("` | ")
                    .append(stability).append(" | ")
                    .append(versions).append(" | ")
                    .append(latest).append(" | ")
                    .append(backends).append(" |\n");
        }
        sb.append('\n');
    }

    /** ローカルモジュール間で最も依存されているモジュールの被依存ランキング。 */
    private static void appendReverseDeps(StringBuilder sb, SoongGraphAnalysis graph) {
        List<Map.Entry<String, Integer>> rank = graph.mostDependedUpon(30);
        if (rank.isEmpty()) {
            return;
        }
        sb.append("\n## Most depended-upon modules (被依存ランキング)\n\n");
        sb.append("_プロジェクト内で他モジュールから参照されている回数が多い順。"
                + "変更時の影響が大きいモジュールを示す。_\n\n");
        sb.append("| Module | Dependents (被依存数) |\n");
        sb.append("|---|---|\n");
        for (Map.Entry<String, Integer> e : rank) {
            sb.append("| `").append(e.getKey()).append("` | ")
                    .append(e.getValue()).append(" |\n");
        }
    }

    /** Tarjan SCC で検出した循環依存を列挙する。 */
    private static void appendCycles(StringBuilder sb, SoongGraphAnalysis graph) {
        List<List<String>> cycles = graph.cycles();
        sb.append("\n## Dependency cycles (循環依存)\n\n");
        if (cycles.isEmpty()) {
            sb.append("_循環依存は検出されませんでした。_\n");
            return;
        }
        sb.append("_互いに依存し合うモジュール群 (強連結成分)。"
                + "ビルド順や責務分離の観点で見直し候補。_\n\n");
        int idx = 1;
        for (List<String> c : cycles) {
            sb.append(idx++).append(". ");
            for (int i = 0; i < c.size(); i++) {
                if (i > 0) {
                    sb.append(" ↔ ");
                }
                sb.append('`').append(c.get(i)).append('`');
            }
            sb.append('\n');
        }
    }
}
