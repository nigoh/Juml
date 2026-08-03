// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void detect_erTemplate_isEr() {
        assertEquals(SketchDiagramType.ER,
                SketchDiagramType.detect(PumlTemplate.ER.body()));
    }

    @Test
    public void detect_crowsFootRelation_isEr() {
        // crow's-foot 演算子は一意なので、hide circle が無くても ER と確定する。
        assertEquals(SketchDiagramType.ER,
                SketchDiagramType.detect("@startuml\nA ||--o{ B\n@enduml\n"));
        assertEquals(SketchDiagramType.ER,
                SketchDiagramType.detect("@startuml\nA }o--|| B\n@enduml\n"));
    }

    @Test
    public void detect_entityBlockWithHideCircle_isEr() {
        assertEquals(SketchDiagramType.ER, SketchDiagramType.detect(
                "@startuml\nhide circle\nentity \"User\" as e1 {\n  * id : int\n}\n@enduml\n"));
    }

    @Test
    public void detect_entityParticipantWithoutErMarkers_staysSequence() {
        // entity 単独 (シーケンス図の参加者宣言) は crow's-foot も列ブロックも無いため
        // ER と誤判定せず、既存のシーケンス判定を維持する。
        assertEquals(SketchDiagramType.SEQUENCE,
                SketchDiagramType.detect("@startuml\nentity Store\nA -> Store : ping\n@enduml\n"));
    }

    @Test
    public void detect_classAggregation_staysClassNotEr() {
        // クラス図の集約 o-- は crow's-foot トークンと一致しないため ER と誤判定しない。
        assertEquals(SketchDiagramType.CLASS,
                SketchDiagramType.detect("@startuml\nWhole o-- Part\n@enduml\n"));
    }

    @Test
    public void detect_objectTemplate_isObject() {
        assertEquals(SketchDiagramType.OBJECT,
                SketchDiagramType.detect(PumlTemplate.OBJECT.body()));
    }

    @Test
    public void detect_objectSampleColonForm_isObject() {
        // タスクが示す代表的なコロン形式のオブジェクト図。
        assertEquals(SketchDiagramType.OBJECT,
                SketchDiagramType.detect("@startuml\nobject User\n"
                        + "User : name = \"Alice\"\nobject Post\nUser --> Post : owns\n@enduml\n"));
    }

    @Test
    public void detect_objectKeyword_isObject() {
        assertEquals(SketchDiagramType.OBJECT,
                SketchDiagramType.detect("@startuml\nobject User\n@enduml\n"));
    }

    @Test
    public void detect_objectMarkerDoesNotRegressClass() {
        // object マーカーの追加でクラス図テンプレートの判定が揺れないこと (先取り判定の非回帰)。
        assertEquals(SketchDiagramType.CLASS,
                SketchDiagramType.detect(PumlTemplate.CLASS.body()));
        assertEquals(SketchDiagramType.COMPONENT,
                SketchDiagramType.detect(PumlTemplate.COMPONENT.body()));
    }

    @Test
    public void detect_mindmapTemplate_isMindmap() {
        assertEquals(SketchDiagramType.MINDMAP,
                SketchDiagramType.detect(PumlTemplate.MINDMAP.body()));
    }

    @Test
    public void detect_startmindmapMarker_isMindmap() {
        assertEquals(SketchDiagramType.MINDMAP,
                SketchDiagramType.detect("@startmindmap\n* Root\n@endmindmap\n"));
    }

    @Test
    public void detect_mindmapMarkerDoesNotRegressOtherTemplates() {
        // @startmindmap の先取り判定を加えても、@startuml 前提の既存 8 図種の判定が揺れないこと。
        assertEquals(SketchDiagramType.CLASS,
                SketchDiagramType.detect(PumlTemplate.CLASS.body()));
        assertEquals(SketchDiagramType.SEQUENCE,
                SketchDiagramType.detect(PumlTemplate.SEQUENCE.body()));
        assertEquals(SketchDiagramType.ACTIVITY,
                SketchDiagramType.detect(PumlTemplate.ACTIVITY.body()));
        assertEquals(SketchDiagramType.STATE,
                SketchDiagramType.detect(PumlTemplate.STATE.body()));
        assertEquals(SketchDiagramType.USECASE,
                SketchDiagramType.detect(PumlTemplate.USECASE.body()));
        assertEquals(SketchDiagramType.COMPONENT,
                SketchDiagramType.detect(PumlTemplate.COMPONENT.body()));
        assertEquals(SketchDiagramType.OBJECT,
                SketchDiagramType.detect(PumlTemplate.OBJECT.body()));
        assertEquals(SketchDiagramType.ER,
                SketchDiagramType.detect(PumlTemplate.ER.body()));
        assertEquals(SketchDiagramType.DEPLOYMENT,
                SketchDiagramType.detect(PumlTemplate.DEPLOYMENT.body()));
    }

    // --- ブロック本体のメンバー名で図種を誤判定しない ------------------------------
    //
    // 回帰: detect() が全行を平坦に走査していたため、ER 図の列名を node/cloud/artifact/
    // component/usecase にしただけで、保存して開き直すと空で編集ロックされた別図種の
    // デザイナーが開いていた (列名は自由入力なので普通に起きる)。

    @Test
    public void erColumnNamedLikeAnotherDiagramKeywordStaysEr() {
        for (String column : new String[] {"node", "cloud", "artifact", "component", "usecase"}) {
            String puml = "@startuml\nhide circle\nentity tree {\n  * id : int\n  --\n  "
                    + column + " : text\n}\n'@pos tree 0 0\n@enduml\n";
            assertEquals(column + " という列名で図種が変わらないこと",
                    SketchDiagramType.ER, SketchDiagramType.detect(puml));
        }
    }

    @Test
    public void classMemberNamedLikeAnotherDiagramKeywordStaysClass() {
        String puml = "@startuml\nclass Tree {\n  node : Node\n  component : Part\n}\n@enduml\n";
        assertEquals(SketchDiagramType.CLASS, SketchDiagramType.detect(puml));
    }

    @Test
    public void topLevelDeclarationsStillDecideTheType() {
        // 非退行: ブロックの外にある本物の宣言はこれまでどおり効く。
        assertEquals(SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(
                "@startuml\nnode Server {\n  database DB\n}\n@enduml\n"));
        assertEquals(SketchDiagramType.COMPONENT, SketchDiagramType.detect(
                "@startuml\ncomponent App\ninterface Api\n@enduml\n"));
    }

    @Test
    public void layoutCommentIsRecognised() {
        assertTrue(SketchDiagramType.isLayoutComment("'@pos tree 10 20"));
        assertTrue(SketchDiagramType.isLayoutComment("  '@pos a1 -5 -6  "));
        assertFalse(SketchDiagramType.isLayoutComment("' ordinary comment"));
        assertFalse(SketchDiagramType.isLayoutComment("class A"));
        assertFalse(SketchDiagramType.isLayoutComment(null));
    }

    // --- 設計器の出力は必ず自分の設計器へ戻る (往復の固定点) ------------------------
    //
    // 回帰: 判定が行の見た目の推測だったため、rectangle/folder/frame をトップに置いた
    // 配置図がクラス図へ流れ、開き直すと空の編集ロック済み設計器になっていた。
    // 判定は「実際にそのコーデックが丸ごと読めるか」で裏を取る。

    @Test
    public void deploymentContainerKindsOpenAnEditableDesigner() {
        // 回帰: rectangle/folder/frame をトップに置いた配置図がクラス図へ流れ、開き直すと
        // 空の編集ロック済み設計器になっていた。COMPONENT だけはコンポーネント図と
        // テキストが完全に同一になるため、どちらの設計器で開いても編集できれば十分。
        for (DeploySketchModel.DeployNode.Kind kind
                : DeploySketchModel.DeployNode.Kind.values()) {
            DeploySketchModel model = new DeploySketchModel();
            model.getNodes().add(
                    new DeploySketchModel.DeployNode(kind, "n1", "Outer", 0, 0));
            String puml = DeploySketchCodec.toPuml(model);
            SketchDiagramType detected = SketchDiagramType.detect(puml);
            if (kind == DeploySketchModel.DeployNode.Kind.COMPONENT) {
                assertTrue(kind + " はコンポーネント図と同一テキストなのでどちらでも可: " + detected,
                        detected == SketchDiagramType.DEPLOYMENT
                                || detected == SketchDiagramType.COMPONENT);
            } else {
                assertEquals(kind + " の配置図が配置図として判定されること: " + puml,
                        SketchDiagramType.DEPLOYMENT, detected);
            }
        }
    }

    @Test
    public void erColumnNamedLikeAnotherKeywordStillOpensTheErDesigner() {
        // ラウンド2で入れたブロック本体の除外に加え、コーデックによる裏取りでも守る。
        for (String column : new String[] {"node", "cloud", "artifact", "component", "usecase"}) {
            String puml = "@startuml\nhide circle\nentity tree {\n  * id : int\n  --\n  "
                    + column + " : text\n}\n'@pos tree 0 0\n@enduml\n";
            assertEquals(column + " という列名でも ER のまま",
                    SketchDiagramType.ER, SketchDiagramType.detect(puml));
            assertTrue("ER コーデックが丸ごと読めること",
                    ErSketchCodec.parse(puml).isFullySupported());
        }
    }

    @Test
    public void bracesInsideCommentsAndLabelsDoNotHideTheDiagram() {
        // 波括弧のカウントはコメントや引用ラベルの中まで数えてしまう。コーデックによる
        // 裏取りがあるので、それでも正しい設計器が開くこと。
        String puml = "@startuml\n' 参考 { メモ\ncomponent \"App {Prod}\" as c1\n"
                + "interface Api\nc1 --> Api\n@enduml\n";
        assertEquals(SketchDiagramType.COMPONENT, SketchDiagramType.detect(puml));
    }

    @Test
    public void proseInsideNotesIsNotReadAsDeclarations() {
        // 回帰: note / legend / title の本文と /' ... '/ は利用者が書いた散文であって
        // 宣言ではない。素通ししていたため、note に「node Server」と 1 行書いただけで
        // 配置図と判定され、空で編集ロックされた配置図デザイナーが開いていた。
        String erWithNote = "@startuml\nentity A {\n  id : int\n}\n"
                + "note right of A\n  node Server に配置する\nend note\n@enduml\n";
        assertEquals("note 本文の node で配置図にしない",
                SketchDiagramType.ER, SketchDiagramType.detect(erWithNote));

        String classWithLegend = "@startuml\nclass A\nlegend\n  node Server\nendlegend\n@enduml\n";
        assertEquals("legend 本文の node で配置図にしない",
                SketchDiagramType.CLASS, SketchDiagramType.detect(classWithLegend));

        String classWithBlockComment = "@startuml\nclass A\n/'\n node Server\n'/\n@enduml\n";
        assertEquals("ブロックコメント本文の node で配置図にしない",
                SketchDiagramType.CLASS, SketchDiagramType.detect(classWithBlockComment));
    }

    @Test
    public void colouredFloatingNoteDoesNotSwallowTheRestOfTheFile() {
        // 回帰: 浮動ノートの 1 行判定が行末で切れていたため、色を付けた
        // note "..." as N1 #pink がブロック開始と誤解され、end note が来ないまま
        // ファイル末尾まで全行が捨てられて、どんな図もクラス図と判定されていた。
        String state = "@startuml\nnote \"draft\" as N1 #pink\nstate Idle\nstate Busy\n"
                + "[*] --> Idle\nIdle --> Busy\n@enduml\n";
        assertEquals("色付き浮動ノートで図種を見失わないこと",
                SketchDiagramType.STATE, SketchDiagramType.detect(state));

        String plain = "@startuml\nnote \"draft\" as N1\nstate Idle\n[*] --> Idle\n@enduml\n";
        assertEquals("色の無い浮動ノートも従来どおり",
                SketchDiagramType.STATE, SketchDiagramType.detect(plain));
    }

    @Test
    public void memberLevelNoteBlockBodyIsStillTreatedAsProse() {
        // 回帰: 1 行ノートの判定が Foo::doWork の :: を本文の区切りと数えたため、
        // メンバー宛ノートの<b>ブロック開始</b>が「1 行ノート」に見え、本文の散文が
        // マスクされず宣言として読まれていた (JavaDoc をそのまま note へ入れる図で頻出)。
        String block = "@startuml\nclass Foo {\n +doWork()\n}\nnote right of Foo::doWork\n"
                + "  node Server on which it runs\nend note\n@enduml\n";
        assertEquals("メンバー宛ノートの本文で配置図にしないこと",
                SketchDiagramType.CLASS, SketchDiagramType.detect(block));

        String oneLine = "@startuml\nclass Foo\n"
                + "note right of Foo::doWork : runs on node Server\n@enduml\n";
        assertEquals("コロン形式の 1 行ノートは従来どおり本文ごと除くこと",
                SketchDiagramType.CLASS, SketchDiagramType.detect(oneLine));
    }

    @Test
    public void unparsableTextStillPicksADesignerToShowLocked() {
        // どのコーデックも扱えないテキストは、従来どおり行走査の答えで表示ロックする。
        String puml = "@startuml\nnode Server\nnote over Server\n未対応の記法\nend note\n"
                + "!include foo.puml\n@enduml\n";
        assertEquals(SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(puml));
    }
}
