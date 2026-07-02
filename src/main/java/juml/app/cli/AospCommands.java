// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.aaos.AidlBinding;
import juml.core.aaos.AidlBindingResolver;
import juml.core.aaos.MarkdownAidlBindingReport;
import juml.core.aaos.MarkdownVhalReport;
import juml.core.aaos.PlantUmlVhalFlowDiagram;
import juml.core.aaos.VehiclePropertyCatalog;
import juml.core.aaos.VhalAccess;
import juml.core.aaos.VhalAnalyzer;
import juml.core.aosp.AndroidBpModule;
import juml.core.aosp.AndroidBpParser;
import juml.core.aosp.AndroidMkParser;
import juml.core.aosp.BuildNinjaGraph;
import juml.core.aosp.BuildNinjaParser;
import juml.core.aosp.IntermediatesAnalyzer;
import juml.core.aosp.IntermediatesInventory;
import juml.core.aosp.MarkdownBuildNinjaReport;
import juml.core.aosp.MarkdownIntermediatesReport;
import juml.core.aosp.MarkdownPartitionReport;
import juml.core.aosp.MarkdownRroReport;
import juml.core.aosp.MarkdownSelinuxReport;
import juml.core.aosp.MarkdownSoongReport;
import juml.core.aosp.MarkdownVintfReport;
import juml.core.aosp.PlantUmlBuildNinjaDiagram;
import juml.core.aosp.PlantUmlIntermediatesDiagram;
import juml.core.aosp.PlantUmlPartitionDiagram;
import juml.core.aosp.PlantUmlSoongDependencyDiagram;
import juml.core.aosp.PlantUmlVintfDiagram;
import juml.core.aosp.RroOverlay;
import juml.core.aosp.RroOverlayDetector;
import juml.core.aosp.SelinuxPolicyParser;
import juml.core.aosp.SelinuxRule;
import juml.core.aosp.VintfProjectScanner;
import juml.core.formats.uml.UmlGenerator;

import java.io.File;
import java.io.IOException;

/**
 * AOSP / AAOS 系の CLI モード
 * ({@code --vhal-flow} / {@code --aidl-binding} / {@code --android-bp} /
 * {@code --android-mk} / {@code --vintf} / {@code --partitions} /
 * {@code --selinux} / {@code --rro-overlays}) のハンドラ群。
 */
public final class AospCommands {

    private AospCommands() {
    }

    /**
     * {@code --vhal-flow}: プロジェクトを走査して
     * {@link juml.core.aaos.CarPropertyManager} 系呼び出しを検出し、
     * Property 別 GET/SET/SUBSCRIBE フローを Markdown + PlantUML で出力する。
     */
    public static void handleVhalFlow(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--vhal-flow requires a project directory as input.");
            System.exit(1);
            return;
        }
        VhalAnalyzer analyzer = new VhalAnalyzer();
        java.util.List<VhalAccess> accesses =
                analyzer.analyzeProject(fileIn, ctx.includeTests);
        VehiclePropertyCatalog catalog = VehiclePropertyCatalog.scanProject(fileIn);
        String md = MarkdownVhalReport.render(accesses, catalog);
        String puml = PlantUmlVhalFlowDiagram.render(accesses);
        CliOutput.writeImpactOutput(fileOut, md, puml, "vhal-flow");
    }

    /**
     * {@code --aidl-binding}: プロジェクト内の AIDL インタフェースと、その
     * {@code Stub} を継承する実装クラスとの対応表を Markdown で出力する。
     */
    public static void handleAidlBinding(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--aidl-binding requires a project directory as input.");
            System.exit(1);
            return;
        }
        UmlGenerator.ProjectParseResult result =
                UmlGenerator.extractFromProjectDetailed(fileIn, ctx.scanOptions(),
                        ctx.listener, null, null, false, UmlGenerator.ParseMode.FULL);
        java.util.Map<String, java.util.List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(result.getClasses());
        String md = MarkdownAidlBindingReport.render(bindings);
        CliOutput.writeText(fileOut, md, "aidl-binding.md");
    }

    /**
     * {@code --android-bp}: プロジェクト下を再帰的に走査して {@code Android.bp}
     * (Soong Blueprint) を解析し、モジュール依存図 (PlantUML) と Markdown レポートを出力する。
     */
    public static void handleAndroidBp(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--android-bp requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<AndroidBpModule> modules =
                new AndroidBpParser().analyzeProject(fileIn);
        String md = MarkdownSoongReport.render(modules);
        String puml = PlantUmlSoongDependencyDiagram.render(modules);
        CliOutput.writeImpactOutput(fileOut, md, puml, "android-bp");
    }

    /**
     * {@code --android-mk}: プロジェクト下を再帰的に走査して {@code Android.mk}
     * (legacy GNU Make 形式) を解析し、{@code --android-bp} と同等のモジュール一覧 +
     * 依存グラフ (Markdown + PlantUML) を出力する。
     */
    public static void handleAndroidMk(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--android-mk requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<AndroidBpModule> modules =
                new AndroidMkParser().analyzeProject(fileIn);
        // LOCAL_MODULE が取れなかった宣言は表・図のノイズになるため除外する
        modules.removeIf(m -> m.getName().isEmpty());
        String md = MarkdownSoongReport.render(modules,
                "Android.mk (Make) Module Report", "(no Android.mk modules found)");
        String puml = PlantUmlSoongDependencyDiagram.render(modules,
                "Android.mk Module Dependencies");
        CliOutput.writeImpactOutput(fileOut, md, puml, "android-mk");
    }

    /**
     * {@code --vintf}: プロジェクト下の VINTF manifest ({@code manifest*.xml} /
     * {@code compatibility_matrix*.xml}) を走査し、HAL 宣言の Markdown レポートと
     * PlantUML 図 (manifest 種別ごとの HAL 一覧と interface/instance) を出力する。
     */
    public static void handleVintf(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--vintf requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<VintfProjectScanner.Entry> entries =
                new VintfProjectScanner().analyzeProject(fileIn);
        String md = MarkdownVintfReport.render(entries);
        String puml = PlantUmlVintfDiagram.render(entries);
        CliOutput.writeImpactOutput(fileOut, md, puml, "vintf");
    }

    /**
     * {@code --partitions}: プロジェクト下の {@code Android.bp} を解析し、partition
     * (system / vendor / product / system_ext / odm 等) 別のモジュール集計と
     * partition を跨ぐ依存の可視化 (Markdown + PlantUML) を出力する。
     */
    public static void handlePartitions(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--partitions requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<AndroidBpModule> modules =
                new AndroidBpParser().analyzeProject(fileIn);
        String md = MarkdownPartitionReport.render(modules);
        String puml = PlantUmlPartitionDiagram.render(modules);
        CliOutput.writeImpactOutput(fileOut, md, puml, "partitions");
    }

    /**
     * {@code --build-ninja}: プロジェクト下 (または {@code out/soong}) の {@code build.ninja}
     * を解析し、rule 使用統計とターゲット依存グラフを Markdown + PlantUML で出力する。
     */
    public static void handleBuildNinja(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--build-ninja requires a project directory as input.");
            System.exit(1);
            return;
        }
        BuildNinjaGraph graph = new BuildNinjaParser().analyzeProject(fileIn);
        String md = MarkdownBuildNinjaReport.render(graph);
        String puml = PlantUmlBuildNinjaDiagram.render(graph);
        CliOutput.writeImpactOutput(fileOut, md, puml, "build-ninja");
    }

    /**
     * {@code --intermediates}: プロジェクト下の {@code .intermediates} ツリーを走査し、
     * モジュール×バリアント×成果物種別の在庫を Markdown + PlantUML で出力する。
     */
    public static void handleIntermediates(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--intermediates requires a project directory as input.");
            System.exit(1);
            return;
        }
        IntermediatesInventory inv = new IntermediatesAnalyzer().analyzeProject(fileIn);
        String md = MarkdownIntermediatesReport.render(inv);
        String puml = PlantUmlIntermediatesDiagram.render(inv);
        CliOutput.writeImpactOutput(fileOut, md, puml, "intermediates");
    }

    /**
     * {@code --selinux}: プロジェクト下の {@code *.te} を再帰走査し、
     * type 宣言と allow/neverallow ルールを Markdown レポートにまとめる。
     */
    public static void handleSelinux(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--selinux requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<SelinuxRule> rules =
                new SelinuxPolicyParser().analyzeProject(fileIn);
        String md = MarkdownSelinuxReport.render(rules);
        CliOutput.writeText(fileOut, md, "selinux.md");
    }

    /**
     * {@code --rro-overlays}: プロジェクト下の {@code AndroidManifest.xml} から
     * {@code &lt;overlay targetPackage="..."&gt;} を検出し、RRO 一覧を Markdown 出力する。
     */
    public static void handleRroOverlays(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--rro-overlays requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<RroOverlay> overlays =
                new RroOverlayDetector().analyzeProject(fileIn);
        String md = MarkdownRroReport.render(overlays);
        CliOutput.writeText(fileOut, md, "rro-overlays.md");
    }
}
