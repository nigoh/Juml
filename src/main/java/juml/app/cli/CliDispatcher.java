// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import java.io.IOException;

/**
 * パース済みオプションに従って対応する CLI コマンドを 1 つ実行するディスパッチャ。
 *
 * <p>出力モードが増えた場合はここに分岐を 1 つ足すだけで済む。どのモードにも
 * 該当しなければ {@code false} を返し、呼び出し元 (Main) が GUI 起動へフォールバックする。</p>
 */
public final class CliDispatcher {

    private CliDispatcher() {
    }

    /**
     * @return CLI モードを実行したら true。いずれにも該当しなければ false (GUI フォールバック)。
     */
    public static boolean dispatch(CliOptions o, CliContext ctx) throws IOException {
        if (o.listMethods.isSet()) {
            UmlCommands.handleListMethods(ctx);
            return true;
        }
        if (o.functionList.isSet()) {
            String fmt = o.functionListFormat.getArguments().isEmpty()
                    ? null : o.functionListFormat.getArguments().getLast();
            UmlCommands.handleFunctionList(ctx,
                    juml.core.formats.uml.MethodUsageReport.Format.fromString(fmt));
            return true;
        }
        if (o.sequenceDiagrams.isSet()) {
            UmlCommands.handleSequenceDiagrams(ctx);
            return true;
        }
        if (o.all.isSet()) {
            AndroidCommands.handleAll(ctx);
            return true;
        }
        if (o.gradle.isSet()) {
            AndroidCommands.handleGradleInput(ctx);
            return true;
        }
        if (o.manifest.isSet()) {
            AndroidCommands.handleManifestInput(ctx);
            return true;
        }
        if (o.component.isSet()) {
            AndroidCommands.handleComponentDiagram(ctx);
            return true;
        }
        if (o.manifestDiagram.isSet()) {
            AndroidCommands.handleManifestDiagram(ctx);
            return true;
        }
        if (o.deepLinkDiagram.isSet()) {
            AndroidCommands.handleDeepLinkDiagram(ctx);
            return true;
        }
        if (o.depGraph.isSet()) {
            AndroidCommands.handleDependencyGraph(ctx);
            return true;
        }
        if (o.summary.isSet()) {
            AndroidCommands.handleSummary(ctx);
            return true;
        }
        if (o.impact.isSet()) {
            int depth = 3;
            if (!o.impactDepth.getArguments().isEmpty()) {
                try {
                    depth = Integer.parseInt(o.impactDepth.getArguments().getLast());
                } catch (NumberFormatException ex) {
                    System.err.println("--impact-depth must be an integer");
                    System.exit(1);
                }
            }
            AnalysisCommands.handleImpact(ctx, o.impact.getArguments().getLast(), depth);
            return true;
        }
        if (o.refFind.isSet()) {
            AnalysisCommands.handleRefFind(ctx, o.refFind.getArguments().getLast());
            return true;
        }
        if (o.vhalFlow.isSet()) {
            AospCommands.handleVhalFlow(ctx);
            return true;
        }
        if (o.aidlBinding.isSet()) {
            AospCommands.handleAidlBinding(ctx);
            return true;
        }
        if (o.erDiagram.isSet()) {
            AnalysisCommands.handleErDiagram(ctx);
            return true;
        }
        if (o.dataFlow.isSet()) {
            AnalysisCommands.handleDataFlow(ctx);
            return true;
        }
        if (o.screenFlow.isSet()) {
            AnalysisCommands.handleScreenFlow(ctx);
            return true;
        }
        if (o.androidBp.isSet()) {
            AospCommands.handleAndroidBp(ctx);
            return true;
        }
        if (o.selinux.isSet()) {
            AospCommands.handleSelinux(ctx);
            return true;
        }
        if (o.rro.isSet()) {
            AospCommands.handleRroOverlays(ctx);
            return true;
        }
        if (o.settings.isSet()) {
            AndroidCommands.handleSettings(ctx);
            return true;
        }
        if (o.initFlow.isSet()) {
            UmlCommands.handleInitFlow(ctx);
            return true;
        }
        if (o.actionMap.isSet()) {
            AndroidCommands.handleActionMap(ctx);
            return true;
        }
        if (o.funcDiff.isSet()) {
            AnalysisCommands.handleFuncDiff(ctx, o.funcDiff.getArguments().getLast());
            return true;
        }
        if (o.insights.isSet()) {
            AnalysisCommands.handleInsights(ctx);
            return true;
        }
        if (o.classDiagram.isSet() && o.perFolder.isSet()) {
            UmlCommands.handleClassDiagramsPerFolder(ctx);
            return true;
        }
        if (o.classDiagram.isSet() || o.sequenceDiagram.isSet()) {
            UmlCommands.handleUmlInput(ctx,
                    o.classDiagram.isSet(),
                    o.sequenceDiagram.isSet()
                            ? o.sequenceDiagram.getArguments().getLast() : null);
            return true;
        }
        return false;
    }
}
