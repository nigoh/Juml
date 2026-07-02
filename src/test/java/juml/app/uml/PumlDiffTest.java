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
        String diff = PumlDiff.unified("x\ny\n", "x\ny\n");
        assertEquals("  x\n  y\n", diff);
    }
}
