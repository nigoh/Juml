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

    /**
     * bug-hunt round9 論点5: 子を持つコンテナも種別ごとの外形で描く。修正前はコンテナは
     * 種別に関わらず常に矩形で塗られ、database/cloud/artifact が子を持つと円柱/雲/成果物の
     * 外形が失われていた。ここでは外形をコーナー画素で判定する: database コンテナは円柱の
     * 曲線で左上コーナーが「切り取られ」背景のままになる一方、rectangle コンテナは矩形塗りで
     * コーナーまで塗られる (ステレオタイプ文字はコーナーに無いため、種別間の文字差に依らず
     * 外形だけを切り分けられる)。
     */
    @Test
    public void containerWithChildren_usesKindShape_databaseCornerIsCutButRectangleIsFilled() {
        int[] dbCornerAndBg = containerCornerAndBackground(DeployNode.Kind.DATABASE);
        int[] rectCornerAndBg = containerCornerAndBackground(DeployNode.Kind.RECTANGLE);
        assertEquals("database コンテナの左上コーナーは円柱の曲線で切り取られ背景のままのはず",
                dbCornerAndBg[1], dbCornerAndBg[0]);
        assertTrue("rectangle コンテナの左上コーナーは矩形塗りで背景と異なるはず",
                rectCornerAndBg[0] != rectCornerAndBg[1]);
    }

    /** {@code kind} の子持ちコンテナを描き、[コンテナ左上コーナー画素, 背景画素] を返す。 */
    private static int[] containerCornerAndBackground(DeployNode.Kind kind) {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode outer = new DeployNode(kind, "Outer", null, 80, 80);
        DeployNode child = new DeployNode(DeployNode.Kind.COMPONENT, "c1", null, 10, 10);
        model.getNodes().add(outer);
        model.addChild(outer, child);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));
        BufferedImage img = paint(canvas);
        java.awt.Rectangle r = GuiActionRunner.execute(() -> canvas.layoutForTest().get(outer));
        int corner = img.getRGB(r.x + 2, r.y + 2);
        int background = img.getRGB(3, 3); // ノードのない左上隅 (最初のノードは x,y>=80)。
        return new int[]{corner, background};
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

    // --- bug-hunt round7 #3: 入れ子子ノード間リンクがコンテナ枠の不透明塗りに隠れないはず ---

    /**
     * コンテナの子ノード同士のリンクを描き、コンテナ内側 (タイトル行より下、子ノードの矩形の
     * 外) にリンク線色 (0x37474F) に近い画素が存在することを確認する。修正前は paintLink が
     * paintNodeTree (= コンテナの不透明な fillRect) より先に呼ばれていたため、この領域は
     * 常にコンテナ背景色 (0xF3F6FA) のままでリンクが完全に隠れていた。
     */
    @Test
    public void nestedContainerLink_isVisibleOverContainerFill() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode outer = new DeployNode(DeployNode.Kind.NODE, "Outer", "Web Server", 40, 40);
        DeployNode child1 = new DeployNode(DeployNode.Kind.ARTIFACT, "c1", null, 10, 70);
        DeployNode child2 = new DeployNode(DeployNode.Kind.COMPONENT, "c2", null, 10, 160);
        model.getNodes().add(outer);
        model.addChild(outer, child1);
        model.addChild(outer, child2);
        model.getLinks().add(new DeployLink("c1", DeployLink.Kind.ARROW, "c2", null));
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));
        BufferedImage img = paint(canvas);

        java.util.Map<DeployNode, java.awt.Rectangle> layout =
                GuiActionRunner.execute(canvas::layoutForTest);
        java.awt.Rectangle containerRect = layout.get(outer);
        java.awt.Rectangle c1Rect = layout.get(child1);
        java.awt.Rectangle c2Rect = layout.get(child2);

        boolean foundLinkColor = false;
        for (int y = containerRect.y + 1;
                y < containerRect.y + containerRect.height - 1 && !foundLinkColor; y++) {
            for (int x = containerRect.x + 1;
                    x < containerRect.x + containerRect.width - 1; x++) {
                if (c1Rect.contains(x, y) || c2Rect.contains(x, y)) {
                    continue;
                }
                if (closeToColor(img.getRGB(x, y), 0x37474F, 20)) {
                    foundLinkColor = true;
                    break;
                }
            }
        }
        assertTrue("コンテナ内側に子ノード間リンクの線 (0x37474F 相当) が見えるはず",
                foundLinkColor);
    }

    private static boolean closeToColor(int argb, int rgb, int tolerance) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int tr = (rgb >> 16) & 0xFF;
        int tg = (rgb >> 8) & 0xFF;
        int tb = rgb & 0xFF;
        return Math.abs(r - tr) <= tolerance && Math.abs(g - tg) <= tolerance
                && Math.abs(b - tb) <= tolerance;
    }

    // --- bug-hunt round7 #4: 端点ハンドルの描画サイズは画面 px 一定 (ズームで拡縮しない) ---

    /**
     * 端点ハンドル (色 0x1565C0) の描画面積を zoom=3.0/1.0/0.25 で比較し、ズームに比例して
     * 拡縮しない (画面上ほぼ一定) ことを確認する。修正前はモデル座標固定サイズのまま既に
     * ズームされた {@code Graphics2D} へ描くため、面積は zoom の 2 乗 (3.0/0.25 で 144 倍) に
     * 比例して変わってしまっていた。
     */
    @Test
    public void endpointHandleScreenSize_staysApproxConstantAcrossZoom() {
        DeploySketchCanvas canvas = newCanvas();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 40, 60);
        DeployNode b = new DeployNode(DeployNode.Kind.NODE, "B", null, 300, 60);
        model.getNodes().add(a);
        model.getNodes().add(b);
        model.getLinks().add(new DeployLink("A", DeployLink.Kind.ARROW, "B", null));
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));

        int zoomIn = countHandleColorPixels(canvas, 3.0);
        int zoom1 = countHandleColorPixels(canvas, 1.0);
        int zoomOut = countHandleColorPixels(canvas, 0.25);

        assertTrue("拡大・等倍・縮小いずれでもハンドルは描かれるはず",
                zoomIn > 0 && zoom1 > 0 && zoomOut > 0);
        double ratio = (double) Math.max(zoomIn, zoomOut) / Math.min(zoomIn, zoomOut);
        assertTrue("ハンドルの画面上サイズはズームに依らずほぼ一定のはず (拡大/縮小の面積比="
                + ratio + ")", ratio < 4.0);
    }

    private static int countHandleColorPixels(DeploySketchCanvas canvas, double zoom) {
        GuiActionRunner.execute(() -> canvas.setZoomForTest(zoom));
        int w = 1600;
        int h = 900;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(w, h);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
        int target = 0xFF1565C0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (img.getRGB(x, y) == target) {
                    count++;
                }
            }
        }
        return count;
    }
}
