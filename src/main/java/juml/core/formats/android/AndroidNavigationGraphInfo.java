// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.List;

/**
 * 1 つの Jetpack Navigation グラフ XML ({@code res/navigation/*.xml}) の解析結果。
 *
 * <p>{@link AndroidNavigationGraphParser} がファイル内容を読み取って構築する。</p>
 *
 * <p>{@link #getKey()} は {@code moduleName::sourceSet::fileName} 形式で、
 * GUI 側 ({@code DiagramRequest.navigationGraphKey}) からの参照キーとして使う。
 * 絶対パスはマシン依存になるため使わない。</p>
 */
public class AndroidNavigationGraphInfo {

    private String filePath;
    private String fileName = "";
    private String moduleName = ":root";
    private String sourceSet = "main";
    private String graphId;
    private String startDestination;
    private String label;
    private final List<NavigationDestination> destinations = new ArrayList<>();
    private final List<NavigationAction> globalActions = new ArrayList<>();

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName == null || moduleName.isEmpty() ? ":root" : moduleName;
    }

    public String getSourceSet() {
        return sourceSet;
    }

    public void setSourceSet(String sourceSet) {
        this.sourceSet = sourceSet == null || sourceSet.isEmpty() ? "main" : sourceSet;
    }

    /** {@code android:id} の正規化済み参照名。 */
    public String getGraphId() {
        return graphId;
    }

    public void setGraphId(String graphId) {
        this.graphId = graphId;
    }

    /** {@code app:startDestination} の正規化済み idRef。 */
    public String getStartDestination() {
        return startDestination;
    }

    public void setStartDestination(String startDestination) {
        this.startDestination = startDestination;
    }

    /** {@code android:label}。 */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** グラフ内の全 Destination リスト ({@code <fragment>} / {@code <activity>} / 等)。 */
    public List<NavigationDestination> getDestinations() {
        return destinations;
    }

    /** グラフルート直下のグローバルアクション ({@code <action>})。 */
    public List<NavigationAction> getGlobalActions() {
        return globalActions;
    }

    /**
     * GUI/Service 層からの参照キー。
     * 形式: {@code moduleName::sourceSet::fileName}
     */
    public String getKey() {
        return moduleName + "::" + sourceSet + "::" + fileName;
    }

    /** ファイル選択 UI に表示する人間可読ラベル。 */
    public String displayLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(fileName.isEmpty() ? "(unnamed)" : fileName);
        if (!"main".equals(sourceSet)) {
            sb.append(" (").append(sourceSet).append(')');
        }
        if (!":root".equals(moduleName)) {
            sb.append(" — ").append(moduleName);
        }
        return sb.toString();
    }
}
