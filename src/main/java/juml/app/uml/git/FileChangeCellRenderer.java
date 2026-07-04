// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.FileChange;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JList;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** 変更ファイル一覧の行: 状態レター (A/M/D/R/C) の色付きバッジ + パス (GitKraken 風)。 */
final class FileChangeCellRenderer extends DefaultListCellRenderer {

    private final StatusBadgeIcon badge = new StatusBadgeIcon();

    @Override public Component getListCellRendererComponent(
            JList<?> list, Object value, int index,
            boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (!(value instanceof FileChange)) {
            return this;
        }
        FileChange fc = (FileChange) value;
        String path;
        if ("RENAME".equals(fc.changeType) || "COPY".equals(fc.changeType)) {
            path = fc.oldPath + "  →  " + fc.path;
        } else {
            path = "DELETE".equals(fc.changeType) ? fc.oldPath : fc.path;
        }
        setText(path);
        badge.letter = fc.changeType.isEmpty() ? "?" : fc.changeType.substring(0, 1);
        badge.color = statusColor(fc.changeType);
        setIcon(badge);
        setIconTextGap(8);
        setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        return this;
    }

    private static Color statusColor(String changeType) {
        switch (changeType) {
            case "ADD":    return new Color(0x00A36B);
            case "MODIFY": return new Color(0x2E9BFF);
            case "DELETE": return new Color(0xE0518A);
            case "RENAME": return new Color(0x9B59F0);
            case "COPY":   return new Color(0x18C0C4);
            default:       return Color.GRAY;
        }
    }

    /** 変更種別レターの角丸バッジ。 */
    private static final class StatusBadgeIcon implements Icon {
        private static final int SIZE = 16;
        private String letter = "?";
        private Color color = Color.GRAY;

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(x, y, SIZE, SIZE, 5, 5);
            Font f = c.getFont().deriveFont(Font.BOLD, SIZE - 5f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(letter);
            g2.setColor(Color.WHITE);
            g2.drawString(letter, x + (SIZE - tw) / 2,
                    y + (SIZE + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override public int getIconWidth() {
            return SIZE;
        }

        @Override public int getIconHeight() {
            return SIZE;
        }
    }
}
