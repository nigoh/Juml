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
 * {@link ComponentSketchCanvas} で端点ドラッグ中に {@code setModel} を呼ぶと、進行中の端点
 * ドラッグが中断されることを検証する ({@code ComponentSketchCanvas.java:155} の
 * {@code this.reattachDrag.cancel();})。
 *
 * <p>Deploy キャンバスの {@code DeploySketchCanvasSetModelCancelsEndpointDragTest} と同じ
 * 主旨だが、このキャンバスにはドラッグ状態を直接覗く {@code endpointDragLinkForTest} 相当の
 * テスト用シームが無い。代わりに (1) press 直後は選択がクリアされること (端点ドラッグ開始の
 * 間接証拠。{@code beginReattachIfHandleHit} が {@code selected = null} にする)、(2) setModel
 * 後に別ノード上へ release しても旧関係が変わらず新モデルにも関係が増えず modelEdited も
 * 飛ばないこと、で固定する。</p>
 */
public class ComponentSketchCanvasSetModelCancelsEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private ComponentSketchCanvas canvas;
    private ComponentNode compB;
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
        model.getNodes().add(compA);
        model.getNodes().add(compB);
        relation = new ComponentRelation("A", ComponentRelation.Kind.ARROW, "B", null);
        model.getRelations().add(relation);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
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
    public void setModelDuringEndpointDrag_cancelsDragAndLaterReleaseDoesNotReattach() {
        Point toHandle = leftMid(compB);
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                toHandle, MouseEvent.BUTTON1);
        assertNull("端点ドラッグ開始時はノード選択されないはず (間接的にドラッグ開始を確認)",
                GuiActionRunner.execute(canvas::selectedForTest));

        // 新しいモデルへ差し替える (図の再ロード相当)。旧モデルの A/B/relation はもう画面上に無い。
        ComponentSketchModel newModel = new ComponentSketchModel();
        ComponentNode compD = new ComponentNode(ComponentNode.Kind.COMPONENT, "D", null, 40, 100);
        ComponentNode compE = new ComponentNode(ComponentNode.Kind.COMPONENT, "E", null, 300, 100);
        newModel.getNodes().add(compD);
        newModel.getNodes().add(compE);
        GuiActionRunner.execute(() -> canvas.setModel(newModel, true, List.of()));

        // ドラッグ中断後に release しても、新モデルに孤立 reattach/modelEdited が起きないこと。
        Point target = centerOf(compE);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("旧関係は変更されないはず", "B", relation.getTo());
        assertEquals("新モデルに関係が増えてはいけない", 0, newModel.getRelations().size());
        assertEquals("孤立 reattach で modelEdited が飛んではならない", 0, edits.get());
    }
}
