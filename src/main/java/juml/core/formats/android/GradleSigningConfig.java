// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * Gradle android.signingConfigs ブロックの 1 つの署名構成。
 *
 * <p>セキュリティ上、{@code keyPassword} や {@code storePassword} などの機密値は
 * 保持・出力しない。設定名と {@code keyAlias} のみを保持する。</p>
 */
public class GradleSigningConfig {

    private final String name;
    private String keyAlias;
    private String storeFile;

    public GradleSigningConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getStoreFile() {
        return storeFile;
    }

    public void setStoreFile(String storeFile) {
        this.storeFile = storeFile;
    }
}
