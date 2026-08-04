// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlSnippetTemplate} のテンプレート展開を検証する純ロジックテスト (headless)。
 */
public class PumlSnippetTemplateTest {

    @Test
    public void placeholders_areStrippedAndReportedAsStops() {
        PumlSnippetTemplate.Expansion ex =
                PumlSnippetTemplate.expand("class ${1:Name} extends ${2:Base}");
        assertEquals("class Name extends Base", ex.text());
        assertEquals(2, ex.stops().size());
        assertEquals("Name", slice(ex, 0));
        assertEquals("Base", slice(ex, 1));
    }

    @Test
    public void emptyPlaceholder_becomesZeroWidthStop() {
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand("loop n\n  ${1}\nend");
        assertEquals("loop n\n  \nend", ex.text());
        assertEquals(1, ex.stops().size());
        assertEquals("", slice(ex, 0));
        assertEquals(9, ex.stops().get(0)[0]);
    }

    @Test
    public void zeroStop_isVisitedLastRegardlessOfPosition() {
        // ${0} は「最後に行き着く場所」。本文の前方にあっても巡回順では末尾になる。
        PumlSnippetTemplate.Expansion ex =
                PumlSnippetTemplate.expand("a${0:X}b${1:Y}c${2:Z}");
        assertEquals("aXbYcZ", ex.text());
        assertEquals(List.of("Y", "Z", "X"),
                List.of(slice(ex, 0), slice(ex, 1), slice(ex, 2)));
    }

    @Test
    public void legacyCaretMarker_isTreatedAsFinalStop() {
        PumlSnippetTemplate.Expansion ex =
                PumlSnippetTemplate.expand("class " + PumlSnippets.CARET + "Name {\n}\n");
        assertEquals("class Name {\n}\n", ex.text());
        assertEquals(1, ex.stops().size());
        assertEquals(6, ex.stops().get(0)[0]);
        assertEquals(6, ex.stops().get(0)[1]);
    }

    @Test
    public void nonMarkerDollarBrace_isKeptVerbatim() {
        // マーカーでない ${...} は本文としてそのまま通す (PlantUML 本文を壊さない)。
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand("note ${env} ${1:x}");
        assertEquals("note ${env} x", ex.text());
        assertEquals(1, ex.stops().size());
        assertEquals("x", slice(ex, 0));
    }

    @Test
    public void unterminatedMarker_isKeptVerbatimAndDoesNotThrow() {
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand("class ${1:Name");
        assertEquals("class ${1:Name", ex.text());
        assertTrue(ex.stops().isEmpty());
    }

    @Test
    public void indent_isAppliedToContinuationLinesOnly() {
        PumlSnippetTemplate.Expansion ex =
                PumlSnippetTemplate.expand("alt ${1:c}\n  x\nend\n", "    ");
        assertEquals("alt c\n      x\n    end\n", ex.text());
    }

    @Test
    public void indent_isNotAppliedToBlankLines() {
        // 空行にまで字下げを付けると行末に無意味な空白が残る。
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand("a\n\nb\n", "  ");
        assertEquals("a\n\n  b\n", ex.text());
        assertFalse("行末に空白を残さない", ex.text().contains(" \n"));
    }

    @Test
    public void indent_shiftsStopOffsetsToMatch() {
        PumlSnippetTemplate.Expansion plain =
                PumlSnippetTemplate.expand("if\n  ${1:x}\nendif\n");
        PumlSnippetTemplate.Expansion indented =
                PumlSnippetTemplate.expand("if\n  ${1:x}\nendif\n", "  ");
        assertEquals("x", slice(plain, 0));
        assertEquals("字下げしてもタブストップは同じ文字を指す", "x", slice(indented, 0));
        assertEquals(plain.stops().get(0)[0] + 2, indented.stops().get(0)[0]);
    }

    @Test
    public void everyCatalogBodyExpandsWithoutLeavingMarkers() {
        for (PumlSnippets.Snippet s : PumlSnippets.all()) {
            PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(s.body());
            assertFalse("マーカーが残っている: " + s.trigger(), ex.text().contains("${"));
            assertFalse("展開結果が空: " + s.trigger(), ex.text().isEmpty());
            for (int[] stop : ex.stops()) {
                assertTrue("タブストップが本文の範囲外: " + s.trigger(),
                        stop[0] >= 0 && stop[1] <= ex.text().length() && stop[0] <= stop[1]);
            }
        }
    }

    private static String slice(PumlSnippetTemplate.Expansion ex, int stop) {
        int[] r = ex.stops().get(stop);
        return ex.text().substring(r[0], r[1]);
    }
}
