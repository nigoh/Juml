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
    public void alignmentPrefixedHeaderAndFooterBlocksAreProse() {
        // 回帰: legend / title / header / footer は配置語を前に置ける (center footer)。
        // 前置きを見落としてブロック開始と認識できず、本文の散文が宣言として読まれ、
        // クラス図が配置図・オブジェクト図の設計器で開いていた。
        String footer = "@startuml\nclass Foo\ncenter footer\nnode Server 2026\n"
                + "endfooter\n@enduml\n";
        assertEquals("center footer の本文で配置図にしないこと",
                SketchDiagramType.CLASS, SketchDiagramType.detect(footer));

        String header = "@startuml\nclass Foo\nleft header\nobject storage report\n"
                + "endheader\n@enduml\n";
        assertEquals("left header の本文でオブジェクト図にしないこと",
                SketchDiagramType.CLASS, SketchDiagramType.detect(header));
    }

    @Test
    public void noteTargetQuotedNameMayContainAColon() {
        // 回帰: 引用名の中のコロンを本文の区切りと数えたため、
        // note over "Alice: boss" が「1 行ノート」に見え、ブロック本文が宣言として読まれた。
        String puml = "@startuml\nparticipant \"Alice: boss\" as A\nA -> A : x\n"
                + "note over \"Alice: boss\"\n node Server\nend note\n@enduml\n";
        assertEquals("引用名のコロンで図種を取り違えないこと",
                SketchDiagramType.SEQUENCE, SketchDiagramType.detect(puml));
    }

    @Test
    public void participantNamedNoteDoesNotSwallowTheFile() {
        // 回帰: note を綴りだけで判定していたため、Note という名前の参加者への関連
        // (Note --> Alice) がノートの開始に見え、end note が来ないままファイル末尾まで
        // 全行が捨てられていた。位置語が続く形だけをノートの開始として受理する。
        String puml = "@startuml\nNote --> Alice\nactivate Alice\n@enduml\n";
        assertEquals("Note という名前の参加者で図種を見失わないこと",
                SketchDiagramType.SEQUENCE, SketchDiagramType.detect(puml));
    }

    @Test
    public void realNoteBlockFormsAreStillTreatedAsProse() {
        // 非退行: ノート開始の受理を絞ったので、本物のブロック開始が漏れないこと。
        for (String head : new String[] {"note over Foo", "note left of Foo",
                                         "note right of Foo", "note as N1", "note", "note #pink"}) {
            String puml = "@startuml\nclass Foo\n" + head + "\n node Server\n"
                    + "end note\n@enduml\n";
            assertEquals(head + " の本文は散文として扱うこと",
                    SketchDiagramType.CLASS, SketchDiagramType.detect(puml));
        }
    }

    @Test
    public void plantUmlsOwnReadingOverrulesTheLineScan() {
        // 回帰: 行走査は自由記述や綴りを共有する識別子で誤判定する。塞いでも別の形が
        // 出てくる (note across / note on link / Legend --> Done / Title --> Footer) ので、
        // 正規表現を完璧にすることを当てにせず、PlantUML 自身の解釈で裏を取る。
        String noteAcross = "@startuml\nAlice -> Bob : start()\nnote across\n"
                + "node names are written to the log\nend note\nBob -> Alice : ok\n@enduml\n";
        assertEquals("note across のシーケンス図を配置図にしないこと",
                SketchDiagramType.SEQUENCE, SketchDiagramType.detect(noteAcross));

        String noteOnLink = "@startuml\nclass A\nclass B\nA --> B\nnote on link\n"
                + "node names are written to the log\nend note\n@enduml\n";
        assertEquals("note on link のクラス図を配置図にしないこと",
                SketchDiagramType.CLASS, SketchDiagramType.detect(noteOnLink));

        String namedLegend = "@startuml\nLegend --> Done\nDone --> [*]\n@enduml\n";
        assertEquals("Legend という名前の要素で図種を見失わないこと",
                SketchDiagramType.STATE, SketchDiagramType.detect(namedLegend));

        String namedTitle = "@startuml\nTitle --> Footer\nFooter --> [*]\n@enduml\n";
        assertEquals("Title / Footer という名前の要素で図種を見失わないこと",
                SketchDiagramType.STATE, SketchDiagramType.detect(namedTitle));
    }

    @Test
    public void everyDesignersOwnOutputStillRoundTripsToItself() {
        // 非退行の要: PlantUML の解釈による裏取りを入れても、10 設計器それぞれの
        // 代表的な出力が自分の設計器へ戻ること (クラス/オブジェクト/ER は PlantUML から
        // 見ればどれも ClassDiagram、ユースケース/コンポーネント/配置はどれも
        // DescriptionDiagram なので、裏取りが乱暴だとここで潰れる)。
        Object[][] cases = {
            {SketchDiagramType.CLASS, "@startuml\nclass A\nclass B\nA --> B\n@enduml\n"},
            {SketchDiagramType.SEQUENCE, "@startuml\nAlice -> Bob : hi\n@enduml\n"},
            {SketchDiagramType.ACTIVITY, "@startuml\nstart\n:do it;\nstop\n@enduml\n"},
            {SketchDiagramType.STATE, "@startuml\nstate Idle\n[*] --> Idle\n@enduml\n"},
            {SketchDiagramType.USECASE, "@startuml\nactor U\nusecase UC1\nU --> UC1\n@enduml\n"},
            {SketchDiagramType.COMPONENT,
                "@startuml\ncomponent C\ninterface I\nC --> I\n@enduml\n"},
            {SketchDiagramType.OBJECT, "@startuml\nobject O1\nobject O2\nO1 --> O2\n@enduml\n"},
            {SketchDiagramType.ER, "@startuml\nhide circle\nentity A {\n id : int\n}\n@enduml\n"},
            {SketchDiagramType.DEPLOYMENT, "@startuml\nnode N\nartifact A\nN --> A\n@enduml\n"},
            {SketchDiagramType.MINDMAP, "@startmindmap\n* Root\n** Child\n@endmindmap\n"},
        };
        for (Object[] c : cases) {
            assertEquals(c[0] + " の出力は自分の設計器へ戻ること",
                    c[0], SketchDiagramType.detect((String) c[1]));
        }
    }

    @Test
    public void parenthesisedUseCaseDiagramsOpenInTheUseCaseDesigner() {
        // 回帰: PlantUML の解釈による裏取りが、どの候補も丸ごと読めないときに
        // 代表値 (記述図 → コンポーネント) をそのまま返していた。コンポーネント
        // コーデックは actor も (Usecase) も読めないので、使用例図が<b>空で編集
        // ロックされたコンポーネント設計器</b>で開き、図に一切触れなくなっていた。
        // 括弧形式は PlantUML の使用例図ドキュメントが一貫して使う書き方。
        String puml = "@startuml\nactor User\nUser --> (Login)\n@enduml\n";
        assertEquals(SketchDiagramType.USECASE, SketchDiagramType.detect(puml));

        String twoActors = "@startuml\nleft to right direction\nactor A\nactor B\n"
                + "A --> (UC1)\nB --> (UC2)\n@enduml\n";
        assertEquals(SketchDiagramType.USECASE, SketchDiagramType.detect(twoActors));
    }

    @Test
    public void deploymentNestedInsideAComponentStaysDeployment() {
        // 回帰: 走査の答えが部分的に読めるだけで打ち切っていたため、配置図コーデックなら
        // 全部読める図でもコンポーネント設計器が一部だけ認識して編集ロックで開いていた。
        // (入れ子の artifact はトップレベルに現れないので走査はコンポーネント図と読む)
        String puml = "@startuml Infra\ncomponent \"App Tier\" as app {\n"
                + "  artifact \"svc.jar\" as jar\n}\ndatabase \"PG\" as db\n"
                + "jar --> db : jdbc\n'@pos app 10 10\n'@pos jar 0 0\n'@pos db 260 10\n@enduml\n";
        assertEquals(SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(puml));
    }

    @Test
    public void aCodecThatMerelyReadsTheTextDoesNotGetToClaimIt() {
        // 回帰 (critical): 「最初に丸ごと読めた設計器を採る」段階に裏取りが無かったため、
        // ユースケースコーデック (actor 宣言と --> 関連を読める) が無関係な図を横取りし、
        // <b>編集可能な状態で</b>開いていた。最初の操作で元の図が書き潰される。
        // PlantUML 自身の解釈で候補を絞る。
        String rightGeneralization = "@startuml\nDog --|> Animal\nAnimal --> Food : eats\n@enduml\n";
        assertEquals("--|> のクラス図をユースケース図にしないこと",
                SketchDiagramType.CLASS, SketchDiagramType.detect(rightGeneralization));

        String quotedParticipant = "@startuml\nBrowser --> Api : request\n"
                + "Api --> \"Order DB\" : query\n@enduml\n";
        assertEquals("引用名のシーケンス図を配置図にしないこと",
                SketchDiagramType.SEQUENCE, SketchDiagramType.detect(quotedParticipant));
    }

    @Test
    public void actorNamedAfterAKeywordStaysASequenceDiagram() {
        // 回帰: 応答矢印だけの図で参加者を明示宣言するようにしたが、ACTOR 種別は
        // "actor X" を出す。これはユースケースコーデックも丸ごと読めるため、
        // 参加者名が走査キーワード (cloud/node/artifact/usecase/component) と同綴りだと
        // 走査がシーケンス図から逸れ、ユースケース設計器が編集可能で開いていた。
        for (String name : new String[] {"cloud", "node", "artifact", "usecase", "component"}) {
            String puml = "@startuml\nactor " + name + "\nactor User\n"
                    + name + " --> User : ack\nUser --> " + name + " : done\n@enduml\n";
            assertEquals(name + " という名前の actor でシーケンス図を見失わないこと",
                    SketchDiagramType.SEQUENCE, SketchDiagramType.detect(puml));
        }
    }

    @Test
    public void designerOutputsPlantUmlReadsAsAClassDiagramStillOpenTheirOwnDesigner() {
        // 回帰: 要素が interface だけ / 中身の無い入れ物だけになると、PlantUML は
        // コンポーネント図も配置図も ClassDiagram と読む。その解釈で候補を
        // {CLASS,OBJECT,ER} に絞っていたため、本来読めるコーデックが候補から外れ、
        // 空で編集ロックされたクラス設計器が開いていた。
        String labelledInterfaces = "@startuml Ports\ninterface \"ILogger\" as ILog\n"
                + "interface \"IRepo\" as IRep\nILog --> IRep\n"
                + "'@pos ILog 10 20\n'@pos IRep 210 20\n@enduml\n";
        assertEquals("表示名付き interface のコンポーネント図",
                SketchDiagramType.COMPONENT, SketchDiagramType.detect(labelledInterfaces));

        String emptyContainer = "@startuml\nrectangle Rect {\n}\n\n'@pos Rect 60 40\n@enduml\n";
        assertEquals("中身の無い入れ物だけの配置図",
                SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(emptyContainer));
    }

    @Test
    public void layoutCommentsRuleOutThePositionlessDesigners() {
        // 回帰: '@pos を書くのは位置を持つ設計器だけで、シーケンス図・アクティビティ図の
        // コーデックは決して出さない。にもかかわらず、未対応行が 1 行混じった途端に
        // PlantUML の「曖昧な断片は既定でシーケンス」という読みが通ってしまい、
        // 配置図がシーケンス設計器で開いていた。
        String withUnsupported = "@startuml\ndatabase A\ndatabase B\nA --> B\n"
                + "' plain comment\n'@pos A 50 50\n'@pos B 250 50\n@enduml\n";
        assertEquals("未対応行が混じっても座標付きの図はシーケンスにしないこと",
                SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(withUnsupported));

        String baseline = "@startuml\ndatabase A\ndatabase B\nA --> B\n\n"
                + "'@pos A 50 50\n'@pos B 250 50\n@enduml\n";
        assertEquals("非退行: 未対応行が無い場合",
                SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(baseline));
    }

    @Test
    public void unparsableTextStillPicksADesignerToShowLocked() {
        // どのコーデックも扱えないテキストは、従来どおり行走査の答えで表示ロックする。
        String puml = "@startuml\nnode Server\nnote over Server\n未対応の記法\nend note\n"
                + "!include foo.puml\n@enduml\n";
        assertEquals(SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(puml));
    }
}
