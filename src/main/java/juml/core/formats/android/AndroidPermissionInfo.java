// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * AndroidManifest.xml の {@code <uses-permission>} 要素。
 */
public class AndroidPermissionInfo {

    private final String name;
    private Integer maxSdkVersion;

    public AndroidPermissionInfo(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() {
        return name;
    }

    /** {@code android.permission.INTERNET} → {@code INTERNET} のように接頭辞を除いた短縮名。 */
    public String getShortName() {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    public Integer getMaxSdkVersion() {
        return maxSdkVersion;
    }

    public void setMaxSdkVersion(Integer maxSdkVersion) {
        this.maxSdkVersion = maxSdkVersion;
    }
}
