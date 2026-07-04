// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 作者を表す「色付きイニシャル丸アバター」をオフラインで生成する (GitKraken 風)。
 *
 * <p>Gravatar 等の外部取得はせず、名前/メールのハッシュから決定的に色とイニシャルを
 * 決める。同じ作者は常に同じ色になる。</p>
 */
final class GitAvatars {

    private GitAvatars() {
    }

    /** 名前 or メールから決定的な淡い色を作る (同じ入力なら常に同じ色)。 */
    static Color colorFor(String key) {
        String k = key == null || key.isEmpty() ? "?" : key;
        int hash = 0;
        for (int i = 0; i < k.length(); i++) {
            hash = hash * 31 + k.charAt(i);
        }
        float hue = Math.floorMod(hash, 360) / 360f;
        return Color.getHSBColor(hue, 0.50f, 0.72f);
    }

    /** 表示名 (無ければメール) から 1〜2 文字のイニシャルを作る。 */
    static String initials(String name, String email) {
        String base = name != null && !name.isBlank() ? name.trim() : null;
        if (base == null) {
            base = email != null ? email.trim() : "";
            int at = base.indexOf('@');
            if (at > 0) {
                base = base.substring(0, at);
            }
        }
        if (base.isEmpty()) {
            return "?";
        }
        String[] parts = base.split("[\\s._-]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (sb.length() == 2) {
                    break;
                }
            }
        }
        return sb.length() == 0
                ? String.valueOf(Character.toUpperCase(base.charAt(0)))
                : sb.toString();
    }

    /**
     * {@code (cx, cy)} を中心に半径 {@code r} の円アバターを描く。
     * イニシャルは白抜きで中央寄せ。
     */
    static void paint(Graphics2D g2, int cx, int cy, int r,
                      String name, String email) {
        Object oldAa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(colorFor(name != null && !name.isBlank() ? name : email));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        String text = initials(name, email);
        Font font = g2.getFont().deriveFont(Font.BOLD, r * 1.0f);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        g2.setColor(Color.WHITE);
        g2.drawString(text, cx - tw / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
        if (oldAa != null) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
        }
    }
}
