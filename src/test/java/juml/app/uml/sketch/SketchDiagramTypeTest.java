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
    public void detect_usecaseTemplate_isUseCase() {
        assertEquals(SketchDiagramType.USECASE,
                SketchDiagramType.detect(PumlTemplate.USECASE.body()));
    }

    @Test
    public void detect_usecaseKeyword_isUseCase() {
        assertEquals(SketchDiagramType.USECASE,
                SketchDiagramType.detect("@startuml\nactor User\nusecase UC1\n@enduml\n"));
    }

    @Test
    public void detect_actorWithoutUsecase_staysSequence() {
        // actor はシーケンス図と共有。usecase キーワードが無ければユースケース図と誤判定しない。
        assertEquals(SketchDiagramType.SEQUENCE,
                SketchDiagramType.detect(PumlTemplate.SEQUENCE.body()));
    }

    @Test
    public void detect_componentTemplate_isComponent() {
        assertEquals(SketchDiagramType.COMPONENT,
                SketchDiagramType.detect(PumlTemplate.COMPONENT.body()));
    }

    @Test
    public void detect_componentKeywordOrBracket_isComponent() {
        assertEquals(SketchDiagramType.COMPONENT,
                SketchDiagramType.detect("@startuml\ncomponent UI\n@enduml\n"));
        assertEquals(SketchDiagramType.COMPONENT,
                SketchDiagramType.detect("@startuml\n[UI]\n@enduml\n"));
    }

    @Test
    public void detect_stateInitialBracket_isNotComponent() {
        // [*] は識別子でないのでコンポーネント短縮形と混同せず状態図のまま。
        assertEquals(SketchDiagramType.STATE,
                SketchDiagramType.detect("@startuml\n[*] --> Idle\n@enduml\n"));
    }

    @Test
    public void detect_actionLine_isActivity() {
        assertEquals(SketchDiagramType.ACTIVITY,
                SketchDiagramType.detect("@startuml\n:Do work;\n@enduml\n"));
    }

    @Test
    public void detect_deploymentSample_isDeployment() {
        // node / artifact / cloud 宣言を含む代表的な配置図は DEPLOYMENT と判定される。
        assertEquals(SketchDiagramType.DEPLOYMENT,
                SketchDiagramType.detect(String.join("\n",
                        "@startuml",
                        "node \"App Server\" as srv",
                        "artifact webapp",
                        "database \"PostgreSQL\" as db",
                        "cloud CDN",
                        "srv --> db : JDBC",
                        "CDN --> srv",
                        "@enduml", "")));
    }

    @Test
    public void detect_deploymentTemplate_isDeployment() {
        assertEquals(SketchDiagramType.DEPLOYMENT,
                SketchDiagramType.detect(PumlTemplate.DEPLOYMENT.body()));
    }

    @Test
    public void detect_nodeArtifactCloudKeywords_areDeployment() {
        assertEquals(SketchDiagramType.DEPLOYMENT,
                SketchDiagramType.detect("@startuml\nnode Srv\n@enduml\n"));
        assertEquals(SketchDiagramType.DEPLOYMENT,
                SketchDiagramType.detect("@startuml\nartifact app\n@enduml\n"));
        assertEquals(SketchDiagramType.DEPLOYMENT,
                SketchDiagramType.detect("@startuml\ncloud CDN\n@enduml\n"));
    }

    @Test
    public void detect_databaseParticipantWithoutNode_staysSequence() {
        // database はシーケンス図の参加者宣言と共有するため、node/artifact/cloud が無ければ
        // 配置図と誤判定せずシーケンス図のままにする。
        assertEquals(SketchDiagramType.SEQUENCE,
                SketchDiagramType.detect(
                        "@startuml\ndatabase DB\nUser -> DB : query\n@enduml\n"));
    }
}
