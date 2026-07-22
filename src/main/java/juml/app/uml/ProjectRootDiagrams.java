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

    /** AOSP 入力検出時の走査上限 (巨大な非 AOSP ツリーでの過走査を防ぐ)。 */
    private static final int AOSP_SCAN_VISIT_CAP = 200_000;

    /**
     * プロジェクトに AOSP 専用図種の入力 ({@code Android.bp} / {@code build.ninja} /
     * {@code .intermediates}) が実在するかを軽量に検出し、描画可能な AOSP 図種の集合を返す。
     *
     * <p>これらの図種は入力が無いと「No … found」の空図しか出せないため、入力があるときだけ
     * ツールバー/メニューで有効化する (graceful degradation)。1 回の境界付きツリー走査で
     * 3 種すべてが見つかるか訪問上限に達したら早期終了する。</p>
     */
    static java.util.EnumSet<DiagramKind> availableAospKinds(java.io.File projectRoot) {
        java.util.EnumSet<DiagramKind> found = java.util.EnumSet.noneOf(DiagramKind.class);
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return found;
        }
        final int[] visits = {0};
        try (java.util.stream.Stream<java.nio.file.Path> walk =
                     java.nio.file.Files.walk(projectRoot.toPath(), 12)) {
            java.util.Iterator<java.nio.file.Path> it = walk.iterator();
            while (it.hasNext()) {
                if (found.size() == 3 || ++visits[0] > AOSP_SCAN_VISIT_CAP) {
                    break;
                }
                java.nio.file.Path p = it.next();
                String name = p.getFileName() != null ? p.getFileName().toString() : "";
                if (java.nio.file.Files.isDirectory(p)) {
                    if (name.endsWith(".intermediates")) {
                        found.add(DiagramKind.INTERMEDIATES);
                    }
                } else if (name.equals("Android.bp")) {
                    found.add(DiagramKind.SOONG);
                } else if (name.equals("build.ninja")) {
                    found.add(DiagramKind.BUILD_NINJA);
                }
            }
        } catch (java.io.IOException | RuntimeException ex) {
            // 走査失敗時は「AOSP 入力なし」とみなす (空集合)。図が消えるだけで害はない。
            juml.util.AppLog.warn(juml.util.ErrorCode.PRJ_001, "ProjectRootDiagrams",
                    "AOSP capability scan failed: " + safeMessage(ex));
        }
        return found;
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
                    + safeMessage(ex) + "\nend note\n@enduml\n";
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
        java.util.List<juml.core.aosp.AndroidBpModule> modules;
        try {
            modules = new juml.core.aosp.AndroidBpParser().analyzeProject(projectRoot);
        } catch (RuntimeException ex) {
            // 壊れた Android.bp でパーサが失敗しても図生成全体を殺さない
            // (ProjectLoader の Soong 走査と同じ扱い)。
            return "@startuml\ntitle Soong (Android.bp) Module Dependencies\n"
                    + "note as N\nScan failed: " + safeMessage(ex) + "\nend note\n@enduml\n";
        }
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
                    + safeMessage(ex) + "\nend note\n@enduml\n";
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
                    + safeMessage(ex) + "\nend note\n@enduml\n";
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
                    + safeMessage(ex) + "\nend note\n@enduml\n";
        }
    }

    /**
     * 例外メッセージをノート 1 行に整形する。{@code getMessage()} は null になりうる
     * (メッセージ無し IOException 等) ため、null 安全に例外型名へフォールバックする。
     */
    private static String safeMessage(Exception ex) {
        String m = ex.getMessage();
        return (m == null || m.isEmpty())
                ? ex.getClass().getSimpleName() : m.replace("\n", " ");
    }
}
