// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.ErrorListener;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlSmaliClassDiagram} のユニットテスト。
 */
public class PlantUmlSmaliClassDiagramTest {

    private static ApkAnalysis decoded() throws IOException {
        return ApktoolDecodedAnalyzer.analyze(
                new File("src/test/resources/samples/apk/decoded"), ErrorListener.silent());
    }

    @Test
    public void testWellFormed() throws IOException {
        String puml = PlantUmlSmaliClassDiagram.generate(decoded(), null);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.trim().endsWith("@enduml"));
    }

    @Test
    public void testContainsClassesAndPackages() throws IOException {
        String puml = PlantUmlSmaliClassDiagram.generate(decoded(), null);
        assertTrue(puml.contains("MainActivity"));
        assertTrue(puml.contains("MainPresenter"));
        assertTrue(puml.contains("package \"com.example.app\""));
    }

    @Test
    public void testInterfaceRendered() throws IOException {
        String puml = PlantUmlSmaliClassDiagram.generate(decoded(), null);
        assertTrue(puml.contains("interface \"Repository\""));
    }

    @Test
    public void testExternalSupertypeShown() throws IOException {
        PlantUmlSmaliClassDiagram.Options o = new PlantUmlSmaliClassDiagram.Options();
        o.showExternalSupertypes = true;
        String puml = PlantUmlSmaliClassDiagram.generate(decoded().getClasses(), o);
        // MainActivity extends AppCompatActivity (スコープ外) が external ノードになる
        assertTrue(puml.contains("<<external>>"));
        assertTrue(puml.contains("AppCompatActivity"));
        assertTrue(puml.contains("--|>"));
    }

    @Test
    public void testExternalSupertypeHidden() throws IOException {
        PlantUmlSmaliClassDiagram.Options o = new PlantUmlSmaliClassDiagram.Options();
        o.showExternalSupertypes = false;
        String puml = PlantUmlSmaliClassDiagram.generate(decoded().getClasses(), o);
        assertFalse(puml.contains("AppCompatActivity"));
    }

    @Test
    public void testConstructorRenderedAsSimpleName() throws IOException {
        String puml = PlantUmlSmaliClassDiagram.generate(decoded(), null);
        // <init> はクラス単純名で表示される (<init> 文字列は出力されない)
        assertFalse(puml.contains("<init>"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullThrows() {
        PlantUmlSmaliClassDiagram.generate((java.util.List<SmaliClassInfo>) null, null);
    }

    /** 同梱 PlantUML が生成図の構文を受理し SVG にレンダリングできることを確認する。 */
    @Test
    public void testRendersToSvg() throws IOException {
        String puml = PlantUmlSmaliClassDiagram.generate(decoded(), null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, bos);
        String svg = bos.toString(StandardCharsets.UTF_8);
        assertTrue("expected SVG output", svg.contains("<svg"));
        assertTrue(svg.contains("MainActivity"));
    }
}
