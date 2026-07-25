// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.MindmapNode.Side;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link MindmapSketchCanvas} の編集操作・reparent ドラッグ・ロック表示を検証する
 * (純 Graphics2D 描画とモデル座標シームのため headless 可、Robot 不要)。
 */
public class MindmapSketchCanvasTest {

    private final AtomicInteger edits = new AtomicInteger();
    private final AtomicReference<Point> addRoot = new AtomicReference<>();

    private MindmapSketchCanvas.Listener listener() {
        return new MindmapSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editRequested(MindmapNode node) {
            }

            @Override public void addRootRequested(Point at) {
                addRoot.set(at);
            }
        };
    }

    private MindmapSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new MindmapSketchCanvas(listener()));
    }

    /** ルート + 兄弟 2 つ (a, b) を持つモデルを組んで編集可能で載せる。 */
    private static MindmapSketchModel rootWithTwoChildren(MindmapNode[] out) {
        MindmapSketchModel m = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        m.setRoot(root);
        MindmapNode a = new MindmapNode("A");
        MindmapNode b = new MindmapNode("B");
        m.addChild(root, a);
        m.addChild(root, b);
        out[0] = root;
        out[1] = a;
        out[2] = b;
        return m;
    }

    private static Point center(MindmapSketchCanvas canvas, MindmapNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.layoutForTest().bounds.get(n));
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private static void paint(MindmapSketchCanvas canvas) {
        BufferedImage img = new BufferedImage(700, 600, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(700, 600);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
    }

    @Test
    public void addChild_onEmptyCanvas_createsRoot() {
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(
                () -> canvas.setModel(new MindmapSketchModel(), true, List.of()));
        assertNull("最初は空図", GuiActionRunner.execute(() -> canvas.model().getRoot()));
        GuiActionRunner.execute(() -> canvas.addChild(null));
        assertTrue("空図に addChild(null) でルートが作られる",
                GuiActionRunner.execute(() -> canvas.model().getRoot()) != null);
    }

    @Test
    public void addChildAndSibling_growTree() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(rootWithTwoChildren(n), true, List.of()));
        GuiActionRunner.execute(() -> canvas.addChild(n[1]));
        assertEquals("A に子が 1 つ増える", 1, n[1].getChildren().size());
        GuiActionRunner.execute(() -> {
            canvas.setSelectedForTest(n[2]);
            canvas.addSibling(n[2]);
        });
        assertEquals("ルート直下が 3 (A/B/新兄弟) になる", 3, n[0].getChildren().size());
    }

    @Test
    public void addSibling_onRoot_isNoOp() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(rootWithTwoChildren(n), true, List.of()));
        GuiActionRunner.execute(() -> canvas.addSibling(n[0]));
        assertEquals("ルートに兄弟は作れない", 2, n[0].getChildren().size());
    }

    @Test
    public void reparentDrag_movesNodeUnderDropTarget() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(rootWithTwoChildren(n), true, List.of()));
        edits.set(0);
        Point onB = center(canvas, n[2]);
        Point onA = center(canvas, n[1]);
        boolean ok = GuiActionRunner.execute(() -> {
            canvas.pressForTest(onB);
            assertSame("press で B を掴む", n[2], canvas.draggingForTest());
            canvas.dragForTest(onA);
            return canvas.releaseForTest(onA);
        });
        assertTrue("B は A の子へ付け替わる", ok);
        assertSame("B の親が A になる", n[1], n[2].getParent());
        assertFalse("B はルート直下から外れる", n[0].getChildren().contains(n[2]));
        assertEquals("付け替えで modelEdited が 1 回発火", 1, edits.get());
        assertNull("ドラッグ状態は解除", GuiActionRunner.execute(canvas::draggingForTest));
    }

    @Test
    public void reparentDrag_ontoSelf_isRejected() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(rootWithTwoChildren(n), true, List.of()));
        edits.set(0);
        Rectangle rb = GuiActionRunner.execute(() -> canvas.layoutForTest().bounds.get(n[2]));
        Point press = new Point(rb.x + 4, rb.y + rb.height / 2);
        // 同じ B の中で press から十分離れた点へ離す (クリック閾値超え・だが target は自分)。
        Point release = new Point(rb.x + rb.width - 4, rb.y + rb.height / 2);
        boolean ok = GuiActionRunner.execute(() -> {
            canvas.pressForTest(press);
            return canvas.releaseForTest(release);
        });
        assertFalse("自分自身へのドロップは拒否", ok);
        assertSame("B の親は変わらない", n[0], n[2].getParent());
        assertEquals("拒否では modelEdited は発火しない", 0, edits.get());
    }

    @Test
    public void reparentDrag_ontoDescendant_isRejected() {
        // Root → A → B。A を自分の子孫 B へ付け替えようとしても循環になるので拒否。
        MindmapSketchModel m = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        m.setRoot(root);
        MindmapNode a = new MindmapNode("A");
        MindmapNode b = new MindmapNode("B");
        m.addChild(root, a);
        m.addChild(a, b);
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(m, true, List.of()));
        edits.set(0);
        Point onA = center(canvas, a);
        Point onB = center(canvas, b);
        boolean ok = GuiActionRunner.execute(() -> {
            canvas.pressForTest(onA);
            return canvas.releaseForTest(onB);
        });
        assertFalse("子孫への付け替えは拒否", ok);
        assertSame("A の親はルートのまま", root, a.getParent());
        assertEquals(0, edits.get());
    }

    @Test
    public void reparentDrag_belowClickThreshold_isNoOp() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(rootWithTwoChildren(n), true, List.of()));
        edits.set(0);
        Point onB = center(canvas, n[2]);
        Point tiny = new Point(onB.x + 1, onB.y + 1); // < CLICK_THRESHOLD_PX(4)
        boolean ok = GuiActionRunner.execute(() -> {
            canvas.pressForTest(onB);
            return canvas.releaseForTest(tiny);
        });
        assertFalse("クリック相当 (微小移動) は付け替えない", ok);
        assertSame("B の親は変わらない", n[0], n[2].getParent());
        assertEquals(0, edits.get());
        assertNull("ドラッグ状態は解除", GuiActionRunner.execute(canvas::draggingForTest));
    }

    @Test
    public void reparentDrag_escapeCancels() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(rootWithTwoChildren(n), true, List.of()));
        edits.set(0);
        Point onB = center(canvas, n[2]);
        GuiActionRunner.execute(() -> {
            canvas.pressForTest(onB);
            canvas.cancelDragForTest();
        });
        assertNull("Esc 相当でドラッグ中断", GuiActionRunner.execute(canvas::draggingForTest));
        assertSame("中断ではモデルは変わらない", n[0], n[2].getParent());
        assertEquals(0, edits.get());
    }

    @Test
    public void lockedModel_ignoresEditsAndPaintsBanner() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(
                () -> canvas.setModel(rootWithTwoChildren(n), false, List.of("skinparam x")));
        edits.set(0);
        GuiActionRunner.execute(() -> {
            canvas.setSelectedForTest(n[1]);
            canvas.addChild(n[1]);
            canvas.deleteSelected();
        });
        assertEquals("ロック中は編集操作を無視する", 0,
                GuiActionRunner.execute(() -> n[1].getChildren().size()) + edits.get());
        assertFalse("ロック中は編集不可", canvas.isModelEditable());
        paint(canvas); // バナー描画で例外が飛ばないこと
    }

    @Test
    public void deleteRoot_emptiesModel_thenAddRootRequestedRecreates() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(rootWithTwoChildren(n), true, List.of()));
        GuiActionRunner.execute(() -> {
            canvas.setSelectedForTest(n[0]);
            canvas.deleteSelected();
        });
        assertNull("ルート削除で空図になる", GuiActionRunner.execute(() -> canvas.model().getRoot()));

        // 空図でのダブルクリックは addRootRequested を通知する (エディタがルート再生成へ配線)。
        addRoot.set(null);
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(canvas,
                MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                40, 40, 2, false, MouseEvent.BUTTON1)));
        assertTrue("空図のダブルクリックで addRootRequested が発火", addRoot.get() != null);
    }

    @Test
    public void setSideOfSelected_onDeepNode_movesBranchOriginNotTheNode() {
        // Root → C(深さ2) → D(深さ3)。D を選択して RIGHT にすると、D 自身ではなく枝の
        // 起点 C の side が変わる (PlantUML は枝内の記号混在を許さず error42L になるため。
        // 深いノードの ownSide を変えても出力は枝の系統へ正規化され無反応に見えてしまう)。
        MindmapSketchModel m = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        m.setRoot(root);
        MindmapNode c = new MindmapNode("C");
        MindmapNode d = new MindmapNode("D");
        m.addChild(root, c);
        m.addChild(c, d);
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> canvas.setModel(m, true, List.of()));
        edits.set(0);
        GuiActionRunner.execute(() -> {
            canvas.setSelectedForTest(d);
            canvas.setSideOfSelected(Side.RIGHT);
        });
        assertSame("枝の起点 C が RIGHT になる", Side.RIGHT, c.getOwnSide());
        assertSame("D 自身の ownSide は AUTO のまま (枝で正規化)", Side.AUTO, d.getOwnSide());
        assertEquals("side 変更で modelEdited が 1 回発火", 1, edits.get());
    }

    @Test
    public void paint_normalTree_doesNotThrow() {
        MindmapNode[] n = new MindmapNode[3];
        MindmapSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(() -> {
            MindmapSketchModel m = rootWithTwoChildren(n);
            n[1].setOwnSide(Side.LEFT);
            n[2].setOwnSide(Side.RIGHT);
            canvas.setModel(m, true, List.of());
            canvas.setSelectedForTest(n[1]);
        });
        Point onB = center(canvas, n[2]);
        GuiActionRunner.execute(() -> {
            canvas.pressForTest(onB);
            canvas.dragForTest(new Point(300, 300));
        });
        paint(canvas); // 選択 + ドラッグオーバーレイ込みで描画できること
    }
}
