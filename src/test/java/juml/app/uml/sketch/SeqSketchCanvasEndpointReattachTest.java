// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * シーケンス図キャンバスの「メッセージ端点 (送信元/送信先ライフライン) の付替え」を検証する。
 *
 * <p>参加者名を 1 文字 (A/B/C) に固定し、列幅が {@code COL_MIN_W} (120px, 名前幅では超えない)
 * で決まる前提でライフライン中心 X 座標を固定値として扱う
 * ({@link SeqSketchCanvasPanGuardTest} と同じ前提)。</p>
 */
public class SeqSketchCanvasEndpointReattachTest {

    // レイアウト定数 (SeqSketchCanvas と同じ値)。
    private static final int MARGIN_X = 32;
    private static final int COL_W = 120;
    private static final int FIRST_ROW_Y = 12 + 48 + 30;
    private static final int ROW_H = 38;
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

    /** 端点ハンドルを (fromX,fromY) で掴み (toX,toY) までドラッグしてリリースする。 */
    private void dragEndpoint(int fromX, int fromY, int toX, int toY) {
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromX, fromY, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, toX, toY, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, toX, toY, MouseEvent.BUTTON1);
    }

    // --- (a) 付替えでメッセージの参加者が変わり、再生成 PlantUML に反映される ------------

    @Test
    public void dragToEndpoint_reattachesTargetAndRegeneratesPuml() {
        // "to" 端点 (B, x=CENTER_B) を C のライフラインへドラッグする。
        dragEndpoint(CENTER_B, FIRST_ROW_Y, CENTER_C, FIRST_ROW_Y);
        assertEquals("A", message.getFrom());
        assertEquals("C", message.getTo());
        assertEquals("modelEdited が 1 回飛ぶはず", 1, edits.get());
        String puml = SeqSketchCodec.toPuml(canvas.model());
        assertTrue("再生成された PlantUML に新しい送信先が反映されるはず: " + puml,
                puml.contains("A -> C : hello"));
        assertFalse("旧宛先 (B) 宛のメッセージ行は残らないはず", puml.contains("A -> B"));
    }

    @Test
    public void dragFromEndpoint_reattachesSourceAndRegeneratesPuml() {
        // "from" 端点 (A, x=CENTER_A) を C のライフラインへドラッグする。
        dragEndpoint(CENTER_A, FIRST_ROW_Y, CENTER_C, FIRST_ROW_Y);
        assertEquals("C", message.getFrom());
        assertEquals("B", message.getTo());
        assertEquals(1, edits.get());
        assertTrue(SeqSketchCodec.toPuml(canvas.model()).contains("C -> B : hello"));
    }

    @Test
    public void reattachForTest_updatesModelThroughRealPathAndFiresModelEdited() {
        SeqParticipant c = model.getParticipants().get(2);
        GuiActionRunner.execute(() -> canvas.reattachForTest(message, false, c));
        assertEquals("C", message.getTo());
        assertEquals(1, edits.get());
    }

    @Test
    public void selfMessage_endpointCanBeReattachedToOtherParticipant() {
        // 自己メッセージ (from == to) の始点ハンドルを別参加者へドラッグすると通常メッセージへ変わる
        // (仕様: 自己メッセージの端点付替えも既存表現が許すため許可する)。
        SeqItem self = SeqItem.message("A", SeqItem.Arrow.SYNC, "A", "loop");
        GuiActionRunner.execute(() -> {
            model.getItems().add(self);
            canvas.setModel(model, true, List.of());
        });
        int y = FIRST_ROW_Y + ROW_H; // 2 番目のメッセージなので 1 行下。
        dragEndpoint(CENTER_A, y - 7, CENTER_B, y);
        assertEquals("B", self.getFrom());
        assertEquals("A", self.getTo());
        assertEquals(1, edits.get());
    }

    // --- (b) ライフライン外リリースはキャンセルされる ---------------------------------

    @Test
    public void releaseOutsideLifeline_cancelsReattach() {
        // ライフラインの X 帯から大きく外れた位置でリリースする。
        dragEndpoint(CENTER_B, FIRST_ROW_Y, CENTER_C + 400, FIRST_ROW_Y);
        assertEquals("ライフライン外リリースは元の宛先のままのはず", "B", message.getTo());
        assertEquals("モデル編集通知が飛んではならない", 0, edits.get());
    }

    @Test
    public void releaseAboveHeader_cancelsReattach() {
        // ライフラインの Y 範囲より上 (ヘッダー領域) でリリースする。
        dragEndpoint(CENTER_B, FIRST_ROW_Y, CENTER_C, 0);
        assertEquals("B", message.getTo());
        assertEquals(0, edits.get());
    }

    @Test
    public void releaseOnSameParticipant_doesNotFireModelEdited() {
        // ドラッグせず同じ端点でリリース (クリック相当) してもモデルは変わらない。
        dragEndpoint(CENTER_B, FIRST_ROW_Y, CENTER_B, FIRST_ROW_Y);
        assertEquals("B", message.getTo());
        assertEquals(0, edits.get());
    }

    @Test
    public void escape_cancelsInProgressEndpointDrag() {
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                CENTER_B, FIRST_ROW_Y, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, CENTER_C, FIRST_ROW_Y, 0);
        // canvas はオフスクリーンでフォーカスを持てないため dispatchEvent は
        // KeyboardFocusManager に横取りされる。登録済み KeyListener (ツールチップの
        // アクセシビリティ用リスナーも含む) を全て直接呼んで実ディスパッチを再現する。
        KeyEvent esc = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
        GuiActionRunner.execute(() -> {
            for (java.awt.event.KeyListener kl : canvas.getKeyListeners()) {
                kl.keyPressed(esc);
            }
        });
        dispatch(MouseEvent.MOUSE_RELEASED, 0, CENTER_C, FIRST_ROW_Y, MouseEvent.BUTTON1);
        assertEquals("Esc で中断したのでリリースしても宛先は変わらないはず", "B", message.getTo());
        assertEquals(0, edits.get());
    }

    // --- ヒットテスト (純関数寄りのシーム) ---------------------------------------------

    @Test
    public void endpointHitTest_findsFromAndToHandles() {
        Point fromPoint = new Point(CENTER_A, FIRST_ROW_Y);
        Point toPoint = new Point(CENTER_B, FIRST_ROW_Y);
        SeqSketchCanvas.EndpointHit fromHit =
                GuiActionRunner.execute(() -> canvas.endpointAtForTest(fromPoint));
        SeqSketchCanvas.EndpointHit toHit =
                GuiActionRunner.execute(() -> canvas.endpointAtForTest(toPoint));
        assertEquals(message, fromHit.message);
        assertTrue(fromHit.fromEnd);
        assertEquals(message, toHit.message);
        assertFalse(toHit.fromEnd);
        assertNull("端点から離れた位置はヒットしないはず",
                GuiActionRunner.execute(() -> canvas.endpointAtForTest(new Point(0, 0))));
    }

    @Test
    public void withinHandle_pureFunctionRespectsGivenThreshold() {
        Point endpoint = new Point(100, 100);
        assertTrue("半径 8px 以内はヒット",
                SeqSketchCanvas.withinHandle(new Point(104, 100), endpoint, 8.0));
        assertFalse("半径 8px を超えたらヒットしない",
                SeqSketchCanvas.withinHandle(new Point(120, 100), endpoint, 8.0));
    }

    // --- bug-hunt round3 指摘 H: 縮小ズームでも端点ハンドルが画面上一定 px で掴めるはず ------

    @Test
    public void withinHandle_zoomScaledThreshold_catchesFartherPressAtMinZoom() {
        Point endpoint = new Point(100, 100);
        Point press = new Point(120, 100); // モデル座標で 20px 離れた press。
        double zoom1Threshold = EndpointHitThreshold.modelRadius(8.0, 1.0);
        double zoom025Threshold = EndpointHitThreshold.modelRadius(8.0, 0.25);
        assertEquals("等倍では従来どおり 8px 相当", 8.0, zoom1Threshold, 1e-9);
        assertEquals("0.25x (MIN_ZOOM) では 32px 相当まで拾うはず", 32.0, zoom025Threshold, 1e-9);
        assertFalse("等倍では 20px 離れると従来どおりヒットしない",
                SeqSketchCanvas.withinHandle(press, endpoint, zoom1Threshold));
        assertTrue("0.25x 縮小時は画面上同じ距離でもモデル座標では拾えるはず (bug-hunt H)",
                SeqSketchCanvas.withinHandle(press, endpoint, zoom025Threshold));
    }

    // --- (c) ハンドル込み paint が例外を投げない ---------------------------------------

    @Test
    public void paint_withHandlesAndActiveDrag_doesNotThrow() {
        SeqItem selfReply = SeqItem.message("B", SeqItem.Arrow.ASYNC_REPLY, "B", null);
        GuiActionRunner.execute(() -> {
            model.getItems().add(selfReply);
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 400);
        });
        paint(canvas);
        // ドラッグ中 (ラバーバンド + ハンドル強調描画) の状態でも例外を投げないこと。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                CENTER_A, FIRST_ROW_Y, MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, CENTER_C, FIRST_ROW_Y, 0);
        paint(canvas);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, CENTER_C, FIRST_ROW_Y, MouseEvent.BUTTON1);
        paint(canvas);
    }

    private static void paint(SeqSketchCanvas canvas) {
        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(600, 400);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
    }
}
