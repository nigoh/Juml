// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;

/**
 * 未対応構文で GUI 編集を無効化しているときの警告バナー描画 (各図種キャンバス共通)。
 * 件数だけでなく実際の未対応行 (先頭数件) を列挙し、テキストのどこを直せば
 * 編集可能になるか分かるようにする。
 */
final class SketchBanner {

    /** バナーに列挙する未対応行の最大数 (残りは「... 他 N 行」でまとめる)。 */
    private static final int MAX_BANNER_LINES = 6;

    private SketchBanner() {
    }

    /** {@code target} の上端へ警告バナーを描く。 */
    static void paint(Graphics2D g2, JComponent target, List<String> unsupported) {
        String header = java.text.MessageFormat.format(
                Messages.get("sketch.disabled.header"), unsupported.size());
        int shown = Math.min(unsupported.size(), MAX_BANNER_LINES);
        int lineH = 15;
        int pad = 5;
        int rows = 1 + shown + (unsupported.size() > shown ? 1 : 0);
        int bannerH = pad * 2 + rows * lineH;
        g2.setColor(new Color(0xB71C1C));
        g2.fillRect(0, 0, target.getWidth(), bannerH);
        g2.setColor(Color.WHITE);
        int y = pad + lineH - 3;
        Font base = target.getFont();
        g2.setFont(base.deriveFont(Font.BOLD));
        g2.drawString(header, 8, y);
        g2.setFont(base.deriveFont(Font.PLAIN));
        for (int i = 0; i < shown; i++) {
            y += lineH;
            g2.drawString("• " + truncate(unsupported.get(i), 90), 16, y);
        }
        if (unsupported.size() > shown) {
            y += lineH;
            g2.drawString(java.text.MessageFormat.format(
                    Messages.get("sketch.disabled.more"), unsupported.size() - shown), 16, y);
        }
    }

    private static String truncate(String s, int max) {
        String t = s == null ? "" : s;
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
