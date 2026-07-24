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

/**
 * {@link ErSketchCanvas} で端点ドラッグ (reattachDrag) 中に中ボタン (パン) をリリースしても
 * 繋ぎ替えが誤って確定しないことを検証する (bug-hunt round5 論点3、全 8 キャンバス共通)。
 *
 * <p>{@link SketchCanvasMiddleButtonReleaseDuringEndpointDragTest} と同じ観点を、
 * フィールド名が {@code reattachDrag} である ER/UseCase/Component 系の代表として
 * ER 図キャンバスで固定する。</p>
 */
public class ErSketchCanvasMiddleButtonReleaseDuringReattachDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private ErSketchCanvas canvas;
    private ErSketchModel.Entity left;
    private ErSketchModel.Entity right;
    private ErSketchModel.Entity other;
    private ErSketchModel.Relation relation;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        ErSketchCanvas.Listener listener = new ErSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editEntityRequested(ErSketchModel.Entity e) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new ErSketchCanvas(listener));
        ErSketchModel model = new ErSketchModel();
        left = entityWithId("Left", 40, 100);
        right = entityWithId("Right", 320, 100);
        other = entityWithId("Other", 600, 100);
        model.getEntities().add(left);
        model.getEntities().add(right);
        model.getEntities().add(other);
        relation = new ErSketchModel.Relation("Left", ErSketchModel.Cardinality.EXACTLY_ONE,
                ErSketchModel.Cardinality.ZERO_OR_MANY, "Right", null);
        model.getRelations().add(relation);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(900, 400);
        });
    }

    private static ErSketchModel.Entity entityWithId(String alias, int x, int y) {
        ErSketchModel.Entity e = new ErSketchModel.Entity(alias, null, x, y);
        e.getColumns().add(new ErSketchModel.Column(true, "id", "int"));
        return e;
    }

    private Point leftMid(ErSketchModel.Entity e) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(e));
        return new Point(r.x, r.y + r.height / 2);
    }

    private Point centerOf(ErSketchModel.Entity e) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(e));
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private void dispatch(int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void middleButtonReleaseDuringReattachDrag_doesNotConfirmReattach() {
        Point rightHandle = leftMid(right);
        Point target = centerOf(other);

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, rightHandle,
                MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);

        // 左ボタンはまだ離さず、中ボタン (パン) だけ press → release する。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK,
                target, MouseEvent.BUTTON2);
        dispatch(MouseEvent.MOUSE_RELEASED, InputEvent.BUTTON1_DOWN_MASK, target, MouseEvent.BUTTON2);

        assertEquals("中ボタンのリリースでは繋ぎ替えが確定しないはず", "Right", relation.getRight());
        assertEquals("中ボタンのリリースでは modelEdited が飛ばないはず", 0, edits.get());

        dispatch(MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("その後の左リリースで従来どおり Other へ付け替わるはず",
                "Other", relation.getRight());
        assertEquals(1, edits.get());
    }
}
