// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 記号一覧ジャンプ ({@link PumlOutlineBar}) と未宣言参加者の宣言追加を検証する
 * GUI テスト (headless-skip)。
 */
public class PumlOutlineAndFixTest {

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
    // 記号一覧
    // -------------------------------------------------------------------------

    @Test
    public void outlineListsEveryDeclaration() {
        PumlSourcePanel panel = editable("@startuml\nparticipant Alice\n"
                + "participant Bob\nclass Foo\n@enduml\n");
        PumlOutlineBar bar = GuiActionRunner.execute(panel::outlineBarForTest);
        GuiActionRunner.execute(() -> bar.activate(textOf(panel)));
        assertEquals(3, (int) GuiActionRunner.execute(bar::visibleCountForTest));
        assertTrue(GuiActionRunner.execute(bar::isVisible));
    }

    @Test
    public void outlineNarrowsAsYouType() {
        PumlSourcePanel panel = editable("@startuml\nparticipant Alice\n"
                + "participant Bob\nclass Foo\n@enduml\n");
        PumlOutlineBar bar = GuiActionRunner.execute(panel::outlineBarForTest);
        GuiActionRunner.execute(() -> {
            bar.activate(textOf(panel));
            bar.setFilterForTest("bo");
        });
        assertEquals(1, (int) GuiActionRunner.execute(bar::visibleCountForTest));
        assertEquals("Bob の宣言行", 3,
                (int) GuiActionRunner.execute(bar::firstLineForTest));
    }

    @Test
    public void outlineCanFilterByKind() {
        PumlSourcePanel panel = editable("@startuml\nparticipant Alice\nclass Foo\n@enduml\n");
        PumlOutlineBar bar = GuiActionRunner.execute(panel::outlineBarForTest);
        GuiActionRunner.execute(() -> {
            bar.activate(textOf(panel));
            bar.setFilterForTest("class");
        });
        assertEquals(1, (int) GuiActionRunner.execute(bar::visibleCountForTest));
    }

    @Test
    public void outlineStaysClosedWhenThereIsNothingToJumpTo() {
        // 記号が無い図でバーだけ開いても場所を取るだけ。
        PumlSourcePanel panel = editable("@startuml\nA -> B : hi\n@enduml\n");
        PumlOutlineBar bar = GuiActionRunner.execute(panel::outlineBarForTest);
        GuiActionRunner.execute(() -> bar.activate(textOf(panel)));
        assertTrue(!GuiActionRunner.execute(bar::isVisible));
    }

    @Test
    public void outlineOpensViaItsKeyboardShortcut() {
        PumlSourcePanel panel = editable("@startuml\nparticipant Alice\n@enduml\n");
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-outline"));
        assertTrue(GuiActionRunner.execute(
                () -> panel.outlineBarForTest().isVisible()));
    }

    // -------------------------------------------------------------------------
    // 未宣言参加者の宣言追加
    // -------------------------------------------------------------------------

    @Test
    public void missingParticipantsAreDeclaredAfterTheExistingOnes() {
        PumlSourcePanel panel = editable(
                "@startuml\nparticipant Alice\nAlice -> Bob : hi\nBob -> Carol : x\n@enduml\n");
        assertEquals(2, (int) GuiActionRunner.execute(panel::declareMissingParticipants));
        assertEquals("@startuml\nparticipant Alice\nparticipant Bob\nparticipant Carol\n"
                + "Alice -> Bob : hi\nBob -> Carol : x\n@enduml\n", textOf(panel));
    }

    @Test
    public void missingParticipantsGoAfterTheHeaderWhenNoneAreDeclared() {
        PumlSourcePanel panel = editable(
                "@startuml\ntitle Flow\nAlice -> Bob : hi\n@enduml\n");
        assertEquals(2, (int) GuiActionRunner.execute(panel::declareMissingParticipants));
        assertEquals("@startuml\ntitle Flow\nparticipant Alice\nparticipant Bob\n"
                + "Alice -> Bob : hi\n@enduml\n", textOf(panel));
    }

    @Test
    public void quotedNamesAreDeclaredWithTheirQuotes() {
        PumlSourcePanel panel = editable(
                "@startuml\n\"Web Server\" -> DB : query\n@enduml\n");
        GuiActionRunner.execute(panel::declareMissingParticipants);
        assertTrue(textOf(panel).contains("participant \"Web Server\""));
        assertTrue(textOf(panel).contains("participant DB"));
    }

    @Test
    public void nothingToDeclare_leavesTheTextAlone() {
        String src = "@startuml\nparticipant Alice\nparticipant Bob\nAlice -> Bob : hi\n"
                + "@enduml\n";
        PumlSourcePanel panel = editable(src);
        assertEquals(0, (int) GuiActionRunner.execute(panel::declareMissingParticipants));
        assertEquals(src, textOf(panel));
    }

    @Test
    public void declarationUndoesInOneStep() {
        PumlSourcePanel panel = editable("@startuml\nAlice -> Bob : hi\n@enduml\n");
        GuiActionRunner.execute(panel::declareMissingParticipants);
        assertTrue(textOf(panel).contains("participant Alice"));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("@startuml\nAlice -> Bob : hi\n@enduml\n", textOf(panel));
    }

    @Test
    public void readOnlyPanel_declaresNothing() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> panel.setText("@startuml\nA -> B : hi\n@enduml\n"));
        assertEquals(0, (int) GuiActionRunner.execute(panel::declareMissingParticipants));
    }
}
