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
 * {@link ErSketchCanvas} のリレーション端点付替え (endpoint reattach) のインタラクション検証。
 *
 * <p>選択/移動モードで端点ハンドルを press → drag → release すると、リリース位置のエンティティ
 * へ端点が付け替わり ({@code setLeft}/{@code setRight})、crow's-foot カーディナリティ記号は
 * 変更されずに保持されたまま再生成 PlantUML に反映されること。ノード外でのリリースはキャンセル
 * (モデル不変) となること、ER 図は既存の 2 クリック追加が自己関連を許すため付替えでも
 * 自己ループを禁止しないこと、ハンドル/ラバーバンド込みの描画が例外を投げないことを固定する。
 * 3 エンティティを同じ列構成・同じ Y 座標に置くことで境界矩形が上下対称になり、端点アンカーが
 * 厳密に右端/左端の中央 (= {@code boundsOf} だけから計算できる点) になるようにしている。</p>
 */
public class ErSketchCanvasReattachTest {

    private final AtomicInteger edits = new AtomicInteger();
    private ErSketchCanvas canvas;
    private ErSketchModel.Entity left;
    private ErSketchModel.Entity right;
    private ErSketchModel.Entity other;
    private ErSketchModel.Relation relation;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        ErSketchCanvas.Listener listener = new ErSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editEntityRequested(ErSketchModel.Entity e) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new ErSketchCanvas(listener));
        ErSketchModel model = new ErSketchModel();
        left = entityWithId("Left", 40, 100);
        right = entityWithId("Right", 320, 100);
        other = entityWithId("Other", 600, 100);
        model.getEntities().add(left);
        model.getEntities().add(right);
        model.getEntities().add(other);
        relation = new ErSketchModel.Relation("Left", ErSketchModel.Cardinality.EXACTLY_ONE,
                ErSketchModel.Cardinality.ZERO_OR_MANY, "Right", null);
        model.getRelations().add(relation);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(900, 400);
        });
    }

    /** 列構成をそろえた (= 同じ高さになる) エンティティを作る。 */
    private static ErSketchModel.Entity entityWithId(String alias, int x, int y) {
        ErSketchModel.Entity e = new ErSketchModel.Entity(alias, null, x, y);
        e.getColumns().add(new ErSketchModel.Column(true, "id", "int"));
        return e;
    }

    /** {@code e} の境界矩形右端中央 (left 側アンカー相当。同一 Y・同一列数なので厳密に一致)。 */
    private Point rightMid(ErSketchModel.Entity e) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(e));
        return new Point(r.x + r.width, r.y + r.height / 2);
    }

    /** {@code e} の境界矩形左端中央 (right 側アンカー相当)。 */
    private Point leftMid(ErSketchModel.Entity e) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(e));
        return new Point(r.x, r.y + r.height / 2);
    }

    private Point centerOf(ErSketchModel.Entity e) {
        Rectangle r = GuiActionRunner.execute(() -> canvas.boundsOf(e));
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
    public void dragEndpointOntoEntity_reattachesAndKeepsCardinality() {
        Point rightHandle = leftMid(right);
        Point target = centerOf(other);
        press(rightHandle);
        drag(target);
        release(target);

        assertEquals("right 側が Other へ付け替わるはず", "Other", relation.getRight());
        assertEquals("left 側は変わらないはず", "Left", relation.getLeft());
        assertEquals("カーディナリティ (左) は保持されるはず",
                ErSketchModel.Cardinality.EXACTLY_ONE, relation.getLeftCard());
        assertEquals("カーディナリティ (右) は保持されるはず",
                ErSketchModel.Cardinality.ZERO_OR_MANY, relation.getRightCard());
        assertEquals("付替えでモデル編集通知が 1 回飛ぶはず", 1, edits.get());

        String puml = GuiActionRunner.execute(() -> ErSketchCodec.toPuml(canvas.model()));
        assertTrue("再生成 PlantUML にカーディナリティ込みで Left ||--o{ Other が反映されるはず",
                puml.contains("Left ||--o{ Other"));
        assertFalse("旧リレーション Left ||--o{ Right は残らないはず",
                puml.contains("Left ||--o{ Right"));
    }

    @Test
    public void releaseOutsideAnyEntity_cancelsReattach() {
        Point rightHandle = leftMid(right);
        Point outside = new Point(2000, 2000);
        press(rightHandle);
        drag(outside);
        release(outside);

        assertEquals("エンティティ外リリースは right を変えないはず", "Right", relation.getRight());
        assertEquals("キャンセルではモデル編集通知は飛ばないはず", 0, edits.get());
    }

    @Test
    public void selfLoopRelease_isAllowedForErDiagram() {
        // ER 図は既存の 2 クリック追加でも自己関連 (left == right) を許すため、
        // 付替えで自己ループになる操作も拒否せず受け入れるはず。
        Point rightHandle = leftMid(right);
        Point ontoLeft = centerOf(left);
        press(rightHandle);
        drag(ontoLeft);
        release(ontoLeft);

        assertEquals("自己ループへの付替えは許可されるはず", "Left", relation.getRight());
        assertEquals("Left", relation.getLeft());
        assertEquals(1, edits.get());
    }

    @Test
    public void pressAwayFromHandle_stillDragsEntityNormally() {
        // ハンドルから十分離れたエンティティ中央を press した場合は、従来どおりエンティティ
        // 移動になること (端点ドラッグの優先判定が既存の移動を壊していないことの回帰確認)。
        Point center = centerOf(left);
        Point moved = new Point(center.x + 5, center.y);
        press(center);
        drag(moved);
        release(moved);

        assertEquals("Left", relation.getLeft());
        assertTrue("通常のエンティティドラッグではモデル編集通知が飛ぶはず", edits.get() >= 1);
    }

    @Test
    public void paintWithHandlesAndRubberBand_doesNotThrow() {
        Point rightHandle = leftMid(right);
        press(rightHandle);
        drag(centerOf(other));

        BufferedImage img = new BufferedImage(900, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });

        release(centerOf(other));
    }

    @Test
    public void reattachForTest_updatesModelThroughRealPath_andRejectsUnknownTarget() {
        boolean ok = GuiActionRunner.execute(() -> canvas.reattachForTest(relation, false, "Other"));
        assertTrue("既存エンティティへの付替えは成功するはず", ok);
        assertEquals("Other", relation.getRight());
        assertEquals(1, edits.get());

        boolean rejected = GuiActionRunner.execute(
                () -> canvas.reattachForTest(relation, false, "NoSuchEntity"));
        assertFalse("存在しないエンティティへの付替えは失敗するはず", rejected);
        assertEquals("失敗時は right が変わらないはず", "Other", relation.getRight());
        assertEquals("失敗時は編集通知が追加で飛ばないはず", 1, edits.get());
    }

    @Test
    public void handleThresholdModel_scalesInverselyWithZoom() {
        assertEquals(8.0, ErSketchCanvas.handleThresholdModel(1.0), 1e-9);
        assertEquals(4.0, ErSketchCanvas.handleThresholdModel(2.0), 1e-9);
        assertEquals(16.0, ErSketchCanvas.handleThresholdModel(0.5), 1e-9);
    }

    @Test
    public void withinHandle_trueInsideRadius_falseOutside() {
        Point handle = new Point(100, 100);
        assertTrue(ErSketchCanvas.withinHandle(new Point(104, 100), handle, 8.0));
        assertTrue("境界ちょうどは含む", ErSketchCanvas.withinHandle(
                new Point(100, 108), handle, 8.0));
        assertFalse(ErSketchCanvas.withinHandle(new Point(109, 100), handle, 8.0));
    }
}
