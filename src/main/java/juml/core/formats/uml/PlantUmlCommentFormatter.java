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
        // JavaDoc 由来の < > & (例: {@code List<String>} や <br>) は PlantUML が
        // creole/HTML タグとして解釈し、外側の <color:...> ラッパとも干渉する。
        // 長さ確定後に HTML エンティティへ変換して無害化する。
        return escapeHtml(t);
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

    /** コメント/ラベル中の {@code & < >} を HTML エンティティへ変換する。 */
    static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * PlantUML の制御行 (矢印ラベル・guard・title 等) に安全に書ける形へ整形する。
     * 改行を畳み、80 文字超を省略し、{@code < > &} を HTML エンティティ化してタグ誤認を防ぐ。
     */
    static String escapeLabel(String s) {
        return escapeLabel(s, 80);
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
        // エスケープ前に長さを評価し、HTML エンティティの途中で切れないようにする
        if (maxLen > 0 && trimmed.length() > maxLen) {
            int cut = Math.max(1, maxLen - 3);
            trimmed = trimmed.substring(0, cut) + "...";
        }
        return escapeHtml(trimmed);
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
                    // note 本文も < > & をエスケープ (INLINE と挙動を揃え、タグ誤認を防ぐ)
                    out.append(indent).append("  ").append(escapeHtml(wl)).append('\n');
                }
            }
        }
    }
}
