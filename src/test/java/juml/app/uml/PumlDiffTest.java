// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlDiff} の行差分 (純関数, headless)。
 */
public class PumlDiffTest {

    @Test
    public void hasChanges_ignoresLineEndings() {
        assertFalse(PumlDiff.hasChanges("a\nb\n", "a\r\nb\r\n"));
        assertTrue(PumlDiff.hasChanges("a\nb\n", "a\nc\n"));
    }

    @Test
    public void unified_marksAddedRemovedAndContext() {
        String diff = PumlDiff.unified(
                "@startuml\nclass A\n@enduml\n",
                "@startuml\nclass A\nclass B\n@enduml\n");
        assertTrue("追加行に + が付くはず: " + diff, diff.contains("+ class B"));
        assertTrue("共通行は空白接頭辞: " + diff, diff.contains("  class A"));
        assertFalse("削除は無いはず: " + diff, diff.contains("- "));
    }

    @Test
    public void unified_marksRemovedLine() {
        String diff = PumlDiff.unified(
                "@startuml\nclass A\nclass B\n@enduml\n",
                "@startuml\nclass A\n@enduml\n");
        assertTrue("削除行に - が付くはず: " + diff, diff.contains("- class B"));
    }

    @Test
    public void unified_identicalText_hasOnlyContext() {
        // 末尾改行は「空の最終行」として保持されるため、両者一致でも末尾に
        // 空の文脈行が 1 本付く (hasChanges と unified の判定を揃えるための仕様)。
        String diff = PumlDiff.unified("x\ny\n", "x\ny\n");
        assertEquals("  x\n  y\n  \n", diff);
    }

    /** 末尾改行なしの identical テキストは余分な文脈行を付けない。 */
    @Test
    public void unified_identicalNoTrailingNewline_hasNoExtraContext() {
        String diff = PumlDiff.unified("x\ny", "x\ny");
        assertEquals("  x\n  y\n", diff);
    }

    /**
     * 末尾改行だけが違うペアは、hasChanges (文字比較) と unified (行比較) で
     * 判定が食い違ってはいけない。以前は splitLines が末尾の空行を落とすため、
     * hasChanges=true なのに unified に +/- が出ない「変化なしの差分」になっていた。
     */
    @Test
    public void trailingNewlineDifference_isVisibleInUnified() {
        assertTrue("末尾改行の差は変化として検出されるべき",
                PumlDiff.hasChanges("a", "a\n"));
        String diff = PumlDiff.unified("a", "a\n");
        assertTrue("hasChanges=true なら unified にも +/- が出るべき: " + diff,
                diff.contains("+ ") || diff.contains("- "));
    }

    /** 末尾改行なし同士で内容が同じなら差分は文脈のみ。 */
    @Test
    public void noTrailingNewlineBoth_identical_hasOnlyContext() {
        assertFalse(PumlDiff.hasChanges("a\nb", "a\nb"));
        String diff = PumlDiff.unified("a\nb", "a\nb");
        assertFalse("差分は無いはず: " + diff, diff.contains("+ ") || diff.contains("- "));
    }
}
