// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * VS Code 風の MRU (最近使用順) タブ切替を実装するコントローラ。
 *
 * <p>{@code Ctrl}(macOS は {@code ⌘})を押したまま {@code Tab} を繰り返し押すと、
 * 最近使ったタブ順のオーバーレイ一覧の中を移動し、修飾キーを離した時点で確定する。
 * {@code Shift} 併用で逆順。確定すると、そのタブが MRU の先頭に来る。これにより
 * 「ひとつ前に見ていたタブ」へ素早く戻れる (単純な索引順巡回より直感的)。</p>
 *
 * <p>索引順の巡回は {@code Ctrl+PageUp/PageDown} ({@link TabKeyBindings}) が引き続き担う。
 * 本コントローラは動的 (図) タブだけを対象とし、末尾の固定ユーティリティタブは含めない。</p>
 */
final class TabMruController {

    private final JTabbedPane tabs;
    private final IntSupplier dynamicCount;
    /** 動的タブの最近使用順 (先頭 = 直近)。要素は各タブのコンテンツ Component。 */
    private final List<Component> mru = new ArrayList<>();
    private final Map<Component, String> labels = new IdentityHashMap<>();

    /** 巡回確定のトリガとなる修飾キー。Ctrl+Tab で束縛するため常に Ctrl。 */
    private static final int MODIFIER_KEY = KeyEvent.VK_CONTROL;

    // ── 巡回中の状態 ──
    private boolean traversing;
    private List<Component> snapshot;
    private int cursor;
    private JWindow overlay;
    private JPanel overlayList;
    private KeyboardFocusManager focusManager;
    private java.awt.KeyEventDispatcher releaseDispatcher;

    TabMruController(JTabbedPane tabs, IntSupplier dynamicCount) {
        this.tabs = tabs;
        this.dynamicCount = dynamicCount;
    }

    /**
     * Ctrl+Tab / Ctrl+Shift+Tab を MRU 巡回に束縛する。macOS でも Cmd+Tab は OS 予約の
     * ため、プラットフォーム共通で {@code Ctrl+Tab} を使い、確定は Ctrl の離脱
     * ({@link #MODIFIER_KEY}) で検出する。
     */
    void install() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK),
                "juml.mruNext", () -> cycle(1));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                "juml.mruPrev", () -> cycle(-1));
    }

    private void bind(KeyStroke ks, String name, Runnable body) {
        tabs.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, name);
        tabs.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.run();
            }
        });
    }

    /** タブがアクティブ化されたとき呼ぶ。巡回中でなければ MRU 先頭へ繰り上げる。 */
    void onActivated(Component tab, String label) {
        if (tab == null) {
            return;
        }
        labels.put(tab, label);
        if (traversing) {
            return; // 巡回中の選択変更は MRU を並べ替えない (確定時にまとめて反映)
        }
        mru.remove(tab);
        mru.add(0, tab);
    }

    /** タブが閉じられたとき呼ぶ。 */
    void onClosed(Component tab) {
        mru.remove(tab);
        labels.remove(tab);
    }

    /** MRU を {@code dir} 方向に 1 つ進める (初回は巡回モードに入りオーバーレイを出す)。 */
    void cycle(int dir) {
        int dyn = Math.max(0, dynamicCount.getAsInt());
        if (dyn <= 1) {
            return;
        }
        if (!traversing) {
            snapshot = currentMruDynamic(dyn);
            if (snapshot.size() <= 1) {
                snapshot = null;
                return;
            }
            traversing = true;
            cursor = 0;
            showOverlay();
            installReleaseDispatcher();
        }
        cursor = Math.floorMod(cursor + dir, snapshot.size());
        Component target = snapshot.get(cursor);
        tabs.setSelectedComponent(target);
        updateOverlay();
    }

    /** 現在の動的タブを MRU 順に並べたリスト (MRU 未登録分は索引順で末尾に補完)。 */
    List<Component> currentMruDynamic(int dyn) {
        List<Component> live = new ArrayList<>();
        for (int i = 0; i < dyn && i < tabs.getTabCount(); i++) {
            live.add(tabs.getComponentAt(i));
        }
        List<Component> ordered = new ArrayList<>();
        for (Component c : mru) {
            if (live.contains(c)) {
                ordered.add(c);
            }
        }
        for (Component c : live) {
            if (!ordered.contains(c)) {
                ordered.add(c);
            }
        }
        return ordered;
    }

    /** 修飾キーが離されたら確定するディスパッチャを登録する。 */
    private void installReleaseDispatcher() {
        focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        releaseDispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == MODIFIER_KEY) {
                commit();
            } else if (e.getID() == KeyEvent.KEY_PRESSED
                    && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                cancel();
                return true;
            }
            return false;
        };
        focusManager.addKeyEventDispatcher(releaseDispatcher);
    }

    private void endTraversal() {
        traversing = false;
        snapshot = null;
        if (focusManager != null && releaseDispatcher != null) {
            focusManager.removeKeyEventDispatcher(releaseDispatcher);
        }
        releaseDispatcher = null;
        focusManager = null;
        hideOverlay();
    }

    /** 確定: 現在選択中のタブを MRU 先頭へ繰り上げて巡回を終える。 */
    private void commit() {
        Component chosen = tabs.getSelectedComponent();
        endTraversal();
        if (chosen != null) {
            mru.remove(chosen);
            mru.add(0, chosen);
        }
    }

    /** 取消: 巡回開始時のタブへ戻して終える。 */
    private void cancel() {
        Component original = (snapshot != null && !snapshot.isEmpty()) ? snapshot.get(0) : null;
        endTraversal();
        if (original != null) {
            tabs.setSelectedComponent(original);
        }
    }

    // ── オーバーレイ ──

    private void showOverlay() {
        try {
            Window owner = SwingUtilities.getWindowAncestor(tabs);
            overlay = new JWindow(owner);
            overlay.setFocusableWindowState(false);
            overlayList = new JPanel();
            overlayList.setLayout(new BoxLayout(overlayList, BoxLayout.Y_AXIS));
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor(), 1),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            root.setBackground(bgColor());
            JLabel title = new JLabel(juml.util.Messages.get("tab.mru.title"));
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            title.setForeground(fgColor());
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            root.add(title);
            root.add(Box.createVerticalStrut(6));
            overlayList.setBackground(bgColor());
            overlayList.setAlignmentX(Component.LEFT_ALIGNMENT);
            root.add(overlayList);
            overlay.setContentPane(root);
            updateOverlay();
            if (owner != null) {
                overlay.pack();
                int x = owner.getX() + (owner.getWidth() - overlay.getWidth()) / 2;
                int y = owner.getY() + owner.getHeight() / 3;
                overlay.setLocation(x, y);
            }
            overlay.setVisible(true);
        } catch (RuntimeException ex) {
            overlay = null; // オーバーレイ失敗時も選択切替自体は機能させる
        }
    }

    private void updateOverlay() {
        if (overlay == null || overlayList == null || snapshot == null) {
            return;
        }
        overlayList.removeAll();
        for (int i = 0; i < snapshot.size(); i++) {
            String label = labels.getOrDefault(snapshot.get(i), "—");
            JLabel row = new JLabel(label);
            row.setOpaque(true);
            row.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 16));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (i == cursor) {
                row.setBackground(UiTheme.ACCENT);
                row.setForeground(Color.WHITE);
            } else {
                row.setBackground(bgColor());
                row.setForeground(fgColor());
            }
            overlayList.add(row);
        }
        overlayList.revalidate();
        overlayList.repaint();
        if (overlay.isVisible()) {
            overlay.pack();
            Window owner = overlay.getOwner();
            if (owner != null) {
                int x = owner.getX() + (owner.getWidth() - overlay.getWidth()) / 2;
                int y = owner.getY() + owner.getHeight() / 3;
                overlay.setLocation(x, y);
            }
        }
    }

    private void hideOverlay() {
        if (overlay != null) {
            overlay.dispose();
            overlay = null;
            overlayList = null;
        }
    }

    private static Color bgColor() {
        Color c = UIManager.getColor("List.background");
        return c != null ? c : Color.WHITE;
    }

    private static Color fgColor() {
        Color c = UIManager.getColor("List.foreground");
        return c != null ? c : Color.BLACK;
    }

    private static Color borderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : new Color(0x9E9E9E);
    }
}
