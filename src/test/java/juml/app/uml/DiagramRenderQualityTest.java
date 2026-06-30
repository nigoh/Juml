// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * {@link DiagramRenderQuality} のユニットテスト。
 *
 * <p>{@code scaleFor(null)} はグラフィック構成を取れないためデバイス倍率 1.0 とみなす。
 * これを基準に、各品質が要求する実効スーパーサンプリング倍率とキー復元を検証する。
 * GUI を生成しないためヘッドレスでも安全。</p>
 */
public class DiagramRenderQualityTest {

    @After
    public void restoreDefault() {
        // 静的状態 (current) を他テストへ漏らさないよう既定へ戻す。
        DiagramRenderQuality.setCurrent(DiagramRenderQuality.AUTO);
    }

    @Test
    public void fromKeyParsesKnownValues() {
        assertSame(DiagramRenderQuality.AUTO, DiagramRenderQuality.fromKey("AUTO"));
        assertSame(DiagramRenderQuality.LOW, DiagramRenderQuality.fromKey("low"));
        assertSame(DiagramRenderQuality.HIGH, DiagramRenderQuality.fromKey(" HIGH "));
        assertSame(DiagramRenderQuality.ULTRA, DiagramRenderQuality.fromKey("ULTRA"));
    }

    @Test
    public void fromKeyUnknownFallsBackToAuto() {
        assertSame(DiagramRenderQuality.AUTO, DiagramRenderQuality.fromKey(null));
        assertSame(DiagramRenderQuality.AUTO, DiagramRenderQuality.fromKey(""));
        assertSame(DiagramRenderQuality.AUTO, DiagramRenderQuality.fromKey("nope"));
    }

    @Test
    public void currentDefaultsToAutoAndCanBeSet() {
        assertSame(DiagramRenderQuality.AUTO, DiagramRenderQuality.current());
        DiagramRenderQuality.setCurrent(DiagramRenderQuality.HIGH);
        assertSame(DiagramRenderQuality.HIGH, DiagramRenderQuality.current());
        // null は AUTO へ丸める
        DiagramRenderQuality.setCurrent(null);
        assertSame(DiagramRenderQuality.AUTO, DiagramRenderQuality.current());
    }

    @Test
    public void scaleForNullComponentUsesDeviceScaleOne() {
        // デバイス倍率 1.0 のとき: AUTO/LOW=1.0、HIGH=2.0、ULTRA=3.0
        assertEquals(1.0, DiagramRenderQuality.AUTO.scaleFor(null), 1e-9);
        assertEquals(1.0, DiagramRenderQuality.LOW.scaleFor(null), 1e-9);
        assertEquals(2.0, DiagramRenderQuality.HIGH.scaleFor(null), 1e-9);
        assertEquals(3.0, DiagramRenderQuality.ULTRA.scaleFor(null), 1e-9);
    }

    @Test
    public void scaleForNeverBelowOne() {
        for (DiagramRenderQuality q : DiagramRenderQuality.values()) {
            double s = q.scaleFor(null);
            org.junit.Assert.assertTrue("scale >= 1 for " + q, s >= 1.0);
            org.junit.Assert.assertTrue("scale <= 4 for " + q, s <= 4.0);
        }
    }
}
