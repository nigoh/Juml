// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1 つの {@code res/values/strings.xml}（{@code values-ja/} 等のバリアント含む。
 * もしくは {@code <resources>} ルートを持つ任意の values XML）から抽出した
 * 文字列リソースの集合。
 *
 * <p>{@link StringResourceParser} が {@code <string name="...">value</string>} を
 * 走査して構築する。{@link AndroidLayoutInfo} と同じく
 * {@code moduleName / sourceSet / configQualifier / fileName} のメタ情報を保持し、
 * {@code configQualifier} には locale 等のバリアント（例: {@code ja}, {@code night}）が入る。</p>
 *
 * <p>{@code @string/foo} 形式の参照を実文言へ解決する際の辞書として、
 * {@link AndroidProjectAnalysis#resolveString(String)} から利用する。</p>
 */
public class AndroidStringResources {

    private String filePath;
    private String moduleName = ":root";
    private String sourceSet = "main";
    private String configQualifier = "";
    private String fileName = "";
    private final Map<String, String> strings = new LinkedHashMap<>();

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
     * values バリアントの configuration qualifier。
     * {@code res/values/} なら空文字列、{@code res/values-ja/} なら {@code "ja"}。
     */
    public String getConfigQualifier() {
        return configQualifier;
    }

    public void setConfigQualifier(String configQualifier) {
        this.configQualifier = configQualifier == null ? "" : configQualifier;
    }

    /** XML ファイル名 (例: {@code strings.xml})。 */
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName;
    }

    /** {@code name → value} の文字列リソース表 (宣言順)。 */
    public Map<String, String> getStrings() {
        return strings;
    }

    /** 指定 name の文言を返す。未定義なら null。 */
    public String getString(String name) {
        return name == null ? null : strings.get(name);
    }

    /** このファイルが「デフォルト locale」(qualifier 無し) かどうか。 */
    public boolean isDefaultLocale() {
        return configQualifier == null || configQualifier.isEmpty();
    }
}
