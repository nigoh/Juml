// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.io.File;

/**
 * Graphviz dot バイナリを検出し {@link PlantUmlRenderer} に通知するユーティリティ。
 *
 * <p>検索順序:
 * <ol>
 *   <li>{@code net.sourceforge.plantuml.GRAPHVIZ_DOT} システムプロパティが既に設定済み</li>
 *   <li>{@code GRAPHVIZ_DOT} 環境変数が設定済み</li>
 *   <li>jar 隣接の {@code graphviz/<platform>/dot[.exe]} (同梱バイナリ)</li>
 *   <li>システム PATH 上の {@code dot[.exe]}</li>
 * </ol>
 * いずれも見つからなければ何もしない (Smetana フォールバックを維持)。</p>
 */
public final class GraphvizLocator {

    static final String PLANTUML_DOT_PROP = "net.sourceforge.plantuml.GRAPHVIZ_DOT";

    /** {@link #init(File)} に渡された jar ディレクトリ。{@link #redetect()} の同梱検索で再利用する。 */
    private static volatile File cachedJarDir;

    private GraphvizLocator() {
    }

    /**
     * dot バイナリを検索し、見つかれば PlantUML のシステムプロパティを設定して
     * {@link PlantUmlRenderer#setGraphvizAvailable(boolean)} を true にする。
     *
     * @param jarDir jar ファイルが置かれているディレクトリ。null の場合は同梱バイナリ検索をスキップ。
     */
    public static void init(File jarDir) {
        cachedJarDir = jarDir;
        // 設定値は「存在するだけ」で信用せず、実行可能かを必ず検証する。実在しない dot を
        // 有効扱いにすると、レンダリング時に svek が起動失敗し要素の無い SVG が生成される。
        String prop = System.getProperty(PLANTUML_DOT_PROP);
        if (prop != null) {
            if (new File(prop).canExecute()) {
                PlantUmlRenderer.setGraphvizAvailable(true);
                logResolved("system property " + PLANTUML_DOT_PROP, prop);
                return;
            }
            juml.util.AppLog.warn(juml.util.ErrorCode.UML_R008, "GraphvizLocator",
                    "system property " + PLANTUML_DOT_PROP
                            + " points to a non-executable dot — ignoring: " + prop);
        }
        String env = System.getenv("GRAPHVIZ_DOT");
        if (env != null) {
            if (new File(env).canExecute()) {
                // PlantUML 側にも同じパスを明示し、環境変数と食い違わないようにする
                System.setProperty(PLANTUML_DOT_PROP, env);
                PlantUmlRenderer.setGraphvizAvailable(true);
                logResolved("GRAPHVIZ_DOT env", env);
                return;
            }
            juml.util.AppLog.warn(juml.util.ErrorCode.UML_R008, "GraphvizLocator",
                    "GRAPHVIZ_DOT env points to a non-executable dot — ignoring: " + env);
        }
        // jarDir が null (jpackage 等でコード位置が取れない環境) でも、
        // カレントディレクトリ基点の探索は行える。
        File bundled = findBundledDot(jarDir);
        if (bundled != null) {
            System.setProperty(PLANTUML_DOT_PROP, bundled.getAbsolutePath());
            PlantUmlRenderer.setGraphvizAvailable(true);
            logResolved("bundled binary", bundled.getAbsolutePath());
            return;
        }
        String systemDot = findSystemDot();
        if (systemDot != null) {
            System.setProperty(PLANTUML_DOT_PROP, systemDot);
            PlantUmlRenderer.setGraphvizAvailable(true);
            logResolved("PATH", systemDot);
            return;
        }
        // 見つからない場合も、どのレイアウトエンジンで描画するかをログに残す
        // (描画失敗の報告時に環境を切り分けやすくするため)。
        juml.util.AppLog.info("GraphvizLocator",
                "Graphviz dot not found — using bundled Smetana layout engine");
    }

    /** dot の解決元とパスを AppLog に記録する。 */
    private static void logResolved(String via, String path) {
        juml.util.AppLog.info("GraphvizLocator",
                "Graphviz dot resolved via " + via + ": " + path);
    }

    /**
     * 起動後にユーザー操作で dot を再検出する。既に有効なパスが設定済みならそれを尊重し、
     * そうでなければ環境変数 → 同梱バイナリ → PATH の順で再スキャンする。見つかれば
     * プロパティを設定して {@link PlantUmlRenderer#setGraphvizAvailable(boolean)} を true にし
     * true を返す。見つからなければ false。
     *
     * <p>{@link #init(File)} と異なり、設定済みプロパティが実行不能になっている場合は無視して
     * 再探索する（インストール後の有効化を確実にするため）。</p>
     */
    public static boolean redetect() {
        String prop = System.getProperty(PLANTUML_DOT_PROP);
        if (prop != null && new File(prop).canExecute()) {
            PlantUmlRenderer.setGraphvizAvailable(true);
            return true;
        }
        String env = System.getenv("GRAPHVIZ_DOT");
        if (env != null && new File(env).canExecute()) {
            System.setProperty(PLANTUML_DOT_PROP, env);
            PlantUmlRenderer.setGraphvizAvailable(true);
            return true;
        }
        File bundled = findBundledDot(cachedJarDir);
        if (bundled != null) {
            System.setProperty(PLANTUML_DOT_PROP, bundled.getAbsolutePath());
            PlantUmlRenderer.setGraphvizAvailable(true);
            return true;
        }
        String systemDot = findSystemDot();
        if (systemDot != null) {
            System.setProperty(PLANTUML_DOT_PROP, systemDot);
            PlantUmlRenderer.setGraphvizAvailable(true);
            return true;
        }
        return false;
    }

    /**
     * ユーザーが明示的に選択した dot 実行ファイルを使うよう設定する。実行可能なファイルなら
     * プロパティを設定して {@link PlantUmlRenderer#setGraphvizAvailable(boolean)} を true にし
     * true を返す。null・非ファイル・実行不能なら何もせず false。
     */
    public static boolean useDotBinary(File dot) {
        if (dot == null || !dot.isFile() || !dot.canExecute()) {
            return false;
        }
        System.setProperty(PLANTUML_DOT_PROP, dot.getAbsolutePath());
        PlantUmlRenderer.setGraphvizAvailable(true);
        return true;
    }

    /**
     * 同梱 dot バイナリ {@code graphviz/<platform>/dot[.exe]} を探す。見つからなければ null。
     *
     * <p>配布 zip では jar と同階層に {@code graphviz/} が展開されるが、
     * リポジトリから直接実行した場合 (jar は {@code build/libs/}) や
     * zip の展開ディレクトリ外から起動した場合も拾えるよう、
     * {@code <jarDir>} → {@code <jarDir>/bundle} → カレントディレクトリ →
     * {@code <cwd>/bundle} の順に基点を変えて探索する。</p>
     */
    static File findBundledDot(File jarDir) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = normalizeArch(System.getProperty("os.arch", ""));
        String exe;
        String platform;
        if (os.contains("win")) {
            platform = "windows-" + arch;
            exe = "dot.exe";
        } else if (os.contains("mac")) {
            platform = "mac-" + arch;
            exe = "dot";
        } else {
            platform = "linux-" + arch;
            exe = "dot";
        }
        for (File base : bundleSearchBases(jarDir)) {
            File candidate = new File(base,
                    "graphviz" + File.separator + platform + File.separator + exe);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
            // プラットフォーム非区別のフォールバック
            candidate = new File(base, "graphviz" + File.separator + exe);
            if (candidate.isFile() && candidate.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 同梱バイナリ探索の基点ディレクトリ列。jar 隣接 → jar 隣接の bundle/ →
     * カレントディレクトリ → cwd の bundle/ の順 (重複と null は除外)。
     */
    public static java.util.List<File> bundleSearchBases(File jarDir) {
        java.util.List<File> bases = new java.util.ArrayList<>();
        if (jarDir != null) {
            bases.add(jarDir);
            bases.add(new File(jarDir, "bundle"));
        }
        String cwd = System.getProperty("user.dir");
        if (cwd != null && !cwd.isEmpty()) {
            File c = new File(cwd);
            if (jarDir == null || !c.equals(jarDir)) {
                bases.add(c);
                bases.add(new File(c, "bundle"));
            }
        }
        return bases;
    }

    /** PATH を検索して dot バイナリのフルパスを返す。見つからなければ null。 */
    static String findSystemDot() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exe = isWin ? "dot.exe" : "dot";
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
