// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchPumlCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless で動く)。
 * GUI デザイナーの信頼性はこの round-trip の正しさに懸かっている。
 */
public class SketchPumlCodecTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "abstract class Base {",
            "  # id : long",
            "  + save() : void",
            "}",
            "interface Greetable {",
            "  + greet() : String",
            "}",
            "enum Color",
            "class Child",
            "",
            "Base <|-- Child",
            "Greetable <|.. Child : impl",
            "Child o-- Color",
            "",
            "'@pos Base 10 20",
            "'@pos Child 300 200",
            "@enduml",
            "");

    @Test
    public void parse_readsClassesKindsMembersAndPositions() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(4, r.model.getClasses().size());

        SketchClass base = r.model.findClass("Base");
        assertNotNull(base);
        assertEquals(SketchClass.Kind.ABSTRACT, base.getKind());
        assertEquals(java.util.List.of("# id : long"), base.getFields());
        assertEquals(java.util.List.of("+ save() : void"), base.getMethods());
        assertEquals(10, base.getX());
        assertEquals(20, base.getY());

        assertEquals(SketchClass.Kind.INTERFACE,
                r.model.findClass("Greetable").getKind());
        assertEquals(SketchClass.Kind.ENUM, r.model.findClass("Color").getKind());
    }

    @Test
    public void parse_readsRelationsWithKindAndLabel() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        assertEquals(3, r.model.getRelations().size());
        SketchRelation impl = r.model.getRelations().get(1);
        assertEquals(SketchRelation.Kind.IMPLEMENTS, impl.getKind());
        assertEquals("Greetable", impl.getLeft());
        assertEquals("Child", impl.getRight());
        assertEquals("impl", impl.getLabel());
        assertEquals(SketchRelation.Kind.AGGREGATION,
                r.model.getRelations().get(2).getKind());
    }

    @Test
    public void roundTrip_preservesModelSemantics() {
        SketchPumlCodec.ParseResult first = SketchPumlCodec.parse(SAMPLE);
        String regenerated = SketchPumlCodec.toPuml(first.model);
        SketchPumlCodec.ParseResult second = SketchPumlCodec.parse(regenerated);

        assertTrue("再生成テキストも全対応構文のはず", second.isFullySupported());
        assertEquals(first.model.getClasses().size(), second.model.getClasses().size());
        assertEquals(first.model.getRelations().size(), second.model.getRelations().size());
        // 2 回目以降の再生成は固定点 (テキストが完全一致) になるはず。
        assertEquals(regenerated, SketchPumlCodec.toPuml(second.model));
        // 位置も '@pos コメントで保存・復元される。
        assertEquals(300, second.model.findClass("Child").getX());
        assertEquals(200, second.model.findClass("Child").getY());
    }

    @Test
    public void parse_relationToUndeclaredClass_createsImplicitClass() {
        SketchPumlCodec.ParseResult r =
                SketchPumlCodec.parse("@startuml\nFoo --> Bar\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertNotNull(r.model.findClass("Foo"));
        assertNotNull(r.model.findClass("Bar"));
        assertEquals(SketchRelation.Kind.ASSOCIATION,
                r.model.getRelations().get(0).getKind());
    }

    @Test
    public void parse_unsupportedSyntax_isReportedNotDropped() {
        String seq = PumlTemplate.SEQUENCE.body(); // actor / participant / -> は未対応
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(seq);
        assertFalse("シーケンス図構文は未対応として報告されるはず",
                r.isFullySupported());
    }

    @Test
    public void parse_classTemplate_isFullySupported() {
        SketchPumlCodec.ParseResult r =
                SketchPumlCodec.parse(PumlTemplate.CLASS.body());
        assertTrue("クラス図テンプレートは GUI 編集可能なはず: " + r.unsupportedLines,
                r.isFullySupported());
        assertNotNull(r.model.findClass("Example"));
        assertEquals(2, r.model.getRelations().size());
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        String puml = SketchPumlCodec.toPuml(new SketchModel());
        assertEquals("@startuml\n@enduml\n", puml);
    }

    @Test
    public void model_removeClass_dropsTouchingRelations() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        r.model.removeClass(r.model.findClass("Child"));
        assertTrue("Child に接続する関係は全て消えるはず",
                r.model.getRelations().isEmpty());
    }

    @Test
    public void model_renameClass_updatesRelationEndpoints() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        r.model.renameClass(r.model.findClass("Child"), "Renamed");
        String puml = SketchPumlCodec.toPuml(r.model);
        assertTrue(puml.contains("Base <|-- Renamed"));
        assertFalse(puml.contains("Child"));
    }
}
