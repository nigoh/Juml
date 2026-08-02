// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link MindmapSketchDialogs} の入力サニタイズ・妥当性判定 (純関数) を検証する
 * (Swing 生成不要、headless 可)。ダイアログ UI 自体は SketchPane 経由の GUI テストで
 * 間接的に確認する。
 */
public class MindmapSketchDialogsValidationTest {

    @Test
    public void sanitize_stripsNewlinesAndTrims() {
        assertEquals("Line one Line two", MindmapSketchDialogs.sanitize("  Line one\nLine two  "));
        assertEquals("A B", MindmapSketchDialogs.sanitize("A\r\nB"));
    }

    @Test
    public void sanitize_nullBecomesEmpty() {
        assertEquals("", MindmapSketchDialogs.sanitize(null));
    }

    @Test
    public void isValidText_rejectsBlank_acceptsNonEmpty() {
        assertFalse("空文字は不可", MindmapSketchDialogs.isValidText(MindmapSketchDialogs.sanitize("   ")));
        assertFalse("改行のみも不可", MindmapSketchDialogs.isValidText(MindmapSketchDialogs.sanitize("\n\n")));
        assertTrue("非空は可", MindmapSketchDialogs.isValidText(MindmapSketchDialogs.sanitize("Idea")));
    }
}
