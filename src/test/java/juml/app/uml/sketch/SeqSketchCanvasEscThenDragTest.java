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
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * {@link SeqSketchCanvas} の「端点ドラッグ中の Esc キャンセル後に押下継続すると意図せず
 * メッセージが並べ替わる」不具合 (bug-hunt round2 の issue F) の回帰テスト。
 *
 * <p>Esc ハンドラは元々 {@code endpointDragMessage}/{@code endpointDragPoint} しか
 * クリアしておらず、{@code selectedItem}/{@code leftDragArmed}/{@code
 * draggedSinceMousePress}/{@code dragPoint} が残ったままだった。そのため Esc 後に
 * ボタンを離さず動かすと通常の並べ替えドラッグ経路に入り、release で {@code
 * dropMessageAt} が走って modelEdited が発火してしまっていた。</p>
 */
public class SeqSketchCanvasEscThenDragTest {

    private static final int MARGIN_X = 32;
    private static final int COL_W = 120;
    private static final int FIRST_ROW_Y = 12 + 48 + 30;
    private static final int ROW_H = 38;
    private static final int CENTER_B = MARGIN_X + COL_W / 2 + COL_W;
    private static final int CENTER_C = CENTER_B + COL_W;

    private final AtomicInteger edits = new AtomicInteger();
    private SeqSketchCanvas canvas;
    private SeqSketchModel model;
    private SeqItem m1;
    private SeqItem m2;
    private SeqItem m3;

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
        m1 = SeqItem.message("A", SeqItem.Arrow.SYNC, "B", "m1");
        m2 = SeqItem.message("B", SeqItem.Arrow.SYNC, "C", "m2");
        m3 = SeqItem.message("A", SeqItem.Arrow.SYNC, "C", "m3");
        model.getItems().add(m1);
        model.getItems().add(m2);
        model.getItems().add(m3);
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
    private void pressEscape() {
        KeyEvent esc = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        GuiActionRunner.execute(() -> {
            for (java.awt.event.KeyListener kl : canvas.getKeyListeners()) {
                kl.keyPressed(esc);
            }
        });
    }

    @Test
    public void escDuringEndpointDrag_thenContinuingToDrag_doesNotReorderMessages() {
        int row1Y = FIRST_ROW_Y + ROW_H; // m2 の行。
        // m2 の "to" 端点ハンドル (C, row1) を掴んで少しドラッグする。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                CENTER_C, row1Y, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK,
                CENTER_C + 5, row1Y, 0);

        // Esc で端点ドラッグを中断する。
        pressEscape();

        // ボタンを離さずさらに動かす (末尾の行付近まで): 旧実装ではここで並べ替えドラッグへ
        // 入ってしまっていた。
        int farBelowY = FIRST_ROW_Y + 3 * ROW_H + 10;
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK,
                CENTER_C, farBelowY, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, CENTER_C, farBelowY, MouseEvent.BUTTON1);

        assertEquals("Esc 後の継続ドラッグでメッセージの並び順が変わってはならない",
                List.of(m1, m2, m3), model.getItems());
        assertEquals("Esc 後の継続ドラッグで modelEdited が飛んではならない", 0, edits.get());
    }
}
