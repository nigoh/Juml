// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java;

import juml.util.CancelToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Android/通常の Gradle プロジェクトディレクトリから Java ソースファイルを再帰的に列挙する。
 *
 * <p>標準的な Gradle プロジェクトレイアウト (src/main/java) と Android プロジェクトレイアウト
 * (app/src/main/java, モジュール/src/main/java) を考慮した収集を行う。</p>
 *
 * <p>大規模プロジェクト (AOSP 級) でも詰まらないよう、内部実装は
 * {@link Files#walkFileTree} ベース。{@link Options#cancelToken} で
 * 途中キャンセル、{@link Options#maxFiles} で取り込み上限を指定できる。</p>
 *
 * <p>主な利用方法:</p>
 * <pre>{@code
 *   List<File> javaFiles = AndroidProjectScanner.scan(new File("/path/to/MyApp"));
 *   for (File f : javaFiles) {
 *       String src = AndroidProjectScanner.readFile(f);
 *       // ... UML 抽出など、ソース文字列を使った処理 ...
 *   }
 * }</pre>
 */
public final class AndroidProjectScanner {

    /** 除外するディレクトリ名 (デフォルト)。 */
    public static final Set<String> DEFAULT_EXCLUDED_DIRS;
    /** AOSP 級プロジェクト向けに追加で除外すべきディレクトリ名 (opt-in)。 */
    public static final Set<String> AOSP_EXTRA_EXCLUDED_DIRS;
    static {
        Set<String> s = new HashSet<>(Arrays.asList(
                "build", ".gradle", ".idea", ".git", "out", "bin",
                "node_modules", ".kotlin", "captures", ".cxx"));
        DEFAULT_EXCLUDED_DIRS = Collections.unmodifiableSet(s);

        Set<String> aosp = new HashSet<>(Arrays.asList(
                "prebuilts", "out-soong", ".repo", "test_mapping", ".cache"));
        AOSP_EXTRA_EXCLUDED_DIRS = Collections.unmodifiableSet(aosp);
    }

    /** スキャンオプション。 */
    public static class Options {
        /** テストソース (src/test/java, src/androidTest/java) を含める。 */
        public boolean includeTests = false;
        /** Kotlin ファイルも含める (現状は変換非対応のため、ファイルリストのみ)。 */
        public boolean includeKotlin = false;
        /** AIDL (.aidl) ファイルも含める。 */
        public boolean includeAidl = false;
        /** HIDL (.hal) ファイルも含める。AOSP の HAL レイヤ解析用。 */
        public boolean includeHidl = false;
        /**
         * VINTF manifest ({@code manifest.xml} / {@code compatibility_matrix.xml})
         * も含める。AOSP の HAL 要求/宣言解析用。AndroidManifest.xml とはルート
         * 要素で区別する (VINTF パーサ側で判定)。
         */
        public boolean includeVintf = false;
        /** Gradle ビルドスクリプト (.gradle / .gradle.kts) を含める。 */
        public boolean includeGradle = false;
        /** AndroidManifest.xml を含める。 */
        public boolean includeManifest = false;
        /** res/layout/ 配下の XML レイアウトファイルを含める。 */
        public boolean includeLayout = false;
        /** res/navigation/ 配下の Jetpack Navigation グラフ XML を含める。 */
        public boolean includeNavigation = false;
        /** res/values/ 配下の XML (strings.xml 等、文字列リソース) を含める。 */
        public boolean includeValues = false;
        /** 除外ディレクトリ名のセット。 */
        public Set<String> excludedDirs = DEFAULT_EXCLUDED_DIRS;
        /** AOSP 級プロジェクト向けの追加除外名 ({@link #AOSP_EXTRA_EXCLUDED_DIRS}) も合成する。 */
        public boolean useAospDefaults = false;
        /** 最大再帰深さ (負値で無制限)。 */
        public int maxDepth = -1;
        /** 取り込みファイル数の上限 (負値で無制限)。超過時点で走査を打ち切る。 */
        public int maxFiles = -1;
        /** シンボリックリンクを辿る。 */
        public boolean followSymlinks = false;
        /** キャンセルトークン。null なら未キャンセル扱い。 */
        public CancelToken cancelToken;
    }

    /** デフォルト Options でスキャン。 */
    public static List<File> scan(File root) {
        return scan(root, null);
    }

    /** オプション付きスキャン。 */
    public static List<File> scan(File root, Options opts) {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        Options o = (opts == null) ? new Options() : opts;
        List<File> result = new ArrayList<>();
        if (!root.exists()) {
            return result;
        }
        if (root.isFile()) {
            if (accept(root, o)) {
                result.add(root);
            }
            return result;
        }
        walk(root.toPath(), o, result);
        Collections.sort(result);
        return result;
    }

    private static void walk(Path rootPath, Options opts, List<File> result) {
        Set<FileVisitOption> visitOpts = opts.followSymlinks
                ? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
                : EnumSet.noneOf(FileVisitOption.class);
        // 旧実装の maxDepth は「root から数えた再帰深さ」。walkFileTree の maxDepth は
        // 「root を 1 と数える訪問深さ」なので +1 して換算 (無制限のときは MAX_VALUE)。
        int walkDepth = opts.maxDepth < 0 ? Integer.MAX_VALUE : opts.maxDepth + 1;
        final int maxFiles = opts.maxFiles;
        final CancelToken cancel = opts.cancelToken != null ? opts.cancelToken : CancelToken.NONE;

        try {
            Files.walkFileTree(rootPath, visitOpts, walkDepth, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (cancel.isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    // ルート自身は除外判定をスキップ
                    if (dir.equals(rootPath)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (shouldExcludeDir(dir.toFile(), opts)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (cancel.isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    File f = file.toFile();
                    if (accept(f, opts)) {
                        result.add(f);
                        if (maxFiles >= 0 && result.size() >= maxFiles) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // 権限拒否などは無視して継続
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            // ルートが読めない等。空のまま返す (既存挙動を踏襲)。
        }
    }

    private static boolean shouldExcludeDir(File dir, Options opts) {
        String name = dir.getName();
        if (opts.excludedDirs != null && opts.excludedDirs.contains(name)) {
            return true;
        }
        if (opts.useAospDefaults && AOSP_EXTRA_EXCLUDED_DIRS.contains(name)) {
            return true;
        }
        if (!opts.includeTests && isTestDir(dir)) {
            return true;
        }
        return false;
    }

    private static boolean isTestDir(File dir) {
        // src/test/... または src/androidTest/... のディレクトリを判定
        String name = dir.getName();
        if (!"test".equals(name) && !"androidTest".equals(name)) {
            return false;
        }
        File parent = dir.getParentFile();
        return parent != null && "src".equals(parent.getName());
    }

    private static boolean accept(File f, Options opts) {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".java")) {
            return true;
        }
        if (opts.includeKotlin && (name.endsWith(".kt") || name.endsWith(".kts"))) {
            return true;
        }
        if (opts.includeAidl && name.endsWith(".aidl")) {
            return true;
        }
        if (opts.includeHidl && name.endsWith(".hal")) {
            return true;
        }
        if (opts.includeGradle
                && (name.endsWith(".gradle") || name.endsWith(".gradle.kts"))) {
            return true;
        }
        if (opts.includeManifest && name.equals("androidmanifest.xml")) {
            return true;
        }
        if (opts.includeVintf
                && (name.equals("manifest.xml")
                    || name.equals("compatibility_matrix.xml"))) {
            return true;
        }
        if (opts.includeLayout && name.endsWith(".xml") && isInLayoutDir(f)) {
            return true;
        }
        if (opts.includeNavigation && name.endsWith(".xml") && isInNavigationDir(f)) {
            return true;
        }
        if (opts.includeValues && name.endsWith(".xml") && isInValuesDir(f)) {
            return true;
        }
        return false;
    }

    /**
     * 親ディレクトリ名が {@code values} または {@code values-*} (例: {@code values-ja},
     * {@code values-night}) の場合に true を返す。
     */
    static boolean isInValuesDir(File f) {
        File parent = f.getParentFile();
        if (parent == null) {
            return false;
        }
        String dir = parent.getName();
        return "values".equals(dir) || dir.startsWith("values-");
    }

    /**
     * 親ディレクトリ名が {@code layout} または {@code layout-*} (例: {@code layout-land},
     * {@code layout-sw600dp-v21}) の場合に true を返す。
     */
    private static boolean isInLayoutDir(File f) {
        File parent = f.getParentFile();
        if (parent == null) {
            return false;
        }
        String dir = parent.getName();
        return "layout".equals(dir) || dir.startsWith("layout-");
    }

    /**
     * 親ディレクトリ名が {@code navigation} または {@code navigation-*} の場合に true を返す。
     */
    static boolean isInNavigationDir(File f) {
        File parent = f.getParentFile();
        if (parent == null) {
            return false;
        }
        String dir = parent.getName();
        return "navigation".equals(dir) || dir.startsWith("navigation-");
    }

    /** ファイルを UTF-8 で読み込んで文字列として返す。 */
    public static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private AndroidProjectScanner() {
        // ユーティリティクラス
    }
}
