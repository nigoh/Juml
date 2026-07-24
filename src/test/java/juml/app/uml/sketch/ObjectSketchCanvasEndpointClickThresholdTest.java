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
 * {@link ObjectSketchCanvas} の端点ハンドル「単純クリック (ドラッグ無し)」で reattach が
 * 誤発火しないこと (bug-hunt round2 の issue E) の回帰テスト。{@link
 * SketchCanvasEndpointClickThresholdTest} の Object 版。
 */
public class ObjectSketchCanvasEndpointClickThresholdTest {

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

    /** User(60,60) --> Post(300,60) のリンク 1 本を持つモデル。 */
    private static ObjectSketchModel sampleModel() {
        ObjectSketchModel model = new ObjectSketchModel();
        model.getObjects().add(new ObjectInstance("User", null, 60, 60));
        model.getObjects().add(new ObjectInstance("Post", null, 300, 60));
        model.getLinks().add(new ObjectLink("User", ObjectLink.Kind.ARROW, "Post", null));
        return model;
    }

    private void dispatch(ObjectSketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
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
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        ObjectInstance user = model.getObjects().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);
        Rectangle boundsUser = GuiActionRunner.execute(() -> canvas.boundsOf(user));
        Point pressInside = pointNear(boundsUser, leftAnchor, 0.1);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInside, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, pressInside, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは端点が変わらないはず", "User", link.getLeft());
        assertEquals("クリックでは modelEdited が飛ばないはず", 0, edits.get());
        assertNull("release 後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragLinkForTest));
    }

    @Test
    public void clickWithoutMoving_whenAnotherNodeOverlaps_doesNotSilentlyReattachToIt() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        ObjectInstance user = model.getObjects().get(0);
        ObjectInstance other = new ObjectInstance("Other", null, user.getX(), user.getY());
        model.getObjects().add(other);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);
        Rectangle boundsUser = GuiActionRunner.execute(() -> canvas.boundsOf(user));
        Point pressInside = pointNear(boundsUser, leftAnchor, 0.1);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInside, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, pressInside, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは重なっている Other へ黙って張替わらないはず",
                "User", link.getLeft());
        assertEquals(0, edits.get());
    }

    @Test
    public void dragBeyondThresholdButBackOntoSameNode_isNoOpDueToEqualityGuard() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        ObjectInstance user = model.getObjects().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);
        Rectangle boundsUser = GuiActionRunner.execute(() -> canvas.boundsOf(user));
        Point pressInside = pointNear(boundsUser, leftAnchor, 0.1);
        Point stillInside = pointNear(boundsUser, leftAnchor, 0.5);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInside, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, stillInside, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, stillInside, MouseEvent.BUTTON1);

        assertEquals("移動しても着地先が自ノードなら no-op のはず", "User", link.getLeft());
        assertEquals("等値ガードにより modelEdited は飛ばないはず", 0, edits.get());
    }

    @Test
    public void dragBeyondThresholdOntoAnotherNode_stillReattachesAsBefore() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = sampleModel();
        ObjectLink link = model.getLinks().get(0);
        ObjectInstance user = model.getObjects().get(0);
        ObjectInstance other = new ObjectInstance("Other", null, 60, 260);
        model.getObjects().add(other);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link)[0]);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        Point insideOther = new Point(other.getX() + 10, other.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, insideOther, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideOther, MouseEvent.BUTTON1);

        assertEquals("実ドラッグなら従来どおり Other へ繋ぎ替わるはず", "Other", link.getLeft());
        assertEquals("Post", link.getRight());
        assertEquals(1, edits.get());
        assertEquals("User ノード自体は動いていないはず", 60, user.getX());
    }
}
