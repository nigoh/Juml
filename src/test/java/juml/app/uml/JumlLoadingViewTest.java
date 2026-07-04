// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link JumlLoadingView} をオフスクリーンに描画して検証する。{@link javax.swing.Timer} を
 * 開始せず ({@code addNotify} を呼ばず) に描画ルーチンだけを叩くのでヘッドレス安全。
 */
public class JumlLoadingViewTest {

    @Test
    public void paintAnimation_withWordmark_doesNotThrow() {
        JumlLoadingView view = new JumlLoadingView("起動中...", 96, true);
        view.setSize(280, 220);
        BufferedImage img = new BufferedImage(280, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            view.paintAnimation(g, 280, 220);
        } finally {
            g.dispose();
        }
        assertTrue("ロゴ/文言が描かれている", hasOpaquePixel(img));
    }

    @Test
    public void paintAnimation_withoutWordmark_doesNotThrow() {
        JumlLoadingView view = new JumlLoadingView(null, 72, false);
        BufferedImage img = new BufferedImage(200, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            view.paintAnimation(g, 200, 160);
        } finally {
            g.dispose();
        }
        assertTrue(hasOpaquePixel(img));
    }

    @Test
    public void setStatus_nullIsSafe() {
        JumlLoadingView view = new JumlLoadingView("初期", 72, false);
        view.setStatus(null);
        assertNotNull(view);
    }

    private static boolean hasOpaquePixel(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
