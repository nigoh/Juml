// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Design サブタブを往復しただけで Undo 履歴が消える不具合の回帰テスト。
 *
 * <p>キャンバス編集は {@code onPumlChange} でテキスト欄へ反映され、Design サブタブを
 * 選び直すたびにその<b>同じテキスト</b>が {@link SketchPane#loadFrom(String)} へ戻ってくる。
 * 以前は無条件に読み直して履歴をリセットしていたため、「図形を動かす → Source を覗く →
 * Design に戻る → Ctrl+Z」が効かなかった。テキストを実際に書き換えた場合は従来どおり
 * 別セッション扱いでリセットする。</p>
 */
public class SketchPaneReloadHistoryTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void reloadingTheSameTextKeepsUndoHistory() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        GuiActionRunner.execute(() -> pane.addClassForTest(SketchClass.Kind.CLASS));
        assertTrue("編集で Undo 履歴が積まれる", GuiActionRunner.execute(pane::canUndoForTest));

        int classes = GuiActionRunner.execute(() -> pane.classesForTest().size());
        // テキスト欄が保持しているのは onPumlChange で流れた currentPuml。
        String synced = GuiActionRunner.execute(pane::currentPuml);
        GuiActionRunner.execute(() -> pane.loadFrom(synced));

        assertTrue("サブタブを往復しても Undo 履歴が残ること",
                GuiActionRunner.execute(pane::canUndoForTest));
        assertEquals("モデルも保たれること", classes,
                (int) GuiActionRunner.execute(() -> pane.classesForTest().size()));
        // 履歴が生きているので実際に取り消せる。
        GuiActionRunner.execute(pane::undo);
        assertEquals(classes - 1, (int) GuiActionRunner.execute(
                () -> pane.classesForTest().size()));
    }

    @Test
    public void reloadingChangedTextStillResetsHistory() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        GuiActionRunner.execute(() -> pane.addClassForTest(SketchClass.Kind.CLASS));
        assertTrue(GuiActionRunner.execute(pane::canUndoForTest));

        // テキスト欄側で書き換えられた内容が来たら別セッション扱い。
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.SEQUENCE.body()));
        assertFalse("内容が変わった読み込みは従来どおり履歴をリセットする",
                GuiActionRunner.execute(pane::canUndoForTest));
    }

    @Test
    public void firstLoadAlwaysApplies() {
        // 初回は baseline の初期値と一致しても必ず読み込む (図種判定・カード切替のため)。
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom("@startuml\n@enduml\n"));
        assertEquals(SketchDiagramType.CLASS, GuiActionRunner.execute(pane::activeTypeForTest));
    }
}
