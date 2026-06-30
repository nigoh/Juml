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
 * PlantUmlLayoutDiagram のユニットテスト。
 */
public class PlantUmlLayoutDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullLayout() {
        PlantUmlLayoutDiagram.generate(null);
    }

    @Test
    public void testNullRootProducesMinimalDiagram() {
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setFileName("empty.xml");
        String puml = PlantUmlLayoutDiagram.generate(info);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.trim().endsWith("@enduml"));
        assertTrue(puml.contains("no view hierarchy parsed"));
    }

    @Test
    public void testSimpleHierarchyGeneratesNestedRectangles() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        info.setFileName("activity_main.xml");
        String puml = PlantUmlLayoutDiagram.generate(info);
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.contains("@enduml"));
        // ルートタイトル
        assertTrue(puml.contains("title Layout: activity_main.xml"));
        // ノード種別
        assertTrue("ViewGroup ステレオタイプが出る", puml.contains("<<ViewGroup>>"));
        assertTrue("View ステレオタイプが出る", puml.contains("<<View>>"));
        // id 表示
        assertTrue(puml.contains("id: root"));
        assertTrue(puml.contains("id: ok"));
        // text 表示
        assertTrue(puml.contains("text: Hello World"));
        // 寸法表示 (match_parent → MP, wrap_content → WC)
        assertTrue(puml.contains("size: MP x MP") || puml.contains("size: MP x WC"));
    }

    @Test
    public void testRectangleCountMatchesNodeCount() throws IOException {
        // activity_main.xml は 5 ノード (root LinearLayout + TextView + LinearLayout + 2 Buttons)
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        String puml = PlantUmlLayoutDiagram.generate(info);
        int rects = countOccurrences(puml, "rectangle \"");
        assertEquals("rectangle 出現数が想定ノード数と一致する", 5, rects);
    }

    @Test
    public void testMaxNodesTruncates() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        PlantUmlLayoutDiagram.Options opts = new PlantUmlLayoutDiagram.Options();
        opts.maxNodes = 2;
        String puml = PlantUmlLayoutDiagram.generate(info, opts);
        assertTrue("truncated note が出る", puml.contains("truncated (maxNodes=2)"));
    }

    @Test
    public void testMaxDepthCollapses() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        PlantUmlLayoutDiagram.Options opts = new PlantUmlLayoutDiagram.Options();
        opts.maxDepth = 1;
        String puml = PlantUmlLayoutDiagram.generate(info, opts);
        assertTrue("collapsed note が出る", puml.contains("collapsed (maxDepth=1)"));
        assertTrue("children... のヒントが出る", puml.contains("children..."));
    }

    @Test
    public void testIncludeAndFragmentAndMergeRendering() throws IOException {
        AndroidLayoutInfo info = parseSample("layout_with_include.xml");
        info.setFileName("layout_with_include.xml");
        String puml = PlantUmlLayoutDiagram.generate(info);
        assertTrue("merge ステレオタイプ", puml.contains("<<merge>>"));
        assertTrue("include ステレオタイプ", puml.contains("<<include>>"));
        assertTrue("fragment ステレオタイプ", puml.contains("<<fragment>>"));
        assertTrue("include layout ref が表示される",
                puml.contains("layout: @layout/common_header"));
        assertTrue("fragment クラス名 (short) が表示される",
                puml.contains("class: ContentFragment"));
    }

    @Test
    public void testLegendIncludedByDefault() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        String puml = PlantUmlLayoutDiagram.generate(info);
        assertTrue(puml.contains("legend top left"));
        assertTrue(puml.contains("endlegend"));
    }

    @Test
    public void testLegendCanBeDisabled() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        PlantUmlLayoutDiagram.Options opts = new PlantUmlLayoutDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlLayoutDiagram.generate(info, opts);
        assertFalse(puml.contains("legend top left"));
    }

    @Test
    public void testTextTruncation() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        // 短い text は切り詰められない
        String puml = PlantUmlLayoutDiagram.generate(info);
        assertTrue(puml.contains("text: Hello World"));

        PlantUmlLayoutDiagram.Options opts = new PlantUmlLayoutDiagram.Options();
        opts.textMaxLen = 3;
        String puml2 = PlantUmlLayoutDiagram.generate(info, opts);
        assertTrue("3 文字で切り詰め", puml2.contains("text: Hel..."));
    }

    private static AndroidLayoutInfo parseSample(String name) throws IOException {
        String xml = readSample(name);
        AndroidLayoutInfo info = AndroidLayoutParser.parse(xml);
        info.setFileName(name);
        return info;
    }

    private static String readSample(String name) throws IOException {
        try (InputStream in = PlantUmlLayoutDiagramTest.class
                .getResourceAsStream("/samples/layouts/" + name)) {
            if (in == null) {
                throw new IOException("resource not found: " + name);
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
