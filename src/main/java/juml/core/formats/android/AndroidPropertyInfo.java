// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * AndroidManifest.xml の {@code <property>} 要素。
 *
 * <p>Android 12 (API 31) から application / activity / service / receiver / provider 配下に
 * 宣言できるようになったメタデータ。{@code <meta-data>} と似ているが、こちらは
 * {@code PackageManager.getProperty()} 経由でランタイムから読み出せる正式 API である。
 * Android 14 / 15 では特殊な foreground service ({@code specialUse} など) で必須宣言
 * ({@code android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE}) として使われるなど、
 * モダンな manifest で増えてきている要素。</p>
 */
public class AndroidPropertyInfo {

    private final String name;
    private String value;
    private String resource;

    public AndroidPropertyInfo(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() {
        return name;
    }

    /** {@code android:value="..."} (リテラル値)。 */
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /** {@code android:resource="@xml/..."} (リソース参照)。 */
    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    /** value または resource のうち、最初に値があるものを返す。 */
    public String effectiveValue() {
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return resource;
    }
}
