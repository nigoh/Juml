// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** {@link NoteExport#injectIntoSvg} の SVG 注入検証。 */
public class NoteExportTest {

    private static final String BASE =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"80\">"
            + "<rect width=\"100\" height=\"80\"/></svg>";

    @Test
    public void injectsNoteRectAndForeignObjectBeforeClosingSvg() {
        DiagramNote n = new DiagramNote(10, 20, 200, 120, "**bold** note");
        n.setColor("#D6F5C8");
        String out = NoteExport.injectIntoSvg(BASE, Collections.singletonList(n));

        assertTrue("注入グループがあること", out.contains("<g class=\"juml-notes\">"));
        assertTrue("付箋矩形があること", out.contains("fill=\"#D6F5C8\""));
        assertTrue("foreignObject があること", out.contains("<foreignObject"));
        assertTrue("Markdown が HTML 化されていること", out.contains("<b>bold</b>"));
        assertTrue("xhtml 名前空間があること",
                out.contains("xmlns=\"http://www.w3.org/1999/xhtml\""));
        // 必ず </svg> の前に入る
        assertTrue(out.indexOf("juml-notes") < out.lastIndexOf("</svg>"));
        assertTrue(out.endsWith("</svg>"));
    }

    @Test
    public void selfClosesVoidElements() {
        // 改行を含む本文 -> <br> が生成されるが、foreignObject では <br/> でなければならない
        DiagramNote n = new DiagramNote(0, 0, 100, 80, "line1\nline2");
        String out = NoteExport.injectIntoSvg(BASE, Collections.singletonList(n));
        assertTrue(out.contains("<br/>"));
        assertTrue("生の <br> (非自己終端) が残らないこと", !out.contains("<br>"));
    }

    @Test
    public void noNotesReturnsUnchanged() {
        assertEquals(BASE, NoteExport.injectIntoSvg(BASE, Collections.emptyList()));
        assertEquals(BASE, NoteExport.injectIntoSvg(BASE, null));
    }

    @Test
    public void multipleNotesAllInjected() {
        String out = NoteExport.injectIntoSvg(BASE, Arrays.asList(
                new DiagramNote(0, 0, 50, 40, "a"),
                new DiagramNote(60, 0, 50, 40, "b")));
        int first = out.indexOf("<foreignObject");
        int second = out.indexOf("<foreignObject", first + 1);
        assertTrue("2 件とも注入されること", first >= 0 && second > first);
    }
}
