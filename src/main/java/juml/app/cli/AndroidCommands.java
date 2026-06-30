// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidManifestParser;
import juml.core.formats.android.AndroidNavigationGraphInfo;
import juml.core.formats.android.AndroidNavigationGraphParser;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.android.AndroidProjectAnalyzer;
import juml.core.formats.android.GradleProjectInfo;
import juml.core.formats.android.GradleScriptParser;
import juml.core.formats.android.PlantUmlComponentDiagram;
import juml.core.formats.android.PlantUmlDeepLinkDiagram;
import juml.core.formats.android.PlantUmlGradleDependencyGraph;
import juml.core.formats.android.PlantUmlManifestDiagram;
import juml.core.formats.android.PlantUmlNavigationGraphDiagram;
import juml.core.formats.android.TextSummaryReport;
import juml.core.formats.android.actions.MarkdownActionReport;
import juml.core.formats.android.actions.UiActionEntry;
import juml.core.formats.android.actions.UiActionScanner;
import juml.core.formats.android.settings.MarkdownSettingsReport;
import juml.core.formats.android.settings.PreferencesXmlParser;
import juml.core.formats.android.settings.SettingsAnalysisResult;
import juml.core.formats.android.settings.SharedPreferencesScanner;
import juml.core.formats.java.AndroidProjectScanner;
import juml.core.formats.uml.PlantUmlRenderer;
import juml.core.formats.uml.UmlGenerator;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;

/**
 * Gradle / AndroidManifest を起点とした Android プロジェクト系 CLI モード
 * ({@code -g} / {@code -m} / {@code -d} / {@code -M} / {@code -D} / {@code -G} /
 * {@code --summary} / {@code -A} / {@code --settings} / {@code --action-map}) のハンドラ群。
 */
public final class AndroidCommands {

    private AndroidCommands() {
    }

    /** {@code --gradle}: 単一 build.gradle (もしくはディレクトリ) を Markdown サマリーに変換。 */
    public static void handleGradleInput(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null) {
            System.err.println("Gradle mode requires an input file or directory.");
            System.exit(1);
            return;
        }
        if (fileIn.isDirectory()) {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
            CliOutput.writeText(fileOut, TextSummaryReport.toMarkdown(analysis),
                    "gradle-summary.md");
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            GradleProjectInfo info = GradleScriptParser.parse(content, fileIn.getName(), listener);
            AndroidProjectAnalysis fake = new AndroidProjectAnalysis();
            fake.getGradleByModule().put(fileIn.getName(), info);
            CliOutput.writeText(fileOut, TextSummaryReport.toMarkdown(fake),
                    "gradle-summary.md");
        }
    }

    /** {@code --manifest}: 単一 AndroidManifest.xml (もしくはディレクトリ) を Markdown サマリーに変換。 */
    public static void handleManifestInput(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null) {
            System.err.println("Manifest mode requires an input file or directory.");
            System.exit(1);
            return;
        }
        if (fileIn.isDirectory()) {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
            CliOutput.writeText(fileOut, TextSummaryReport.toMarkdown(analysis),
                    "manifest-summary.md");
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidManifestInfo info = AndroidManifestParser.parse(content, listener);
            AndroidProjectAnalysis fake = new AndroidProjectAnalysis();
            java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
            list.add(info);
            fake.getManifestsByModule().put(fileIn.getName(), list);
            CliOutput.writeText(fileOut, TextSummaryReport.toMarkdown(fake),
                    "manifest-summary.md");
        }
    }

    /** {@code --component-diagram}: コンポーネント図 PlantUML を生成。 */
    public static void handleComponentDiagram(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Component diagram requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        CliOutput.writeUmlOutput(fileOut,
                PlantUmlComponentDiagram.generate(analysis, o), "component-diagram");
    }

    /**
     * {@code --manifest-diagram}: AndroidManifest.xml の構造 (Application + 配下コンポーネント
     * + permissions + features) を 1 枚にまとめた PlantUML 図を生成する。
     */
    public static void handleManifestDiagram(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        if (fileIn == null) {
            System.err.println("Manifest diagram requires an input file or directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis;
        if (fileIn.isDirectory()) {
            analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidManifestInfo info = AndroidManifestParser.parse(content, listener);
            analysis = new AndroidProjectAnalysis();
            java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
            list.add(info);
            analysis.getManifestsByModule().put(fileIn.getName(), list);
        }
        PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        CliOutput.writeUmlOutput(fileOut,
                PlantUmlManifestDiagram.generate(analysis, o), "manifest-diagram");
    }

    /**
     * {@code --deeplink-diagram} / {@code -D}: VIEW + BROWSABLE を持つ Activity の
     * Deep Link / App Links を可視化する PlantUML 図を生成する。
     */
    public static void handleDeepLinkDiagram(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        if (fileIn == null) {
            System.err.println("Deep link diagram requires an input file or directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis;
        if (fileIn.isDirectory()) {
            analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidManifestInfo info = AndroidManifestParser.parse(content, listener);
            analysis = new AndroidProjectAnalysis();
            java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
            list.add(info);
            analysis.getManifestsByModule().put(fileIn.getName(), list);
        }
        PlantUmlDeepLinkDiagram.Options o = new PlantUmlDeepLinkDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        CliOutput.writeUmlOutput(fileOut,
                PlantUmlDeepLinkDiagram.generate(analysis, o), "deeplink-diagram");
    }

    /**
     * {@code --nav-graph}: Jetpack Navigation グラフ ({@code res/navigation/*.xml}) の
     * 画面遷移 (destination + action/deeplink) を可視化する PlantUML 図を生成する。
     * 入力がディレクトリならプロジェクト全体の Navigation グラフを、単一 XML なら
     * そのファイルだけを解析する。複数グラフは {@code @startuml} ブロックを連結して出力する。
     */
    public static void handleNavGraph(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        if (fileIn == null) {
            System.err.println("Navigation graph requires an input file or directory.");
            System.exit(1);
            return;
        }
        java.util.List<AndroidNavigationGraphInfo> graphs = new java.util.ArrayList<>();
        if (fileIn.isDirectory()) {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
            graphs.addAll(analysis.allNavigationGraphs());
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidNavigationGraphInfo info =
                    AndroidNavigationGraphParser.parse(content, listener);
            if (info != null) {
                graphs.add(info);
            }
        }
        if (graphs.isEmpty()) {
            System.err.println("No Jetpack Navigation graphs (res/navigation/*.xml) found.");
            System.exit(1);
            return;
        }
        PlantUmlNavigationGraphDiagram.Options o = new PlantUmlNavigationGraphDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        // 複数グラフは個別の @startuml ブロックとして連結 (PlantUML は複数図を扱える)
        StringBuilder sb = new StringBuilder();
        for (AndroidNavigationGraphInfo g : graphs) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(PlantUmlNavigationGraphDiagram.generate(g, o));
        }
        CliOutput.writeUmlOutput(fileOut, sb.toString(), "nav-graph");
    }

    /** {@code --dependency-graph}: Gradle 依存グラフ PlantUML を生成。 */
    public static void handleDependencyGraph(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Dependency graph requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        CliOutput.writeUmlOutput(fileOut,
                PlantUmlGradleDependencyGraph.generate(analysis, o), "dependency-graph");
    }

    /** {@code --summary}: プロジェクト全体の Markdown サマリーを生成。 */
    public static void handleSummary(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Summary requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        CliOutput.writeText(fileOut, TextSummaryReport.toMarkdown(analysis),
                "summary.md");
    }

    /**
     * {@code --settings}: プロジェクト全体の SharedPreferences / DataStore 読み書きと
     * res/xml/ の Preference XML 定義を Markdown レポートとして出力する。
     */
    public static void handleSettings(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--settings requires a project directory as input.");
            System.exit(1);
            return;
        }
        SharedPreferencesScanner scanner = new SharedPreferencesScanner();
        SettingsAnalysisResult result = scanner.analyzeProject(fileIn, ctx.includeTests);
        PreferencesXmlParser xmlParser = new PreferencesXmlParser();
        for (juml.core.formats.android.settings.PreferenceXmlEntry e
                : xmlParser.analyzeProject(fileIn)) {
            result.addXmlEntry(e);
        }
        CliOutput.writeText(fileOut, MarkdownSettingsReport.render(result), "settings.md");
    }

    /**
     * {@code --action-map}: プロジェクト内の onClick ハンドラ・Compose クリックイベント・
     * XML android:onClick を検出して Markdown レポートを出力する。
     */
    public static void handleActionMap(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--action-map requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<UiActionEntry> entries =
                new UiActionScanner().analyzeProject(fileIn, ctx.includeTests);
        CliOutput.writeText(fileOut, MarkdownActionReport.render(entries),
                "action-map.md");
    }

    /**
     * {@code --all}: プロジェクトディレクトリを入力に、複数種類の成果物を出力ディレクトリへ一括書き出し。
     * <ul>
     *   <li>{@code summary.md} - Markdown プロジェクトサマリー</li>
     *   <li>{@code class-diagram.svg} - PlantUML クラス図 (manifest 自動マージ)</li>
     *   <li>{@code component-diagram.svg} - PlantUML Android コンポーネント図</li>
     *   <li>{@code manifest-diagram.svg} - PlantUML AndroidManifest 図 (Application + 配下コンポーネント)</li>
     *   <li>{@code deeplink-diagram.svg} - PlantUML Deep Link / App Links 図</li>
     *   <li>{@code dependency-graph.svg} - PlantUML Gradle 依存グラフ</li>
     *   <li>{@code methods.txt} - シーケンス図の起点候補一覧 ({@code Class.method}) </li>
     *   <li>{@code sequence-diagrams/}{@code <Class.method>.svg} - Android ライフサイクル
     *       (Activity/Service の {@code onCreate} 等) を起点に自動生成したシーケンス図</li>
     * </ul>
     * <p>すべて同梱ライブラリのみで完結するため、PlantUML/dot のインストールは不要。
     * ライフサイクル外のメソッドからシーケンス図を作る場合は {@code methods.txt} を
     * 参考に {@code -q Class.method -o seq.svg} で個別生成する。</p>
     */
    public static void handleAll(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        boolean mergeManifest = ctx.mergeManifest;
        UmlOverrides overrides = ctx.overrides;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--all requires a project directory.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--all requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory when --all is set: " + fileOut);
            System.exit(1);
            return;
        }

        // --all は時間がかかる複合処理なので、-v の有無に関わらず進捗ログを stderr に
        // 出す。ユーザが渡したリスナー (verbose なら stderr / silent なら no-op) は
        // パース警告等の付随情報として残しつつ、進捗ログは別経路で確実に表示する。
        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();
        progress.step("Analyzing project: " + fileIn.getAbsolutePath());

        // プロジェクト解析を 1 回だけ実行して再利用する
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);

        // 1) Markdown サマリー
        progress.step("[1/8] Generating summary.md");
        File summaryFile = new File(fileOut, "summary.md");
        CliOutput.writeText(summaryFile, TextSummaryReport.toMarkdown(analysis));
        progress.wrote(summaryFile);
        listener.onError(null, -1, "wrote " + summaryFile.getPath());

        // 2) コンポーネント図 (SVG)
        progress.step("[2/8] Generating component-diagram.svg");
        PlantUmlComponentDiagram.Options compOpts = new PlantUmlComponentDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            compOpts.includeLegend = false;
        }
        File compFile = new File(fileOut, "component-diagram.svg");
        CliOutput.renderSvgOrFallback(PlantUmlComponentDiagram.generate(analysis, compOpts),
                compFile, progress, listener);

        // 3) Manifest 図 (SVG) — Application + 配下コンポーネントを 1 枚で可視化
        progress.step("[3/8] Generating manifest-diagram.svg");
        PlantUmlManifestDiagram.Options manOpts = new PlantUmlManifestDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            manOpts.includeLegend = false;
        }
        File manFile = new File(fileOut, "manifest-diagram.svg");
        CliOutput.renderSvgOrFallback(PlantUmlManifestDiagram.generate(analysis, manOpts),
                manFile, progress, listener);

        // 4) Deep Link 図 (SVG) — VIEW + BROWSABLE intent-filter の URI 入口を可視化
        progress.step("[4/8] Generating deeplink-diagram.svg");
        PlantUmlDeepLinkDiagram.Options dlOpts = new PlantUmlDeepLinkDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            dlOpts.includeLegend = false;
        }
        File dlFile = new File(fileOut, "deeplink-diagram.svg");
        CliOutput.renderSvgOrFallback(PlantUmlDeepLinkDiagram.generate(analysis, dlOpts),
                dlFile, progress, listener);

        // 5) 依存グラフ (SVG)
        progress.step("[5/8] Generating dependency-graph.svg");
        PlantUmlGradleDependencyGraph.Options depOpts = new PlantUmlGradleDependencyGraph.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            depOpts.includeLegend = false;
        }
        File depFile = new File(fileOut, "dependency-graph.svg");
        CliOutput.renderSvgOrFallback(PlantUmlGradleDependencyGraph.generate(analysis, depOpts),
                depFile, progress, listener);

        // 6) クラス図 (SVG)。UmlGenerator は内部で再走査するが、manifest 連携のため別経路。
        progress.step("[6/8] Generating class-diagram.svg (scanning Java/AIDL)");
        java.util.List<juml.core.formats.uml.JavaClassInfo> infos =
                UmlGenerator.extractFromProject(fileIn, ctx.scanOptions(), listener,
                        mergeManifest);
        juml.core.formats.uml.PlantUmlClassDiagram.Options clsOpts =
                new juml.core.formats.uml.PlantUmlClassDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            clsOpts.includeLegend = false;
        }
        if (overrides != null) {
            overrides.applyTo(clsOpts);
        }
        File clsFile = new File(fileOut, "class-diagram.svg");
        String clsPuml = juml.core.formats.uml.PlantUmlClassDiagram.generate(infos, clsOpts);
        try {
            PlantUmlRenderer.renderSvg(clsPuml, clsFile);
            progress.wrote(clsFile, "(" + infos.size() + " class(es))");
            listener.onError(null, -1, "wrote " + clsFile.getPath());
        } catch (juml.core.formats.uml.PlantUmlRenderFailedException ex) {
            File clsPumlFallback = CliOutput.siblingPumlFor(clsFile);
            CliOutput.writeText(clsPumlFallback, clsPuml);
            System.err.println("[juml]     -> " + clsFile.getName()
                    + " FAILED: " + ex.getMessage());
            System.err.println("[juml]        Saved " + clsPumlFallback.getName()
                    + " -- render externally with: plantuml -tsvg "
                    + clsPumlFallback.getName());
        }

        // 7) シーケンス図の起点候補一覧
        progress.step("[7/8] Generating methods.txt (sequence diagram entry candidates)");
        java.util.List<juml.core.formats.uml.PlantUmlSequenceDiagram.Candidate> candidates =
                juml.core.formats.uml.PlantUmlSequenceDiagram.listCandidates(infos);
        StringBuilder methodsBuf = new StringBuilder();
        for (juml.core.formats.uml.PlantUmlSequenceDiagram.Candidate c : candidates) {
            methodsBuf.append(c.getEntry())
                    .append("\t(").append(c.callCount).append(" call")
                    .append(c.callCount == 1 ? "" : "s")
                    .append(", ").append(c.visibility.name().toLowerCase()).append(")\n");
        }
        File methodsFile = new File(fileOut, "methods.txt");
        CliOutput.writeText(methodsFile, methodsBuf.toString());
        progress.wrote(methodsFile, "(" + candidates.size() + " method(s))");
        listener.onError(null, -1, "wrote " + methodsFile.getPath());

        // 8) Android ライフサイクルメソッドを自動的に起点としたシーケンス図
        progress.step("[8/8] Generating sequence-diagrams/ (Android lifecycle entry points)");
        File seqDir = new File(fileOut, "sequence-diagrams");
        if (!seqDir.exists() && !seqDir.mkdirs()) {
            System.err.println("[juml]     Skipping sequence-diagrams (cannot create dir)");
        } else {
            int seqCount = UmlCommands.generateLifecycleSequenceDiagrams(infos, seqDir,
                    legendOverride, overrides, progress, listener);
            progress.wrote(seqDir, "(" + seqCount + " diagram(s), .puml + .svg)");
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        progress.done(fileOut, elapsedMs);
    }
}
