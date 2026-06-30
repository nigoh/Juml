// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * MarkdownSettingsReport のユニットテスト。
 */
public class MarkdownSettingsReportTest {

    @Test
    public void emptyResultShowsNoDetectedMessage() {
        SettingsAnalysisResult result = new SettingsAnalysisResult();
        String md = MarkdownSettingsReport.render(result);
        assertTrue(md.contains("no SharedPreferences"));
    }

    @Test
    public void reportContainsKeyTable() {
        SettingsAnalysisResult result = new SettingsAnalysisResult();
        result.addCodeEntry(new SharedPreferencesEntry(
                "user_name", "String", "Guest", "user_prefs", false, "Profile.java", 42));
        result.addCodeEntry(new SharedPreferencesEntry(
                "user_name", "String", "", "user_prefs", true, "Settings.java", 88));
        String md = MarkdownSettingsReport.render(result);
        assertTrue(md.contains("user_name"));
        assertTrue(md.contains("Profile.java:42"));
        assertTrue(md.contains("Settings.java:88"));
    }

    @Test
    public void reportContainsStoreSection() {
        SettingsAnalysisResult result = new SettingsAnalysisResult();
        result.addCodeEntry(new SharedPreferencesEntry(
                "key1", "Boolean", "false", "app_prefs", false, "A.java", 10));
        String md = MarkdownSettingsReport.render(result);
        assertTrue(md.contains("app_prefs"));
        assertTrue(md.contains("SharedPreferences Stores"));
    }

    @Test
    public void reportContainsXmlSection() {
        SettingsAnalysisResult result = new SettingsAnalysisResult();
        result.addXmlEntry(new PreferenceXmlEntry(
                "notifications", "SwitchPreference", "true", "Notifications", "prefs.xml"));
        String md = MarkdownSettingsReport.render(result);
        assertTrue(md.contains("Preference XML Definitions"));
        assertTrue(md.contains("notifications"));
        assertTrue(md.contains("SwitchPreference"));
    }

    @Test
    public void readAndWriteAreDistinguished() {
        SettingsAnalysisResult result = new SettingsAnalysisResult();
        result.addCodeEntry(new SharedPreferencesEntry(
                "theme", "String", "light", "", false, "ThemeHelper.java", 15));
        result.addCodeEntry(new SharedPreferencesEntry(
                "theme", "String", "", "", true, "SettingsActivity.java", 60));
        String md = MarkdownSettingsReport.render(result);
        // Read At と Written At がそれぞれ含まれる
        assertTrue(md.contains("ThemeHelper.java:15"));
        assertTrue(md.contains("SettingsActivity.java:60"));
        assertFalse(md.contains("no SharedPreferences"));
    }
}
