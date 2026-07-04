// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.LineDiff.Row;
import juml.app.uml.git.LineDiff.Type;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;

/**
 * {@link LineDiff} の整列結果を左右 2 ペイン (旧 / 新) で並べる読み取り専用ビュー。
 *
 * <p>左に旧、右に新を置き、垂直スクロールを共有 (行が常に揃う)・水平スクロールは各側独立。
 * 削除行は左を赤、追加行は右を緑、変更行は両側を色付けする。配色はテーマの明暗に追従し、
 * 可視行だけ描画するので大きな diff でも軽い。</p>
 */
final class SideBySideDiffView extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_DIFF = "diff";

    private final CardLayout cards = new CardLayout();
    private final JLabel emptyLabel = new JLabel("", SwingConstants.CENTER);
    private final SidePane left = new SidePane(true);
    private final SidePane right = new SidePane(false);
    private final JScrollPane leftScroll = new JScrollPane(left);
    private final JScrollPane rightScroll = new JScrollPane(right);

    SideBySideDiffView() {
        setLayout(cards);
        JPanel split = new JPanel(new GridLayout(1, 2, 1, 0));
        // 垂直スクロールバーを共有して両側の行を同期させる (行数=高さが等しいため成立)。
        rightScroll.getVerticalScrollBar().setModel(
                leftScroll.getVerticalScrollBar().getModel());
        leftScroll.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        split.add(leftScroll);
        split.add(rightScroll);
        add(emptyLabel, CARD_EMPTY);
        add(split, CARD_DIFF);
        cards.show(this, CARD_EMPTY);
    }

    /** 整列済みの行を差し込む。 */
    void setRows(List<Row> rows) {
        List<Row> r = rows != null ? rows : List.of();
        left.setRows(r);
        right.setRows(r);
        cards.show(this, CARD_DIFF);
    }

    /** diff が無いときのヒント文言を表示する。 */
    void setEmptyText(String text) {
        emptyLabel.setText(text != null ? text : "");
        cards.show(this, CARD_EMPTY);
    }

    /** 片側 (旧 or 新) の行を描く内部コンポーネント。 */
    private static final class SidePane extends JComponent implements Scrollable {
        private static final int GUTTER_W = 48;
        private static final int PAD = 6;

        private final boolean oldSide;
        private List<Row> rows = List.of();
        private int lineHeight = 16;
        private int contentWidth = 200;
        private Color changeBg;
        private Color gutterFg;

        SidePane(boolean oldSide) {
            this.oldSide = oldSide;
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            setOpaque(true);
            refreshTheme();
        }

        @Override public void updateUI() {
            super.updateUI();
            refreshTheme();
        }

        private void refreshTheme() {
            Color bg = background();
            boolean dark = luminance(bg) < 0.5;
            Color base = oldSide
                    ? (dark ? new Color(0xF85149) : new Color(0xCF222E))
                    : (dark ? new Color(0x2EA043) : new Color(0x2DA44E));
            changeBg = tint(base, dark ? 0.20f : 0.16f, bg);
            gutterFg = new Color(0x8A94A6);
        }

        private Color background() {
            Color bg = UIManager.getColor("TextArea.background");
            if (bg == null) {
                bg = UIManager.getColor("Panel.background");
            }
            return bg != null ? bg : Color.WHITE;
        }

        private Color foreground() {
            Color fg = UIManager.getColor("TextArea.foreground");
            return fg != null ? fg : Color.DARK_GRAY;
        }

        void setRows(List<Row> rows) {
            this.rows = rows;
            FontMetrics fm = getFontMetrics(getFont());
            lineHeight = fm.getHeight();
            int maxText = 0;
            for (Row r : rows) {
                String t = oldSide ? r.oldText : r.newText;
                if (t != null) {
                    maxText = Math.max(maxText, fm.stringWidth(t));
                }
            }
            contentWidth = GUTTER_W + PAD * 2 + maxText + 8;
            revalidate();
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(contentWidth, Math.max(1, rows.size()) * lineHeight + PAD);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(background());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int ascent = fm.getAscent();
            Rectangle clip = g2.getClipBounds();
            int first = clip != null ? Math.max(0, clip.y / lineHeight) : 0;
            int last = clip != null
                    ? Math.min(rows.size(), (clip.y + clip.height) / lineHeight + 1)
                    : rows.size();
            for (int i = first; i < last; i++) {
                Row r = rows.get(i);
                int y = i * lineHeight;
                boolean changed = oldSide
                        ? (r.type == Type.REMOVED || r.type == Type.MODIFIED)
                        : (r.type == Type.ADDED || r.type == Type.MODIFIED);
                if (changed) {
                    g2.setColor(changeBg);
                    g2.fillRect(0, y, getWidth(), lineHeight);
                }
                int lineNo = oldSide ? r.oldLine : r.newLine;
                String text = oldSide ? r.oldText : r.newText;
                int baseline = y + ascent;
                if (lineNo > 0) {
                    String num = Integer.toString(lineNo);
                    g2.setColor(gutterFg);
                    g2.drawString(num, GUTTER_W - PAD - fm.stringWidth(num), baseline);
                }
                if (text != null) {
                    g2.setColor(foreground());
                    g2.drawString(text, GUTTER_W + PAD, baseline);
                }
            }
            g2.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override public int getScrollableUnitIncrement(Rectangle vr, int orient, int dir) {
            return orient == SwingConstants.VERTICAL ? lineHeight : 24;
        }

        @Override public int getScrollableBlockIncrement(Rectangle vr, int orient, int dir) {
            return orient == SwingConstants.VERTICAL
                    ? Math.max(lineHeight, vr.height - lineHeight) : Math.max(24, vr.width - 24);
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        private static Color tint(Color color, float alpha, Color bg) {
            int r = Math.round(color.getRed() * alpha + bg.getRed() * (1 - alpha));
            int g = Math.round(color.getGreen() * alpha + bg.getGreen() * (1 - alpha));
            int b = Math.round(color.getBlue() * alpha + bg.getBlue() * (1 - alpha));
            return new Color(r, g, b);
        }

        private static double luminance(Color c) {
            return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
        }
    }
}
