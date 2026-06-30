// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlRenderer#injectScaleMax(String, int)} と画像サイズ上限ヘルパーの検証。
 *
 * <p>PNG エクスポートで巨大な図がキャンバス上限で切り詰められないよう、{@code scale max} を
 * 自動注入する。ここでは挿入位置・既存 scale の尊重に加え、{@code scale max N*N} が有効な
 * PlantUML 構文であることを実レンダリングで実証する (無効なら renderSvg がエラー SVG を検出)。</p>
 */
public class PlantUmlScaleInjectionTest {

    @After
    public void resetRendererImpl() {
        PlantUmlRenderer.setRendererImplForTest(null);
    }

    @Test
    public void injectAddsScaleMaxAfterStartuml() {
        String out = PlantUmlRenderer.injectScaleMax("@startuml\nclass A\n@enduml\n", 4096);
        assertTrue(out.contains("scale max 4096*4096"));
        int startuml = out.indexOf("@startuml");
        int scale = out.indexOf("scale max");
        int body = out.indexOf("class A");
        assertTrue("scale must be after @startuml and before body",
                startuml < scale && scale < body);
    }

    @Test
    public void injectSkipsWhenScaleAlreadyPresent() {
        String src = "@startuml\nscale 1.5\nclass A\n@enduml\n";
        assertEquals(src, PlantUmlRenderer.injectScaleMax(src, 4096));
    }

    @Test
    public void injectNoopWithoutStartumlOrBadLimit() {
        assertEquals("class A", PlantUmlRenderer.injectScaleMax("class A", 4096));
        String src = "@startuml\nclass A\n@enduml\n";
        assertEquals(src, PlantUmlRenderer.injectScaleMax(src, 0));
        assertNull(PlantUmlRenderer.injectScaleMax(null, 4096));
    }

    @Test
    public void imageLimitIsPositiveAndConfigurable() {
        String saved = System.getProperty("PLANTUML_LIMIT_SIZE");
        try {
            System.clearProperty("PLANTUML_LIMIT_SIZE");
            assertTrue(PlantUmlRenderer.imageLimit() > 0);
            System.setProperty("PLANTUML_LIMIT_SIZE", "8192");
            assertEquals(8192, PlantUmlRenderer.imageLimit());
            System.setProperty("PLANTUML_LIMIT_SIZE", "garbage");
            assertEquals(PlantUmlRenderer.DEFAULT_IMAGE_LIMIT, PlantUmlRenderer.imageLimit());
        } finally {
            if (saved != null) {
                System.setProperty("PLANTUML_LIMIT_SIZE", saved);
            } else {
                System.clearProperty("PLANTUML_LIMIT_SIZE");
            }
        }
    }

    @Test
    public void scaleMaxDirectiveRendersWithoutError() throws IOException {
        PlantUmlRenderer.setRendererImplForTest(null);
        String puml = PlantUmlRenderer.injectScaleMax("@startuml\nclass Foo\n@enduml\n", 4096);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        assertTrue("SVG should be produced", out.size() > 0);
        assertFalse("scale max N*N must be valid syntax (no error SVG)",
                PlantUmlRenderer.isErrorSvg(out.toByteArray()));
    }
}
