// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ComponentSketchCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless)。
 */
public class ComponentSketchCodecTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "component UI",
            "component \"Core Engine\" as Core",
            "interface API",
            "[Legacy]",
            "UI --> Core",
            "Core ..> API",
            "UI -- Legacy",
            "'@pos UI 50 50",
            "'@pos Core 250 50",
            "'@pos API 250 150",
            "'@pos Legacy 50 150",
            "@enduml",
            "");

    @Test
    public void parse_readsNodesKindsLabelsRelationsPositions() {
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(4, r.model.getNodes().size());
        assertEquals(3, r.model.getRelations().size());

        assertEquals(ComponentNode.Kind.COMPONENT, r.model.findNode("UI").getKind());
        assertEquals("Core Engine", r.model.findNode("Core").getLabel());
        assertEquals(ComponentNode.Kind.INTERFACE, r.model.findNode("API").getKind());
        // 短縮形 [Legacy] はコンポーネントとして解釈される。
        assertNotNull(r.model.findNode("Legacy"));
        assertEquals(ComponentNode.Kind.COMPONENT, r.model.findNode("Legacy").getKind());

        assertEquals(ComponentRelation.Kind.ARROW, r.model.getRelations().get(0).getKind());
        assertEquals(ComponentRelation.Kind.DEPENDENCY, r.model.getRelations().get(1).getKind());
        assertEquals(ComponentRelation.Kind.LINK, r.model.getRelations().get(2).getKind());
    }

    @Test
    public void parse_bracketEndpointsAreNormalized() {
        ComponentSketchCodec.ParseResult r =
                ComponentSketchCodec.parse("@startuml\n[A] --> [B]\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertEquals("A", r.model.getRelations().get(0).getFrom());
        assertEquals("B", r.model.getRelations().get(0).getTo());
    }

    @Test
    public void parse_packageBoundary_isReportedNotEdited() {
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(
                "@startuml\npackage App {\n  component UI\n}\n@enduml\n");
        assertFalse("パッケージ境界は未対応として保護されるはず", r.isFullySupported());
    }

    @Test
    public void parse_generalComment_isReportedNotDropped() {
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(
                "@startuml\n' keep this\ncomponent UI\n@enduml\n");
        assertFalse(r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("' keep this"));
    }

    @Test
    public void roundTrip_reachesFixedPointAndNormalizesBrackets() {
        ComponentSketchCodec.ParseResult first = ComponentSketchCodec.parse(SAMPLE);
        String regenerated = ComponentSketchCodec.toPuml(first.model);
        ComponentSketchCodec.ParseResult second = ComponentSketchCodec.parse(regenerated);
        assertTrue(second.isFullySupported());
        assertEquals("2 回目以降の再生成は固定点になるはず",
                regenerated, ComponentSketchCodec.toPuml(second.model));
        // 別名は往復で保全、[Legacy] はキーワード形へ正規化される。
        assertTrue(regenerated.contains("component \"Core Engine\" as Core"));
        assertTrue(regenerated.contains("component Legacy"));
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        assertEquals("@startuml\n@enduml\n",
                ComponentSketchCodec.toPuml(new ComponentSketchModel()));
    }

    @Test
    public void model_renameNode_updatesRelationEndpoints() {
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(SAMPLE);
        r.model.renameNode(r.model.findNode("UI"), "Frontend");
        String puml = ComponentSketchCodec.toPuml(r.model);
        assertTrue(puml.contains("Frontend --> Core"));
        assertFalse(puml.contains("UI "));
    }
}
