// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AndroidManifest.xml の解析結果。
 */
public class AndroidManifestInfo {

    private String packageName = "";
    private String sourceSet = "main";
    private String applicationClass;
    private String applicationLabel;
    private String applicationTheme;
    private Boolean applicationDebuggable;
    private Boolean applicationAllowBackup;
    private Integer minSdkVersion;
    private Integer targetSdkVersion;
    private Integer maxSdkVersion;
    // Android 10+ / 13+ / 14+ で追加された Application 属性。値は宣言があるものだけ非 null。
    private Boolean applicationUsesCleartextTraffic;
    private String applicationNetworkSecurityConfig;
    private Boolean applicationEnableOnBackInvokedCallback;
    private String applicationLocaleConfig;
    private String applicationDataExtractionRules;
    private Boolean applicationHardwareAccelerated;
    private Boolean applicationLargeHeap;
    private String applicationAppCategory;

    private final List<AndroidComponentInfo> activities = new ArrayList<>();
    private final List<AndroidComponentInfo> services = new ArrayList<>();
    private final List<AndroidComponentInfo> receivers = new ArrayList<>();
    private final List<AndroidComponentInfo> providers = new ArrayList<>();
    private final List<AndroidPermissionInfo> permissions = new ArrayList<>();
    private final List<AndroidCustomPermission> customPermissions = new ArrayList<>();
    private final List<String> features = new ArrayList<>();
    private final Map<String, String> applicationMetaData = new LinkedHashMap<>();
    private final List<AndroidPropertyInfo> applicationProperties = new ArrayList<>();

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName == null ? "" : packageName;
    }

    /**
     * このマニフェストが属する Gradle ソースセット ({@code main} / {@code debug} /
     * フレーバー名)。Analyzer がファイルパスから {@code src/<sourceSet>/AndroidManifest.xml}
     * の {@code <sourceSet>} 部分を抽出して設定する。
     */
    public String getSourceSet() {
        return sourceSet;
    }

    public void setSourceSet(String sourceSet) {
        this.sourceSet = sourceSet == null || sourceSet.isEmpty() ? "main" : sourceSet;
    }

    public String getApplicationClass() {
        return applicationClass;
    }

    public void setApplicationClass(String applicationClass) {
        this.applicationClass = applicationClass;
    }

    public String getApplicationLabel() {
        return applicationLabel;
    }

    public void setApplicationLabel(String applicationLabel) {
        this.applicationLabel = applicationLabel;
    }

    public String getApplicationTheme() {
        return applicationTheme;
    }

    public void setApplicationTheme(String applicationTheme) {
        this.applicationTheme = applicationTheme;
    }

    public Boolean getApplicationDebuggable() {
        return applicationDebuggable;
    }

    public void setApplicationDebuggable(Boolean applicationDebuggable) {
        this.applicationDebuggable = applicationDebuggable;
    }

    public Boolean getApplicationAllowBackup() {
        return applicationAllowBackup;
    }

    public void setApplicationAllowBackup(Boolean applicationAllowBackup) {
        this.applicationAllowBackup = applicationAllowBackup;
    }

    public List<AndroidComponentInfo> getActivities() {
        return activities;
    }

    public List<AndroidComponentInfo> getServices() {
        return services;
    }

    public List<AndroidComponentInfo> getReceivers() {
        return receivers;
    }

    public List<AndroidComponentInfo> getProviders() {
        return providers;
    }

    public List<AndroidPermissionInfo> getPermissions() {
        return permissions;
    }

    /** アプリ自身が {@code <permission>} で宣言した独自パーミッション。 */
    public List<AndroidCustomPermission> getCustomPermissions() {
        return customPermissions;
    }

    public List<String> getFeatures() {
        return features;
    }

    /** {@code <uses-sdk android:minSdkVersion="..."/>}。 */
    public Integer getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(Integer minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    /** {@code <uses-sdk android:targetSdkVersion="..."/>}。 */
    public Integer getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(Integer targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    /** {@code <uses-sdk android:maxSdkVersion="..."/>} (推奨されないが互換のため保持)。 */
    public Integer getMaxSdkVersion() {
        return maxSdkVersion;
    }

    public void setMaxSdkVersion(Integer maxSdkVersion) {
        this.maxSdkVersion = maxSdkVersion;
    }

    public Map<String, String> getApplicationMetaData() {
        return applicationMetaData;
    }

    /** Application 直下の {@code <property>} 宣言。 */
    public List<AndroidPropertyInfo> getApplicationProperties() {
        return applicationProperties;
    }

    public Boolean getApplicationUsesCleartextTraffic() {
        return applicationUsesCleartextTraffic;
    }

    public void setApplicationUsesCleartextTraffic(Boolean v) {
        this.applicationUsesCleartextTraffic = v;
    }

    /** {@code @xml/network_security_config} のようなリソース参照。 */
    public String getApplicationNetworkSecurityConfig() {
        return applicationNetworkSecurityConfig;
    }

    public void setApplicationNetworkSecurityConfig(String v) {
        this.applicationNetworkSecurityConfig = v;
    }

    /** Android 13+: Predictive Back Gesture を有効化したか。 */
    public Boolean getApplicationEnableOnBackInvokedCallback() {
        return applicationEnableOnBackInvokedCallback;
    }

    public void setApplicationEnableOnBackInvokedCallback(Boolean v) {
        this.applicationEnableOnBackInvokedCallback = v;
    }

    /** Android 13+: 対応ロケール宣言 ({@code @xml/locale_config})。 */
    public String getApplicationLocaleConfig() {
        return applicationLocaleConfig;
    }

    public void setApplicationLocaleConfig(String v) {
        this.applicationLocaleConfig = v;
    }

    /** Android 12+: D2D/B&R で吸い出される範囲の宣言 ({@code @xml/data_extraction_rules})。 */
    public String getApplicationDataExtractionRules() {
        return applicationDataExtractionRules;
    }

    public void setApplicationDataExtractionRules(String v) {
        this.applicationDataExtractionRules = v;
    }

    public Boolean getApplicationHardwareAccelerated() {
        return applicationHardwareAccelerated;
    }

    public void setApplicationHardwareAccelerated(Boolean v) {
        this.applicationHardwareAccelerated = v;
    }

    public Boolean getApplicationLargeHeap() {
        return applicationLargeHeap;
    }

    public void setApplicationLargeHeap(Boolean v) {
        this.applicationLargeHeap = v;
    }

    /** Android 12+: {@code "social" / "productivity" / "game" / ...} 等のアプリ分類ヒント。 */
    public String getApplicationAppCategory() {
        return applicationAppCategory;
    }

    public void setApplicationAppCategory(String v) {
        this.applicationAppCategory = v;
    }

    /** 全コンポーネントを 1 つのリストに集約。 */
    public List<AndroidComponentInfo> allComponents() {
        List<AndroidComponentInfo> all = new ArrayList<>();
        all.addAll(activities);
        all.addAll(services);
        all.addAll(receivers);
        all.addAll(providers);
        return all;
    }

    /**
     * 相対名 ({@code .MainActivity}) を {@code packageName} と結合して FQN に解決する。
     * 既に完全修飾の場合はそのまま返す。
     */
    public String resolveClassName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (raw.startsWith(".")) {
            return packageName + raw;
        }
        if (!raw.contains(".") && !packageName.isEmpty()) {
            return packageName + "." + raw;
        }
        return raw;
    }
}
