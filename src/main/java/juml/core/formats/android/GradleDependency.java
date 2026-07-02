// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * Gradle ビルドスクリプトで宣言された依存。
 */
public class GradleDependency {

    private final String scope;
    private final String notation;
    private String group;
    private String name;
    private String version;
    private String moduleRef;
    /** files('libs/a.jar') 形式のローカル JAR/AAR パス (build.gradle からの相対)。 */
    private String filePath;
    /** fileTree(dir: 'libs') 形式のローカル JAR ディレクトリ (build.gradle からの相対)。 */
    private String fileTreeDir;

    public GradleDependency(String scope, String notation) {
        this.scope = scope == null ? "" : scope;
        this.notation = notation == null ? "" : notation;
        parseNotation();
    }

    /**
     * {@code implementation files('libs/a.jar')} のようなローカルファイル依存を表す
     * インスタンスを生成する。座標 (group:name:version) は持たない。
     */
    public static GradleDependency forFile(String scope, String path) {
        GradleDependency d = new GradleDependency(scope, "files('" + path + "')");
        d.group = null;
        d.name = path;
        d.version = null;
        d.filePath = path;
        return d;
    }

    /**
     * {@code implementation fileTree(dir: 'libs', include: ['*.jar'])} のような
     * ディレクトリ一括のローカル依存を表すインスタンスを生成する。
     */
    public static GradleDependency forFileTree(String scope, String dir) {
        GradleDependency d = new GradleDependency(scope, "fileTree('" + dir + "')");
        d.group = null;
        d.name = dir;
        d.version = null;
        d.fileTreeDir = dir;
        return d;
    }

    private void parseNotation() {
        String n = notation;
        if (n.startsWith("project(")) {
            int s = n.indexOf('(');
            int e = n.lastIndexOf(')');
            if (s >= 0 && e > s) {
                String inner = n.substring(s + 1, e).trim();
                // 長さ 2 未満 (単一クォート) では substring(1, 0) が例外になるためガードする。
                if (inner.length() >= 2
                        && (inner.startsWith("'") || inner.startsWith("\""))) {
                    inner = inner.substring(1, inner.length() - 1);
                }
                if (inner.startsWith(":")) {
                    inner = inner.substring(1);
                }
                moduleRef = inner;
            }
            return;
        }
        String[] parts = n.split(":");
        if (parts.length >= 3) {
            group = parts[0];
            name = parts[1];
            version = parts[2];
        } else if (parts.length == 2) {
            group = parts[0];
            name = parts[1];
        } else {
            name = n;
        }
    }

    public String getScope() {
        return scope;
    }

    public String getNotation() {
        return notation;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getModuleRef() {
        return moduleRef;
    }

    /** プロジェクト内モジュール参照か。 */
    public boolean isModuleReference() {
        return moduleRef != null;
    }

    /** files(...) の単一ファイルパス。ファイル依存でなければ null。 */
    public String getFilePath() {
        return filePath;
    }

    /** fileTree(dir: ...) のディレクトリパス。fileTree 依存でなければ null。 */
    public String getFileTreeDir() {
        return fileTreeDir;
    }

    /** プロジェクト内に同梱されたローカル JAR/AAR 依存 (files / fileTree) か。 */
    public boolean isFileDependency() {
        return filePath != null || fileTreeDir != null;
    }
}
