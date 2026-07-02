// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link SketchCanvas} のグリッド吸着 (8px) の丸め境界 (純関数, headless)。
 */
public class SketchCanvasSnapTest {

    @Test
    public void snap_roundsToNearest8pxGrid() {
        assertEquals(0, SketchCanvas.snapForTest(0));
        assertEquals(0, SketchCanvas.snapForTest(3));   // 4 未満は 0 へ
        assertEquals(8, SketchCanvas.snapForTest(4));   // ちょうど半分は上へ (round)
        assertEquals(8, SketchCanvas.snapForTest(7));
        assertEquals(8, SketchCanvas.snapForTest(8));
        assertEquals(8, SketchCanvas.snapForTest(11));
        assertEquals(16, SketchCanvas.snapForTest(12));
        assertEquals(240, SketchCanvas.snapForTest(238)); // 実配置座標付近
    }
}
