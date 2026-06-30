// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SharedPreferencesScanner} と {@link PreferencesXmlParser} の集計結果。
 */
public final class SettingsAnalysisResult {

    private final List<SharedPreferencesEntry> codeEntries = new ArrayList<>();
    private final List<PreferenceXmlEntry> xmlEntries = new ArrayList<>();

    public void addCodeEntry(SharedPreferencesEntry e) {
        codeEntries.add(e);
    }

    public void addXmlEntry(PreferenceXmlEntry e) {
        xmlEntries.add(e);
    }

    public List<SharedPreferencesEntry> getCodeEntries() {
        return codeEntries;
    }

    public List<PreferenceXmlEntry> getXmlEntries() {
        return xmlEntries;
    }

    public boolean isEmpty() {
        return codeEntries.isEmpty() && xmlEntries.isEmpty();
    }
}
