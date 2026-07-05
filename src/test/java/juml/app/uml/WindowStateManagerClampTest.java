// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link WindowStateManager#clampToScreens} の純関数テスト (headless でも走る)。
 *
 * <p>マルチモニタでの位置復元 (#41): プライマリより左/上のモニタ (負座標) はそのまま復元し、
 * 切断済みモニタ用の座標 (どの画面とも交差しない) はプライマリ内へクランプする。</p>
 */
public class WindowStateManagerClampTest {

    private static final Rectangle PRIMARY = new Rectangle(0, 0, 1920, 1080);

    /** プライマリ上に十分収まる正座標はそのまま維持する。 */
    @Test
    public void keepsLocationFullyOnPrimary() {
        Point p = WindowStateManager.clampToScreens(100, 200, 800, 600,
                Collections.singletonList(PRIMARY), PRIMARY);
        assertEquals(100, p.x);
        assertEquals(200, p.y);
    }

    /** プライマリより左/上のモニタ (負座標) に十分交差していれば負座標のまま復元する。 */
    @Test
    public void keepsNegativeCoordinatesOnLeftMonitor() {
        Rectangle leftMonitor = new Rectangle(-1920, 0, 1920, 1080);
        List<Rectangle> screens = Arrays.asList(leftMonitor, PRIMARY);
        Point p = WindowStateManager.clampToScreens(-1800, 100, 800, 600, screens, PRIMARY);
        assertEquals("負座標のモニタ上の位置はクランプせず維持する", -1800, p.x);
        assertEquals(100, p.y);
    }

    /** 切断済みモニタ用の座標 (どの画面とも交差しない) はプライマリ内へクランプする。 */
    @Test
    public void clampsOffscreenCoordinatesIntoPrimary() {
        Point p = WindowStateManager.clampToScreens(5000, 4000, 800, 600,
                Collections.singletonList(PRIMARY), PRIMARY);
        assertTrue("x はプライマリ内に収まるべき: " + p.x,
                p.x >= PRIMARY.x && p.x + 800 <= PRIMARY.x + PRIMARY.width);
        assertTrue("y はプライマリ内に収まるべき: " + p.y,
                p.y >= PRIMARY.y && p.y + 600 <= PRIMARY.y + PRIMARY.height);
    }

    /** タイトルバーだけ画面外 (ほとんど交差しない) もクランプ対象。 */
    @Test
    public void clampsWhenBarelyIntersecting() {
        // ウィンドウの大半が右へはみ出し、交差幅が掴める最小 (100px) 未満。
        Point p = WindowStateManager.clampToScreens(1900, 200, 800, 600,
                Collections.singletonList(PRIMARY), PRIMARY);
        assertTrue("右端に収まるべき", p.x + 800 <= PRIMARY.x + PRIMARY.width);
    }

    /** 画面情報が無い (headless 相当) 場合は元の座標を返す。 */
    @Test
    public void returnsInputWhenNoPrimary() {
        Point p = WindowStateManager.clampToScreens(-1800, 100, 800, 600,
                Collections.emptyList(), null);
        assertEquals(-1800, p.x);
        assertEquals(100, p.y);
    }
}
