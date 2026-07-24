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
 * {@link ObjectSketchCanvas} で端点ドラッグ中に {@code setModel} を呼ぶと、進行中の端点
 * ドラッグが中断されることを検証する ({@link DeploySketchCanvasSetModelCancelsEndpointDragTest}
 * と同じ観点の回帰網を Object キャンバスへ広げたもの)。
 *
 * <p>モデル差替え (図の再ロード等) の瞬間に端点ドラッグが進行中だと、旧モデルのリンクを
 * 指したままドラッグ状態が残り、以後の release で新モデルに存在しないオブジェクトへ
 * reattach/modelEdited を試みてしまう (孤立参照)。{@code setModel} 内の
 * {@code endpointDrag.cancel()} (ObjectSketchCanvas.java:173) がこれを防いでいることを、
 * (1) setModel 後に {@link ObjectSketchCanvas#dragLinkForTest()} が null に戻ること、
 * (2) その後の release で新モデルの edits カウントが変化しないことで確認する。</p>
 */
public class ObjectSketchCanvasSetModelCancelsEndpointDragTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private ObjectSketchCanvas.Listener listener() {
        return new ObjectSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editObjectRequested(ObjectInstance o) {
            }

            @Override public void addObjectRequested(Point at) {
            }
        };
    }

    private void dispatch(ObjectSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void setModelDuringEndpointDrag_cancelsDragAndLaterReleaseDoesNotReattach() {
        ObjectSketchCanvas canvas = GuiActionRunner.execute(() -> new ObjectSketchCanvas(listener()));
        ObjectSketchModel oldModel = new ObjectSketchModel();
        ObjectInstance user = new ObjectInstance("User", null, 60, 60);
        ObjectInstance post = new ObjectInstance("Post", null, 300, 60);
        oldModel.getObjects().add(user);
        oldModel.getObjects().add(post);
        ObjectLink oldLink = new ObjectLink("User", ObjectLink.Kind.ARROW, "Post", null);
        oldModel.getLinks().add(oldLink);
        GuiActionRunner.execute(() -> {
            canvas.setModel(oldModel, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });

        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(oldLink)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        assertTrue("端点ドラッグが開始しているはず",
                GuiActionRunner.execute(() -> canvas.dragLinkForTest() == oldLink));

        // 新しいモデルへ差し替える (図の再ロード相当)。旧モデルの User/Post/oldLink はもう
        // 画面上に無い。
        ObjectSketchModel newModel = new ObjectSketchModel();
        ObjectInstance other1 = new ObjectInstance("Other1", null, 60, 60);
        ObjectInstance other2 = new ObjectInstance("Other2", null, 300, 60);
        newModel.getObjects().add(other1);
        newModel.getObjects().add(other2);
        GuiActionRunner.execute(() -> canvas.setModel(newModel, true, Collections.emptyList()));

        assertNull("setModel で端点ドラッグは中断されるはず (ObjectSketchCanvas.java:173)",
                GuiActionRunner.execute(canvas::dragLinkForTest));

        // ドラッグ中断後に release しても、新モデルに孤立 reattach/modelEdited が起きないこと。
        Point insideOther2 = new Point(other2.getX() + 10, other2.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideOther2, MouseEvent.BUTTON1);

        assertEquals("旧リンクは変更されないはず", "Post", oldLink.getRight());
        assertEquals("新モデルにリンクが増えてはいけない", 0, newModel.getLinks().size());
        assertEquals("孤立 reattach で modelEdited が飛んではならない", 0, edits.get());
    }
}
