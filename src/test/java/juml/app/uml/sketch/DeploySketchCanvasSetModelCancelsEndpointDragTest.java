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
import static org.junit.Assert.assertNull;

/**
 * {@link DeploySketchCanvas} で端点ドラッグ中に {@code setModel} を呼ぶと、進行中の端点
 * ドラッグが中断されることを検証する (bug-hunt round4 指摘 L)。
 *
 * <p>他 6 キャンバス (Class/State/Object/UseCase/Component/ER) はいずれも {@code setModel}
 * で {@code endpointDrag}/{@code reattachDrag} を {@code cancel()} するが、修正前の Deploy は
 * これを欠いていた。モデル差替え (図の再ロード等) の瞬間に端点ドラッグが進行中だと、旧モデルの
 * リンクを指したままドラッグ状態が残り、以後の release で新モデルに存在しないリンクへ
 * reattach/modelEdited を試みてしまう (孤立参照)。ここでは (1) setModel 後に
 * {@link DeploySketchCanvas#endpointDragLinkForTest()} が null に戻ること、(2) その後の
 * release で新モデルの edits カウントが変化しないことを確認する。</p>
 */
public class DeploySketchCanvasSetModelCancelsEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private DeploySketchCanvas canvas;
    private DeploySketchModel oldModel;
    private DeployNode a;
    private DeployNode b;
    private DeployLink oldLink;

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
        oldModel = new DeploySketchModel();
        a = new DeployNode(DeployNode.Kind.NODE, "A", null, 40, 40);
        b = new DeployNode(DeployNode.Kind.DATABASE, "B", null, 260, 40);
        oldModel.getNodes().add(a);
        oldModel.getNodes().add(b);
        oldLink = new DeployLink("A", DeployLink.Kind.ARROW, "B", "JDBC");
        oldModel.getLinks().add(oldLink);
        GuiActionRunner.execute(() -> {
            canvas.setModel(oldModel, true, List.of());
            canvas.setSize(600, 500);
        });
    }

    private void dispatch(int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    @Test
    public void setModelDuringEndpointDrag_cancelsDragAndLaterReleaseDoesNotReattach() {
        Rectangle bRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(b));
        int handleX = bRect.x;
        int handleY = bRect.y + bRect.height / 2;

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                handleX, handleY, MouseEvent.BUTTON1);
        assertEquals("端点ドラッグが開始しているはず", oldLink,
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));

        // 新しいモデルへ差し替える (図の再ロード相当)。旧モデルの A/B/oldLink はもう画面上に無い。
        DeploySketchModel newModel = new DeploySketchModel();
        DeployNode c = new DeployNode(DeployNode.Kind.NODE, "C", null, 40, 40);
        DeployNode d = new DeployNode(DeployNode.Kind.CLOUD, "D", null, 260, 40);
        newModel.getNodes().add(c);
        newModel.getNodes().add(d);
        GuiActionRunner.execute(() -> canvas.setModel(newModel, true, List.of()));

        assertNull("setModel で端点ドラッグは中断されるはず (bug-hunt round4 指摘 L)",
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));

        // ドラッグ中断後に release しても、新モデルに孤立 reattach/modelEdited が起きないこと。
        Rectangle dRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(d));
        int targetX = dRect.x + dRect.width / 2;
        int targetY = dRect.y + dRect.height / 2;
        dispatch(MouseEvent.MOUSE_RELEASED, 0, targetX, targetY, MouseEvent.BUTTON1);

        assertEquals("旧リンクは変更されないはず", "B", oldLink.getTo());
        assertEquals("新モデルにリンクが増えてはいけない", 0, newModel.getLinks().size());
        assertEquals("孤立 reattach で modelEdited が飛んではならない", 0, edits.get());
    }
}
