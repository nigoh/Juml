// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlModuleDiagram のユニットテスト。
 */
public class PlantUmlModuleDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClassesThrows() {
        PlantUmlModuleDiagram.generate(null);
    }

    @Test
    public void testEmptyListProducesFallbackNote() {
        String puml = PlantUmlModuleDiagram.generate(Collections.emptyList());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue("should note that no module-info was found: " + puml,
                puml.contains("No module-info.java found"));
    }

    @Test
    public void testNonModuleClassesAreIgnored() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.example; public class Foo {}");
        String puml = PlantUmlModuleDiagram.generate(infos);
        assertTrue("should produce fallback note for non-module input: " + puml,
                puml.contains("No module-info.java found"));
        assertFalse("ordinary class should not appear in module diagram: " + puml,
                puml.contains("Foo"));
    }

    @Test
    public void testSingleModuleRendersAsComponent() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app { }");
        String puml = PlantUmlModuleDiagram.generate(infos);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue("module should render as component: " + puml,
                puml.contains("component"));
        assertTrue("module name should appear: " + puml,
                puml.contains("com.example.app"));
    }

    @Test
    public void testRequiresBecomesArrow() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app {\n"
                + "  requires com.example.core;\n"
                + "}");
        String puml = PlantUmlModuleDiagram.generate(infos);
        assertTrue("requires should produce an arrow: " + puml,
                puml.contains("-->"));
        assertTrue("label should say 'requires': " + puml,
                puml.contains(": requires"));
    }

    @Test
    public void testRequiresTransitiveUsesDoubleArrow() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app {\n"
                + "  requires transitive com.example.core;\n"
                + "}");
        String puml = PlantUmlModuleDiagram.generate(infos);
        assertTrue("requires transitive should produce ==>: " + puml,
                puml.contains("==>"));
        assertTrue("label should say 'requires transitive': " + puml,
                puml.contains(": requires transitive"));
    }

    @Test
    public void testTwoKnownModulesUseAliases() {
        String src1 = "module com.example.app { requires com.example.core; }";
        String src2 = "module com.example.core { }";
        List<JavaClassInfo> all = new java.util.ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(src1));
        all.addAll(JavaStructureExtractor.extract(src2));
        String puml = PlantUmlModuleDiagram.generate(all);
        // 両モジュールの alias (M0, M1) が矢印に使われることを確認
        assertTrue("arrow between known modules should use aliases: " + puml,
                puml.matches("(?s).*M\\d+ --> M\\d+.*"));
    }

    @Test
    public void testExternalRequiredModuleUsesQuotedName() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app { requires java.base; }");
        String puml = PlantUmlModuleDiagram.generate(infos);
        // java.base は known modules に含まれないため引用符付きで出力される
        assertTrue("external module should appear as quoted name: " + puml,
                puml.contains("\"java.base\""));
    }

    @Test
    public void testExportsAppearsInNote() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app {\n"
                + "  exports com.example.api;\n"
                + "}");
        PlantUmlModuleDiagram.Options o = new PlantUmlModuleDiagram.Options();
        o.showExportsOpens = true;
        String puml = PlantUmlModuleDiagram.generate(infos, o);
        assertTrue("exports should appear in a note: " + puml,
                puml.contains("exports com.example.api"));
        assertTrue("note block should be present: " + puml,
                puml.contains("note bottom of"));
    }

    @Test
    public void testExportsHiddenWhenDisabled() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app {\n"
                + "  exports com.example.api;\n"
                + "}");
        PlantUmlModuleDiagram.Options o = new PlantUmlModuleDiagram.Options();
        o.showExportsOpens = false;
        String puml = PlantUmlModuleDiagram.generate(infos, o);
        assertFalse("exports note should be suppressed: " + puml,
                puml.contains("note bottom of"));
    }

    @Test
    public void testLegendIncludedByDefault() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app { }");
        String puml = PlantUmlModuleDiagram.generate(infos);
        assertTrue("legend should be included by default: " + puml,
                puml.contains("legend top left"));
        assertTrue("legend should explain component: " + puml,
                puml.contains("module-info.java"));
    }

    @Test
    public void testLegendCanBeDisabled() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app { }");
        PlantUmlModuleDiagram.Options o = new PlantUmlModuleDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlModuleDiagram.generate(infos, o);
        assertFalse("legend should be suppressed: " + puml,
                puml.contains("legend top left"));
    }

    @Test
    public void testCustomTitle() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "module com.example.app { }");
        PlantUmlModuleDiagram.Options o = new PlantUmlModuleDiagram.Options();
        o.title = "My Module Graph";
        String puml = PlantUmlModuleDiagram.generate(infos, o);
        assertTrue("custom title should appear: " + puml,
                puml.contains("title My Module Graph"));
    }

    @Test
    public void testOpenModuleShowsOpenStereotype() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "open module com.example.app { }");
        String puml = PlantUmlModuleDiagram.generate(infos);
        assertTrue("open module should show <<open>>: " + puml,
                puml.contains("<<open>>"));
    }
}
