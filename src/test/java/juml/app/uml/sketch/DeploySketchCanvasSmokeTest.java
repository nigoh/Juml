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
}
