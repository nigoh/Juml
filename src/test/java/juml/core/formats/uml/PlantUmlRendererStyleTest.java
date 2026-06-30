// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlRenderer#injectLayout(String)} のスタイル挿入挙動のテスト。
 *
 * <p>静的なグローバル状態 ({@code currentStyle}) に依存するため、各テストで
 * 既定値へリセットする。</p>
 */
public class PlantUmlRendererStyleTest {

    private String savedFallbackFont;

    @Before
    public void resetStyle() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        // 実行環境のフォント有無に依存しないよう、フォールバックフォントを無効化する。
        savedFallbackFont = PlantUmlRenderer.getFallbackFontName();
        PlantUmlRenderer.setFallbackFontName("");
    }

    @After
    public void tearDownStyle() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        PlantUmlRenderer.setFallbackFontName(savedFallbackFont);
    }

    @Test
    public void defaultStyleInjectsOnlyLayoutPragma() {
        String puml = "@startuml\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);
        assertEquals(
                "@startuml\n!pragma layout smetana\nclass A\n@enduml\n",
                out);
    }

    @Test
    public void styleLinesAreInsertedAfterLayoutPragma() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("cerulean");
        s.setBackgroundColor("#1E1E1E");
        PlantUmlRenderer.setStyle(s);

        String puml = "@startuml\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);

        assertTrue(out, out.startsWith("@startuml\n!pragma layout smetana\n"));
        assertTrue(out, out.contains("!theme cerulean\n"));
        assertTrue(out, out.contains("skinparam backgroundColor #1E1E1E\n"));
        // 挿入位置: @startuml の直後、本体の前
        int classIdx = out.indexOf("class A");
        int themeIdx = out.indexOf("!theme cerulean");
        assertTrue("theme should appear before class body",
                themeIdx >= 0 && themeIdx < classIdx);
    }

    @Test
    public void existingLayoutPragmaIsPreservedAndStyleStillInjected() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("hacker");
        PlantUmlRenderer.setStyle(s);

        String puml = "@startuml\n!pragma layout dot\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);

        // 既存 layout 行は重複しない
        assertFalse(out, out.contains("!pragma layout smetana"));
        assertTrue(out, out.contains("!pragma layout dot"));
        assertTrue(out, out.contains("!theme hacker\n"));
    }

    @Test
    public void noStartumlTagYieldsNoInjection() {
        String puml = "class A {}\n";
        DiagramStyle s = new DiagramStyle();
        s.setTheme("plain");
        assertEquals(puml, PlantUmlRenderer.injectLayout(puml, s));
    }

    @Test
    public void nullInputReturnsNull() {
        assertNull(PlantUmlRenderer.injectLayout(null));
    }

    @Test
    public void graphvizAvailableSkipsSmetanaPragma() {
        PlantUmlRenderer.setGraphvizAvailable(true);
        String puml = "@startuml\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);
        assertFalse("smetana should not be injected when graphviz is available",
                out.contains("!pragma layout smetana"));
    }

    @Test
    public void graphvizAvailablePreservesExplicitDotPragma() {
        PlantUmlRenderer.setGraphvizAvailable(true);
        String puml = "@startuml\n!pragma layout dot\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);
        assertTrue(out, out.contains("!pragma layout dot"));
        assertFalse(out, out.contains("!pragma layout smetana"));
    }

    @Test
    public void fallbackFontInjectedWhenStyleHasNoFont() {
        PlantUmlRenderer.setFallbackFontName("Noto Sans CJK JP");
        String puml = "@startuml\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);
        assertTrue(out, out.contains("skinparam defaultFontName Noto Sans CJK JP\n"));
    }

    @Test
    public void explicitFontOverridesFallback() {
        PlantUmlRenderer.setFallbackFontName("Noto Sans CJK JP");
        DiagramStyle s = new DiagramStyle();
        s.setFontName("Helvetica");
        PlantUmlRenderer.setStyle(s);
        String puml = "@startuml\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);
        assertTrue(out, out.contains("skinparam defaultFontName Helvetica\n"));
        assertFalse(out, out.contains("Noto Sans CJK JP"));
    }

    @Test
    public void noFallbackFontMeansNoFontLine() {
        PlantUmlRenderer.setFallbackFontName("");
        String puml = "@startuml\nclass A\n@enduml\n";
        String out = PlantUmlRenderer.injectLayout(puml);
        assertFalse(out, out.contains("skinparam defaultFontName"));
    }

    @Test
    public void setStyleWithNullResetsToDefaults() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("plain");
        PlantUmlRenderer.setStyle(s);
        PlantUmlRenderer.setStyle(null);
        assertEquals("", PlantUmlRenderer.getStyle().getTheme());
    }

    @Test
    public void setStyleStoresIndependentCopy() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("plain");
        PlantUmlRenderer.setStyle(s);
        s.setTheme("hacker");
        assertEquals("plain", PlantUmlRenderer.getStyle().getTheme());
    }
}
