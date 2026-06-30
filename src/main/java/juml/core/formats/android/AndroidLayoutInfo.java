// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * 1 つの Android layout XML ({@code res/layout/*.xml} などのバリアント込み) の解析結果。
 *
 * <p>{@link AndroidLayoutParser} がファイル内容を読み取って構築する。
 * {@link #getRoot()} が View 階層のルートノード。空ファイル/不正 XML の場合は null。</p>
 *
 * <p>{@link #getKey()} は {@code moduleName::sourceSet::relativePath} 形式で、
 * GUI 側 ({@code DiagramRequest.layoutKey}) からの参照キーとして使う。
 * 絶対パスはマシン依存になるため使わない。</p>
 */
public class AndroidLayoutInfo {

    private String filePath;
    private String moduleName = ":root";
    private String sourceSet = "main";
    private String configQualifier = "";
    private String fileName = "";
    private LayoutViewNode root;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
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

    /**
     * layout バリアントの configuration qualifier。
     * {@code res/layout/} なら空文字列、{@code res/layout-land/} なら {@code "land"}、
     * {@code res/layout-sw600dp-v21/} なら {@code "sw600dp-v21"}。
     */
    public String getConfigQualifier() {
        return configQualifier;
    }

    public void setConfigQualifier(String configQualifier) {
        this.configQualifier = configQualifier == null ? "" : configQualifier;
    }

    /** XML ファイル名 (例: {@code activity_main.xml})。 */
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName;
    }

    public LayoutViewNode getRoot() {
        return root;
    }

    public void setRoot(LayoutViewNode root) {
        this.root = root;
    }

    /**
     * GUI/Service 層からの参照キー。
     * 形式: {@code moduleName::sourceSet::configQualifier::fileName}
     */
    public String getKey() {
        return moduleName + "::" + sourceSet + "::" + configQualifier + "::" + fileName;
    }

    /** ファイル選択 UI に表示する人間可読ラベル。 */
    public String displayLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(fileName);
        if (configQualifier != null && !configQualifier.isEmpty()) {
            sb.append(" [").append(configQualifier).append(']');
        }
        if (!"main".equals(sourceSet)) {
            sb.append(" (").append(sourceSet).append(')');
        }
        if (!":root".equals(moduleName)) {
            sb.append(" — ").append(moduleName);
        }
        return sb.toString();
    }
}
