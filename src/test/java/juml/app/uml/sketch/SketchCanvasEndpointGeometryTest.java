// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.awt.Point;

import static org.junit.Assert.assertEquals;

/**
 * {@link SketchCanvas#nearestEndpointForTest} (端点ドラッグのヒットテストの核となる
 * 純関数) の境界値テスト。Swing/表示環境に依存しないため headless でも常に実行する。
 */
public class SketchCanvasEndpointGeometryTest {

    private static final Point A = new Point(100, 100);
    private static final Point B = new Point(200, 100);

    @Test
    public void pickAOverB_whenCloserToA() {
        Point p = new Point(101, 100); // A から 1px, B から 99px
        assertEquals(0, SketchCanvas.nearestEndpointForTest(p, A, B, 8.0));
    }

    @Test
    public void pickBOverA_whenCloserToB() {
        Point p = new Point(199, 100); // B から 1px, A から 99px
        assertEquals(1, SketchCanvas.nearestEndpointForTest(p, A, B, 8.0));
    }

    @Test
    public void returnsMinusOne_whenBothOutsideRadius() {
        Point p = new Point(150, 100); // A/B どちらからも 50px
        assertEquals(-1, SketchCanvas.nearestEndpointForTest(p, A, B, 8.0));
    }

    @Test
    public void picksNearerSide_whenBothWithinRadius() {
        // A から 3px, B から 97px なので、しきい値を広げても近い方 (0) を選ぶ。
        Point p = new Point(103, 100);
        assertEquals(0, SketchCanvas.nearestEndpointForTest(p, A, B, 200.0));
    }

    @Test
    public void boundaryAtExactlyRadius_isStillAHit() {
        Point p = new Point(108, 100); // A からちょうど 8px (しきい値と同値)
        assertEquals(0, SketchCanvas.nearestEndpointForTest(p, A, B, 8.0));
    }
}
