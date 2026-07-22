// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * シーケンス図キャンバスの「中ボタンパンが並べ替えとして確定されない」ガード。
 *
 * <p>左クリックでメッセージを選択したあと中ボタンでパンしても、選択メッセージが
 * パン終了地点の行へ並べ替えられない (leftDragArmed ガード) ことと、
 * 通常の左ドラッグ並べ替えは引き続き動くことを固定する。</p>
 */
public class SeqSketchCanvasPanGuardTest {

    /** レイアウト定数 (SeqSketchCanvas と同じ値): 1 行目のメッセージ Y。 */
    private static final int FIRST_ROW_Y = 12 + 48 + 30;
    private static final int ROW_H = 38;

    private final AtomicInteger edits = new AtomicInteger();
    private SeqSketchCanvas canvas;
    private SeqItem first;
    private SeqItem second;

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

            @Override public void editMessageRequested(SeqItem message) {
            }

            @Override public void editParticipantRequested(SeqParticipant participant) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new SeqSketchCanvas(listener));
        SeqSketchModel m = new SeqSketchModel();
        m.getParticipants().add(new SeqParticipant("A", SeqParticipant.Kind.PARTICIPANT, true));
        m.getParticipants().add(new SeqParticipant("B", SeqParticipant.Kind.PARTICIPANT, true));
        first = SeqItem.message("A", SeqItem.Arrow.SYNC, "B", "m1");
        second = SeqItem.message("B", SeqItem.Arrow.SYNC, "A", "m2");
        m.getItems().add(first);
        m.getItems().add(second);
        GuiActionRunner.execute(() -> {
            canvas.setModel(m, true, List.of());
            canvas.setSize(600, 400);
        });
    }

    private void dispatch(int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    private List<SeqItem> items() {
        return GuiActionRunner.execute(() -> canvas.model().getItems());
    }

    @Test
    public void middleDragPan_doesNotReorderSelectedMessage() {
        // 1 行目のメッセージ (2 ライフラインの中間 x=150 付近) を左クリックで選択。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                150, FIRST_ROW_Y, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, 150, FIRST_ROW_Y, MouseEvent.BUTTON1);
        edits.set(0);
        // 中ボタンでドラッグ (パン) して 2 行ぶん下で離す。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON2_DOWN_MASK,
                150, FIRST_ROW_Y, MouseEvent.BUTTON2);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON2_DOWN_MASK,
                150, FIRST_ROW_Y + 2 * ROW_H, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0,
                150, FIRST_ROW_Y + 2 * ROW_H, MouseEvent.BUTTON2);
        assertEquals("パンでメッセージ順序が変わってはならない", first, items().get(0));
        assertEquals(second, items().get(1));
        assertEquals("パンでモデル編集通知が飛んではならない", 0, edits.get());
    }

    @Test
    public void leftDrag_stillReordersMessage() {
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                150, FIRST_ROW_Y, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK,
                150, FIRST_ROW_Y + ROW_H, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0,
                150, FIRST_ROW_Y + ROW_H, MouseEvent.BUTTON1);
        assertEquals("左ドラッグの並べ替えは引き続き動くはず", first, items().get(1));
        assertEquals(second, items().get(0));
        assertEquals(1, edits.get());
    }
}
