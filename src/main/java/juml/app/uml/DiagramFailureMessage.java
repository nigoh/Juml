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

    /** カードに載せるエンジン出力 (stderr 末尾) の最大文字数。長すぎる場合は末尾のみ残す。 */
    private static final int ENGINE_OUTPUT_CARD_MAX = 800;

    /** 原因チェーンをたどる最大段数 (循環・過剰な深さでの肥大化を防ぐ)。 */
    private static final int MAX_CAUSE_DEPTH = 5;

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
        // レンダリングエンジン (PlantUML/Smetana) が stderr へ出した生の診断を
        // カード内にも直接見せる。これまではログファイルにしか残らず、原因
        // (レイアウトエンジンの内部エラーやスタックトレース) が目視しづらかった。
        String engine = engineOutput(error, ENGINE_OUTPUT_CARD_MAX);
        if (!engine.isEmpty()) {
            sb.append("<br><br><b>").append(Messages.get("diag.fail.detailTitle"))
              .append("</b><br>")
              .append("<span style='font-size:9pt;font-family:monospace'>")
              .append(esc(engine).replace("\n", "<br>"))
              .append("</span>");
        }
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
     *
     * <p>ラップされた例外 (例: {@code IOException} が別例外に包まれている) では、
     * 先頭のメッセージだけだと根本原因が見えないため、原因チェーンを
     * {@code " ← "} で連結して最後まで見せる (最大 {@link #MAX_CAUSE_DEPTH} 段)。</p>
     */
    static String fullReason(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(describe(error));
        Throwable cause = error.getCause();
        int depth = 0;
        while (cause != null && cause != error && depth++ < MAX_CAUSE_DEPTH) {
            String c = describe(cause);
            // 上位メッセージが原因メッセージをそのまま内包している場合は重複を避ける。
            if (!c.isEmpty() && sb.indexOf(c) < 0) {
                sb.append(" ← ").append(c);
            }
            cause = cause.getCause();
        }
        return sb.toString();
    }

    /** 1 例外を「メッセージ、無ければクラス単純名」の 1 行で表す。 */
    private static String describe(Throwable error) {
        String m = error.getMessage();
        return (m == null || m.isEmpty()) ? error.getClass().getSimpleName() : m;
    }

    /**
     * レンダリングエンジン (PlantUML / Smetana) が描画中に stderr へ出力した末尾を返す。
     * レイアウトエンジンの内部エラーやスタックトレースを含み、原因調査の主要な手がかりになる。
     * これまではログファイルにしか残らなかったため、カード・報告テキストへ直接出すために使う。
     *
     * @param error    発生した例外
     * @param maxChars 返す最大文字数 (超過時は末尾を残し先頭を {@code "…"} で省く)。0 以下なら無制限。
     * @return stderr 末尾。PlantUML 由来の失敗でない、または捕捉が無ければ空文字。
     */
    static String engineOutput(Throwable error, int maxChars) {
        if (!(error instanceof juml.core.formats.uml.PlantUmlRenderFailedException)) {
            return "";
        }
        String tail = ((juml.core.formats.uml.PlantUmlRenderFailedException) error)
                .getStderrTail();
        if (tail == null) {
            return "";
        }
        tail = tail.trim();
        if (maxChars > 0 && tail.length() > maxChars) {
            tail = "…" + tail.substring(tail.length() - maxChars);
        }
        return tail;
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
