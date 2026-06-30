// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.android.PlantUmlManifestDiagram;
import juml.core.formats.android.apk.ApkAnalysis;
import juml.core.formats.android.apk.ApkSummaryReport;
import juml.core.formats.android.apk.ApktoolDecodedAnalyzer;
import juml.core.formats.android.apk.ApktoolDecoder;
import juml.core.formats.android.apk.PlantUmlSmaliClassDiagram;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Apktool で逆コンパイルされた APK ディレクトリを起点とする CLI モード
 * ({@code --apk-summary} / {@code --apk-class-diagram} / {@code --apk}) のハンドラ群。
 *
 * <p>入力は {@code apktool d app.apk} の出力ディレクトリ。Juml はそのディレクトリ内の
 * {@code apktool.yml} / {@code AndroidManifest.xml} / {@code smali*} を読むだけで、
 * Apktool 自体の実行やネットワークアクセスは行わない。</p>
 */
public final class ApkCommands {

    private ApkCommands() {
    }

    /** {@code --apk-summary}: APK の Markdown サマリーを出力。 */
    public static void handleApkSummary(CliContext ctx, String packagePrefix)
            throws IOException {
        ApkAnalysis analysis = analyzeOrExit(ctx, packagePrefix);
        if (analysis == null) {
            return;
        }
        CliOutput.writeText(ctx.fileOut, ApkSummaryReport.toMarkdown(analysis),
                "apk-summary.md");
    }

    /** {@code --apk-smali}: クラスごとの smali 構造を Markdown で出力。 */
    public static void handleApkSmali(CliContext ctx, String packagePrefix)
            throws IOException {
        ApkAnalysis analysis = analyzeOrExit(ctx, packagePrefix);
        if (analysis == null) {
            return;
        }
        CliOutput.writeText(ctx.fileOut,
                juml.core.formats.android.apk.SmaliStructureReport.toMarkdown(analysis),
                "apk-smali.md");
    }

    /** {@code --apk-class-diagram}: smali クラス図 PlantUML を出力。 */
    public static void handleApkClassDiagram(CliContext ctx, String packagePrefix)
            throws IOException {
        ApkAnalysis analysis = analyzeOrExit(ctx, packagePrefix);
        if (analysis == null) {
            return;
        }
        PlantUmlSmaliClassDiagram.Options o = new PlantUmlSmaliClassDiagram.Options();
        if (Boolean.FALSE.equals(ctx.legendOverride)) {
            o.includeLegend = false;
        }
        CliOutput.writeUmlOutput(ctx.fileOut,
                PlantUmlSmaliClassDiagram.generate(analysis, o), "apk-class-diagram");
    }

    /** {@code --apk-sequence Class.method}: smali 本体を辿ったシーケンス図 PlantUML を出力。 */
    public static void handleApkSequence(CliContext ctx, String entry, String packagePrefix)
            throws IOException {
        if (entry == null || entry.isEmpty()) {
            System.err.println("--apk-sequence requires an entry method (e.g. Class.method).");
            System.exit(1);
            return;
        }
        ApkAnalysis analysis = analyzeOrExit(ctx, packagePrefix);
        if (analysis == null) {
            return;
        }
        juml.core.formats.android.apk.PlantUmlSmaliSequenceDiagram.Options o =
                new juml.core.formats.android.apk.PlantUmlSmaliSequenceDiagram.Options();
        o.title = entry;
        if (Boolean.FALSE.equals(ctx.legendOverride)) {
            o.includeLegend = false;
        }
        CliOutput.writeUmlOutput(ctx.fileOut,
                juml.core.formats.android.apk.PlantUmlSmaliSequenceDiagram.generate(
                        analysis, entry, o),
                "apk-sequence");
    }

    /**
     * {@code --apk}: 逆コンパイル APK ディレクトリから複数成果物を {@code -o} ディレクトリへ
     * 一括出力する (apk-summary.md / apk-class-diagram.svg / manifest-diagram.svg)。
     */
    public static void handleApkAll(CliContext ctx, String packagePrefix) throws IOException {
        File fileOut = ctx.fileOut;
        if (fileOut == null) {
            System.err.println("--apk requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory when --apk is set: " + fileOut);
            System.exit(1);
            return;
        }
        // 入力が .apk のときは一時ディレクトリではなく -o/decoded に展開して残し、
        // smali ファイルをそのまま閲覧できるようにする。
        File decodeTarget = ApktoolDecoder.looksLikeApk(ctx.fileIn)
                ? new File(fileOut, "decoded") : null;
        ApkAnalysis analysis = analyzeOrExit(ctx, packagePrefix, decodeTarget);
        if (analysis == null) {
            return;
        }
        ErrorListener listener = ctx.listener;
        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();

        progress.step("[1/3] Generating apk-summary.md");
        File summaryFile = new File(fileOut, "apk-summary.md");
        CliOutput.writeText(summaryFile, ApkSummaryReport.toMarkdown(analysis));
        progress.wrote(summaryFile);

        progress.step("[2/3] Generating apk-class-diagram.svg ("
                + analysis.classCount() + " class(es))");
        PlantUmlSmaliClassDiagram.Options clsOpts = new PlantUmlSmaliClassDiagram.Options();
        if (Boolean.FALSE.equals(ctx.legendOverride)) {
            clsOpts.includeLegend = false;
        }
        File clsFile = new File(fileOut, "apk-class-diagram.svg");
        CliOutput.renderSvgOrFallback(
                PlantUmlSmaliClassDiagram.generate(analysis, clsOpts), clsFile,
                progress, listener);

        progress.step("[3/3] Generating manifest-diagram.svg");
        File manFile = new File(fileOut, "manifest-diagram.svg");
        if (analysis.getManifest() != null) {
            PlantUmlManifestDiagram.Options manOpts = new PlantUmlManifestDiagram.Options();
            if (Boolean.FALSE.equals(ctx.legendOverride)) {
                manOpts.includeLegend = false;
            }
            CliOutput.renderSvgOrFallback(
                    PlantUmlManifestDiagram.generate(toManifestAnalysis(analysis), manOpts),
                    manFile, progress, listener);
        } else {
            System.err.println("[juml]     Skipping manifest-diagram (no AndroidManifest.xml)");
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        progress.done(fileOut, elapsedMs);
    }

    /** APK の manifest を既存の Manifest 図ジェネレータが期待する形へラップする。 */
    private static AndroidProjectAnalysis toManifestAnalysis(ApkAnalysis analysis) {
        AndroidProjectAnalysis pa = new AndroidProjectAnalysis();
        AndroidManifestInfo m = analysis.getManifest();
        if (m != null) {
            List<AndroidManifestInfo> list = new ArrayList<>();
            list.add(m);
            pa.getManifestsByModule().put("apk", list);
        }
        return pa;
    }

    /**
     * 入力を検証して解析する。入力が {@code .apk} ファイルなら同梱 Apktool で一時ディレクトリへ
     * 逆コンパイルしてから解析し、ディレクトリなら Apktool 出力とみなして直接解析する。
     * いずれにも該当しなければ stderr にエラーを出し {@code System.exit(1)} して null を返す。
     */
    private static ApkAnalysis analyzeOrExit(CliContext ctx, String packagePrefix)
            throws IOException {
        return analyzeOrExit(ctx, packagePrefix, null);
    }

    /**
     * 入力を解決して解析する。{@code decodeTarget} が非 null なら {@code .apk} はそこへ
     * 逆コンパイルして展開結果を残す (一時ディレクトリではなく既知の場所に smali を保存)。
     */
    private static ApkAnalysis analyzeOrExit(CliContext ctx, String packagePrefix,
                                             File decodeTarget) throws IOException {
        File decodedDir = resolveDecodedDir(ctx.fileIn, ctx.listener, decodeTarget);
        if (decodedDir == null) {
            return null;
        }
        ApktoolDecodedAnalyzer.Options o = new ApktoolDecodedAnalyzer.Options();
        if (packagePrefix != null && !packagePrefix.isEmpty()) {
            o.packagePrefix = packagePrefix;
        }
        return ApktoolDecodedAnalyzer.analyze(decodedDir, ctx.listener, o);
    }

    /**
     * 解析対象のディレクトリを解決する。{@code .apk} は逆コンパイルし、既に展開済みの
     * ディレクトリはそのまま返す。不正な入力なら {@code System.exit(1)} して null。
     *
     * @param decodeTarget {@code .apk} の展開先 (null なら一時ディレクトリ)。指定すると
     *                     smali を含む展開結果がその場所に残り、ユーザが直接閲覧できる。
     */
    private static File resolveDecodedDir(File fileIn, ErrorListener listener,
                                          File decodeTarget) throws IOException {
        if (ApktoolDecoder.looksLikeApk(fileIn)) {
            File decoded;
            try {
                if (decodeTarget != null) {
                    ApktoolDecoder.decode(fileIn, decodeTarget, listener);
                    decoded = decodeTarget;
                } else {
                    decoded = ApktoolDecoder.decodeToTempDir(fileIn, listener);
                }
            } catch (IOException ex) {
                // スタックトレースではなく 1 行のエラーで終了する (decode 失敗時の UX)。
                System.err.println("Failed to decompile APK: " + ex.getMessage());
                System.exit(1);
                return null;
            }
            System.err.println("[juml] Decoded " + fileIn.getName() + " -> "
                    + decoded.getPath());
            return decoded;
        }
        if (fileIn != null && fileIn.isDirectory()) {
            if (!ApktoolDecodedAnalyzer.isApktoolDecodedDir(fileIn)) {
                System.err.println("Not an apktool-decoded directory (expected apktool.yml or"
                        + " AndroidManifest.xml + smali/): " + fileIn.getPath());
                System.exit(1);
                return null;
            }
            return fileIn;
        }
        System.err.println("APK mode requires an .apk file or an apktool-decoded directory"
                + " (output of `apktool d app.apk`).");
        System.exit(1);
        return null;
    }

    /**
     * {@code --apk-decode}: {@code .apk} を {@code -o} ディレクトリへ逆コンパイルするだけの
     * モード (図は生成しない)。smali / AndroidManifest.xml / apktool.yml をそのまま取り出して
     * エディタ等で閲覧できるようにする。
     */
    public static void handleApkDecode(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (!ApktoolDecoder.looksLikeApk(fileIn)) {
            System.err.println("--apk-decode requires an .apk file as input.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--apk-decode requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory when --apk-decode is set: "
                    + fileOut);
            System.exit(1);
            return;
        }
        try {
            ApktoolDecoder.decode(fileIn, fileOut, ctx.listener);
        } catch (IOException ex) {
            System.err.println("Failed to decompile APK: " + ex.getMessage());
            System.exit(1);
            return;
        }
        int smaliCount = ApktoolDecodedAnalyzer.analyze(fileOut, ctx.listener).classCount();
        System.err.println("[juml] Decompiled " + fileIn.getName() + " -> "
                + fileOut.getPath() + " (" + smaliCount + " smali class(es))");
    }
}
