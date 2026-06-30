// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JSplitPane;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link AppShortcuts#toggleSidebar(JSplitPane)} のテスト。
 *
 * <p>サイドバートグル (Ctrl+B) の折りたたみ / 復元ロジックを検証する。Swing コンポーネントの
 * 生成・操作は EDT 上で行う ({@link GuiActionRunner})。ウィンドウは可視化せず Robot も使わない
 * ため headless でも実行できる。</p>
 */
public class AppShortcutsTest {

    private static JSplitPane newSplit(int divider) {
        return GuiActionRunner.execute(() -> {
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JLabel("left"), new JLabel("right"));
            split.setDividerLocation(divider);
            return split;
        });
    }

    private static int dividerOf(JSplitPane split) {
        return GuiActionRunner.execute(split::getDividerLocation);
    }

    private static void toggle(JSplitPane split) {
        GuiActionRunner.execute(() -> {
            AppShortcuts.toggleSidebar(split);
            return null;
        });
    }

    /** 開いている状態から畳むと 0 幅、もう一度で直前の幅に戻る。 */
    @Test
    public void collapseThenRestoreToPreviousWidth() {
        JSplitPane split = newSplit(280);
        assertEquals("初期はサイドバーが開いている", 280, dividerOf(split));

        toggle(split);
        assertEquals("1 回目のトグルでサイドバーが 0 幅に畳まれる", 0, dividerOf(split));

        toggle(split);
        assertEquals("2 回目のトグルで直前の幅 (280) に復元される", 280, dividerOf(split));
    }

    /** 既に畳まれていて保存幅が無い場合は、既定幅 (280) に開く。 */
    @Test
    public void restoreToDefaultWhenNoSavedWidth() {
        JSplitPane split = newSplit(0);
        // 保存プロパティが無い状態でトグル → 既定の 280 に開くはず。
        toggle(split);
        assertEquals("保存幅が無ければ既定幅 280 に開く", 280, dividerOf(split));
    }

    /** トグルは冪等な往復: 2 回トグルすれば元の幅へ戻る (状態リークしない)。 */
    @Test
    public void doubleToggleReturnsToOriginal() {
        JSplitPane split = newSplit(200);
        toggle(split);
        toggle(split);
        assertTrue("往復トグルで元の幅域に戻るはず: " + dividerOf(split),
                dividerOf(split) == 200);
    }
}
