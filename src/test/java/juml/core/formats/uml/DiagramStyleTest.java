// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramStyle#toPlantUmlPrelude()} の出力規則とコピー動作のテスト。
 */
public class DiagramStyleTest {

    @Test
    public void emptyStyleProducesEmptyPrelude() {
        DiagramStyle s = DiagramStyle.defaults();
        assertEquals("", s.toPlantUmlPrelude());
    }

    @Test
    public void themeOnlyEmitsThemeLine() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("cerulean");
        assertEquals("!theme cerulean\n", s.toPlantUmlPrelude());
    }

    @Test
    public void allFieldsEmitAllLines() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("plain");
        s.setBackgroundColor("#FFFFFF");
        s.setFontName("Helvetica");
        s.setFontSize(12);
        s.setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        s.setCustomSkinparam("skinparam shadowing false");
        String out = s.toPlantUmlPrelude();
        assertTrue(out, out.contains("!theme plain\n"));
        assertTrue(out, out.contains("skinparam backgroundColor #FFFFFF\n"));
        assertTrue(out, out.contains("skinparam defaultFontName Helvetica\n"));
        assertTrue(out, out.contains("skinparam defaultFontSize 12\n"));
        assertTrue(out, out.contains("left to right direction\n"));
        assertTrue(out, out.contains("skinparam shadowing false\n"));
    }

    @Test
    public void lineTypeOrthoEmitsSkinparam() {
        DiagramStyle s = new DiagramStyle();
        s.setLineType(DiagramStyle.LineType.ORTHO);
        assertEquals("skinparam linetype ortho\n", s.toPlantUmlPrelude());
    }

    @Test
    public void lineTypePolylineEmitsSkinparam() {
        DiagramStyle s = new DiagramStyle();
        s.setLineType(DiagramStyle.LineType.POLYLINE);
        assertEquals("skinparam linetype polyline\n", s.toPlantUmlPrelude());
    }

    @Test
    public void shadowingOffEmitsSkinparam() {
        DiagramStyle s = new DiagramStyle();
        s.setShadowing(DiagramStyle.Shadowing.OFF);
        assertEquals("skinparam shadowing false\n", s.toPlantUmlPrelude());
    }

    @Test
    public void captionEmitsCaptionLineAndSurvivesCopy() {
        DiagramStyle s = new DiagramStyle();
        assertEquals("", s.toPlantUmlPrelude()); // 既定は空
        s.setCaption("Acme Corp — Confidential");
        String out = s.toPlantUmlPrelude();
        assertTrue(out, out.contains("caption "));
        assertTrue(out, out.contains("Acme Corp"));
        // copy / equals にも含まれる
        assertEquals(s, s.copy());
        assertEquals("Acme Corp — Confidential", s.copy().getCaption());
    }

    @Test
    public void captionNewlinesAreFlattenedToOneLine() {
        DiagramStyle s = new DiagramStyle();
        s.setCaption("line1\nline2");
        String out = s.toPlantUmlPrelude();
        // caption 行は 1 行に畳まれる (改行で PlantUML 構文が壊れない)
        assertTrue(out, out.contains("caption line1 line2\n"));
    }

    @Test
    public void spacingEmitsNodesepAndRanksep() {
        DiagramStyle s = new DiagramStyle();
        s.setNodeSep(40);
        s.setRankSep(55);
        String out = s.toPlantUmlPrelude();
        assertTrue(out, out.contains("skinparam nodesep 40\n"));
        assertTrue(out, out.contains("skinparam ranksep 55\n"));
    }

    @Test
    public void readablePresetEmitsReadabilityLines() {
        DiagramStyle s = DiagramStyle.readable();
        String out = s.toPlantUmlPrelude();
        assertTrue(out, out.contains("!theme plain\n"));
        assertTrue(out, out.contains("skinparam linetype ortho\n"));
        assertTrue(out, out.contains("skinparam shadowing false\n"));
        assertTrue(out, out.contains("skinparam nodesep "));
        assertTrue(out, out.contains("skinparam ranksep "));
    }

    @Test
    public void readablePresetLeavesFontUnspecified() {
        DiagramStyle s = DiagramStyle.readable();
        assertEquals("", s.getFontName());
        assertEquals("", s.getBackgroundColor());
    }

    @Test
    public void copyPreservesReadabilityFields() {
        DiagramStyle s = new DiagramStyle();
        s.setLineType(DiagramStyle.LineType.ORTHO);
        s.setShadowing(DiagramStyle.Shadowing.OFF);
        s.setNodeSep(30);
        s.setRankSep(70);
        DiagramStyle c = s.copy();
        assertEquals(s, c);
        assertEquals(DiagramStyle.LineType.ORTHO, c.getLineType());
        assertEquals(DiagramStyle.Shadowing.OFF, c.getShadowing());
        assertEquals(30, c.getNodeSep());
        assertEquals(70, c.getRankSep());
    }

    @Test
    public void topToBottomDirectionEmitsExplicitLine() {
        DiagramStyle s = new DiagramStyle();
        s.setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        assertEquals("top to bottom direction\n", s.toPlantUmlPrelude());
    }

    @Test
    public void customSkinparamWithoutTrailingNewlineGetsOne() {
        DiagramStyle s = new DiagramStyle();
        s.setCustomSkinparam("skinparam shadowing false");
        assertEquals("skinparam shadowing false\n", s.toPlantUmlPrelude());
    }

    @Test
    public void customSkinparamCrlfIsNormalized() {
        DiagramStyle s = new DiagramStyle();
        s.setCustomSkinparam("skinparam shadowing false\r\nskinparam classBackgroundColor #EEE\r\n");
        String out = s.toPlantUmlPrelude();
        assertEquals(
                "skinparam shadowing false\nskinparam classBackgroundColor #EEE\n",
                out);
    }

    @Test
    public void copyReturnsIndependentInstance() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("plain");
        DiagramStyle c = s.copy();
        assertNotSame(s, c);
        assertEquals(s, c);
        c.setTheme("hacker");
        assertEquals("plain", s.getTheme());
    }

    @Test
    public void nullSettersDoNotThrow() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme(null);
        s.setBackgroundColor(null);
        s.setFontName(null);
        s.setCustomSkinparam(null);
        s.setDirection(null);
        s.setLineType(null);
        s.setShadowing(null);
        s.setNodeSep(-3);
        s.setRankSep(-9);
        s.setFontSize(-5);
        assertEquals("", s.getTheme());
        assertEquals("", s.getBackgroundColor());
        assertEquals("", s.getFontName());
        assertEquals("", s.getCustomSkinparam());
        assertEquals(DiagramStyle.Direction.DEFAULT, s.getDirection());
        assertEquals(DiagramStyle.LineType.DEFAULT, s.getLineType());
        assertEquals(DiagramStyle.Shadowing.DEFAULT, s.getShadowing());
        assertEquals(0, s.getNodeSep());
        assertEquals(0, s.getRankSep());
        assertEquals(0, s.getFontSize());
    }
}
