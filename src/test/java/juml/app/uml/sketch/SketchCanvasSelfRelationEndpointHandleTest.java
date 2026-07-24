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
 * {@link SketchCanvas} で自己関連 (両端が同一クラス) になっても端点ハンドルが消えず、
 * 再び掴み直せることを検証する (bug-hunt round 1: 自己関連の端点ハンドルが消えない)。
 *
 * <p>修正前の {@code nonSelfEndpointAnchors} は自己関連 (left==right) を非対応として
 * null を返しており、通常の関係を一方の端点だけ付け替えて自己関連にしてしまうと、以後は
 * 端点ハンドルが描かれず二度と掴み直せない「行き止まり」になっていた。修正後は自己関連の
 * ループ上アンカー ({@link SketchCanvas#endpointAnchorsForTest}) を返すため掴み直せる。</p>
 */
public class SketchCanvasSelfRelationEndpointHandleTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private SketchCanvas.Listener noopListener() {
        return new SketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editRequested(SketchClass c) {
            }

            @Override public void addClassRequested(Point at) {
            }
        };
    }

    private SketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new SketchCanvas(noopListener()));
    }

    @Test
    public void selfRelationDeclaredDirectly_hasNonNullEndpointAnchors() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = new SketchModel();
        model.getClasses().add(new SketchClass("A", SketchClass.Kind.CLASS, 60, 60));
        SketchRelation rel = new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "A", null);
        model.getRelations().add(rel);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));

        Point[] anchors = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel));
        assertNotNull("直接定義した自己関連でも端点ハンドルは非null (掴み直せる) はず", anchors);
        assertEquals(2, anchors.length);
    }

    @Test
    public void reattachOntoOtherEnd_becomesSelfRelation_stillHasEndpointAnchors() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = new SketchModel();
        model.getClasses().add(new SketchClass("A", SketchClass.Kind.CLASS, 60, 60));
        model.getClasses().add(new SketchClass("B", SketchClass.Kind.CLASS, 300, 60));
        SketchRelation rel = new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "B", null);
        model.getRelations().add(rel);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });

        // right 側を left と同じ A へ付け替え、通常の関係を自己関連 (A --> A) にする。
        GuiActionRunner.execute(() -> canvas.reattachForTest(rel, false, "A"));
        assertEquals("A", rel.getLeft());
        assertEquals("A", rel.getRight());

        Point[] anchors = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel));
        assertNotNull("付替えで自己関連になった後もハンドルは非null (掴み直せる) はず", anchors);
        assertEquals(2, anchors.length);
    }
}
