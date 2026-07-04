// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.PlantUmlSvgRenderer;
import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramExport} の画像合成・ファイル名基底ロジックを検証する
 * (SVG レンダリングはオフスクリーン; headless で動く)。
 */
public class DiagramExportTest {

    private static RenderedSvg render(String puml) throws Exception {
        return PlantUmlSvgRenderer.render(puml);
    }

    @Test
    public void baseName_stripsDirAndExtension() {
        assertEquals("Foo", DiagramExport.baseName("src/main/java/pkg/Foo.java"));
        assertEquals("Foo", DiagramExport.baseName("Foo"));
        assertEquals("diagram", DiagramExport.baseName(null));
        assertEquals("diagram", DiagramExport.baseName(""));
    }

    @Test
    public void composite_placesBothSidesWithHeaderMargin() throws Exception {
        RenderedSvg svg = render("@startuml\nclass A\nclass B\nA --> B\n@enduml\n");
        BufferedImage img = DiagramExport.composite(svg, svg, "old", "new");
        assertNotNull(img);
        // 2 カラム分の幅と、ヘッダ + 本体の高さがあること。
        assertTrue("横並びなので単体図の 2 倍近い幅",
                img.getWidth() >= (int) svg.getWidth() * 2);
        assertTrue(img.getHeight() > (int) svg.getHeight());
    }

    @Test
    public void composite_absentSideUsesPlaceholderWithoutCrashing() throws Exception {
        RenderedSvg svg = render("@startuml\nclass A\n@enduml\n");
        BufferedImage left = DiagramExport.composite(null, svg, "old", "new");
        BufferedImage right = DiagramExport.composite(svg, null, "old", "new");
        assertNotNull("旧側 null でもプレースホルダで合成できる", left);
        assertNotNull("新側 null でもプレースホルダで合成できる", right);
        assertTrue(left.getWidth() > 0 && left.getHeight() > 0);
        assertTrue(right.getWidth() > 0 && right.getHeight() > 0);
    }
}
