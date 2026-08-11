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

    /**
     * {@code implementation platform('androidx.compose:compose-bom:2024.02.00')} のような
     * BOM 依存を表すインスタンスを生成する。座標は<b>ラッパを剥がしてから</b>解釈する。
     *
     * <p>兄弟の {@link #forFile} / {@link #forFileTree} は既にこの形だったが、
     * platform だけ素の座標として {@code new GradleDependency(scope, "platform('…')")} を
     * 呼んでいた。{@link #parseNotation} は {@code project(} 以外を素の座標とみなして
     * {@code :} で割るので、group が {@code platform('androidx.compose}、version が
     * {@code 2024.02.00')} という<b>ラッパの断片を含んだ座標</b>になる。依存グラフは
     * それをそのままノード名に使い、jar 索引はその名前でキャッシュを探して
     * 「解決できなかった依存」として数える。</p>
     */
    public static GradleDependency forPlatform(String scope, String coords, boolean enforced) {
        String wrapper = enforced ? "enforcedPlatform" : "platform";
        GradleDependency d = new GradleDependency(scope, wrapper + "('" + coords + "')");
        String[] parts = coords.split(":");
        d.group = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : null;
        d.name = parts.length > 1 ? parts[1] : (parts.length > 0 ? parts[0] : coords);
        d.version = parts.length > 2 ? parts[2] : null;
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
