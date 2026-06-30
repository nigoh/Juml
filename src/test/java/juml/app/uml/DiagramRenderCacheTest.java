// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JPanel;
import javax.swing.JViewport;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramRenderCache} の補助ロジックとラスタライズ (スーパーサンプリング) 経路のテスト。
 *
 * <p>{@code viewportRect} は static かつ Swing 描画を伴わないためヘッドレスで完結する
 * （{@link JViewport} は realize 不要で位置/範囲を設定できる）。HiDPI でのボケ対策として、
 * バッファを {@code renderScale} 倍の画素で確保し描画時に被覆矩形へ縮小して貼る経路は、
 * {@link BufferedImage} 上に直接描くためこれもヘッドレスで検証できる。</p>
 */
public class DiagramRenderCacheTest {

    // ── viewportRect / invalidate (純粋な補助ロジック) ──

    @Test
    public void viewportRect_null_returnsNull() {
        assertNull("スクロールペイン外 (null) なら null", DiagramRenderCache.viewportRect(null));
    }

    @Test
    public void viewportRect_returnsPositionAndExtent() {
        JViewport vp = new JViewport();
        JPanel view = new JPanel();
        view.setPreferredSize(new Dimension(2000, 2000));
        view.setSize(2000, 2000);
        vp.setView(view);
        vp.setExtentSize(new Dimension(800, 600));
        vp.setViewPosition(new Point(10, 20));

        Rectangle r = DiagramRenderCache.viewportRect(vp);
        assertEquals("可視矩形の X はビュー位置に一致すること", 10, r.x);
        assertEquals("可視矩形の Y はビュー位置に一致すること", 20, r.y);
        assertEquals("可視矩形の幅は extent に一致すること", 800, r.width);
        assertEquals("可視矩形の高さは extent に一致すること", 600, r.height);
    }

    @Test
    public void invalidate_isIdempotentAndSafe() {
        DiagramRenderCache cache = new DiagramRenderCache();
        // バッファ未生成でも invalidate が例外を出さず複数回呼べること
        cache.invalidate();
        cache.invalidate();
    }

    // ── ラスタライズ (スーパーサンプリング) 経路 ──

    /** 不透明な単色画像を作る。 */
    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static int countOpaque(BufferedImage img) {
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getRGB(x, y) >>> 24) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    private static BufferedImage paintOnce(DiagramRenderCache cache,
                                           BufferedImage source, double zoom,
                                           double renderScale, int targetSize) {
        BufferedImage target = new BufferedImage(targetSize, targetSize,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = target.createGraphics();
        Rectangle area = new Rectangle(0, 0, targetSize, targetSize);
        DiagramRenderCache.Source src = new DiagramRenderCache.Source(
                null, source, source.getWidth(), source.getHeight());
        cache.paint(g2, area, src, zoom, renderScale, area);
        g2.dispose();
        return target;
    }

    @Test
    public void rendersContentAtUnitScale() {
        DiagramRenderCache cache = new DiagramRenderCache();
        BufferedImage source = solid(4, 4, Color.RED);
        BufferedImage out = paintOnce(cache, source, 2.0, 1.0, 8);
        // zoom 2 で 8x8 全域が赤く塗られる。
        assertEquals(64, countOpaque(out));
    }

    @Test
    public void rendersContentWhenSupersampled() {
        DiagramRenderCache cache = new DiagramRenderCache();
        BufferedImage source = solid(4, 4, Color.RED);
        // renderScale 2 でも被覆領域は同じ (8x8)。内部バッファは 16x16 で描かれ縮小される。
        BufferedImage out = paintOnce(cache, source, 2.0, 2.0, 8);
        assertEquals(64, countOpaque(out));
    }

    @Test
    public void changingRenderScaleStillRenders() {
        // 同一キャッシュで renderScale を切り替えても (= バッファ無効化されても) 描けること。
        DiagramRenderCache cache = new DiagramRenderCache();
        BufferedImage source = solid(4, 4, Color.RED);
        assertTrue(countOpaque(paintOnce(cache, source, 1.0, 1.0, 4)) > 0);
        assertTrue(countOpaque(paintOnce(cache, source, 1.0, 3.0, 4)) > 0);
        assertTrue(countOpaque(paintOnce(cache, source, 1.0, 1.0, 4)) > 0);
    }
}
