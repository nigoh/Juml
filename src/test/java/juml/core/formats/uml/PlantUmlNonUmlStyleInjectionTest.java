// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code @startuml} 以外の図種 (マインドマップ / WBS / salt / ガント / JSON / YAML) にも
 * スタイル行と {@code scale max} が届くことの回帰テスト。
 *
 * <p>修正前は {@link PlantUmlRenderer#injectLayout(String)} /
 * {@link PlantUmlRenderer#injectScaleMax(String, int)} が {@code puml.indexOf("@startuml")}
 * だけを見ていたため、これらの図種はテーマ・フォント・{@code scale max} を一切受け取れなかった。
 * 結果として (1) 日本語フォント fallback が効かず文字化けし、(2) 巨大な図の PNG が
 * {@code PLANTUML_LIMIT_SIZE} で切れていた。</p>
 *
 * <p>ただし向き指定は {@code @startuml} 専用である。実機 (PlantUML 1.2026.6) で確認した
 * 挙動は「wbs / gantt = 構文エラー」「salt = PlantUML 本体がクラッシュ」
 * 「json = 指定行をデータと誤読して図が壊れる」なので、非 UML 図には出さない。
 * 同様に {@code !pragma layout smetana} は Graphviz を使う {@code @startuml} 専用とする。</p>
 */
public class PlantUmlNonUmlStyleInjectionTest {

    private static final String MINDMAP = "@startmindmap\n* ルート\n** 子\n@endmindmap\n";
    private static final String WBS = "@startwbs\n* Root\n** Child\n@endwbs\n";
    private static final String SALT = "@startsalt\n{\n  Name | \"  \"\n}\n@endsalt\n";
    private static final String GANTT = "@startgantt\n[T1] lasts 3 days\n@endgantt\n";
    private static final String JSON = "@startjson\n{\"a\": 1}\n@endjson\n";
    private static final String YAML = "@startyaml\na: 1\n@endyaml\n";

    private String savedFallbackFont;

    @Before
    public void setUp() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        savedFallbackFont = PlantUmlRenderer.getFallbackFontName();
        PlantUmlRenderer.setFallbackFontName("DejaVu Sans");
    }

    @After
    public void tearDown() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        PlantUmlRenderer.setFallbackFontName(savedFallbackFont);
    }

    private static DiagramStyle styleWithDirection(DiagramStyle.Direction d) {
        DiagramStyle s = new DiagramStyle();
        s.setDirection(d);
        return s;
    }

    // --- スタイル行が届くこと ---------------------------------------------------

    @Test
    public void fontFallbackReachesNonUmlDiagrams() {
        for (String puml : new String[] {MINDMAP, WBS, SALT, GANTT, JSON, YAML}) {
            String out = PlantUmlRenderer.injectLayout(puml, new DiagramStyle());
            assertTrue("フォント fallback が挿入されること: " + out,
                    out.contains("skinparam defaultFontName DejaVu Sans"));
        }
    }

    @Test
    public void styleLandsRightAfterStartDirective() {
        String out = PlantUmlRenderer.injectLayout(MINDMAP, new DiagramStyle());
        String[] lines = out.split("\n", -1);
        assertEquals("1 行目は開始ディレクティブのまま", "@startmindmap", lines[0]);
        assertTrue("2 行目から挿入される: " + out, lines[1].startsWith("skinparam "));
    }

    @Test
    public void diagramNameOnStartLineIsPreserved() {
        String out = PlantUmlRenderer.injectLayout(
                "@startmindmap Plan\n* Root\n@endmindmap\n", new DiagramStyle());
        assertTrue("図名付きの開始行を壊さないこと: " + out,
                out.startsWith("@startmindmap Plan\n"));
    }

    @Test
    public void scaleMaxReachesNonUmlDiagrams() {
        for (String puml : new String[] {MINDMAP, WBS, SALT, GANTT, JSON, YAML}) {
            String out = PlantUmlRenderer.injectScaleMax(puml, 4096);
            assertTrue("scale max が挿入されること: " + out,
                    out.contains("scale max 4096*4096"));
        }
    }

    @Test
    public void scaleMaxStillRespectsExplicitScale() {
        String out = PlantUmlRenderer.injectScaleMax(
                "@startmindmap\nscale 2\n* Root\n@endmindmap\n", 4096);
        assertFalse("ユーザ指定の scale があるときは足さない: " + out,
                out.contains("scale max"));
    }

    // --- 向き指定 / Smetana は @startuml 専用 -----------------------------------

    @Test
    public void directionIsNeverInjectedIntoNonUmlDiagrams() {
        for (String puml : new String[] {MINDMAP, WBS, SALT, GANTT, JSON, YAML}) {
            String out = PlantUmlRenderer.injectLayout(
                    puml, styleWithDirection(DiagramStyle.Direction.LEFT_TO_RIGHT));
            assertFalse("非 UML 図に向き指定を入れない (壊れる): " + out,
                    out.contains("direction"));
        }
    }

    @Test
    public void smetanaPragmaIsNeverInjectedIntoNonUmlDiagrams() {
        for (String puml : new String[] {MINDMAP, WBS, SALT, GANTT, JSON, YAML}) {
            String out = PlantUmlRenderer.injectLayout(puml, new DiagramStyle());
            assertFalse("非 UML 図に Smetana 指定は不要: " + out,
                    out.contains("!pragma layout"));
        }
    }

    @Test
    public void umlKeepsSmetanaAndDirection() {
        String out = PlantUmlRenderer.injectLayout(
                "@startuml\nclass A\nclass B\nA --> B\n@enduml\n",
                styleWithDirection(DiagramStyle.Direction.LEFT_TO_RIGHT));
        assertTrue("@startuml では Smetana を出す: " + out, out.contains("!pragma layout smetana"));
        assertTrue("@startuml では向き指定を出す: " + out, out.contains("left to right direction"));
    }

    // --- 本文が別言語の図種には一切触らない -------------------------------------

    @Test
    public void rawSourceDiagramsAreLeftUntouched() {
        for (String puml : new String[] {
            "@startdot\ndigraph G { a -> b }\n@enddot\n",
            "@startditaa\n+---+\n| a |\n+---+\n@endditaa\n",
            "@startlatex\n\\sum_{i=0}^{n}\n@endlatex\n",
            "@startchen\nentity E {\n  a key\n}\n@endchen\n"}) {
            assertEquals("生ソース図種は素通し", puml,
                    PlantUmlRenderer.injectLayout(puml, new DiagramStyle()));
            assertEquals("生ソース図種は素通し (scale)", puml,
                    PlantUmlRenderer.injectScaleMax(puml, 4096));
        }
    }

    // --- 実レンダリング回帰 -----------------------------------------------------

    private static void assertRenders(String label, String puml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = out.toString(StandardCharsets.UTF_8);
        assertTrue(label + " の SVG が空でない", svg.contains("<svg"));
    }

    @Test
    public void nonUmlDiagramsStillRenderWithInjectedStyle() throws Exception {
        // renderSvg は内部で injectLayout を通す。向き指定込みのスタイルでも
        // 非 UML 図が壊れないこと (修正前の素通しと同じく成功する) を実機で確認する。
        PlantUmlRenderer.setStyle(styleWithDirection(DiagramStyle.Direction.LEFT_TO_RIGHT));
        assertRenders("mindmap", MINDMAP);
        assertRenders("wbs", WBS);
        assertRenders("salt", SALT);
        assertRenders("gantt", GANTT);
        assertRenders("json", JSON);
        assertRenders("yaml", YAML);
    }

    @Test
    public void mindmapRendersWithScaleMaxApplied() throws Exception {
        PlantUmlRenderer.setStyle(new DiagramStyle());
        assertRenders("mindmap+scale", PlantUmlRenderer.injectScaleMax(MINDMAP, 4096));
    }
}
