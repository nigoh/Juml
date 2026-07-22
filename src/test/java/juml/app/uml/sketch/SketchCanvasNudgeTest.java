// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * {@link SketchCanvas#nudgeSelected(int, int)} の矢印キー移動 (キーボード微調整)。
 *
 * <p>選択クラスが相対移動し、モデル編集通知 (テキスト再生成) が飛ぶこと、
 * 未選択・編集不可では何もしないことを固定する。座標計算のみで実ディスプレイ
 * (Robot) は不要。</p>
 */
public class SketchCanvasNudgeTest {

    private static final class CountingListener implements SketchCanvas.Listener {
        final AtomicInteger edits = new AtomicInteger();

        @Override public void modelEdited() {
            edits.incrementAndGet();
        }

        @Override public void editRequested(SketchClass c) {
        }

        @Override public void addClassRequested(Point at) {
        }
    }

    private static SketchModel modelWithClass(SketchClass c) {
        SketchModel m = new SketchModel();
        m.getClasses().add(c);
        return m;
    }

    @Test
    public void nudgeSelected_movesSelectedAndNotifiesEdit() {
        CountingListener listener = new CountingListener();
        SketchCanvas canvas = GuiActionRunner.execute(() -> new SketchCanvas(listener));
        SketchClass c = new SketchClass("A", SketchClass.Kind.CLASS, 40, 40);
        GuiActionRunner.execute(() -> {
            canvas.setModel(modelWithClass(c), true, List.of());
            canvas.setSelectedForTest(c);
            canvas.nudgeSelected(1, 0);
            canvas.nudgeSelected(0, -8);
        });
        assertEquals(41, c.getX());
        assertEquals(32, c.getY());
        assertEquals("移動のたびにモデル編集通知が飛ぶはず", 2, listener.edits.get());
    }

    @Test
    public void nudgeSelected_clampsAtOrigin() {
        CountingListener listener = new CountingListener();
        SketchCanvas canvas = GuiActionRunner.execute(() -> new SketchCanvas(listener));
        SketchClass c = new SketchClass("A", SketchClass.Kind.CLASS, 0, 0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(modelWithClass(c), true, List.of());
            canvas.setSelectedForTest(c);
            canvas.nudgeSelected(-5, -5);
        });
        assertEquals("負座標へは行かずクランプされるはず", 0, c.getX());
        assertEquals(0, c.getY());
    }

    @Test
    public void nudgeSelected_ignoredWithoutSelection() {
        CountingListener listener = new CountingListener();
        SketchCanvas canvas = GuiActionRunner.execute(() -> new SketchCanvas(listener));
        SketchClass c = new SketchClass("A", SketchClass.Kind.CLASS, 40, 40);
        GuiActionRunner.execute(() -> {
            canvas.setModel(modelWithClass(c), true, List.of());
            canvas.nudgeSelected(1, 0);
        });
        assertEquals("未選択では移動しないはず", 40, c.getX());
        assertEquals(0, listener.edits.get());
    }

    @Test
    public void nudgeSelected_ignoredWhenNotEditable() {
        CountingListener listener = new CountingListener();
        SketchCanvas canvas = GuiActionRunner.execute(() -> new SketchCanvas(listener));
        SketchClass c = new SketchClass("A", SketchClass.Kind.CLASS, 40, 40);
        GuiActionRunner.execute(() -> {
            canvas.setModel(modelWithClass(c), false, List.of("未対応行"));
            canvas.setSelectedForTest(c);
            canvas.nudgeSelected(1, 0);
        });
        assertEquals("編集不可では移動しないはず", 40, c.getX());
        assertEquals(0, listener.edits.get());
    }
}
