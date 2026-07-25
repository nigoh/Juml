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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * {@link DeploySketchCanvas} の端点ハンドルしきい値 (ヒット半径のズーム対応) の配線検証。
 *
 * <p>{@code handlePress} は {@link DeploySketchLinkHandles#hitTest} 経由で {@link
 * EndpointHitThreshold#modelRadius} を使い、押下点がハンドルから画面上約 8px 以内かを判定する。
 * 純関数自体は {@link DeploySketchLinkHandlesZoomTest} で固定済みだが、実キャンバスの
 * {@code view.zoom()} が正しく {@code handlePress} まで配線されているか (ズーム係数の掛け忘れ
 * 等の単純ミス) は未検証だったため、実 press/drag/release ディスパッチで確認する。{@link
 * SketchCanvasEndpointClickThresholdTest} と同じ検証観点・作法を配置図へ適用する。Deploy は
 * {@code layoutForTest}/{@code endpointDragLinkForTest}/{@code setZoomForTest} を持つため、
 * 他 4 キャンバスより厳密なドラッグ中状態の直接確認も併せて行う。</p>
 */
public class DeploySketchCanvasEndpointClickThresholdTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private DeploySketchCanvas.Listener listener() {
        return new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(DeployNode n) {
            }
        };
    }

    private DeploySketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new DeploySketchCanvas(listener()));
    }

    /** A(60,60) --> B(300,60) のリンク 1 本と、離れた付替え先候補 C(60,260) を持つモデル。 */
    private static DeploySketchModel sampleModel() {
        DeploySketchModel model = new DeploySketchModel();
        model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, "A", null, 60, 60));
        model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, "B", null, 300, 60));
        model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, "C", null, 60, 260));
        model.getLinks().add(new DeployLink("A", DeployLink.Kind.ARROW, "B", null));
        return model;
    }

    private void dispatch(DeploySketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    /** リンクの from 側アンカー (絶対座標。{@link DeploySketchLinkHandles} と同じ幾何)。 */
    private static Point fromAnchor(DeploySketchCanvas canvas, DeploySketchModel model, DeployLink link) {
        Map<DeployNode, Rectangle> layout = GuiActionRunner.execute(canvas::layoutForTest);
        return DeploySketchLinkHandles.endpointsOf(model, link, layout)[0];
    }

    private static Point centerOf(DeploySketchCanvas canvas, DeployNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.layoutForTest().get(n));
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private static Point toScreen(Point modelPoint, double zoom) {
        return new Point((int) Math.round(modelPoint.x * zoom), (int) Math.round(modelPoint.y * zoom));
    }

    @Test
    public void clickWithoutMoving_doesNotReattachOrFireModelEdited() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = sampleModel();
        DeployLink link = model.getLinks().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point anchor = fromAnchor(canvas, model, link);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                anchor, MouseEvent.BUTTON1);
        assertSame("しきい値内 press (アンカーそのもの) は端点ドラッグを開始するはず",
                link, GuiActionRunner.execute(canvas::endpointDragLinkForTest));
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, anchor, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは端点が変わらないはず", "A", link.getFrom());
        assertEquals("クリックでは modelEdited が飛ばないはず", 0, edits.get());
        assertNull("release 後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));
    }

    @Test
    public void pressWithinHandleRadius_atDefaultZoom_startsEndpointDragAndReattaches() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = sampleModel();
        DeployLink link = model.getLinks().get(0);
        DeployNode c = model.getNodes().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point anchor = fromAnchor(canvas, model, link);
        // ハンドル中心から 6px (< 8px しきい値) だけ離れた press。A/B いずれの矩形にも重ならない
        // 空白位置 (A の右端の外、B の手前) になる。
        Point near = new Point(anchor.x + 6, anchor.y);
        Point target = centerOf(canvas, c);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                near, MouseEvent.BUTTON1);
        assertSame("しきい値内の press は端点ドラッグを開始するはず",
                link, GuiActionRunner.execute(canvas::endpointDragLinkForTest));
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("C へ繋ぎ替わるはず", "C", link.getFrom());
        assertEquals("B", link.getTo());
        assertEquals(1, edits.get());
    }

    @Test
    public void pressBeyondHandleRadius_atDefaultZoom_doesNotStartEndpointDrag() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = sampleModel();
        DeployLink link = model.getLinks().get(0);
        DeployNode c = model.getNodes().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point anchor = fromAnchor(canvas, model, link);
        // ハンドル中心から 20px (> 8px しきい値) 離れた press。依然 A/B どちらの矩形にも重ならない。
        Point far = new Point(anchor.x + 20, anchor.y);
        Point target = centerOf(canvas, c);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                far, MouseEvent.BUTTON1);
        assertNull("しきい値外の press は端点ドラッグを開始しないはず",
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("A", link.getFrom());
        assertEquals("B", link.getTo());
        assertEquals("ノードにも重ならないので modelEdited も飛ばないはず", 0, edits.get());
    }

    // --- bug-hunt round4 指摘 K 相当: 縮小ズームでも端点ハンドルが画面上一定 px で掴めるはず ---

    @Test
    public void zoomedOut_pressAtSameScreenDistanceBeyondModelRadius_stillReattaches() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = sampleModel();
        DeployLink link = model.getLinks().get(0);
        DeployNode c = model.getNodes().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point anchor = fromAnchor(canvas, model, link);
        // 等倍では 20px > 8px のしきい値なので掴めない (前テストで確認済み)。
        Point farInModel = new Point(anchor.x + 20, anchor.y);
        Point targetInModel = centerOf(canvas, c);

        GuiActionRunner.execute(() -> canvas.setZoomForTest(SketchViewport.MIN_ZOOM));
        double zoom = SketchViewport.MIN_ZOOM;

        Point pressAtMinZoom = toScreen(farInModel, zoom);
        Point dragAtMinZoom = toScreen(targetInModel, zoom);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressAtMinZoom, MouseEvent.BUTTON1);
        assertSame("0.25x (MIN_ZOOM) では画面上同じ距離でも端点ドラッグを開始できるはず"
                        + " (bug-hunt round4 K 相当)",
                link, GuiActionRunner.execute(canvas::endpointDragLinkForTest));
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, dragAtMinZoom, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, dragAtMinZoom, MouseEvent.BUTTON1);

        assertEquals("C へ繋ぎ替わるはず", "C", link.getFrom());
        assertEquals("B", link.getTo());
        assertEquals(1, edits.get());
    }
}
