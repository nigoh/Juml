// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidManifestParser;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Apktool で逆コンパイルされたディレクトリ ({@code apktool d app.apk} の出力先) を走査し、
 * {@link ApkAnalysis} に集約する解析器。
 *
 * <p>典型的なレイアウト:</p>
 * <pre>
 *   decoded/
 *     apktool.yml
 *     AndroidManifest.xml
 *     res/
 *     smali/               com/example/... .smali
 *     smali_classes2/      (multidex の 2 つ目以降)
 *     ...
 * </pre>
 *
 * <p>{@code apktool.yml} と {@code AndroidManifest.xml} を再利用パーサで解析し、
 * {@code smali_* (multidex を含む)} 配下の全 {@code .smali} を {@link SmaliParser} に通す。ファイルを
 * 読むだけで外部プロセスは起動しない (Apktool 自体の実行はユーザに委ねる) ため、
 * Juml の「ローカル読み取り専用」方針を保つ。</p>
 */
public final class ApktoolDecodedAnalyzer {

    /** 解析オプション。 */
    public static final class Options {
        /** このプレフィックスで始まるパッケージのクラスのみ取り込む (null/空なら全件)。 */
        public String packagePrefix;
        /** コンパイラ生成 (synthetic) クラスを含めるか。既定 false。 */
        public boolean includeSynthetic;
        /** 取り込むクラス数の上限 (0 なら無制限)。巨大 APK の暴発を防ぐ安全弁。 */
        public int maxClasses;
    }

    private ApktoolDecodedAnalyzer() {
    }

    /**
     * 指定ディレクトリが Apktool の逆コンパイル出力らしいかを判定する。
     * {@code apktool.yml} があるか、もしくは {@code AndroidManifest.xml} と
     * {@code smali_* (multidex を含む)} ディレクトリが揃っていれば true。
     */
    public static boolean isApktoolDecodedDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        if (new File(dir, "apktool.yml").isFile()) {
            return true;
        }
        boolean manifest = new File(dir, "AndroidManifest.xml").isFile();
        return manifest && !smaliRoots(dir).isEmpty();
    }

    /** デフォルトオプションで解析する。 */
    public static ApkAnalysis analyze(File dir, ErrorListener listener) throws IOException {
        return analyze(dir, listener, new Options());
    }

    /**
     * Apktool 出力ディレクトリを解析する。
     *
     * @param dir      逆コンパイル出力のルート
     * @param listener パース上の注意点の通知先 (null なら silent)
     * @param opts     取り込み範囲オプション (null ならデフォルト)
     */
    public static ApkAnalysis analyze(File dir, ErrorListener listener, Options opts)
            throws IOException {
        if (dir == null || !dir.isDirectory()) {
            throw new IllegalArgumentException("not a directory: " + dir);
        }
        ErrorListener log = listener != null ? listener : ErrorListener.silent();
        Options o = opts != null ? opts : new Options();
        ApkAnalysis analysis = new ApkAnalysis();

        File yml = new File(dir, "apktool.yml");
        if (yml.isFile()) {
            analysis.setApktoolInfo(ApktoolYmlParser.parse(readFile(yml), log));
        }
        File manifestFile = new File(dir, "AndroidManifest.xml");
        if (manifestFile.isFile()) {
            AndroidManifestInfo m = AndroidManifestParser.parse(readFile(manifestFile), log);
            analysis.setManifest(m);
        }

        String prefix = o.packagePrefix == null ? "" : o.packagePrefix.trim();
        for (File smaliRoot : smaliRoots(dir)) {
            collectSmali(smaliRoot, analysis, prefix, o, log);
            if (o.maxClasses > 0 && analysis.classCount() >= o.maxClasses) {
                break;
            }
        }
        return analysis;
    }

    /** {@code smali} / {@code smali_classes2} … といったルートディレクトリ群を返す。 */
    static List<File> smaliRoots(File dir) {
        List<File> roots = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) {
            return roots;
        }
        for (File c : children) {
            if (c.isDirectory() && isSmaliRootName(c.getName())) {
                roots.add(c);
            }
        }
        roots.sort((a, b) -> a.getName().compareTo(b.getName()));
        return roots;
    }

    private static boolean isSmaliRootName(String name) {
        return "smali".equals(name) || name.startsWith("smali_");
    }

    private static void collectSmali(File root, ApkAnalysis analysis, String prefix,
                                     Options o, ErrorListener log) throws IOException {
        Path base = root.toPath();
        try (Stream<Path> stream = Files.walk(base)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".smali"))
                    .sorted()
                    .toList();
            for (Path p : files) {
                if (o.maxClasses > 0 && analysis.classCount() >= o.maxClasses) {
                    return;
                }
                String name = base.relativize(p).toString();
                SmaliClassInfo info = SmaliParser.parse(
                        new String(Files.readAllBytes(p), StandardCharsets.UTF_8), name, log);
                if (info == null) {
                    continue;
                }
                if (!o.includeSynthetic && info.isSynthetic()) {
                    continue;
                }
                if (!prefix.isEmpty() && !info.getClassName().startsWith(prefix)) {
                    continue;
                }
                analysis.getClasses().add(info);
            }
        }
    }

    private static String readFile(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
