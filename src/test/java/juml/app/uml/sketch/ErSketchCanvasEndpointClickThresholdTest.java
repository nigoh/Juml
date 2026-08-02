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
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * {@link ErSketchCanvas} の端点ハンドルしきい値 (ヒット半径のズーム対応) の配線検証。
 *
 * <p>{@code handlePress} は {@code endpointHandleAt} で {@link #handleThresholdModel(double)}
 * (= {@link EndpointHitThreshold#modelRadius} と同じ意味論) を使い、押下点がハンドルから
 * 画面上約 8px 以内かを判定する。純関数自体は {@link ErSketchCanvasReattachTest} で固定済みだが、
 * 実キャンバスの {@code view.zoom()} が正しく {@code handlePress} まで配線されているか
 * (ズーム係数の掛け忘れ等の単純ミス) は未検証だったため、実 press/drag/release ディスパッチで
 * 確認する。{@link SketchCanvasEndpointClickThresholdTest} と同じ検証観点・作法を ER 図へ適用
 * する。ER は {@code setZoomForTest} を持たないため、縮小は実際の Ctrl+ホイールディスパッチ
 * ({@link SketchViewport#onWheel}) で行う。</p>
 */
public class ErSketchCanvasEndpointClickThresholdTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private ErSketchCanvas.Listener listener() {
        return new ErSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editEntityRequested(ErSketchModel.Entity e) {
            }
        };
    }

    private ErSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new ErSketchCanvas(listener()));
    }

    /** 列構成をそろえた (= 同じ高さになる) エンティティを作る。 */
    private static ErSketchModel.Entity entityWithId(String alias, int x, int y) {
        ErSketchModel.Entity e = new ErSketchModel.Entity(alias, null, x, y);
        e.getColumns().add(new ErSketchModel.Column(true, "id", "int"));
        return e;
    }

    /** Left(40,100) --Right(320,100) のリレーション 1 本と、離れた付替え先候補 Other(600,100)。 */
    private static ErSketchModel sampleModel() {
        ErSketchModel model = new ErSketchModel();
        model.getEntities().add(entityWithId("Left", 40, 100));
        model.getEntities().add(entityWithId("Right", 320, 100));
        model.getEntities().add(entityWithId("Other", 600, 100));
        model.getRelations().add(new ErSketchModel.Relation("Left",
                ErSketchModel.Cardinality.EXACTLY_ONE, ErSketchModel.Cardinality.ZERO_OR_MANY,
                "Right", null));
        return model;
    }

    private void dispatch(ErSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    /** {@code e} の境界矩形右端中央 (left 側アンカー相当。同一 Y・同一列数なので厳密に一致)。 */
    private static Point rightMid(ErSketchCanvas canvas, ErSketchModel.Entity e) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(e));
        return new Point(r.x + r.width, r.y + r.height / 2);
    }

    private static Point centerOf(ErSketchCanvas canvas, ErSketchModel.Entity e) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(e));
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** Ctrl+ホイールを繰り返しディスパッチし、実操作の経路で {@link SketchViewport#MIN_ZOOM} まで縮小する。 */
    private static void zoomOutToMinViaWheel(ErSketchCanvas canvas) {
        GuiActionRunner.execute(() -> {
            for (int i = 0; i < 40; i++) {
                canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL,
                        System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, 0, 0, 0, false,
                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1));
            }
        });
    }

    private static Point toScreen(Point modelPoint, double zoom) {
        return new Point((int) Math.round(modelPoint.x * zoom), (int) Math.round(modelPoint.y * zoom));
    }

    @Test
    public void clickWithoutMoving_doesNotReattachOrFireModelEdited() {
        ErSketchCanvas canvas = newCanvas();
        ErSketchModel model = sampleModel();
        ErSketchModel.Relation rel = model.getRelations().get(0);
        ErSketchModel.Entity left = model.getEntities().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(900, 400);
        });
        Point anchor = rightMid(canvas, left);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                anchor, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, anchor, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは端点が変わらないはず", "Left", rel.getLeft());
        assertEquals("クリックでは modelEdited が飛ばないはず", 0, edits.get());
    }

    @Test
    public void pressWithinHandleRadius_atDefaultZoom_startsEndpointDragAndReattaches() {
        ErSketchCanvas canvas = newCanvas();
        ErSketchModel model = sampleModel();
        ErSketchModel.Relation rel = model.getRelations().get(0);
        ErSketchModel.Entity left = model.getEntities().get(0);
        ErSketchModel.Entity other = model.getEntities().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(900, 400);
        });
        Point anchor = rightMid(canvas, left);
        // ハンドル中心から 6px (< 8px しきい値) だけ離れた press。Left/Right いずれの矩形にも
        // 重ならない空白位置になる。
        Point near = new Point(anchor.x + 6, anchor.y);
        Point target = centerOf(canvas, other);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                near, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("しきい値内の press は端点ドラッグを開始し Other へ繋ぎ替わるはず",
                "Other", rel.getLeft());
        assertEquals("Right", rel.getRight());
        assertEquals(1, edits.get());
    }

    @Test
    public void pressBeyondHandleRadius_atDefaultZoom_doesNotStartEndpointDrag() {
        ErSketchCanvas canvas = newCanvas();
        ErSketchModel model = sampleModel();
        ErSketchModel.Relation rel = model.getRelations().get(0);
        ErSketchModel.Entity left = model.getEntities().get(0);
        ErSketchModel.Entity other = model.getEntities().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(900, 400);
        });
        Point anchor = rightMid(canvas, left);
        // ハンドル中心から 20px (> 8px しきい値) 離れた press。依然どちらの矩形にも重ならない。
        Point far = new Point(anchor.x + 20, anchor.y);
        Point target = centerOf(canvas, other);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                far, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, target, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, target, MouseEvent.BUTTON1);

        assertEquals("しきい値外の press は端点ドラッグを開始しないはず", "Left", rel.getLeft());
        assertEquals("Right", rel.getRight());
        assertEquals("エンティティにも重ならないので modelEdited も飛ばないはず", 0, edits.get());
    }

    // --- bug-hunt round3 指摘 H 相当: 縮小ズームでも端点ハンドルが画面上一定 px で掴めるはず ---

    @Test
    public void zoomedOutViaRealWheel_pressAtSameScreenDistanceBeyondModelRadius_stillReattaches() {
        ErSketchCanvas canvas = newCanvas();
        ErSketchModel model = sampleModel();
        ErSketchModel.Relation rel = model.getRelations().get(0);
        ErSketchModel.Entity left = model.getEntities().get(0);
        ErSketchModel.Entity other = model.getEntities().get(2);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(900, 400);
        });
        Point anchor = rightMid(canvas, left);
        // 等倍では 20px > 8px のしきい値なので掴めない (前テストで確認済み)。
        Point farInModel = new Point(anchor.x + 20, anchor.y);
        Point targetInModel = centerOf(canvas, other);

        // 実際の Ctrl+ホイール操作で MIN_ZOOM (0.25x) まで縮小する (setZoomForTest 非公開のため)。
        zoomOutToMinViaWheel(canvas);
        double zoom = SketchViewport.MIN_ZOOM;

        Point pressAtMinZoom = toScreen(farInModel, zoom);
        Point dragAtMinZoom = toScreen(targetInModel, zoom);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressAtMinZoom, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, dragAtMinZoom, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, dragAtMinZoom, MouseEvent.BUTTON1);

        assertEquals("0.25x (MIN_ZOOM) では画面上同じ距離でも掴めて Other へ繋ぎ替わるはず"
                        + " (bug-hunt round3 H 相当)", "Other", rel.getLeft());
        assertEquals("Right", rel.getRight());
        assertEquals(1, edits.get());
    }
}
