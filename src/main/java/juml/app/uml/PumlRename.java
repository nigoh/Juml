// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 記号 (参加者・クラス・状態…) の一括リネーム (純関数)。
 *
 * <p>長い図で参加者名を変えたくなったとき、検索置換では {@code Alice} が
 * {@code AliceSmith} や説明文の中の Alice まで巻き込まれる。ここでは
 * <b>語として一致する参照だけ</b>を対象にし、さらに壊すと困る場所 —
 * コメント・引用符の中・行の {@code :} より後ろのラベル文 — を最初から見ない。</p>
 *
 * <p>ラベル文まで除くのは誤置換の被害が非対称なため。参照の書き換え漏れは
 * その場で色や描画で気づけるが、文章に紛れた同名語の誤置換は読み直すまで
 * 気づけない。</p>
 */
final class PumlRename {

    private PumlRename() {
    }

    /** 識別子を構成する文字 (PlantUML の素の名前と同じ: 英数字・{@code _}・{@code .})。 */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    /** {@code offset} 位置にある識別子 (無ければ空文字)。 */
    static String wordAt(String text, int offset) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int at = Math.max(0, Math.min(offset, text.length()));
        int start = at;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        int end = at;
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

    /**
     * 新しい名前として通せるか。空・識別子でない文字・構文キーワードを弾く
     * ({@code Alice} を {@code end} に改名できてしまうと、図が静かに壊れる)。
     */
    static boolean isValidNewName(String name) {
        if (name == null || name.isEmpty()
                || (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_')) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (!isWordChar(name.charAt(i))) {
                return false;
            }
        }
        return PumlCompletionDictionary.groupsOf(name.toLowerCase(Locale.ROOT)) == null;
    }

    /**
     * {@code name} が語として現れる範囲 ({@code [start, end)}) を出現順に返す。
     * コメント (行・ブロック)・引用符の中・行の {@code :} 以降は対象外。
     */
    static List<int[]> occurrences(String text, String name) {
        List<int[]> out = new ArrayList<>();
        if (text == null || text.isEmpty() || name == null || name.isEmpty()) {
            return out;
        }
        boolean inComment = false;
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int nl = text.indexOf('\n', lineStart);
            int lineEnd = nl < 0 ? text.length() : nl;
            String stripped = text.substring(lineStart, lineEnd).strip();
            if (inComment) {
                inComment = !stripped.contains("'/");
            } else if (stripped.startsWith("/'")) {
                inComment = !stripped.contains("'/");
            } else if (!stripped.startsWith("'")) {
                scanLine(text, lineStart, lineEnd, name, out);
            }
            if (nl < 0) {
                break;
            }
            lineStart = nl + 1;
        }
        return out;
    }

    /** 1 行を走査する。引用符の中は飛ばし、{@code :} が来たらそこで打ち切る。 */
    private static void scanLine(String text, int from, int to, String name,
                                 List<int[]> out) {
        boolean inQuote = false;
        int i = from;
        while (i < to) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                i++;
                continue;
            }
            if (inQuote) {
                i++;
                continue;
            }
            if (c == ':') {
                // ここから先はメッセージや状態のラベル文。名前と同じ語が
                // 文章として現れても、それは参照ではない。
                return;
            }
            if (isWordChar(c)) {
                int end = i;
                while (end < to && isWordChar(text.charAt(end))) {
                    end++;
                }
                if (end - i == name.length() && text.regionMatches(i, name, 0, name.length())) {
                    out.add(new int[]{i, end});
                }
                i = end;
                continue;
            }
            i++;
        }
    }

    /** {@code name} の全出現を {@code newName} に置き換えた本文を返す (テスト・適用用)。 */
    static String rename(String text, String name, String newName) {
        List<int[]> occ = occurrences(text, name);
        if (occ.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int at = 0;
        for (int[] r : occ) {
            sb.append(text, at, r[0]).append(newName);
            at = r[1];
        }
        sb.append(text, at, text.length());
        return sb.toString();
    }
}
