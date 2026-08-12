// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 閉じ忘れ指摘のクイックフィックス ({@link PumlDiagnostics#closerEdit}) の
 * 純ロジックテスト (headless)。
 */
public class PumlQuickFixTest {

    private static PumlDiagnostics.Diagnostic first(String text) {
        List<PumlDiagnostics.Diagnostic> d = PumlDiagnostics.analyze(text);
        assertTrue("前提: 指摘があるはず", !d.isEmpty());
        return d.get(0);
    }

    private static String applied(String text) {
        PumlEditorKeys.Edit e = PumlDiagnostics.closerEdit(text, first(text));
        assertTrue("前提: 修正できる指摘のはず", e != null);
        return text.substring(0, e.start) + e.replacement + text.substring(e.end);
    }

    // -------------------------------------------------------------------------
    // 挿入する終端の選び方
    // -------------------------------------------------------------------------

    @Test
    public void altGetsAnEnd_beforeTheEnduml() {
        String fixed = applied("@startuml\nalt ok\n  A -> B : hi\n@enduml\n");
        assertEquals("@startuml\nalt ok\n  A -> B : hi\nend\n@enduml\n", fixed);
        assertTrue(PumlDiagnostics.analyze(fixed).isEmpty());
    }

    @Test
    public void closerCopiesTheOpenersIndent() {
        String fixed = applied("@startuml\nalt ok\n  loop n\n    A -> B\n@enduml\n");
        // 最初の指摘 (行順) は alt。字下げは alt 行に合わせる。
        assertTrue(fixed.contains("\nend\n@enduml"));
    }

    @Test
    public void missingEnduml_isAppendedAtTheEnd() {
        assertEquals("@startuml\nA -> B\n@enduml\n",
                applied("@startuml\nA -> B\n"));
    }

    @Test
    public void missingEnduml_worksWithoutATrailingNewline() {
        assertEquals("@startuml\nA -> B\n@enduml",
                applied("@startuml\nA -> B"));
    }

    @Test
    public void blockSpecificClosersAreUsed() {
        assertTrue(applied("@startuml\nstart\nif (x?) then (y)\n  :a;\nstop\n@enduml\n")
                .contains("\nendif\n"));
        assertTrue(applied("@startuml\nnote over A\n  free text\n@enduml\n")
                .contains("\nend note\n"));
        assertTrue(applied("@startuml\n!ifdef FLAG\nA -> B\n@enduml\n")
                .contains("\n!endif\n"));
        assertTrue(applied("@startmindmap\n* Root\n").endsWith("@endmindmap\n"));
    }

    @Test
    public void repeatHasNoMechanicalFix() {
        // repeat while (条件) は条件を書かないと成立しない。中途半端な行を
        // 挿し込むと修正がかえって増えるので、直せないものとして扱う。
        String text = "@startuml\nstart\nrepeat\n  :a;\nstop\n@enduml\n";
        assertNull(PumlDiagnostics.closerEdit(text, first(text)));
    }

    @Test
    public void fixableDiagnosticsCarryTheirCloser() {
        assertEquals("end", first("@startuml\nalt ok\n@enduml\n").closer());
        assertEquals("@enduml", first("@startuml\nA -> B\n").closer());
        // 対応の無い @end… は「開始が無い」指摘であり、挿入では直せない。
        assertNull(first("A -> B\n@enduml\n").closer());
    }

    @Test
    public void nestedFixes_convergeToACleanDiagram() {
        // 1 件ずつ直していけば、指摘ゼロまで機械的に到達できる。
        String text = "@startuml\nalt a\n  loop n\n    A -> B\n@enduml\n";
        for (int guard = 0; guard < 5; guard++) {
            List<PumlDiagnostics.Diagnostic> d = PumlDiagnostics.analyze(text);
            if (d.isEmpty()) {
                break;
            }
            PumlEditorKeys.Edit e = PumlDiagnostics.closerEdit(text, d.get(0));
            assertTrue("直せない指摘で停滞: " + d.get(0), e != null);
            text = text.substring(0, e.start) + e.replacement + text.substring(e.end);
        }
        assertTrue("修正後の残指摘: " + PumlDiagnostics.analyze(text),
                PumlDiagnostics.analyze(text).isEmpty());
    }
}
