// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.funcdiff;

import juml.core.formats.uml.JavaMethodInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link MethodDiffAnalyzer.DiffResult} を Markdown レポート文字列に整形する。
 */
public final class MarkdownMethodDiffReport {

    private MarkdownMethodDiffReport() {
    }

    public static String render(MethodDiffAnalyzer.DiffResult result) {
        if (result == null) {
            return "# 関数差分レポート\n\n(データなし)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# 関数差分レポート\n\n");
        appendSummary(sb, result);
        appendCallComparison(sb, result);
        appendDiffDetail(sb, result);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // サマリー
    // -------------------------------------------------------------------------

    private static void appendSummary(StringBuilder sb, MethodDiffAnalyzer.DiffResult r) {
        sb.append("## サマリー\n\n");
        sb.append("| 項目 | 値 |\n");
        sb.append("|---|---|\n");
        sb.append("| Method A | `").append(r.specA.filePath)
          .append("` :: `").append(r.specA.label()).append("()` |\n");
        sb.append("| Method B | `").append(r.specB.filePath)
          .append("` :: `").append(r.specB.label()).append("()` |\n");
        sb.append("| 呼び出し総数 (A / B) | ").append(r.totalCallsA)
          .append(" / ").append(r.totalCallsB).append(" |\n");

        int maxLen = Math.max(r.totalCallsA, r.totalCallsB);
        MethodDiffAnalyzer.SimilarityMetrics m = r.metrics;

        sb.append("| **LCS 類似度** | **")
          .append(fmt2(m.lcsSimilarity))
          .append("** (LCS長=").append(m.lcsLen)
          .append(" / max(").append(r.totalCallsA).append(',').append(r.totalCallsB)
          .append(")=").append(maxLen).append(") |\n");

        sb.append("| **編集距離** | **")
          .append(m.editDistance)
          .append("** — 正規化類似度: ")
          .append(fmt2(m.normalizedEditSimilarity))
          .append(" (1 − ").append(m.editDistance).append('/').append(maxLen)
          .append(") |\n");

        sb.append("| **Jaccard 係数** | **")
          .append(fmt2(m.jaccard))
          .append("** (|A∩B| / |A∪B|) |\n");

        if (r.matchCount + r.partialCount > 0) {
            sb.append("| **平均信頼度** | **")
              .append(fmt2(r.avgConfidence))
              .append("** (マッチペアのみ) |\n");
        }

        sb.append("\n### 計算式の説明\n\n");
        sb.append("- LCS 類似度 = LCS長 / max(|A|, |B|)"
                + " — 順序保持の共通部分割合\n");
        sb.append("- 編集距離 (Levenshtein)"
                + " = 最小(挿入 + 削除 + 置換) 操作数\n");
        sb.append("- 正規化編集類似度"
                + " = 1 − 編集距離 / max(|A|, |B|)\n");
        sb.append("- Jaccard 係数 = |A∩B| / |A∪B|"
                + " — 集合ベース（順序無視・重複排除）\n");
        sb.append("- 信頼度 = receiver\\_score×0.4"
                + " + firstArg\\_score×0.3 + position\\_score×0.3\n");
        sb.append('\n');
    }

    // -------------------------------------------------------------------------
    // 呼び出し比較テーブル
    // -------------------------------------------------------------------------

    private static void appendCallComparison(StringBuilder sb,
                                              MethodDiffAnalyzer.DiffResult r) {
        sb.append("## 呼び出し比較\n\n");
        sb.append("| # | 状態 | メソッドA の呼び出し | メソッドB の呼び出し | 信頼度 | 備考 |\n");
        sb.append("|---|---|---|---|---|---|\n");

        int seq = 1;
        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            String callAStr = row.callA != null ? callLabel(row.callA) : "—";
            String callBStr = row.callB != null ? callLabel(row.callB) : "—";
            String confStr = row.confidence >= 0 ? fmt2(row.confidence) : "—";
            String notes = row.detail != null ? escapePipe(row.detail) : "";

            sb.append("| ").append(seq++).append(" | ")
              .append(kindLabel(row.kind)).append(" | `")
              .append(callAStr).append("` | `")
              .append(callBStr).append("` | ")
              .append(confStr).append(" | ")
              .append(notes).append(" |\n");
        }
        sb.append('\n');
    }

    // -------------------------------------------------------------------------
    // 差分詳細
    // -------------------------------------------------------------------------

    private static void appendDiffDetail(StringBuilder sb, MethodDiffAnalyzer.DiffResult r) {
        List<MethodDiffAnalyzer.DiffRow> onlyA = new ArrayList<>();
        List<MethodDiffAnalyzer.DiffRow> onlyB = new ArrayList<>();
        List<MethodDiffAnalyzer.DiffRow> partial = new ArrayList<>();

        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            switch (row.kind) {
                case ONLY_A:  onlyA.add(row);   break;
                case ONLY_B:  onlyB.add(row);   break;
                case PARTIAL: partial.add(row);  break;
                default: break;
            }
        }

        if (onlyA.isEmpty() && onlyB.isEmpty() && partial.isEmpty()) {
            sb.append("## 差分詳細\n\n差分なし（完全一致）\n");
            return;
        }

        sb.append("## 差分詳細\n\n");

        if (!onlyA.isEmpty()) {
            sb.append("### A のみ\n\n");
            for (MethodDiffAnalyzer.DiffRow row : onlyA) {
                sb.append("- `").append(callLabel(row.callA)).append('`');
                if (row.callA.getReceiver() != null) {
                    sb.append(" — レシーバー: `").append(row.callA.getReceiver()).append('`');
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        if (!onlyB.isEmpty()) {
            sb.append("### B のみ\n\n");
            for (MethodDiffAnalyzer.DiffRow row : onlyB) {
                sb.append("- `").append(callLabel(row.callB)).append('`');
                if (row.callB.getReceiver() != null) {
                    sb.append(" — レシーバー: `").append(row.callB.getReceiver()).append('`');
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        if (!partial.isEmpty()) {
            sb.append("### 部分一致\n\n");
            for (MethodDiffAnalyzer.DiffRow row : partial) {
                sb.append("- `").append(row.callA.getMethodName()).append("()`");
                if (row.detail != null) {
                    sb.append(" — ").append(row.detail);
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
    }

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    private static String kindLabel(MethodDiffAnalyzer.MatchKind kind) {
        switch (kind) {
            case MATCH:   return "一致";
            case PARTIAL: return "部分一致";
            case ONLY_A:  return "A のみ";
            case ONLY_B:  return "B のみ";
            default:      return kind.name();
        }
    }

    private static String callLabel(JavaMethodInfo.Call c) {
        String base = c.getReceiver() != null
                ? c.getReceiver() + "." + c.getMethodName() + "()"
                : c.getMethodName() + "()";
        if (c.getFirstArgLabel() != null) {
            base = base.replace("()", "(" + c.getFirstArgLabel() + ")");
        }
        return base;
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String escapePipe(String s) {
        return s.replace("|", "\\|");
    }
}
