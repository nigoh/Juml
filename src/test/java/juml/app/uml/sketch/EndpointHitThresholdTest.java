// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link EndpointHitThreshold#handleSizeModel} が、ヒット半径 ({@link
 * EndpointHitThreshold#modelRadius} と同じく) 画面上 px 一定の意味論になっていることを
 * 純関数として検証する (bug-hunt round7 #4)。
 *
 * <p>修正前は 8 キャンバスすべてがハンドルをモデル座標の固定サイズ (6px 等) で描いていたため、
 * 既にズームされた {@code Graphics2D} 上では見た目がズームに比例して拡縮し、常に画面上 px
 * 一定のヒット半径と食い違っていた (拡大時は見た目 &gt; 掴める範囲、縮小時はほぼ不可視なのに
 * 掴める)。{@code handleSizeModel(basePx, zoom)} は {@code basePx / zoom} をモデル座標として
 * 返すため、描画側で zoom 倍された後の見た目は {@code basePx} 一定に戻るはず。</p>
 */
public class EndpointHitThresholdTest {

    @Test
    public void handleSizeModel_screenSizeStaysApproxConstantAcrossZoom() {
        int basePx = 6;
        for (double zoom : new double[]{3.0, 1.0, 0.25}) {
            int modelPx = EndpointHitThreshold.handleSizeModel(basePx, zoom);
            double screenPx = modelPx * zoom;
            assertEquals("zoom=" + zoom + " でも画面上のハンドルサイズは basePx 一定のはず",
                    basePx, screenPx, 1.0);
        }
    }

    @Test
    public void handleSizeModel_zoomedInGivesSmallerModelPxThanZoomedOut() {
        // 拡大 (3.0x) では画面上一定を保つため、より小さいモデル px で描く必要があるはず。
        // 修正前 (固定 6 モデル px) は zoom に関わらず同じ値を返していたため、この不等式は
        // 固定モデル px 実装のままだと崩れる。
        int atZoomIn = EndpointHitThreshold.handleSizeModel(6, 3.0);
        int atZoomOut = EndpointHitThreshold.handleSizeModel(6, 0.25);
        assertTrue("拡大時は縮小時よりモデル座標のハンドルサイズが小さいはず",
                atZoomIn < atZoomOut);
    }

    @Test
    public void handleSizeModel_neverReturnsZeroOrNegative() {
        assertEquals("極端な拡大でも最低 1 モデル px は保つはず",
                1, EndpointHitThreshold.handleSizeModel(6, 100.0));
        assertTrue(EndpointHitThreshold.handleSizeModel(6, 0.01) > 0);
    }
}
