// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.dataflow.MarkdownDataFlowReport;
import juml.core.dataflow.PlantUmlErDiagram;
import juml.core.dataflow.RoomAnalyzer;
import juml.core.formats.java.AndroidProjectScanner;
import juml.core.formats.uml.UmlGenerator;
import juml.core.impact.ImpactAnalyzer;
import juml.core.impact.ImpactGraph;
import juml.core.impact.MarkdownImpactReport;
import juml.core.impact.PlantUmlImpactDiagram;
import juml.core.insights.InsightsAnalyzer;
import juml.core.insights.InsightsModel;
import juml.core.insights.MarkdownInsightsReport;
import juml.core.insights.PlantUmlPackageCycleDiagram;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceIndexBuilder;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;
import juml.core.screen.IntentNavigationDetector;
import juml.core.screen.MarkdownScreenFlowReport;
import juml.core.screen.PlantUmlScreenFlowDiagram;
import juml.core.screen.ScreenTransition;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;

/**
 * 参照・影響・差分・データフローなどの解析系 CLI モード
 * ({@code --impact} / {@code --ref-find} / {@code --er-diagram} / {@code --data-flow} /
 * {@code --screen-flow} / {@code --func-diff} / {@code --insights}) のハンドラ群。
 */
public final class AnalysisCommands {

    private AnalysisCommands() {
    }

    /**
     * {@code --impact <FQN[.method]>}: 指定シンボルを起点に逆参照を辿り、
     * 影響を受けるクラスを推移閉包で列挙する。
     *
     * <p>出力先:</p>
     * <ul>
     *   <li>{@code -o foo.md}: Markdown レポート 1 ファイル</li>
     *   <li>{@code -o foo.puml}: PlantUML 影響図 1 ファイル</li>
     *   <li>{@code -o foo}: 拡張子なし → {@code foo.md} と {@code foo.puml} を両方書く</li>
     *   <li>{@code -o} 省略: Markdown を標準出力</li>
     * </ul>
     */
    public static void handleImpact(CliContext ctx, String target, int depth)
            throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--impact requires a project directory as input.");
            System.exit(1);
            return;
        }
        if (target == null || target.isEmpty()) {
            System.err.println("--impact requires a symbol target (FQN or FQN.method).");
            System.exit(1);
            return;
        }
        ReferenceIndex refIndex = buildReferenceIndex(ctx);

        ImpactGraph graph;
        ImpactAnalyzer analyzer = new ImpactAnalyzer(refIndex);
        // FQN.method の区別: 最後のドット以降が小文字始まりかつクラスが存在 → method
        int lastDot = target.lastIndexOf('.');
        if (lastDot > 0 && lastDot < target.length() - 1) {
            String maybeOwner = target.substring(0, lastDot);
            String maybeMember = target.substring(lastDot + 1);
            if (!maybeMember.isEmpty()
                    && Character.isLowerCase(maybeMember.charAt(0))) {
                graph = analyzer.analyzeMethod(maybeOwner, maybeMember, depth);
            } else {
                graph = analyzer.analyzeClass(target, depth);
            }
        } else {
            graph = analyzer.analyzeClass(target, depth);
        }

        String markdown = MarkdownImpactReport.render(graph);
        String puml = PlantUmlImpactDiagram.render(graph);
        CliOutput.writeImpactOutput(fileOut, markdown, puml, "impact");
    }

    /**
     * {@code --ref-find <FQN[.member]>}: シンボルを参照している箇所を 1 行ずつ
     * プレーンテキストで列挙する (grep 互換)。
     */
    public static void handleRefFind(CliContext ctx, String target) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--ref-find requires a project directory as input.");
            System.exit(1);
            return;
        }
        if (target == null || target.isEmpty()) {
            System.err.println("--ref-find requires a symbol target.");
            System.exit(1);
            return;
        }
        ReferenceIndex refIndex = buildReferenceIndex(ctx);

        java.util.List<ReferenceSite> sites;
        int lastDot = target.lastIndexOf('.');
        if (lastDot > 0 && lastDot < target.length() - 1) {
            String maybeOwner = target.substring(0, lastDot);
            String maybeMember = target.substring(lastDot + 1);
            if (!maybeMember.isEmpty()
                    && Character.isLowerCase(maybeMember.charAt(0))) {
                sites = refIndex.sitesByMember(
                        ReferenceKey.Kind.METHOD, maybeOwner, maybeMember);
            } else {
                sites = refIndex.sitesForClass(target);
            }
        } else {
            sites = refIndex.sitesForClass(target);
        }

        StringBuilder sb = new StringBuilder();
        for (ReferenceSite s : sites) {
            sb.append(s.getCallerFqn());
            if (!s.getCallerMethod().isEmpty()) {
                sb.append('.').append(s.getCallerMethod());
            }
            sb.append('\t').append(s.getKind().name());
            if (!s.getFile().isEmpty()) {
                sb.append('\t').append(s.getFile());
            }
            sb.append('\n');
        }
        if (sb.length() == 0) {
            sb.append("(no references found for ").append(target).append(")\n");
        }
        CliOutput.writeText(fileOut, sb.toString(), "ref-find.txt");
    }

    /**
     * プロジェクトをパースして {@link ReferenceIndex} を構築する。
     * Stage B 化されたクラスをすべて {@link ReferenceIndexBuilder} に流す。
     */
    static ReferenceIndex buildReferenceIndex(CliContext ctx)
            throws IOException {
        return buildReferenceIndex(parseProject(ctx), ctx.listener);
    }

    /** パース済み結果から {@link ReferenceIndex} を構築する (結果を他用途と共有する場合)。 */
    static ReferenceIndex buildReferenceIndex(UmlGenerator.ProjectParseResult result,
                                              ErrorListener listener) {
        ReferenceIndex idx = new ReferenceIndex();
        ReferenceIndexBuilder builder = new ReferenceIndexBuilder(idx,
                result.getIndex(), result.getDependencyIndex(), listener);
        builder.addAll(result.getClasses());
        return idx;
    }

    private static UmlGenerator.ProjectParseResult parseProject(CliContext ctx)
            throws IOException {
        return UmlGenerator.extractFromProjectDetailed(ctx.fileIn, ctx.scanOptions(),
                ctx.listener, null, null, false, UmlGenerator.ParseMode.FULL);
    }

    /**
     * {@code --insights}: コードリーディングの足がかりとなるアーキテクチャ俯瞰
     * (エントリポイント / ホットスポット / パッケージ循環 / デッドコード候補 /
     * 推定レイヤ) を Markdown レポート + 循環図 PlantUML で出力する。
     *
     * <p>出力先の規約は {@code --impact} と同じ
     * ({@link CliOutput#writeImpactOutput(File, String, String)})。</p>
     */
    public static void handleInsights(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--insights requires a project directory as input.");
            System.exit(1);
            return;
        }
        UmlGenerator.ProjectParseResult result = parseProject(ctx);
        ReferenceIndex refIndex = buildReferenceIndex(result, listener);
        InsightsModel model = InsightsAnalyzer.analyze(
                result.getClasses(), result.getIndex(), refIndex);
        String markdown = MarkdownInsightsReport.render(model);
        String puml = PlantUmlPackageCycleDiagram.render(model);
        CliOutput.writeImpactOutput(fileOut, markdown, puml, "insights");
    }

    /**
     * {@code --er-diagram}: プロジェクト内 Room {@code @Entity} の ER 図を出力する。
     */
    public static void handleErDiagram(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--er-diagram requires a project directory as input.");
            System.exit(1);
            return;
        }
        UmlGenerator.ProjectParseResult result =
                UmlGenerator.extractFromProjectDetailed(fileIn, ctx.scanOptions(), listener,
                        null, null, false, UmlGenerator.ParseMode.FULL);
        RoomAnalyzer.Result room = new RoomAnalyzer().analyze(result.getClasses());
        String puml = PlantUmlErDiagram.render(room);
        CliOutput.writeUmlOutput(fileOut, puml, "er-diagram");
    }

    /**
     * {@code --data-flow}: Room の Entity / DAO / Database を集計した
     * Markdown レポートと ER PlantUML を一括出力する。
     */
    public static void handleDataFlow(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--data-flow requires a project directory as input.");
            System.exit(1);
            return;
        }
        UmlGenerator.ProjectParseResult result =
                UmlGenerator.extractFromProjectDetailed(fileIn, ctx.scanOptions(), listener,
                        null, null, false, UmlGenerator.ParseMode.FULL);
        RoomAnalyzer.Result room = new RoomAnalyzer().analyze(result.getClasses());
        String md = MarkdownDataFlowReport.render(room);
        String puml = PlantUmlErDiagram.render(room);
        CliOutput.writeImpactOutput(fileOut, md, puml, "data-flow");
    }

    /**
     * {@code --screen-flow}: プロジェクト内 Java ソースから Intent ベースの画面遷移
     * (startActivity / setClass / setClassName) を検出し、Markdown + PlantUML
     * 状態遷移図で出力する。NavGraph XML は別系統 (DiagramService NAVIGATION) で処理。
     */
    public static void handleScreenFlow(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--screen-flow requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<ScreenTransition> transitions =
                new IntentNavigationDetector().analyzeProject(fileIn, ctx.includeTests);
        String md = MarkdownScreenFlowReport.render(transitions);
        String puml = PlantUmlScreenFlowDiagram.render(transitions);
        CliOutput.writeImpactOutput(fileOut, md, puml, "screen-flow");
    }

    /**
     * {@code --func-diff "FileA.java::ClassName.methodA,FileB.java::ClassName.methodB"}:
     * 2つのメソッドの呼び出し列を比較し、LCS/Levenshtein/Jaccard の3指標と
     * 行ごとの信頼度スコアを Markdown レポートとして出力する。
     */
    public static void handleFuncDiff(CliContext ctx, String spec) throws IOException {
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (spec == null || !spec.contains(",")) {
            System.err.println(
                    "--func-diff requires \"SpecA,SpecB\" format where each spec is"
                    + " \"filePath::ClassName.methodName\".");
            System.exit(1);
            return;
        }
        int comma = spec.indexOf(',');
        String rawA = spec.substring(0, comma).trim();
        String rawB = spec.substring(comma + 1).trim();

        juml.core.funcdiff.MethodDiffAnalyzer.MethodSpec specA;
        juml.core.funcdiff.MethodDiffAnalyzer.MethodSpec specB;
        try {
            specA = juml.core.funcdiff.MethodDiffAnalyzer.parseSpec(rawA);
            specB = juml.core.funcdiff.MethodDiffAnalyzer.parseSpec(rawB);
        } catch (IllegalArgumentException e) {
            System.err.println("--func-diff: " + e.getMessage());
            System.exit(1);
            return;
        }

        java.util.List<juml.core.formats.uml.JavaClassInfo> classesA =
                UmlGenerator.extractFromSource(
                        AndroidProjectScanner.readFile(new java.io.File(specA.filePath)),
                        new java.io.File(specA.filePath).getName(), listener);
        java.util.List<juml.core.formats.uml.JavaClassInfo> classesB =
                UmlGenerator.extractFromSource(
                        AndroidProjectScanner.readFile(new java.io.File(specB.filePath)),
                        new java.io.File(specB.filePath).getName(), listener);

        juml.core.formats.uml.JavaMethodInfo methodA =
                juml.core.funcdiff.MethodDiffAnalyzer.findMethod(classesA, specA);
        juml.core.formats.uml.JavaMethodInfo methodB =
                juml.core.funcdiff.MethodDiffAnalyzer.findMethod(classesB, specB);

        if (methodA == null) {
            System.err.println("--func-diff: method not found: " + rawA);
        }
        if (methodB == null) {
            System.err.println("--func-diff: method not found: " + rawB);
        }

        juml.core.funcdiff.MethodDiffAnalyzer.DiffResult result =
                juml.core.funcdiff.MethodDiffAnalyzer.analyze(methodA, specA, methodB, specB);
        CliOutput.writeText(fileOut,
                juml.core.funcdiff.MarkdownMethodDiffReport.render(result),
                "func-diff.md");
    }
}
