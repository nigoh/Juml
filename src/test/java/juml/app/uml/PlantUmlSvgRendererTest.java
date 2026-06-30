// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PlantUmlSvgRenderer} のユニットテスト。
 * 同梱 PlantUML が SVG 出力経路を通り、Batik で GraphicsNode 化できることを確認する。
 */
public class PlantUmlSvgRendererTest {

    @Test
    public void testSimpleClassDiagramRenders() throws IOException {
        String puml = "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull("RenderedSvg should not be null", r);
        assertNotNull("GraphicsNode should not be null", r.getRoot());
        assertTrue("width > 0", r.getWidth() > 0);
        assertTrue("height > 0", r.getHeight() > 0);
        assertTrue("svg xml should contain <svg",
                r.getSvgXml() != null && r.getSvgXml().contains("<svg"));
    }

    @Test
    public void testSimpleSequenceDiagramRenders() throws IOException {
        String puml = "@startuml\nA -> B: hello\nB -> A: world\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        assertNotNull(r.getRoot());
        assertTrue(r.getWidth() > 0);
        assertTrue(r.getHeight() > 0);
    }

    @Test
    public void testNullPumlRejected() {
        try {
            PlantUmlSvgRenderer.render(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        } catch (IOException ex) {
            fail("Unexpected IOException: " + ex.getMessage());
        }
    }

    @Test
    public void testLinkAreasEmptyWhenNoLinks() throws IOException {
        String puml = "@startuml\nclass Foo\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        assertNotNull("getLinkAreas should never be null", r.getLinkAreas());
        assertTrue("should be empty when no [[url]] embedded",
                r.getLinkAreas().isEmpty());
    }

    @Test
    public void testLinkAreasExtractedFromPlantUmlUrl() throws IOException {
        String puml = "@startuml\n"
                + "class Foo [[juml://class/com.example.Foo]]\n"
                + "class Bar [[juml://class/com.example.Bar]]\n"
                + "Foo --> Bar\n"
                + "@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        java.util.List<PlantUmlSvgRenderer.LinkArea> areas = r.getLinkAreas();
        assertNotNull(areas);
        assertTrue("should have at least two link areas, got " + areas.size(),
                areas.size() >= 2);
        boolean foo = false;
        boolean bar = false;
        for (PlantUmlSvgRenderer.LinkArea a : areas) {
            if ("juml://class/com.example.Foo".equals(a.getHref())) {
                foo = true;
                assertTrue(a.getWidth() > 0);
                assertTrue(a.getHeight() > 0);
            }
            if ("juml://class/com.example.Bar".equals(a.getHref())) {
                bar = true;
            }
        }
        assertTrue("Foo link area not found", foo);
        assertTrue("Bar link area not found", bar);
    }

    @Test
    public void testTextItemsExtracted() throws IOException {
        String puml = "@startuml\nclass Foo\nclass Bar\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        java.util.List<PlantUmlSvgRenderer.SvgTextItem> items = r.getTextItems();
        assertNotNull("getTextItems should never be null", items);
        assertFalse("should have at least one text item for class names", items.isEmpty());
        boolean hasFoo = false;
        boolean hasBar = false;
        for (PlantUmlSvgRenderer.SvgTextItem item : items) {
            if ("Foo".equals(item.getText())) {
                hasFoo = true;
                assertTrue("x > 0", item.getX() > 0);
                assertTrue("y > 0", item.getY() > 0);
            }
            if ("Bar".equals(item.getText())) {
                hasBar = true;
            }
        }
        assertTrue("Foo text item not found", hasFoo);
        assertTrue("Bar text item not found", hasBar);
    }

    @Test
    public void testTextItemsHaveEstimatedBounds() throws IOException {
        // 図内検索のハイライト矩形のため、テキスト要素は幅・高さの推定値を持つ。
        String puml = "@startuml\nclass Foo\nclass Bar\n@enduml\n";
        PlantUmlSvgRenderer.RenderedSvg r = PlantUmlSvgRenderer.render(puml);
        assertNotNull(r);
        boolean checked = false;
        for (PlantUmlSvgRenderer.SvgTextItem item : r.getTextItems()) {
            if ("Foo".equals(item.getText())) {
                checked = true;
                assertTrue("width should be estimated > 0, got " + item.getWidth(),
                        item.getWidth() > 0);
                assertTrue("height should be estimated > 0, got " + item.getHeight(),
                        item.getHeight() > 0);
            }
        }
        assertTrue("Foo text item not found", checked);
    }
}
