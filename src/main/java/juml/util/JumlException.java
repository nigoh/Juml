// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

/**
 * Juml のドメイン例外の共通基底。{@link ErrorCode} を保持し、捕捉側が
 * 「どのエラーか」「対処法は何か」を ID 経由で一意に特定できるようにする。
 *
 * <p>unchecked (RuntimeException 継承) とする。既存コードの throws 宣言を
 * 壊さずに全面移行するためで、致命的でない失敗は各所の境界
 * (SwingWorker の {@code done()}、CLI のトップレベル等) でまとめて捕捉する
 * 従来方針を変えない。</p>
 */
public class JumlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** このエラーのカタログ ID。null は渡せない ({@link ErrorCode#NONE} を使う)。 */
    private final ErrorCode code;

    public JumlException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public JumlException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code != null ? code : ErrorCode.NONE;
    }

    /** カタログ ID。未分類の場合は {@link ErrorCode#NONE}。 */
    public ErrorCode getErrorCode() {
        return code;
    }

    /**
     * 任意の例外からエラー ID を取り出すヘルパ。{@link JumlException} 系なら
     * その ID を、それ以外は fallback を返す。ログ記録側の分類に使う。
     */
    public static ErrorCode codeOf(Throwable t, ErrorCode fallback) {
        if (t instanceof JumlException) {
            ErrorCode c = ((JumlException) t).getErrorCode();
            if (c != null && c.hasId()) {
                return c;
            }
        }
        return fallback;
    }
}
