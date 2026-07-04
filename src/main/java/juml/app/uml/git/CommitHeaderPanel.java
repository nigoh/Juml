// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.CommitInfo;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 選択コミットのヘッダ表示: アバター + 作者、SHA・相対日時を強調する。2 コミット比較モード
 * では新しい方のアバター/作者と「old → new」を表示する (GitKraken 風の詳細ヘッダ)。
 */
final class CommitHeaderPanel extends JPanel {

    private CommitInfo commit;
    /** 非 null なら 2 コミット比較モードの 2 行目 (old → new)。 */
    private String compareLine;

    CommitHeaderPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(10, 46));
    }

    void setCommit(CommitInfo c) {
        this.commit = c;
        this.compareLine = null;
        repaint();
    }

    /** 2 コミット比較モード: 新しい方のアバター/作者と「old → new」を表示する。 */
    void setCompare(CommitInfo older, CommitInfo newer) {
        this.commit = newer;
        this.compareLine = older.shortSha + "  →  " + newer.shortSha;
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (commit == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int h = getHeight();
        int r = 16;
        int cx = 6 + r;
        int cy = h / 2;
        GitAvatars.paint(g2, cx, cy, r, commit.author, commit.authorEmail);

        Color fg = UIManager.getColor("Label.foreground");
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (fg == null) {
            fg = Color.DARK_GRAY;
        }
        if (muted == null) {
            muted = Color.GRAY;
        }
        int tx = cx + r + 10;
        Font bold = getFont().deriveFont(Font.BOLD);
        g2.setFont(bold);
        FontMetrics bfm = g2.getFontMetrics();
        g2.setColor(fg);
        g2.drawString(commit.author, tx, cy - 2);
        Font small = getFont().deriveFont(getFont().getSize2D() - 1f);
        g2.setFont(small);
        g2.setColor(muted);
        String line2 = compareLine != null ? compareLine
                : commit.shortSha + "   ·   " + GitTimes.relative(commit.when);
        g2.drawString(line2, tx, cy + bfm.getHeight() - 2);
        g2.dispose();
    }
}
