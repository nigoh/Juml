// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.PlantUmlResourceLinkDiagram;
import juml.core.formats.android.ResourceLinkAnalysis;
import juml.core.formats.android.ResourceLinkAnalyzer;

/**
 * プロジェクトルート (ソース/ビルド成果物ツリー) を再走査して生成する図の PlantUML 生成器。
 *
 * <p>{@link DiagramService} の {@code generatePuml(request, cache)} 入口から、ルートが
 * 必要な図種 (SCREEN_FLOW / SOONG / BUILD_NINJA / INTERMEDIATES / RESOURCE_LINK) だけを
 * ここに委譲する。ルート非依存の図種は従来どおり {@link DiagramService} の switch で処理する。</p>
 */
final class ProjectRootDiagrams {

    private ProjectRootDiagrams() {
    }

    static String screenFlow(java.io.File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return "@startuml\ntitle Screen Flow\n"
                    + "note as N\nOpen a project directory to detect screen transitions.\nend note\n"
                    + "@enduml\n";
        }
        try {
            java.util.List<juml.core.screen.ScreenTransition> transitions =
                    new juml.core.screen.IntentNavigationDetector()
                            .analyzeProject(projectRoot);
            return juml.core.screen.PlantUmlScreenFlowDiagram.render(transitions);
        } catch (java.io.IOException ex) {
            return "@startuml\ntitle Screen Flow\nnote as N\nScan failed: "
                    + ex.getMessage().replace("\n", " ") + "\nend note\n@enduml\n";
        }
    }

    /**
     * プロジェクト下を再帰走査して {@code Android.bp} (Soong Blueprint) を解析し、
     * モジュール依存を {@link juml.core.aosp.PlantUmlSoongDependencyDiagram} で描画する。
     */
    static String soong(java.io.File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return "@startuml\ntitle Soong (Android.bp) Module Dependencies\n"
                    + "note as N\nOpen a project directory to detect Android.bp modules.\nend note\n"
                    + "@enduml\n";
        }
        java.util.List<juml.core.aosp.AndroidBpModule> modules =
                new juml.core.aosp.AndroidBpParser().analyzeProject(projectRoot);
        if (modules.isEmpty()) {
            return "@startuml\ntitle Soong (Android.bp) Module Dependencies\n"
                    + "note as N\nNo Android.bp modules were found under this project.\nend note\n"
                    + "@enduml\n";
        }
        return juml.core.aosp.PlantUmlSoongDependencyDiagram.render(modules);
    }

    /**
     * プロジェクト下 (または {@code out/soong}) の {@code build.ninja} を解析し、
     * ターゲット依存を {@link juml.core.aosp.PlantUmlBuildNinjaDiagram} で描画する。
     */
    static String buildNinja(java.io.File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return "@startuml\ntitle build.ninja Target Dependencies\n"
                    + "note as N\nOpen a project directory containing build.ninja.\nend note\n"
                    + "@enduml\n";
        }
        try {
            juml.core.aosp.BuildNinjaGraph graph =
                    new juml.core.aosp.BuildNinjaParser().analyzeProject(projectRoot);
            return juml.core.aosp.PlantUmlBuildNinjaDiagram.render(graph);
        } catch (java.io.IOException ex) {
            return "@startuml\ntitle build.ninja Target Dependencies\nnote as N\nScan failed: "
                    + ex.getMessage().replace("\n", " ") + "\nend note\n@enduml\n";
        }
    }

    /**
     * プロジェクト下の {@code .intermediates} ツリーを走査し、モジュール別の中間生成物在庫を
     * {@link juml.core.aosp.PlantUmlIntermediatesDiagram} で描画する。
     */
    static String intermediates(java.io.File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return "@startuml\ntitle Soong .intermediates Inventory\n"
                    + "note as N\nOpen a project directory containing .intermediates.\nend note\n"
                    + "@enduml\n";
        }
        try {
            juml.core.aosp.IntermediatesInventory inv =
                    new juml.core.aosp.IntermediatesAnalyzer().analyzeProject(projectRoot);
            return juml.core.aosp.PlantUmlIntermediatesDiagram.render(inv);
        } catch (java.io.IOException ex) {
            return "@startuml\ntitle Soong .intermediates Inventory\nnote as N\nScan failed: "
                    + ex.getMessage().replace("\n", " ") + "\nend note\n@enduml\n";
        }
    }

    /**
     * プロジェクト下の Java/Kotlin と res/layout・res/values を再走査し、
     * コード ↔ リソースの紐づけを {@link PlantUmlResourceLinkDiagram} で描画する。
     */
    static String resourceLink(java.io.File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return "@startuml\ntitle Resource Links\n"
                    + "note as N\nOpen a project directory to detect code↔resource links.\n"
                    + "end note\n@enduml\n";
        }
        try {
            ResourceLinkAnalysis model =
                    new ResourceLinkAnalyzer().analyzeProject(projectRoot);
            return PlantUmlResourceLinkDiagram.generate(model);
        } catch (java.io.IOException ex) {
            return "@startuml\ntitle Resource Links\nnote as N\nScan failed: "
                    + ex.getMessage().replace("\n", " ") + "\nend note\n@enduml\n";
        }
    }
}
