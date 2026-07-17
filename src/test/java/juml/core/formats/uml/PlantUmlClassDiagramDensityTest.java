// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * クラス図の密度削減トグル (hide empty members / remove @unlinked) が
 * 対応する PlantUML ディレクティブを条件どおり出力することを固定する。
 */
public class PlantUmlClassDiagramDensityTest {

    private static List<JavaClassInfo> sample() {
        return JavaStructureExtractor.extract(
                "package x; interface Marker {} class Foo { int a; }");
    }

    @Test
    public void hideEmptyMembersEmitsDirectiveWhenOn() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.hideEmptyMembers = true;
        String puml = PlantUmlClassDiagram.generate(sample(), o);
        assertTrue(puml, puml.contains("hide empty members"));
    }

    @Test
    public void hideEmptyMembersOffByDefault() {
        String puml = PlantUmlClassDiagram.generate(sample());
        assertFalse("既定では空メンバー欄を隠さない (従来出力を維持)",
                puml.contains("hide empty members"));
    }

    @Test
    public void hideUnlinkedEmitsDirectiveWhenOn() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.hideUnlinkedClasses = true;
        String puml = PlantUmlClassDiagram.generate(sample(), o);
        assertTrue(puml, puml.contains("remove @unlinked"));
    }

    @Test
    public void hideUnlinkedOffByDefault() {
        String puml = PlantUmlClassDiagram.generate(sample());
        assertFalse("既定では孤立クラスを取り除かない (従来出力を維持)",
                puml.contains("remove @unlinked"));
    }

    @Test
    public void colorCodeStereotypesEmitsSkinparamForPresentStereotype() {
        // record は <<record>> ステレオタイプを持ち、パレットに色がある。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; public record Point(int a, int b) {}");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.colorCodeStereotypes = true;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue(puml, puml.contains("skinparam class {"));
        assertTrue(puml, puml.contains("BackgroundColor<<record>>"));
    }

    @Test
    public void colorCodeStereotypesOffByDefault() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; public record Point(int a, int b) {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertFalse("既定ではステレオタイプ色分けをしない",
                puml.contains("BackgroundColor<<"));
    }

    @Test
    public void colorCodeStereotypesEmitsNothingWhenNoPaletteMatch() {
        // 素の class にはパレット対象ステレオタイプが無いので skinparam block を出さない。
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.colorCodeStereotypes = true;
        String puml = PlantUmlClassDiagram.generate(sample(), o);
        assertFalse("色分け対象が無ければ skinparam class ブロックを出さない",
                puml.contains("BackgroundColor<<"));
    }

    @Test
    public void bothDirectivesRenderWithoutSyntaxError() {
        // ディレクティブを両方入れても同梱 PlantUML で構文エラーにならないこと。
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.hideEmptyMembers = true;
        o.hideUnlinkedClasses = true;
        String puml = PlantUmlClassDiagram.generate(sample(), o);
        assertTrue(puml, puml.startsWith("@startuml"));
        assertTrue(puml, puml.trim().endsWith("@enduml"));
    }
}
