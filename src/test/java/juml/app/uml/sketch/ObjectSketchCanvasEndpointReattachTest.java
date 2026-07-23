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
 * {@link ObjectSketchCanvas} のリンクの端点付替え (endpoint reattach) の検証。
 *
 * <p>(a) ドラッグでの繋ぎ替えがモデルの端点を更新し、再生成 PlantUML ({@link
 * ObjectSketchCodec#toPuml}) にも反映されること、(b) オブジェクト外でのリリースは
 * キャンセルされモデルが変わらないこと、(c) 端点ハンドル/ラバーバンド込みの描画が例外を
 * 投げないこと、を実際の press/drag/release 経路 ({@link java.awt.Component#dispatchEvent})
 * で確認する。</p>
 */
public class ObjectSketchCanvasEndpointReattachTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private ObjectSketchCanvas.Listener listener() {
        return new ObjectSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editObjectRequested(ObjectInstance o) {
            }

            @Override public void addObjectRequested(Point at) {
            }
        };
    }

    private ObjectSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new ObjectSketchCanvas(listener()));
    }

    /** User(60,60) --> Post(300,60) のリンク 1 本と、付替え先候補 Other(60,260) を持つモデル。 */
    private static ObjectSketchModel sampleModel() {
        ObjectSketchModel model = new ObjectSketchModel();
        model.getObjects().add(new ObjectInstance("User", null, 60, 60));
        model.getObjects().add(new ObjectInstance("Post", null, 300, 60));
        model.getObjects().add(new ObjectInstance("Other", null, 60, 260));
        model.getLinks().add(new ObjectLink("User", ObjectLink.Kind.ARROW, "Post", null));
        return model;
    }

    private void dispatch(ObjectSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void dragLeftEndpointOntoAnotherObject_reattachesAndRegeneratesPuml() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        ObjectInstance user = model.getObjects().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);
        ObjectInstance other = model.getObjects().get(2);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        assertTrue("端点ドラッグが開始しているはず",
                GuiActionRunner.execute(() -> canvas.dragLinkForTest() == link));
        assertNull("端点ドラッグ開始時はノード選択されないはず",
                GuiActionRunner.execute(canvas::selectedForTest));

        Point insideOther = new Point(other.getX() + 10, other.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, insideOther, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideOther, MouseEvent.BUTTON1);

        assertEquals("Other へ繋ぎ替わるはず", "Other", link.getLeft());
        assertEquals("right 側は変わらないはず", "Post", link.getRight());
        assertEquals("繋ぎ替え 1 回ぶんの modelEdited 通知", 1, edits.get());
        assertNull("リリース後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragLinkForTest));
        assertEquals("User ノード自体は動いていないはず", 60, user.getX());
        assertEquals(60, user.getY());

        String puml = ObjectSketchCodec.toPuml(model);
        assertTrue("再生成 PlantUML に新しい端点 (Other --> Post) が反映されるはず",
                puml.contains("Other --> Post"));
        assertFalse("旧端点 (User --> Post) はもう出ないはず", puml.contains("User --> Post"));
    }

    @Test
    public void releaseOutsideAnyObject_cancelsReattach() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        Point empty = new Point(550, 380);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, empty, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, empty, MouseEvent.BUTTON1);

        assertEquals("オブジェクト外リリースなので端点は変わらないはず", "User", link.getLeft());
        assertEquals("Post", link.getRight());
        assertEquals("キャンセル時は modelEdited が飛ばないはず", 0, edits.get());
        assertNull("キャンセル後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragLinkForTest));
    }

    @Test
    public void escapeDuringEndpointDrag_cancelsWithoutReattaching() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
            canvas.requestFocusInWindow();
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        assertTrue(GuiActionRunner.execute(() -> canvas.dragLinkForTest() == link));

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
                GuiActionRunner.execute(canvas::dragLinkForTest));
        assertEquals("User", link.getLeft());
        assertEquals(0, edits.get());
    }

    @Test
    public void paintWithHandlesAndDragOverlay_doesNotThrow() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK,
                new Point(200, 200), 0);

        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
    }
}
