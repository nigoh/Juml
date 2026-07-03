// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** {@link MarkdownRenderer} のサブセット変換検証。 */
public class MarkdownRendererTest {

    @Test
    public void rendersHeadingsBoldItalicCode() {
        String html = MarkdownRenderer.toHtml("# Title\nsome **bold** and *italic* and `code`");
        // 見出しレベルは意味論どおり (# -> h1)。表示サイズは wrapDocument の CSS で抑える。
        assertTrue(html.contains("<h1>Title</h1>"));
        assertTrue(html.contains("<b>bold</b>"));
        assertTrue(html.contains("<i>italic</i>"));
        assertTrue(html.contains("<code>code</code>"));
    }

    @Test
    public void mapsHeadingLevelsOneToThree() {
        assertTrue(MarkdownRenderer.toHtml("## H2").contains("<h2>H2</h2>"));
        assertTrue(MarkdownRenderer.toHtml("### H3").contains("<h3>H3</h3>"));
    }

    @Test
    public void escapesQuoteInLinkHref() {
        String html = MarkdownRenderer.toHtml("[x](https://e.com/?a=\"b\")");
        // href 属性値内の引用符を無害化し、属性が壊れないこと。
        assertFalse(html.contains("href=\"https://e.com/?a=\"b\"\""));
        assertTrue(html.contains("&quot;"));
    }

    @Test
    public void wrapDocumentEmbedsHeadingStyle() {
        String doc = MarkdownRenderer.wrapDocument("<h1>T</h1>", 0, 11);
        assertTrue(doc.startsWith("<html>"));
        assertTrue(doc.contains("h1{font-size:"));
    }

    @Test
    public void rendersBulletAndOrderedLists() {
        String ul = MarkdownRenderer.toHtml("- a\n- b");
        assertTrue(ul.contains("<ul>"));
        assertTrue(ul.contains("<li>a</li>"));
        String ol = MarkdownRenderer.toHtml("1. one\n2. two");
        assertTrue(ol.contains("<ol>"));
        assertTrue(ol.contains("<li>one</li>"));
    }

    @Test
    public void rendersLinks() {
        String html = MarkdownRenderer.toHtml("see [docs](https://example.com)");
        assertTrue(html.contains("<a href=\"https://example.com\">docs</a>"));
    }

    @Test
    public void escapesHtmlSpecialChars() {
        String html = MarkdownRenderer.toHtml("a < b && c > d");
        assertTrue(html.contains("&lt;"));
        assertTrue(html.contains("&gt;"));
        assertTrue(html.contains("&amp;"));
        assertFalse(html.contains("a < b"));
    }

    @Test
    public void handlesNullAndEmpty() {
        assertEquals("", MarkdownRenderer.toHtml(null));
        assertEquals("", MarkdownRenderer.toHtml(""));
    }

    /**
     * コードスパンの中身は Markdown 的にリテラル扱い。以前は生成した
     * {@code <code>} の中を後段の装飾規則が再処理し、コード内で太字/リンクが
     * 効いてしまっていた。
     */
    @Test
    public void codeSpanContentIsNotReDecorated() {
        String bold = MarkdownRenderer.toHtml("`a**b**c`");
        assertTrue("コード内は太字化されない: " + bold, bold.contains("<code>a**b**c</code>"));
        assertFalse("コード内に <b> が入ってはいけない: " + bold, bold.contains("<b>"));

        String link = MarkdownRenderer.toHtml("`[x](y)`");
        assertTrue("コード内はリンク化されない: " + link, link.contains("<code>[x](y)</code>"));
        assertFalse("コード内に <a が入ってはいけない: " + link, link.contains("<a "));
    }

    /** コードスパンの外側は通常どおり装飾される (プレースホルダの誤爆がない)。 */
    @Test
    public void decorationOutsideCodeStillWorks() {
        String html = MarkdownRenderer.toHtml("**b** `code` *i*");
        assertTrue(html.contains("<b>b</b>"));
        assertTrue(html.contains("<code>code</code>"));
        assertTrue(html.contains("<i>i</i>"));
    }

    /**
     * コードスパンのプレースホルダに使う私用領域文字 (U+E000/U+E001) が入力に
     * 紛れ込んでも、プレースホルダと衝突して誤ってコードスパンに置換されない。
     */
    @Test
    public void privateUseAreaCharsInInputDoNotCollideWithPlaceholders() {
        char e0 = '\uE000';
        char e1 = '\uE001';
        // U+E000 + "0" + U+E001 はプレースホルダと同形。除去されて素通しされるべき。
        String input = "before " + e0 + "0" + e1 + " `code` after";
        String html = MarkdownRenderer.toHtml(input);
        assertTrue("実コードスパンは正しく描画される",
                html.contains("<code>code</code>"));
        // PUA 文字は除去され、周囲テキストは保持される。
        assertTrue(html.contains("before"));
        assertTrue(html.contains("after"));
        assertFalse("U+E000 は出力に残らない", html.indexOf(e0) >= 0);
        assertFalse("U+E001 は出力に残らない", html.indexOf(e1) >= 0);
    }
}
