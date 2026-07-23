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
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchCanvas} の関係線の端点付替え (endpoint reattach) の検証。
 *
 * <p>(a) ドラッグでの繋ぎ替えがモデルの端点を更新し、再生成 PlantUML ({@link
 * SketchPumlCodec#toPuml}) にも反映されること、(b) ノード外でのリリースはキャンセルされ
 * モデルが変わらないこと、(c) 端点ハンドル/ラバーバンド込みの描画が例外を投げないこと、を
 * 実際の press/drag/release 経路 ({@link java.awt.Component#dispatchEvent}) で確認する
 * (SeqSketchCanvasPanGuardTest と同じ手法。Robot は使わない)。</p>
 */
public class SketchCanvasEndpointReattachTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private SketchCanvas.Listener listener() {
        return new SketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editRequested(SketchClass c) {
            }

            @Override public void addClassRequested(Point at) {
            }
        };
    }

    private SketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new SketchCanvas(listener()));
    }

    /** A(60,60) --> B(300,60) の関係 1 本と、離れた位置に付替え先候補 C(60,260) を持つモデル。 */
    private static SketchModel sampleModel() {
        SketchModel model = new SketchModel();
        model.getClasses().add(new SketchClass("A", SketchClass.Kind.CLASS, 60, 60));
        model.getClasses().add(new SketchClass("B", SketchClass.Kind.CLASS, 300, 60));
        model.getClasses().add(new SketchClass("C", SketchClass.Kind.CLASS, 60, 260));
        model.getRelations().add(
                new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "B", null));
        return model;
    }

    private void dispatch(SketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void dragLeftEndpointOntoAnotherNode_reattachesAndRegeneratesPuml() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        SketchClass a = model.getClasses().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);
        SketchClass c = model.getClasses().get(2);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        // 端点ハンドルの掴みはノードドラッグより優先し、旧選択もクリアするはず。
        assertTrue("端点ドラッグが開始しているはず",
                GuiActionRunner.execute(() -> canvas.dragRelationForTest() == rel));
        assertNull("端点ドラッグ開始時はノード選択されないはず",
                GuiActionRunner.execute(canvas::selectedForTest));

        Point insideC = new Point(c.getX() + 10, c.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, insideC, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideC, MouseEvent.BUTTON1);

        assertEquals("C へ繋ぎ替わるはず", "C", rel.getLeft());
        assertEquals("right 側は変わらないはず", "B", rel.getRight());
        assertEquals("繋ぎ替え 1 回ぶんの modelEdited 通知", 1, edits.get());
        assertNull("リリース後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragRelationForTest));
        assertEquals("A ノード自体は動いていないはず", 60, a.getX());
        assertEquals(60, a.getY());

        String puml = SketchPumlCodec.toPuml(model);
        assertTrue("再生成 PlantUML に新しい端点 (C --> B) が反映されるはず",
                puml.contains("C --> B"));
        assertFalse("旧端点 (A --> B) はもう出ないはず", puml.contains("A --> B"));
    }

    @Test
    public void releaseOutsideAnyNode_cancelsReattach() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        Point empty = new Point(550, 380); // どのクラスの矩形にも重ならない空白位置
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, empty, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, empty, MouseEvent.BUTTON1);

        assertEquals("ノード外リリースなので端点は変わらないはず", "A", rel.getLeft());
        assertEquals("B", rel.getRight());
        assertEquals("キャンセル時は modelEdited が飛ばないはず", 0, edits.get());
        assertNull("キャンセル後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragRelationForTest));
    }

    @Test
    public void escapeDuringEndpointDrag_cancelsWithoutReattaching() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
            canvas.requestFocusInWindow();
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        assertTrue(GuiActionRunner.execute(() -> canvas.dragRelationForTest() == rel));

        // 非表示/未フォーカスのコンポーネントへの dispatchEvent は KeyboardFocusManager に
        // 横取りされて届かないため、登録済みリスナーを EDT 上で直接起動する
        // (LayoutFileChooserDialogTest と同じ手法)。
        GuiActionRunner.execute(() -> {
            java.awt.event.KeyEvent ke = new java.awt.event.KeyEvent(
                    canvas, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                    java.awt.event.KeyEvent.VK_ESCAPE, java.awt.event.KeyEvent.CHAR_UNDEFINED);
            for (java.awt.event.KeyListener kl : canvas.getKeyListeners()) {
                kl.keyPressed(ke);
            }
        });

        assertNull("Esc で端点ドラッグは中断されるはず",
                GuiActionRunner.execute(canvas::dragRelationForTest));
        assertEquals("A", rel.getLeft());
        assertEquals(0, edits.get());
    }

    @Test
    public void paintWithHandlesAndDragOverlay_doesNotThrow() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK,
                new Point(200, 200), 0);

        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.paintComponent(g2); // ハンドル + ラバーバンド線込みの描画で例外なし
            } finally {
                g2.dispose();
            }
        });
    }
}
