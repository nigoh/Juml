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
 * スニペット展開とタブストップ巡回の統合テスト (GUI, headless-skip)。
 *
 * <p>「打鍵を減らす」機能の本体はここで、{@code alt} と打って確定すれば
 * ブロックが丸ごと入り、{@code Tab} で穴だけを順に埋められることを固定する。</p>
 */
public class PumlSourcePanelTabStopTest {

    /** 実際の Tab キーが辿る配線 (補完ポップアップ → タブストップ → インデント)。 */
    private static final String TAB = "juml-comp-tab";
    /** 実際の Esc キーが辿る配線。 */
    private static final String ESC = "juml-comp-esc";

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static PumlSourcePanel editable(String text, int caret) {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText(text);
            panel.setCaretForTest(caret);
        });
        return panel;
    }

    private static PumlCompletionItem snippet(String trigger) {
        for (PumlSnippets.Snippet s : PumlSnippets.all()) {
            if (s.trigger().equals(trigger)) {
                return PumlCompletionItem.snippet(s.trigger(), s.body(), "");
            }
        }
        throw new AssertionError("トリガが見つからない: " + trigger);
    }

    private static void press(PumlSourcePanel panel, String action) {
        GuiActionRunner.execute(() -> panel.performEditorActionForTest(action));
    }

    private static String textOf(PumlSourcePanel panel) {
        return GuiActionRunner.execute(panel::getText);
    }

    private static String selectionOf(PumlSourcePanel panel) {
        return GuiActionRunner.execute(panel::selectedTextForTest);
    }

    @Test
    public void snippetAccept_replacesTypedWordWithTheWholeBlock() {
        PumlSourcePanel panel = editable("al", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("alt")));
        assertEquals("alt condition\n  \nelse otherwise\n  \nend\n", textOf(panel));
    }

    @Test
    public void snippetAccept_selectsTheFirstHoleSoTypingReplacesIt() {
        PumlSourcePanel panel = editable("al", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("alt")));
        assertEquals("condition", selectionOf(panel));
    }

    @Test
    public void tab_walksThroughTheHolesInOrder() {
        PumlSourcePanel panel = editable("en", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("enum")));
        assertEquals("Name", selectionOf(panel));
        press(panel, TAB);
        assertEquals("VALUE_A", selectionOf(panel));
        press(panel, TAB);
        assertEquals("VALUE_B", selectionOf(panel));
    }

    @Test
    public void tab_afterTheLastHole_endsTheWalkAndRestoresIndentBehaviour() {
        PumlSourcePanel panel = editable("st", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("state")));
        assertEquals("Name", selectionOf(panel));
        press(panel, TAB);
        assertEquals("最後の穴を過ぎたら巡回は終わる", 0,
                (int) GuiActionRunner.execute(panel::tabStopsRemainingForTest));
        // 巡回が終われば Tab は本来のインデント挿入へ戻る。
        String before = textOf(panel);
        press(panel, TAB);
        assertTrue("Tab がインデントとして働く", textOf(panel).length() > before.length());
    }

    @Test
    public void escape_cancelsTheWalk() {
        PumlSourcePanel panel = editable("al", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("alt")));
        assertTrue(GuiActionRunner.execute(panel::tabStopsRemainingForTest) > 0);
        press(panel, ESC);
        assertEquals(0, (int) GuiActionRunner.execute(panel::tabStopsRemainingForTest));
    }

    @Test
    public void caretLeavingTheSnippet_cancelsTheWalk() {
        // 雛形の外を触りだしたら Tab を奪い続けない (通常のインデントが効かなくなる)。
        PumlSourcePanel panel = editable("al\nunrelated line\n", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("alt")));
        assertTrue(GuiActionRunner.execute(panel::tabStopsRemainingForTest) > 0);
        GuiActionRunner.execute(() -> panel.setCaretForTest(textOf(panel).length()));
        assertEquals(0, (int) GuiActionRunner.execute(panel::tabStopsRemainingForTest));
    }

    @Test
    public void replacingTheWholeText_endsTheWalk() {
        // 全文差し替え後は指し先が意味を失う。残すと Tab が本来のインデントへ戻らない。
        PumlSourcePanel panel = editable("al", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("alt")));
        assertTrue(GuiActionRunner.execute(panel::tabStopsRemainingForTest) > 0);
        GuiActionRunner.execute(() -> panel.setText("@startuml\n@enduml\n"));
        assertEquals(0, (int) GuiActionRunner.execute(panel::tabStopsRemainingForTest));
    }

    @Test
    public void multiLineSnippet_isIndentedToMatchTheCurrentLine() {
        PumlSourcePanel panel = editable("alt outer\n  lo\n", 14);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("loop")));
        assertEquals("alt outer\n  loop times\n    \n  end\n\n", textOf(panel));
    }

    @Test
    public void snippetExpansion_undoesInOneStep() {
        PumlSourcePanel panel = editable("al", 2);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(snippet("alt")));
        assertTrue(textOf(panel).contains("else"));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("al", textOf(panel));
    }

    @Test
    public void palettePath_alsoArmsTheHoles() {
        // 挿入パレット経由でも同じ経路を通るので Tab の穴埋めが効く。
        PumlSourcePanel panel = editable("", 0);
        GuiActionRunner.execute(() -> panel.insertSnippet("class ${1:Name} {\n  ${2}\n}\n"));
        assertEquals("class Name {\n  \n}\n", textOf(panel));
        assertEquals("Name", selectionOf(panel));
        press(panel, TAB);
        assertEquals("", selectionOf(panel));
        // 2 番目の穴は "class Name {\n  " の直後 (本文 15 文字目)。
        assertEquals(15, (int) GuiActionRunner.execute(panel::caretForTest));
    }

    @Test
    public void arrowCompletion_replacesTheTypedDashes() {
        PumlSourcePanel panel = editable("@startuml\nclass A\nclass B\nA --", 32);
        GuiActionRunner.execute(() -> panel.applyCompletionItemForTest(
                PumlCompletionItem.word(PumlCompletionItem.Kind.ARROW, "--|>", "")));
        assertEquals("@startuml\nclass A\nclass B\nA --|>", textOf(panel));
    }

    @Test
    public void readOnlyPanel_ignoresSnippetInsertion() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> panel.setText("x"));
        GuiActionRunner.execute(() -> panel.insertSnippet("class ${1:Name}\n"));
        assertEquals("x", textOf(panel));
    }
}
