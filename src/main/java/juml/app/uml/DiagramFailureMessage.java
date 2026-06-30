// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

/**
 * 図の描画に失敗したときにタブ内へ表示する案内 (原因 + 対処) を組み立てる。
 *
 * <p>{@link DiagramTabPane} 本体の肥大化を避けるため、HTML メッセージ生成だけを
 * ここに切り出している。文言は {@link Messages} 経由で言語設定に追従する。</p>
 */
final class DiagramFailureMessage {

    private DiagramFailureMessage() {
    }

    /** 失敗原因と対処を含む HTML メッセージを返す。 */
    static String forError(Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(Messages.get("diag.fail.title")).append("</b><br>")
          .append(esc(reason(error)))
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
        return sb.toString();
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
