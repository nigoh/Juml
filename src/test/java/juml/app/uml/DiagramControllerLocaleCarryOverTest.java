// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * レイアウト図で選んだ文言 locale が、spec の再構築で捨てられないことの回帰テスト。
 *
 * <p>ロケール切替はタブ固有の設定で、共有 {@link DiagramState} は持たない。
 * {@code buildSpecForKind} は共有状態だけからリクエストを組み直すため、引き継ぎを
 * 入れないとスコープ変更などのたびに<b>選んだ言語が既定へ勝手に戻る</b>。</p>
 */
public class DiagramControllerLocaleCarryOverTest {

    private static DiagramRequest layout(String locale) {
        DiagramRequest base = DiagramRequest.forLayout("res/layout/main.xml", true);
        return locale == null ? base : base.withStringLocale(locale);
    }

    @Test
    public void localeOfActiveTabIsCarriedIntoRebuiltSpec() {
        DiagramRequest rebuilt = DiagramController.keepLocaleFrom(layout("ja"), layout(null));
        assertEquals("ja", rebuilt.getStringLocale());
    }

    @Test
    public void noLocaleOnActiveTabLeavesSpecUntouched() {
        DiagramRequest spec = layout(null);
        assertNull(DiagramController.keepLocaleFrom(layout(null), spec).getStringLocale());
        assertNull(DiagramController.keepLocaleFrom(null, spec).getStringLocale());
        assertNull(DiagramController.keepLocaleFrom(layout(""), spec).getStringLocale());
    }

    @Test
    public void otherRequestFieldsComeFromTheRebuiltSpec() {
        // 引き継ぐのは locale だけ。題材や図種は再構築側 (共有状態) が決める。
        DiagramRequest current = DiagramRequest.forLayout("res/layout/old.xml", true)
                .withStringLocale("fr");
        DiagramRequest rebuilt = DiagramController.keepLocaleFrom(
                current, DiagramRequest.forLayoutScreen("res/layout/new.xml", true));
        assertEquals("fr", rebuilt.getStringLocale());
        assertEquals(DiagramKind.LAYOUT_SCREEN, rebuilt.getKind());
        assertEquals("res/layout/new.xml", rebuilt.getLayoutKey());
    }
}
