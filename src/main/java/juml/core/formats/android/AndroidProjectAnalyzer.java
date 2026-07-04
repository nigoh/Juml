// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.java.AndroidProjectScanner;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Android Gradle プロジェクトを走査し、{@link AndroidProjectAnalysis} を返すファサード。
 *
 * <p>{@link AndroidProjectScanner} を内部で再利用し、{@code includeGradle = true} かつ
 * {@code includeManifest = true} でスキャンする。settings.gradle を最初にパースして
 * サブプロジェクト構造を把握し、その後各モジュールの build.gradle と AndroidManifest.xml
 * を読み込む。</p>
 */
public final class AndroidProjectAnalyzer {

    /** デフォルト (silent) リスナーで解析。 */
    public static AndroidProjectAnalysis analyze(File projectRoot) throws IOException {
        return analyze(projectRoot, null);
    }

    /** リスナー付き解析。個別ファイルの IO 失敗は listener に通知して継続。 */
    public static AndroidProjectAnalysis analyze(File projectRoot,
                                                  ErrorListener listener) throws IOException {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidProjectAnalysis analysis = new AndroidProjectAnalysis();
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeGradle = true;
        opts.includeManifest = true;
        opts.includeLayout = true;
        opts.includeNavigation = true;
        opts.includeValues = true;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);

        // 0. gradle/libs.versions.toml があれば先に読み込み、後段のパースで参照する
        VersionCatalog catalog = loadVersionCatalog(projectRoot, l);

        // 1. settings.gradle (root) を先にパースしてサブプロジェクト構造を取得
        for (File f : files) {
            String lower = f.getName().toLowerCase();
            if (lower.equals("settings.gradle") || lower.equals("settings.gradle.kts")) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                GradleProjectInfo settings = GradleScriptParser.parse(content,
                        f.getName(), l, catalog);
                settings.setModuleName(":root");
                analysis.setRootSettings(settings);
            }
        }

        // 2. 各 build.gradle と manifest をパース
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.equals("settings.gradle") || name.equals("settings.gradle.kts")) {
                continue;
            }
            String moduleName = inferModuleName(projectRoot, f);
            if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                GradleProjectInfo info;
                try {
                    info = GradleScriptParser.parse(content, f.getName(), l, catalog);
                } catch (RuntimeException ex) {
                    l.onError(juml.util.ErrorCode.PRJ_009, f.getName(), -1, "gradle parse failed: " + ex.getMessage());
                    continue;
                }
                info.setModuleName(moduleName);
                analysis.getGradleByModule().put(moduleName, info);
            } else if (name.endsWith(".xml") && isInNavigationDir(f)) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                AndroidNavigationGraphInfo info;
                try {
                    info = AndroidNavigationGraphParser.parse(content, l);
                } catch (RuntimeException ex) {
                    l.onError(juml.util.ErrorCode.PRJ_008, f.getName(), -1, "navigation parse failed: " + ex.getMessage());
                    continue;
                }
                info.setFilePath(f.getAbsolutePath());
                info.setFileName(f.getName());
                info.setModuleName(moduleName);
                info.setSourceSet(inferLayoutSourceSet(f));
                analysis.getNavigationsByModule()
                        .computeIfAbsent(moduleName, k -> new ArrayList<>())
                        .add(info);
            } else if (name.endsWith(".xml") && isInLayoutDir(f)) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                AndroidLayoutInfo info;
                try {
                    info = AndroidLayoutParser.parse(content, l);
                } catch (RuntimeException ex) {
                    l.onError(juml.util.ErrorCode.PRJ_008, f.getName(), -1, "layout parse failed: " + ex.getMessage());
                    continue;
                }
                info.setFilePath(f.getAbsolutePath());
                info.setFileName(f.getName());
                info.setModuleName(moduleName);
                info.setSourceSet(inferLayoutSourceSet(f));
                info.setConfigQualifier(inferLayoutConfigQualifier(f));
                analysis.getLayoutsByModule()
                        .computeIfAbsent(moduleName, k -> new ArrayList<>())
                        .add(info);
            } else if (name.endsWith(".xml") && isInValuesDir(f)) {
                parseValuesResources(f, moduleName, analysis, l);
            } else if (name.equals("androidmanifest.xml")) {
                String content = safeRead(f, l);
                if (content == null) {
                    continue;
                }
                AndroidManifestInfo info;
                try {
                    info = AndroidManifestParser.parse(content, l);
                } catch (RuntimeException ex) {
                    l.onError(juml.util.ErrorCode.PRJ_008, f.getName(), -1, "manifest parse failed: " + ex.getMessage());
                    continue;
                }
                info.setSourceSet(inferSourceSet(f));
                analysis.getManifestsByModule()
                        .computeIfAbsent(moduleName, k -> new ArrayList<>())
                        .add(info);
            }
        }

        // モダンな AndroidManifest は package 属性を持たないことがある (build.gradle の
        // namespace で代用される)。同モジュールの gradle.namespace を反映してコンポーネント
        // FQN を再解決する。
        backfillPackageFromGradle(analysis);

        int gradleCount = analysis.getGradleByModule().size();
        int manifestCount = analysis.allManifests().size();
        int layoutCount = analysis.allLayouts().size();
        int navCount = analysis.allNavigationGraphs().size();
        int stringFileCount = analysis.allStringResources().size();
        int styleFileCount = analysis.allStyleResources().size();
        l.onError(null, -1,
                "android analyzer: " + gradleCount + " gradle file(s), "
                        + manifestCount + " manifest(s), "
                        + layoutCount + " layout(s), "
                        + navCount + " navigation graph(s), "
                        + stringFileCount + " string resource file(s), "
                        + styleFileCount + " style resource file(s)");

        return analysis;
    }

    /** 親ディレクトリが {@code navigation} または {@code navigation-*} のときに true。 */
    private static boolean isInNavigationDir(File f) {
        File parent = f.getParentFile();
        if (parent == null) {
            return false;
        }
        String dir = parent.getName();
        return "navigation".equals(dir) || dir.startsWith("navigation-");
    }

    /**
     * 1 つの values XML をパースして文字列リソース ({@code <string>}) と
     * スタイル/テーマ ({@code <style>}) を取り込む。該当要素を 1 つも含まない
     * (colors.xml 等) 場合は何も追加しない。
     */
    private static void parseValuesResources(File f, String moduleName,
                                             AndroidProjectAnalysis analysis, ErrorListener l) {
        String content = safeRead(f, l);
        if (content == null) {
            return;
        }
        String sourceSet = inferLayoutSourceSet(f);
        String qualifier = inferValuesConfigQualifier(f);

        AndroidStringResources strings;
        try {
            strings = StringResourceParser.parse(content, l);
        } catch (RuntimeException ex) {
            l.onError(juml.util.ErrorCode.PRJ_008, f.getName(), -1, "strings parse failed: " + ex.getMessage());
            strings = new AndroidStringResources();
        }
        if (!strings.getStrings().isEmpty()) {
            strings.setFilePath(f.getAbsolutePath());
            strings.setFileName(f.getName());
            strings.setModuleName(moduleName);
            strings.setSourceSet(sourceSet);
            strings.setConfigQualifier(qualifier);
            analysis.getStringResourcesByModule()
                    .computeIfAbsent(moduleName, k -> new ArrayList<>())
                    .add(strings);
        }

        AndroidStyleResources styles;
        try {
            styles = StyleResourceParser.parse(content, l);
        } catch (RuntimeException ex) {
            l.onError(juml.util.ErrorCode.PRJ_008, f.getName(), -1, "styles parse failed: " + ex.getMessage());
            styles = new AndroidStyleResources();
        }
        if (!styles.getStyles().isEmpty()) {
            styles.setFilePath(f.getAbsolutePath());
            styles.setFileName(f.getName());
            styles.setModuleName(moduleName);
            styles.setSourceSet(sourceSet);
            styles.setConfigQualifier(qualifier);
            analysis.getStyleResourcesByModule()
                    .computeIfAbsent(moduleName, k -> new ArrayList<>())
                    .add(styles);
        }
    }

    /** 親ディレクトリが {@code values} または {@code values-*} のときに true。 */
    private static boolean isInValuesDir(File f) {
        File parent = f.getParentFile();
        if (parent == null) {
            return false;
        }
        String dir = parent.getName();
        return "values".equals(dir) || dir.startsWith("values-");
    }

    /**
     * values ファイルの親ディレクトリ名から configuration qualifier (locale 等) を抽出する。
     * {@code res/values/} → {@code ""}、{@code res/values-ja/} → {@code "ja"}。
     */
    static String inferValuesConfigQualifier(File valuesFile) {
        File parent = valuesFile.getParentFile();
        if (parent == null) {
            return "";
        }
        String dir = parent.getName();
        if ("values".equals(dir)) {
            return "";
        }
        if (dir.startsWith("values-") && dir.length() > "values-".length()) {
            return dir.substring("values-".length());
        }
        return "";
    }

    /** 親ディレクトリが {@code layout} または {@code layout-*} のときに true。 */
    private static boolean isInLayoutDir(File f) {
        File parent = f.getParentFile();
        if (parent == null) {
            return false;
        }
        String dir = parent.getName();
        return "layout".equals(dir) || dir.startsWith("layout-");
    }

    /**
     * layout ファイルの親ディレクトリ名から configuration qualifier を抽出する。
     * {@code res/layout/} → {@code ""}、{@code res/layout-land/} → {@code "land"}、
     * {@code res/layout-sw600dp-v21/} → {@code "sw600dp-v21"}。
     */
    static String inferLayoutConfigQualifier(File layoutFile) {
        File parent = layoutFile.getParentFile();
        if (parent == null) {
            return "";
        }
        String dir = parent.getName();
        if ("layout".equals(dir)) {
            return "";
        }
        if (dir.startsWith("layout-") && dir.length() > "layout-".length()) {
            return dir.substring("layout-".length());
        }
        return "";
    }

    /**
     * layout ファイルのパスから Gradle ソースセット名を推定する。
     * {@code src/<sourceSet>/res/layout/...} の {@code <sourceSet>} 部分。
     * 該当しなければ {@code "main"}。
     */
    static String inferLayoutSourceSet(File layoutFile) {
        // .../<sourceSet>/res/layout-?/<file>.xml の <sourceSet> を取り出す
        File parent = layoutFile.getParentFile();      // layout or layout-*
        if (parent == null) {
            return "main";
        }
        File res = parent.getParentFile();             // res
        if (res == null || !"res".equals(res.getName())) {
            return "main";
        }
        File sourceSetDir = res.getParentFile();       // main / debug / flavor
        if (sourceSetDir == null) {
            return "main";
        }
        File src = sourceSetDir.getParentFile();       // src
        if (src != null && "src".equals(src.getName())) {
            return sourceSetDir.getName();
        }
        return "main";
    }

    private static void backfillPackageFromGradle(AndroidProjectAnalysis analysis) {
        for (java.util.Map.Entry<String, java.util.List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            GradleProjectInfo gradle = analysis.getGradleByModule().get(e.getKey());
            if (gradle == null || gradle.getNamespace() == null) {
                continue;
            }
            String ns = gradle.getNamespace();
            for (AndroidManifestInfo info : e.getValue()) {
                if (info.getPackageName() == null || info.getPackageName().isEmpty()) {
                    info.setPackageName(ns);
                    // 既に保持しているコンポーネントの FQN を再解決
                    rewriteFqn(info);
                }
            }
        }
    }

    private static void rewriteFqn(AndroidManifestInfo info) {
        if (info.getApplicationClass() != null
                && info.getApplicationClass().startsWith(".")) {
            info.setApplicationClass(info.getPackageName() + info.getApplicationClass());
        }
        for (AndroidComponentInfo c : info.allComponents()) {
            c.setName(absolutize(info.getPackageName(), c.getName()));
            // <activity-alias> の targetActivity も相対 → 絶対へ揃える。揃えないと別名の
            // name だけが絶対化され、targetActivity (.Main) が対象 Activity の FQN と一致せず
            // 別名→対象のリンクが切れる。
            if (c.getTargetActivity() != null) {
                c.setTargetActivity(absolutize(info.getPackageName(), c.getTargetActivity()));
            }
        }
    }

    /** 相対クラス名 ({@code .Foo} / {@code Foo}) をパッケージで絶対化する。null/絶対はそのまま。 */
    private static String absolutize(String pkg, String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith(".")) {
            return pkg + name;
        }
        if (!name.contains(".")) {
            return pkg + "." + name;
        }
        return name;
    }

    private static String safeRead(File f, ErrorListener l) {
        try {
            return AndroidProjectScanner.readFile(f);
        } catch (IOException ex) {
            l.onError(juml.util.ErrorCode.PRJ_004, f.getName(), -1, "read failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * プロジェクトルートの {@code gradle/libs.versions.toml} を読み込み、
     * {@link VersionCatalog} に変換する。存在しなければ null。
     */
    static VersionCatalog loadVersionCatalog(File projectRoot, ErrorListener l) {
        File toml = new File(projectRoot, "gradle/libs.versions.toml");
        if (!toml.isFile()) {
            return null;
        }
        String content = safeRead(toml, l);
        if (content == null) {
            return null;
        }
        VersionCatalog catalog = VersionCatalogParser.parse(content, l);
        l.onError(null, -1,
                "version catalog: " + catalog.getVersions().size() + " versions, "
                        + catalog.getLibraries().size() + " libraries, "
                        + catalog.getPlugins().size() + " plugins");
        return catalog;
    }

    /**
     * AndroidManifest.xml のパスから所属する Gradle ソースセット名を推定する。
     * {@code src/<name>/AndroidManifest.xml} の {@code <name>} (main/debug/release/flavor)。
     * 該当しなければ {@code "main"}。
     */
    static String inferSourceSet(File manifestFile) {
        File parent = manifestFile.getParentFile();
        if (parent == null) {
            return "main";
        }
        File grand = parent.getParentFile();
        if (grand != null && "src".equals(grand.getName())) {
            return parent.getName();
        }
        return "main";
    }

    /**
     * ファイルのプロジェクトルートからの相対位置からモジュール名を推定する。
     * 例: {@code /root/app/build.gradle} → {@code app}、
     * {@code /root/core/common/build.gradle} → {@code core:common}、
     * {@code /root/build.gradle} → {@code :root}、
     * {@code /root/app/src/main/AndroidManifest.xml} → {@code app}
     * (src/main 以下はモジュール内のソースセットなのでモジュール名から除外)。
     */
    public static String inferModuleName(File root, File f) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = f.getCanonicalPath();
            if (filePath.startsWith(rootPath)) {
                String rel = filePath.substring(rootPath.length());
                if (rel.startsWith(File.separator)) {
                    rel = rel.substring(1);
                }
                // ファイル名を除いた親ディレクトリのパスを取り出す
                int lastSep = rel.lastIndexOf(File.separatorChar);
                if (lastSep < 0) {
                    return ":root";
                }
                String dir = rel.substring(0, lastSep);
                // src/main, src/debug, src/<flavor> 以降を取り除く
                int srcIdx = dir.indexOf(File.separator + "src" + File.separator);
                if (srcIdx >= 0) {
                    dir = dir.substring(0, srcIdx);
                } else if (dir.equals("src") || dir.startsWith("src" + File.separator)) {
                    dir = "";
                }
                if (dir.isEmpty()) {
                    return ":root";
                }
                return dir.replace(File.separatorChar, ':');
            }
        } catch (IOException ignore) {
            // フォールバック
        }
        File parent = f.getParentFile();
        return parent == null ? ":root" : parent.getName();
    }

    private AndroidProjectAnalyzer() {
    }
}
