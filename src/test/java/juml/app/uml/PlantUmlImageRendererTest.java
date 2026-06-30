// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PlantUmlImageRenderer} のユニットテスト。
 * 同梱 PlantUML が PNG 出力経路で {@link BufferedImage} を返せることを確認する。
 */
public class PlantUmlImageRendererTest {

    @Test
    public void testSimpleClassDiagramRenders() throws IOException {
        String puml = "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml\n";
        BufferedImage img = PlantUmlImageRenderer.toBufferedImage(puml);
        assertNotNull("BufferedImage should not be null", img);
        assertTrue("width > 0", img.getWidth() > 0);
        assertTrue("height > 0", img.getHeight() > 0);
    }

    @Test
    public void testSimpleSequenceDiagramRenders() throws IOException {
        String puml = "@startuml\nA -> B: hello\nB -> A: world\n@enduml\n";
        BufferedImage img = PlantUmlImageRenderer.toBufferedImage(puml);
        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    public void testNullPumlRejected() {
        try {
            PlantUmlImageRenderer.toBufferedImage(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        } catch (IOException ex) {
            fail("Unexpected IOException: " + ex.getMessage());
        }
    }
}
