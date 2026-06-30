// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 実プロジェクトで遭遇しがちな AndroidManifest.xml の難しめのケース。
 */
public class AndroidManifestParserStressTest {

    private static final String NS = "xmlns:android=\"http://schemas.android.com/apk/res/android\"";
    private static final String TOOLS = "xmlns:tools=\"http://schemas.android.com/tools\"";

    @Test
    public void testNoApplicationTag() {
        // ライブラリモジュールの manifest は <application> が無いことがある
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <uses-permission android:name=\"x.y.Z\"/>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals("p", info.getPackageName());
        assertEquals(1, info.getPermissions().size());
        assertTrue(info.getActivities().isEmpty());
    }

    @Test
    public void testEmptyApplicationTag() {
        String xml = "<manifest " + NS + " package=\"p\"><application/></manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertTrue(info.getActivities().isEmpty());
    }

    @Test
    public void testNoPackageAttribute() {
        // 新しい Android プロジェクトは AndroidManifest に package を持たないことが多い
        // (build.gradle.kts の namespace で代替)
        String xml = "<manifest " + NS + "><application>"
                + "<activity android:name=\".MainActivity\"/></application></manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals("", info.getPackageName());
        // package が無いので相対名は解決されず . で始まる
        assertEquals(".MainActivity", info.getActivities().get(0).getName());
    }

    @Test
    public void testToolsNamespaceIgnored() {
        // tools: namespace の属性 (tools:targetApi, tools:replace 等) があっても問題ない
        String xml = "<manifest " + NS + " " + TOOLS + " package=\"p\">\n"
                + "  <application tools:targetApi=\"34\">\n"
                + "    <activity android:name=\".A\" tools:replace=\"android:exported\"\n"
                + "              android:exported=\"true\"/>\n"
                + "  </application>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(1, info.getActivities().size());
        assertEquals(Boolean.TRUE, info.getActivities().get(0).getExported());
    }

    @Test
    public void testQueriesElementSkipped() {
        // Android 11+ の <queries> 要素 (action/package/intent を宣言) は components ではない
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <queries>\n"
                + "    <intent>\n"
                + "      <action android:name=\"android.intent.action.SEND\"/>\n"
                + "    </intent>\n"
                + "    <package android:name=\"com.example\"/>\n"
                + "  </queries>\n"
                + "  <application/>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        // queries 内の action はコンポーネントの intent-filter ではない (今回は無視される)
        assertTrue(info.getActivities().isEmpty());
        assertTrue(info.getServices().isEmpty());
    }

    @Test
    public void testUsesPermissionUnderQueriesNotCollected() {
        // <queries> 配下の uses-permission は「要求権限」ではないので収集しない。
        // <manifest> 直下の uses-permission のみが対象。
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <queries>\n"
                + "    <uses-permission android:name=\"perm.IN_QUERIES\"/>\n"
                + "  </queries>\n"
                + "  <uses-permission android:name=\"perm.TOP_LEVEL\"/>\n"
                + "  <application/>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(1, info.getPermissions().size());
        assertEquals("perm.TOP_LEVEL", info.getPermissions().get(0).getName());
    }

    @Test
    public void testActivityAliasParsedAsActivity() {
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <application>\n"
                + "    <activity-alias android:name=\".Alias\" android:targetActivity=\".A\"/>\n"
                + "    <activity android:name=\".A\"/>\n"
                + "  </application>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(2, info.getActivities().size());
    }

    @Test
    public void testComplexIntentFilter() {
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <application>\n"
                + "    <activity android:name=\".Deep\">\n"
                + "      <intent-filter android:priority=\"10\">\n"
                + "        <action android:name=\"android.intent.action.VIEW\"/>\n"
                + "        <category android:name=\"android.intent.category.DEFAULT\"/>\n"
                + "        <category android:name=\"android.intent.category.BROWSABLE\"/>\n"
                + "        <data android:scheme=\"https\" android:mimeType=\"text/html\"/>\n"
                + "        <data android:scheme=\"app\"/>\n"
                + "      </intent-filter>\n"
                + "    </activity>\n"
                + "  </application>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        AndroidComponentInfo c = info.getActivities().get(0);
        AndroidIntentFilter f = c.getIntentFilters().get(0);
        assertEquals(Integer.valueOf(10), f.getPriority());
        assertEquals(2, f.getCategories().size());
        assertTrue(f.getDataSchemes().contains("https"));
        assertTrue(f.getDataSchemes().contains("app"));
        assertTrue(f.getDataMimeTypes().contains("text/html"));
        assertFalse(f.isLauncher());
    }

    @Test
    public void testProviderWithAuthorities() {
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <application>\n"
                + "    <provider\n"
                + "        android:name=\".MyProvider\"\n"
                + "        android:authorities=\"p.provider;p.provider.alt\"\n"
                + "        android:exported=\"false\"/>\n"
                + "  </application>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        AndroidComponentInfo c = info.getProviders().get(0);
        assertEquals("p.provider;p.provider.alt", c.getAuthorities());
        assertEquals(Boolean.FALSE, c.getExported());
    }

    @Test
    public void testReceiverWithMultipleIntentFilters() {
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <application>\n"
                + "    <receiver android:name=\".R\">\n"
                + "      <intent-filter>"
                + "<action android:name=\"a1\"/></intent-filter>\n"
                + "      <intent-filter>"
                + "<action android:name=\"a2\"/></intent-filter>\n"
                + "    </receiver>\n"
                + "  </application>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        AndroidComponentInfo c = info.getReceivers().get(0);
        assertEquals(2, c.getIntentFilters().size());
    }

    @Test
    public void testCommentsInManifest() {
        String xml = "<!-- top -->\n<manifest " + NS + " package=\"p\">\n"
                + "  <!-- inside -->\n"
                + "  <application>\n"
                + "    <activity android:name=\".A\"/>\n"
                + "  </application>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals(1, info.getActivities().size());
    }

    @Test
    public void testManifestWithUnicodeStrings() {
        // 多言語の string 参照 (android:label) を含む。属性は文字列としてそのまま保持
        String xml = "<manifest " + NS + " package=\"p\">\n"
                + "  <application android:label=\"アプリ\">\n"
                + "    <activity android:name=\".A\"/>\n"
                + "  </application>\n"
                + "</manifest>";
        AndroidManifestInfo info = AndroidManifestParser.parse(xml);
        assertEquals("アプリ", info.getApplicationLabel());
    }

    @Test
    public void testSaxFatalErrorRoutedToListener() {
        List<String> log = new ArrayList<>();
        AndroidManifestParser.parse("not xml at all <<<", ErrorListener.collecting(log));
        boolean found = false;
        for (String s : log) {
            // System.err への直書きが抑制され、listener にも届く
            if (s.contains("manifest parse failed")) {
                found = true;
            }
        }
        assertTrue("expected parse failure routed to listener: " + log, found);
    }
}
