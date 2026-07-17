// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * キャレット隣の括弧とその対応括弧の位置を求める純ロジック。
 *
 * <p>{@link PumlSourcePanel} の対応括弧強調で使う。PlantUML は {@code package{}} /
 * クラス本体 / 複合状態 / JSON など入れ子の括弧が多いため、対応先を可視化して
 * 編集を助ける。ネストは深さで数える。</p>
 */
final class BracketMatcher {

    static final String OPEN = "({[";
    static final String CLOSE = ")}]";

    private BracketMatcher() {
    }

    /**
     * キャレットの直前/直後にある括弧と、その対応括弧の位置 {@code [open, close]} を返す。
     * どちらにも括弧が無い、または対応が見つからなければ null。
     */
    static int[] matchingBrackets(String text, int caret) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int n = text.length();
        if (caret > 0 && caret - 1 < n) {
            int[] m = matchFrom(text, caret - 1);
            if (m != null) {
                return m;
            }
        }
        if (caret >= 0 && caret < n) {
            return matchFrom(text, caret);
        }
        return null;
    }

    private static int[] matchFrom(String text, int at) {
        char c = text.charAt(at);
        int oi = OPEN.indexOf(c);
        if (oi >= 0) {
            int m = scan(text, at, CLOSE.charAt(oi), c, +1);
            return m < 0 ? null : new int[]{at, m};
        }
        int ci = CLOSE.indexOf(c);
        if (ci >= 0) {
            int m = scan(text, at, OPEN.charAt(ci), c, -1);
            return m < 0 ? null : new int[]{m, at};
        }
        return null;
    }

    /** {@code from} の括弧 {@code self} に対応する {@code target} を {@code dir} 方向に探す。 */
    private static int scan(String text, int from, char target, char self, int dir) {
        int depth = 0;
        for (int i = from; i >= 0 && i < text.length(); i += dir) {
            char c = text.charAt(i);
            if (c == self) {
                depth++;
            } else if (c == target) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
