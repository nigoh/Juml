// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

/**
 * {@link ProjectRootDiagrams} の null / 非ディレクトリガードを直接検証する。
 *
 * <p>ルート非依存の図種が cache 経路 (DiagramServiceTest) では実ディレクトリしか
 * 使わないため、ここでガード分岐（「プロジェクトを開いてください」案内図を返す）を
 * 直接カバーする。ファイルシステム走査のみでヘッドレス完結。</p>
 */
public class ProjectRootDiagramsTest {

    private static void assertUmlEnvelope(String puml, String label) {
        assertTrue(label + " は @startuml を含むこと: " + puml, puml.contains("@startuml"));
        assertTrue(label + " は @enduml を含むこと: " + puml, puml.contains("@enduml"));
    }

    @Test
    public void nullRoot_returnsGuideDiagramForAllKinds() {
        assertUmlEnvelope(ProjectRootDiagrams.screenFlow(null), "screenFlow(null)");
        assertUmlEnvelope(ProjectRootDiagrams.soong(null), "soong(null)");
        assertUmlEnvelope(ProjectRootDiagrams.buildNinja(null), "buildNinja(null)");
        assertUmlEnvelope(ProjectRootDiagrams.intermediates(null), "intermediates(null)");
        assertUmlEnvelope(ProjectRootDiagrams.resourceLink(null), "resourceLink(null)");
    }

    @Test
    public void nonDirectoryRoot_returnsGuideDiagram() {
        File notDir = new File("/nonexistent/path/not-a-directory-xyz");
        assertUmlEnvelope(ProjectRootDiagrams.screenFlow(notDir), "screenFlow(non-dir)");
        assertUmlEnvelope(ProjectRootDiagrams.soong(notDir), "soong(non-dir)");
        assertUmlEnvelope(ProjectRootDiagrams.resourceLink(notDir), "resourceLink(non-dir)");
    }
}
