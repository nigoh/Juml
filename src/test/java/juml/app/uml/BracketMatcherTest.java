// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link BracketMatcher} の対応括弧探索を検証する純ロジックテスト (headless)。
 */
public class BracketMatcherTest {

    @Test
    public void matchesOpenBracketAfterCaret() {
        // "a{b}c" : キャレット 1 (a と { の間) → 直後の { と対応 } を返す。
        assertArrayEquals(new int[]{1, 3}, BracketMatcher.matchingBrackets("a{b}c", 1));
    }

    @Test
    public void matchesCloseBracketBeforeCaret() {
        // "a{b}c" : キャレット 4 (} の直後) → 直前の } と対応 { を返す。
        assertArrayEquals(new int[]{1, 3}, BracketMatcher.matchingBrackets("a{b}c", 4));
    }

    @Test
    public void respectsNesting() {
        // "{a{b}c}" : キャレット 0 → 外側 { と最外の } (index 6)。
        assertArrayEquals(new int[]{0, 6}, BracketMatcher.matchingBrackets("{a{b}c}", 0));
        // 内側の { (index 2) の直後 (caret 3) → 内側 } (index 4)。
        assertArrayEquals(new int[]{2, 4}, BracketMatcher.matchingBrackets("{a{b}c}", 3));
    }

    @Test
    public void unmatchedBracketReturnsNull() {
        assertNull("閉じられていない { は対応なし", BracketMatcher.matchingBrackets("{a", 1));
        assertNull("括弧の無い位置は対応なし", BracketMatcher.matchingBrackets("abc", 2));
    }

    @Test
    public void handlesParenthesesAndSquare() {
        assertArrayEquals(new int[]{0, 2}, BracketMatcher.matchingBrackets("(x)", 1));
        assertArrayEquals(new int[]{0, 2}, BracketMatcher.matchingBrackets("[y]", 1));
    }

    @Test
    public void nullAndEmptyAndBoundsAreSafe() {
        assertNull(BracketMatcher.matchingBrackets(null, 0));
        assertNull(BracketMatcher.matchingBrackets("", 0));
        assertNull(BracketMatcher.matchingBrackets("{}", 5));
    }
}
