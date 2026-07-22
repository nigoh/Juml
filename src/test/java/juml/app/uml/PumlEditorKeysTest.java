// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlEditorKeys} の編集計算 (純関数, headless)。
 *
 * <p>Enter 自動インデント・自動閉じペア・行移動/複製/削除の {@link PumlEditorKeys.Edit}
 * 生成を、EOF (末尾改行なし) の境界を含めて固定する。</p>
 */
public class PumlEditorKeysTest {

    // -------------------------------------------------------------------------
    // 行ユーティリティ / ブロック判定
    // -------------------------------------------------------------------------

    @Test
    public void indentOf_returnsLeadingWhitespace() {
        assertEquals("", PumlEditorKeys.indentOf("class A"));
        assertEquals("  ", PumlEditorKeys.indentOf("  +field: int"));
        assertEquals("\t", PumlEditorKeys.indentOf("\tx"));
        assertEquals("", PumlEditorKeys.indentOf(""));
    }

    @Test
    public void opensBlock_braceAndKeywords() {
        assertTrue(PumlEditorKeys.opensBlock("class A {"));
        assertTrue(PumlEditorKeys.opensBlock("  package p {"));
        assertTrue(PumlEditorKeys.opensBlock("alt success"));
        assertTrue(PumlEditorKeys.opensBlock("loop 10 times"));
        assertTrue(PumlEditorKeys.opensBlock("if (x) then (yes)"));
        assertTrue(PumlEditorKeys.opensBlock("else (no)"));
        assertFalse(PumlEditorKeys.opensBlock("class A"));
        assertFalse(PumlEditorKeys.opensBlock("A --> B"));
        assertFalse(PumlEditorKeys.opensBlock("' alt comment"));
        assertFalse(PumlEditorKeys.opensBlock(""));
        // キーワードが行中の識別子の一部であってはならない (altitude は alt でない)。
        assertFalse(PumlEditorKeys.opensBlock("altitude x"));
    }

    // -------------------------------------------------------------------------
    // Enter 自動インデント
    // -------------------------------------------------------------------------

    @Test
    public void newlineAt_keepsIndent() {
        String text = "  class A\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.newlineAt(text, 9); // "  class A" の行末
        assertEquals("\n  ", e.replacement);
        assertEquals(9 + 3, e.selStart);
    }

    @Test
    public void newlineAt_indentsDeeperAfterBlockOpener() {
        String text = "alt ok\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.newlineAt(text, 6);
        assertEquals("\n" + PumlEditorKeys.INDENT, e.replacement);
    }

    @Test
    public void newlineAt_smartExpandsBetweenBraces() {
        String text = "class A {}";
        PumlEditorKeys.Edit e = PumlEditorKeys.newlineAt(text, 9); // { と } の間
        assertEquals("\n" + PumlEditorKeys.INDENT + "\n", e.replacement);
        // キャレットは中間行 (インデント済み) の末尾。
        assertEquals(9 + 1 + PumlEditorKeys.INDENT.length(), e.selStart);
    }

    @Test
    public void newlineAt_midLineUsesIndentOfTextBeforeCaret() {
        String text = "  foo bar\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.newlineAt(text, 5); // "  foo|bar"
        assertEquals("\n  ", e.replacement);
    }

    // -------------------------------------------------------------------------
    // 自動閉じペア / オーバータイプ
    // -------------------------------------------------------------------------

    @Test
    public void typedOpen_pairsAtLineEnd() {
        PumlEditorKeys.Edit e = PumlEditorKeys.typedOpen("note \n", 5, '(');
        assertEquals("()", e.replacement);
        assertEquals(6, e.selStart); // 括弧の間
    }

    @Test
    public void typedOpen_singleBeforeWord() {
        PumlEditorKeys.Edit e = PumlEditorKeys.typedOpen("foo", 0, '(');
        assertEquals("(", e.replacement);
    }

    @Test
    public void typedOpen_quoteOvertypesExistingQuote() {
        PumlEditorKeys.Edit e = PumlEditorKeys.typedOpen("\"label\"", 6, '"');
        assertEquals("", e.replacement);
        assertEquals(7, e.selStart);
    }

    @Test
    public void typedOpen_pairsAtEndOfText() {
        PumlEditorKeys.Edit e = PumlEditorKeys.typedOpen("x", 1, '{');
        assertEquals("{}", e.replacement);
    }

    @Test
    public void typedClose_overtypesMatchingChar() {
        PumlEditorKeys.Edit e = PumlEditorKeys.typedClose("()", 1, ')');
        assertEquals("", e.replacement);
        assertEquals(2, e.selStart);
    }

    @Test
    public void typedClose_insertsWhenNoMatch() {
        PumlEditorKeys.Edit e = PumlEditorKeys.typedClose("(x", 2, ')');
        assertEquals(")", e.replacement);
    }

    // -------------------------------------------------------------------------
    // 行移動
    // -------------------------------------------------------------------------

    private static String applied(String text, PumlEditorKeys.Edit e) {
        return text.substring(0, e.start) + e.replacement + text.substring(e.end);
    }

    @Test
    public void moveLines_upSwapsWithPreviousLine() {
        String text = "a\nb\nc\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.moveLines(text, 2, 2, true); // b の行
        assertEquals("b\na\nc\n", applied(text, e));
        assertEquals(0, e.selStart); // 選択 (キャレット) はブロックに追従して上へ
    }

    @Test
    public void moveLines_downSwapsWithNextLine() {
        String text = "a\nb\nc\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.moveLines(text, 0, 0, false); // a の行
        assertEquals("b\na\nc\n", applied(text, e));
        assertEquals(2, e.selStart);
    }

    @Test
    public void moveLines_upAtFirstLineIsNoop() {
        assertNull(PumlEditorKeys.moveLines("a\nb\n", 0, 0, true));
    }

    @Test
    public void moveLines_downAtLastLineIsNoop() {
        assertNull(PumlEditorKeys.moveLines("a\nb", 3, 3, false));
        assertNull(PumlEditorKeys.moveLines("a\nb\n", 2, 2, false));
    }

    @Test
    public void moveLines_upWithEofLineWithoutNewline() {
        String text = "a\nb";
        PumlEditorKeys.Edit e = PumlEditorKeys.moveLines(text, 2, 2, true); // b (EOF 行)
        assertEquals("b\na", applied(text, e));
    }

    @Test
    public void moveLines_downOntoEofLineWithoutNewline() {
        String text = "a\nb";
        PumlEditorKeys.Edit e = PumlEditorKeys.moveLines(text, 0, 0, false); // a の行
        assertEquals("b\na", applied(text, e));
    }

    @Test
    public void moveLines_multiLineSelectionMovesBlock() {
        String text = "a\nb\nc\nd\n";
        // b..c を選択 (選択終端が c 行の途中)
        PumlEditorKeys.Edit e = PumlEditorKeys.moveLines(text, 2, 5, false);
        assertEquals("a\nd\nb\nc\n", applied(text, e));
    }

    @Test
    public void moveLines_selectionEndingAtLineStartExcludesThatLine() {
        String text = "a\nb\nc\n";
        // 選択が c の行頭ちょうどで終わる → 対象は b 行のみ
        PumlEditorKeys.Edit e = PumlEditorKeys.moveLines(text, 2, 4, true);
        assertEquals("b\na\nc\n", applied(text, e));
    }

    // -------------------------------------------------------------------------
    // 行複製 / 行削除
    // -------------------------------------------------------------------------

    @Test
    public void duplicateLines_insertsCopyBelow() {
        String text = "a\nb\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.duplicateLines(text, 0, 0);
        assertEquals("a\na\nb\n", applied(text, e));
        assertEquals(2, e.selStart); // 選択は複製側へ
    }

    @Test
    public void duplicateLines_atEofWithoutNewline() {
        String text = "a\nb";
        PumlEditorKeys.Edit e = PumlEditorKeys.duplicateLines(text, 2, 2);
        assertEquals("a\nb\nb", applied(text, e));
        assertEquals(4, e.selStart);
    }

    @Test
    public void deleteLines_removesWholeLine() {
        String text = "a\nb\nc\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.deleteLines(text, 2, 2); // b の行
        assertEquals("a\nc\n", applied(text, e));
    }

    @Test
    public void deleteLines_lastLineWithoutNewlineRemovesPrecedingBreak() {
        String text = "a\nb";
        PumlEditorKeys.Edit e = PumlEditorKeys.deleteLines(text, 2, 2);
        assertEquals("a", applied(text, e));
    }

    @Test
    public void deleteLines_multiLineSelection() {
        String text = "a\nb\nc\nd\n";
        PumlEditorKeys.Edit e = PumlEditorKeys.deleteLines(text, 2, 5); // b..c
        assertEquals("a\nd\n", applied(text, e));
    }
}
