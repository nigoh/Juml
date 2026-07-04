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

    /**
     * normalizeColorHex は画面描画 ({@link Color#decode}) と同じ解釈で {@code #RRGGBB} を返す。
     * {@code #FFF} は CSS では白だが {@code Color.decode} では (0,15,255) なので、SVG
     * エクスポートの fill もこの解釈へ揃えて画面と一致させる (#42)。
     */
    @Test
    public void normalizeColorHex_matchesScreenDecodeNotCss() {
        assertEquals("#000FFF", NoteRenderer.normalizeColorHex("#FFF"));
        assertEquals("#123456", NoteRenderer.normalizeColorHex("#123456"));
    }

    /** 不正な色文字列は既定色の {@code #RRGGBB} へフォールバックする。 */
    @Test
    public void normalizeColorHex_invalidFallsBackToDefault() {
        String expected = String.format("#%06X",
                Color.decode(DiagramNote.DEFAULT_COLOR).getRGB() & 0xFFFFFF);
        assertEquals(expected, NoteRenderer.normalizeColorHex("bogus"));
    }
}
