// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 日本語で名前を付けた要素がビジュアル設計器で失われないことの回帰テスト。
 *
 * <p>以前は各コーデックの識別子が {@code [A-Za-z_$][\w$]*} と ASCII 限定だった
 * (Java の {@code \w} は既定で ASCII のみ)。そのため {@code entity ユーザ} や
 * {@code class 注文} が未対応行に落ち、<b>要素ごとモデルから消えたうえで編集がロック</b>
 * されていた。PlantUML 1.2026.6 は引用符なしの日本語識別子をすべて描画できることを
 * 実機で確認済みなので、設計器側の規則を Unicode へ揃えた。</p>
 *
 * <p>各図種について「日本語名のテキストを読める」ことと「編集がロックされない」ことの
 * 両方を見る。前者だけだと、要素を落としつつロックだけ外す退行を検出できない。</p>
 */
public class SketchUnicodeIdentifierTest {

    private static String uml(String body) {
        return "@startuml\n" + body + "\n@enduml\n";
    }

    @Test
    public void classDesignerKeepsJapaneseClassNames() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(
                uml("class ユーザ\nclass 注文\nユーザ --> 注文"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getClasses().size());
        assertEquals("ユーザ", r.model.getClasses().get(0).getName());
        assertEquals(1, r.model.getRelations().size());
    }

    @Test
    public void erDesignerKeepsJapaneseEntityAndColumnNames() {
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(uml(
                "hide circle\nentity ユーザ {\n  * 番号 : int\n  --\n  氏名 : varchar\n}"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(1, r.model.getEntities().size());
        ErSketchModel.Entity e = r.model.getEntities().get(0);
        assertEquals("ユーザ", e.getAlias());
        assertEquals(2, e.getColumns().size());
        assertEquals("番号", e.getColumns().get(0).getName());
        assertTrue("先頭の * が主キーとして残ること", e.getColumns().get(0).isPrimaryKey());
        assertEquals("氏名", e.getColumns().get(1).getName());
    }

    @Test
    public void useCaseDesignerKeepsJapaneseActorAndUseCase() {
        UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(
                uml("actor 利用者\nusecase 注文する\n利用者 --> 注文する"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getNodes().size());
        assertEquals(1, r.model.getRelations().size());
    }

    @Test
    public void stateDesignerKeepsJapaneseStates() {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(
                uml("state 待機\nstate 実行中\n待機 --> 実行中 : 開始"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getStates().size());
        assertEquals(1, r.model.getTransitions().size());
    }

    @Test
    public void sequenceDesignerKeepsJapaneseParticipants() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(
                uml("participant 利用者\nparticipant 画面\n利用者 -> 画面 : 押す"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getParticipants().size());
    }

    @Test
    public void componentDesignerKeepsJapaneseComponents() {
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(
                uml("component 認証\ninterface 入口\n入口 --> 認証"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getNodes().size());
        assertEquals(1, r.model.getRelations().size());
    }

    @Test
    public void objectDesignerKeepsJapaneseObjects() {
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(
                uml("object 田中さん\n田中さん : 年齢 = 30"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(1, r.model.getObjects().size());
        assertEquals("田中さん", r.model.getObjects().get(0).getName());
    }

    @Test
    public void deployDesignerKeepsJapaneseNodes() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(
                uml("node サーバ {\n  artifact アプリ\n}"));
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(1, r.model.getNodes().size());
        assertEquals("サーバ", r.model.getNodes().get(0).getId());
        assertEquals(1, r.model.getNodes().get(0).getChildren().size());
    }

    @Test
    public void japaneseNamesSurviveARoundTripThroughPuml() {
        // 読めるだけでなく、書き戻して読み直しても同じであること (GUI 編集の往復)。
        String source = uml("hide circle\nentity 注文 {\n  * 番号 : int\n}");
        ErSketchCodec.ParseResult first = ErSketchCodec.parse(source);
        String regenerated = ErSketchCodec.toPuml(first.model);
        ErSketchCodec.ParseResult second = ErSketchCodec.parse(regenerated);
        assertTrue(second.isFullySupported());
        assertEquals("注文", second.model.getEntities().get(0).getAlias());
        assertEquals("番号", second.model.getEntities().get(0).getColumns().get(0).getName());
    }

    @Test
    public void digitLeadingNameStaysUnsupported() {
        // 据え置きの境界: 先頭が数字の名前は従来どおり識別子として扱わない
        // (PlantUML 自体は通すが、座標コメント等の数字トークンと取り違えないため)。
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(uml("class 1st"));
        assertTrue(r.model.getClasses().isEmpty());
    }
}
