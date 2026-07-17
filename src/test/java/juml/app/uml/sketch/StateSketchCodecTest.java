// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link StateSketchCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless)。
 */
public class StateSketchCodecTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "state Idle",
            "state Running",
            "[*] --> Idle",
            "Idle --> Running : start",
            "Running --> Idle : stop",
            "Running --> [*] : finish",
            "'@pos Idle 50 50",
            "'@pos Running 250 50",
            "@enduml",
            "");

    @Test
    public void parse_readsStatesTransitionsAndPositions() {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getStates().size());
        assertEquals(4, r.model.getTransitions().size());
        StateNode idle = r.model.findState("Idle");
        assertNotNull(idle);
        assertEquals(50, idle.getX());
        assertEquals(50, idle.getY());
        StateTransition start = r.model.getTransitions().get(1);
        assertEquals("Idle", start.getFrom());
        assertEquals("Running", start.getTo());
        assertEquals("start", start.getLabel());
    }

    @Test
    public void parse_initialAndFinalPseudostates() {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(SAMPLE);
        assertEquals("[*]", r.model.getTransitions().get(0).getFrom());
        assertEquals("Idle", r.model.getTransitions().get(0).getTo());
        assertEquals("[*]", r.model.getTransitions().get(3).getTo());
        // [*] は状態ノードにしない。
        assertNull("[*] は状態として保持しない", r.model.findState("[*]"));
    }

    @Test
    public void parse_transitionToUndeclaredState_createsImplicitState() {
        StateSketchCodec.ParseResult r =
                StateSketchCodec.parse("@startuml\nA --> B : go\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertNotNull(r.model.findState("A"));
        assertNotNull(r.model.findState("B"));
    }

    @Test
    public void parse_compositeState_isReportedNotEdited() {
        // 複合状態は往復できないため編集をロックする (原文保全)。
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(
                "@startuml\nstate Running {\n  [*] --> Working\n}\n@enduml\n");
        assertFalse("複合状態は未対応として保護されるはず", r.isFullySupported());
    }

    @Test
    public void parse_generalComment_isReportedNotDropped() {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(
                "@startuml\n' keep this\nstate A\n@enduml\n");
        assertFalse(r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("' keep this"));
    }

    @Test
    public void namedStartuml_isPreservedThroughRoundTrip() {
        StateSketchCodec.ParseResult r =
                StateSketchCodec.parse("@startuml Machine\nstate A\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertEquals("Machine", r.model.getDiagramName());
        assertTrue(StateSketchCodec.toPuml(r.model).startsWith("@startuml Machine\n"));
    }

    @Test
    public void roundTrip_reachesFixedPoint() {
        StateSketchCodec.ParseResult first = StateSketchCodec.parse(SAMPLE);
        String regenerated = StateSketchCodec.toPuml(first.model);
        StateSketchCodec.ParseResult second = StateSketchCodec.parse(regenerated);
        assertTrue("再生成テキストも全対応構文のはず", second.isFullySupported());
        assertEquals("2 回目以降の再生成は固定点になるはず",
                regenerated, StateSketchCodec.toPuml(second.model));
        assertEquals(first.model.getStates().size(), second.model.getStates().size());
        assertEquals(first.model.getTransitions().size(), second.model.getTransitions().size());
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        assertEquals("@startuml\n@enduml\n", StateSketchCodec.toPuml(new StateSketchModel()));
    }

    @Test
    public void model_removeState_dropsTouchingTransitions() {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(SAMPLE);
        r.model.removeState(r.model.findState("Running"));
        // Running に接続する遷移 (start/stop/finish) は消え、[*] --> Idle だけ残る。
        assertEquals(1, r.model.getTransitions().size());
        assertEquals("Idle", r.model.getTransitions().get(0).getTo());
    }

    @Test
    public void model_renameState_updatesTransitionEndpoints() {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(SAMPLE);
        r.model.renameState(r.model.findState("Idle"), "Ready");
        String puml = StateSketchCodec.toPuml(r.model);
        assertTrue(puml.contains("[*] --> Ready"));
        assertFalse(puml.contains("Idle"));
    }
}
