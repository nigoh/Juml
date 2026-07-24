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
import static org.junit.Assert.assertNull;

/**
 * {@link DeploySketchCanvas} が {@code !editable} (未対応構文によるロック状態) のとき、
 * リンク端点ハンドルを press しても端点ドラッグが一切開始しないことを検証する。
 *
 * <p>{@code handlePress} 冒頭のガード ({@code if (!editable) return;}, DeploySketchCanvas.java:258)
 * により、端点ハンドルの当たり判定にすら到達しないはず。{@link DeploySketchCanvas#endpointDragLinkForTest()}
 * を使い、press/drag/release の一連の操作を通しても端点ドラッグが一度も始まらないこと、
 * リンクも {@code modelEdited} も変わらないことを固定する。</p>
 */
public class DeploySketchCanvasLockedEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private DeploySketchCanvas canvas;
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
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 40, 40);
        b = new DeployNode(DeployNode.Kind.DATABASE, "B", null, 260, 40);
        c = new DeployNode(DeployNode.Kind.CLOUD, "C", null, 40, 220);
        model.getNodes().add(a);
        model.getNodes().add(b);
        model.getNodes().add(c);
        link = new DeployLink("A", DeployLink.Kind.ARROW, "B", "JDBC");
        model.getLinks().add(link);
        GuiActionRunner.execute(() -> {
            // 未対応構文を渡してロック状態 (editable=false) にする。
            canvas.setModel(model, false, List.of("node X {"));
            canvas.setSize(600, 500);
        });
    }

    private void dispatch(int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    @Test
    public void pressDragReleaseOnEndpointHandle_doesNothingWhileLocked() {
        assertFalse("ロック状態のはず", GuiActionRunner.execute(canvas::isModelEditable));
        Rectangle bRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(b));
        Rectangle cRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(c));
        int handleX = bRect.x;
        int handleY = bRect.y + bRect.height / 2;
        int targetX = cRect.x + cRect.width / 2;
        int targetY = cRect.y + cRect.height / 2;

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                handleX, handleY, MouseEvent.BUTTON1);
        assertNull("ロック中は端点ドラッグが一切始まらないはず",
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));

        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, targetX, targetY, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, targetX, targetY, MouseEvent.BUTTON1);

        assertEquals("ロック中は to 側が変わらないはず", "B", link.getTo());
        assertEquals("ロック中は from 側も変わらないはず", "A", link.getFrom());
        assertEquals("ロック中は modelEdited が飛ばないはず", 0, edits.get());
    }
}
