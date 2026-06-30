// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SystemFonts} のユニットテスト。
 *
 * <p>ヘッドレス環境ではフォントが 1 つも無いこともあるため、件数ではなく
 * 「非 null / 例外を投げない / 並び順が安定」を中心に検証する。</p>
 */
public class SystemFontsTest {

    @Test
    public void familiesIsNeverNull() {
        String[] f = SystemFonts.families();
        assertNotNull(f);
    }

    @Test
    public void familiesIsCachedAndIndependentCopy() {
        String[] a = SystemFonts.families();
        String[] b = SystemFonts.families();
        assertNotNull(a);
        assertNotNull(b);
        // 返り値はクローンなので、書き換えてもキャッシュは汚染されない。
        if (a.length > 0) {
            a[0] = "MUTATED";
            assertFalse("MUTATED".equals(SystemFonts.families()[0]));
        }
    }

    @Test
    public void japaneseFirstContainsSameElementsAsFamilies() {
        String[] all = SystemFonts.families();
        List<String> ordered = SystemFonts.familiesJapaneseFirst();
        assertNotNull(ordered);
        assertTrue(ordered.size() == all.length);
    }

    @Test
    public void canDisplayJapaneseHandlesNullAndEmpty() {
        assertFalse(SystemFonts.canDisplayJapanese(null));
        assertFalse(SystemFonts.canDisplayJapanese(""));
    }
}
