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
 * {@link StateSketchCanvas} の遷移の端点付替え (endpoint reattach) の検証。
 *
 * <p>(a) ドラッグでの繋ぎ替えがモデルの端点を更新し、再生成 PlantUML ({@link
 * StateSketchCodec#toPuml}) にも反映されること、(b) 状態外でのリリースはキャンセルされ
 * モデルが変わらないこと、(c) 端点ハンドル/ラバーバンド込みの描画が例外を投げないこと、を
 * 実際の press/drag/release 経路 ({@link java.awt.Component#dispatchEvent}) で確認する。</p>
 */
public class StateSketchCanvasEndpointReattachTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private StateSketchCanvas.Listener listener() {
        return new StateSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editStateRequested(StateNode s) {
            }
        };
    }

    private StateSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new StateSketchCanvas(listener()));
    }

    /** S1(60,60) --> S2(300,60) の遷移 1 本と、付替え先候補 S3(60,260) を持つモデル。 */
    private static StateSketchModel sampleModel() {
        StateSketchModel model = new StateSketchModel();
        model.getStates().add(new StateNode("S1", 60, 60));
        model.getStates().add(new StateNode("S2", 300, 60));
        model.getStates().add(new StateNode("S3", 60, 260));
        model.getTransitions().add(new StateTransition("S1", "S2", null));
        return model;
    }

    private void dispatch(StateSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void dragFromEndpointOntoAnotherState_reattachesAndRegeneratesPuml() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        StateNode s1 = model.getStates().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);
        StateNode s3 = model.getStates().get(2);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromAnchor, MouseEvent.BUTTON1);
        assertTrue("端点ドラッグが開始しているはず",
                GuiActionRunner.execute(() -> canvas.dragTransitionForTest() == t));
        assertNull("端点ドラッグ開始時はノード選択されないはず",
                GuiActionRunner.execute(canvas::selectedForTest));

        Point insideS3 = new Point(s3.getX() + 10, s3.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, insideS3, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideS3, MouseEvent.BUTTON1);

        assertEquals("S3 へ繋ぎ替わるはず", "S3", t.getFrom());
        assertEquals("to 側は変わらないはず", "S2", t.getTo());
        assertEquals("繋ぎ替え 1 回ぶんの modelEdited 通知", 1, edits.get());
        assertNull("リリース後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragTransitionForTest));
        assertEquals("S1 ノード自体は動いていないはず", 60, s1.getX());
        assertEquals(60, s1.getY());

        String puml = StateSketchCodec.toPuml(model);
        assertTrue("再生成 PlantUML に新しい端点 (S3 --> S2) が反映されるはず",
                puml.contains("S3 --> S2"));
        assertFalse("旧端点 (S1 --> S2) はもう出ないはず", puml.contains("S1 --> S2"));
    }

    @Test
    public void releaseOutsideAnyState_cancelsReattach() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromAnchor, MouseEvent.BUTTON1);
        Point empty = new Point(550, 380);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, empty, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, empty, MouseEvent.BUTTON1);

        assertEquals("状態外リリースなので端点は変わらないはず", "S1", t.getFrom());
        assertEquals("S2", t.getTo());
        assertEquals("キャンセル時は modelEdited が飛ばないはず", 0, edits.get());
        assertNull("キャンセル後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragTransitionForTest));
    }

    @Test
    public void escapeDuringEndpointDrag_cancelsWithoutReattaching() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
            canvas.requestFocusInWindow();
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromAnchor, MouseEvent.BUTTON1);
        assertTrue(GuiActionRunner.execute(() -> canvas.dragTransitionForTest() == t));

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
                GuiActionRunner.execute(canvas::dragTransitionForTest));
        assertEquals("S1", t.getFrom());
        assertEquals(0, edits.get());
    }

    @Test
    public void pseudoStateEndpoint_hasNoDraggableAnchor() {
        // 初期擬似状態 ([*] --> S1) は擬似状態側の端点ハンドルを持たない (ドラッグ対象外)。
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition initial = new StateTransition(StateTransition.PSEUDO, "S1", null);
        model.getTransitions().add(initial);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point[] anchors = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(initial));
        assertNull("擬似状態を含む遷移は端点ドラッグ対象外のはず", anchors);
    }

    @Test
    public void paintWithHandlesAndDragOverlay_doesNotThrow() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromAnchor, MouseEvent.BUTTON1);
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
