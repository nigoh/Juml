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
}
