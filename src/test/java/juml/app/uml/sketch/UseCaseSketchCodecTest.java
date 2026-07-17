// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link UseCaseSketchCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless)。
 */
public class UseCaseSketchCodecTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "actor User",
            "usecase \"Do something\" as UC1",
            "usecase UC2",
            "User --> UC1",
            "User --> UC2 : trigger",
            "UC1 ..> UC2 : include",
            "'@pos User 50 50",
            "'@pos UC1 250 50",
            "'@pos UC2 250 150",
            "@enduml",
            "");

    @Test
    public void parse_readsNodesKindsLabelsRelationsPositions() {
        UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(3, r.model.getNodes().size());
        assertEquals(3, r.model.getRelations().size());

        UseCaseNode user = r.model.findNode("User");
        assertNotNull(user);
        assertEquals(UseCaseNode.Kind.ACTOR, user.getKind());
        assertEquals(50, user.getX());

        UseCaseNode uc1 = r.model.findNode("UC1");
        assertEquals(UseCaseNode.Kind.USECASE, uc1.getKind());
        assertEquals("Do something", uc1.getLabel());

        assertEquals(UseCaseRelation.Kind.ASSOCIATION,
                r.model.getRelations().get(0).getKind());
        assertEquals(UseCaseRelation.Kind.DEPENDENCY,
                r.model.getRelations().get(2).getKind());
        assertEquals("include", r.model.getRelations().get(2).getLabel());
    }

    @Test
    public void parse_generalizationArrow() {
        UseCaseSketchCodec.ParseResult r =
                UseCaseSketchCodec.parse("@startuml\nUC1 --|> UC2\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertEquals(UseCaseRelation.Kind.GENERALIZATION,
                r.model.getRelations().get(0).getKind());
    }

    @Test
    public void parse_boundaryRectangle_isReportedNotEdited() {
        UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(
                "@startuml\nrectangle System {\n  usecase UC1\n}\n@enduml\n");
        assertFalse("境界 rectangle は未対応として保護されるはず", r.isFullySupported());
    }

    @Test
    public void parse_directionLine_isReportedNotEdited() {
        UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(
                "@startuml\nleft to right direction\nactor User\n@enduml\n");
        assertFalse("向き指定は未対応として保護されるはず", r.isFullySupported());
    }

    @Test
    public void parse_generalComment_isReportedNotDropped() {
        UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(
                "@startuml\n' keep this\nactor User\n@enduml\n");
        assertFalse(r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("' keep this"));
    }

    @Test
    public void parse_relationToUndeclared_createsImplicitUsecase() {
        UseCaseSketchCodec.ParseResult r =
                UseCaseSketchCodec.parse("@startuml\nUser --> UC9\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertEquals(UseCaseNode.Kind.USECASE, r.model.findNode("UC9").getKind());
    }

    @Test
    public void roundTrip_reachesFixedPoint() {
        UseCaseSketchCodec.ParseResult first = UseCaseSketchCodec.parse(SAMPLE);
        String regenerated = UseCaseSketchCodec.toPuml(first.model);
        UseCaseSketchCodec.ParseResult second = UseCaseSketchCodec.parse(regenerated);
        assertTrue(second.isFullySupported());
        assertEquals("2 回目以降の再生成は固定点になるはず",
                regenerated, UseCaseSketchCodec.toPuml(second.model));
        // 別名も往復で保全される。
        assertTrue(regenerated.contains("usecase \"Do something\" as UC1"));
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        assertEquals("@startuml\n@enduml\n",
                UseCaseSketchCodec.toPuml(new UseCaseSketchModel()));
    }

    @Test
    public void model_renameNode_updatesRelationEndpoints() {
        UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(SAMPLE);
        r.model.renameNode(r.model.findNode("User"), "Admin");
        String puml = UseCaseSketchCodec.toPuml(r.model);
        assertTrue(puml.contains("Admin --> UC1"));
        assertFalse(puml.contains("User"));
    }
}
