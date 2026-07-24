// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * {@link ComponentSketchCanvas} が {@code !editable} (未対応構文によるロック状態) のとき、
 * 端点ハンドルを press しても端点付替えドラッグが一切開始しないことを検証する。
 *
 * <p>{@code handlePress} 冒頭のガード ({@code if (!editable) return;}, ComponentSketchCanvas.java:204)
 * により、端点ハンドルの当たり判定にすら到達しないはず。このキャンバスにドラッグ状態を
 * 直接覗くテスト用シームが無いため、実際の press/drag/release 経路 ({@code dispatchEvent})
 * を通し、リリース後も端点/選択状態が変わらず {@code modelEdited} も飛ばないことで固定する。</p>
 */
public class ComponentSketchCanvasLockedEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private ComponentSketchCanvas canvas;
    private ComponentNode compB;
    private ComponentNode compC;
    private ComponentRelation relation;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        ComponentSketchCanvas.Listener listener = new ComponentSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(ComponentNode n) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new ComponentSketchCanvas(listener));
        ComponentSketchModel model = new ComponentSketchModel();
        ComponentNode compA = new ComponentNode(ComponentNode.Kind.COMPONENT, "A", null, 40, 100);
        compB = new ComponentNode(ComponentNode.Kind.COMPONENT, "B", null, 300, 100);
        compC = new ComponentNode(ComponentNode.Kind.COMPONENT, "C", null, 560, 100);
        model.getNodes().add(compA);
        model.getNodes().add(compB);
        model.getNodes().add(compC);
        relation = new ComponentRelation("A", ComponentRelation.Kind.ARROW, "B", null);
        model.getRelations().add(relation);
        GuiActionRunner.execute(() -> {
            // 未対応構文を渡してロック状態 (editable=false) にする。
            canvas.setModel(model, false, List.of("actor \"Someone\" as u"));
            canvas.setSize(800, 400);
        });
    }

    private Point leftMid(ComponentNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(n));
        return new Point(r.x, r.y + r.height / 2);
    }

    private Point centerOf(ComponentNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(n));
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private void dispatch(int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void pressDragReleaseOnEndpointHandle_doesNothingWhileLocked() {
        assertFalse("ロック状態のはず", GuiActionRunner.execute(canvas::isModelEditable));
        Point toHandle = leftMid(compB);
        Point target = centerOf(compC);

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                toHandle, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("ロック中は to 側が変わらないはず", "B", relation.getTo());
        assertEquals("ロック中は from 側も変わらないはず", "A", relation.getFrom());
        assertEquals("ロック中は modelEdited が飛ばないはず", 0, edits.get());
        assertNull("ロック中は選択も発生しないはず",
                GuiActionRunner.execute(canvas::selectedForTest));
    }
}
