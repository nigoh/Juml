// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JFrame;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 入力追従補完ポップアップ ({@link PumlCompletionPopup}) の表示・絞り込み・確定を
 * 検証する GUI テスト (headless-skip)。
 *
 * <p>エンジン側の候補生成は {@link PumlCompletionEngineTest} が固定しているので、
 * ここは「いつ出て、いつ消えて、確定すると何が入るか」という導線に集中する。</p>
 *
 * <p>ポップアップの更新と観測は必ず同一の EDT ブロックで行う。テストのペインは
 * フォーカスを持たないため、{@code setText} が積んだ入力追従の再計算が
 * {@code invokeLater} で後から走ると「フォーカスが無い」と判断してポップアップを
 * 閉じてしまい、ブロックを分けると観測が不安定になる (実アプリではペインが
 * フォーカスを持つのでこの経路には入らない)。</p>
 */
public class PumlCompletionPopupTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    /** ポップアップは画面座標を要求するため、実際に表示されたフレームに載せる。 */
    private static PumlSourcePanel shown(String text, int caret) {
        return GuiActionRunner.execute(() -> {
            PumlSourcePanel panel = new PumlSourcePanel();
            JFrame frame = new JFrame("completion");
            frame.setContentPane(panel);
            frame.setSize(600, 400);
            frame.setVisible(true);
            panel.setEditable(true);
            panel.setText(text);
            panel.setCaretForTest(caret);
            return panel;
        });
    }

    /** ポップアップを更新し、同じ EDT ブロックのうちに見えている候補を読む。 */
    private static List<String> labelsAfterUpdate(PumlSourcePanel panel, boolean explicit) {
        return GuiActionRunner.execute(() -> {
            panel.completionPopupForTest().updateForTest(explicit);
            return new ArrayList<>(panel.completionPopupForTest().visibleLabelsForTest());
        });
    }

    /** ポップアップを更新し、同じ EDT ブロックのうちにキー操作を流す。 */
    private static void updateThenPress(PumlSourcePanel panel, String... actions) {
        GuiActionRunner.execute(() -> {
            panel.completionPopupForTest().updateForTest(false);
            for (String a : actions) {
                panel.performEditorActionForTest(a);
            }
        });
    }

    private static String textOf(PumlSourcePanel panel) {
        return GuiActionRunner.execute(panel::getText);
    }

    @Test
    public void popupIsCreatedOnlyForEditablePanels() {
        PumlSourcePanel readOnly = GuiActionRunner.execute(PumlSourcePanel::new);
        assertNull(GuiActionRunner.execute(readOnly::completionPopupForTest));
        assertNotNull(GuiActionRunner.execute(shown("", 0)::completionPopupForTest));
    }

    @Test
    public void typingTwoCharacters_showsCandidates() {
        PumlSourcePanel panel =
                shown("@startuml\nclass Foo\nclass Bar\nFoo <|-- Bar\ncl", 45);
        List<String> labels = labelsAfterUpdate(panel, false);
        assertFalse("2 文字打てば候補が出る", labels.isEmpty());
        assertTrue(labels.contains("class"));
    }

    @Test
    public void oneCharacter_doesNotAutoOpenThePopup() {
        // 1 文字ごとに開くと、書いている最中ずっと視界を塞ぐ。
        assertTrue(labelsAfterUpdate(shown("@startuml\nc", 11), false).isEmpty());
    }

    @Test
    public void explicitInvoke_opensEvenWithNothingTyped() {
        PumlSourcePanel panel =
                shown("@startuml\nparticipant A\nparticipant B\nA -> B : x\n", 49);
        assertFalse("Ctrl+Space は 0 文字でも開く", labelsAfterUpdate(panel, true).isEmpty());
    }

    @Test
    public void continuedTyping_narrowsTheCandidates() {
        int wide = labelsAfterUpdate(shown("@startuml\npa", 12), false).size();
        int narrow = labelsAfterUpdate(shown("@startuml\npart", 14), false).size();
        assertTrue("打つほど候補は絞れる (" + wide + " → " + narrow + ")", narrow < wide);
    }

    @Test
    public void noMatch_closesThePopup() {
        assertTrue(labelsAfterUpdate(shown("@startuml\nzzqqxx", 16), false).isEmpty());
    }

    @Test
    public void enter_acceptsTheSelectedCandidate() {
        // "part" の 4 打から "participant Name" まで届き、名前が選択状態で待つ。
        // これが「入力を少なくする」導線そのもの。
        PumlSourcePanel panel = shown("@startuml\nparticipant A\npart", 28);
        updateThenPress(panel, "juml-comp-enter");
        assertEquals("@startuml\nparticipant A\nparticipant Name\n", textOf(panel));
        assertEquals("Name", GuiActionRunner.execute(panel::selectedTextForTest));
    }

    @Test
    public void enterWithoutPopup_stillInsertsANewline() {
        // ポップアップを Enter に噛ませたせいで改行が効かなくなっていないこと。
        PumlSourcePanel panel = shown("@startuml\n", 10);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-comp-enter"));
        assertEquals("@startuml\n\n", textOf(panel));
    }

    @Test
    public void escape_closesThePopupWithoutChangingText() {
        PumlSourcePanel panel = shown("@startuml\npart", 14);
        Boolean stillVisible = GuiActionRunner.execute(() -> {
            panel.completionPopupForTest().updateForTest(false);
            panel.performEditorActionForTest("juml-comp-esc");
            return panel.completionPopupForTest().isVisible();
        });
        assertFalse("Esc で閉じる", stillVisible);
        assertEquals("Esc は本文を変えない", "@startuml\npart", textOf(panel));
    }

    @Test
    public void downArrow_movesTheSelectionWithinThePopup() {
        PumlSourcePanel panel = shown("@startuml\nal", 12);
        List<String> labels = labelsAfterUpdate(panel, false);
        Assume.assumeTrue("候補が 2 件以上必要", labels.size() >= 2);
        updateThenPress(panel, "juml-comp-down", "juml-comp-enter");
        assertTrue("2 番目の候補が確定される",
                textOf(panel).startsWith("@startuml\n" + labels.get(1)));
    }

    @Test
    public void arrowTyping_opensThePopupWithArrowGlyphs() {
        PumlSourcePanel panel = shown("@startuml\nclass A\nclass B\nA <|-- B\nA --", 39);
        List<String> labels = labelsAfterUpdate(panel, false);
        assertFalse("矢印を打ちかけたら候補が出る", labels.isEmpty());
        assertTrue(labels.contains("-->"));
    }

    @Test
    public void caretMovingToAnotherWord_closesThePopup() {
        // 古い位置の候補を出しっぱなしにすると、確定が無関係な語を潰す。
        PumlSourcePanel panel = shown("@startuml\npart other", 14);
        Boolean stillVisible = GuiActionRunner.execute(() -> {
            panel.completionPopupForTest().updateForTest(false);
            panel.setCaretForTest(20);
            return panel.completionPopupForTest().isVisible();
        });
        assertFalse("別の語へ移ったら閉じる", stillVisible);
    }

    @Test
    public void snippetCandidate_acceptedFromThePopup_expandsTheBlock() {
        PumlSourcePanel panel =
                shown("@startuml\nparticipant A\nparticipant B\nA -> B : x\nal", 51);
        assertEquals("先頭はブロック展開のスニペット", "alt",
                labelsAfterUpdate(panel, false).get(0));
        updateThenPress(panel, "juml-comp-enter");
        assertTrue("ブロックが丸ごと入る: " + textOf(panel), textOf(panel).endsWith(
                "alt condition\n  \nelse otherwise\n  \nend\n"));
        assertEquals("最初の穴が選択される", "condition",
                GuiActionRunner.execute(panel::selectedTextForTest));
    }

    @Test
    public void tabAcceptsTheCandidateWhenThePopupIsOpen() {
        PumlSourcePanel panel = shown("@startuml\nparticipant A\npart", 28);
        updateThenPress(panel, "juml-comp-tab");
        assertEquals("@startuml\nparticipant A\nparticipant Name\n", textOf(panel));
    }
}
