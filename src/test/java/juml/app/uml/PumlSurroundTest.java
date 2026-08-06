// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PumlSnippets.Group;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 選択範囲の囲み ({@link PumlSurrounds} + {@code ${SELECTION}} 展開) を検証する
 * 純ロジックテスト (headless)。
 */
public class PumlSurroundTest {

    @Test
    public void selectionMarker_isReplacedByTheSelectedText() {
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(
                "alt ${1:cond}\n  ${SELECTION}\nend\n", "", "A -> B : hi");
        assertEquals("alt cond\n  A -> B : hi\nend\n", ex.text());
    }

    @Test
    public void multiLineSelection_isIndentedToTheMarkerColumn() {
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(
                "alt ${1:cond}\n  ${SELECTION}\nend\n", "",
                "A -> B : hi\nB --> A : ok");
        assertEquals("alt cond\n  A -> B : hi\n  B --> A : ok\nend\n", ex.text());
    }

    @Test
    public void selectionKeepsItsOwnNesting() {
        // 選択内部の相対的な入れ子は保つ。全行に共通する字下げだけを外して付け直す。
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(
                "group ${1:g}\n  ${SELECTION}\nend\n", "",
                "  A -> B : hi\n    note right : deep\n  B --> A : ok");
        assertEquals("group g\n  A -> B : hi\n    note right : deep\n  B --> A : ok\nend\n",
                ex.text());
    }

    @Test
    public void blankLinesInsideSelection_getNoTrailingWhitespace() {
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(
                "loop ${1:n}\n  ${SELECTION}\nend\n", "", "A -> B\n\nB --> A");
        assertFalse("行末に空白を残さない", ex.text().contains(" \n"));
    }

    @Test
    public void emptySelection_leavesATabStopInsideTheBlock() {
        // 何も選ばずに囲んだときは、ブロックの内側にキャレットが落ちてほしい。
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(
                "loop ${1:n}\n  ${SELECTION}\nend\n", "", "");
        assertEquals("loop n\n  \nend\n", ex.text());
        assertEquals("最初の穴は n、最後が本体", 2, ex.stops().size());
        int[] last = ex.stops().get(1);
        assertEquals(last[0], last[1]);
        assertEquals("ブロックの内側を指す", "loop n\n  ".length(), last[0]);
    }

    @Test
    public void selectionIsNotRescannedForPlaceholders() {
        // 選択の中に ${1:…} 風の文字列があってもタブストップにしない
        // (本文をそのまま残すのが正しい)。
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(
                "note as ${1:N}\n  ${SELECTION}\nend note\n", "", "cost is ${1:x}");
        assertTrue(ex.text().contains("cost is ${1:x}"));
        assertEquals(1, ex.stops().size());
    }

    @Test
    public void indentAndSelection_compose() {
        // 既に字下げされた行の中で囲むと、ブロックごとその字下げに乗る。
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(
                "alt ${1:cond}\n  ${SELECTION}\nend\n", "    ", "A -> B");
        assertEquals("alt cond\n      A -> B\n    end\n", ex.text());
    }

    @Test
    public void everySurroundBodyCarriesTheSelectionMarker() {
        // マーカーの無い囲みは選択を捨ててしまう。カタログ全体で担保する。
        for (PumlSurrounds.Surround s : PumlSurrounds.all()) {
            assertTrue("${SELECTION} が無い: " + s.displayName(),
                    s.body().contains("${SELECTION}"));
            assertFalse("ラベルが未解決キー: " + s.displayName(),
                    s.displayName().startsWith("puml.surround"));
        }
    }

    @Test
    public void everySurroundLabelResolvesInEnglishToo() {
        try {
            juml.util.Messages.setLanguage("en");
            for (PumlSurrounds.Surround s : PumlSurrounds.all()) {
                assertFalse("ラベル(en)が未解決: " + s.displayName(),
                        s.displayName().startsWith("puml.surround"));
            }
        } finally {
            juml.util.Messages.setLanguage("ja");
        }
    }

    @Test
    public void surroundsAreOrderedByTheDiagramKindInUse() {
        List<PumlSurrounds.Surround> seq = PumlSurrounds.forFlavor(Group.SEQUENCE);
        assertEquals("シーケンス図では複合フラグメントが先頭",
                Group.SEQUENCE, seq.get(0).group());
        List<PumlSurrounds.Surround> act = PumlSurrounds.forFlavor(Group.ACTIVITY);
        assertEquals(Group.ACTIVITY, act.get(0).group());
        assertEquals("並べ替えても件数は変わらない",
                PumlSurrounds.all().size(), seq.size());
    }

    @Test
    public void unknownFlavor_keepsDeclarationOrder() {
        assertEquals(PumlSurrounds.all(), PumlSurrounds.forFlavor(Group.COMMON));
        assertEquals(PumlSurrounds.all(), PumlSurrounds.forFlavor(null));
    }
}
