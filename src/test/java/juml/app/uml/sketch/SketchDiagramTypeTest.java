// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link SketchDiagramType#detect(String)} の図種判定を検証する純ロジックテスト。
 */
public class SketchDiagramTypeTest {

    @Test
    public void detect_classTemplate_isClass() {
        assertEquals(SketchDiagramType.CLASS,
                SketchDiagramType.detect(PumlTemplate.CLASS.body()));
    }

    @Test
    public void detect_sequenceTemplate_isSequence() {
        assertEquals(SketchDiagramType.SEQUENCE,
                SketchDiagramType.detect(PumlTemplate.SEQUENCE.body()));
    }

    @Test
    public void detect_activityTemplate_isActivity() {
        assertEquals(SketchDiagramType.ACTIVITY,
                SketchDiagramType.detect(PumlTemplate.ACTIVITY.body()));
    }

    @Test
    public void detect_emptyTemplate_defaultsToClass() {
        assertEquals(SketchDiagramType.CLASS,
                SketchDiagramType.detect(PumlTemplate.EMPTY.body()));
    }

    @Test
    public void detect_stateTemplate_isState() {
        // 状態遷移図テンプレートは専用デザイナーで扱えるよう STATE と判定される。
        assertEquals(SketchDiagramType.STATE,
                SketchDiagramType.detect(PumlTemplate.STATE.body()));
    }

    @Test
    public void detect_stateDeclaration_isState() {
        assertEquals(SketchDiagramType.STATE,
                SketchDiagramType.detect("@startuml\nstate Idle\n@enduml\n"));
    }

    @Test
    public void detect_initialTransition_isState() {
        assertEquals(SketchDiagramType.STATE,
                SketchDiagramType.detect("@startuml\n[*] --> Idle\n@enduml\n"));
    }

    @Test
    public void detect_finalTransition_isState() {
        assertEquals(SketchDiagramType.STATE,
                SketchDiagramType.detect("@startuml\nRunning --> [*]\n@enduml\n"));
    }

    @Test
    public void detect_messageArrowWithoutDeclarations_isSequence() {
        assertEquals(SketchDiagramType.SEQUENCE,
                SketchDiagramType.detect("@startuml\nA -> B : hi\n@enduml\n"));
    }

    @Test
    public void detect_classAssociationArrow_staysClass() {
        // "-->" はクラス図の関連と曖昧なためシーケンス判定の材料にしない。
        assertEquals(SketchDiagramType.CLASS,
                SketchDiagramType.detect("@startuml\nA --> B\n@enduml\n"));
    }

    @Test
    public void detect_actionLine_isActivity() {
        assertEquals(SketchDiagramType.ACTIVITY,
                SketchDiagramType.detect("@startuml\n:Do work;\n@enduml\n"));
    }
}
