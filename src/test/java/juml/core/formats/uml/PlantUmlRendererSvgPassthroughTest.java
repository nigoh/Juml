// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlRenderer#looksLikeSvg(String)} の判定と SVG パススルーのテスト。
 */
public class PlantUmlRendererSvgPassthroughTest {

    @Test
    public void detectsSvgWithXmlDeclaration() {
        assertTrue(PlantUmlRenderer.looksLikeSvg(
                "<?xml version=\"1.0\"?>\n<svg xmlns=\"...\"></svg>"));
    }

    @Test
    public void detectsBareSvgElement() {
        assertTrue(PlantUmlRenderer.looksLikeSvg("  \n<svg></svg>"));
    }

    @Test
    public void detectsSvgAfterDoctypeAndComment() {
        assertTrue(PlantUmlRenderer.looksLikeSvg(
                "<?xml version=\"1.0\"?><!-- c --><!DOCTYPE svg><svg/>"));
    }

    @Test
    public void rejectsPlantUmlSource() {
        assertFalse(PlantUmlRenderer.looksLikeSvg("@startuml\nclass A\n@enduml"));
        assertFalse(PlantUmlRenderer.looksLikeSvg("@startsalt\n{ }\n@endsalt"));
        assertFalse(PlantUmlRenderer.looksLikeSvg(null));
        assertFalse(PlantUmlRenderer.looksLikeSvg(""));
        assertFalse(PlantUmlRenderer.looksLikeSvg("<not-svg/>"));
    }

    @Test
    public void renderSvgWritesThroughVerbatim() throws IOException {
        String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                + "<rect width=\"10\" height=\"10\"/></svg>\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(svg, out);
        String written = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("SVG はそのまま書き出される", svg, written);
    }
}
