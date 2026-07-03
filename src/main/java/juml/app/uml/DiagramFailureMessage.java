// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.ErrorCode;
import juml.util.Messages;

/**
 * 図の描画に失敗したときにタブ内へ表示する案内 (原因 + 対処) を組み立てる。
 *
 * <p>{@link DiagramTabPane} 本体の肥大化を避けるため、HTML メッセージ生成だけを
 * ここに切り出している。文言は {@link Messages} 経由で言語設定に追従する。</p>
 *
 * <p>クローズド環境での目視転記を想定し、エラー ID を大きく見出し表示する。
 * ID は {@code juml-errcode:} リンクでアプリ内リファレンスへ、
 * {@code juml-copy:} リンクで詳細のクリップボードコピーへ誘導する
 * (処理は {@link DiagramTabPane} 側のハイパーリンクリスナーが行う)。</p>
 */
final class DiagramFailureMessage {

    private DiagramFailureMessage() {
    }

    /** 失敗原因と対処を含む HTML メッセージを返す。 */
    static String forError(Throwable error) {
        return forError(error, null, null);
    }

    /**
     * 失敗原因と対処を含む HTML メッセージを返す。
     *
     * @param dumped 保存済みの失敗 PlantUML ファイル (null なら保存案内を省略)
     * @param code   分類済みのエラー ID (null なら ID 見出しを省略)
     */
    static String forError(Throwable error, java.io.File dumped, ErrorCode code) {
        StringBuilder sb = new StringBuilder();
        if (code != null && code.hasId()) {
            // 手書き転記の主役: ID を大きく表示し、リファレンスへのリンクにする
            sb.append("<div style='font-size:16pt'><b><a href='juml-errcode:")
              .append(code.getId()).append("'>").append(code.getId())
              .append("</a></b></div>")
              .append(esc(code.summary()))
              .append("<br><br>");
        }
        sb.append("<b>").append(Messages.get("diag.fail.title")).append("</b><br>")
          .append(esc(fullReason(error)))
          .append("<br><br>");
        if (!juml.core.formats.uml.PlantUmlRenderer.isGraphvizAvailable()) {
            // Graphviz 無効時は純 Java の Smetana レイアウトになり、大きな図で破綻しやすい。
            // dot を有効化すれば描画できる可能性が高いので、最優先で案内する。
            sb.append(Messages.get("diag.fail.graphviz"));
        } else {
            sb.append(Messages.get("diag.fail.tooLarge")).append("<br>");
        }
        sb.append("• ").append(Messages.get("diag.fail.tipPackage")).append("<br>")
          .append("• ").append(Messages.get("diag.fail.tipPreset")).append("<br>")
          .append("• ").append(Messages.get("diag.fail.tipScope")).append("<br><br>")
          .append(Messages.get("diag.fail.pumlShown"));
        if (dumped != null) {
            sb.append("<br>").append(Messages.get("diag.fail.savedTo"))
              .append(' ').append(esc(dumped.getAbsolutePath()));
        }
        sb.append("<br>").append(Messages.get("diag.fail.seeLog"));
        if (code != null && code.hasId()) {
            sb.append("<br><br><a href='juml-errcode:").append(code.getId()).append("'>")
              .append(Messages.get("diag.fail.remedyLink")).append("</a>")
              .append(" | <a href='juml-copy:'>")
              .append(Messages.get("diag.fail.copyLink")).append("</a>");
        }
        return sb.toString();
    }

    /**
     * 失敗原因の全文 (メッセージカード用、切り詰めなし)。ステータスバー用の
     * {@link #reason(Throwable)} と違い、原因を "…" で省略しない。
     */
    static String fullReason(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String m = error.getMessage();
        return (m == null || m.isEmpty()) ? error.getClass().getSimpleName() : m;
    }

    /** 失敗原因の 1 行要約 (ステータスバー用)。 */
    static String reason(Throwable error) {
        if (error instanceof juml.core.formats.uml.PlantUmlRenderFailedException) {
            // リンタが原因候補を添えていれば、それも含めて見せる (例: 色とリンクの順序崩れ)。
            String detail = error.getMessage();
            String r = (detail == null || detail.isEmpty())
                    ? "PlantUML layout error (Smetana)" : detail;
            return r.length() > 160 ? r.substring(0, 159) + "…" : r;
        }
        String m = error != null ? error.getMessage() : null;
        return m == null ? (error != null ? error.getClass().getSimpleName() : "unknown")
                : (m.length() > 160 ? m.substring(0, 159) + "…" : m);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
