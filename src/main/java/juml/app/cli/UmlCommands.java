// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.android.actions.UiActionEntry;
import juml.core.formats.android.actions.UiActionScanner;
import juml.core.formats.java.AndroidProjectScanner;
import juml.core.formats.uml.LifecycleSequenceDiagrams;
import juml.core.formats.uml.PlantUmlRenderer;
import juml.core.formats.uml.UmlGenerator;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceIndexBuilder;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;

/**
 * Java/AIDL ソースを起点とする UML 系 CLI モード
 * ({@code -c} / {@code -q} / {@code --list-methods} / {@code -Q} / {@code -P} / {@code --init-flow})
 * のハンドラ群。
 */
public final class UmlCommands {

    private UmlCommands() {
    }

    /** 入力ファイルが {@code .jar}/{@code .aar}/{@code .class} のいずれかか。 */
    static boolean isArchiveFile(File f) {
        if (f == null || !f.isFile()) {
            return false;
        }
        String n = f.getName().toLowerCase();
        return n.endsWith(".jar") || n.endsWith(".aar") || n.endsWith(".class");
    }

    /**
     * Java/AIDL ソースから PlantUML (クラス図 / シーケンス図) を生成。
     * @param classDiagram true でクラス図モード
     * @param sequenceEntry "Class.method" 形式のエントリ。null/空ならシーケンス図モードを無効化
     */
    public static void handleUmlInput(CliContext ctx, boolean classDiagram,
                                      String sequenceEntry) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        boolean mergeManifest = ctx.mergeManifest;
        UmlOverrides overrides = ctx.overrides;
        if (fileIn == null) {
            System.err.println("UML generation requires an input file or directory.");
            System.exit(1);
            return;
        }
        String spec = sequenceEntry == null ? "" : sequenceEntry.trim();
        java.util.List<juml.core.formats.uml.JavaClassInfo> infos;
        // 機能A: 継承先の単純名を FQN へ解決する任意の resolver (プロジェクト解析時のみ構築)。
        juml.core.formats.uml.TypeRefResolver supertypeResolver = null;
        juml.core.formats.uml.DependencyJarIndex depIndex = null;
        UmlGenerator.ParseMode parseMode = overrides != null
                ? overrides.parseMode : UmlGenerator.ParseMode.FULL;
        if (fileIn.isDirectory()) {
            UmlGenerator.ProjectParseResult result = UmlGenerator.extractFromProjectDetailed(
                    fileIn, ctx.scanOptions(), listener, null, null,
                    mergeManifest, parseMode);
            infos = new java.util.ArrayList<>(result.getClasses());
            juml.core.refs.NameResolver nr = new juml.core.refs.NameResolver(
                    result.getIndex(), result.getDependencyIndex());
            supertypeResolver = nr::resolve;
            depIndex = result.getDependencyIndex();
        } else if (isArchiveFile(fileIn)) {
            // .jar / .aar / .class はコンパイル済みバイトコードとして直接解析する。
            infos = UmlGenerator.extractFromArchive(fileIn, listener);
        } else {
            String src = AndroidProjectScanner.readFile(fileIn);
            if (parseMode == UmlGenerator.ParseMode.HEADERS_ONLY) {
                infos = UmlGenerator.extractHeadersFromSource(src, fileIn.getName(), listener);
            } else {
                infos = UmlGenerator.extractFromSource(src, fileIn.getName(), listener);
            }
        }
        // クラス図モードで --exclude-package が指定されていれば、ここで除外を効かせる。
        if (classDiagram && overrides != null
                && overrides.excludedPackages != null
                && !overrides.excludedPackages.isEmpty()) {
            java.util.List<juml.core.formats.uml.JavaClassInfo> filtered =
                    new java.util.ArrayList<>(infos.size());
            for (juml.core.formats.uml.JavaClassInfo c : infos) {
                String pkg = c.getPackageName() == null ? "" : c.getPackageName();
                boolean drop = false;
                for (String ex : overrides.excludedPackages) {
                    if (pkg.equals(ex) || pkg.startsWith(ex + ".")) {
                        drop = true;
                        break;
                    }
                }
                if (!drop) {
                    filtered.add(c);
                }
            }
            infos = filtered;
        }

        String output;
        if (sequenceEntry != null && !spec.isEmpty()) {
            int dot = spec.lastIndexOf('.');
            if (dot < 0) {
                System.err.println("Sequence entry must be in 'Class.method' format: " + spec);
                System.exit(1);
                return;
            }
            String entryClass = spec.substring(0, dot);
            String entryMethod = spec.substring(dot + 1);
            juml.core.formats.uml.PlantUmlSequenceDiagram.Options sqOpts
                    = new juml.core.formats.uml.PlantUmlSequenceDiagram.Options();
            if (Boolean.FALSE.equals(legendOverride)) {
                sqOpts.includeLegend = false;
            }
            if (overrides != null) {
                overrides.applyTo(sqOpts);
                if (overrides.seqDepth != null) {
                    sqOpts.maxDepth = overrides.seqDepth;
                }
            }
            output = juml.core.formats.uml.PlantUmlSequenceDiagram.generate(
                    infos, entryClass, entryMethod, sqOpts);
        } else {
            juml.core.formats.uml.PlantUmlClassDiagram.Options clOpts
                    = new juml.core.formats.uml.PlantUmlClassDiagram.Options();
            if (Boolean.FALSE.equals(legendOverride)) {
                clOpts.includeLegend = false;
            }
            if (overrides != null) {
                overrides.applyTo(clOpts);
            }
            if (supertypeResolver != null) {
                clOpts.supertypeResolver = supertypeResolver;
            }
            if (depIndex != null) {
                final juml.core.formats.uml.DependencyJarIndex di = depIndex;
                clOpts.dependencyClassPredicate = fqn -> di.resolve(fqn).isPresent();
            }
            output = juml.core.formats.uml.PlantUmlClassDiagram.generate(infos, clOpts);
        }
        CliOutput.writeUmlOutput(fileOut, output,
                spec.isEmpty() ? "class-diagram" : "sequence-diagram");
    }

    /**
     * {@code --list-methods}: 入力ソース内のメソッドを列挙し、{@code Class.method} 形式で
     * 1 行ずつ stdout (もしくは {@code -o} 指定先) に書き出す。シーケンス図の起点を
     * シェルから選ぶ用途 (fzf, peco 等と組み合わせ) を想定。
     */
    public static void handleListMethods(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null) {
            System.err.println("--list-methods requires an input file or directory.");
            System.exit(1);
            return;
        }
        java.util.List<juml.core.formats.uml.JavaClassInfo> infos;
        if (fileIn.isDirectory()) {
            infos = UmlGenerator.extractFromProject(fileIn, ctx.scanOptions(), listener);
        } else {
            String src = AndroidProjectScanner.readFile(fileIn);
            infos = UmlGenerator.extractFromSource(src, fileIn.getName(), listener);
        }
        java.util.List<juml.core.formats.uml.PlantUmlSequenceDiagram.Candidate> candidates =
                juml.core.formats.uml.PlantUmlSequenceDiagram.listCandidates(infos);
        StringBuilder sb = new StringBuilder();
        for (juml.core.formats.uml.PlantUmlSequenceDiagram.Candidate c : candidates) {
            sb.append(c.getEntry())
                    .append("\t(").append(c.callCount).append(" call")
                    .append(c.callCount == 1 ? "" : "s")
                    .append(", ").append(c.visibility.name().toLowerCase()).append(")\n");
        }
        CliOutput.writeText(fileOut, sb.toString(), "method-list.txt");
    }

    /**
     * {@code --function-list}: 全クラスの関数（メソッド）一覧を、各関数の利用側（呼び出し元）と
     * 実行条件（呼び出しを囲む分岐条件 / リスナーの UI トリガ）付きで出力する。
     * ディレクトリ入力時はボタン押下リスナー (XML/Compose/メニュー) も併記する。
     */
    public static void handleFunctionList(CliContext ctx,
            juml.core.formats.uml.MethodUsageReport.Format format) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        if (fileIn == null) {
            System.err.println("--function-list requires an input file or directory.");
            System.exit(1);
            return;
        }
        java.util.List<juml.core.formats.uml.JavaClassInfo> infos;
        ReferenceIndex refIndex = null;
        java.util.List<UiActionEntry> actions = java.util.Collections.emptyList();
        if (fileIn.isDirectory()) {
            UmlGenerator.ProjectParseResult result = UmlGenerator.extractFromProjectDetailed(
                    fileIn, ctx.scanOptions(), listener, null, null, false,
                    UmlGenerator.ParseMode.FULL);
            infos = new java.util.ArrayList<>(result.getClasses());
            ReferenceIndex idx = new ReferenceIndex();
            new ReferenceIndexBuilder(idx, result.getIndex(), result.getDependencyIndex(), listener)
                    .addAll(result.getClasses());
            refIndex = idx;
            actions = new UiActionScanner().analyzeProject(fileIn, ctx.includeTests);
        } else {
            String src = AndroidProjectScanner.readFile(fileIn);
            infos = UmlGenerator.extractFromSource(src, fileIn.getName(), listener);
        }
        CliOutput.writeText(fileOut,
                juml.core.formats.uml.MethodUsageReport.render(
                        infos, refIndex, actions, format),
                "function-list.md");
    }

    /**
     * {@code --sequence-diagrams} / {@code -Q}: Android プロジェクトを入力に、
     * Activity/Service/Receiver/Provider のライフサイクルメソッドを起点とする
     * PlantUML シーケンス図を {@code -o} で指定されたディレクトリへ一括出力する。
     * 各起点について {@code Class.method.puml} と {@code Class.method.svg} の両方を書き出す。
     */
    public static void handleSequenceDiagrams(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        boolean mergeManifest = ctx.mergeManifest;
        UmlOverrides overrides = ctx.overrides;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--sequence-diagrams requires a project directory.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--sequence-diagrams requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory for --sequence-diagrams: " + fileOut);
            System.exit(1);
            return;
        }
        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();
        progress.step("Analyzing project: " + fileIn.getAbsolutePath());
        java.util.List<juml.core.formats.uml.JavaClassInfo> infos =
                UmlGenerator.extractFromProject(fileIn, ctx.scanOptions(),
                        listener, mergeManifest);
        progress.step("Generating sequence diagrams (.puml + .svg)");
        int count = generateLifecycleSequenceDiagrams(infos, fileOut, legendOverride,
                overrides, progress, listener);
        progress.wrote(fileOut, "(" + count + " diagram(s))");
        progress.done(fileOut, System.currentTimeMillis() - startMs);
    }

    /**
     * {@code -c --per-folder}: プロジェクトを再帰スキャンし、ソースファイルを直接含む
     * 各フォルダごとに 1 枚ずつ PlantUML クラス図 ({@code classes.puml} + {@code classes.svg})
     * を {@code -o} で指定されたディレクトリ配下にサブフォルダ構造を維持して出力する。
     */
    public static void handleClassDiagramsPerFolder(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        boolean mergeManifest = ctx.mergeManifest;
        UmlOverrides overrides = ctx.overrides;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--per-folder requires a project directory.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--per-folder requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory for --per-folder: " + fileOut);
            System.exit(1);
            return;
        }
        juml.core.formats.uml.PlantUmlClassDiagram.Options clsOpts =
                new juml.core.formats.uml.PlantUmlClassDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            clsOpts.includeLegend = false;
        }
        if (overrides != null) {
            overrides.applyTo(clsOpts);
        }

        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();
        progress.step("Analyzing project: " + fileIn.getAbsolutePath());
        progress.step("Generating per-folder class diagrams (.puml + .svg)");
        juml.core.formats.uml.PerFolderClassDiagrams.Result result =
                juml.core.formats.uml.PerFolderClassDiagrams.generate(
                        fileIn, fileOut, null, clsOpts, mergeManifest, null, listener);
        progress.wrote(fileOut,
                "(" + result.getFolderCount() + " folder(s), "
                        + result.getClassCount() + " class(es))");
        progress.done(fileOut, System.currentTimeMillis() - startMs);
    }

    /**
     * {@code --init-flow}: Application サブクラスの {@code onCreate()} を起点とした
     * 初期化シーケンス図を出力する。既存のライフサイクルシーケンス図生成を Application にも適用する。
     */
    public static void handleInitFlow(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        ErrorListener listener = ctx.listener;
        Boolean legendOverride = ctx.legendOverride;
        UmlOverrides overrides = ctx.overrides;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--init-flow requires a project directory as input.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--init-flow requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory for --init-flow: " + fileOut);
            System.exit(1);
            return;
        }
        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();
        progress.step("Analyzing project: " + fileIn.getAbsolutePath());
        java.util.List<juml.core.formats.uml.JavaClassInfo> infos =
                UmlGenerator.extractFromProject(fileIn, ctx.scanOptions(),
                        listener, false);
        progress.step("Generating Application init-flow sequence diagrams");
        int count = generateInitFlowSequenceDiagrams(infos, fileOut, legendOverride,
                overrides, progress, listener);
        if (count == 0) {
            System.err.println("[juml] No Application subclass found with onCreate().");
        }
        progress.done(fileOut, System.currentTimeMillis() - startMs);
    }

    /**
     * Activity/Service のライフサイクルメソッドを起点にシーケンス図を一括生成し、
     * 各起点ごとに {@code Class.method.puml} と {@code Class.method.svg} の両方を出力する。
     */
    static int generateLifecycleSequenceDiagrams(
            java.util.List<juml.core.formats.uml.JavaClassInfo> infos,
            File outDir,
            Boolean legendOverride,
            UmlOverrides overrides,
            ProgressLogger progress,
            ErrorListener listener) throws IOException {
        juml.core.formats.uml.PlantUmlSequenceDiagram.Options sqOpts
                = new juml.core.formats.uml.PlantUmlSequenceDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            sqOpts.includeLegend = false;
        }
        if (overrides != null) {
            overrides.applyTo(sqOpts);
            if (overrides.seqDepth != null) {
                sqOpts.maxDepth = overrides.seqDepth;
            }
        }
        java.util.List<LifecycleSequenceDiagrams.Entry> entries =
                LifecycleSequenceDiagrams.generateAll(infos, sqOpts);
        for (LifecycleSequenceDiagrams.Entry e : entries) {
            CliOutput.writeText(new File(outDir, e.baseName() + ".puml"), e.puml);
            File svgFile = new File(outDir, e.baseName() + ".svg");
            try {
                PlantUmlRenderer.renderSvg(e.puml, svgFile);
            } catch (juml.core.formats.uml.PlantUmlRenderFailedException ex) {
                System.err.println("[juml]     -> " + svgFile.getName()
                        + " FAILED: " + ex.getMessage()
                        + " (.puml is preserved)");
            }
        }
        return entries.size();
    }

    /** Application の基底クラス名セット (FQN + 単純名)。 */
    private static final java.util.Set<String> APPLICATION_BASES;
    static {
        java.util.Set<String> s = new java.util.HashSet<>(java.util.Arrays.asList(
                "Application", "android.app.Application",
                "MultiDexApplication", "androidx.multidex.MultiDexApplication"));
        APPLICATION_BASES = java.util.Collections.unmodifiableSet(s);
    }

    /**
     * {@code --init-flow} 専用: Application サブクラスのみを対象に
     * onCreate / onConfigurationChanged / onLowMemory のシーケンス図を生成する。
     *
     * <p>{@code androidComponentType} の設定有無によらず、スーパークラス名で直接判定する。</p>
     */
    static int generateInitFlowSequenceDiagrams(
            java.util.List<juml.core.formats.uml.JavaClassInfo> infos,
            File outDir,
            Boolean legendOverride,
            UmlOverrides overrides,
            ProgressLogger progress,
            ErrorListener listener) throws IOException {
        // Application サブクラスにマークを付ける (元のリストを変更しない)
        java.util.List<juml.core.formats.uml.JavaClassInfo> marked =
                new java.util.ArrayList<>();
        for (juml.core.formats.uml.JavaClassInfo c : infos) {
            String superClass = c.getSuperClass();
            if (superClass != null && APPLICATION_BASES.contains(superClass)) {
                // androidComponentType を一時的に設定したコピーを作成
                juml.core.formats.uml.JavaClassInfo copy =
                        copyWithComponentType(c, "Application");
                marked.add(copy);
            } else {
                marked.add(c);
            }
        }
        return generateLifecycleSequenceDiagrams(marked, outDir, legendOverride,
                overrides, progress, listener);
    }

    /** 指定の androidComponentType を持つシャローコピーを返す。 */
    static juml.core.formats.uml.JavaClassInfo copyWithComponentType(
            juml.core.formats.uml.JavaClassInfo src, String type) {
        juml.core.formats.uml.JavaClassInfo copy =
                new juml.core.formats.uml.JavaClassInfo();
        copy.setPackageName(src.getPackageName());
        copy.setSimpleName(src.getSimpleName());
        copy.setSuperClass(src.getSuperClass());
        copy.setAndroidComponentType(type);
        copy.getMethods().addAll(src.getMethods());
        copy.getFields().addAll(src.getFields());
        copy.getAnnotations().addAll(src.getAnnotations());
        copy.getInterfaces().addAll(src.getInterfaces());
        return copy;
    }
}
