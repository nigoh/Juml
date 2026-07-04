// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import juml.util.ErrorListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 検出済みの Doxygen バイナリ ({@link DoxygenLocator}) を外部プロセスとして起動し、
 * 対象 Java プロジェクトの XML ドキュメント ({@code GENERATE_XML=YES}) を生成する。
 *
 * <p>{@link juml.core.formats.android.apk.ApktoolDecoder} (JVM 内呼び出し) と異なり、Doxygen は
 * ネイティブバイナリなので {@link ProcessBuilder} で起動する。生成された XML は
 * {@link DoxygenXmlParser} でパースしてネイティブ表示する (HTML は生成しない)。</p>
 */
public final class DoxygenRunner {

    private DoxygenRunner() {
    }

    /**
     * 一時ディレクトリへ XML を生成し、{@code index.xml} を含む {@code xml} ディレクトリを返す。
     *
     * @param projectRoot Java プロジェクトのルート
     * @param listener    doxygen の出力・進捗を通知するリスナー (null なら silent)
     * @return 生成された XML 出力ディレクトリ ({@code <tmp>/xml})
     * @throws IOException doxygen 未検出・実行失敗・出力欠落時
     */
    public static File run(File projectRoot, ErrorListener listener) throws IOException {
        File outDir = Files.createTempDirectory("juml-doxygen-").toFile();
        return run(projectRoot, outDir, listener);
    }

    /**
     * {@link DoxygenCache} を使い、対象プロジェクトの {@code *.java} に変更が無ければ前回生成した
     * XML を再利用する。変更があれば doxygen を再実行してキャッシュを更新する。
     *
     * @param projectRoot Java プロジェクトのルート
     * @param listener    doxygen の出力・進捗を通知するリスナー (null なら silent)
     * @return 利用可能な XML 出力ディレクトリ ({@code <cache>/xml})
     * @throws IOException doxygen 未検出・実行失敗・出力欠落時
     */
    public static File runCached(File projectRoot, ErrorListener listener) throws IOException {
        ErrorListener log = listener != null ? listener : ErrorListener.silent();
        File cacheDir = DoxygenCache.cacheDirFor(projectRoot);
        String signature = DoxygenCache.signature(projectRoot, DoxygenLocator.getDoxygenPath());
        if (DoxygenCache.isFresh(cacheDir, signature)) {
            log.onError("doxygen", -1, "reusing cached XML in " + cacheDir);
            return new File(cacheDir, "xml");
        }
        // 旧キャッシュを掃除してから再生成する (古い XML が残らないように)。
        deleteRecursively(new File(cacheDir, "xml"));
        File xmlDir = run(projectRoot, cacheDir, log);
        DoxygenCache.writeStamp(cacheDir, signature);
        return xmlDir;
    }

    /** ディレクトリを再帰削除する (存在しなければ何もしない)。 */
    private static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        // 失敗は無視 (次の doxygen 実行で上書きされる)。
        if (!dir.delete()) {
            dir.deleteOnExit();
        }
    }

    /**
     * 指定した出力ディレクトリへ XML を生成し、その {@code xml} サブディレクトリを返す。
     *
     * @param projectRoot Java プロジェクトのルート
     * @param outDir      出力先ディレクトリ (Doxyfile と {@code xml/} を置く)
     * @param listener    doxygen の出力・進捗を通知するリスナー (null なら silent)
     * @throws IOException doxygen 未検出・実行失敗・出力欠落時
     */
    public static File run(File projectRoot, File outDir, ErrorListener listener) throws IOException {
        ErrorListener log = listener != null ? listener : ErrorListener.silent();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            throw new IOException("project root is not a directory: " + projectRoot);
        }
        String doxygen = DoxygenLocator.getDoxygenPath();
        if (doxygen == null) {
            throw new IOException("doxygen binary not found."
                    + " Install doxygen and ensure it is on PATH, or set DOXYGEN_BINARY.");
        }
        Files.createDirectories(outDir.toPath());
        File doxyfile = new File(outDir, "Doxyfile");
        Files.writeString(doxyfile.toPath(), buildDoxyfile(projectRoot, outDir), StandardCharsets.UTF_8);

        ProcessBuilder pb = new ProcessBuilder(doxygen, doxyfile.getAbsolutePath());
        pb.directory(outDir);
        pb.redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            throw new IOException("failed to start doxygen: " + ex.getMessage(), ex);
        }
        // doxygen の標準出力/エラーを読み捨てつつ、致命的行だけ通知する。
        // 読み取り/待機のどの段階で例外が飛んでも子プロセスを孤児にしないよう、
        // finally で生存していれば強制終了する (成功時は既に終了済みなので no-op)。
        try {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("error:") || line.contains(": error:")) {
                        log.onError("doxygen", -1, line);
                    }
                }
            }
            int exit;
            try {
                exit = process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("doxygen run interrupted", ex);
            }
            if (exit != 0) {
                throw new IOException("doxygen exited with code " + exit);
            }
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        File xmlDir = new File(outDir, "xml");
        if (!new File(xmlDir, "index.xml").isFile()) {
            throw new IOException("doxygen produced no XML output in " + xmlDir);
        }
        return xmlDir;
    }

    /**
     * Java プロジェクト向けの最小 Doxyfile を組み立てる。HTML/LaTeX は無効化し XML のみ生成、
     * dot (Graphviz) も不要 ({@code HAVE_DOT=NO})。{@code EXTRACT_ALL=YES} で
     * doc コメントが無いシンボルも拾い、全体構造をツリー表示できるようにする。
     */
    static String buildDoxyfile(File projectRoot, File outDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT_NAME = ").append(quote(projectRoot.getName())).append('\n');
        sb.append("OUTPUT_DIRECTORY = ").append(quote(outDir.getAbsolutePath())).append('\n');
        sb.append("INPUT = ").append(quote(projectRoot.getAbsolutePath())).append('\n');
        sb.append("RECURSIVE = YES\n");
        sb.append("FILE_PATTERNS = *.java\n");
        sb.append("OPTIMIZE_OUTPUT_JAVA = YES\n");
        sb.append("EXTRACT_ALL = YES\n");
        sb.append("EXTRACT_PRIVATE = YES\n");
        sb.append("EXTRACT_STATIC = YES\n");
        sb.append("GENERATE_HTML = NO\n");
        sb.append("GENERATE_LATEX = NO\n");
        sb.append("GENERATE_XML = YES\n");
        sb.append("XML_OUTPUT = xml\n");
        sb.append("XML_PROGRAMLISTING = NO\n");
        sb.append("HAVE_DOT = NO\n");
        sb.append("QUIET = YES\n");
        sb.append("WARNINGS = NO\n");
        sb.append("WARN_IF_UNDOCUMENTED = NO\n");
        // ビルド成果物・依存ソースを除外してノイズと実行時間を抑える。
        sb.append("EXCLUDE_PATTERNS = */build/* */.gradle/* */test/* */generated/*\n");
        return sb.toString();
    }

    /** Doxyfile の値をダブルクォートで囲む (空白を含むパス対策)。内部の {@code "} はエスケープ。 */
    private static String quote(String value) {
        return '"' + value.replace("\"", "\\\"") + '"';
    }
}
