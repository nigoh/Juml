// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AndroidManifest.xml の Activity / Service / Receiver / Provider 宣言。
 */
public class AndroidComponentInfo {

    /** コンポーネント種別。 */
    public enum Kind {
        ACTIVITY("Activity"),
        SERVICE("Service"),
        RECEIVER("BroadcastReceiver"),
        PROVIDER("ContentProvider");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Kind kind;
    private String name;
    private Boolean exported;
    private Boolean enabled;
    private String taskAffinity;
    private String process;
    private String permission;
    private String authorities;
    private String targetActivity;
    private String foregroundServiceType;
    private final List<AndroidIntentFilter> intentFilters = new ArrayList<>();
    private final Map<String, String> metaData = new LinkedHashMap<>();
    private final List<AndroidPropertyInfo> properties = new ArrayList<>();

    public AndroidComponentInfo(Kind kind, String name) {
        this.kind = kind;
        this.name = name == null ? "" : name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    /** FQN を後付けで補完するための setter。 */
    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public Boolean getExported() {
        return exported;
    }

    public void setExported(Boolean exported) {
        this.exported = exported;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getTaskAffinity() {
        return taskAffinity;
    }

    public void setTaskAffinity(String taskAffinity) {
        this.taskAffinity = taskAffinity;
    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String process) {
        this.process = process;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getAuthorities() {
        return authorities;
    }

    public void setAuthorities(String authorities) {
        this.authorities = authorities;
    }

    /**
     * {@code <activity-alias>} の {@code targetActivity} 属性 (FQN 解決済み)。
     * 通常の Activity / Service / Receiver / Provider では null。
     */
    public String getTargetActivity() {
        return targetActivity;
    }

    public void setTargetActivity(String targetActivity) {
        this.targetActivity = targetActivity;
    }

    /** alias として宣言されたかどうか。 */
    public boolean isActivityAlias() {
        return targetActivity != null && !targetActivity.isEmpty();
    }

    /**
     * {@code <service>} の {@code foregroundServiceType} 属性。
     * Android 14 以降の foreground service では必須宣言で、
     * {@code "camera|microphone"} のように {@code |} 区切りで複数指定可能。
     */
    public String getForegroundServiceType() {
        return foregroundServiceType;
    }

    public void setForegroundServiceType(String foregroundServiceType) {
        this.foregroundServiceType = foregroundServiceType;
    }

    public List<AndroidIntentFilter> getIntentFilters() {
        return intentFilters;
    }

    public Map<String, String> getMetaData() {
        return metaData;
    }

    /** コンポーネント直下の {@code <property>} 宣言。 */
    public List<AndroidPropertyInfo> getProperties() {
        return properties;
    }

    /**
     * {@code foregroundServiceType} 文字列に含まれる種別を要求 API レベルの大きい順に分解する。
     * 例: {@code "shortService|dataSync"} → {@code [shortService(34), dataSync(29)]}。
     */
    public List<String> getForegroundServiceTypeList() {
        return ForegroundServiceTypeCatalog.split(foregroundServiceType);
    }

    /** ランチャー Activity か判定 (Activity 限定)。 */
    public boolean isLauncher() {
        if (kind != Kind.ACTIVITY) {
            return false;
        }
        for (AndroidIntentFilter f : intentFilters) {
            if (f.isLauncher()) {
                return true;
            }
        }
        return false;
    }
}
