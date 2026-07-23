// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 配置図リンクの端点付け替え (ドラッグ&ドロップ) を検証する。
 *
 * <p>{@link DeploySketchCanvas#reattachForTest} 経由のモデル更新確認に加え、
 * 実際のマウス操作経路 (端点ハンドルを掴んでドラッグ→ノード上で離す) も
 * 合成 {@link MouseEvent} (Robot 不要) で固定する。選択/移動モードでのみ有効で、
 * ノード外で離せば変更されないことも確認する。</p>
 */
public class DeploySketchLinkReattachTest {

    private final AtomicInteger edits = new AtomicInteger();
    private DeploySketchCanvas canvas;
    private DeploySketchModel model;
    private DeployNode a;
    private DeployNode b;
    private DeployNode c;
    private DeployLink link;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        DeploySketchCanvas.Listener listener = new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(DeployNode n) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new DeploySketchCanvas(listener));
        model = new DeploySketchModel();
        a = new DeployNode(DeployNode.Kind.NODE, "A", null, 40, 40);
        b = new DeployNode(DeployNode.Kind.DATABASE, "B", null, 260, 40);
        c = new DeployNode(DeployNode.Kind.CLOUD, "C", null, 40, 220);
        model.getNodes().add(a);
        model.getNodes().add(b);
        model.getNodes().add(c);
        link = new DeployLink("A", DeployLink.Kind.ARROW, "B", "JDBC");
        model.getLinks().add(link);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 500);
        });
    }

    private void dispatch(int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    @Test
    public void reattachForTest_startEnd_updatesFromAndFiresModelEdited() {
        GuiActionRunner.execute(() -> canvas.reattachForTest(link, true, c));
        assertEquals("from 側が付け替わるはず", "C", link.getFrom());
        assertEquals("to 側は変わらないはず", "B", link.getTo());
        assertEquals(1, edits.get());
    }

    @Test
    public void reattachForTest_endEnd_updatesToAndFiresModelEdited() {
        GuiActionRunner.execute(() -> canvas.reattachForTest(link, false, c));
        assertEquals("from 側は変わらないはず", "A", link.getFrom());
        assertEquals("to 側が付け替わるはず", "C", link.getTo());
        assertEquals(1, edits.get());
    }

    /** ハンドルを掴んでノード C の上で離すと、実際のマウス経路でも to 側が付け替わる。 */
    @Test
    public void dragEndpointHandle_ontoAnotherNode_reattachesTo() {
        Rectangle bRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(b));
        Rectangle cRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(c));
        // to 側 (B に近い端) のハンドル位置: B の左辺中央付近 (A から B へ向かう矢印の先端)。
        int handleX = bRect.x;
        int handleY = bRect.y + bRect.height / 2;
        int targetX = cRect.x + cRect.width / 2;
        int targetY = cRect.y + cRect.height / 2;

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                handleX, handleY, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, targetX, targetY, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, targetX, targetY, MouseEvent.BUTTON1);

        assertEquals("ドラッグ経路でも to 側がノード C へ付け替わるはず", "C", link.getTo());
        assertEquals("from 側は変わらないはず", "A", link.getFrom());
        assertTrue("modelEdited が飛ぶはず", edits.get() >= 1);
    }

    /** ノード外で離すと付け替えは取消され、モデルは変わらない。 */
    @Test
    public void dragEndpointHandle_releasedOutsideAnyNode_cancels() {
        Rectangle bRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(b));
        int handleX = bRect.x;
        int handleY = bRect.y + bRect.height / 2;
        edits.set(0);

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                handleX, handleY, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, 550, 480, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, 550, 480, MouseEvent.BUTTON1);

        assertEquals("ノード外で離したら to 側は変わらないはず", "B", link.getTo());
        assertEquals("from 側も変わらないはず", "A", link.getFrom());
        assertEquals("モデル未変更なので modelEdited は飛ばないはず", 0, edits.get());
    }

    /** リンク作成モード中は端点ハンドルのドラッグを起動しない (通常のリンク作成優先)。 */
    @Test
    public void endpointDrag_isInactiveInLinkCreationMode() {
        Rectangle bRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(b));
        int handleX = bRect.x;
        int handleY = bRect.y + bRect.height / 2;
        GuiActionRunner.execute(() -> canvas.setLinkMode(DeployLink.Kind.ARROW));
        edits.set(0);

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                handleX, handleY, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, handleX, handleY, MouseEvent.BUTTON1);

        assertEquals("リンク作成モード中は端点付け替えが起きないはず", "B", link.getTo());
        assertFalse("リンクは 1 本のまま増えないはず (単発クリックなので未確定)",
                canvas.model().getLinks().size() > 1);
    }
}
