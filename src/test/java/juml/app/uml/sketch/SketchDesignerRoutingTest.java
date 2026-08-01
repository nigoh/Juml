// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「デザイナー自身の出力で図種判定が壊れ、別図種のデザイナーへ誤ルーティングされる」
 * 回帰を守るテスト。
 *
 * <p>各設計器は要素を削るほど出力の判定材料が減り、残ったキーワードが他図種と綴りを
 * 共有すると内容だけでは持ち主を決められない (配置図の {@code database} はシーケンス図の
 * 参加者宣言、ユースケース図の {@code actor} も同様)。以前は Design タブを開き直した
 * だけで別の設計器が現れ、自分の図が編集できなくなっていた。</p>
 *
 * <p>守り方は 2 段:
 * (1) {@link SketchDiagramType#detect} が座標コメント {@code '@pos} を手がかりに
 * 「シーケンス図ではない」と判断する、(2) {@link SketchPane} が「現在の設計器で完全に
 * 扱えるなら図種を維持する」。</p>
 */
public class SketchDesignerRoutingTest {

    // --- detect() 単体 (headless) -------------------------------------------

    @Test
    public void detect_databaseOnlyWithPosComment_isDeploymentNotSequence() {
        String puml = "@startuml\ndatabase DB\n'@pos DB 40 40\n@enduml\n";
        assertEquals(SketchDiagramType.DEPLOYMENT, SketchDiagramType.detect(puml));
    }

    @Test
    public void detect_actorOnlyWithPosComment_isUseCaseNotSequence() {
        String puml = "@startuml\nactor U\n'@pos U 40 40\n@enduml\n";
        assertEquals(SketchDiagramType.USECASE, SketchDiagramType.detect(puml));
    }

    @Test
    public void detect_columnlessEntityWithHideCircle_isEr() {
        // 列を持たないエンティティしか残っていない ER 図。entity 単独はシーケンス図の
        // 参加者宣言と衝突するが、hide circle は ER コーデック専用なので確定できる。
        String puml = "@startuml\nhide circle\nentity A\n'@pos A 40 40\n@enduml\n";
        assertEquals(SketchDiagramType.ER, SketchDiagramType.detect(puml));
    }

    @Test
    public void detect_realSequenceDiagram_isStillSequence() {
        // 座標コメントを持たない本物のシーケンス図は従来どおり判定されること (非退行)。
        String puml = "@startuml\nactor U\ndatabase DB\nU -> DB: query\n@enduml\n";
        assertEquals(SketchDiagramType.SEQUENCE, SketchDiagramType.detect(puml));
    }

    // --- SketchPane の図種維持 (要ディスプレイ) --------------------------------

    @Before
    public void requireDisplayForPaneTests() {
        // detect() 単体テストは headless でも動くが、@Before は全メソッドに掛かるため
        // Swing 生成を伴うテストに合わせて skip する (既存 sketch テストと同じ作法)。
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static SketchPane paneWith(String puml) {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(puml));
        return pane;
    }

    @Test
    public void pane_deploymentReducedToComponentOnly_keepsDeploymentDesigner() {
        // 配置図で component ノードだけ残すと、内容はコンポーネント図と区別できない。
        // 現在の設計器 (配置図) で完全に扱えるので図種を維持すること。
        SketchPane pane = paneWith("@startuml\nnode N\ncomponent C\n"
                + "'@pos N 40 40\n'@pos C 40 140\n@enduml\n");
        assertEquals("前提: 配置図として開かれる", SketchDiagramType.DEPLOYMENT,
                GuiActionRunner.execute(pane::activeTypeForTest));
        GuiActionRunner.execute(() -> pane.loadFrom(
                "@startuml\ncomponent C\n'@pos C 40 140\n@enduml\n"));
        assertEquals("component だけになっても配置図デザイナーを維持する",
                SketchDiagramType.DEPLOYMENT,
                GuiActionRunner.execute(pane::activeTypeForTest));
        assertTrue("維持した設計器で編集可能なこと",
                GuiActionRunner.execute(pane::isEditable));
    }

    @Test
    public void pane_allElementsDeleted_keepsCurrentDesigner() {
        // 全要素を消すと判定材料が消えて既定 (クラス図) へ落ちていた。
        SketchPane pane = paneWith("@startuml\nnode N\n'@pos N 40 40\n@enduml\n");
        assertEquals(SketchDiagramType.DEPLOYMENT,
                GuiActionRunner.execute(pane::activeTypeForTest));
        GuiActionRunner.execute(() -> pane.loadFrom("@startuml\n@enduml\n"));
        assertEquals("空になっても直前の設計器を維持する", SketchDiagramType.DEPLOYMENT,
                GuiActionRunner.execute(pane::activeTypeForTest));
    }

    @Test
    public void pane_textRewrittenToAnotherDiagramType_switchesDesigner() {
        // 図種の維持が効きすぎて本当の切り替えを妨げないこと。現在の設計器で
        // 扱えなくなったら内容判定へ委ねる。
        SketchPane pane = paneWith("@startuml\nnode N\n'@pos N 40 40\n@enduml\n");
        assertEquals(SketchDiagramType.DEPLOYMENT,
                GuiActionRunner.execute(pane::activeTypeForTest));
        GuiActionRunner.execute(() -> pane.loadFrom(
                "@startuml\nparticipant A\nparticipant B\nA -> B: hi\n@enduml\n"));
        assertEquals("本物のシーケンス図へ書き換えたら切り替わる", SketchDiagramType.SEQUENCE,
                GuiActionRunner.execute(pane::activeTypeForTest));
        assertTrue(GuiActionRunner.execute(pane::isEditable));
    }
}
