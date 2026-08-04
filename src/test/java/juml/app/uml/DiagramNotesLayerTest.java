// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JPanel;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramNotesLayer} の操作性拡張 (Undo/Redo・複数選択・複製・重なり順・ロック) の検証。
 *
 * <p>マウス選択は合成 {@link MouseEvent} を {@code zoom=1.0} で流し込み、図座標 =
 * パネル座標として当たり判定させる。</p>
 */
public class DiagramNotesLayerTest {

    private static MouseEvent press(JPanel owner, int x, int y, int mods) {
        return new MouseEvent(owner, MouseEvent.MOUSE_PRESSED, 1L, mods, x, y, 1, false,
                MouseEvent.BUTTON1);
    }

    @Test
    public void addThenUndoRedo() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);
        layer.addNoteAt(new Point(400, 10), 1.0);
        assertEquals(2, layer.getNotes().size());

        assertTrue(layer.undo());
        assertEquals(1, layer.getNotes().size());
        assertTrue(layer.undo());
        assertEquals(0, layer.getNotes().size());
        assertFalse("これ以上戻れない", layer.undo());

        assertTrue(layer.redo());
        assertEquals(1, layer.getNotes().size());
        assertTrue(layer.redo());
        assertEquals(2, layer.getNotes().size());
        assertFalse("これ以上やり直せない", layer.redo());
    }

    @Test
    public void duplicateAddsOffsetCopyAndIsUndoable() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0); // 追加直後は選択状態
        DiagramNote original = layer.getNotes().get(0);

        layer.duplicateSelected();
        List<DiagramNote> after = layer.getNotes();
        assertEquals(2, after.size());
        DiagramNote dup = after.get(1);
        assertNotEquals(original.getId(), dup.getId());
        assertEquals(original.getX() + 16, dup.getX(), 0.001);
        assertEquals(original.getY() + 16, dup.getY(), 0.001);

        layer.undo();
        assertEquals(1, layer.getNotes().size());
    }

    @Test
    public void shiftClickMultiSelectThenDeleteRemovesBoth() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);   // A: (10,10) 280x160
        layer.addNoteAt(new Point(400, 10), 1.0);  // B: 追加直後で選択中
        // Shift+クリックで A を選択集合へ追加 (B も選択のまま)
        layer.pressed(press(owner, 20, 20, InputEvent.SHIFT_DOWN_MASK), 1.0);

        assertTrue(layer.deleteSelected());
        assertEquals(0, layer.getNotes().size());

        layer.undo();
        assertEquals(2, layer.getNotes().size());
    }

    @Test
    public void bringToFrontReordersSelected() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);   // A (奥)
        layer.addNoteAt(new Point(400, 10), 1.0);  // B (手前)
        String idA = layer.getNotes().get(0).getId();

        layer.pressed(press(owner, 20, 20, 0), 1.0); // A を単独選択
        layer.released();
        layer.bringToFront();

        List<DiagramNote> notes = layer.getNotes();
        assertEquals("A が最前面 (末尾) へ", idA, notes.get(notes.size() - 1).getId());
    }

    @Test
    public void fitHeightIsUndoable() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);
        DiagramNote n = layer.getNotes().get(0);
        n.setText("# Title\n\nline1\nline2\nline3\nline4\nline5\nline6\nline7\nline8");
        n.setHeight(44);
        double before = layer.getNotes().get(0).getHeight();

        layer.fitHeightSelected();
        assertTrue("本文に合わせて高さは縮まない", layer.getNotes().get(0).getHeight() >= before);

        layer.undo();
        assertEquals(before, layer.getNotes().get(0).getHeight(), 0.001);
    }

    @Test
    public void paintWithElementLeaderDoesNotThrow() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.setElementResolver(fqn -> new double[] {200, 200, 120, 60});
        layer.addElementNote("com.x.Foo", new double[] {200, 200, 120, 60});

        BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            layer.paint(g, 1.0); // リーダー線 + 付箋描画が例外なく通ること
        } finally {
            g.dispose();
        }
        assertEquals(1, layer.getNotes().size());
    }

    @Test
    public void noteRectAndSelectOnlyTargetTheRightNote() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(30, 40), 1.0);
        String id = layer.getNotes().get(0).getId();

        double[] r = layer.noteRect(id);
        assertNotNull(r);
        assertEquals(30, r[0], 0.001);
        assertEquals(40, r[1], 0.001);

        layer.addNoteAt(new Point(400, 10), 1.0); // 2 つ目を追加 (これが選択される)
        layer.selectOnly(id); // 1 つ目だけ選択へ
        layer.deleteSelected();

        List<DiagramNote> rest = layer.getNotes();
        assertEquals(1, rest.size());
        assertNotEquals(id, rest.get(0).getId());
    }

    @Test
    public void connectorCreatedViaConnectModeAndPrunedOnDelete() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);   // A
        layer.addNoteAt(new Point(400, 10), 1.0);  // B
        String idA = layer.getNotes().get(0).getId();

        // A を始点にコネクタモードへ → B をクリックで 1 本引く
        layer.startConnectorFrom(idA);
        layer.pressed(press(owner, 420, 30, 0), 1.0); // B 上をクリック
        assertEquals(1, layer.getConnectors().size());

        // 重複は無視 / Undo で消える
        layer.undo();
        assertEquals(0, layer.getConnectors().size());
        layer.redo();
        assertEquals(1, layer.getConnectors().size());

        // 端点の付箋を消すとコネクタも一緒に消える
        layer.selectOnly(idA);
        layer.deleteSelected();
        assertEquals(0, layer.getConnectors().size());
    }

    /**
     * 回帰: コネクタ作成モードの途中で始点の付箋を消しても、宙ぶらりんのコネクタが
     * できないこと。以前は {@code deleteSelected} が {@code connectFromId} を残したため、
     * 次にクリックした付箋との間に<b>存在しない付箋を始点とするコネクタ</b>ができ、
     * そのまま保存されていた (読み込み時の prune で最終的には消えるが、そのセッション中は
     * 残り続ける)。8 つのスケッチキャンバスでは同種の穴を既に塞いであり、付箋レイヤだけが
     * 取り残されていた。
     */
    /**
     * 回帰: 付箋一覧の総入れ替え (図種の切替・再読込) でもコネクタ作成モードを畳むこと。
     *
     * <p>{@code createConnector} の端点チェックは「宙ぶらりんのコネクタができる」ことは
     * 防ぐが、モード自体は残っていた。すると盤面全体が十字カーソルのままになり、
     * 次の左クリックが {@code connectFromId != null} の分岐に食われて、付箋の選択も
     * ドラッグもできない (利用者は 2 回クリックする必要がある)。</p>
     */
    @Test
    public void reloadingNotesCancelsConnectorCreation() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);
        String idA = layer.getNotes().get(0).getId();
        layer.startConnectorFrom(idA);

        // 図種切替相当: 中身を別の付箋一覧へ総入れ替えする。
        DiagramNote other = new DiagramNote();
        other.setX(400);
        other.setY(10);
        layer.setNotes(java.util.List.of(other));

        // モードが畳まれていること自体を見る。カーソルが十字のままなら作成モードが生きており、
        // 次のクリックは pressed() の connectFromId 分岐に食われる。
        assertNotEquals("盤面が十字カーソルのまま残らないこと",
                java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR),
                layer.cursorFor(new Point(420, 30), 1.0));
        layer.pressed(press(owner, 420, 30, 0), 1.0);
        assertEquals("宙ぶらりんのコネクタもできないこと", 0, layer.getConnectors().size());
    }

    @Test
    public void deletingTheSourceNoteCancelsConnectorCreation() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);   // A
        layer.addNoteAt(new Point(400, 10), 1.0);  // B
        String idA = layer.getNotes().get(0).getId();

        // A を始点にコネクタモードへ入り、確定する前に A を消す。
        layer.startConnectorFrom(idA);
        layer.selectOnly(idA);
        assertTrue(layer.deleteSelected());
        assertEquals(1, layer.getNotes().size());

        // 次に B をクリックしてもコネクタはできない (始点がもう存在しない)。
        layer.pressed(press(owner, 420, 30, 0), 1.0);
        assertEquals("消えた付箋を始点とするコネクタを作らないこと",
                0, layer.getConnectors().size());
    }

    /**
     * ELEMENT アンカー付箋のエクスポート座標は、対象要素の位置を解決した絶対座標に
     * なる。以前は SVG 出力が相対オフセットを絶対座標として書き、要素から離れた
     * 原点付近へ描画されていた。
     */
    @Test
    public void notesForExportResolvesElementAnchorToAbsolute() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        // 対象要素は (800, 300) に幅 120。付箋は要素右隣 (offX = 120+16=136, offY=0)。
        layer.setElementResolver(fqn -> new double[] {800, 300, 120, 60});
        layer.addElementNote("com.x.Foo", new double[] {800, 300, 120, 60});

        List<DiagramNote> resolved = layer.notesForExportResolved();
        assertEquals(1, resolved.size());
        DiagramNote n = resolved.get(0);
        // 絶対座標 = 要素左上 + オフセット。原点付近ではなく要素の隣にあること。
        assertEquals("X は要素の右隣の絶対座標", 800 + 136, n.getX(), 0.001);
        assertEquals("Y は要素上端の絶対座標", 300 + 0, n.getY(), 0.001);
        assertEquals("エクスポート用は FREE 化される",
                DiagramNote.Anchor.FREE, n.getAnchor());
    }

    /** FREE 付箋のエクスポート座標はそのまま (アンカー解決の影響を受けない)。 */
    @Test
    public void notesForExportKeepsFreeCoordinates() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(45, 67), 1.0);
        DiagramNote n = layer.notesForExportResolved().get(0);
        assertEquals(45, n.getX(), 0.001);
        assertEquals(67, n.getY(), 0.001);
    }

    @Test
    public void lockedNoteIsNotMovedByArrowKeys() {
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.addNoteAt(new Point(10, 10), 1.0);
        layer.toggleLockSelected();

        DiagramNote before = layer.getNotes().get(0);
        double x0 = before.getX();
        double y0 = before.getY();

        layer.moveSelected(5, 5);

        DiagramNote after = layer.getNotes().get(0);
        assertTrue(after.isLocked());
        assertEquals(x0, after.getX(), 0.001);
        assertEquals(y0, after.getY(), 0.001);
    }
}
