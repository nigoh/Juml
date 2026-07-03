// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

/**
 * PlantUML 図のコメント/ノート整形を担う共有ユーティリティ。
 *
 * <p>クラス図・シーケンス図など複数の生成器で重複していたインラインコメントの無害化・
 * 単語境界での折り返し・note 本文の整形を 1 箇所に集約する。状態を持たない純粋関数のみ。</p>
 */
final class PlantUmlCommentFormatter {

    private PlantUmlCommentFormatter() {
    }

    /**
     * PlantUML の {@code ..} セパレータと干渉する文字を抑止し、長さも制限する。
     */
    static String sanitizeInlineComment(String s, int maxLen) {
        // PlantUML の class body 内でレイアウトを乱す制御文字を除去
        String t = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        // 末尾の '..' は区切りと干渉するためスペースに置換
        t = t.replaceAll("\\.\\.+$", ".");
        if (maxLen > 0 && t.length() > maxLen) {
            t = t.substring(0, Math.max(1, maxLen - 1)) + "…";
        }
        // JavaDoc が PlantUML 図 (@startuml/@enduml 等) を例示していると、別図の開始と
        // 誤認され構文エラーになる。@ の直後にゼロ幅スペースを挟んで無害化する。
        t = neutralizePlantUmlDirectives(t);
        // JavaDoc 由来の <br> や <b> 等は PlantUML が creole/HTML タグとして解釈し
        // テキストが欠落する。長さ確定後にタグ開始をエスケープして無害化する。
        return escapeText(t);
    }

    /**
     * コメント本文中の図境界ディレクティブ ({@code @startuml} / {@code @enduml} 等) を
     * 無害化する。{@code @} の直後にゼロ幅スペース (U+200B) を挟み、PlantUML が別図の
     * 開始/終了と誤認しないようにする。表示上は元のテキストと変わらない。
     */
    static String neutralizePlantUmlDirectives(String s) {
        if (s == null || s.indexOf('@') < 0) {
            return s;
        }
        return s.replaceAll("(?i)@(start|end)", "@\u200B$1");
    }

    /**
     * note ブロック本文の 1 行が終端キーワード ({@code end note} / {@code endnote})
     * そのものだった場合に、行頭へゼロ幅スペースを挟んで終端と誤認されないようにする。
     *
     * <p>コメント本文に「end note」という行があると、そこで note ブロックが
     * 打ち切られ、残りの本文が生の PlantUML ディレクティブとして解釈されて
     * 構文エラーになる (終端注入)。表示上は元のテキストと変わらない。</p>
     */
    static String neutralizeNoteTerminator(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return NOTE_TERMINATOR.matcher(s).matches() ? "\u200B" + s : s;
    }

    /** note ブロックの終端キーワードだけの行。 */
    private static final java.util.regex.Pattern NOTE_TERMINATOR =
            java.util.regex.Pattern.compile("(?i)^\\s*end\\s?note\\s*$");

    /**
     * コメント/ラベル中の creole/HTML タグ開始 ({@code <}) をチルダエスケープする。
     *
     * <p>同梱の PlantUML 1.2026.x は {@code &lt;} 等の HTML エンティティを解釈せず
     * そのまま表示してしまう一方、{@code <b>} のような既知タグは書式として解釈され
     * テキストが欠落する。creole のエスケープ文字 {@code ~} を {@code <} の直前に
     * 挟むことで、全コンテキスト (メンバ行 / note / ラベル / title / WBS) で
     * 元の文字どおり表示される (実測確認済み)。{@code >} と {@code &} は生のままで
     * 安全に表示されるため変換しない。</p>
     */
    static String escapeText(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        // 制御文字 (NUL 等) はソース由来の定数値・コメントから紛れ込むことがあり、
        // PlantUML の解釈や出力 SVG を壊すため除去する (タブは許容)。
        String cleaned = CONTROL_CHARS.matcher(s).replaceAll("");
        // 図テキスト中に "@startuml"/"@enduml" という文字列 (例: テンプレート定数の値)
        // が現れても、PlantUML にブロック境界と誤認されないよう不可視文字で分断する。
        cleaned = BLOCK_DIRECTIVE.matcher(cleaned).replaceAll("@\u200B$1uml");
        // "[[" は PlantUML のリンク構文 ([[url]]) として解釈され、テキストが実リンク化して
        // 欠落する (正規表現定数などで頻出)。creole エスケープ ~ を間に挟んで
        // リテラル "[[" として表示させる (実測確認済み)。意図的なリンク
        // ([[juml://...]]) は本メソッドを通らず生成されるため影響しない。
        cleaned = cleaned.replace("[[", "[~[");
        return cleaned.replace("<", "~<");
    }

    /** 除去対象の制御文字 (水平タブ・改行・復帰は除く)。 */
    private static final java.util.regex.Pattern CONTROL_CHARS =
            java.util.regex.Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /** 図テキストに埋め込まれた PlantUML ブロック境界文字列。 */
    private static final java.util.regex.Pattern BLOCK_DIRECTIVE =
            java.util.regex.Pattern.compile("(?i)@(start|end)uml");

    /**
     * PlantUML の制御行 (矢印ラベル・guard・title 等) に安全に書ける形へ整形する。
     * 改行を畳み、{@code <} をチルダエスケープしてタグ誤認を防ぐ。
     * 既定では切り詰めない (全文表示)。
     */
    static String escapeLabel(String s) {
        return escapeLabel(s, 0);
    }

    /**
     * {@link #escapeLabel(String)} の最大長を指定できる版。
     * {@code maxLen} 以下なら切り詰めない (0 以下は無制限)。
     */
    static String escapeLabel(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String trimmed = s.replaceAll("\\s+", " ").trim();
        // エスケープ前に長さを評価し、エスケープ文字の途中で切れないようにする
        if (maxLen > 0 && trimmed.length() > maxLen) {
            int cut = Math.max(1, maxLen - 3);
            trimmed = trimmed.substring(0, cut) + "...";
        }
        return escapeText(trimmed);
    }

    /**
     * テキストを単語境界（スペース）で折り返し、maxLen 文字以内の行に分割する。
     * スペースが見つからない場合は maxLen 文字でハードブレークする。
     */
    static String wordWrap(String s, int maxLen) {
        if (maxLen <= 0 || s == null || s.length() <= maxLen) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < s.length()) {
            if (s.length() - start <= maxLen) {
                sb.append(s, start, s.length());
                break;
            }
            int end = start + maxLen;
            int breakAt = s.lastIndexOf(' ', end);
            if (breakAt <= start) {
                // スペースが見つからないためハードブレーク
                breakAt = end;
                sb.append(s, start, breakAt).append('\n');
                start = breakAt;
            } else {
                sb.append(s, start, breakAt).append('\n');
                start = breakAt + 1; // スペースをスキップ
            }
        }
        return sb.toString();
    }

    /** note ブロックの本文を 1 行ずつ書き出す。maxLen &gt; 0 のとき wordWrap を適用。 */
    static void appendNoteBody(StringBuilder out, String comment, String indent, int maxLen) {
        // @startuml/@enduml 等の図境界ディレクティブを無害化してから整形する。
        comment = neutralizePlantUmlDirectives(comment);
        String[] lines = comment.split("\n", -1);
        for (String line : lines) {
            String t = line.replace('\r', ' ').replace('\t', ' ').trim();
            if (t.isEmpty()) {
                continue;
            }
            String wrapped = wordWrap(t, maxLen);
            for (String wl : wrapped.split("\n", -1)) {
                if (!wl.isEmpty()) {
                    // note 本文もタグ開始をエスケープ (INLINE と挙動を揃え、タグ誤認を防ぐ)。
                    // "end note" だけの行は終端注入になるため無害化する。
                    out.append(indent).append("  ")
                            .append(neutralizeNoteTerminator(escapeText(wl))).append('\n');
                }
            }
        }
    }
}
