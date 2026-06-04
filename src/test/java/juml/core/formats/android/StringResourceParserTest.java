// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * StringResourceParser のユニットテスト。
 */
public class StringResourceParserTest {

    private static final String STRINGS_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "  <string name=\"app_name\">My App</string>\n"
            + "  <string name=\"greeting\">Hello, World!</string>\n"
            + "  <string name=\"quoted\">\"  spaced  \"</string>\n"
            + "  <string name=\"escaped\">It\\'s a test</string>\n"
            + "  <string name=\"multiline\">line1\\nline2</string>\n"
            + "  <color name=\"primary\">#FF0000</color>\n"
            + "</resources>\n";

    @Test(expected = IllegalArgumentException.class)
    public void testNullXml() {
        StringResourceParser.parse(null);
    }

    @Test
    public void testParsesStringsOnly() {
        AndroidStringResources res = StringResourceParser.parse(STRINGS_XML);
        // <color> は無視され、<string> のみ取り込まれる
        assertEquals(5, res.getStrings().size());
        assertEquals("My App", res.getString("app_name"));
        assertEquals("Hello, World!", res.getString("greeting"));
        assertNull("color は文字列リソースに含めない", res.getString("primary"));
    }

    @Test
    public void testNormalizesQuotesAndEscapes() {
        AndroidStringResources res = StringResourceParser.parse(STRINGS_XML);
        // 前後の "..." は剥がされ、内部の連続空白は 1 つに畳まれる
        assertEquals("spaced", res.getString("quoted"));
        // \' は ' へ
        assertEquals("It's a test", res.getString("escaped"));
        // \n は空白へ畳まれる
        assertEquals("line1 line2", res.getString("multiline"));
    }

    @Test
    public void testInvalidXmlReturnsEmpty() {
        AndroidStringResources res = StringResourceParser.parse("<resources><string>");
        assertTrue(res.getStrings().isEmpty());
    }

    @Test
    public void testNormalizeValueHelper() {
        assertEquals("a b", StringResourceParser.normalizeValue("  a   b  "));
        assertEquals("keep", StringResourceParser.normalizeValue("\"keep\""));
        assertEquals("", StringResourceParser.normalizeValue(null));
    }
}
