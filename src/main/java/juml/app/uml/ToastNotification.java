// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * VS Code 風のトースト通知。画面右下に一時的に表示して自動的に消える。
 */
final class ToastNotification {

    private static final int DISPLAY_MS = 5000;
    private static final int MARGIN = 16;

    private ToastNotification() {
    }

    static void show(Component anchor, String message) {
        JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        JLayeredPane layered = root.getLayeredPane();

        JPanel toast = new JPanel(new BorderLayout(8, 0));
        Color bg = EditorColors.isDark() ? new Color(0x3C3C3C) : new Color(0xF3F3F3);
        Color fg = EditorColors.isDark() ? new Color(0xCCCCCC) : new Color(0x333333);
        Color border = EditorColors.isDark() ? new Color(0x505050) : new Color(0xCCCCCC);
        toast.setBackground(bg);
        toast.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JLabel label = new JLabel(message);
        label.setForeground(fg);
        toast.add(label, BorderLayout.CENTER);
        toast.setOpaque(true);

        Dimension pref = toast.getPreferredSize();
        toast.setSize(pref);
        positionToast(toast, layered, pref);

        layered.add(toast, JLayeredPane.POPUP_LAYER);
        layered.revalidate();
        layered.repaint();

        ComponentAdapter reposition = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                positionToast(toast, layered, pref);
            }
        };
        layered.addComponentListener(reposition);

        Timer hide = new Timer(DISPLAY_MS, e -> {
            // トーストを消すときはリスナーも必ず外す。外し忘れると、消えた
            // トーストを参照する ComponentListener が共有 JLayeredPane に溜まり続け、
            // 長時間セッションでウィンドウリサイズが徐々に重くなる (リスナーリーク)。
            layered.removeComponentListener(reposition);
            layered.remove(toast);
            layered.revalidate();
            layered.repaint();
        });
        hide.setRepeats(false);
        hide.start();
    }

    private static void positionToast(JPanel toast, JLayeredPane layered, Dimension pref) {
        Insets insets = layered.getInsets();
        int x = layered.getWidth() - insets.right - pref.width - MARGIN;
        int y = layered.getHeight() - insets.bottom - pref.height - MARGIN;
        toast.setLocation(Math.max(0, x), Math.max(0, y));
    }
}
