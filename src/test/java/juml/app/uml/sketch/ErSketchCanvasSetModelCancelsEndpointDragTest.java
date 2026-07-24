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
import static org.junit.Assert.assertNull;

/**
 * {@link ErSketchCanvas} で端点ドラッグ中に {@code setModel} を呼ぶと、進行中の端点
 * ドラッグが中断されることを検証する ({@code ErSketchCanvas.java:171} の
 * {@code this.reattachDrag.cancel();})。
 *
 * <p>Deploy キャンバスの {@code DeploySketchCanvasSetModelCancelsEndpointDragTest} と同じ
 * 主旨だが、このキャンバスにはドラッグ状態を直接覗く {@code endpointDragLinkForTest} 相当の
 * テスト用シームが無い。代わりに (1) press 直後は選択がクリアされること (端点ドラッグ開始の
 * 間接証拠。{@code beginReattachIfHandleHit} が {@code selected = null} にする)、(2) setModel
 * 後に別エンティティ上へ release しても旧リレーションが変わらず新モデルにもリレーションが
 * 増えず modelEdited も飛ばないこと、で固定する。</p>
 */
public class ErSketchCanvasSetModelCancelsEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private ErSketchCanvas canvas;
    private ErSketchModel.Entity right;
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
        model.getEntities().add(left);
        model.getEntities().add(right);
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
    public void setModelDuringEndpointDrag_cancelsDragAndLaterReleaseDoesNotReattach() {
        Point rightHandle = leftMid(right);
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                rightHandle, MouseEvent.BUTTON1);
        assertNull("端点ドラッグ開始時はエンティティ選択されないはず (間接的にドラッグ開始を確認)",
                GuiActionRunner.execute(canvas::selectedForTest));

        // 新しいモデルへ差し替える (図の再ロード相当)。旧モデルの Left/Right/relation はもう
        // 画面上に無い。
        ErSketchModel newModel = new ErSketchModel();
        ErSketchModel.Entity entityD = entityWithId("D", 40, 100);
        ErSketchModel.Entity entityE = entityWithId("E", 320, 100);
        newModel.getEntities().add(entityD);
        newModel.getEntities().add(entityE);
        GuiActionRunner.execute(() -> canvas.setModel(newModel, true, List.of()));

        // ドラッグ中断後に release しても、新モデルに孤立 reattach/modelEdited が起きないこと。
        Point target = centerOf(entityE);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("旧リレーションは変更されないはず", "Right", relation.getRight());
        assertEquals("新モデルにリレーションが増えてはいけない", 0, newModel.getRelations().size());
        assertEquals("孤立 reattach で modelEdited が飛んではならない", 0, edits.get());
    }
}
