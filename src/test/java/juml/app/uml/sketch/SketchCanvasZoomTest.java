// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchCanvas} のキャンバスズーム (Ctrl+ホイール相当)。
 *
 * <p>推奨サイズがズームに追従して拡縮し、ズーム中の描画が例外を投げないことを
 * 固定する (座標逆変換は {@link SketchViewportTest} で検証)。</p>
 */
public class SketchCanvasZoomTest {

    private static SketchCanvas.Listener noopListener() {
        return new SketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editRequested(SketchClass c) {
            }

            @Override public void addClassRequested(Point at) {
            }
        };
    }

    private static SketchCanvas canvasWithClass() {
        SketchCanvas canvas = GuiActionRunner.execute(() -> new SketchCanvas(noopListener()));
        SketchModel m = new SketchModel();
        m.getClasses().add(new SketchClass("A", SketchClass.Kind.CLASS, 100, 80));
        GuiActionRunner.execute(() -> canvas.setModel(m, true, List.of()));
        return canvas;
    }

    @Test
    public void preferredSize_scalesWithZoom() {
        SketchCanvas canvas = canvasWithClass();
        Dimension base = GuiActionRunner.execute(canvas::getPreferredSize);
        GuiActionRunner.execute(() -> canvas.setZoomForTest(2.0));
        Dimension zoomed = GuiActionRunner.execute(canvas::getPreferredSize);
        assertEquals(2.0, canvas.zoomForTest(), 1e-9);
        assertEquals("幅はズームに比例して拡大するはず", base.width * 2, zoomed.width);
        assertEquals("高さはズームに比例して拡大するはず", base.height * 2, zoomed.height);
    }

    @Test
    public void paint_atZoomDoesNotThrow_andDrawsScaledContent() {
        SketchCanvas canvas = canvasWithClass();
        GuiActionRunner.execute(() -> canvas.setZoomForTest(0.5));
        BufferedImage img = new BufferedImage(500, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(500, 400);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
        // 0.5x では (100,80) のボックスが (50,40) 付近に描かれる。
        // 60x50 付近 (縮小後のボックス内部) に背景以外の画素があることを確認する。
        boolean found = false;
        for (int y = 40; y < 90 && !found; y++) {
            for (int x = 50; x < 120 && !found; x++) {
                int argb = img.getRGB(x, y);
                boolean transparent = (argb >>> 24) == 0;
                boolean white = (argb & 0x00FFFFFF) == 0x00FFFFFF;
                if (!transparent && !white) {
                    found = true;
                }
            }
        }
        assertTrue("縮小後のボックス位置に描画があるはず", found);
    }

    @Test
    public void zoom_isClampedByViewportRange() {
        SketchCanvas canvas = canvasWithClass();
        GuiActionRunner.execute(() -> canvas.setZoomForTest(99.0));
        assertEquals(SketchViewport.MAX_ZOOM, canvas.zoomForTest(), 1e-9);
    }

    // --- bug-hunt round7 #4: 端点ハンドルの描画サイズは画面 px 一定 (ズームで拡縮しない) ---

    /**
     * 端点ハンドル (色 0x1565C0) の描画面積を zoom=3.0/1.0/0.25 で比較し、ズームに比例して
     * 拡縮しない (画面上ほぼ一定) ことを確認する (代表 2 キャンバスの 1 つ。他方は
     * {@link DeploySketchCanvasSmokeTest#endpointHandleScreenSize_staysApproxConstantAcrossZoom}
     * )。修正前はモデル座標固定サイズ ({@code HANDLE_SIZE}) のまま既にズームされた
     * {@code Graphics2D} へ描くため、面積は zoom の 2 乗 (3.0/0.25 で 144 倍) に比例して
     * 変わってしまっていた。
     */
    @Test
    public void endpointHandleScreenSize_staysApproxConstantAcrossZoom() {
        SketchCanvas canvas = GuiActionRunner.execute(() -> new SketchCanvas(noopListener()));
        SketchModel m = new SketchModel();
        SketchClass a = new SketchClass("A", SketchClass.Kind.CLASS, 40, 60);
        SketchClass b = new SketchClass("B", SketchClass.Kind.CLASS, 300, 60);
        m.getClasses().add(a);
        m.getClasses().add(b);
        m.getRelations().add(new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "B", null));
        GuiActionRunner.execute(() -> canvas.setModel(m, true, List.of()));

        int zoomIn = countHandleColorPixels(canvas, 3.0);
        int zoom1 = countHandleColorPixels(canvas, 1.0);
        int zoomOut = countHandleColorPixels(canvas, 0.25);

        assertTrue("拡大・等倍・縮小いずれでもハンドルは描かれるはず",
                zoomIn > 0 && zoom1 > 0 && zoomOut > 0);
        double ratio = (double) Math.max(zoomIn, zoomOut) / Math.min(zoomIn, zoomOut);
        assertTrue("ハンドルの画面上サイズはズームに依らずほぼ一定のはず (拡大/縮小の面積比="
                + ratio + ")", ratio < 4.0);
    }

    private static int countHandleColorPixels(SketchCanvas canvas, double zoom) {
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
