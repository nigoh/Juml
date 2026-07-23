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
import java.awt.image.BufferedImage;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ObjectSketchCanvas} の描画・リンク操作のスモークテスト。
 *
 * <p>純 Graphics2D 描画は headless でも安全だが、Swing コンポーネント生成は表示環境を
 * 前提とするため、ヘッドレスではスキップする。(1) オブジェクト + リンク + 自己リンクを
 * 含むモデルが例外なく描画できること、(2) 同一オブジェクトを 2 回クリックで自己リンクが
 * 作られること、(3) リンクモードで旧選択がクリアされること、を確認する。</p>
 */
public class ObjectSketchCanvasSmokeTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static ObjectSketchCanvas.Listener noopListener() {
        return new ObjectSketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editObjectRequested(ObjectInstance o) {
            }

            @Override public void addObjectRequested(Point at) {
            }
        };
    }

    private static ObjectSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new ObjectSketchCanvas(noopListener()));
    }

    private static void paint(ObjectSketchCanvas canvas) {
        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(600, 400);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
    }

    private static ObjectSketchModel sampleModel() {
        ObjectSketchModel model = new ObjectSketchModel();
        ObjectInstance user = new ObjectInstance("User", "entity", 60, 60);
        user.getAttributes().add("name = \"Alice\"");
        user.getAttributes().add("age = 30");
        ObjectInstance post = new ObjectInstance("Post", null, 320, 60);
        model.getObjects().add(user);
        model.getObjects().add(post);
        model.getLinks().add(new ObjectLink("User", ObjectLink.Kind.ARROW, "Post", "owns"));
        return model;
    }

    @Test
    public void paintsObjectsAndLinkWithoutThrowing() {
        ObjectSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(
                () -> canvas.setModel(sampleModel(), true, Collections.emptyList()));
        paint(canvas); // 例外が飛ばなければ成功
    }

    @Test
    public void selfLink_createdByClickingSameObjectTwice_andPaints() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = new ObjectSketchModel();
        ObjectInstance a = new ObjectInstance("A", null, 80, 80);
        model.getObjects().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setRelationMode(ObjectLink.Kind.ARROW);
            canvas.relationClickForTest(a);
            canvas.relationClickForTest(a); // 同一オブジェクト 2 回目 → 自己リンク
        });
        assertEquals("自己リンクが 1 本作られるはず", 1, canvas.model().getLinks().size());
        ObjectLink link = canvas.model().getLinks().get(0);
        assertEquals("A", link.getLeft());
        assertEquals("A", link.getRight());
        paint(canvas); // 自己リンクの描画で例外が飛ばないこと
    }

    @Test
    public void enteringRelationMode_clearsSelection() {
        ObjectSketchCanvas canvas = newCanvas();
        ObjectSketchModel model = new ObjectSketchModel();
        ObjectInstance a = new ObjectInstance("A", null, 60, 60);
        model.getObjects().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSelectedForTest(a);
        });
        assertTrue("前提: A が選択されている",
                GuiActionRunner.execute(() -> canvas.selectedForTest() == a));
        GuiActionRunner.execute(() -> canvas.setRelationMode(ObjectLink.Kind.LINK));
        assertNull("リンクモードに入ると選択はクリアされるはず",
                GuiActionRunner.execute(canvas::selectedForTest));
    }

    @Test
    public void addObject_incrementsModel() {
        ObjectSketchCanvas canvas = newCanvas();
        GuiActionRunner.execute(
                () -> canvas.setModel(new ObjectSketchModel(), true, Collections.emptyList()));
        GuiActionRunner.execute(() -> canvas.addObject(null));
        assertEquals(1, canvas.model().getObjects().size());
    }
}
