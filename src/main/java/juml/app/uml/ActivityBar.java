// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * ウィンドウ左端に置く VS Code 風の縦アクティビティバー。
 *
 * <p>主要導線をアイコンだけの縦列にまとめる: プロジェクトを開く / サイドバー開閉 /
 * 検索 / コマンドパレット、最下部に設定。各ボタンはツールチップで用途を補足し、
 * Material アイコン ({@link MaterialIcons}) を用いる。状態は持たず、操作はすべて
 * {@link Actions} のコールバックへ委譲する。</p>
 */
final class ActivityBar extends JPanel {

    private static final int BAR_WIDTH = 46;
    private static final int ICON = 22;

    /** アクティビティバーの各アイコンが呼ぶアクション。null のものはボタンを出さない。 */
    static final class Actions {
        Runnable openProject;
        Runnable toggleSidebar;
        Runnable search;
        Runnable commandPalette;
        Runnable preferences;
    }

    ActivityBar(Actions a) {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(barBackground());
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, separator()));
        setPreferredSize(new Dimension(BAR_WIDTH, 10));

        JPanel top = column();
        addButton(top, MaterialIcons.Glyph.SIDEBAR, "activitybar.explorer", a.toggleSidebar, true);
        addButton(top, MaterialIcons.Glyph.FOLDER_OPEN, "activitybar.open", a.openProject, false);
        addButton(top, MaterialIcons.Glyph.SEARCH, "activitybar.search", a.search, false);
        addButton(top, MaterialIcons.Glyph.TERMINAL, "activitybar.palette",
                a.commandPalette, false);

        JPanel bottom = column();
        addButton(bottom, MaterialIcons.Glyph.SETTINGS, "activitybar.settings",
                a.preferences, false);

        add(top, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        return p;
    }

    private void addButton(JPanel column, MaterialIcons.Glyph glyph, String tooltipKey,
                           Runnable action, boolean accentMarker) {
        if (action == null) {
            return;
        }
        JButton b = new JButton(MaterialIcons.of(glyph, ICON, iconColor())) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (accentMarker) {
                    // 左端にアクセントのインジケータ (現在ビュー=Explorer を示す)
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(UiTheme.ACCENT);
                    g2.fillRect(0, getHeight() / 2 - 9, 2, 18);
                }
            }
        };
        b.setToolTipText(Messages.get(tooltipKey));
        b.getAccessibleContext().setAccessibleName(Messages.get(tooltipKey));
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(BAR_WIDTH, BAR_WIDTH));
        b.setPreferredSize(new Dimension(BAR_WIDTH, BAR_WIDTH));
        // ホバーで淡くハイライト (VS Code 風)。
        Color hover = hoverColor();
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                b.setContentAreaFilled(true);
                b.setOpaque(true);
                b.setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                b.setContentAreaFilled(false);
                b.setOpaque(false);
            }
        });
        b.addActionListener(e -> action.run());
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrap.setOpaque(false);
        wrap.setMaximumSize(new Dimension(BAR_WIDTH, BAR_WIDTH));
        wrap.add(b);
        column.add(wrap);
        column.add(Box.createVerticalStrut(2));
    }

    // ── テーマ追従の色 ──
    private static Color barBackground() {
        Color c = UIManager.getColor("Panel.background");
        if (c == null) {
            return new Color(0xECECEC);
        }
        // 本文より僅かに沈めて VS Code のアクティビティバーらしい一段濃い帯にする。
        boolean dark = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) < 128;
        int d = dark ? 10 : -10;
        return new Color(clamp(c.getRed() + d), clamp(c.getGreen() + d), clamp(c.getBlue() + d));
    }

    private static Color iconColor() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : new Color(0x3C3C3C);
    }

    private static Color hoverColor() {
        Color c = UIManager.getColor("List.selectionBackground");
        if (c == null) {
            return new Color(0x0, 0x0, 0x0, 30);
        }
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 60);
    }

    private static Color separator() {
        Color c = UIManager.getColor("Separator.foreground");
        return c != null ? c : new Color(0xBDBDBD);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
