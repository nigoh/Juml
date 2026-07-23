// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DeploySketchCanvas} の描画スモークテスト (純 Graphics2D 描画のため headless 可)。
 *
 * <p>全 8 種のノードと 3 種のリンク・自己リンクを含むモデルを {@link BufferedImage} へ
 * 描画して例外が飛ばないこと、リンク追加経路 (2 クリック) で通常リンク/自己リンクが
 * 作られることを最低限確認する。実ディスプレイ (Robot) は不要。</p>
 */
public class DeploySketchCanvasSmokeTest {

    private static DeploySketchCanvas.Listener noopListener() {
        return new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editNodeRequested(DeployNode n) {
            }
        };
    }

    private static DeploySketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new DeploySketchCanvas(noopListener()));
    }

    private static BufferedImage paint(DeploySketchCanvas canvas) {
        BufferedImage img = new BufferedImage(600, 500, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(600, 500);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
        return img;
    }

    private static DeploySketchModel allKindsModel() {
        DeploySketchModel model = new DeploySketchModel();
        DeployNode.Kind[] kinds = DeployNode.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            model.getNodes().add(new DeployNode(kinds[i], kinds[i].name(), null,
                    40 + (i % 4) * 130, 60 + (i / 4) * 120));
        }
        model.getLinks().add(new DeployLink("NODE", DeployLink.Kind.ARROW, "DATABASE", "JDBC"));
        model.getLinks().add(new DeployLink("ARTIFACT", DeployLink.Kind.DEPENDENCY, "CLOUD", null));
        model.getLinks().add(new DeployLink("COMPONENT", DeployLink.Kind.LINK, "FOLDER", null));
        model.getLinks().add(new DeployLink("NODE", DeployLink.Kind.ARROW, "NODE", "self"));
        return model;
    }

    @Test
    public void paintsAllKindsAndLinksWithoutThrowing() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = allKindsModel();
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));
        // 最低限の保証: 例外を投げずに描画できること。
        paint(canvas);
    }

    @Test
    public void lockedModel_paintsBannerWithoutThrowing() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, "N", null, 60, 60));
        GuiActionRunner.execute(
                () -> canvas.setModel(model, false, java.util.List.of("node X {")));
        paint(canvas);
    }

    @Test
    public void link_createdByClickingTwoNodes() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 60, 60);
        DeployNode b = new DeployNode(DeployNode.Kind.DATABASE, "B", null, 260, 60);
        model.getNodes().add(a);
        model.getNodes().add(b);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setLinkMode(DeployLink.Kind.ARROW);
            canvas.linkClickForTest(a);
            canvas.linkClickForTest(b);
        });
        assertEquals("リンクが 1 本作られるはず", 1, canvas.model().getLinks().size());
        assertEquals("A", canvas.model().getLinks().get(0).getFrom());
        assertEquals("B", canvas.model().getLinks().get(0).getTo());
    }

    @Test
    public void selfLink_createdByClickingSameNodeTwice() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 60, 60);
        model.getNodes().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setLinkMode(DeployLink.Kind.ARROW);
            canvas.linkClickForTest(a);
            canvas.linkClickForTest(a);
        });
        assertEquals("自己リンクが 1 本作られるはず", 1, canvas.model().getLinks().size());
        DeployLink r = canvas.model().getLinks().get(0);
        assertEquals("A", r.getFrom());
        assertEquals("A", r.getTo());
    }

    @Test
    public void addNode_growsModelAndPaints() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.addNode(DeployNode.Kind.DATABASE, null);
        });
        assertTrue("ノードが追加されるはず", canvas.model().getNodes().size() == 1);
        assertEquals(DeployNode.Kind.DATABASE, canvas.model().getNodes().get(0).getKind());
        paint(canvas);
    }

    /** 入れ子コンテナ (コンテナ + 子 2 個 + リンク) を含むモデルを描いて例外が飛ばないこと。 */
    @Test
    public void paintsNestedContainerWithChildrenWithoutThrowing() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode outer = new DeployNode(DeployNode.Kind.NODE, "Outer", "Web Server", 40, 40);
        DeployNode child1 = new DeployNode(DeployNode.Kind.ARTIFACT, "c1", null, 10, 10);
        DeployNode child2 = new DeployNode(DeployNode.Kind.COMPONENT, "c2", null, 10, 70);
        model.getNodes().add(outer);
        model.addChild(outer, child1);
        model.addChild(outer, child2);
        model.getLinks().add(new DeployLink("c1", DeployLink.Kind.ARROW, "c2", "calls"));
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));
        paint(canvas);
        assertTrue("外側ノードはコンテナ扱いのはず", outer.isContainer());
    }

    /** コンテナの子ノードをドラッグすると、親内側原点からの相対座標として更新されること。 */
    @Test
    public void dragChildNode_movesRelativeToParentOrigin() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode outer = new DeployNode(DeployNode.Kind.NODE, "Outer", null, 100, 80);
        DeployNode child = new DeployNode(DeployNode.Kind.ARTIFACT, "c1", null, 5, 5);
        model.getNodes().add(outer);
        model.addChild(outer, child);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(600, 500);
            canvas.setSnapToGrid(false);
        });
        java.awt.Rectangle childRectBefore = GuiActionRunner.execute(
                () -> canvas.layoutForTest().get(child));
        // 子ノードの中心付近を押し、右へ 20px ドラッグする。
        int px = childRectBefore.x + childRectBefore.width / 2;
        int py = childRectBefore.y + childRectBefore.height / 2;
        GuiActionRunner.execute(() -> canvas.setSelectedForTest(null));
        dispatch(canvas, java.awt.event.MouseEvent.MOUSE_PRESSED,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, px, py,
                java.awt.event.MouseEvent.BUTTON1);
        dispatch(canvas, java.awt.event.MouseEvent.MOUSE_DRAGGED,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, px + 20, py, 0);
        dispatch(canvas, java.awt.event.MouseEvent.MOUSE_RELEASED, 0, px + 20, py,
                java.awt.event.MouseEvent.BUTTON1);
        assertEquals("子は親に付いたまま (親自身は移動していない)", 100, outer.getX());
        assertEquals("子の相対 x が右へ動いているはず", 25, child.getX());
    }

    private static void dispatch(DeploySketchCanvas canvas, int id, int modifiersEx,
                                 int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new java.awt.event.MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }
}
