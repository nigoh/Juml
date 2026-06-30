// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link SvgPreviewPanel} の基本動作を検証する。
 *
 * <p>レンダリングそのものはディスプレイなしで再現困難なため、
 * 状態管理 (画像セット / ズーム値の範囲) のみ単体テストする。</p>
 */
public class SvgPreviewPanelTest {

    @Test
    public void testSetImage() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        BufferedImage img = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        p.setImage(img);
        assertSame(img, p.getImage());
    }

    @Test
    public void testClearImage() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
        p.setImage(null);
        assertNull(p.getImage());
    }

    @Test
    public void testZoomDefaults() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        assertEquals(1.0, p.getZoomLevel(), 1e-9);
    }

    @Test
    public void testZoomReset() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));
        p.setZoomLevel(2.5);
        assertEquals(2.5, p.getZoomLevel(), 1e-9);
        p.zoomReset();
        assertEquals(1.0, p.getZoomLevel(), 1e-9);
    }

    @Test
    public void testZoomClamped() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));
        p.setZoomLevel(100.0);
        assertTrue("zoom should be clamped: " + p.getZoomLevel(),
                p.getZoomLevel() <= 8.0 + 1e-9);
        p.setZoomLevel(0.001);
        assertTrue("zoom should be clamped: " + p.getZoomLevel(),
                p.getZoomLevel() >= 0.1 - 1e-9);
    }

    @Test
    public void testZoomChangeListenerInvoked() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB));
        int[] count = new int[]{0};
        p.setZoomChangeListener(() -> count[0]++);
        p.setZoomLevel(2.0);
        assertEquals(1, count[0]);
    }

    @Test
    public void testSetSvgGraphicsNodeClearsImage() throws Exception {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB));
        PlantUmlSvgRenderer.RenderedSvg svg = PlantUmlSvgRenderer.render(
                "@startuml\nA -> B\n@enduml\n");
        assertNotNull(svg);
        p.setSvgGraphicsNode(svg.getRoot(), svg.getWidth(), svg.getHeight());
        // SVG モードに切替後は画像モードはクリアされる
        assertNull(p.getImage());
        assertSame(svg.getRoot(), p.getSvgGraphicsNode());
    }

    @Test
    public void testSetImageClearsSvgGraphicsNode() throws Exception {
        SvgPreviewPanel p = new SvgPreviewPanel();
        PlantUmlSvgRenderer.RenderedSvg svg = PlantUmlSvgRenderer.render(
                "@startuml\nA -> B\n@enduml\n");
        assertNotNull(svg);
        p.setSvgGraphicsNode(svg.getRoot(), svg.getWidth(), svg.getHeight());
        p.setImage(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB));
        // 画像モードに切替後は SVG モードはクリアされる
        assertNull(p.getSvgGraphicsNode());
        assertNotNull(p.getImage());
    }

    @Test
    public void testClearSvgGraphicsNode() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setSvgGraphicsNode(null, 0, 0);
        assertNull(p.getSvgGraphicsNode());
        assertNull(p.getImage());
    }

    @Test
    public void testSetLinkAreasStoresList() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        PlantUmlSvgRenderer.LinkArea area = new PlantUmlSvgRenderer.LinkArea(
                "juml://class/x.Foo", 10, 20, 100, 50);
        p.setLinkAreas(java.util.Collections.singletonList(area));
        assertEquals(1, p.getLinkAreas().size());
        assertEquals("juml://class/x.Foo", p.getLinkAreas().get(0).getHref());
    }

    @Test
    public void testSetLinkAreasNullClears() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setLinkAreas(java.util.Collections.singletonList(
                new PlantUmlSvgRenderer.LinkArea("u", 0, 0, 10, 10)));
        p.setLinkAreas(null);
        assertTrue(p.getLinkAreas().isEmpty());
    }

    @Test
    public void testSetSvgGraphicsNodeClearsLinkAreas() throws Exception {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setLinkAreas(java.util.Collections.singletonList(
                new PlantUmlSvgRenderer.LinkArea("u", 0, 0, 10, 10)));
        PlantUmlSvgRenderer.RenderedSvg svg = PlantUmlSvgRenderer.render(
                "@startuml\nA -> B\n@enduml\n");
        p.setSvgGraphicsNode(svg.getRoot(), svg.getWidth(), svg.getHeight());
        // 古い SVG 由来のリンク領域は無効化される
        assertTrue(p.getLinkAreas().isEmpty());
    }

    @Test
    public void testHitTestLinkAtZoom1() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        PlantUmlSvgRenderer.LinkArea area = new PlantUmlSvgRenderer.LinkArea(
                "juml://class/Foo", 10, 20, 100, 50);
        p.setLinkAreas(java.util.Collections.singletonList(area));
        // 内側
        assertNotNull(p.hitTestLink(new java.awt.Point(50, 40)));
        // 外側
        assertNull(p.hitTestLink(new java.awt.Point(5, 5)));
        assertNull(p.hitTestLink(new java.awt.Point(150, 200)));
    }

    @Test
    public void testGetTextItemsReflectsSetTextItems() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        assertTrue("default text items empty", p.getTextItems().isEmpty());
        p.setTextItems(java.util.Collections.singletonList(
                new PlantUmlSvgRenderer.SvgTextItem("Foo", 10, 20, 30, 16)));
        assertEquals(1, p.getTextItems().size());
        assertEquals("Foo", p.getTextItems().get(0).getText());
    }

    @Test
    public void testSearchHighlightsSetAndClear() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB));
        java.util.List<java.awt.geom.Rectangle2D> hits = java.util.Arrays.asList(
                new java.awt.geom.Rectangle2D.Double(10, 10, 40, 16),
                new java.awt.geom.Rectangle2D.Double(60, 30, 40, 16));
        // 例外なく設定・スクロール・クリアできること (ヘッドレスでも安全)。
        p.setSearchHighlights(hits, 1);
        p.scrollSvgRectToVisible(hits.get(1));
        p.clearSearchHighlights();
    }

    @Test
    public void testSetSvgGraphicsNodeClearsSearchHighlights() throws Exception {
        SvgPreviewPanel p = new SvgPreviewPanel();
        p.setImage(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        p.setSearchHighlights(java.util.Collections.singletonList(
                new java.awt.geom.Rectangle2D.Double(0, 0, 10, 10)), 0);
        PlantUmlSvgRenderer.RenderedSvg svg = PlantUmlSvgRenderer.render(
                "@startuml\nA -> B\n@enduml\n");
        p.setSvgGraphicsNode(svg.getRoot(), svg.getWidth(), svg.getHeight());
        // 図差し替え後はヒットが残らない (再描画してもエラーにならない)。
        p.scrollSvgRectToVisible(null);
    }

    @Test
    public void testHitTestLinkAtZoom2() {
        SvgPreviewPanel p = new SvgPreviewPanel();
        // ヘッドレスでも setImage して content がある状態を作る
        p.setImage(new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB));
        PlantUmlSvgRenderer.LinkArea area = new PlantUmlSvgRenderer.LinkArea(
                "juml://class/Foo", 10, 20, 100, 50);
        p.setLinkAreas(java.util.Collections.singletonList(area));
        p.setZoomLevel(2.0);
        // SVG 座標 (50,40) はパネル座標 (100,80) に対応
        assertNotNull(p.hitTestLink(new java.awt.Point(100, 80)));
        // ズーム前なら (50,40) で当たるが、ズーム 2 倍ではパネル (50,40) は SVG (25,20)
        // -> エリア (10,20,100,50) 内 (sx=25 in [10,110], sy=20 in [20,70]) なので当たる
        assertNotNull(p.hitTestLink(new java.awt.Point(50, 40)));
        // 完全に外
        assertNull(p.hitTestLink(new java.awt.Point(400, 400)));
    }
}
