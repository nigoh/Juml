// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SeqSketchCodec} の解析・再生成 (round-trip) を検証する純ロジックテスト。
 */
public class SeqSketchCodecTest {

    @Test
    public void parse_sequenceTemplate_isFullySupported() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(PumlTemplate.SEQUENCE.body());
        assertTrue("テンプレートの全行がモデル化できるはず: " + r.unsupportedLines,
                r.isFullySupported());
        assertEquals(2, r.model.getParticipants().size());
        SeqParticipant user = r.model.getParticipants().get(0);
        assertEquals("User", user.getName());
        assertEquals(SeqParticipant.Kind.ACTOR, user.getKind());
        assertTrue(user.isDeclared());
        assertEquals(SeqParticipant.Kind.PARTICIPANT,
                r.model.getParticipants().get(1).getKind());
        // メッセージ 2 + activate/deactivate 2 = タイムライン 4 項目。
        assertEquals(4, r.model.getItems().size());
        assertEquals(SeqItem.Kind.MESSAGE, r.model.getItems().get(0).getKind());
        assertEquals(SeqItem.Kind.ACTIVATE, r.model.getItems().get(1).getKind());
        assertEquals(SeqItem.Kind.MESSAGE, r.model.getItems().get(2).getKind());
        assertEquals(SeqItem.Kind.DEACTIVATE, r.model.getItems().get(3).getKind());
    }

    @Test
    public void roundTrip_sequenceTemplate_isLossless() {
        String original = PumlTemplate.SEQUENCE.body();
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(original);
        assertEquals(original, SeqSketchCodec.toPuml(r.model));
    }

    @Test
    public void parse_arrowKinds_mapToEnum() {
        String puml = "@startuml\n"
                + "A -> B : sync\n"
                + "A ->> B : async\n"
                + "B --> A : reply\n"
                + "B -->> A : asyncReply\n"
                + "@enduml\n";
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(puml);
        assertTrue(r.isFullySupported());
        assertEquals(SeqItem.Arrow.SYNC, r.model.getItems().get(0).getArrow());
        assertEquals(SeqItem.Arrow.ASYNC, r.model.getItems().get(1).getArrow());
        assertEquals(SeqItem.Arrow.REPLY, r.model.getItems().get(2).getArrow());
        assertEquals(SeqItem.Arrow.ASYNC_REPLY, r.model.getItems().get(3).getArrow());
        assertEquals(puml, SeqSketchCodec.toPuml(r.model));
    }

    @Test
    public void parse_implicitParticipant_staysUndeclaredOnRegenerate() {
        String puml = "@startuml\nA -> B : hi\n@enduml\n";
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(puml);
        assertTrue(r.isFullySupported());
        assertFalse("メッセージからの暗黙参加者は宣言行を出さない",
                r.model.getParticipants().get(0).isDeclared());
        assertEquals(puml, SeqSketchCodec.toPuml(r.model));
    }

    @Test
    public void parse_selfMessage_roundTrips() {
        String puml = "@startuml\nparticipant A\nA -> A : self()\n@enduml\n";
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(puml);
        assertTrue(r.isFullySupported());
        assertEquals(puml, SeqSketchCodec.toPuml(r.model));
    }

    @Test
    public void parse_diagramName_isPreserved() {
        String puml = "@startuml login-flow\nA -> B : go\n@enduml\n";
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(puml);
        assertEquals("login-flow", r.model.getDiagramName());
        assertEquals(puml, SeqSketchCodec.toPuml(r.model));
    }

    @Test
    public void parse_labelWithoutText_isNull() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse("@startuml\nA -> B\n@enduml\n");
        assertNull(r.model.getItems().get(0).getLabel());
    }

    @Test
    public void parse_unsupportedSyntax_locksEditing() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(
                "@startuml\nA -> B : hi\nnote over A : memo\n@enduml\n");
        assertFalse("note はモデル化できないので編集ロック", r.isFullySupported());
        assertEquals(1, r.unsupportedLines.size());
    }

    @Test
    public void parse_comment_locksEditing() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(
                "@startuml\n' user comment\nA -> B\n@enduml\n");
        assertFalse("コメントは再生成で失われるため編集ロック", r.isFullySupported());
    }

    @Test
    public void model_removeParticipant_removesTouchingItems() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(PumlTemplate.SEQUENCE.body());
        SeqSketchModel model = r.model;
        model.removeParticipant(model.findParticipant("Service"));
        assertEquals(1, model.getParticipants().size());
        assertTrue("Service に触れる全項目 (メッセージ + activate/deactivate) が消える",
                model.getItems().isEmpty());
    }

    @Test
    public void model_renameParticipant_updatesItemEndpoints() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(PumlTemplate.SEQUENCE.body());
        SeqSketchModel model = r.model;
        model.renameParticipant(model.findParticipant("Service"), "Backend");
        String puml = SeqSketchCodec.toPuml(model);
        assertTrue(puml.contains("participant Backend"));
        assertTrue(puml.contains("User -> Backend : request()"));
        assertTrue(puml.contains("activate Backend"));
        assertFalse(puml.contains("Service"));
    }

    @Test
    public void model_moveItem_reordersTimeline() {
        SeqSketchModel model = new SeqSketchModel();
        SeqItem first = SeqItem.message("A", SeqItem.Arrow.SYNC, "B", "1");
        SeqItem second = SeqItem.message("A", SeqItem.Arrow.SYNC, "B", "2");
        model.getItems().add(first);
        model.getItems().add(second);
        model.moveItem(second, 0);
        assertEquals("2", model.getItems().get(0).getLabel());
        assertEquals("1", model.getItems().get(1).getLabel());
    }

    @Test
    public void model_moveParticipant_reordersLifelines() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(PumlTemplate.SEQUENCE.body());
        SeqSketchModel model = r.model;
        model.moveParticipant(model.findParticipant("Service"), 0);
        assertEquals("Service", model.getParticipants().get(0).getName());
        // 並べ替えは宣言行の順序として保存される。
        String puml = SeqSketchCodec.toPuml(model);
        assertTrue(puml.indexOf("participant Service") < puml.indexOf("actor User"));
    }
}
