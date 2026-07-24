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

/**
 * {@link SketchCanvas} で端点ドラッグ中に中ボタン (パン) をリリースしても繋ぎ替えが
 * 誤って確定しないことを検証する (bug-hunt round5 論点3、全 8 キャンバス共通)。
 *
 * <p>{@code handlePress} は中ボタンを早期 return するが、進行中の端点ドラッグを中断しない。
 * 一方 {@code handleRelease} 先頭の確定分岐は修正前、ボタン種別を見ずに
 * {@code endpointDrag.isActive()} だけで無条件実行していたため、左ボタンで端点ドラッグ中に
 * 中ボタンを押して離すと、そこで繋ぎ替えが確定してしまっていた。ここでは左 press → 左 drag
 * (別クラスの上まで) → 中ボタン press/release → (まだ繋ぎ替わっていないことを確認) →
 * 本来の左 release、という経路で、中ボタンのリリースではモデルが変わらず、その後の左
 * release で従来どおり付替えが完了することを固定する。</p>
 */
public class SketchCanvasMiddleButtonReleaseDuringEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private SketchCanvas canvas;
    private SketchModel model;
    private SketchRelation rel;
    private SketchClass a;
    private SketchClass b;
    private SketchClass c;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        SketchCanvas.Listener listener = new SketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editRequested(SketchClass cls) {
            }

            @Override public void addClassRequested(Point at) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new SketchCanvas(listener));
        model = new SketchModel();
        a = new SketchClass("A", SketchClass.Kind.CLASS, 60, 60);
        b = new SketchClass("B", SketchClass.Kind.CLASS, 300, 60);
        c = new SketchClass("C", SketchClass.Kind.CLASS, 60, 260);
        model.getClasses().add(a);
        model.getClasses().add(b);
        model.getClasses().add(c);
        rel = new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "B", null);
        model.getRelations().add(rel);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });
    }

    private void dispatch(int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    @Test
    public void middleButtonReleaseDuringEndpointDrag_doesNotConfirmReattach() {
        Point rightAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[1]);
        Rectangle boundsC = GuiActionRunner.execute(() -> canvas.boundsOf(c));
        Point overC = new Point(boundsC.x + boundsC.width / 2, boundsC.y + boundsC.height / 2);

        // 左ボタンで "right" 側の端点ハンドルを掴み、C の上までドラッグする (まだリリースしない)。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, rightAnchor,
                MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, overC, 0);

        // ここで中ボタン (パン) を press → release する (左ボタンはまだ離していない想定)。
        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK,
                overC, MouseEvent.BUTTON2);
        dispatch(MouseEvent.MOUSE_RELEASED, InputEvent.BUTTON1_DOWN_MASK, overC, MouseEvent.BUTTON2);

        assertEquals("中ボタンのリリースでは繋ぎ替えが確定しないはず", "B", rel.getRight());
        assertEquals("中ボタンのリリースでは modelEdited が飛ばないはず", 0, edits.get());

        // 本来の左リリースでは、中断されずに繋ぎ替えが正常に完了するはず。
        dispatch(MouseEvent.MOUSE_RELEASED, 0, overC, MouseEvent.BUTTON1);

        assertEquals("その後の左リリースで従来どおり C へ付け替わるはず", "C", rel.getRight());
        assertEquals(1, edits.get());
    }
}
