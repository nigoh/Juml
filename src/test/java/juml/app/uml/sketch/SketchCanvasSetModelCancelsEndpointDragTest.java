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
 * {@link SketchCanvas} で端点ドラッグ中に {@code setModel} を呼ぶと、進行中の端点ドラッグが
 * 中断されることを検証する ({@link DeploySketchCanvasSetModelCancelsEndpointDragTest} と同じ
 * 観点の回帰網を Class キャンバスへ広げたもの)。
 *
 * <p>モデル差替え (図の再ロード等) の瞬間に端点ドラッグが進行中だと、旧モデルの関係を
 * 指したままドラッグ状態が残り、以後の release で新モデルに存在しないクラスへ
 * reattach/modelEdited を試みてしまう (孤立参照)。{@code setModel} 内の
 * {@code endpointDrag.cancel()} (SketchCanvas.java:175) がこれを防いでいることを、
 * (1) setModel 後に {@link SketchCanvas#dragRelationForTest()} が null に戻ること、
 * (2) その後の release で新モデルの edits カウントが変化しないことで確認する。</p>
 */
public class SketchCanvasSetModelCancelsEndpointDragTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private SketchCanvas.Listener listener() {
        return new SketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editRequested(SketchClass c) {
            }

            @Override public void addClassRequested(Point at) {
            }
        };
    }

    private void dispatch(SketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void setModelDuringEndpointDrag_cancelsDragAndLaterReleaseDoesNotReattach() {
        SketchCanvas canvas = GuiActionRunner.execute(() -> new SketchCanvas(listener()));
        SketchModel oldModel = new SketchModel();
        SketchClass a = new SketchClass("A", SketchClass.Kind.CLASS, 60, 60);
        SketchClass b = new SketchClass("B", SketchClass.Kind.CLASS, 300, 60);
        oldModel.getClasses().add(a);
        oldModel.getClasses().add(b);
        SketchRelation oldRel = new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "B", null);
        oldModel.getRelations().add(oldRel);
        GuiActionRunner.execute(() -> {
            canvas.setModel(oldModel, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });

        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(oldRel)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        assertTrue("端点ドラッグが開始しているはず",
                GuiActionRunner.execute(() -> canvas.dragRelationForTest() == oldRel));

        // 新しいモデルへ差し替える (図の再ロード相当)。旧モデルの A/B/oldRel はもう画面上に無い。
        SketchModel newModel = new SketchModel();
        SketchClass c = new SketchClass("C", SketchClass.Kind.CLASS, 60, 60);
        SketchClass d = new SketchClass("D", SketchClass.Kind.CLASS, 300, 60);
        newModel.getClasses().add(c);
        newModel.getClasses().add(d);
        GuiActionRunner.execute(() -> canvas.setModel(newModel, true, Collections.emptyList()));

        assertNull("setModel で端点ドラッグは中断されるはず (SketchCanvas.java:175)",
                GuiActionRunner.execute(canvas::dragRelationForTest));

        // ドラッグ中断後に release しても、新モデルに孤立 reattach/modelEdited が起きないこと。
        Point insideD = new Point(d.getX() + 10, d.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideD, MouseEvent.BUTTON1);

        assertEquals("旧関係は変更されないはず", "B", oldRel.getRight());
        assertEquals("新モデルに関係が増えてはいけない", 0, newModel.getRelations().size());
        assertEquals("孤立 reattach で modelEdited が飛んではならない", 0, edits.get());
    }
}
