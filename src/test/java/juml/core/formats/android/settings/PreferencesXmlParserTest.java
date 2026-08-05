// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PreferencesXmlParser のユニットテスト。
 */
public class PreferencesXmlParserTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private final PreferencesXmlParser parser = new PreferencesXmlParser();

    private static final String SIMPLE_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <SwitchPreference\n"
            + "        android:key=\"notifications_enabled\"\n"
            + "        android:title=\"Notifications\"\n"
            + "        android:defaultValue=\"true\" />\n"
            + "    <EditTextPreference\n"
            + "        android:key=\"username\"\n"
            + "        android:title=\"User Name\"\n"
            + "        android:defaultValue=\"\" />\n"
            + "</PreferenceScreen>\n";

    @Test
    public void parsesKeysFromXml() {
        List<PreferenceXmlEntry> entries = parser.parse(SIMPLE_XML, "preferences.xml");
        assertEquals(2, entries.size());
    }

    @Test
    public void parsesSwitchPreferenceAttributes() {
        List<PreferenceXmlEntry> entries = parser.parse(SIMPLE_XML, "preferences.xml");
        PreferenceXmlEntry e = entries.get(0);
        assertEquals("notifications_enabled", e.key);
        assertEquals("SwitchPreference", e.elementType);
        assertEquals("true", e.defaultValue);
        assertEquals("Notifications", e.title);
    }

    @Test
    public void parsesEditTextPreference() {
        List<PreferenceXmlEntry> entries = parser.parse(SIMPLE_XML, "preferences.xml");
        PreferenceXmlEntry e = entries.get(1);
        assertEquals("username", e.key);
        assertEquals("EditTextPreference", e.elementType);
    }

    @Test
    public void returnsEmptyOnInvalidXml() {
        List<PreferenceXmlEntry> entries = parser.parse("not-valid-xml", "bad.xml");
        assertTrue(entries.isEmpty());
    }

    @Test
    public void parsesNestedPreferences() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <PreferenceCategory android:title=\"Category\">\n"
                + "        <CheckBoxPreference android:key=\"opt_a\" android:defaultValue=\"false\" />\n"
                + "    </PreferenceCategory>\n"
                + "</PreferenceScreen>\n";
        List<PreferenceXmlEntry> entries = parser.parse(xml, "prefs.xml");
        assertEquals(1, entries.size());
        assertEquals("opt_a", entries.get(0).key);
    }

    @Test
    public void parsesPreferenceXmlFromQualifiedDirectory() throws Exception {
        // res/xml-v21/ のような修飾子付きディレクトリ配下の Preference XML も収集する
        File res = tmp.newFolder("res");
        File xmlDir = new File(res, "xml-v21");
        assertTrue(xmlDir.mkdirs());
        Files.write(new File(xmlDir, "prefs.xml").toPath(),
                SIMPLE_XML.getBytes(StandardCharsets.UTF_8));

        List<PreferenceXmlEntry> entries = parser.analyzeProject(tmp.getRoot());
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> "notifications_enabled".equals(e.key)));
    }

    /**
     * 回帰: AndroidX の {@code *PreferenceCompat} も設定項目として拾うこと。
     *
     * <p>タグ名が {@code "Preference"} で終わるかだけを見ていたため、Android Studio の
     * Settings Activity テンプレートが生成する {@code SwitchPreferenceCompat} の
     * {@code android:key} が黙って落ち、レポートの項目数も表も 1 件少なくなっていた。
     * 「未対応なので飛ばした」という通知も出ないので、利用者は設定項目を見落とす。</p>
     */
    @Test
    public void parsesAndroidXCompatPreferences() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <SwitchPreferenceCompat android:key=\"notifications_enabled\"\n"
                + "        android:defaultValue=\"true\" android:title=\"Enable notifications\" />\n"
                + "    <CheckBoxPreference android:key=\"sync_wifi_only\" />\n"
                + "    <EditTextPreference android:key=\"user_name\" />\n"
                + "</PreferenceScreen>\n";

        List<PreferenceXmlEntry> entries = parser.parse(xml, "root_preferences.xml");

        assertEquals("Compat も含めて 3 件拾うこと", 3, entries.size());
        assertTrue("SwitchPreferenceCompat の key が含まれること",
                entries.stream().anyMatch(e -> "notifications_enabled".equals(e.key)));
    }

    /** 非退行: 容器要素 (PreferenceScreen / PreferenceCategory) は項目として数えないこと。 */
    @Test
    public void containerElementsAreNotCountedAsEntries() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "        android:key=\"screen_root\">\n"
                + "    <PreferenceCategory android:key=\"cat_general\" android:title=\"General\">\n"
                + "        <SwitchPreferenceCompat android:key=\"only_real_one\" />\n"
                + "    </PreferenceCategory>\n"
                + "</PreferenceScreen>\n";

        List<PreferenceXmlEntry> entries = parser.parse(xml, "root_preferences.xml");

        assertEquals("実際の設定項目 1 件だけであること: " + entries.size(), 1, entries.size());
        assertEquals("only_real_one", entries.get(0).key);
    }
}
