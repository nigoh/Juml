// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;

/**
 * {@link NoteRenderer#parseColor(String)} の正常系/異常系フォールバックを検証する
 * (package-private static ヘルパ。純関数で headless でも安全)。
 */
public class NoteRendererParseColorTest {

    @Test
    public void parseColor_validHex_returnsExactColor() {
        assertEquals(new Color(0x12, 0x34, 0x56), NoteRenderer.parseColor("#123456"));
    }

    @Test
    public void parseColor_invalidString_fallsBackToDefaultNoteColor() {
        assertEquals(Color.decode(DiagramNote.DEFAULT_COLOR),
                NoteRenderer.parseColor("not-a-color"));
    }

    @Test
    public void parseColor_emptyString_fallsBackToDefaultNoteColor() {
        assertEquals(Color.decode(DiagramNote.DEFAULT_COLOR), NoteRenderer.parseColor(""));
    }
}
