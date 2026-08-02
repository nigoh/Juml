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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * {@link SeqSketchCanvas} の端点ハンドルしきい値 (ヒット半径のズーム対応) の配線検証。
 *
 * <p>{@code handlePress} は {@code endpointAt} で {@link EndpointHitThreshold#modelRadius} /
 * {@link EndpointHitThreshold#nearestPair} を使い、押下点がハンドルから画面上約 8px 以内かを
 * 判定する。純関数寄りの当たり判定 ({@code endpointAtForTest}) 自体は {@link
 * SeqSketchCanvasEndpointReattachTest} で固定済みだが、実キャンバスの {@code view.zoom()} が
 * 正しく {@code handlePress} まで配線されているか (ズーム係数の掛け忘れ等の単純ミス) は
 * 未検証だったため、実 press/drag/release ディスパッチで確認する。{@link
 * SketchCanvasEndpointClickThresholdTest} と同じ検証観点・作法をシーケンス図へ適用する。
 * 参加者名を 1 文字 (A/B/C) に固定し、列幅が {@code COL_MIN_W} (120px) で決まる前提でライフ
 * ライン中心 X 座標を固定値として扱う ({@link SeqSketchCanvasEndpointReattachTest} と同じ前提)。</p>
 */
public class SeqSketchCanvasEndpointClickThresholdTest {

    // レイアウト定数 (SeqSketchCanvas と同じ値)。
    private static final int MARGIN_X = 32;
    private static final int COL_W = 120;
    private static final int FIRST_ROW_Y = 12 + 48 + 30;
    private static final int CENTER_A = MARGIN_X + COL_W / 2;
    private static final int CENTER_B = CENTER_A + COL_W;
    private static final int CENTER_C = CENTER_B + COL_W;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private SeqSketchCanvas.Listener listener() {
        return new SeqSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editMessageRequested(SeqItem message) {
            }

            @Override public void editParticipantRequested(SeqParticipant participant) {
            }
        };
    }

    private SeqSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new SeqSketchCanvas(listener()));
    }

    /** A→B の SYNC メッセージ 1 本と、離れた付替え先候補 C を持つモデル。 */
    private SeqSketchModel sampleModel() {
        SeqSketchModel model = new SeqSketchModel();
        model.getParticipants().add(new SeqParticipant("A", SeqParticipant.Kind.PARTICIPANT, true));
        model.getParticipants().add(new SeqParticipant("B", SeqParticipant.Kind.PARTICIPANT, true));
        model.getParticipants().add(new SeqParticipant("C", SeqParticipant.Kind.PARTICIPANT, true));
        model.getItems().add(SeqItem.message("A", SeqItem.Arrow.SYNC, "B", "hello"));
        return model;
    }

    private void dispatch(SeqSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    private static Point toScreen(Point modelPoint, double zoom) {
        return new Point((int) Math.round(modelPoint.x * zoom), (int) Math.round(modelPoint.y * zoom));
    }

    @Test
    public void clickWithoutMoving_doesNotReattachOrFireModelEdited() {
        SeqSketchCanvas canvas = newCanvas();
        SeqSketchModel model = sampleModel();
        SeqItem message = model.getItems().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 400);
        });
        Point anchor = new Point(CENTER_A, FIRST_ROW_Y);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                anchor, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, anchor, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは端点が変わらないはず", "A", message.getFrom());
        assertEquals("クリックでは modelEdited が飛ばないはず", 0, edits.get());
    }

    @Test
    public void pressWithinHandleRadius_atDefaultZoom_startsEndpointDragAndReattaches() {
        SeqSketchCanvas canvas = newCanvas();
        SeqSketchModel model = sampleModel();
        SeqItem message = model.getItems().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 400);
        });
        Point anchor = new Point(CENTER_A, FIRST_ROW_Y);
        // ハンドル中心から 6px (< 8px しきい値) だけ離れた press。
        Point near = new Point(anchor.x + 6, anchor.y);
        Point target = new Point(CENTER_C, FIRST_ROW_Y);

        assertNotNull("しきい値内の座標は endpointAtForTest でもヒットするはず",
                GuiActionRunner.execute(() -> canvas.endpointAtForTest(near)));

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                near, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("しきい値内の press は端点ドラッグを開始し C へ繋ぎ替わるはず",
                "C", message.getFrom());
        assertEquals("B", message.getTo());
        assertEquals(1, edits.get());
    }

    @Test
    public void pressBeyondHandleRadius_atDefaultZoom_doesNotStartEndpointDrag() {
        SeqSketchCanvas canvas = newCanvas();
        SeqSketchModel model = sampleModel();
        SeqItem message = model.getItems().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 400);
        });
        Point anchor = new Point(CENTER_A, FIRST_ROW_Y);
        // ハンドル中心から 20px (> 8px しきい値) 離れた press。
        Point far = new Point(anchor.x + 20, anchor.y);
        Point target = new Point(CENTER_C, FIRST_ROW_Y);

        assertNull("しきい値外の座標は endpointAtForTest でもヒットしないはず",
                GuiActionRunner.execute(() -> canvas.endpointAtForTest(far)));

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                far, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("しきい値外の press は端点ドラッグを開始しないはず", "A", message.getFrom());
        assertEquals("B", message.getTo());
        assertEquals("行が変わらないので modelEdited も飛ばないはず", 0, edits.get());
    }

    // --- bug-hunt round3 指摘 H 相当: 縮小ズームでも端点ハンドルが画面上一定 px で掴めるはず ---

    @Test
    public void zoomedOut_pressAtSameScreenDistanceBeyondModelRadius_stillReattaches() {
        SeqSketchCanvas canvas = newCanvas();
        SeqSketchModel model = sampleModel();
        SeqItem message = model.getItems().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 400);
        });
        Point anchor = new Point(CENTER_A, FIRST_ROW_Y);
        // 等倍では 20px > 8px のしきい値なので掴めない (前テストで確認済み)。
        Point farInModel = new Point(anchor.x + 20, anchor.y);
        Point targetInModel = new Point(CENTER_C, FIRST_ROW_Y);

        GuiActionRunner.execute(() -> canvas.setZoomForTest(SketchViewport.MIN_ZOOM));
        double zoom = SketchViewport.MIN_ZOOM;

        Point pressAtMinZoom = toScreen(farInModel, zoom);
        Point dragAtMinZoom = toScreen(targetInModel, zoom);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressAtMinZoom, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, dragAtMinZoom, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, dragAtMinZoom, MouseEvent.BUTTON1);

        assertEquals("0.25x (MIN_ZOOM) では画面上同じ距離でも掴めて C へ繋ぎ替わるはず"
                        + " (bug-hunt round3 H 相当)", "C", message.getFrom());
        assertEquals("B", message.getTo());
        assertEquals(1, edits.get());
    }
}
