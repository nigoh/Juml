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
 * {@link ObjectSketchCanvas} で自己リンク (両端が同一オブジェクト) になっても端点ハンドルが
 * 消えず、再び掴み直せることを検証する (bug-hunt round 1: 自己関連の端点ハンドルが消えない)。
 *
 * <p>修正前の {@code nonSelfEndpointAnchors} は自己リンク (left==right) を非対応として
 * null を返しており、通常のリンクを一方の端点だけ付け替えて自己リンクにしてしまうと、以後は
 * 端点ハンドルが描かれず二度と掴み直せない「行き止まり」になっていた。修正後は自己リンクの
 * ループ上アンカー ({@link ObjectSketchCanvas#endpointAnchorsForTest}) を返すため掴み直せる。</p>
 */
public class ObjectSketchCanvasSelfLinkEndpointHandleTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private ObjectSketchCanvas.Listener noopListener() {
        return new ObjectSketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editObjectRequested(ObjectInstance o) {
            }

            @Override public void addObjectRequested(Point at) {
            }
        };
    }

    private ObjectSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new ObjectSketchCanvas(noopListener()));
    }

    @Test
    public void selfLinkDeclaredDirectly_hasNonNullEndpointAnchors() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = new ObjectSketchModel();
        model.getObjects().add(new ObjectInstance("User", null, 60, 60));
        ObjectLink link = new ObjectLink("User", ObjectLink.Kind.ARROW, "User", null);
        model.getLinks().add(link);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));

        Point[] anchors = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link));
        assertNotNull("直接定義した自己リンクでも端点ハンドルは非null (掴み直せる) はず", anchors);
        assertEquals(2, anchors.length);
    }

    @Test
    public void reattachOntoOtherEnd_becomesSelfLink_stillHasEndpointAnchors() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = new ObjectSketchModel();
        model.getObjects().add(new ObjectInstance("User", null, 60, 60));
        model.getObjects().add(new ObjectInstance("Post", null, 300, 60));
        ObjectLink link = new ObjectLink("User", ObjectLink.Kind.ARROW, "Post", null);
        model.getLinks().add(link);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 400);
        });

        // right 側を left と同じ User へ付け替え、通常のリンクを自己リンクにする。
        GuiActionRunner.execute(() -> canvas.reattachForTest(link, false, "User"));
        assertEquals("User", link.getLeft());
        assertEquals("User", link.getRight());

        Point[] anchors = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(link));
        assertNotNull("付替えで自己リンクになった後もハンドルは非null (掴み直せる) はず", anchors);
        assertEquals(2, anchors.length);
    }
}
