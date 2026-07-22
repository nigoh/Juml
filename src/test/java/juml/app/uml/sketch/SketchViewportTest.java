// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;

import static org.junit.Assert.assertEquals;

/**
 * {@link SketchViewport} のズーム計算 (クランプ・座標逆変換・サイズ拡縮)。
 */
public class SketchViewportTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static SketchViewport newViewport() {
        return GuiActionRunner.execute(() -> new SketchViewport(new JPanel()));
    }

    @Test
    public void setZoom_clampsToRange() {
        SketchViewport v = newViewport();
        GuiActionRunner.execute(() -> v.setZoom(100.0));
        assertEquals(SketchViewport.MAX_ZOOM, v.zoom(), 1e-9);
        GuiActionRunner.execute(() -> v.setZoom(0.001));
        assertEquals(SketchViewport.MIN_ZOOM, v.zoom(), 1e-9);
    }

    @Test
    public void toModel_invertsZoom() {
        SketchViewport v = newViewport();
        GuiActionRunner.execute(() -> v.setZoom(2.0));
        Point m = v.toModel(new Point(100, 50));
        assertEquals(50, m.x);
        assertEquals(25, m.y);
    }

    @Test
    public void toModel_atDefaultZoomIsIdentity() {
        SketchViewport v = newViewport();
        Point m = v.toModel(new Point(37, 91));
        assertEquals(37, m.x);
        assertEquals(91, m.y);
    }

    @Test
    public void scaled_multipliesPreferredSize() {
        SketchViewport v = newViewport();
        GuiActionRunner.execute(() -> v.setZoom(2.0));
        Dimension d = v.scaled(new Dimension(400, 300));
        assertEquals(800, d.width);
        assertEquals(600, d.height);
    }
}
