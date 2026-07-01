// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntSupplier;

/**
 * ダイアグラムタブ ({@link JTabbedPane} の動的タブ) をドラッグで並び替え可能にするヘルパ。
 *
 * <p>VS Code のエディタタブ同様、タブヘッダを掴んで左右にドラッグすると、その位置へ
 * タブが即時移動する (ライブ並び替え)。末尾の固定ユーティリティタブ
 * ({@code dynamicCount} より後ろ) は並び替え対象に含めない。</p>
 *
 * <p>並び替えは {@link JTabbedPane} 上のタブ位置のみを操作する。タブのキー→実体マップは
 * 検索用であり順序には依存しないため、本体クラスの状態を変更する必要はない。</p>
 */
final class TabReorderHandler {

    /** これ未満の水平移動はクリックとみなし並び替えを始めない。 */
    private static final int DRAG_THRESHOLD = 5;
    private static final Color INDICATOR_COLOR = new Color(0x00, 0x7A, 0xCC);
    private static final Border LEFT_INDICATOR =
            BorderFactory.createMatteBorder(0, 2, 0, 0, INDICATOR_COLOR);
    private static final Border RIGHT_INDICATOR =
            BorderFactory.createMatteBorder(0, 0, 0, 2, INDICATOR_COLOR);

    private TabReorderHandler() {
    }

    /**
     * タブヘッダ (とその子要素) にドラッグ並び替えを仕込む。
     *
     * @param tabs         対象のタブペイン
     * @param header       このタブのヘッダコンポーネント (タブ実体ではなく tabComponent)
     * @param dynamicCount 動的タブ数を返すサプライヤ (= 全タブ数 - 固定ユーティリティタブ数)
     */
    static void install(JTabbedPane tabs, Component header, IntSupplier dynamicCount) {
        MouseAdapter ma = new MouseAdapter() {
            private boolean armed;
            private Point pressPoint;
            private int lastIndicatorIdx = -1;

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    armed = true;
                    pressPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), header);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                armed = false;
                pressPoint = null;
                clearIndicator(tabs);
                lastIndicatorIdx = -1;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!armed || pressPoint == null) {
                    return;
                }
                Point inHeader = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), header);
                if (Math.abs(inHeader.x - pressPoint.x) < DRAG_THRESHOLD) {
                    return;
                }
                int from = indexOf(tabs, header);
                if (from < 0) {
                    return;
                }
                Point inTabs = SwingUtilities.convertPoint(header, inHeader, tabs);
                int target = tabs.indexAtLocation(inTabs.x, inTabs.y);
                int dyn = Math.max(0, dynamicCount.getAsInt());
                if (target >= 0 && target < dyn && from < dyn && target != from) {
                    showIndicator(tabs, target, from < target);
                    lastIndicatorIdx = target;
                    moveTab(tabs, from, target);
                } else if (target < 0 || target == from) {
                    clearIndicator(tabs);
                    lastIndicatorIdx = -1;
                }
            }
        };
        addRecursively(header, ma);
    }

    /** ヘッダとその全子孫に同じリスナを付け、タブのどこを掴んでもドラッグできるようにする。 */
    private static void addRecursively(Component c, MouseAdapter ma) {
        c.addMouseListener(ma);
        c.addMouseMotionListener(ma);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                addRecursively(child, ma);
            }
        }
    }

    /** {@code header} を tabComponent に持つタブの索引 (無ければ -1)。 */
    private static int indexOf(JTabbedPane tabs, Component header) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTabComponentAt(i) == header) {
                return i;
            }
        }
        return -1;
    }

    private static void showIndicator(JTabbedPane tabs, int idx, boolean rightSide) {
        clearIndicator(tabs);
        Component hdr = tabs.getTabComponentAt(idx);
        if (hdr instanceof JComponent) {
            ((JComponent) hdr).setBorder(rightSide ? RIGHT_INDICATOR : LEFT_INDICATOR);
        }
    }

    private static void clearIndicator(JTabbedPane tabs) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component hdr = tabs.getTabComponentAt(i);
            if (hdr instanceof JComponent) {
                ((JComponent) hdr).setBorder(null);
            }
        }
    }

    /** タブを {@code from} から {@code to} へ移動する (内容・ヘッダ・選択状態を保持)。 */
    static void moveTab(JTabbedPane tabs, int from, int to) {
        Component comp = tabs.getComponentAt(from);
        Component tabComp = tabs.getTabComponentAt(from);
        String title = tabs.getTitleAt(from);
        String tip = tabs.getToolTipTextAt(from);
        Icon icon = tabs.getIconAt(from);
        boolean wasSelected = tabs.getSelectedIndex() == from;
        tabs.remove(from);
        tabs.insertTab(title, icon, comp, tip, to);
        tabs.setTabComponentAt(to, tabComp);
        if (wasSelected) {
            tabs.setSelectedIndex(to);
        }
    }
}
