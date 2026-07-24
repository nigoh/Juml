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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link StateSketchCanvas} の端点ハンドル「単純クリック (ドラッグ無し)」で reattach が
 * 誤発火しないこと (bug-hunt round2 の issue E) の回帰テスト。{@link
 * SketchCanvasEndpointClickThresholdTest} の State 版。
 */
public class StateSketchCanvasEndpointClickThresholdTest {

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

    /** S1(60,60) --> S2(300,60) の遷移 1 本を持つモデル。 */
    private static StateSketchModel sampleModel() {
        StateSketchModel model = new StateSketchModel();
        model.getStates().add(new StateNode("S1", 60, 60));
        model.getStates().add(new StateNode("S2", 300, 60));
        model.getTransitions().add(new StateTransition("S1", "S2", null));
        return model;
    }

    private void dispatch(StateSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    private static Point pointNear(Rectangle r, Point anchor, double frac) {
        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        int x = anchor.x + (int) Math.round((cx - anchor.x) * frac);
        int y = anchor.y + (int) Math.round((cy - anchor.y) * frac);
        return new Point(x, y);
    }

    @Test
    public void clickWithoutMoving_doesNotReattachOrFireModelEdited() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        StateNode s1 = model.getStates().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);
        Rectangle boundsS1 = GuiActionRunner.execute(() -> canvas.boundsOf(s1));
        Point pressInside = pointNear(boundsS1, fromAnchor, 0.1);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInside, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, pressInside, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは端点が変わらないはず", "S1", t.getFrom());
        assertEquals("クリックでは modelEdited が飛ばないはず", 0, edits.get());
        assertNull("release 後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragTransitionForTest));
    }

    @Test
    public void clickWithoutMoving_whenAnotherNodeOverlaps_doesNotSilentlyReattachToIt() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        StateNode s1 = model.getStates().get(0);
        StateNode overlap = new StateNode("S3", s1.getX(), s1.getY());
        model.getStates().add(overlap);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);
        Rectangle boundsS1 = GuiActionRunner.execute(() -> canvas.boundsOf(s1));
        Point pressInside = pointNear(boundsS1, fromAnchor, 0.1);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInside, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, pressInside, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは重なっている S3 へ黙って張替わらないはず", "S1", t.getFrom());
        assertEquals(0, edits.get());
    }

    @Test
    public void dragBeyondThresholdButBackOntoSameNode_isNoOpDueToEqualityGuard() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        StateNode s1 = model.getStates().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);
        Rectangle boundsS1 = GuiActionRunner.execute(() -> canvas.boundsOf(s1));
        Point pressInside = pointNear(boundsS1, fromAnchor, 0.1);
        Point stillInside = pointNear(boundsS1, fromAnchor, 0.5);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInside, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, stillInside, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, stillInside, MouseEvent.BUTTON1);

        assertEquals("移動しても着地先が自ノードなら no-op のはず", "S1", t.getFrom());
        assertEquals("等値ガードにより modelEdited は飛ばないはず", 0, edits.get());
    }

    @Test
    public void dragBeyondThresholdOntoAnotherNode_stillReattachesAsBefore() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = sampleModel();
        StateTransition t = model.getTransitions().get(0);
        StateNode s1 = model.getStates().get(0);
        StateNode s3 = new StateNode("S3", 60, 260);
        model.getStates().add(s3);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point fromAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t)[0]);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                fromAnchor, MouseEvent.BUTTON1);
        Point insideS3 = new Point(s3.getX() + 10, s3.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, insideS3, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideS3, MouseEvent.BUTTON1);

        assertEquals("実ドラッグなら従来どおり S3 へ繋ぎ替わるはず", "S3", t.getFrom());
        assertEquals("S2", t.getTo());
        assertEquals(1, edits.get());
        assertEquals("S1 ノード自体は動いていないはず", 60, s1.getX());
    }
}
