// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link StateSketchCanvas} で端点ドラッグ中に {@code setModel} を呼ぶと、進行中の端点
 * ドラッグが中断されることを検証する ({@link DeploySketchCanvasSetModelCancelsEndpointDragTest}
 * と同じ観点の回帰網を State キャンバスへ広げたもの)。
 *
 * <p>モデル差替え (図の再ロード等) の瞬間に端点ドラッグが進行中だと、旧モデルの遷移を
 * 指したままドラッグ状態が残り、以後の release で新モデルに存在しない状態へ
 * reattach/modelEdited を試みてしまう (孤立参照)。{@code setModel} 内の
 * {@code endpointDrag.cancel()} (StateSketchCanvas.java:162) がこれを防いでいることを、
 * (1) setModel 後に {@link StateSketchCanvas#dragTransitionForTest()} が null に戻ること、
 * (2) その後の release で新モデルの edits カウントが変化しないことで確認する。</p>
 */
public class StateSketchCanvasSetModelCancelsEndpointDragTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private StateSketchCanvas.Listener listener() {
        return new StateSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editStateRequested(StateNode s) {
            }
        };
    }

    private void dispatch(StateSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void setModelDuringEndpointDrag_cancelsDragAndLaterReleaseDoesNotReattach() {
        StateSketchCanvas canvas = GuiActionRunner.execute(() -> new StateSketchCanvas(listener()));
        StateSketchModel oldModel = new StateSketchModel();
        StateNode s1 = new StateNode("S1", 60, 60);
        StateNode s2 = new StateNode("S2", 300, 60);
        oldModel.getStates().add(s1);
        oldModel.getStates().add(s2);
        StateTransition oldTransition = new StateTransition("S1", "S2", null);
        oldModel.getTransitions().add(oldTransition);
        GuiActionRunner.execute(() -> {
            canvas.setModel(oldModel, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });

        Point fromAnchor =
                GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(oldTransition)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromAnchor, MouseEvent.BUTTON1);
        assertTrue("端点ドラッグが開始しているはず",
                GuiActionRunner.execute(() -> canvas.dragTransitionForTest() == oldTransition));

        // 新しいモデルへ差し替える (図の再ロード相当)。旧モデルの S1/S2/oldTransition はもう
        // 画面上に無い。
        StateSketchModel newModel = new StateSketchModel();
        StateNode s3 = new StateNode("S3", 60, 60);
        StateNode s4 = new StateNode("S4", 300, 60);
        newModel.getStates().add(s3);
        newModel.getStates().add(s4);
        GuiActionRunner.execute(() -> canvas.setModel(newModel, true, Collections.emptyList()));

        assertNull("setModel で端点ドラッグは中断されるはず (StateSketchCanvas.java:162)",
                GuiActionRunner.execute(canvas::dragTransitionForTest));

        // ドラッグ中断後に release しても、新モデルに孤立 reattach/modelEdited が起きないこと。
        Point insideS4 = new Point(s4.getX() + 10, s4.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideS4, MouseEvent.BUTTON1);

        assertEquals("旧遷移は変更されないはず", "S2", oldTransition.getTo());
        assertEquals("新モデルに遷移が増えてはいけない", 0, newModel.getTransitions().size());
        assertEquals("孤立 reattach で modelEdited が飛んではならない", 0, edits.get());
    }
}
