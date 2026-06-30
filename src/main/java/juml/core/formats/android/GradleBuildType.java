// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * Gradle android.buildTypes ブロックの 1 つのビルドタイプ。
 */
public class GradleBuildType {

    private final String name;
    private Boolean minifyEnabled;
    private Boolean debuggable;
    private String applicationIdSuffix;
    private String versionNameSuffix;
    private String signingConfig;

    public GradleBuildType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Boolean getMinifyEnabled() {
        return minifyEnabled;
    }

    public void setMinifyEnabled(Boolean minifyEnabled) {
        this.minifyEnabled = minifyEnabled;
    }

    public Boolean getDebuggable() {
        return debuggable;
    }

    public void setDebuggable(Boolean debuggable) {
        this.debuggable = debuggable;
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

    public String getSigningConfig() {
        return signingConfig;
    }

    public void setSigningConfig(String signingConfig) {
        this.signingConfig = signingConfig;
    }
}
