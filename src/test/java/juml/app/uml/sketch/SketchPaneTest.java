// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchPane} のテキスト同期 (loadFrom / onPumlChange) を検証する GUI テスト。
 * 変換ロジック自体の網羅は {@link SketchPumlCodecTest} が担う。
 */
public class SketchPaneTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void loadFrom_classTemplate_enablesEditing() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        assertTrue("クラス図テンプレートは GUI 編集可能なはず",
                GuiActionRunner.execute(pane::isEditable));
        assertEquals(3, (int) GuiActionRunner.execute(
                () -> pane.classesForTest().size()));
    }

    @Test
    public void loadFrom_sequenceTemplate_disablesEditing() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.SEQUENCE.body()));
        assertFalse("未対応構文では GUI 編集が無効になるはず",
                GuiActionRunner.execute(pane::isEditable));
    }

    @Test
    public void noOpEdit_doesNotCreateUndoHistoryOrClearRedo() {
        // スナップで元位置へ戻る微小ドラッグのような「実質変化ゼロの編集」通知が来ても、
        // Undo 履歴を積まず Redo を破棄しないこと (無変更の空 Undo / 偽 dirty の防止)。
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        // 実編集 → Undo を 1 回押して Redo を有効化しておく。
        GuiActionRunner.execute(() -> pane.addClassForTest(SketchClass.Kind.CLASS));
        GuiActionRunner.execute(pane::undo);
        assertTrue("Undo 後は Redo 可能なはず", GuiActionRunner.execute(pane::canRedoForTest));
        boolean undoBefore = GuiActionRunner.execute(pane::canUndoForTest);
        // 変化ゼロの編集通知。
        GuiActionRunner.execute(pane::fireNoOpEditForTest);
        assertEquals("無変更の編集で Undo 履歴は変わらないはず",
                undoBefore, (boolean) GuiActionRunner.execute(pane::canUndoForTest));
        assertTrue("無変更の編集で Redo が破棄されないはず",
                GuiActionRunner.execute(pane::canRedoForTest));
    }

    @Test
    public void undoRedo_reversesAndReappliesEdits() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        int base = GuiActionRunner.execute(() -> pane.classesForTest().size());
        GuiActionRunner.execute(() -> pane.addClassForTest(SketchClass.Kind.CLASS));
        assertEquals("クラス追加で 1 増える", base + 1,
                (int) GuiActionRunner.execute(() -> pane.classesForTest().size()));
        GuiActionRunner.execute(pane::undo);
        assertEquals("Undo で追加が取り消される", base,
                (int) GuiActionRunner.execute(() -> pane.classesForTest().size()));
        GuiActionRunner.execute(pane::redo);
        assertEquals("Redo で追加が復活する", base + 1,
                (int) GuiActionRunner.execute(() -> pane.classesForTest().size()));
    }

    @Test
    public void undoRedo_keepsTextSyncConsistent() {
        // Undo/Redo がモデルだけでなく onPumlChange のテキスト同期まで一貫して巻き戻す
        // ことを検証する (モデルとテキストが乖離して「ぐちゃぐちゃ」にならないこと)。
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        AtomicReference<String> lastPuml = new AtomicReference<>("");
        GuiActionRunner.execute(() -> {
            pane.setOnPumlChange(lastPuml::set);
            pane.loadFrom(PumlTemplate.CLASS.body());
        });
        GuiActionRunner.execute(() -> pane.addClassForTest(SketchClass.Kind.CLASS));
        assertTrue("追加直後のテキストに新クラスが載る: " + lastPuml.get(),
                lastPuml.get().contains("NewClass"));

        GuiActionRunner.execute(pane::undo);
        assertFalse("Undo 後のテキストからは新クラスが消える: " + lastPuml.get(),
                lastPuml.get().contains("NewClass"));
        // テキストとモデルが一致していること (乖離していないこと)。
        assertEquals(GuiActionRunner.execute(pane::currentPuml), lastPuml.get());

        GuiActionRunner.execute(pane::redo);
        assertTrue("Redo 後のテキストに新クラスが戻る: " + lastPuml.get(),
                lastPuml.get().contains("NewClass"));
        assertEquals(GuiActionRunner.execute(pane::currentPuml), lastPuml.get());
    }

    @Test
    public void undo_withoutHistory_isNoOp() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        int base = GuiActionRunner.execute(() -> pane.classesForTest().size());
        GuiActionRunner.execute(pane::undo); // 履歴が無いので何も起きない
        assertEquals(base, (int) GuiActionRunner.execute(() -> pane.classesForTest().size()));
    }

    @Test
    public void loadFrom_clearsUndoHistory() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        GuiActionRunner.execute(() -> pane.addClassForTest(SketchClass.Kind.CLASS));
        // 別内容を読み込むと履歴はリセットされ、Undo しても前内容へは戻らない。
        GuiActionRunner.execute(() -> pane.loadFrom("@startuml\nclass Solo\n@enduml\n"));
        int after = GuiActionRunner.execute(() -> pane.classesForTest().size());
        GuiActionRunner.execute(pane::undo);
        assertEquals("loadFrom 後の Undo は前内容へ戻さない", after,
                (int) GuiActionRunner.execute(() -> pane.classesForTest().size()));
    }

    @Test
    public void modelEdit_regeneratesPumlWithPositions() {
        SketchPane pane = GuiActionRunner.execute(SketchPane::new);
        AtomicReference<String> lastPuml = new AtomicReference<>();
        GuiActionRunner.execute(() -> {
            pane.setOnPumlChange(lastPuml::set);
            pane.loadFrom(PumlTemplate.CLASS.body());
            // キャンバス操作相当: クラスを移動して再生成テキストを取得する。
            pane.classesForTest().get(0).moveTo(123, 456);
        });
        String puml = GuiActionRunner.execute(pane::currentPuml);
        assertTrue("移動後の座標が '@pos コメントとして保存されるはず",
                puml.contains("'@pos Example 123 456"));
        assertTrue(puml.contains("Example <|-- Child"));
    }
}
