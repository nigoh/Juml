// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * PlantUmlNavigationGraphDiagram のユニットテスト。
 */
public class PlantUmlNavigationGraphDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        PlantUmlNavigationGraphDiagram.generate(null);
    }

    @Test
    public void testEmptyDestinations() {
        AndroidNavigationGraphInfo info = new AndroidNavigationGraphInfo();
        info.setFileName("empty_nav.xml");
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("no destinations found"));
    }

    @Test
    public void testContainsStartupml() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("@enduml"));
    }

    @Test
    public void testStartDestinationArrow() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("start destination arrow should be present",
                puml.contains("[*] -->"));
    }

    @Test
    public void testStateNodesPresent() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("state declarations should be present", puml.contains("state \""));
    }

    @Test
    public void testActionArrowPresent() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("action arrow should be present", puml.contains(" --> "));
    }

    @Test
    public void testSkinparamPresent() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue(puml.contains("skinparam state"));
        assertTrue(puml.contains("<<fragment>>"));
    }

    @Test
    public void testLegendIncluded() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.includeLegend = true;
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertTrue(puml.contains("legend top left"));
        assertTrue(puml.contains("endlegend"));
    }

    @Test
    public void testLegendExcluded() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertFalse(puml.contains("legend top left"));
    }

    @Test
    public void testArgumentsShown() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.showArguments = true;
        opts.includeLegend = false;
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertTrue("arguments separator should be present when showArguments=true",
                puml.contains("--"));
        assertTrue("argument type should appear", puml.contains("integer"));
    }

    @Test
    public void testArgumentsHidden() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.showArguments = false;
        opts.includeLegend = false;
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertFalse("argument type should not appear when showArguments=false",
                puml.contains("itemId: integer"));
    }

    @Test
    public void testDeepLinksShown() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.showDeepLinks = true;
        opts.includeLegend = false;
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertTrue("deep link note should be present", puml.contains("note left of"));
        assertTrue("deep link URI should appear", puml.contains("example.com"));
    }

    @Test
    public void testDeepLinkDiagramActuallyRenders() throws IOException {
        // 添付ノートに別名を付けると PlantUML 構文エラーで図全体が失敗する回帰。
        // deep link を持つ実サンプルを同梱 PlantUML で SVG レンダリングし、例外が
        // 出ない (= 図が壊れていない) ことを確認する。
        AndroidNavigationGraphInfo info = parseFullSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertTrue("前提: deep link ノートを含む", puml.contains("note left of"));
        assertFalse("添付ノートに 'as' 別名を付けない", puml.contains("note left of ")
                && puml.matches("(?s).*note left of \\S+ as \\S+.*"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        juml.core.formats.uml.PlantUmlRenderer.renderSvg(puml, out);
        String svg = out.toString(StandardCharsets.UTF_8.name());
        assertTrue("SVG が生成される", svg.contains("<svg"));
    }

    @Test
    public void testDeepLinksHidden() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.showDeepLinks = false;
        opts.includeLegend = false;
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertFalse("deep link note should not appear when showDeepLinks=false",
                puml.contains("note left of"));
    }

    @Test
    public void testCustomTitle() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        PlantUmlNavigationGraphDiagram.Options opts = new PlantUmlNavigationGraphDiagram.Options();
        opts.title = "My Custom Title";
        String puml = PlantUmlNavigationGraphDiagram.generate(info, opts);
        assertTrue(puml.contains("My Custom Title"));
    }

    @Test
    public void testDefaultTitleContainsFileName() throws IOException {
        AndroidNavigationGraphInfo info = parseSimpleSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("title should contain filename", puml.contains("simple_nav.xml"));
    }

    @Test
    public void testDefaultTitleFallsBackToLabelThenGraphId() {
        // fileName が無いとき (単一 XML 直接指定など) は android:label を使う
        AndroidNavigationGraphInfo withLabel = new AndroidNavigationGraphInfo();
        withLabel.setLabel("Full Navigation");
        withLabel.setGraphId("full_nav");
        String puml = PlantUmlNavigationGraphDiagram.generate(withLabel);
        assertTrue("title should use android:label when fileName is empty",
                puml.contains("Full Navigation"));
        assertFalse("should not degrade to (unnamed)", puml.contains("(unnamed)"));

        // label も無ければ graph id を使う
        AndroidNavigationGraphInfo idOnly = new AndroidNavigationGraphInfo();
        idOnly.setGraphId("auth_graph");
        String puml2 = PlantUmlNavigationGraphDiagram.generate(idOnly);
        assertTrue("title should fall back to graph id", puml2.contains("auth_graph"));
    }

    @Test
    public void testGlobalActionArrow() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("global action should produce [*] --> arrow",
                puml.contains("(global)"));
    }

    @Test
    public void testActivityStereotype() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("activity stereotype should be present",
                puml.contains("<<activity>>"));
    }

    @Test
    public void testDialogStereotype() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("dialog stereotype should be present",
                puml.contains("<<dialog>>"));
    }

    @Test
    public void testNavigationStereotype() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("navigation stereotype should be present",
                puml.contains("<<navigation>>"));
    }

    @Test
    public void testIncludeStereotype() throws IOException {
        AndroidNavigationGraphInfo info = parseFullSample();
        String puml = PlantUmlNavigationGraphDiagram.generate(info);
        assertTrue("include stereotype should be present",
                puml.contains("<<include>>"));
    }

    private static AndroidNavigationGraphInfo parseSimpleSample() throws IOException {
        String xml = readSample("simple_nav.xml");
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        info.setFileName("simple_nav.xml");
        info.setModuleName("app");
        info.setSourceSet("main");
        return info;
    }

    private static AndroidNavigationGraphInfo parseFullSample() throws IOException {
        String xml = readSample("full_nav.xml");
        AndroidNavigationGraphInfo info = AndroidNavigationGraphParser.parse(xml);
        info.setFileName("full_nav.xml");
        info.setModuleName("app");
        info.setSourceSet("main");
        return info;
    }

    private static String readSample(String name) throws IOException {
        try (InputStream in = PlantUmlNavigationGraphDiagramTest.class
                .getResourceAsStream("/samples/navigation/" + name)) {
            assertNotNull("sample resource not found: " + name, in);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
