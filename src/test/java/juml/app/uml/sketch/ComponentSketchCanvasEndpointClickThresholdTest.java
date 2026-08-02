// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * {@link ComponentSketchCanvas} の端点ハンドルしきい値 (ヒット半径のズーム対応) の配線検証。
 *
 * <p>{@code handlePress} は {@code endpointHandleAt} で {@link #handleThresholdModel(double)}
 * (= {@link EndpointHitThreshold#modelRadius} と同じ意味論) を使い、押下点がハンドルから
 * 画面上約 8px 以内かを判定する。純関数自体は {@link ComponentSketchCanvasReattachTest} で
 * 固定済みだが、実キャンバスの {@code view.zoom()} が正しく {@code handlePress} まで配線されて
 * いるか (ズーム係数の掛け忘れ等の単純ミス) は未検証だったため、実 press/drag/release
 * ディスパッチで確認する。{@link SketchCanvasEndpointClickThresholdTest} と同じ検証観点・
 * 作法を Component 図へ適用する。</p>
 */
public class ComponentSketchCanvasEndpointClickThresholdTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private ComponentSketchCanvas.Listener listener() {
        return new ComponentSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(ComponentNode n) {
            }
        };
    }

    private ComponentSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new ComponentSketchCanvas(listener()));
    }

    /** A(40,100) --> B(300,100) の関係 1 本と、離れた付替え先候補 C(560,100) を持つモデル。 */
    private static ComponentSketchModel sampleModel() {
        ComponentSketchModel model = new ComponentSketchModel();
        model.getNodes().add(new ComponentNode(ComponentNode.Kind.COMPONENT, "A", null, 40, 100));
        model.getNodes().add(new ComponentNode(ComponentNode.Kind.COMPONENT, "B", null, 300, 100));
        model.getNodes().add(new ComponentNode(ComponentNode.Kind.COMPONENT, "C", null, 560, 100));
        model.getRelations().add(
                new ComponentRelation("A", ComponentRelation.Kind.ARROW, "B", null));
        return model;
    }

    private void dispatch(ComponentSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    /** {@code n} の境界矩形右端中央 (from 側アンカー相当。同一 Y 配置なので厳密に一致)。 */
    private static Point rightMid(ComponentSketchCanvas canvas, ComponentNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(n));
        return new Point(r.x + r.width, r.y + r.height / 2);
    }

    private static Point centerOf(ComponentSketchCanvas canvas, ComponentNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(n));
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private static Point toScreen(Point modelPoint, double zoom) {
        return new Point((int) Math.round(modelPoint.x * zoom), (int) Math.round(modelPoint.y * zoom));
    }

    @Test
    public void clickWithoutMoving_doesNotReattachOrFireModelEdited() {
        ComponentSketchCanvas canvas = newCanvas();
        ComponentSketchModel model = sampleModel();
        ComponentRelation rel = model.getRelations().get(0);
        ComponentNode a = model.getNodes().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(800, 400);
        });
        Point anchor = rightMid(canvas, a);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                anchor, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, anchor, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは端点が変わらないはず", "A", rel.getFrom());
        assertEquals("クリックでは modelEdited が飛ばないはず", 0, edits.get());
    }

    @Test
    public void pressWithinHandleRadius_atDefaultZoom_startsEndpointDragAndReattaches() {
        ComponentSketchCanvas canvas = newCanvas();
        ComponentSketchModel model = sampleModel();
        ComponentRelation rel = model.getRelations().get(0);
        ComponentNode a = model.getNodes().get(0);
        ComponentNode c = model.getNodes().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(800, 400);
        });
        Point anchor = rightMid(canvas, a);
        // ハンドル中心から 6px (< 8px しきい値) だけ離れた press。A/B いずれの矩形にも重ならない
        // 空白位置 (A の右端の外、B の手前) になる。
        Point near = new Point(anchor.x + 6, anchor.y);
        Point target = centerOf(canvas, c);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                near, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("しきい値内の press は端点ドラッグを開始し C へ繋ぎ替わるはず", "C", rel.getFrom());
        assertEquals("B", rel.getTo());
        assertEquals(1, edits.get());
    }

    @Test
    public void pressBeyondHandleRadius_atDefaultZoom_doesNotStartEndpointDrag() {
        ComponentSketchCanvas canvas = newCanvas();
        ComponentSketchModel model = sampleModel();
        ComponentRelation rel = model.getRelations().get(0);
        ComponentNode a = model.getNodes().get(0);
        ComponentNode c = model.getNodes().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(800, 400);
        });
        Point anchor = rightMid(canvas, a);
        // ハンドル中心から 20px (> 8px しきい値) 離れた press。依然 A/B どちらの矩形にも重ならない。
        Point far = new Point(anchor.x + 20, anchor.y);
        Point target = centerOf(canvas, c);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                far, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("しきい値外の press は端点ドラッグを開始しないはず", "A", rel.getFrom());
        assertEquals("B", rel.getTo());
        assertEquals("ノードにも重ならないので modelEdited も飛ばないはず", 0, edits.get());
    }

    // --- bug-hunt round3 指摘 H 相当: 縮小ズームでも端点ハンドルが画面上一定 px で掴めるはず ---

    @Test
    public void zoomedOut_pressAtSameScreenDistanceBeyondModelRadius_stillReattaches() {
        ComponentSketchCanvas canvas = newCanvas();
        ComponentSketchModel model = sampleModel();
        ComponentRelation rel = model.getRelations().get(0);
        ComponentNode a = model.getNodes().get(0);
        ComponentNode c = model.getNodes().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(800, 400);
        });
        Point anchor = rightMid(canvas, a);
        // 等倍では 20px > 8px のしきい値なので掴めない (前テストで確認済み)。
        Point farInModel = new Point(anchor.x + 20, anchor.y);
        Point targetInModel = centerOf(canvas, c);

        GuiActionRunner.execute(() -> canvas.setZoomForTest(SketchViewport.MIN_ZOOM));
        double zoom = SketchViewport.MIN_ZOOM;

        Point pressAtMinZoom = toScreen(farInModel, zoom);
        Point dragAtMinZoom = toScreen(targetInModel, zoom);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressAtMinZoom, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, dragAtMinZoom, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, dragAtMinZoom, MouseEvent.BUTTON1);

        assertEquals("0.25x (MIN_ZOOM) では画面上同じ距離でも掴めて C へ繋ぎ替わるはず"
                        + " (bug-hunt round3 H 相当)", "C", rel.getFrom());
        assertEquals("B", rel.getTo());
        assertEquals(1, edits.get());
    }
}
