// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 全ビジュアル設計器のツールバーが「編集ロック中は無効・解除で再有効」へ追従する
 * ことを検証する。
 *
 * <p>未対応構文 (ここでは一般コメント行) を含む図はモデル編集がロックされる
 * ({@code isFullySupported()==false})。その間は追加ボタンやモード切替を押しても
 * キャンバス側の editable ガードで無反応になるため、ツールバーを無効表示にして
 * 「押せるのに効かない」誤解を防ぐ (各 {@code *SketchEditor#updateToolbarEnabled} 参照)。
 * ここでは 6 種すべてのエディタで (1) 対応図では有効、(2) ロック図では無効、
 * (3) 対応図へ戻すと再有効、というトグルの往復を確認する。純粋な Swing 生成のみで
 * 実ディスプレイ (Robot) は不要だが、ヘッドレスでは Swing 生成が失敗するため skip する。</p>
 */
public class SketchEditorToolbarLockTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    /**
     * テンプレート本文の {@code @enduml} 直前へ一般コメント行を差し込み、コーデックが
     * 往復できない未対応行として編集ロックさせる (全コーデックが {@code '} 始まりの
     * 一般コメントを未対応として扱う)。
     */
    private static String locked(String body) {
        return body.replace("@enduml", "' a user comment the codec cannot round-trip\n@enduml");
    }

    private static void assertToolbarTracksLock(Supplier<SketchEditor> factory, String template) {
        SketchEditor editor = GuiActionRunner.execute(factory::get);
        // 1) 完全対応の図: 編集可能 → ツールバーは有効。
        GuiActionRunner.execute(() -> editor.load(template));
        assertTrue("前提: テンプレートは編集可能なはず",
                GuiActionRunner.execute(editor::isEditable));
        assertAllEnabled(editor, true);
        // 2) 未対応コメントを含む図: ロック → ツールバーは無効。
        GuiActionRunner.execute(() -> editor.load(locked(template)));
        assertFalse("前提: コメントを含む図は編集ロックされるはず",
                GuiActionRunner.execute(editor::isEditable));
        assertAllEnabled(editor, false);
        // 3) 再び完全対応の図を読み込むと有効表示へ戻ること (トグルの往復)。
        GuiActionRunner.execute(() -> editor.load(template));
        assertAllEnabled(editor, true);
    }

    private static void assertAllEnabled(SketchEditor editor, boolean expected) {
        Component[] tools =
                GuiActionRunner.execute(() -> editor.toolbarComponent().getComponents());
        assertTrue("ツールバーには少なくとも 1 つの操作要素があるはず", tools.length > 0);
        for (Component c : tools) {
            assertEquals("ツールバー項目 " + c.getClass().getSimpleName() + " の有効状態",
                    expected, c.isEnabled());
        }
    }

    @Test
    public void classEditor_toolbarTracksLock() {
        assertToolbarTracksLock(ClassSketchEditor::new, PumlTemplate.CLASS.body());
    }

    @Test
    public void sequenceEditor_toolbarTracksLock() {
        assertToolbarTracksLock(SeqSketchEditor::new, PumlTemplate.SEQUENCE.body());
    }

    @Test
    public void activityEditor_toolbarTracksLock() {
        assertToolbarTracksLock(ActivitySketchEditor::new, PumlTemplate.ACTIVITY.body());
    }

    @Test
    public void stateEditor_toolbarTracksLock() {
        assertToolbarTracksLock(StateSketchEditor::new, PumlTemplate.STATE.body());
    }

    @Test
    public void useCaseEditor_toolbarTracksLock() {
        assertToolbarTracksLock(UseCaseSketchEditor::new, PumlTemplate.USECASE.body());
    }

    @Test
    public void componentEditor_toolbarTracksLock() {
        assertToolbarTracksLock(ComponentSketchEditor::new, PumlTemplate.COMPONENT.body());
    }
}
