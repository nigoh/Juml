// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * リネーム・クイックフィックス・整形のエディタ配線 ({@link PumlEditorCommands}) を
 * 検証する GUI テスト (headless-skip)。計算そのものは PumlRenameTest /
 * PumlQuickFixTest / PumlFormatterTest が持つので、ここでは「キーから呼べて、
 * ドキュメントとキャレットと Undo が正しく振る舞う」ことだけを見る。
 */
public class PumlEditorCommandsTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static PumlSourcePanel editable(String text) {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText(text);
        });
        return panel;
    }

    private static String textOf(PumlSourcePanel panel) {
        return GuiActionRunner.execute(panel::getText);
    }

    // -------------------------------------------------------------------------
    // リネーム
    // -------------------------------------------------------------------------

    @Test
    public void renameReplacesEveryReference() {
        String text = "@startuml\nparticipant Alice\nAlice -> Bob : Alice calls\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        int count = GuiActionRunner.execute(() -> {
            panel.setCaretForTest(text.indexOf("Alice") + 2);
            return panel.commandsForTest().renameTo("Carol");
        });
        assertEquals(2, count);
        assertEquals("@startuml\nparticipant Carol\nCarol -> Bob : Alice calls\n@enduml\n",
                textOf(panel));
    }

    @Test
    public void renameIsOneUndoStep() {
        String text = "@startuml\nparticipant Alice\nAlice -> Bob\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        GuiActionRunner.execute(() -> {
            panel.setCaretForTest(text.indexOf("Alice"));
            panel.commandsForTest().renameTo("Carol");
            panel.undoForTest();
        });
        assertEquals(text, textOf(panel));
    }

    @Test
    public void renameRejectsKeywordsAndDoesNothing() {
        String text = "@startuml\nparticipant Alice\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        int count = GuiActionRunner.execute(() -> {
            panel.setCaretForTest(text.indexOf("Alice"));
            return panel.commandsForTest().renameTo("end");
        });
        assertEquals(0, count);
        assertEquals(text, textOf(panel));
    }

    // -------------------------------------------------------------------------
    // クイックフィックス
    // -------------------------------------------------------------------------

    @Test
    public void quickFixOnTheFlaggedLineInsertsTheCloser() {
        String text = "@startuml\nalt ok\n  A -> B : hi\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        boolean fixed = GuiActionRunner.execute(() -> {
            panel.setCaretForTest(text.indexOf("alt ok") + 1);
            return panel.commandsForTest().quickFixAtCaret();
        });
        assertTrue(fixed);
        assertEquals("@startuml\nalt ok\n  A -> B : hi\nend\n@enduml\n", textOf(panel));
        assertEquals(0, (int) GuiActionRunner.execute(panel::diagnosticCountForTest));
    }

    @Test
    public void quickFixAwayFromTheFlaggedLineDoesNothing() {
        String text = "@startuml\nalt ok\n  A -> B : hi\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        boolean fixed = GuiActionRunner.execute(() -> {
            panel.setCaretForTest(text.indexOf("A -> B"));
            return panel.commandsForTest().quickFixAtCaret();
        });
        assertFalse(fixed);
        assertEquals(text, textOf(panel));
    }

    @Test
    public void quickFixIsReachableFromItsKeyBinding() {
        String text = "@startuml\nalt ok\n  A -> B : hi\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        GuiActionRunner.execute(() -> {
            panel.setCaretForTest(text.indexOf("alt ok"));
            panel.performEditorActionForTest("juml-quickfix");
        });
        assertTrue(textOf(panel).contains("\nend\n@enduml"));
    }

    @Test
    public void diagnosticTooltipMentionsTheQuickFix() {
        String text = "@startuml\nalt ok\n  A -> B : hi\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        GuiActionRunner.execute(panel::diagnosticCountForTest);
        String tip = GuiActionRunner.execute(
                () -> panel.diagnosticAtForTest(text.indexOf("alt ok")));
        assertTrue("直し方への導線が無い: " + tip, tip != null && tip.contains("Alt+Enter"));
    }

    // -------------------------------------------------------------------------
    // 整形
    // -------------------------------------------------------------------------

    @Test
    public void formatReindentsAndKeepsTheCaretOnItsLine() {
        String text = "@startuml\nalt ok\nA -> B : hi\nend\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        boolean changed = GuiActionRunner.execute(() -> {
            panel.setCaretForTest(text.indexOf("A -> B"));
            return panel.commandsForTest().formatDocument();
        });
        assertTrue(changed);
        assertEquals("@startuml\nalt ok\n  A -> B : hi\nend\n@enduml\n", textOf(panel));
        int caret = GuiActionRunner.execute(panel::caretForTest);
        String after = textOf(panel);
        int lineStart = after.indexOf("  A -> B");
        assertTrue("キャレットが整形した行から離れた",
                caret >= lineStart && caret <= after.indexOf('\n', lineStart));
    }

    @Test
    public void formatIsOneUndoStep() {
        String text = "@startuml\nalt ok\nA -> B\nend\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        GuiActionRunner.execute(() -> {
            panel.commandsForTest().formatDocument();
            panel.undoForTest();
        });
        assertEquals(text, textOf(panel));
    }

    @Test
    public void formatIsReachableFromItsKeyBinding() {
        String text = "@startuml\nalt ok\nA -> B\nend\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-format"));
        assertEquals("@startuml\nalt ok\n  A -> B\nend\n@enduml\n", textOf(panel));
    }

    @Test
    public void alreadyFormattedTextIsLeftAlone() {
        String text = "@startuml\nalt ok\n  A -> B\nend\n@enduml\n";
        PumlSourcePanel panel = editable(text);
        boolean changed = GuiActionRunner.execute(
                () -> panel.commandsForTest().formatDocument());
        assertFalse(changed);
        assertEquals(text, textOf(panel));
    }

    // -------------------------------------------------------------------------
    // モードのガード
    // -------------------------------------------------------------------------

    @Test
    public void commandsAreAbsentInReadOnlyMode() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> panel.setText("@startuml\nA -> B\n@enduml\n"));
        assertNull(GuiActionRunner.execute(panel::commandsForTest));
    }
}
