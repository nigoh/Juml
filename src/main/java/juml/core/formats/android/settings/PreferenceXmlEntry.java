// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

/**
 * res/xml/ の Preference XML から抽出したキー定義を表す。
 */
public final class PreferenceXmlEntry {

    /** android:key 属性の値。 */
    public final String key;
    /** 要素タグ名 (SwitchPreference, EditTextPreference など)。 */
    public final String elementType;
    /** android:defaultValue 属性。不明なら ""。 */
    public final String defaultValue;
    /** android:title 属性。不明なら ""。 */
    public final String title;
    /** ファイルパス。 */
    public final String file;

    public PreferenceXmlEntry(String key, String elementType, String defaultValue,
                               String title, String file) {
        this.key = key != null ? key : "";
        this.elementType = elementType != null ? elementType : "";
        this.defaultValue = defaultValue != null ? defaultValue : "";
        this.title = title != null ? title : "";
        this.file = file != null ? file : "";
    }

    /** ファイル名のみ (パスを除いた末尾コンポーネント) を返す。 */
    public String shortFileName() {
        if (file.isEmpty()) {
            return "";
        }
        int sep = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        return sep >= 0 ? file.substring(sep + 1) : file;
    }
}
