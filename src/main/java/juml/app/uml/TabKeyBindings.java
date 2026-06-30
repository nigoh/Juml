// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import java.awt.AWTKeyStroke;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ダイアグラムタブの {@link JTabbedPane} に VS Code 風のキーボード操作を登録するヘルパ。
 *
 * <ul>
 *   <li>Ctrl/Cmd+W … アクティブな動的タブを閉じる</li>
 *   <li>Ctrl+Tab / Ctrl+Shift+Tab … 次/前のタブへ巡回</li>
 *   <li>Ctrl/Cmd+PageDown / PageUp … 次/前のタブへ巡回</li>
 * </ul>
 *
 * <p>Ctrl+Tab はデフォルトでフォーカストラバーサルに消費されるため、束縛前に
 * トラバーサルキーから取り除く。</p>
 */
final class TabKeyBindings {

    private TabKeyBindings() {
    }

    /**
     * 与えられたタブペインにキーボード操作を登録する。巡回はタブ索引のみで完結し、
     * タブを閉じる操作だけ {@code closeActiveTab} へ委譲する。
     */
    static void install(JTabbedPane tabs, Runnable closeActiveTab, Runnable reopenClosedTab) {
        install(tabs, 0, closeActiveTab, reopenClosedTab);
    }

    /**
     * {@code fixedSuffix} 本の末尾固定ユーティリティタブを巡回対象から除外して登録する。
     * VS Code の {@code Ctrl+Tab} が「自分が開いたエディタ」だけを巡回するのに合わせ、
     * 図 (動的タブ) の範囲内だけで巡回する。
     */
    static void install(JTabbedPane tabs, int fixedSuffix,
                        Runnable closeActiveTab, Runnable reopenClosedTab) {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        // Ctrl+Tab / Ctrl+Shift+Tab がタブ順送りに届くよう、フォーカストラバーサルから除外する。
        clearTraversalKey(tabs, KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
        clearTraversalKey(tabs, KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));

        bind(tabs, KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask),
                "juml.closeTab", e -> closeActiveTab.run());
        bind(tabs, KeyStroke.getKeyStroke(KeyEvent.VK_T,
                        menuMask | InputEvent.SHIFT_DOWN_MASK),
                "juml.reopenTab", e -> reopenClosedTab.run());
        // Ctrl+Tab / Ctrl+Shift+Tab は MRU (最近使用順) 巡回として
        // {@link TabMruController} が担う。ここでは索引順の Ctrl+PageUp/PageDown のみ束ねる。
        bind(tabs, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, menuMask),
                "juml.nextTabPg", e -> cycle(tabs, 1, fixedSuffix));
        bind(tabs, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, menuMask),
                "juml.prevTabPg", e -> cycle(tabs, -1, fixedSuffix));
    }

    private static void bind(JComponent c, KeyStroke ks, String name,
                             Consumer<ActionEvent> body) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.accept(e);
            }
        });
    }

    /**
     * 動的タブ (先頭 {@code count - fixedSuffix} 本) の範囲内で索引を {@code dir} だけ
     * 進める (端で巻き戻す)。末尾の固定ユーティリティタブは巡回対象から除外する。
     * 現在の選択が固定タブ側にある場合は動的タブの端へ寄せる。動的タブが 0/1 枚なら何もしない。
     */
    private static void cycle(JTabbedPane tabs, int dir, int fixedSuffix) {
        int total = tabs.getTabCount();
        int n = Math.max(0, total - Math.max(0, fixedSuffix));
        if (n <= 1) {
            return;
        }
        int i = tabs.getSelectedIndex();
        if (i < 0 || i >= n) {
            // 固定タブ等にフォーカスがある → 動的タブの端から入る。
            tabs.setSelectedIndex(dir >= 0 ? 0 : n - 1);
            return;
        }
        tabs.setSelectedIndex((i + dir + n) % n);
    }

    private static void clearTraversalKey(JComponent c, int id, AWTKeyStroke ks) {
        Set<AWTKeyStroke> keys = new HashSet<>(c.getFocusTraversalKeys(id));
        keys.remove(ks);
        c.setFocusTraversalKeys(id, keys);
    }
}
