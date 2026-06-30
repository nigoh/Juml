// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Test;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link JapaneseFontSupport} の既定フォント解決テスト。
 *
 * <p>同梱フォント（BIZ UDPGothic）が起動時に登録され、見やすい日本語フォントが
 * 既定として返ること、および返ったフォントで日本語が表示できることを検証する。</p>
 */
public class JapaneseFontSupportTest {

    /** jar / クラスパスに同梱したフォントが登録され、そのファミリ名が既定として返る。 */
    @Test
    public void bundledFontIsRegisteredAndUsedByDefault() {
        // ヘッドレス環境（CI 等）でフォント登録できないケースもあるため、その場合のみ許容。
        if (GraphicsEnvironment.isHeadless()) {
            // フォント環境が無い場合は空文字でも許容（文字化け防止のためのベストエフォート）。
            assertNotNull(JapaneseFontSupport.defaultFontFamily());
            return;
        }
        String family = JapaneseFontSupport.defaultFontFamily();
        assertNotNull(family);
        assertFalse("日本語フォントが解決できなかった", family.isEmpty());
        // 同梱フォントが優先されるはず（登録に失敗した場合のみ環境フォントへフォールバック）。
        assertEquals("BIZ UDPGothic", family);
    }

    /** 解決された既定フォントは、ひらがな・漢字・カタカナを表示できる。 */
    @Test
    public void resolvedFontCanDisplayJapanese() {
        String family = JapaneseFontSupport.defaultFontFamily();
        if (family.isEmpty()) {
            return; // フォント環境が無い場合はスキップ。
        }
        Font f = new Font(family, Font.PLAIN, 12);
        assertTrue("ひらがなを表示できない: " + family, f.canDisplay('あ'));
        assertTrue("漢字を表示できない: " + family, f.canDisplay('漢'));
        assertTrue("カタカナを表示できない: " + family, f.canDisplay('ア'));
    }

    /** 結果はキャッシュされ、複数回呼んでも同じ値を返す。 */
    @Test
    public void resultIsCached() {
        assertEquals(JapaneseFontSupport.defaultFontFamily(),
                JapaneseFontSupport.defaultFontFamily());
    }
}
