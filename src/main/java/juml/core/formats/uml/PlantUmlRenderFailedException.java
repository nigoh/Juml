// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.io.IOException;

/**
 * PlantUML のレンダリング失敗 (Smetana のレイアウト例外等) を表す例外。
 *
 * <p>同梱 PlantUML は内部のレイアウトエンジン (Smetana) が落ちた場合、
 * 例外を握り潰してフォールバックの「An error has occured」SVG を出力する。
 * その出力をそのまま保存するとユーザは壊れた SVG を有効なものと誤認するため、
 * Juml 側でフォールバック SVG を検出して本例外に変換する。</p>
 *
 * <p>原因調査を助けるため、エラー SVG から抽出した診断テキスト
 * ({@link #getErrorDetail()})・失敗行番号 ({@link #getErrorLine()})・
 * レンダリング中に PlantUML/Smetana が stderr へ出力した内容の末尾
 * ({@link #getStderrTail()}) を構造化して保持する。</p>
 */
public final class PlantUmlRenderFailedException extends IOException {

    private static final long serialVersionUID = 1L;

    /** エラー SVG 内の {@code [From string (line N)]} から取り出した行番号。不明なら -1。 */
    private final int errorLine;

    /** エラー SVG のテキストノードから抽出した診断メッセージ。無ければ空文字。 */
    private final String errorDetail;

    /** レンダリング中に捕捉した stderr 出力の末尾。無ければ空文字。 */
    private final String stderrTail;

    public PlantUmlRenderFailedException(String message) {
        this(message, -1, "", "");
    }

    public PlantUmlRenderFailedException(String message, int errorLine,
                                          String errorDetail, String stderrTail) {
        super(message);
        this.errorLine = errorLine;
        this.errorDetail = errorDetail != null ? errorDetail : "";
        this.stderrTail = stderrTail != null ? stderrTail : "";
    }

    /** PlantUML が報告した失敗行番号 (生成 PlantUML テキスト内、1 始まり)。不明なら -1。 */
    public int getErrorLine() {
        return errorLine;
    }

    /** エラー SVG から抽出した診断テキスト (エラー行の内容・Syntax Error? 等)。 */
    public String getErrorDetail() {
        return errorDetail;
    }

    /** レンダリング中の stderr 出力の末尾 (Smetana の内部ログ等)。 */
    public String getStderrTail() {
        return stderrTail;
    }
}
