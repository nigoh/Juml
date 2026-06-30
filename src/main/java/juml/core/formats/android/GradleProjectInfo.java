// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1 つの Gradle モジュールの構成情報。
 *
 * <p>{@code GradleScriptParser} がビルドスクリプトと {@code settings.gradle} から
 * 抽出した値を集約する。動的構文や未対応構文で抽出できなかったフィールドは null。</p>
 */
public class GradleProjectInfo {

    private String moduleName = "";
    private String moduleType;
    private String applicationId;
    private String namespace;
    private Integer compileSdk;
    private Integer minSdk;
    private Integer targetSdk;
    private Integer versionCode;
    private String versionName;

    private final List<String> plugins = new ArrayList<>();
    private final List<GradleDependency> dependencies = new ArrayList<>();
    private final Map<String, GradleBuildType> buildTypes = new LinkedHashMap<>();
    private final Map<String, GradleProductFlavor> productFlavors = new LinkedHashMap<>();
    private final Map<String, GradleSigningConfig> signingConfigs = new LinkedHashMap<>();
    private final List<String> flavorDimensions = new ArrayList<>();
    private final List<String> subprojects = new ArrayList<>();

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName == null ? "" : moduleName;
    }

    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Integer getCompileSdk() {
        return compileSdk;
    }

    public void setCompileSdk(Integer compileSdk) {
        this.compileSdk = compileSdk;
    }

    public Integer getMinSdk() {
        return minSdk;
    }

    public void setMinSdk(Integer minSdk) {
        this.minSdk = minSdk;
    }

    public Integer getTargetSdk() {
        return targetSdk;
    }

    public void setTargetSdk(Integer targetSdk) {
        this.targetSdk = targetSdk;
    }

    public Integer getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(Integer versionCode) {
        this.versionCode = versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public List<String> getPlugins() {
        return plugins;
    }

    public List<GradleDependency> getDependencies() {
        return dependencies;
    }

    public Map<String, GradleBuildType> getBuildTypes() {
        return buildTypes;
    }

    public Map<String, GradleProductFlavor> getProductFlavors() {
        return productFlavors;
    }

    public Map<String, GradleSigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    public List<String> getFlavorDimensions() {
        return flavorDimensions;
    }

    public List<String> getSubprojects() {
        return subprojects;
    }

    /** Android Application プラグインを適用していそうか。 */
    public boolean isAndroidApplication() {
        for (String p : plugins) {
            if (p.equals("com.android.application")
                    || p.equals("android")) {
                return true;
            }
        }
        return false;
    }

    /** Android Library プラグインを適用していそうか。 */
    public boolean isAndroidLibrary() {
        for (String p : plugins) {
            if (p.equals("com.android.library")
                    || p.equals("android-library")) {
                return true;
            }
        }
        return false;
    }
}
