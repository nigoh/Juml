// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * {@link StateSketchCanvas} で自己遷移 (両端が同一状態) になっても端点ハンドルが消えず、
 * 再び掴み直せることを検証する (bug-hunt round 1: 自己関連の端点ハンドルが消えない)。
 *
 * <p>修正前の {@code realEndpointAnchors} は自己遷移 (from==to) を非対応として null を
 * 返しており、通常の遷移を一方の端点だけ付け替えて自己遷移にしてしまうと、以後は
 * 端点ハンドルが描かれず二度と掴み直せない「行き止まり」になっていた。修正後は自己遷移の
 * ループ上アンカー ({@link StateSketchCanvas#endpointAnchorsForTest}) を返すため掴み直せる。</p>
 */
public class StateSketchCanvasSelfTransitionEndpointHandleTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private StateSketchCanvas.Listener noopListener() {
        return new StateSketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editStateRequested(StateNode s) {
            }
        };
    }

    private StateSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new StateSketchCanvas(noopListener()));
    }

    @Test
    public void selfTransitionDeclaredDirectly_hasNonNullEndpointAnchors() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = new StateSketchModel();
        model.getStates().add(new StateNode("S1", 60, 60));
        StateTransition t = new StateTransition("S1", "S1", null);
        model.getTransitions().add(t);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));

        Point[] anchors = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t));
        assertNotNull("直接定義した自己遷移でも端点ハンドルは非null (掴み直せる) はず", anchors);
        assertEquals(2, anchors.length);
    }

    @Test
    public void reattachOntoOtherEnd_becomesSelfTransition_stillHasEndpointAnchors() {
        StateSketchCanvas canvas = newCanvas();
        StateSketchModel model = new StateSketchModel();
        model.getStates().add(new StateNode("S1", 60, 60));
        model.getStates().add(new StateNode("S2", 300, 60));
        StateTransition t = new StateTransition("S1", "S2", null);
        model.getTransitions().add(t);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });

        // to 側を from と同じ S1 へ付け替え、通常の遷移を自己遷移 (S1 --> S1) にする。
        GuiActionRunner.execute(() -> canvas.reattachForTest(t, false, "S1"));
        assertEquals("S1", t.getFrom());
        assertEquals("S1", t.getTo());

        Point[] anchors = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(t));
        assertNotNull("付替えで自己遷移になった後もハンドルは非null (掴み直せる) はず", anchors);
        assertEquals(2, anchors.length);
    }
}
