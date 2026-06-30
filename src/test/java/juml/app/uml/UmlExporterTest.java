// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link UmlExporter} のユニットテスト。
 */
public class UmlExporterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String SAMPLE_PUML =
            "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml\n";

    @Test
    public void testExportPuml() throws Exception {
        File out = tmp.newFile("out.puml");
        UmlExporter.export(UmlExporter.Format.PUML, out, SAMPLE_PUML, null);
        String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertEquals(SAMPLE_PUML, text);
    }

    @Test
    public void testExportSvg() throws Exception {
        File out = tmp.newFile("out.svg");
        UmlExporter.export(UmlExporter.Format.SVG, out, SAMPLE_PUML, null);
        assertTrue("svg file must exist", out.exists());
        assertTrue("svg file must have content", out.length() > 0);
        String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("<svg"));
    }

    @Test
    public void testExportPng() throws Exception {
        // 単純な BufferedImage を作って PNG として書き出す
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        File out = tmp.newFile("out.png");
        UmlExporter.export(UmlExporter.Format.PNG, out, null, img);
        assertTrue(out.exists());
        assertTrue(out.length() > 0);
        BufferedImage read = ImageIO.read(out);
        assertNotNull(read);
        assertEquals(40, read.getWidth());
        assertEquals(30, read.getHeight());
    }

    @Test
    public void testFormatFromFileName() {
        assertEquals(UmlExporter.Format.SVG, UmlExporter.Format.fromFileName("a.svg"));
        assertEquals(UmlExporter.Format.SVG, UmlExporter.Format.fromFileName("A.SVG"));
        assertEquals(UmlExporter.Format.PNG, UmlExporter.Format.fromFileName("a.png"));
        assertEquals(UmlExporter.Format.PUML, UmlExporter.Format.fromFileName("a.puml"));
        assertEquals(UmlExporter.Format.PUML, UmlExporter.Format.fromFileName("a.plantuml"));
        assertEquals(UmlExporter.Format.PUML, UmlExporter.Format.fromFileName("a.txt"));
        assertEquals(null, UmlExporter.Format.fromFileName("a.jpg"));
        assertEquals(null, UmlExporter.Format.fromFileName(null));
    }

    @Test
    public void testSvgRequiresPuml() throws Exception {
        try {
            UmlExporter.export(UmlExporter.Format.SVG, tmp.newFile("a.svg"), null, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }

    @Test
    public void testPngRequiresImage() throws Exception {
        try {
            UmlExporter.export(UmlExporter.Format.PNG, tmp.newFile("a.png"),
                    SAMPLE_PUML, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }
}
