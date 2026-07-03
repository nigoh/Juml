// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.util.Option;
import juml.util.OptionParser;
import juml.util.UnknownOptionException;

import java.util.List;

/**
 * Main の CLI オプション定義・パース・ヘルプ表示を一手に引き受けるクラス。
 *
 * <p>各 {@link Option} を public final フィールドとして保持し、{@link CliDispatcher} /
 * {@link CliContext} がモード判定や値取得に直接参照する。出力モードが増えても
 * このクラスとディスパッチャの追記だけで済むよう、Main からオプション定義を切り離す。</p>
 */
public final class CliOptions {

    public final Option help = new Option("h", "help", false);
    public final Option out = new Option("o", "output", true);
    public final Option classDiagram = new Option("c", "class-diagram", false);
    public final Option sequenceDiagram = new Option("q", "sequence-diagram", true);
    public final Option verbose = new Option("v", "verbose", false);
    public final Option legend = new Option("L", "legend", false);
    public final Option noLegend = new Option(null, "no-legend", false);
    public final Option gradle = new Option("g", "gradle", false);
    public final Option manifest = new Option("m", "manifest", false);
    public final Option component = new Option("d", "component-diagram", false);
    public final Option manifestDiagram = new Option("M", "manifest-diagram", false);
    public final Option deepLinkDiagram = new Option("D", "deeplink-diagram", false);
    public final Option navGraph = new Option(null, "nav-graph", false);
    public final Option depGraph = new Option("G", "dependency-graph", false);
    public final Option summary = new Option(null, "summary", false);
    public final Option all = new Option("A", "all", false);
    public final Option noManifestMerge = new Option(null, "no-manifest-merge", false);
    public final Option noComments = new Option(null, "no-comments", false);
    public final Option commentStyle = new Option(null, "comment-style", true);
    public final Option noAnnotations = new Option(null, "no-annotations", false);
    public final Option noEnumConstants = new Option(null, "no-enum-constants", false);
    public final Option noFinal = new Option(null, "no-final", false);
    public final Option listMethods = new Option(null, "list-methods", false);
    public final Option functionList = new Option(null, "function-list", false);
    public final Option functionListFormat = new Option(null, "function-list-format", true);
    public final Option seqDepth = new Option(null, "seq-depth", true);
    public final Option sequenceDiagrams = new Option("Q", "sequence-diagrams", false);
    public final Option jetpack = new Option(null, "jetpack", false);
    public final Option perFolder = new Option("P", "per-folder", false);
    public final Option preset = new Option(null, "preset", true);
    public final Option noFields = new Option(null, "no-fields", false);
    public final Option noMethods = new Option(null, "no-methods", false);
    public final Option publicOnly = new Option(null, "public-only", false);
    public final Option excludeExternal = new Option(null, "exclude-external", false);
    public final Option markExternalSupertypes =
            new Option(null, "mark-external-supertypes", false);
    public final Option noVisibilityIcons = new Option(null, "no-visibility-icons", false);
    public final Option excludePackage = new Option(null, "exclude-package", true);
    public final Option colorRelations = new Option(null, "color-relations", false);
    public final Option focus = new Option(null, "focus", true);
    public final Option relation = new Option(null, "relation", true);
    public final Option mode = new Option(null, "mode", true);
    public final Option interactiveSvg = new Option(null, "interactive-svg", false);
    public final Option hiddenAnnotations = new Option(null, "hidden-annotations", true);
    public final Option commentMaxLength = new Option(null, "comment-max-length", true);
    public final Option impact = new Option(null, "impact", true);
    public final Option impactDepth = new Option(null, "impact-depth", true);
    public final Option refFind = new Option(null, "ref-find", true);
    public final Option vhalFlow = new Option(null, "vhal-flow", false);
    public final Option aidlBinding = new Option(null, "aidl-binding", false);
    public final Option erDiagram = new Option(null, "er-diagram", false);
    public final Option dataFlow = new Option(null, "data-flow", false);
    public final Option screenFlow = new Option(null, "screen-flow", false);
    public final Option androidBp = new Option(null, "android-bp", false);
    public final Option androidMk = new Option(null, "android-mk", false);
    public final Option vintf = new Option(null, "vintf", false);
    public final Option partitions = new Option(null, "partitions", false);
    public final Option buildNinja = new Option(null, "build-ninja", false);
    public final Option intermediates = new Option(null, "intermediates", false);
    public final Option selinux = new Option(null, "selinux", false);
    public final Option rro = new Option(null, "rro-overlays", false);
    public final Option settings = new Option(null, "settings", false);
    public final Option initFlow = new Option(null, "init-flow", false);
    public final Option actionMap = new Option(null, "action-map", false);
    public final Option funcDiff = new Option(null, "func-diff", true);
    public final Option insights = new Option(null, "insights", false);
    public final Option includeTests = new Option(null, "include-tests", false);
    public final Option apkSummary = new Option(null, "apk-summary", false);
    public final Option apkClassDiagram = new Option(null, "apk-class-diagram", false);
    public final Option apk = new Option(null, "apk", false);
    public final Option apkPackage = new Option(null, "apk-package", true);
    public final Option apkDecode = new Option(null, "apk-decode", false);
    public final Option apkSmali = new Option(null, "apk-smali", false);
    public final Option apkSequence = new Option(null, "apk-sequence", true);

    private final OptionParser parser = new OptionParser(new Option[]{
            help, out,
            classDiagram, sequenceDiagram, verbose,
            legend, noLegend,
            gradle, manifest, component, manifestDiagram,
            deepLinkDiagram, navGraph, depGraph, summary,
            all, noManifestMerge,
            noComments, commentStyle, noAnnotations,
            noEnumConstants, noFinal,
            listMethods, functionList, functionListFormat, seqDepth,
            sequenceDiagrams, jetpack, perFolder,
            preset, noFields, noMethods, publicOnly,
            excludeExternal, markExternalSupertypes, noVisibilityIcons,
            excludePackage, colorRelations, focus, relation, mode,
            interactiveSvg, hiddenAnnotations, commentMaxLength,
            impact, impactDepth, refFind,
            vhalFlow, aidlBinding, erDiagram, dataFlow,
            screenFlow, androidBp, androidMk, vintf, partitions,
            buildNinja, intermediates, selinux, rro,
            settings, initFlow, actionMap,
            funcDiff, insights, includeTests,
            apkSummary, apkClassDiagram, apk, apkPackage, apkDecode, apkSmali,
            apkSequence});

    /** 引数をパースする。未知オプションは stderr に出して {@code System.exit(1)}。 */
    public void parse(String[] args) {
        try {
            parser.parse(args);
        } catch (UnknownOptionException ex) {
            // エラー ID を先頭に出し、クローズド環境でも ID から対処法 (docs/errors.md /
            // GUI のエラーコード一覧) を辿れるようにする。
            System.err.println(ex.getErrorCode().tag()
                    + " Unknown option: " + ex.getOption());
            System.exit(1);
        }
    }

    /** {@code -h} / {@code --help} が指定されたか。 */
    public boolean helpRequested() {
        return help.isSet();
    }

    /** 位置引数 (入力パス等)。 */
    public List<String> arguments() {
        return parser.getArguments();
    }

    public void printUsage() {
        System.err.println(
                "Arguments: [-o file] [-c|-q M|-d|-M|-G|-g|-m|-Q|-A|--summary] [-v] [-h] [input]");
        System.err.println("  No arguments / [input dir]: Launch UML GUI for the project.");
        System.err.println("  -o path: Save diagram (puml/svg) or report (md) to path."
                + " If path is an existing directory, a command-specific default"
                + " file name is used inside it.");
        System.err.println("  --include-tests: Include test sources (src/test,"
                + " src/androidTest, tests/, */hostsidetests/, *Test.java etc.) in"
                + " analysis. Excluded by default to keep reports production-only.");
        System.err.println("  -h: Show this help.");
        System.err.println("  -c --class-diagram: Output PlantUML class diagram.");
        System.err.println("  -q --sequence-diagram Class.method: PlantUML sequence diagram.");
        System.err.println("  -v --verbose: Emit per-file warnings and summary to stderr.");
        System.err.println("  -L --legend: Force include legend (default on for UML).");
        System.err.println("  --no-legend: Force exclude legend.");
        System.err.println("  -g --gradle: Output Markdown summary from build.gradle.");
        System.err.println("  -m --manifest: Output Markdown summary from AndroidManifest.xml.");
        System.err.println("  -d --component-diagram: PlantUML Android component diagram.");
        System.err.println("  -M --manifest-diagram: "
                + "PlantUML AndroidManifest diagram (Application + components).");
        System.err.println("  -D --deeplink-diagram: "
                + "PlantUML Deep Link / App Links diagram (VIEW + BROWSABLE filters).");
        System.err.println("  --nav-graph: PlantUML Jetpack Navigation graph diagram "
                + "(res/navigation/*.xml: destinations + actions/deeplinks). dir or single .xml.");
        System.err.println("  -G --dependency-graph: PlantUML Gradle dependency graph.");
        System.err.println("  --summary: Full project Markdown summary (dir).");
        System.err.println("  -A --all: Output ALL artifacts as SVG "
                + "(summary.md + svg files) to the directory specified by -o.");
        System.err.println("  --no-manifest-merge: Disable manifest auto-merge in class diagram.");
        System.err.println("  --no-comments: Disable JavaDoc/comment rendering"
                + " in class & sequence diagrams.");
        System.err.println("  --comment-style inline|note: "
                + "Choose comment placement for class & sequence diagrams (default: inline).");
        System.err.println("  --no-annotations: Disable @annotation rendering in class diagram.");
        System.err.println("  --no-enum-constants: Disable enum constant rendering.");
        System.err.println("  --no-final: Disable {final} marker on final fields.");
        System.err.println("  --list-methods: List Class.method candidates for use with -q.");
        System.err.println("  --function-list: List all methods of all classes with callers"
                + " (利用側) and execution conditions (実行条件: guarding branch / UI trigger),"
                + " including button-click listeners.");
        System.err.println("  --function-list-format table|csv: Output format for"
                + " --function-list (default: table).");
        System.err.println("  --seq-depth N: Sequence trace depth limit (default 5, 0=unlimited).");
        System.err.println("  -Q --sequence-diagrams: Output PlantUML sequence diagrams"
                + " (.puml + .svg) for Android lifecycle entry points to the directory specified by -o.");
        System.err.println("  --jetpack: Enable Jetpack stereotypes (Fragment / ViewModel /"
                + " Hilt etc.) on class diagram (-c / -A).");
        System.err.println("  -P --per-folder: With -c: write one class diagram (.puml + .svg)"
                + " per source folder into the -o directory (preserves subfolder layout).");
        System.err.println("  --preset minimal|balanced|detailed: "
                + "Apply a readability preset to the class diagram (default: balanced).");
        System.err.println("  --no-fields: Hide fields in the class diagram.");
        System.err.println("  --no-methods: Hide methods in the class diagram.");
        System.err.println("  --public-only: Show only public classes and members.");
        System.err.println("  --exclude-external: Exclude classes from java.*, android.*,"
                + " kotlin.* and other external libraries (Origin + prefix).");
        System.err.println("  --mark-external-supertypes: Mark superclasses/interfaces that"
                + " are JDK standard (<<standard>>) or external libraries (<<external>>)"
                + " as stereotyped nodes in the class diagram.");
        System.err.println("  --no-visibility-icons: Show visibility as +/-/#/~ text symbols"
                + " instead of color icons in the class diagram.");
        System.err.println("  --exclude-package PREFIX: Exclude classes whose package"
                + " matches PREFIX (may be specified multiple times).");
        System.err.println("  --relation inherit,impl,use: Limit relation lines to the"
                + " listed kinds (CSV).");
        System.err.println("  --color-relations: Color-code class-diagram relations"
                + " (inherit=green / implement=blue / use=gray dashed).");
        System.err.println("  --focus FQN: Focus the class diagram on a class; emphasize it"
                + " and its 1-hop neighbors, dim the rest (FQN or simple name).");
        System.err.println("  --mode headers-only|full: Use the lightweight headers-only"
                + " parse mode for projects (default: full).");
        System.err.println("  --interactive-svg: Embed clickable class hyperlinks in"
                + " the generated SVG (juml://class/...).");
        System.err.println("  --hidden-annotations CSV: Override the list of hidden"
                + " annotation names (comma-separated).");
        System.err.println("  --comment-max-length N: Maximum inline comment length"
                + " (0 disables comments).");
        System.err.println("  --impact SYMBOL: Show reverse-reference impact for a class"
                + " FQN or FQN.method (Markdown + PlantUML).");
        System.err.println("  --impact-depth N: BFS depth for --impact (default 3).");
        System.err.println("  --ref-find SYMBOL: Print every reference site of a class"
                + " FQN or FQN.method (plain text, grep-friendly).");
        System.err.println("  --vhal-flow: Detect CarPropertyManager get/set/subscribe"
                + " usage in the project and emit Markdown + PlantUML flow.");
        System.err.println("  --aidl-binding: List AIDL interfaces and the classes that"
                + " extend their Stub (Markdown table).");
        System.err.println("  --er-diagram: Generate a PlantUML ER diagram for Room"
                + " @Entity classes grouped by @Database.");
        System.err.println("  --data-flow: Markdown report + ER diagram covering Room"
                + " @Entity / @Dao / @Database in the project.");
        System.err.println("  --screen-flow: Screen transitions (Intent startActivity/setClass"
                + " + Car App Library ScreenManager.push) as Markdown (with multi-step routes)"
                + " + PlantUML state diagram.");
        printAospUsage();
        System.err.println("  --settings: Scan SharedPreferences get*/put* calls and"
                + " res/xml/ Preference XML to report keys, types, and access locations"
                + " (Markdown).");
        System.err.println("  --init-flow: Generate PlantUML sequence diagrams for"
                + " Application subclass onCreate / onConfigurationChanged / onLowMemory"
                + " (output dir required via -o).");
        System.err.println("  --action-map: Detect onClick handlers (setOnClickListener,"
                + " android:onClick, Compose onClick) and menu handlers, output as"
                + " Markdown table.");
        System.err.println("  --insights: Architecture overview for code reading:"
                + " entry points, hotspots (fan-in/out), package cycles, dead-code"
                + " candidates and estimated layers (Markdown + PlantUML).");
        System.err.println("  --apk-summary: Markdown summary of an APK (.apk file is"
                + " auto-decompiled with the bundled Apktool, or pass an apktool-decoded"
                + " directory): apktool.yml metadata + manifest + smali class stats.");
        System.err.println("  --apk-class-diagram: PlantUML class diagram reconstructed from"
                + " the smali classes of an APK (.apk file or apktool-decoded directory).");
        System.err.println("  --apk: Output ALL APK artifacts (apk-summary.md +"
                + " apk-class-diagram.svg + manifest-diagram.svg) to the -o directory."
                + " When input is an .apk, the decompiled tree (smali/ + manifest) is also"
                + " kept under <-o>/decoded/ for browsing.");
        System.err.println("  --apk-decode: Just decompile an .apk into the -o directory"
                + " (smali/ + AndroidManifest.xml + apktool.yml), no diagrams. Lets you read"
                + " the smali files directly.");
        System.err.println("  --apk-smali: Per-class smali structure as Markdown (extends/"
                + "implements, fields, method signatures) for an .apk or decoded directory.");
        System.err.println("  --apk-sequence Class.method: PlantUML sequence diagram built by"
                + " following invoke-* calls in the smali method body (depth-limited, recurses"
                + " into in-scope classes). Turns the APK's processing flow into UML.");
        System.err.println("  --apk-package PREFIX: Restrict --apk* analysis to smali classes"
                + " whose package starts with PREFIX (keeps large APK diagrams readable).");
        System.err.println("  input: Java/AIDL file, .jar/.aar/.class file (compiled bytecode),"
                + " a Gradle/Android project directory, an .apk file, or an apktool-decoded"
                + " directory (for --apk* modes).");
    }

    /** AOSP ビルドシステム系モード ({@code --android-bp} 〜 {@code --rro-overlays}) の説明。 */
    private void printAospUsage() {
        System.err.println("  --android-bp: Parse all Android.bp (Soong) files under"
                + " the project and emit module inventory + dependency graph"
                + " (Markdown + PlantUML).");
        System.err.println("  --android-mk: Parse all Android.mk (legacy Make) files under"
                + " the project and emit module inventory + dependency graph"
                + " (Markdown + PlantUML), same shape as --android-bp.");
        System.err.println("  --vintf: Scan VINTF manifests (manifest*.xml /"
                + " compatibility_matrix*.xml) under the project and emit the HAL"
                + " declaration report + diagram (Markdown + PlantUML).");
        System.err.println("  --partitions: Aggregate Android.bp modules by partition"
                + " (system/vendor/product/system_ext/odm) and emit per-partition"
                + " breakdown + cross-partition dependency diagram"
                + " (Markdown + PlantUML).");
        System.err.println("  --build-ninja: Analyze build.ninja (Soong) rule usage and"
                + " target dependency graph aggregated by module"
                + " (Markdown + PlantUML). Point at out/soong or a tree containing it.");
        System.err.println("  --intermediates: Inventory of out/soong/.intermediates/"
                + " artifacts grouped by module / variant / kind (Markdown + PlantUML).");
        System.err.println("  --selinux: Parse all *.te SELinux policy files under"
                + " the project (type declarations, allow/neverallow rules,"
                + " Markdown report).");
        System.err.println("  --rro-overlays: Detect Android Runtime Resource Overlays"
                + " by scanning AndroidManifest.xml for <overlay> elements.");
    }
}
