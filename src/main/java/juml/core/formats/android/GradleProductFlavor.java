// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * Gradle android.productFlavors ブロックの 1 つのフレーバー。
 */
public class GradleProductFlavor {

    private final String name;
    private String dimension;
    private String applicationIdSuffix;
    private String versionNameSuffix;

    public GradleProductFlavor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getApplicationIdSuffix() {
        return applicationIdSuffix;
    }

    public void setApplicationIdSuffix(String applicationIdSuffix) {
        this.applicationIdSuffix = applicationIdSuffix;
    }

    public String getVersionNameSuffix() {
        return versionNameSuffix;
    }

    public void setVersionNameSuffix(String versionNameSuffix) {
        this.versionNameSuffix = versionNameSuffix;
    }
}
