// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import juml.core.formats.android.GradleProjectInfo;
import juml.core.formats.android.GradleScriptParser;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gradle の {@code settings.gradle} / {@code build.gradle} から解析対象パスを推定する。
 *
 * <p>主用途は {@code IndexCommand}（事前フルスキャン）。AOSP のように
 * モジュールが何百もあるツリーでルート全走査をすると時間とディスクの両方で
 * 不利なので、Gradle が知っているモジュール境界を先に拾って解析対象を絞る。</p>
 *
 * <p>取れる情報は粗い: モジュール名 (例 {@code ":app"}) と、その配下の
 * Android デフォルト sourceSet ({@code src/main/java}, {@code src/main/aidl},
 * {@code src/main/AndroidManifest.xml}) のパス候補だけ。{@code sourceSets.main.srcDirs}
 * での明示的な上書きはサポートしない (Juml の Gradle 解析が正規表現ベースで
 * srcDirs を取り切れないため。誤検出を防ぐためデフォルト配置に限定する)。</p>
 *
 * <p>{@code settings.gradle} が無い / 解析に失敗した場合は
 * {@link Scope#isFallback()} = true の結果を返す。呼び出し側はこのとき
 * 「ルート以下全走査」の従来動作にフォールバックする。</p>
 */
public final class GradleProjectScope {

    /** モジュールに紐付くソース候補パス 1 件分。 */
    public static final class ScopePath {

        /** ファイル種別。 */
        public enum Kind { JAVA, AIDL, MANIFEST }

        private final String moduleName;
        private final Kind kind;
        private final File path;

        ScopePath(String moduleName, Kind kind, File path) {
            this.moduleName = moduleName;
            this.kind = kind;
            this.path = path;
        }

        public String getModuleName() {
            return moduleName;
        }

        public Kind getKind() {
            return kind;
        }

        /** 解析対象のディレクトリ or ファイル (絶対パス)。存在しない場合もある (呼び出し側で existence チェック)。 */
        public File getPath() {
            return path;
        }
    }

    /** 解析対象パス候補の集合。 */
    public static final class Scope {
        private final List<ScopePath> paths;
        private final Map<String, String> moduleNameToRelDir;
        private final boolean fallback;

        Scope(List<ScopePath> paths, Map<String, String> moduleNameToRelDir, boolean fallback) {
            this.paths = paths;
            this.moduleNameToRelDir = moduleNameToRelDir;
            this.fallback = fallback;
        }

        public List<ScopePath> getPaths() {
            return Collections.unmodifiableList(paths);
        }

        /** {@code ":app" → "app"} のような (moduleName → projectRoot 相対ディレクトリ) マップ。 */
        public Map<String, String> getModuleNameToRelDir() {
            return Collections.unmodifiableMap(moduleNameToRelDir);
        }

        /**
         * {@code settings.gradle} 由来のモジュール特定に失敗したかどうか。
         * true の場合、呼び出し側は「ルート全走査」にフォールバックすべき。
         */
        public boolean isFallback() {
            return fallback;
        }
    }

    private GradleProjectScope() {
    }

    /** プロジェクトルートから解析対象を推定する。 */
    public static Scope resolve(File projectRoot, ErrorListener listener) {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return new Scope(Collections.emptyList(), Collections.emptyMap(), true);
        }
        File settings = pickExisting(
                new File(projectRoot, "settings.gradle"),
                new File(projectRoot, "settings.gradle.kts"));
        if (settings == null) {
            return new Scope(Collections.emptyList(), Collections.emptyMap(), true);
        }
        String script;
        try {
            script = new String(Files.readAllBytes(settings.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            l.onError(settings.getName(), -1, "failed to read settings: " + ex.getMessage());
            return new Scope(Collections.emptyList(), Collections.emptyMap(), true);
        }
        GradleProjectInfo info = GradleScriptParser.parse(script, settings.getName(), l, null);
        List<String> subprojects = info.getSubprojects();
        if (subprojects == null || subprojects.isEmpty()) {
            return new Scope(Collections.emptyList(), Collections.emptyMap(), true);
        }

        List<ScopePath> paths = new ArrayList<>();
        Map<String, String> moduleDir = new LinkedHashMap<>();
        for (String moduleName : subprojects) {
            String relDir = moduleNameToRelDir(moduleName);
            if (relDir == null) {
                continue;
            }
            File moduleRoot = new File(projectRoot, relDir);
            if (!moduleRoot.isDirectory()) {
                continue;
            }
            moduleDir.put(moduleName, relDir);
            for (String sourceSet : Arrays.asList("main", "debug", "release")) {
                File javaDir = new File(moduleRoot, "src/" + sourceSet + "/java");
                if (javaDir.isDirectory()) {
                    paths.add(new ScopePath(moduleName, ScopePath.Kind.JAVA, javaDir));
                }
                File aidlDir = new File(moduleRoot, "src/" + sourceSet + "/aidl");
                if (aidlDir.isDirectory()) {
                    paths.add(new ScopePath(moduleName, ScopePath.Kind.AIDL, aidlDir));
                }
                File manifest = new File(moduleRoot, "src/" + sourceSet + "/AndroidManifest.xml");
                if (manifest.isFile()) {
                    paths.add(new ScopePath(moduleName, ScopePath.Kind.MANIFEST, manifest));
                }
            }
        }
        if (paths.isEmpty()) {
            return new Scope(Collections.emptyList(), Collections.emptyMap(), true);
        }
        return new Scope(paths, moduleDir, false);
    }

    /** {@code ":app"} → {@code "app"}, {@code ":car:lib"} → {@code "car/lib"}。空文字なら null。 */
    static String moduleNameToRelDir(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) {
            return null;
        }
        String trimmed = moduleName.startsWith(":") ? moduleName.substring(1) : moduleName;
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replace(':', '/');
    }

    private static File pickExisting(File... candidates) {
        for (File f : candidates) {
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }
}
