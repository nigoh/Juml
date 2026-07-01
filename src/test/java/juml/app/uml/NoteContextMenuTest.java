// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link NoteContextMenu#buildMenu(DiagramNotesLayer, DiagramNote, MouseEvent, double)}
 * のメニュー構造・状態・アクション結線を検証する。
 *
 * <p>{@link JMenuItem} の構築に UIManager 参照が含まれる可能性があるため headless guard を
 * 入れている。アクション結線の確認は {@link DiagramNotesLayer} の副作用 (付箋数の変化等) で
 * 行い、モーダルダイアログを伴う {@code editNote} / {@code pickCustomColor} は発火しない。</p>
 */
public class NoteContextMenuTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では JMenuItem / JPopupMenu 構築が失敗する場合があるためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** MouseEvent のダミー (ポップアップメニュー構築に component が必要)。 */
    private static MouseEvent dummyEvent(JPanel owner) {
        return new MouseEvent(owner, MouseEvent.MOUSE_PRESSED, 0L, 0, 0, 0, 1, true);
    }

    /** JPopupMenu の JMenuItem を (Separator・JMenu を除いて) フラットに収集する。 */
    private static List<JMenuItem> flatMenuItems(JPopupMenu menu) {
        List<JMenuItem> result = new ArrayList<>();
        for (int i = 0; i < menu.getComponentCount(); i++) {
            java.awt.Component c = menu.getComponent(i);
            if (c instanceof JMenuItem) {
                result.add((JMenuItem) c);
            }
        }
        return result;
    }

    /** テスト用の DiagramNotesLayer (owner は JPanel)。付箋を 1 件追加済みで返す。 */
    private static DiagramNotesLayer singleNoteLayer() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);
        return layer;
    }

    /** テスト用の DiagramNotesLayer (owner は JPanel)。付箋を 2 件追加済みで返す。 */
    private static DiagramNotesLayer twoNoteLayer() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);   // A (追加直後は選択状態)
        layer.addNoteAt(new Point(300, 10), 1.0);  // B
        // 両方を選択状態にする (Shift+click で A を追加)
        int mods = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
        MouseEvent shiftPress = new MouseEvent(owner, MouseEvent.MOUSE_PRESSED,
                1L, mods, 20, 20, 1, false, MouseEvent.BUTTON1);
        layer.pressed(shiftPress, 1.0);
        return layer;
    }

    // -------------------------------------------------------------------------
    // テスト: 想定される項目数が生成される (単一選択・コネクタなし)
    // -------------------------------------------------------------------------

    @Test
    public void buildMenu_singleSelection_hasExpectedItemCount() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = singleNoteLayer();
        DiagramNote note = layer.getNotes().get(0);
        MouseEvent e = dummyEvent(owner);

        JPopupMenu menu = GuiActionRunner.execute(
                () -> NoteContextMenu.buildMenu(layer, note, e, 1.0));

        List<JMenuItem> items = flatMenuItems(menu);
        // edit / color / duplicate / fitHeight / lock / --- /
        // toFront / toBack / --- / connect / --- / addHere / --- / delete = 10 items
        assertTrue("メニュー項目が 10 件以上あること: actual=" + items.size(), items.size() >= 10);
    }

    // -------------------------------------------------------------------------
    // テスト: 複数選択時は edit 項目が無効化される
    // -------------------------------------------------------------------------

    @Test
    public void buildMenu_multipleSelection_editItemIsDisabled() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = twoNoteLayer();
        DiagramNote note = layer.getNotes().get(0);
        MouseEvent e = dummyEvent(owner);

        JPopupMenu menu = GuiActionRunner.execute(
                () -> NoteContextMenu.buildMenu(layer, note, e, 1.0));

        List<JMenuItem> items = flatMenuItems(menu);
        assertTrue("メニュー項目が 1 件以上あること", !items.isEmpty());
        JMenuItem editItem = items.get(0);
        assertFalse("複数選択時は edit 項目が無効化されること", editItem.isEnabled());
    }

    // -------------------------------------------------------------------------
    // テスト: 単一選択時は edit 項目が有効
    // -------------------------------------------------------------------------

    @Test
    public void buildMenu_singleSelection_editItemIsEnabled() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = singleNoteLayer();
        DiagramNote note = layer.getNotes().get(0);
        MouseEvent e = dummyEvent(owner);

        JPopupMenu menu = GuiActionRunner.execute(
                () -> NoteContextMenu.buildMenu(layer, note, e, 1.0));

        List<JMenuItem> items = flatMenuItems(menu);
        assertTrue("メニュー項目が 1 件以上あること", !items.isEmpty());
        assertTrue("単一選択時は edit 項目が有効であること", items.get(0).isEnabled());
    }

    // -------------------------------------------------------------------------
    // テスト: コネクタがない場合は unlink 項目が出ない
    // -------------------------------------------------------------------------

    @Test
    public void buildMenu_noConnectors_doesNotContainUnlinkItem() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = singleNoteLayer();
        DiagramNote note = layer.getNotes().get(0);
        MouseEvent e = dummyEvent(owner);

        JPopupMenu menu = GuiActionRunner.execute(
                () -> NoteContextMenu.buildMenu(layer, note, e, 1.0));

        assertFalse("コネクタなしのとき unlink 項目が存在しないこと",
                layer.hasConnectorsTouchingSelection());
        // unlink が出ないので項目数は connect なし版と同じになる
        List<JMenuItem> items = flatMenuItems(menu);
        String unlinkLabel = Messages.get("note.menu.removeConnectors");
        boolean foundUnlink = false;
        for (JMenuItem mi : items) {
            if (unlinkLabel.equals(mi.getText())) {
                foundUnlink = true;
                break;
            }
        }
        assertFalse("コネクタなし時に unlink 項目が出ないこと", foundUnlink);
    }

    // -------------------------------------------------------------------------
    // テスト: hasConnectorsTouchingSelection()==true のときのみ unlink 項目が出る
    // -------------------------------------------------------------------------

    @Test
    public void buildMenu_withConnectors_containsUnlinkItem() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);   // A
        layer.addNoteAt(new Point(300, 10), 1.0);  // B
        String idA = layer.getNotes().get(0).getId();

        // A を始点にコネクタを引く (A は選択状態に戻す)
        layer.startConnectorFrom(idA);
        MouseEvent clickOnB = new MouseEvent(owner, MouseEvent.MOUSE_PRESSED,
                1L, 0, 320, 30, 1, false, MouseEvent.BUTTON1);
        layer.pressed(clickOnB, 1.0); // B 上をクリック → コネクタ生成

        // A を単独選択してコネクタが触れるようにする
        layer.selectOnly(idA);
        assertTrue("コネクタが A に接続していること", layer.hasConnectorsTouchingSelection());

        DiagramNote noteA = layer.getNotes().get(0);
        MouseEvent e = dummyEvent(owner);

        JPopupMenu menu = GuiActionRunner.execute(
                () -> NoteContextMenu.buildMenu(layer, noteA, e, 1.0));

        List<JMenuItem> items = flatMenuItems(menu);
        String unlinkLabel = Messages.get("note.menu.removeConnectors");
        boolean hasUnlink = false;
        for (JMenuItem mi : items) {
            if (unlinkLabel.equals(mi.getText())) {
                hasUnlink = true;
                break;
            }
        }
        assertTrue("コネクタがある場合は unlink 項目が出ること: label=" + unlinkLabel, hasUnlink);
    }

    // -------------------------------------------------------------------------
    // テスト: duplicate アクションが duplicateSelected を呼ぶ
    // -------------------------------------------------------------------------

    @Test
    public void buildMenu_duplicateAction_callsDuplicateSelected() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = singleNoteLayer();
        DiagramNote note = layer.getNotes().get(0);
        MouseEvent e = dummyEvent(owner);

        JPopupMenu menu = GuiActionRunner.execute(
                () -> NoteContextMenu.buildMenu(layer, note, e, 1.0));

        List<JMenuItem> items = flatMenuItems(menu);
        // Messages.get("note.menu.duplicate") のラベルを持つ項目を探す
        String dupLabel = Messages.get("note.menu.duplicate");
        JMenuItem dupItem = null;
        for (JMenuItem mi : items) {
            if (dupLabel.equals(mi.getText())) {
                dupItem = mi;
                break;
            }
        }
        assertTrue("duplicate 項目が見つかること: label=" + dupLabel, dupItem != null);

        int beforeCount = layer.getNotes().size();
        final JMenuItem finalDupItem = dupItem;
        GuiActionRunner.execute(() -> finalDupItem.doClick());

        assertEquals("duplicate アクション発火後に付箋が 1 件増えること",
                beforeCount + 1, layer.getNotes().size());
    }

    // -------------------------------------------------------------------------
    // テスト: delete アクションが deleteSelected を呼ぶ
    // -------------------------------------------------------------------------

    @Test
    public void buildMenu_deleteAction_callsDeleteSelected() {
        JPanel owner = GuiActionRunner.execute(() -> new JPanel());
        DiagramNotesLayer layer = singleNoteLayer();
        DiagramNote note = layer.getNotes().get(0);
        MouseEvent e = dummyEvent(owner);

        JPopupMenu menu = GuiActionRunner.execute(
                () -> NoteContextMenu.buildMenu(layer, note, e, 1.0));

        List<JMenuItem> items = flatMenuItems(menu);
        // delete 項目は最後
        JMenuItem delItem = items.get(items.size() - 1);
        int beforeCount = layer.getNotes().size();

        GuiActionRunner.execute(() -> { delItem.doClick(); return null; });

        assertTrue("delete アクション発火後に付箋が減ること",
                layer.getNotes().size() < beforeCount);
    }
}
