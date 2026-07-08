// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * JavaCommentScanner#cleanText / stripInlineTags のユニットテスト。
 */
public class JavaCommentScannerTest {

    private static JavaCommentScanner.Comment javadoc(String body) {
        return new JavaCommentScanner.Comment(0, 0, 1,
                JavaCommentScanner.Kind.JAVADOC, body);
    }

    @Test
    public void stripsSingleLineInlineTags() {
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * Uses {@code foo} and {@link Bar} here.\n"));
        assertEquals("Uses foo and Bar here.", cleaned);
    }

    @Test
    public void resolvesInlineTagSpanningTwoLines() {
        // {@link が行末で開き、次行で閉じる JavaDoc でも残骸 ({@link) を残さない
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * Called from the calling {@link\n"
                + " * Activity} now.\n"));
        assertFalse("must not leave {@ residue", cleaned.contains("{@"));
        assertTrue(cleaned.contains("Activity"));
    }

    @Test
    public void linkRefWithMethodSignatureIsNotMisSplit() {
        // {@link Foo#bar(int, String[])} のように参照内に空白/カンマがあっても壊さない
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * See {@link Foo#bar(int, String[])} for details.\n"));
        assertFalse(cleaned.contains("{@"));
        assertTrue(cleaned, cleaned.contains("bar(int, String[])"));
        assertTrue(cleaned, cleaned.contains("for details."));
    }

    // ------------ firstSentence: 折り返し文の途中省略を防ぐ ------------

    @Test
    public void firstSentenceJoinsWrappedFirstSentence() {
        // JavaDoc の説明文がソース上で折り返されていても、firstLine のように
        // 途中で切らず、句点までを 1 行へ復元する
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * ユーザー入力を検証して\n"
                + " * データベースに保存する。\n"
                + " * 2 文目は含めない。\n"));
        assertEquals("ユーザー入力を検証して データベースに保存する。",
                JavaCommentScanner.firstSentence(cleaned));
        // 従来の firstLine は途中で切れてしまう (回帰の対比)
        assertEquals("ユーザー入力を検証して", JavaCommentScanner.firstLine(cleaned));
    }

    @Test
    public void firstSentenceStopsAtEnglishPeriod() {
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * Validate the input\n"
                + " * and persist it. Then notify.\n"));
        assertEquals("Validate the input and persist it.",
                JavaCommentScanner.firstSentence(cleaned));
    }

    @Test
    public void firstSentenceKeepsDecimalPointIntact() {
        // 小数点 (3.14) を文末と誤検出しない
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * Pi is about 3.14 here.\n"));
        assertEquals("Pi is about 3.14 here.",
                JavaCommentScanner.firstSentence(cleaned));
    }

    @Test
    public void firstSentenceReturnsWholeTextWhenNoTerminator() {
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * 説明文に句点が無い\n"
                + " * 折り返しコメント\n"));
        assertEquals("説明文に句点が無い 折り返しコメント",
                JavaCommentScanner.firstSentence(cleaned));
    }

    @Test
    public void firstSentenceHandlesNullAndEmpty() {
        assertEquals("", JavaCommentScanner.firstSentence(null));
        assertEquals("", JavaCommentScanner.firstSentence(""));
    }
}
