// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.awt.Point;

import static org.junit.Assert.assertEquals;

/**
 * {@link StateSketchCanvas#nearestEndpointForTest} (端点ドラッグのヒットテストの核となる
 * 純関数) の境界値テスト。Swing/表示環境に依存しないため headless でも常に実行する。
 */
public class StateSketchCanvasEndpointGeometryTest {

    private static final Point A = new Point(100, 100);
    private static final Point B = new Point(200, 100);

    @Test
    public void pickAOverB_whenCloserToA() {
        Point p = new Point(101, 100);
        assertEquals(0, StateSketchCanvas.nearestEndpointForTest(p, A, B, 8.0));
    }

    @Test
    public void pickBOverA_whenCloserToB() {
        Point p = new Point(199, 100);
        assertEquals(1, StateSketchCanvas.nearestEndpointForTest(p, A, B, 8.0));
    }

    @Test
    public void returnsMinusOne_whenBothOutsideRadius() {
        Point p = new Point(150, 100);
        assertEquals(-1, StateSketchCanvas.nearestEndpointForTest(p, A, B, 8.0));
    }

    @Test
    public void picksNearerSide_whenBothWithinRadius() {
        Point p = new Point(103, 100);
        assertEquals(0, StateSketchCanvas.nearestEndpointForTest(p, A, B, 200.0));
    }
}
