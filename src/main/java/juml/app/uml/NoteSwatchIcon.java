// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/** 付箋の色メニュー用の小さな色見本アイコン (塗り + 枠)。 */
final class NoteSwatchIcon implements Icon {

    private final Color fill;
    private final Color border;

    NoteSwatchIcon(Color fill, Color border) {
        this.fill = fill;
        this.border = border;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        g.setColor(fill);
        g.fillRect(x, y, 14, 12);
        g.setColor(border);
        g.drawRect(x, y, 14, 12);
    }

    @Override
    public int getIconWidth() {
        return 16;
    }

    @Override
    public int getIconHeight() {
        return 14;
    }
}
