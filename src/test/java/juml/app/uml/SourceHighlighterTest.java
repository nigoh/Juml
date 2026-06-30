// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SourceHighlighter#highlight} のトークン走査ロジックを検証する。
 *
 * <p>純粋な文字列走査でヘッドレス完結する。色はテーマ（light/dark）で変わるため、
 * 個別の定数値ではなく「キーワード色か/コメント色か/文字列色か」というカテゴリで判定する。</p>
 */
public class SourceHighlighterTest {

    private static boolean isKeyword(Color c) {
        return c.equals(SourceHighlighter.COL_KEYWORD) || c.equals(SourceHighlighter.COL_KEYWORD_DARK);
    }

    private static boolean isComment(Color c) {
        return c.equals(SourceHighlighter.COL_COMMENT) || c.equals(SourceHighlighter.COL_COMMENT_DARK);
    }

    private static boolean isString(Color c) {
        return c.equals(SourceHighlighter.COL_STRING) || c.equals(SourceHighlighter.COL_STRING_DARK);
    }

    private static boolean isAnnotation(Color c) {
        return c.equals(SourceHighlighter.COL_ANNOTATION)
                || c.equals(SourceHighlighter.COL_ANNOTATION_DARK);
    }

    private static boolean isNumber(Color c) {
        return c.equals(SourceHighlighter.COL_NUMBER) || c.equals(SourceHighlighter.COL_NUMBER_DARK);
    }

    /** 指定オフセットを覆い、述語に一致する色のスパンを 1 件返す（無ければ fail 用に null）。 */
    private static SourceHighlighter.Span spanCovering(List<SourceHighlighter.Span> spans, int offset) {
        for (SourceHighlighter.Span s : spans) {
            if (offset >= s.start && offset < s.start + s.length) {
                return s;
            }
        }
        return null;
    }

    @Test
    public void keyword_isColored() {
        String src = "class Foo";
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span kw = spanCovering(spans, 0); // "class"
        assertNotNull("class がスパン化されること", kw);
        assertEquals("class の開始位置", 0, kw.start);
        assertEquals("class の長さ", 5, kw.length);
        assertTrue("class はキーワード色であること", isKeyword(kw.color));
        // 識別子 Foo はキーワードではないので色付けされない
        assertEquals("非キーワード識別子は着色しないこと", null, spanCovering(spans, 6));
    }

    @Test
    public void lineComment_runsToEndOfLine() {
        String src = "int x; // hi\nint y;";
        int commentStart = src.indexOf("//");
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span cm = spanCovering(spans, commentStart);
        assertNotNull("行コメントがスパン化されること", cm);
        assertTrue("行コメント色であること", isComment(cm.color));
        assertEquals("行コメントは改行直前まで", "// hi".length(), cm.length);
    }

    @Test
    public void blockComment_isTerminated() {
        String src = "/* a */ x";
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span cm = spanCovering(spans, 0);
        assertNotNull(cm);
        assertTrue("ブロックコメント色であること", isComment(cm.color));
        assertEquals("/* a */ の 7 文字を覆うこと", 7, cm.length);
    }

    @Test
    public void unterminatedBlockComment_doesNotOverrun() {
        String src = "/* abc"; // 終端なし
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span cm = spanCovering(spans, 0);
        assertNotNull("終端なしブロックコメントも安全に終端まで覆うこと", cm);
        assertTrue(isComment(cm.color));
        assertEquals("末尾を超えないこと", src.length(), cm.start + cm.length);
    }

    @Test
    public void tripleQuotedString_isOneSpan() {
        String src = "x = \"\"\"abc\"\"\";";
        int q = src.indexOf("\"\"\"");
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span str = spanCovering(spans, q);
        assertNotNull("三連クオート文字列がスパン化されること", str);
        assertTrue("文字列色であること", isString(str.color));
        assertEquals("\"\"\"abc\"\"\" 全体を覆うこと", "\"\"\"abc\"\"\"".length(), str.length);
    }

    @Test
    public void unterminatedTripleQuote_doesNotOverrun() {
        String src = "\"\"\"abc"; // 終端なし
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span str = spanCovering(spans, 0);
        assertNotNull(str);
        assertEquals("末尾を超えないこと", src.length(), str.start + str.length);
    }

    @Test
    public void stringWithEscapedQuote_staysOneSpan() {
        String src = "\"a\\\"b\""; // "a\"b"
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span str = spanCovering(spans, 0);
        assertNotNull(str);
        assertTrue(isString(str.color));
        assertEquals("エスケープされたクオートで途切れないこと", src.length(), str.length);
    }

    @Test
    public void string_stopsAtNewline() {
        String src = "\"abc\nx";
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span str = spanCovering(spans, 0);
        assertNotNull(str);
        assertTrue("文字列は改行を越えないこと", str.start + str.length <= src.indexOf('\n') + 1);
    }

    @Test
    public void annotation_isColored() {
        String src = "@Override void f()";
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight(src, false);
        SourceHighlighter.Span an = spanCovering(spans, 0);
        assertNotNull(an);
        assertTrue("アノテーション色であること", isAnnotation(an.color));
        assertEquals("@Override を覆うこと", "@Override".length(), an.length);
    }

    @Test
    public void number_isColored_butNotInsideIdentifier() {
        List<SourceHighlighter.Span> numSpans = SourceHighlighter.highlight("x = 42;", false);
        SourceHighlighter.Span num = spanCovering(numSpans, 4); // "42"
        assertNotNull(num);
        assertTrue("数値リテラルが着色されること", isNumber(num.color));

        // 識別子内の数字 (a1) は数値として着色しない
        List<SourceHighlighter.Span> idSpans = SourceHighlighter.highlight("a1 = 0;", false);
        SourceHighlighter.Span notNum = spanCovering(idSpans, 1); // 'a1' の '1'
        boolean coloredAsNumber = notNum != null && isNumber(notNum.color);
        assertFalse("識別子内の数字は数値扱いしないこと", coloredAsNumber);
    }

    @Test
    public void emptyString_returnsNoSpans() {
        List<SourceHighlighter.Span> spans = SourceHighlighter.highlight("", false);
        assertTrue("空文字列はスパンなし", spans.isEmpty());
    }
}
