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
 * {@link SketchCanvas} の端点ハンドル「単純クリック (ドラッグ無し)」で reattach が
 * 誤発火しないこと (bug-hunt round2 の issue E) の回帰テスト。
 *
 * <p>端点アンカーはノード境界上にあり、ハンドル自体はノード内側に数px 重なる
 * (矩形の当たり判定は右/下辺が exclusive なので、境界のごく内側は当該ノードの
 * 領域として扱われる) ため、ハンドルを掴んで動かさずに離すだけでも旧実装では
 * (a) 自ノードへの no-op reattach、(b) ノードが重なっている場合は最前面の別
 * ノードへの黙った張替え、が modelEdited 付きで発火してしまっていた。
 * {@link EndpointDragSession} の移動しきい値 + 等値ガードで両方を防ぐ。</p>
 */
public class SketchCanvasEndpointClickThresholdTest {

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

    /** A(60,60) --> B(300,60) の関係 1 本を持つモデル。 */
    private static SketchModel sampleModel() {
        SketchModel model = new SketchModel();
        model.getClasses().add(new SketchClass("A", SketchClass.Kind.CLASS, 60, 60));
        model.getClasses().add(new SketchClass("B", SketchClass.Kind.CLASS, 300, 60));
        model.getRelations().add(
                new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "B", null));
        return model;
    }

    private void dispatch(SketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    /** アンカーから矩形中心へ {@code frac} だけ寄せた点 (境界のごく内側を作るための純計算)。 */
    private static Point pointNear(Rectangle r, Point anchor, double frac) {
        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        int x = anchor.x + (int) Math.round((cx - anchor.x) * frac);
        int y = anchor.y + (int) Math.round((cy - anchor.y) * frac);
        return new Point(x, y);
    }

    @Test
    public void clickWithoutMoving_doesNotReattachOrFireModelEdited() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        SketchClass a = model.getClasses().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);
        Rectangle boundsA = GuiActionRunner.execute(() -> canvas.boundsOf(a));
        // 境界のごく内側 (数px) を press/release 位置にする: ハンドルの視覚上の重なりを再現する。
        Point pressInsideA = pointNear(boundsA, leftAnchor, 0.1);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInsideA, MouseEvent.BUTTON1);
        // ドラッグ無しでそのまま release (= クリック相当)。
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, pressInsideA, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは端点が変わらないはず", "A", rel.getLeft());
        assertEquals("クリックでは modelEdited が飛ばないはず", 0, edits.get());
        assertNull("release 後は端点ドラッグ状態が解除されるはず",
                GuiActionRunner.execute(canvas::dragRelationForTest));
    }

    @Test
    public void clickWithoutMoving_whenAnotherNodeOverlaps_doesNotSilentlyReattachToIt() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        SketchClass a = model.getClasses().get(0);
        // C を A と全く同じ位置に、A より後ろへ追加する (同一座標なので bounds も同一になり、
        // classAt の「重なりは後に描いたもの優先」ロジックで C が最前面としてヒットする)。
        SketchClass c = new SketchClass("C", SketchClass.Kind.CLASS, a.getX(), a.getY());
        model.getClasses().add(c);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);
        Rectangle boundsA = GuiActionRunner.execute(() -> canvas.boundsOf(a));
        Point pressInsideA = pointNear(boundsA, leftAnchor, 0.1);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInsideA, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, pressInsideA, MouseEvent.BUTTON1);

        assertEquals("クリックだけでは重なっている C へ黙って張替わらないはず", "A", rel.getLeft());
        assertEquals(0, edits.get());
    }

    @Test
    public void dragBeyondThresholdButBackOntoSameNode_isNoOpDueToEqualityGuard() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        SketchClass a = model.getClasses().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);
        Rectangle boundsA = GuiActionRunner.execute(() -> canvas.boundsOf(a));
        Point pressInsideA = pointNear(boundsA, leftAnchor, 0.1);
        // クリックしきい値 (4px) を超える移動だが、着地点はやはり A の内部 (= 現在の左端と同一)。
        Point stillInsideA = pointNear(boundsA, leftAnchor, 0.5);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                pressInsideA, MouseEvent.BUTTON1);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK,
                stillInsideA, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, stillInsideA, MouseEvent.BUTTON1);

        assertEquals("移動しても着地先が自ノードなら no-op のはず", "A", rel.getLeft());
        assertEquals("等値ガードにより modelEdited は飛ばないはず", 0, edits.get());
    }

    @Test
    public void dragBeyondThresholdOntoAnotherNode_stillReattachesAsBefore() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        SketchClass a = model.getClasses().get(0);
        SketchClass c = new SketchClass("C", SketchClass.Kind.CLASS, 60, 260);
        model.getClasses().add(c);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        Point insideC = new Point(c.getX() + 10, c.getY() + 10);
        dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, insideC, 0);
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideC, MouseEvent.BUTTON1);

        assertEquals("実ドラッグなら従来どおり C へ繋ぎ替わるはず", "C", rel.getLeft());
        assertEquals("B", rel.getRight());
        assertEquals(1, edits.get());
        assertEquals("A ノード自体は動いていないはず", 60, a.getX());
    }
}
