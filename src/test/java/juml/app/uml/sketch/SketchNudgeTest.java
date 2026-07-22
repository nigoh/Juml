// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.awt.event.KeyEvent;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link SketchNudge} の矢印キー移動量計算 (純関数, headless)。
 */
public class SketchNudgeTest {

    @Test
    public void deltaFor_arrowKeysMoveOnePixel() {
        assertArrayEquals(new int[]{-1, 0}, SketchNudge.deltaFor(KeyEvent.VK_LEFT, false, 8));
        assertArrayEquals(new int[]{1, 0}, SketchNudge.deltaFor(KeyEvent.VK_RIGHT, false, 8));
        assertArrayEquals(new int[]{0, -1}, SketchNudge.deltaFor(KeyEvent.VK_UP, false, 8));
        assertArrayEquals(new int[]{0, 1}, SketchNudge.deltaFor(KeyEvent.VK_DOWN, false, 8));
    }

    @Test
    public void deltaFor_shiftMovesByGrid() {
        assertArrayEquals(new int[]{-8, 0}, SketchNudge.deltaFor(KeyEvent.VK_LEFT, true, 8));
        assertArrayEquals(new int[]{0, 8}, SketchNudge.deltaFor(KeyEvent.VK_DOWN, true, 8));
    }

    @Test
    public void deltaFor_nonArrowKeysReturnNull() {
        assertNull(SketchNudge.deltaFor(KeyEvent.VK_DELETE, false, 8));
        assertNull(SketchNudge.deltaFor(KeyEvent.VK_A, false, 8));
        assertNull(SketchNudge.deltaFor(KeyEvent.VK_ESCAPE, true, 8));
    }
}
