// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link SeqSketchCanvas} で端点ドラッグ中に Delete キーを押しても、ドラッグ中のメッセージが
 * 削除されないことを検証する (bug-hunt round4 指摘 J)。
 *
 * <p>他 6 キャンバス (Class/State/Object/UseCase/Component/ER) は端点ドラッグ開始時に
 * {@code selected}({@code =null}) を切り離すため、Delete ハンドラが {@code selected != null}
 * を見るだけで自然にドラッグ中の要素へは効かない。Seq はドラッグ中のメッセージ全体を青く
 * ハイライトする都合上、開始時に {@code selectedItem = hit.message} を設定するため、この
 * ガードだけでは守られない。修正前は {@code deleteSelection()} が
 * {@code endpointDrag.isActive()} を見ずに呼ばれており、ドラッグ中に Delete するとドラッグ中の
 * メッセージ自体がモデルから削除され、以後の release ({@code finishEndpointDrag}) が
 * 既に取り除かれたメッセージへ孤立参照のまま繋ぎ替え/{@code modelEdited} を試みて
 * dangling selection になっていた。</p>
 */
public class SeqSketchCanvasDeleteDuringEndpointDragTest {

    // レイアウト定数 (SeqSketchCanvas / SeqSketchCanvasEndpointReattachTest と同じ値)。
    private static final int MARGIN_X = 32;
    private static final int COL_W = 120;
    private static final int FIRST_ROW_Y = 12 + 48 + 30;
    private static final int CENTER_A = MARGIN_X + COL_W / 2;
    private static final int CENTER_B = CENTER_A + COL_W;
    private static final int CENTER_C = CENTER_B + COL_W;

    private final AtomicInteger edits = new AtomicInteger();
    private SeqSketchCanvas canvas;
    private SeqSketchModel model;
    private SeqItem message;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        SeqSketchCanvas.Listener listener = new SeqSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editMessageRequested(SeqItem m) {
            }

            @Override public void editParticipantRequested(SeqParticipant p) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new SeqSketchCanvas(listener));
        model = new SeqSketchModel();
        model.getParticipants().add(new SeqParticipant("A", SeqParticipant.Kind.PARTICIPANT, true));
        model.getParticipants().add(new SeqParticipant("B", SeqParticipant.Kind.PARTICIPANT, true));
        model.getParticipants().add(new SeqParticipant("C", SeqParticipant.Kind.PARTICIPANT, true));
        message = SeqItem.message("A", SeqItem.Arrow.SYNC, "B", "hello");
        model.getItems().add(message);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 400);
        });
    }

    private void dispatch(int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    /** 非表示/未フォーカスのコンポーネントには KeyboardFocusManager 経由で届かないため、
     * 登録済み KeyListener を EDT 上で直接起動する (SeqSketchCanvasEndpointReattachTest と同じ手法)。 */
    private void pressDelete() {
        KeyEvent del = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED);
        GuiActionRunner.execute(() -> {
            for (KeyListener kl : canvas.getKeyListeners()) {
                kl.keyPressed(del);
            }
        });
    }

    @Test
    public void deleteDuringEndpointDrag_doesNotRemoveDraggedMessage() {
        // "to" 端点 (B) のハンドルを掴んでドラッグを開始する。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                CENTER_B, FIRST_ROW_Y, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, CENTER_C, FIRST_ROW_Y, 0);

        pressDelete();

        assertEquals("端点ドラッグ中の Delete でメッセージが削除されてはならない",
                List.of(message), model.getItems());
        assertEquals("削除されていないので modelEdited は飛ばないはず", 0, edits.get());

        // ドラッグを正常に終える (C へ繋ぎ替え)。dangling 参照にならず通常どおり完了するはず。
        dispatch(MouseEvent.MOUSE_RELEASED, 0, CENTER_C, FIRST_ROW_Y, MouseEvent.BUTTON1);

        assertEquals("Delete 後もドラッグは継続し、release で通常どおり繋ぎ替わるはず",
                "C", message.getTo());
        assertEquals("A", message.getFrom());
        assertTrue("繋ぎ替えで modelEdited が飛ぶはず", edits.get() >= 1);
        assertTrue("メッセージはモデルに残ったままのはず", model.getItems().contains(message));
        assertSame("selection はドラッグ対象のメッセージのままのはず (dangling ではない)",
                message, GuiActionRunner.execute(canvas::selectedItemForTest));
    }
}
