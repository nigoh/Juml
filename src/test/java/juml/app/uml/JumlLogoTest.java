// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link JumlLogo} のベクター描画を検証する。オフスクリーン ({@link BufferedImage}) へ
 * 描くだけなのでヘッドレスでも完結する。
 */
public class JumlLogoTest {

    /** マーク画像は指定サイズで生成され、実際にピクセルが描かれる (透明のままでない)。 */
    @Test
    public void renderMarkImage_producesNonEmptyImage() {
        BufferedImage img = JumlLogo.renderMarkImage(64);
        assertNotNull("画像が返る", img);
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        assertTrue("中央付近にブランド色が塗られている", hasOpaquePixel(img));
    }

    /** パルス描画は位相を変えても例外を出さない (子→親を流れきってもクラッシュしない)。 */
    @Test
    public void paintPulse_acrossFullTravel_doesNotThrow() {
        BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            for (int i = 0; i <= 40; i++) {
                JumlLogo.paintPulse(g, 60, 60, 100, i / 40.0);
            }
        } finally {
            g.dispose();
        }
        assertTrue("パルスが描画されている", hasOpaquePixel(img));
    }

    /** 位相が範囲外 (負 / 1 超) でもクランプされて例外を出さない。 */
    @Test
    public void paintPulse_outOfRangePhase_isClamped() {
        BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            JumlLogo.paintPulse(g, 60, 60, 100, -0.5);
            JumlLogo.paintPulse(g, 60, 60, 100, 1.5);
        } finally {
            g.dispose();
        }
        assertTrue(hasOpaquePixel(img));
    }

    /** 呼吸スケール係数を変えてもマーク描画は破綻しない。 */
    @Test
    public void paintMark_withBreath_doesNotThrow() {
        BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            JumlLogo.paintMark(g, 60, 60, 80, 0.9);
            JumlLogo.paintMark(g, 60, 60, 80, 1.05);
        } finally {
            g.dispose();
        }
        assertTrue(hasOpaquePixel(img));
    }

    /** 不透明ピクセルが 1 つでもあれば true。 */
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
