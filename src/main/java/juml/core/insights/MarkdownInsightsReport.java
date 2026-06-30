// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.insights;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link InsightsModel} を Markdown レポート文字列に整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー: クラス数 / 参照数 / 循環数 / デッドコード候補数</li>
 *   <li>エントリポイント</li>
 *   <li>ホットスポット (fan-in / fan-out 上位)</li>
 *   <li>パッケージ循環依存</li>
 *   <li>デッドコード候補</li>
 *   <li>推定レイヤ構造</li>
 * </ol>
 */
public final class MarkdownInsightsReport {

    /** デッドコード候補の表示上限 (超過分は件数のみ表示)。 */
    private static final int MAX_DEAD_CODE_ROWS = 100;

    private MarkdownInsightsReport() {
    }

    public static String render(InsightsModel model) {
        if (model == null) {
            return "# Architecture Insights\n\n(no data)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Architecture Insights\n\n");
        sb.append("> 静的解析による推定です。DI / リフレクション / AIDL / XML 参照、および\n");
        sb.append("> Kotlin ソース内の参照 (Kotlin は構造のみ抽出し呼び出しは追跡しない) は\n");
        sb.append("> 検出できないため、特にデッドコード候補は削除前に必ず確認してください。\n");
        sb.append("> Kotlin を含むプロジェクトでは、Kotlin からのみ参照される型が誤って\n");
        sb.append("> dead code 候補に挙がることがあります。\n\n");

        renderSummary(sb, model);
        renderEntryPoints(sb, model);
        renderHotspots(sb, model);
        renderPackageCycles(sb, model);
        renderDeadCode(sb, model);
        renderLayers(sb, model);
        return sb.toString();
    }

    private static void renderSummary(StringBuilder sb, InsightsModel model) {
        sb.append("## Summary\n\n");
        sb.append("- Classes: ").append(model.getClassCount()).append('\n');
        sb.append("- References indexed: ").append(model.getReferenceCount()).append('\n');
        sb.append("- Entry points: ").append(model.getEntryPoints().size()).append('\n');
        sb.append("- Package cycles: ").append(model.getPackageCycles().size()).append('\n');
        sb.append("- Dead code candidates: ")
                .append(model.getDeadCodeCandidates().size()).append('\n');
    }

    private static void renderEntryPoints(StringBuilder sb, InsightsModel model) {
        sb.append("\n## Entry Points\n\n");
        if (model.getEntryPoints().isEmpty()) {
            sb.append("(no entry points detected)\n");
            return;
        }
        sb.append("| Kind | Class | Detail |\n");
        sb.append("|---|---|---|\n");
        for (InsightsModel.EntryPoint e : model.getEntryPoints()) {
            sb.append("| ").append(e.getKind()).append(" | `")
                    .append(e.getFqn()).append("` | ")
                    .append(e.getDetail()).append(" |\n");
        }
    }

    private static void renderHotspots(StringBuilder sb, InsightsModel model) {
        sb.append("\n## Hotspots (top ").append(InsightsAnalyzer.MAX_HOTSPOTS)
                .append(" by fan-in)\n\n");
        if (model.getHotspots().isEmpty()) {
            sb.append("(no references indexed)\n");
            return;
        }
        sb.append("コードリーディングはこの表の上位 (多くから使われる中心クラス) から");
        sb.append("読み始めると効率的です。\n\n");
        sb.append("| Class | Fan-in | Fan-out | Top referrers |\n");
        sb.append("|---|---|---|---|\n");
        for (InsightsModel.Hotspot h : model.getHotspots()) {
            sb.append("| `").append(h.getFqn()).append("` | ")
                    .append(h.getFanIn()).append(" | ")
                    .append(h.getFanOut()).append(" | ");
            List<String> refs = h.getTopReferrers();
            for (int i = 0; i < refs.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append('`').append(simpleName(refs.get(i))).append('`');
            }
            sb.append(" |\n");
        }
    }

    private static void renderPackageCycles(StringBuilder sb, InsightsModel model) {
        sb.append("\n## Package Cycles\n\n");
        if (model.getPackageCycles().isEmpty()) {
            sb.append("循環依存は検出されませんでした。\n");
            return;
        }
        sb.append("パッケージ間の循環依存は変更影響が双方向に波及します。\n\n");
        int n = 1;
        for (InsightsModel.PackageCycle c : model.getPackageCycles()) {
            sb.append("### Cycle ").append(n++).append(" (")
                    .append(c.getPackages().size()).append(" packages)\n\n");
            for (String pkg : c.getPackages()) {
                sb.append("- `").append(displayPackage(pkg)).append("`\n");
            }
            sb.append("\nEdges:\n\n");
            for (InsightsModel.PackageEdge e : c.getEdges()) {
                sb.append("- `").append(displayPackage(e.getFrom()))
                        .append("` → `").append(displayPackage(e.getTo()))
                        .append("`\n");
            }
            sb.append('\n');
        }
    }

    private static void renderDeadCode(StringBuilder sb, InsightsModel model) {
        sb.append("\n## Dead Code Candidates\n\n");
        List<InsightsModel.DeadCodeCandidate> all = model.getDeadCodeCandidates();
        if (all.isEmpty()) {
            sb.append("デッドコード候補は検出されませんでした。\n");
            return;
        }
        sb.append("「候補」であり確定ではありません。オーバーロードは名前単位で集約され、\n");
        sb.append("アノテーション付きシンボルはフレームワークから呼ばれる可能性があるため\n");
        sb.append("Confidence を下げています。\n\n");
        sb.append("| Symbol | Kind | Reason | Confidence |\n");
        sb.append("|---|---|---|---|\n");
        int shown = Math.min(all.size(), MAX_DEAD_CODE_ROWS);
        for (int i = 0; i < shown; i++) {
            InsightsModel.DeadCodeCandidate d = all.get(i);
            sb.append("| `").append(d.getSymbol()).append("` | ")
                    .append(d.getKind()).append(" | ")
                    .append(d.getReason()).append(" | ")
                    .append(d.getConfidence()).append(" |\n");
        }
        if (all.size() > shown) {
            sb.append("\n(").append(all.size() - shown)
                    .append(" more candidates omitted)\n");
        }
    }

    private static void renderLayers(StringBuilder sb, InsightsModel model) {
        sb.append("\n## Estimated Layers (best-effort)\n\n");
        if (model.getLayerByPackage().isEmpty()) {
            sb.append("(no packages)\n");
            return;
        }
        sb.append("パッケージ名のセグメント辞書による推定です。\n\n");
        // レイヤ → パッケージ一覧 にグループ化 (レイヤ名順、Unclassified は最後)
        Map<String, List<String>> byLayer = new TreeMap<>();
        for (Map.Entry<String, String> e : model.getLayerByPackage().entrySet()) {
            byLayer.computeIfAbsent(e.getValue(), k -> new ArrayList<>())
                    .add(e.getKey());
        }
        Map<String, List<String>> ordered = new LinkedHashMap<>(byLayer);
        List<String> unclassified = ordered.remove("Unclassified");
        if (unclassified != null) {
            ordered.put("Unclassified", unclassified);
        }
        for (Map.Entry<String, List<String>> e : ordered.entrySet()) {
            sb.append("### ").append(e.getKey()).append('\n').append('\n');
            for (String pkg : e.getValue()) {
                sb.append("- `").append(displayPackage(pkg)).append('`');
                Integer count = model.getClassCountByPackage().get(pkg);
                if (count != null) {
                    sb.append(" (").append(count)
                            .append(count == 1 ? " class)" : " classes)");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
    }

    private static String displayPackage(String pkg) {
        return pkg == null || pkg.isEmpty() ? "(default)" : pkg;
    }

    private static String simpleName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }
}
