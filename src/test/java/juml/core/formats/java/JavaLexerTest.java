// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * JavaLexer のユニットテスト。
 */
public class JavaLexerTest {

    private List<JavaToken> tokenize(String src) {
        return new JavaLexer(src).tokenize();
    }

    private void assertToken(JavaToken t, JavaToken.Type type, String text) {
        assertEquals("type for " + t.text, type, t.type);
        assertEquals(text, t.text);
    }

    @Test
    public void testEmpty() {
        List<JavaToken> toks = tokenize("");
        assertEquals(1, toks.size());
        assertEquals(JavaToken.Type.EOF, toks.get(0).type);
    }

    @Test
    public void testIdentifier() {
        List<JavaToken> toks = tokenize("hello");
        assertEquals(2, toks.size());
        assertToken(toks.get(0), JavaToken.Type.IDENT, "hello");
    }

    @Test
    public void testIdentifierWithUnderscore() {
        List<JavaToken> toks = tokenize("_my$var123");
        assertToken(toks.get(0), JavaToken.Type.IDENT, "_my$var123");
    }

    @Test
    public void testNumber() {
        List<JavaToken> toks = tokenize("123 1.5 0xFF 1L 1_000");
        assertToken(toks.get(0), JavaToken.Type.NUMBER, "123");
        assertToken(toks.get(1), JavaToken.Type.NUMBER, "1.5");
        assertToken(toks.get(2), JavaToken.Type.NUMBER, "0xFF");
        assertToken(toks.get(3), JavaToken.Type.NUMBER, "1L");
        assertToken(toks.get(4), JavaToken.Type.NUMBER, "1_000");
    }

    @Test
    public void testScientificNotationWithNegativeExponent() {
        // 1.5e-10 は 1 トークンとして読み取られるべき ([NUMBER:1.5e][OP:-][NUMBER:10] に
        // 分割されないことを確認する)
        List<JavaToken> toks = tokenize("1.5e-10");
        assertEquals("1.5e-10 は NUMBER トークン 1 個 + EOF のはず", 2, toks.size());
        assertToken(toks.get(0), JavaToken.Type.NUMBER, "1.5e-10");
    }

    @Test
    public void testScientificNotationWithPositiveExponent() {
        // 3.0E+2 は 1 トークンとして読み取られるべき
        List<JavaToken> toks = tokenize("3.0E+2");
        assertEquals("3.0E+2 は NUMBER トークン 1 個 + EOF のはず", 2, toks.size());
        assertToken(toks.get(0), JavaToken.Type.NUMBER, "3.0E+2");
    }

    @Test
    public void testScientificNotationInExpression() {
        // 式中での科学表記: a * 1.5e-10 で演算子 * と数値が正しく分離できるか
        List<JavaToken> toks = tokenize("a * 1.5e-10");
        assertEquals("a, *, 1.5e-10, EOF の 4 トークンのはず", 4, toks.size());
        assertToken(toks.get(0), JavaToken.Type.IDENT, "a");
        assertToken(toks.get(2), JavaToken.Type.NUMBER, "1.5e-10");
    }

    @Test
    public void testStringLiteral() {
        List<JavaToken> toks = tokenize("\"hello\\nworld\"");
        assertToken(toks.get(0), JavaToken.Type.STRING, "\"hello\\nworld\"");
    }

    @Test
    public void testCharLiteral() {
        List<JavaToken> toks = tokenize("'a' '\\n' '{'");
        assertToken(toks.get(0), JavaToken.Type.CHAR, "'a'");
        assertToken(toks.get(1), JavaToken.Type.CHAR, "'\\n'");
        assertToken(toks.get(2), JavaToken.Type.CHAR, "'{'");
    }

    @Test
    public void testLineComment() {
        List<JavaToken> toks = tokenize("a // comment\nb");
        assertEquals(3, toks.size()); // a, b, EOF
        assertToken(toks.get(0), JavaToken.Type.IDENT, "a");
        assertToken(toks.get(1), JavaToken.Type.IDENT, "b");
    }

    @Test
    public void testBlockComment() {
        List<JavaToken> toks = tokenize("a /* multi\nline */ b");
        assertEquals(3, toks.size());
        assertToken(toks.get(0), JavaToken.Type.IDENT, "a");
        assertToken(toks.get(1), JavaToken.Type.IDENT, "b");
    }

    @Test
    public void testPunctuation() {
        List<JavaToken> toks = tokenize("(){}[];,.");
        assertToken(toks.get(0), JavaToken.Type.PUNCT, "(");
        assertToken(toks.get(1), JavaToken.Type.PUNCT, ")");
        assertToken(toks.get(2), JavaToken.Type.PUNCT, "{");
        assertToken(toks.get(3), JavaToken.Type.PUNCT, "}");
        assertToken(toks.get(4), JavaToken.Type.PUNCT, "[");
        assertToken(toks.get(5), JavaToken.Type.PUNCT, "]");
        assertToken(toks.get(6), JavaToken.Type.PUNCT, ";");
        assertToken(toks.get(7), JavaToken.Type.PUNCT, ",");
        assertToken(toks.get(8), JavaToken.Type.PUNCT, ".");
    }

    @Test
    public void testMultiCharOperators() {
        List<JavaToken> toks = tokenize("== != <= >= && || ++ -- += -> ::");
        assertToken(toks.get(0), JavaToken.Type.OP, "==");
        assertToken(toks.get(1), JavaToken.Type.OP, "!=");
        assertToken(toks.get(2), JavaToken.Type.OP, "<=");
        assertToken(toks.get(3), JavaToken.Type.OP, ">=");
        assertToken(toks.get(4), JavaToken.Type.OP, "&&");
        assertToken(toks.get(5), JavaToken.Type.OP, "||");
        assertToken(toks.get(6), JavaToken.Type.OP, "++");
        assertToken(toks.get(7), JavaToken.Type.OP, "--");
        assertToken(toks.get(8), JavaToken.Type.OP, "+=");
        assertToken(toks.get(9), JavaToken.Type.OP, "->");
        assertToken(toks.get(10), JavaToken.Type.OP, "::");
    }

    @Test
    public void testShiftOperators() {
        List<JavaToken> toks = tokenize(">> >>> << >>= <<=");
        assertToken(toks.get(0), JavaToken.Type.OP, ">>");
        assertToken(toks.get(1), JavaToken.Type.OP, ">>>");
        assertToken(toks.get(2), JavaToken.Type.OP, "<<");
        assertToken(toks.get(3), JavaToken.Type.OP, ">>=");
        assertToken(toks.get(4), JavaToken.Type.OP, "<<=");
    }

    @Test
    public void testLineNumbers() {
        List<JavaToken> toks = tokenize("a\nb\nc");
        assertEquals(1, toks.get(0).line);
        assertEquals(2, toks.get(1).line);
        assertEquals(3, toks.get(2).line);
    }

    @Test
    public void testPositions() {
        String src = "hello world";
        List<JavaToken> toks = tokenize(src);
        JavaToken first = toks.get(0);
        JavaToken second = toks.get(1);
        assertEquals(0, first.start);
        assertEquals(5, first.end);
        assertEquals("hello", src.substring(first.start, first.end));
        assertEquals("world", src.substring(second.start, second.end));
    }

    @Test
    public void testIsKw() {
        JavaToken kw = new JavaToken(JavaToken.Type.IDENT, "if", 1, 0, 2);
        assertTrue(kw.isKw("if"));
        assertFalse(kw.isKw("while"));
        JavaToken op = new JavaToken(JavaToken.Type.OP, "==", 1, 0, 2);
        assertFalse(op.isKw("=="));
    }

    @Test
    public void testIs() {
        JavaToken t = new JavaToken(JavaToken.Type.PUNCT, "{", 1, 0, 1);
        assertTrue(t.is("{"));
        assertFalse(t.is("}"));
    }

    @Test
    public void testUnterminatedStringRecovery() {
        // 改行で String を終了させて続行できることを確認
        List<JavaToken> toks = tokenize("\"oops\nb");
        // EOF より前に少なくとも 1 トークン
        assertTrue(toks.size() >= 2);
    }

    @Test
    public void testTextBlockSingleLine() {
        String src = "\"\"\"hello\"\"\"";
        List<JavaToken> toks = tokenize(src);
        assertToken(toks.get(0), JavaToken.Type.STRING, src);
        assertEquals(JavaToken.Type.EOF, toks.get(1).type);
    }

    @Test
    public void testTextBlockMultiline() {
        String src = "\"\"\"\n  line1\n  line2\n  \"\"\"";
        List<JavaToken> toks = tokenize(src);
        assertToken(toks.get(0), JavaToken.Type.STRING, src);
        assertEquals(JavaToken.Type.EOF, toks.get(1).type);
    }

    @Test
    public void testTextBlockWithEmbeddedQuote() {
        // テキストブロック内の "" は終端ではない (3 連続 " のみ終端)
        String src = "\"\"\"he said \"hi\" here\"\"\"";
        List<JavaToken> toks = tokenize(src);
        assertToken(toks.get(0), JavaToken.Type.STRING, src);
    }

    @Test
    public void testTextBlockDoesNotBreakSubsequentTokens() {
        String src = "a = \"\"\"\n  body\n  \"\"\"; b";
        List<JavaToken> toks = tokenize(src);
        assertToken(toks.get(0), JavaToken.Type.IDENT, "a");
        assertToken(toks.get(1), JavaToken.Type.OP, "=");
        assertToken(toks.get(2), JavaToken.Type.STRING, "\"\"\"\n  body\n  \"\"\"");
        assertToken(toks.get(3), JavaToken.Type.PUNCT, ";");
        assertToken(toks.get(4), JavaToken.Type.IDENT, "b");
    }

    @Test
    public void testExpandUnicodeEscapeSimple() {
        // A == 'A'
        assertEquals("A", JavaLexer.expandUnicodeEscapes("\\u0041"));
    }

    @Test
    public void testExpandUnicodeEscapeInIdentifier() {
        // class A {} → class A {}
        assertEquals("class A {}", JavaLexer.expandUnicodeEscapes("class \\u0041 {}"));
    }

    @Test
    public void testExpandUnicodeEscapeMultipleUs() {
        // \uuu0041 も有効 (UnicodeMarker: u {u})
        assertEquals("A", JavaLexer.expandUnicodeEscapes("\\uuu0041"));
    }

    @Test
    public void testExpandUnicodeEscapeEscapedBackslash() {
        // \\u0041 は \\ + u0041 のままで、エスケープ展開されない
        assertEquals("\\\\u0041", JavaLexer.expandUnicodeEscapes("\\\\u0041"));
    }

    @Test
    public void testExpandUnicodeEscapeOddBackslashes() {
        // \\A → \\ + A (3 個の \\ は 2 個のリテラル + 1 個のエスケープ開始)
        assertEquals("\\\\A", JavaLexer.expandUnicodeEscapes("\\\\\\u0041"));
    }

    @Test
    public void testExpandUnicodeEscapeNoEscape() {
        assertEquals("hello", JavaLexer.expandUnicodeEscapes("hello"));
    }

    @Test
    public void testExpandUnicodeEscapeInvalid() {
        // 妥当な 4 桁の 16 進数でない場合は展開しない
        assertEquals("\\u123z", JavaLexer.expandUnicodeEscapes("\\u123z"));
    }

    @Test
    public void testGetSource() {
        JavaLexer lex = new JavaLexer("abc");
        lex.tokenize();
        assertEquals("abc", lex.getSource());
    }

    // ---- テキストブロック境界条件テスト ----

    /**
     * 空テキストブロック {@code """""" } が 1 つの STRING トークンとして
     * 正しくトークン化されることを確認する。
     * (開始 """ の直後にすぐ終端 """ が続く)
     */
    @Test
    public void testTextBlockEmpty() {
        String src = "\"\"\"\"\"\"";
        List<JavaToken> toks = tokenize(src);
        // 空テキストブロック: STRING + EOF の 2 トークン
        assertEquals("empty text block should yield exactly 2 tokens", 2, toks.size());
        assertToken(toks.get(0), JavaToken.Type.STRING, src);
        assertEquals(JavaToken.Type.EOF, toks.get(1).type);
    }

    /**
     * ソースがちょうど閉じ {@code """} で終わるケース。
     * {@code readTextBlock} の境界判定 ({@code pos + 2 < len}) が終端 {@code """}
     * を取りこぼさないことを確認する。
     */
    @Test
    public void testTextBlockEndsAtExactEof() {
        // ソースの最後の 3 文字がちょうど """ となるケース
        String src = "\"\"\"hello\"\"\"";
        List<JavaToken> toks = tokenize(src);
        assertEquals("text block at EOF should yield exactly 2 tokens", 2, toks.size());
        assertToken(toks.get(0), JavaToken.Type.STRING, src);
        assertEquals(JavaToken.Type.EOF, toks.get(1).type);
    }

    /**
     * 終端 {@code """} が存在しない (ソースが途中で終わる) ケース。
     * EOF でフォールバックし、クラッシュなく残りを 1 STRING トークンとして返すことを確認する。
     */
    @Test
    public void testTextBlockUnterminatedRecovery() {
        String src = "\"\"\"unterminated";
        List<JavaToken> toks = tokenize(src);
        // 回復パスを通って STRING + EOF の 2 トークン以上が返ること (クラッシュしないこと)
        assertTrue("unterminated text block should produce at least 2 tokens", toks.size() >= 2);
        assertEquals(JavaToken.Type.STRING, toks.get(0).type);
        // 回復後のトークンがソース末尾まで span していること
        assertEquals(src, toks.get(0).text);
    }
}
