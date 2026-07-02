// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import java.io.File;

/**
 * Doxygen 実行バイナリを検出するユーティリティ。
 *
 * <p>{@link juml.core.formats.uml.GraphvizLocator} と同じ発想で、Doxygen は
 * 外部ネイティブバイナリ (C++) なので JVM 内呼び出しはできない。リリース時に
 * {@code bundle/doxygen/<platform>/doxygen[.exe]} を同梱する方針だが、開発・CI 環境では
 * 同梱が無いことが多いため PATH / 環境変数フォールバックも併設する。</p>
 *
 * <p>検索順序:
 * <ol>
 *   <li>{@link #useDoxygenBinary(File)} などで明示設定済みのパス</li>
 *   <li>{@code DOXYGEN_BINARY} 環境変数</li>
 *   <li>jar 隣接の {@code doxygen/<platform>/doxygen[.exe]} (同梱バイナリ)</li>
 *   <li>システム PATH 上の {@code doxygen[.exe]}</li>
 * </ol>
 * いずれも見つからなければ {@link #isAvailable()} は false を返す。</p>
 */
public final class DoxygenLocator {

    /** 解決済みの doxygen 実行ファイルの絶対パス。未解決なら null。 */
    private static volatile String resolvedPath;

    /** {@link #init(File)} に渡された jar ディレクトリ。{@link #redetect()} の同梱検索で再利用する。 */
    private static volatile File cachedJarDir;

    private DoxygenLocator() {
    }

    /**
     * doxygen バイナリを検索し、見つかればパスを記憶する。
     *
     * @param jarDir jar ファイルが置かれているディレクトリ。null の場合は同梱バイナリ検索をスキップ。
     */
    public static void init(File jarDir) {
        cachedJarDir = jarDir;
        if (resolvedPath != null && new File(resolvedPath).canExecute()) {
            return;
        }
        resolvedPath = detect(jarDir);
    }

    /**
     * 起動後にユーザー操作で再検出する。既に有効なパスを記憶していればそれを尊重し、
     * そうでなければ環境変数 → 同梱バイナリ → PATH の順で再スキャンする。
     *
     * @return 見つかれば true。
     */
    public static boolean redetect() {
        if (resolvedPath != null && new File(resolvedPath).canExecute()) {
            return true;
        }
        resolvedPath = detect(cachedJarDir);
        return resolvedPath != null;
    }

    /**
     * ユーザーが明示的に選択した doxygen 実行ファイルを使うよう設定する。
     *
     * @return 実行可能なファイルなら true。null・非ファイル・実行不能なら false。
     */
    public static boolean useDoxygenBinary(File doxygen) {
        if (doxygen == null || !doxygen.isFile() || !doxygen.canExecute()) {
            return false;
        }
        resolvedPath = doxygen.getAbsolutePath();
        return true;
    }

    /** 検出済みの doxygen 実行ファイルの絶対パスを返す。未検出なら null。 */
    public static String getDoxygenPath() {
        return resolvedPath;
    }

    /** doxygen が利用可能か (パス解決済みかつ実行可能)。 */
    public static boolean isAvailable() {
        return resolvedPath != null && new File(resolvedPath).canExecute();
    }

    /** 環境変数 → 同梱バイナリ → PATH の順で探す。見つからなければ null。 */
    private static String detect(File jarDir) {
        String env = System.getenv("DOXYGEN_BINARY");
        if (env != null && new File(env).canExecute()) {
            return env;
        }
        // jarDir が null でもカレントディレクトリ基点の同梱探索は行える。
        File bundled = findBundledDoxygen(jarDir);
        if (bundled != null) {
            return bundled.getAbsolutePath();
        }
        return findSystemDoxygen();
    }

    /**
     * 同梱 doxygen バイナリ {@code doxygen/<platform>/doxygen[.exe]} を探す。
     * 見つからなければ null。基点は {@link
     * juml.core.formats.uml.GraphvizLocator#bundleSearchBases(File)} と同じく
     * jar 隣接 → jar 隣接の bundle/ → カレントディレクトリ → cwd の bundle/ の順。
     */
    static File findBundledDoxygen(File jarDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = normalizeArch(System.getProperty("os.arch", ""));
        String exe;
        String platform;
        if (os.contains("win")) {
            platform = "windows-" + arch;
            exe = "doxygen.exe";
        } else if (os.contains("mac")) {
            platform = "mac-" + arch;
            exe = "doxygen";
        } else {
            platform = "linux-" + arch;
            exe = "doxygen";
        }
        for (File base : juml.core.formats.uml.GraphvizLocator.bundleSearchBases(jarDir)) {
            File candidate = new File(base,
                    "doxygen" + File.separator + platform + File.separator + exe);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
            // プラットフォーム非区別のフォールバック
            candidate = new File(base, "doxygen" + File.separator + exe);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    /** PATH を検索して doxygen バイナリのフルパスを返す。見つからなければ null。 */
    static String findSystemDoxygen() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = isWin ? "doxygen.exe" : "doxygen";
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File candidate = new File(dir, exe);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }

    /** "aarch64" / "arm64" → "aarch64"、"amd64" / "x86_64" → "amd64" に正規化。 */
    private static String normalizeArch(String raw) {
        String lower = raw.toLowerCase();
        if (lower.contains("aarch") || lower.contains("arm64")) {
            return "aarch64";
        }
        return "amd64";
    }
}
