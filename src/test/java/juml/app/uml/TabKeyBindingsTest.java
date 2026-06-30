// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * {@link TabKeyBindings} のキーバインド登録と巡回ロジックのテスト。
 *
 * <p>Ctrl/Cmd+W (閉じる) / Ctrl+Shift+T (復元) が action map に結線されること、
 * Ctrl+PageDown/PageUp が末尾固定タブ ({@code fixedSuffix}) を除外して動的タブ範囲内で
 * 巡回することを、frame を可視化せず input/action map 直接検証で守る。</p>
 *
 * <p>{@code TabKeyBindings.install} と本テストはともに
 * {@link Toolkit#getMenuShortcutKeyMaskEx()} を呼ぶ。これはヘッドレスでは
 * {@code HeadlessException} を投げるため display が必要 → {@code Assume} で skip する
 * (xvfb-run でラップして実行)。Robot は使わない。</p>
 */
public class TabKeyBindingsTest {

    private int mask;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境ではスキップ (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static JTabbedPane newTabs(int count) {
        return GuiActionRunner.execute(() -> {
            JTabbedPane tabs = new JTabbedPane();
            for (int i = 0; i < count; i++) {
                tabs.addTab("t" + i, new JLabel("c" + i));
            }
            return tabs;
        });
    }

    private static void fire(JTabbedPane tabs, String actionKey) {
        GuiActionRunner.execute(() -> {
            tabs.getActionMap().get(actionKey)
                    .actionPerformed(new ActionEvent(tabs, ActionEvent.ACTION_PERFORMED, actionKey));
            return null;
        });
    }

    /** Ctrl/Cmd+W が closeActiveTab に、Ctrl+Shift+T が reopenClosedTab に結線される。 */
    @Test
    public void closeAndReopenAreBoundToCallbacks() {
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger reopened = new AtomicInteger();
        JTabbedPane tabs = newTabs(3);
        GuiActionRunner.execute(() -> {
            TabKeyBindings.install(tabs, closed::incrementAndGet, reopened::incrementAndGet);
            return null;
        });

        KeyStroke closeKs = KeyStroke.getKeyStroke(KeyEvent.VK_W, mask);
        KeyStroke reopenKs = KeyStroke.getKeyStroke(KeyEvent.VK_T, mask | InputEvent.SHIFT_DOWN_MASK);
        assertEquals("Ctrl+W が closeTab に登録されるはず", "juml.closeTab",
                tabs.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(closeKs));
        assertEquals("Ctrl+Shift+T が reopenTab に登録されるはず", "juml.reopenTab",
                tabs.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(reopenKs));
        assertNotNull(tabs.getActionMap().get("juml.closeTab"));

        fire(tabs, "juml.closeTab");
        fire(tabs, "juml.reopenTab");
        assertEquals("Ctrl+W で closeActiveTab が 1 回呼ばれる", 1, closed.get());
        assertEquals("Ctrl+Shift+T で reopenClosedTab が 1 回呼ばれる", 1, reopened.get());
    }

    /** Ctrl+PageDown/PageUp が動的タブ範囲内で索引を巡回し、端で巻き戻す。 */
    @Test
    public void pageNavigationCyclesWithinDynamicTabs() {
        JTabbedPane tabs = newTabs(3); // すべて動的タブ (fixedSuffix=0)
        GuiActionRunner.execute(() -> {
            TabKeyBindings.install(tabs, () -> { }, () -> { });
            tabs.setSelectedIndex(0);
            return null;
        });

        fire(tabs, "juml.nextTabPg");
        assertEquals("PageDown で次のタブへ", 1, (int) GuiActionRunner.execute(tabs::getSelectedIndex));
        fire(tabs, "juml.nextTabPg");
        assertEquals(2, (int) GuiActionRunner.execute(tabs::getSelectedIndex));
        fire(tabs, "juml.nextTabPg");
        assertEquals("末尾の次は先頭へ巻き戻る", 0, (int) GuiActionRunner.execute(tabs::getSelectedIndex));

        fire(tabs, "juml.prevTabPg");
        assertEquals("PageUp で先頭から末尾へ巻き戻る", 2,
                (int) GuiActionRunner.execute(tabs::getSelectedIndex));
    }

    /** 末尾固定タブ (fixedSuffix) は巡回対象から除外される。 */
    @Test
    public void fixedSuffixTabsAreExcludedFromCycling() {
        JTabbedPane tabs = newTabs(3); // 3 枚中、末尾 1 枚を固定タブとする → 動的は 2 枚 (index 0,1)
        GuiActionRunner.execute(() -> {
            TabKeyBindings.install(tabs, 1, () -> { }, () -> { });
            tabs.setSelectedIndex(0);
            return null;
        });

        fire(tabs, "juml.nextTabPg");
        assertEquals("動的タブ 0→1", 1, (int) GuiActionRunner.execute(tabs::getSelectedIndex));
        fire(tabs, "juml.nextTabPg");
        assertEquals("固定タブ(2)を飛ばして先頭へ巻き戻る", 0,
                (int) GuiActionRunner.execute(tabs::getSelectedIndex));
    }
}
