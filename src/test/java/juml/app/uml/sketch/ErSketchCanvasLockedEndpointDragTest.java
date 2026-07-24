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
 * {@link ErSketchCanvas} が {@code !editable} (未対応構文によるロック状態) のとき、
 * 端点ハンドルを press してもリレーション付替えドラッグが一切開始しないことを検証する。
 *
 * <p>{@code handlePress} 冒頭のガード ({@code if (!editable) return;}, ErSketchCanvas.java の
 * 200 行付近) により、端点ハンドルの当たり判定にすら到達しないはず。このキャンバスに
 * ドラッグ状態を直接覗くテスト用シームが無いため、実際の press/drag/release 経路
 * ({@code dispatchEvent}) を通し、リリース後も端点/選択状態が変わらず {@code modelEdited}
 * も飛ばないことで固定する。</p>
 */
public class ErSketchCanvasLockedEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private ErSketchCanvas canvas;
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
        ErSketchModel.Entity left = entityWithId("Left", 40, 100);
        right = entityWithId("Right", 320, 100);
        other = entityWithId("Other", 600, 100);
        model.getEntities().add(left);
        model.getEntities().add(right);
        model.getEntities().add(other);
        relation = new ErSketchModel.Relation("Left", ErSketchModel.Cardinality.EXACTLY_ONE,
                ErSketchModel.Cardinality.ZERO_OR_MANY, "Right", null);
        model.getRelations().add(relation);
        GuiActionRunner.execute(() -> {
            // 未対応構文を渡してロック状態 (editable=false) にする。
            canvas.setModel(model, false, List.of("package \"P\" {"));
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
    public void pressDragReleaseOnEndpointHandle_doesNothingWhileLocked() {
        assertFalse("ロック状態のはず", GuiActionRunner.execute(canvas::isModelEditable));
        Point rightHandle = leftMid(right);
        Point target = centerOf(other);

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                rightHandle, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("ロック中は right 側が変わらないはず", "Right", relation.getRight());
        assertEquals("ロック中は left 側も変わらないはず", "Left", relation.getLeft());
        assertEquals("ロック中は modelEdited が飛ばないはず", 0, edits.get());
        assertNull("ロック中は選択も発生しないはず",
                GuiActionRunner.execute(canvas::selectedForTest));
    }
}
