// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * 2 つのテキスト (保存版 vs 編集中) の行単位差分を作る小さなユーティリティ。
 *
 * <p>LCS (最長共通部分列) ベースで、各行を {@code "  "} (変化なし) /
 * {@code "- "} (削除) / {@code "+ "} (追加) の接頭辞付きで並べる。.puml は
 * 数十行程度なので O(n·m) で十分。外部依存なしの純関数。</p>
 */
final class PumlDiff {

    private PumlDiff() {
    }

    /** 変化があれば true (差分行が 1 つ以上ある)。 */
    static boolean hasChanges(String oldText, String newText) {
        return !normalize(oldText).equals(normalize(newText));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 行単位の差分を接頭辞付きテキストで返す。 */
    static String unified(String oldText, String newText) {
        List<String> a = splitLines(normalize(oldText));
        List<String> b = splitLines(normalize(newText));
        int n = a.size();
        int m = b.size();
        int[][] c = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                c[i][j] = a.get(i).equals(b.get(j))
                        ? c[i + 1][j + 1] + 1
                        : Math.max(c[i + 1][j], c[i][j + 1]);
            }
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                sb.append("  ").append(a.get(i)).append('\n');
                i++;
                j++;
            } else if (c[i + 1][j] >= c[i][j + 1]) {
                sb.append("- ").append(a.get(i)).append('\n');
                i++;
            } else {
                sb.append("+ ").append(b.get(j)).append('\n');
                j++;
            }
        }
        while (i < n) {
            sb.append("- ").append(a.get(i++)).append('\n');
        }
        while (j < m) {
            sb.append("+ ").append(b.get(j++)).append('\n');
        }
        return sb.toString();
    }

    private static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) {
            return out;
        }
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) {
            out.add(s.substring(start)); // 末尾に改行が無い残りの行
        }
        return out;
    }
}
