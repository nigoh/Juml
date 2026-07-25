// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.MindmapNode.Side;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertSame;

/**
 * {@link MindmapSketchEditor} の選択連動 UI (side コンボが選択ノードの実効 side に追従する
 * こと) を検証する。純粋な Swing 生成のみだがヘッドレスでは生成が失敗するため skip する。
 */
public class MindmapSketchEditorTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void sideCombo_followsSelectedNodeEffectiveSide() {
        MindmapSketchEditor editor = GuiActionRunner.execute(MindmapSketchEditor::new);
        // Root(AUTO) → L(LEFT) → G(AUTO) の LEFT 枝を組んで載せる (対応構文)。
        GuiActionRunner.execute(() -> editor.load(String.join("\n",
                "@startmindmap", "* Root", "-- L", "--- G", "@endmindmap", "")));
        MindmapNode root = editor.rootForTest();
        MindmapNode l = root.getChildren().get(0);
        MindmapNode g = l.getChildren().get(0);

        // ルート選択 → 自身の AUTO。
        GuiActionRunner.execute(() -> editor.selectForTest(root));
        assertSame("ルートは AUTO を表示", Side.AUTO,
                GuiActionRunner.execute(editor::comboSideForTest));
        // LEFT 枝の起点を選択 → LEFT。
        GuiActionRunner.execute(() -> editor.selectForTest(l));
        assertSame("枝起点 L は LEFT を表示", Side.LEFT,
                GuiActionRunner.execute(editor::comboSideForTest));
        // 深い AUTO の子 G を選択 → 枝の実効 side (LEFT) を表示 (前回値のまま残らない)。
        GuiActionRunner.execute(() -> editor.selectForTest(g));
        assertSame("深い子 G は枝の実効 side LEFT を表示", Side.LEFT,
                GuiActionRunner.execute(editor::comboSideForTest));
    }

    @Test
    public void sideCombo_withNoSelection_snapsBackToAutoAndDoesNotMutate() {
        // 選択が無い状態 (ロード直後は setModel が select(null)) でコンボを変えても、
        // setSideOfSelected は no-op なのでコンボは Auto へ戻り、モデルも変わらないこと。
        MindmapSketchEditor editor = GuiActionRunner.execute(MindmapSketchEditor::new);
        int[] edits = {0};
        GuiActionRunner.execute(() -> editor.setOnEdited(() -> edits[0]++));
        GuiActionRunner.execute(() -> editor.load("@startmindmap\n* Root\n@endmindmap\n"));
        // ルートは非選択のまま (setModel が select(null))。念のため明示的に選択解除する。
        GuiActionRunner.execute(() -> editor.selectForTest(null));
        edits[0] = 0;
        GuiActionRunner.execute(() -> editor.userPickSideForTest(Side.LEFT));
        assertSame("選択が無ければコンボは Auto へ戻る", Side.AUTO,
                GuiActionRunner.execute(editor::comboSideForTest));
        org.junit.Assert.assertEquals("選択なしの側変更はモデルを変えない", 0, edits[0]);
    }

    @Test
    public void sideCombo_userPickOnDeepNode_reflectsBranchSide() {
        // 深い AUTO の子を選択してコンボで Right を選ぶと、枝起点が Right になり、コンボ表示も
        // 枝の実効 side (Right) を映すこと (setSideOfSelected の枝委譲 + 再同期の統合確認)。
        MindmapSketchEditor editor = GuiActionRunner.execute(MindmapSketchEditor::new);
        GuiActionRunner.execute(() -> editor.load(String.join("\n",
                "@startmindmap", "* Root", "-- L", "--- G", "@endmindmap", "")));
        MindmapNode g = editor.rootForTest().getChildren().get(0).getChildren().get(0);
        GuiActionRunner.execute(() -> editor.selectForTest(g));
        GuiActionRunner.execute(() -> editor.userPickSideForTest(Side.RIGHT));
        assertSame("枝起点が Right になりコンボも Right を映す", Side.RIGHT,
                GuiActionRunner.execute(editor::comboSideForTest));
        assertSame("枝の起点 L が Right へ変わる", Side.RIGHT,
                editor.rootForTest().getChildren().get(0).getOwnSide());
    }

    @Test
    public void sideCombo_sync_doesNotMutateModel() {
        // 選択連動でコンボを書き換えても (syncingSide ガード) 側変更・テキスト再生成が起きないこと。
        MindmapSketchEditor editor = GuiActionRunner.execute(MindmapSketchEditor::new);
        int[] edits = {0};
        GuiActionRunner.execute(() -> editor.setOnEdited(() -> edits[0]++));
        GuiActionRunner.execute(() -> editor.load(String.join("\n",
                "@startmindmap", "* Root", "++ R", "@endmindmap", "")));
        MindmapNode root = editor.rootForTest();
        MindmapNode r = root.getChildren().get(0);
        edits[0] = 0;
        // RIGHT 枝を選択 → コンボは RIGHT へ同期するが setOnEdited は発火しない。
        GuiActionRunner.execute(() -> editor.selectForTest(r));
        assertSame(Side.RIGHT, GuiActionRunner.execute(editor::comboSideForTest));
        org.junit.Assert.assertEquals("選択連動の同期でモデル編集は起きない", 0, edits[0]);
    }
}
