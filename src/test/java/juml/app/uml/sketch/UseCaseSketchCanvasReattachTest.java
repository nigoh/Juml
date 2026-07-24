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
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link UseCaseSketchCanvas} の関係線端点付替え (endpoint reattach) のインタラクション検証。
 *
 * <p>選択/移動モードで端点ハンドルを press → drag → release すると、リリース位置のノードへ
 * 端点が付け替わり ({@code setTo}/{@code setFrom})、モデル編集通知が飛んで再生成 PlantUML
 * にも反映されること。ノード外でのリリースはキャンセル (モデル不変) となること、
 * ハンドル/ラバーバンド込みの描画が例外を投げないことも固定する。3 体のアクターを同じ Y
 * 座標に置くことで境界矩形が上下対称になり、端点アンカーが厳密に右端/左端の中央 (=
 * {@code boundsOf} だけから計算できる点) になるようにしている。</p>
 */
public class UseCaseSketchCanvasReattachTest {

    private final AtomicInteger edits = new AtomicInteger();
    private UseCaseSketchCanvas canvas;
    private UseCaseNode actorA;
    private UseCaseNode actorB;
    private UseCaseNode actorC;
    private UseCaseRelation relation;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        UseCaseSketchCanvas.Listener listener = new UseCaseSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(UseCaseNode n) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new UseCaseSketchCanvas(listener));
        UseCaseSketchModel model = new UseCaseSketchModel();
        actorA = new UseCaseNode(UseCaseNode.Kind.ACTOR, "A", null, 40, 100);
        actorB = new UseCaseNode(UseCaseNode.Kind.ACTOR, "B", null, 300, 100);
        actorC = new UseCaseNode(UseCaseNode.Kind.ACTOR, "C", null, 560, 100);
        model.getNodes().add(actorA);
        model.getNodes().add(actorB);
        model.getNodes().add(actorC);
        relation = new UseCaseRelation("A", UseCaseRelation.Kind.ASSOCIATION, "B", null);
        model.getRelations().add(relation);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(800, 400);
        });
    }

    /** {@code n} の境界矩形右端中央 (from 側アンカー相当。同一 Y 配置なので厳密に一致)。 */
    private Point rightMid(UseCaseNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(n));
        return new Point(r.x + r.width, r.y + r.height / 2);
    }

    /** {@code n} の境界矩形左端中央 (to 側アンカー相当)。 */
    private Point leftMid(UseCaseNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(n));
        return new Point(r.x, r.y + r.height / 2);
    }

    private Point centerOf(UseCaseNode n) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(n));
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private void dispatch(int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    private void press(Point p) {
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, p, MouseEvent.BUTTON1);
    }

    private void drag(Point p) {
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, p, 0);
    }

    private void release(Point p) {
        dispatch(MouseEvent.MOUSE_RELEASED, 0, p, MouseEvent.BUTTON1);
    }

    @Test
    public void dragEndpointOntoNode_reattachesAndRegeneratesPuml() {
        Point toHandle = leftMid(actorB);
        Point target = centerOf(actorC);
        press(toHandle);
        drag(target);
        release(target);

        assertEquals("to 側が C へ付け替わるはず", "C", relation.getTo());
        assertEquals("from 側は変わらないはず", "A", relation.getFrom());
        assertEquals("付替えでモデル編集通知が 1 回飛ぶはず", 1, edits.get());

        String puml = GuiActionRunner.execute(() -> UseCaseSketchCodec.toPuml(canvas.model()));
        assertTrue("再生成 PlantUML に A --> C が反映されるはず", puml.contains("A --> C"));
        assertFalse("旧関係 A --> B は残らないはず", puml.contains("A --> B"));
    }

    @Test
    public void releaseOutsideAnyNode_cancelsReattach() {
        Point toHandle = leftMid(actorB);
        Point outside = new Point(2000, 2000);
        press(toHandle);
        drag(outside);
        release(outside);

        assertEquals("ノード外リリースは to を変えないはず", "B", relation.getTo());
        assertEquals("キャンセルではモデル編集通知は飛ばないはず", 0, edits.get());
    }

    @Test
    public void selfLoopRelease_isRejectedForUseCaseDiagram() {
        // from 側ハンドルを、既に to 側である B の上へドロップ = A==B の自己ループになる操作。
        // ユースケース図は自己ループの新規作成自体が未対応のため、付替えでも拒否されるはず。
        Point fromHandle = rightMid(actorA);
        Point ontoB = centerOf(actorB);
        press(fromHandle);
        drag(ontoB);
        release(ontoB);

        assertEquals("自己ループになる付替えは拒否されるはず", "A", relation.getFrom());
        assertEquals(0, edits.get());
    }

    @Test
    public void pressAwayFromHandle_stillDragsNodeNormally() {
        // ハンドルから十分離れたノード中央を press した場合は、従来どおりノード移動になること
        // (端点ドラッグの優先判定がノード移動を壊していないことの回帰確認)。
        Point center = centerOf(actorA);
        Point moved = new Point(center.x + 5, center.y);
        press(center);
        drag(moved);
        release(moved);

        assertEquals("A", relation.getFrom());
        assertTrue("通常のノードドラッグではモデル編集通知が飛ぶはず", edits.get() >= 1);
    }

    @Test
    public void paintWithHandlesAndRubberBand_doesNotThrow() {
        Point toHandle = leftMid(actorB);
        press(toHandle);
        drag(centerOf(actorC));

        BufferedImage img = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });

        release(centerOf(actorC));
    }

    @Test
    public void reattachForTest_updatesModelThroughRealPath_andRejectsUnknownTarget() {
        boolean ok = GuiActionRunner.execute(() -> canvas.reattachForTest(relation, true, "C"));
        assertTrue("既存ノードへの付替えは成功するはず", ok);
        assertEquals("C", relation.getFrom());
        assertEquals(1, edits.get());

        boolean rejected = GuiActionRunner.execute(
                () -> canvas.reattachForTest(relation, true, "NoSuchNode"));
        assertFalse("存在しないノードへの付替えは失敗するはず", rejected);
        assertEquals("失敗時は from が変わらないはず", "C", relation.getFrom());
        assertEquals("失敗時は編集通知が追加で飛ばないはず", 1, edits.get());
    }

    @Test
    public void handleThresholdModel_scalesInverselyWithZoom() {
        assertEquals(8.0, UseCaseSketchCanvas.handleThresholdModel(1.0), 1e-9);
        assertEquals(4.0, UseCaseSketchCanvas.handleThresholdModel(2.0), 1e-9);
        assertEquals(16.0, UseCaseSketchCanvas.handleThresholdModel(0.5), 1e-9);
    }

    @Test
    public void withinHandle_trueInsideRadius_falseOutside() {
        Point handle = new Point(100, 100);
        assertTrue(UseCaseSketchCanvas.withinHandle(new Point(104, 100), handle, 8.0));
        assertTrue("境界ちょうどは含む", UseCaseSketchCanvas.withinHandle(
                new Point(100, 108), handle, 8.0));
        assertFalse(UseCaseSketchCanvas.withinHandle(new Point(109, 100), handle, 8.0));
    }
}
