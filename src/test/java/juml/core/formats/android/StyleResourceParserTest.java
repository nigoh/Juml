// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * StyleResourceParser のユニットテスト。
 */
public class StyleResourceParserTest {

    private static final String STYLES_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<resources>\n"
            + "  <style name=\"AppTheme\" parent=\"Theme.Material\">\n"
            + "    <item name=\"colorPrimary\">@color/primary</item>\n"
            + "    <item name=\"android:windowBackground\">@color/bg</item>\n"
            + "  </style>\n"
            + "  <style name=\"AppTheme.Dialog\">\n"
            + "    <item name=\"android:windowIsFloating\">true</item>\n"
            + "  </style>\n"
            + "  <style name=\"Button.Primary\" parent=\"@style/Widget.Button\"/>\n"
            + "  <string name=\"ignored\">not a style</string>\n"
            + "</resources>\n";

    @Test(expected = IllegalArgumentException.class)
    public void testNullXml() {
        StyleResourceParser.parse(null);
    }

    @Test
    public void testParsesStylesOnly() {
        AndroidStyleResources res = StyleResourceParser.parse(STYLES_XML);
        assertEquals(3, res.getStyles().size());
        assertNotNull(res.getStyle("AppTheme"));
        assertNull("string は無視される", res.getStyle("ignored"));
    }

    @Test
    public void testExplicitParent() {
        AndroidStyleResources res = StyleResourceParser.parse(STYLES_XML);
        assertEquals("Theme.Material", res.getStyle("AppTheme").getParent());
        // parent="@style/Widget.Button" は短縮名へ
        assertEquals("Widget.Button", res.getStyle("Button.Primary").getParent());
    }

    @Test
    public void testImplicitDottedParent() {
        AndroidStyleResources res = StyleResourceParser.parse(STYLES_XML);
        // parent 属性なし → ドット記法で AppTheme.Dialog の親は AppTheme
        assertEquals("AppTheme", res.getStyle("AppTheme.Dialog").getParent());
    }

    @Test
    public void testItemsCollected() {
        AndroidStyleResources res = StyleResourceParser.parse(STYLES_XML);
        AndroidStyleResources.StyleDef app = res.getStyle("AppTheme");
        assertEquals(2, app.getItems().size());
        assertEquals("@color/primary", app.getItems().get("colorPrimary"));
    }

    @Test
    public void testResolveParentHelper() {
        assertEquals("Foo", StyleResourceParser.resolveParent("Foo.Bar", null));
        assertEquals("Base", StyleResourceParser.resolveParent("X", "@style/Base"));
        assertNull(StyleResourceParser.resolveParent("Top", null));
    }

    @Test
    public void testNonStyleXmlReturnsEmpty() {
        AndroidStyleResources res = StyleResourceParser.parse(
                "<resources><color name=\"x\">#fff</color></resources>");
        assertTrue(res.getStyles().isEmpty());
    }
}
