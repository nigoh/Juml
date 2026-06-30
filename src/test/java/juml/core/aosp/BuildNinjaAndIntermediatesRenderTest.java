// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * build.ninja / .intermediates の Markdown・PlantUML 出力テスト。
 */
public class BuildNinjaAndIntermediatesRenderTest {

    @Test
    public void buildNinjaDiagramContainsNodesAndStats() {
        BuildNinjaGraph g = new BuildNinjaGraph();
        g.ruleFor("cc.compile").incrementBuildCount();
        g.addBuildStatement(1);
        g.addGroupEdge("frameworks/base", "system/core");
        String puml = PlantUmlBuildNinjaDiagram.render(g);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("frameworks/base"));
        assertTrue(puml.contains("-->"));
        assertTrue(puml.trim().endsWith("@enduml"));
    }

    @Test
    public void buildNinjaReportListsRules() {
        BuildNinjaGraph g = new BuildNinjaGraph();
        g.ruleFor("javac").incrementBuildCount();
        g.addBuildStatement(1);
        String md = MarkdownBuildNinjaReport.render(g);
        assertTrue(md.contains("# build.ninja Build Graph Report"));
        assertTrue(md.contains("javac"));
    }

    @Test
    public void buildNinjaHandlesEmpty() {
        String puml = PlantUmlBuildNinjaDiagram.render(new BuildNinjaGraph());
        assertTrue(puml.contains("@startuml"));
        String md = MarkdownBuildNinjaReport.render(new BuildNinjaGraph());
        assertTrue(md.contains("no build.ninja statements"));
    }

    @Test
    public void intermediatesDiagramAndReport() {
        IntermediatesInventory inv = new IntermediatesInventory();
        IntermediateModule m = inv.moduleFor("frameworks/base", "framework");
        m.addVariant("android_common");
        m.addArtifact("jar", 100);
        inv.addFile(100);
        String puml = PlantUmlIntermediatesDiagram.render(inv);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("framework"));
        String md = MarkdownIntermediatesReport.render(inv);
        assertTrue(md.contains("# Soong .intermediates Inventory"));
        assertTrue(md.contains("framework"));
    }

    @Test
    public void humanBytesFormatsSizes() {
        assertTrue(MarkdownIntermediatesReport.humanBytes(512).contains("B"));
        assertTrue(MarkdownIntermediatesReport.humanBytes(2048).contains("KB"));
    }
}
