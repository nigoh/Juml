// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1 つの values XML ({@code styles.xml} / {@code themes.xml} など) から抽出した
 * スタイル/テーマ定義の集合。
 *
 * <p>{@link StyleResourceParser} が {@code <style name="..." parent="...">} とその
 * {@code <item name="...">value</item>} を走査して構築する。
 * {@link AndroidStringResources} と同じく {@code moduleName / sourceSet /
 * configQualifier / fileName} のメタ情報を保持する。</p>
 */
public class AndroidStyleResources {

    /** 1 つの {@code <style>} 定義。 */
    public static class StyleDef {
        private final String name;
        private String parent;
        private final Map<String, String> items = new LinkedHashMap<>();

        public StyleDef(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        /** {@code parent="@style/Foo"} の参照先 (短縮名)。未指定なら null。 */
        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        /** {@code <item name>value</item>} の表 (宣言順)。 */
        public Map<String, String> getItems() {
            return items;
        }
    }

    private String filePath;
    private String moduleName = ":root";
    private String sourceSet = "main";
    private String configQualifier = "";
    private String fileName = "";
    private final Map<String, StyleDef> styles = new LinkedHashMap<>();

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

    public String getConfigQualifier() {
        return configQualifier;
    }

    public void setConfigQualifier(String configQualifier) {
        this.configQualifier = configQualifier == null ? "" : configQualifier;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName;
    }

    /** {@code name → StyleDef} のスタイル表 (宣言順)。 */
    public Map<String, StyleDef> getStyles() {
        return styles;
    }

    /** 指定 name のスタイル定義を返す。未定義なら null。 */
    public StyleDef getStyle(String name) {
        return name == null ? null : styles.get(name);
    }
}
