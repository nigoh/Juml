// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.List;

/**
 * AndroidManifest.xml の {@code <intent-filter>} 要素。
 */
public class AndroidIntentFilter {

    private final List<String> actions = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<String> dataSchemes = new ArrayList<>();
    private final List<String> dataMimeTypes = new ArrayList<>();
    private final List<AndroidDataSpec> dataSpecs = new ArrayList<>();
    private Integer priority;
    private Integer order;
    private Boolean autoVerify;

    public List<String> getActions() {
        return actions;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getDataSchemes() {
        return dataSchemes;
    }

    public List<String> getDataMimeTypes() {
        return dataMimeTypes;
    }

    /**
     * {@code <data>} 要素を 1 つずつ保持したリスト。
     * scheme と path 系を組として扱う Deep Link 解析向け。
     */
    public List<AndroidDataSpec> getDataSpecs() {
        return dataSpecs;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /** Android 8+: 同一 priority 内での処理順序。 */
    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    /**
     * App Links 用フラグ。
     * {@code true} のとき、http/https の Deep Link は OS が
     * Digital Asset Links で検証する。
     */
    public Boolean getAutoVerify() {
        return autoVerify;
    }

    public void setAutoVerify(Boolean autoVerify) {
        this.autoVerify = autoVerify;
    }

    /** action.MAIN + category.LAUNCHER を持つランチャー filter か判定。 */
    public boolean isLauncher() {
        return actions.contains("android.intent.action.MAIN")
                && categories.contains("android.intent.category.LAUNCHER");
    }

    /**
     * VIEW + BROWSABLE を持つ Deep Link 入口の intent-filter か判定。
     * App Links / カスタムスキーム Deep Link の検出に使う。
     */
    public boolean isViewDeepLink() {
        return actions.contains("android.intent.action.VIEW")
                && categories.contains("android.intent.category.BROWSABLE");
    }
}
