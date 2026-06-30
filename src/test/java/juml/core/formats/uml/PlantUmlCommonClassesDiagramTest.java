// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PlantUmlCommonClassesDiagram} の単体テスト。
 */
public class PlantUmlCommonClassesDiagramTest {

    @Test
    public void testNullClasses() {
        try {
            PlantUmlCommonClassesDiagram.generate(null);
            fail("expected IAE");
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }

    @Test
    public void testEmptyClassesGeneratesEmptyDiagram() {
        String puml = PlantUmlCommonClassesDiagram.generate(
                new ArrayList<>());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("No common classes"));
    }

    @Test
    public void testFanInRankingIdentifiesCommonClass() {
        // Util クラスを 3 つのクラスが参照する → Util が共通クラスとして検出される
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class Util { void run() {} }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class A { Util u; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class B { Util u; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class C { Util u; }"));

        PlantUmlCommonClassesDiagram.Options o = new PlantUmlCommonClassesDiagram.Options();
        o.minReferences = 1;
        List<PlantUmlCommonClassesDiagram.Entry> entries =
                PlantUmlCommonClassesDiagram.analyze(infos, o);
        assertFalse("expected at least one common class entry", entries.isEmpty());
        // 最上位が Util であること、参照件数が 3 件であること
        PlantUmlCommonClassesDiagram.Entry top = entries.get(0);
        assertEquals("Util", top.getTarget().getSimpleName());
        assertEquals(3, top.getReferenceCount());
    }

    @Test
    public void testPlantUmlOutputContainsHubAndUsesArrow() {
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class Util {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class A { Util u; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class B { Util u; }"));
        PlantUmlCommonClassesDiagram.Options o = new PlantUmlCommonClassesDiagram.Options();
        o.minReferences = 1;
        String puml = PlantUmlCommonClassesDiagram.generate(infos, o);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        // ハブクラスは <<common>> ステレオタイプで強調
        assertTrue(puml, puml.contains("<<common>>"));
        // 参照元 ..> ハブ の矢印
        assertTrue(puml, puml.contains(" ..> "));
        // 「uses」ラベル
        assertTrue(puml, puml.contains(": uses"));
    }

    @Test
    public void testMinReferencesFiltersWeakClasses() {
        // 1 件しか参照されないクラスは除外される (既定 minReferences=2)
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class Lonely {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class A { Lonely l; }"));
        String puml = PlantUmlCommonClassesDiagram.generate(infos);
        assertTrue(puml, puml.contains("No common classes"));
    }

    @Test
    public void testInterfaceReferencesAreCounted() {
        // implements される interface も「共通クラス」として検出される
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; interface Listener {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class A implements Listener {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; class B implements Listener {}"));
        PlantUmlCommonClassesDiagram.Options o = new PlantUmlCommonClassesDiagram.Options();
        o.minReferences = 2;
        List<PlantUmlCommonClassesDiagram.Entry> entries =
                PlantUmlCommonClassesDiagram.analyze(infos, o);
        assertFalse(entries.isEmpty());
        assertEquals("Listener", entries.get(0).getTarget().getSimpleName());
        assertEquals(2, entries.get(0).getReferenceCount());
    }

    @Test
    public void testSelfReferenceIsIgnored() {
        // 自分自身を参照しても fan-in にカウントされない
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.x; class Tree { Tree parent; Tree[] children; }");
        PlantUmlCommonClassesDiagram.Options o = new PlantUmlCommonClassesDiagram.Options();
        o.minReferences = 1;
        List<PlantUmlCommonClassesDiagram.Entry> entries =
                PlantUmlCommonClassesDiagram.analyze(infos, o);
        assertTrue("self-reference should not count as fan-in", entries.isEmpty());
    }

    @Test
    public void testExternalLibrariesExcluded() {
        // java.lang.String / java.util.List 等の外部型は集計対象外
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; import java.util.List; class A { List<String> xs; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.x; import java.util.List; class B { List<String> ys; }"));
        List<PlantUmlCommonClassesDiagram.Entry> entries =
                PlantUmlCommonClassesDiagram.analyze(infos, null);
        assertTrue("external types should not be counted", entries.isEmpty());
    }

    @Test
    public void testTopNLimit() {
        // 多数の共通クラス候補があっても topN で打ち切られる
        List<JavaClassInfo> infos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            infos.addAll(JavaStructureExtractor.extract(
                    "package com.x; class Hub" + i + " {}"));
            infos.addAll(JavaStructureExtractor.extract(
                    "package com.x; class UserA" + i + " { Hub" + i + " h; }"));
            infos.addAll(JavaStructureExtractor.extract(
                    "package com.x; class UserB" + i + " { Hub" + i + " h; }"));
        }
        PlantUmlCommonClassesDiagram.Options o = new PlantUmlCommonClassesDiagram.Options();
        o.minReferences = 2;
        o.topN = 2;
        String puml = PlantUmlCommonClassesDiagram.generate(infos, o);
        // topN=2 なので Hub のうち 2 つだけが図に登場する想定
        int hubLines = 0;
        for (String line : puml.split("\n")) {
            if (line.contains("<<common>>")) {
                hubLines++;
            }
        }
        assertEquals("topN should cap hub classes in the diagram", 2, hubLines);
    }
}
