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
 * bug-hunt round8 #2 の回帰テスト: シーケンス図で端点ハンドルを参加者ヘッダー箱
 * (棒人間/名前ボックス, 可視 Y 範囲 = [HEAD_TOP, HEAD_TOP+HEAD_H) = [12,60)) の上で
 * 離すと、{@code lifelineAt} が {@code y < HEAD_TOP + HEAD_H} で弾いていたため
 * target=null となり付替えが無反応だった不具合を検証する。他 6 キャンバスは付替え先
 * ノードの可視矩形全域 (このヘッダー箱に相当) で受理するため、この不一致を解消する。
 *
 * <p>参加者名を 1 文字 (A/B/C) に固定し、列幅が {@code COL_MIN_W} (120px、名前幅では
 * 超えない) で決まる前提でライフライン中心 X 座標を固定値として扱う
 * ({@link SeqSketchCanvasEndpointReattachTest} と同じ前提)。</p>
 */
public class SeqSketchCanvasHeaderDropReattachTest {

    private static final int MARGIN_X = 32;
    private static final int COL_W = 120;
    private static final int FIRST_ROW_Y = 12 + 48 + 30;
    private static final int CENTER_A = MARGIN_X + COL_W / 2;
    private static final int CENTER_B = CENTER_A + COL_W;
    private static final int CENTER_C = CENTER_B + COL_W;
    /** 参加者ヘッダー箱の可視 Y 範囲 [12,60) の中間点。 */
    private static final int HEADER_Y = 30;

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

    /** 端点ハンドルを (fromX,fromY) で掴み (toX,toY) までドラッグしてリリースする。 */
    private void dragEndpoint(int fromX, int fromY, int toX, int toY) {
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromX, fromY, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, toX, toY, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, toX, toY, MouseEvent.BUTTON1);
    }

    @Test
    public void dropOnParticipantHeaderBox_reattachesToThatParticipant() {
        // "to" 端点 (B, x=CENTER_B) を C の参加者ヘッダー箱 (y=30、可視範囲 [12,60)) へドラッグする。
        dragEndpoint(CENTER_B, FIRST_ROW_Y, CENTER_C, HEADER_Y);
        assertEquals("ヘッダー箱へ落とすと C へ付替わるはず", "C", message.getTo());
        assertEquals("modelEdited が 1 回飛ぶはず", 1, edits.get());
    }

    @Test
    public void dropOnLifelineColumn_stillReattachesAsBefore() {
        // ライフライン縦線 (y >= 60) でも従来どおり付替わることを確認する (回帰防止)。
        dragEndpoint(CENTER_B, FIRST_ROW_Y, CENTER_C, FIRST_ROW_Y);
        assertEquals("ライフライン列でも C へ付替わるはず", "C", message.getTo());
        assertEquals(1, edits.get());
    }

    @Test
    public void dropAboveHeaderBox_stillCancelsReattach() {
        // ヘッダー箱より上 (y < HEAD_TOP=12) は引き続きキャンセルされるはず。
        dragEndpoint(CENTER_B, FIRST_ROW_Y, CENTER_C, 0);
        assertEquals("ヘッダーより上でのリリースは元の宛先のままのはず", "B", message.getTo());
        assertEquals("モデル編集通知が飛んではならない", 0, edits.get());
    }
}
