// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * AndroidManifest.xml の {@code <permission>} 要素 (アプリ自身が定義する独自パーミッション)。
 *
 * <p>{@code <uses-permission>} はアプリが要求するパーミッションだが、こちらは
 * アプリが提供 (宣言) するパーミッションで、Activity / Service / Provider などを
 * 保護するために自分で定義するもの。{@code protectionLevel} によって
 * 公開範囲 (normal / dangerous / signature / signatureOrSystem) が決まる。</p>
 */
public class AndroidCustomPermission {

    private final String name;
    private String protectionLevel;
    private String permissionGroup;
    private String label;
    private String description;

    public AndroidCustomPermission(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() {
        return name;
    }

    /** {@code com.x.permission.FOO} → {@code FOO}。 */
    public String getShortName() {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    public String getProtectionLevel() {
        return protectionLevel;
    }

    public void setProtectionLevel(String protectionLevel) {
        this.protectionLevel = protectionLevel;
    }

    public String getPermissionGroup() {
        return permissionGroup;
    }

    public void setPermissionGroup(String permissionGroup) {
        this.permissionGroup = permissionGroup;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
