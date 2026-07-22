// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import java.awt.Color;
import java.awt.geom.Rectangle2D;

/**
 * {@link PumlSourcePanel} のハイライトペインター群 (現在行・対応括弧)。
 *
 * <p>色はテーマ切替へ追従できるよう描画時に {@link EditorColors} から解決する。
 * ドキュメントは一切変更しない装飾専用。</p>
 */
final class PumlEditorPainters {

    private PumlEditorPainters() {
    }

    /** 対応括弧を枠線で囲むペインター (色はテーマで描画時解決)。 */
    static final Highlighter.HighlightPainter BRACKET =
            (g, p0, p1, bounds, c) -> {
                try {
                    Rectangle2D r = c.modelToView2D(p0);
                    Rectangle2D r2 = c.modelToView2D(p1);
                    if (r == null || r2 == null) {
                        return;
                    }
                    g.setColor(EditorColors.isDark()
                            ? new Color(0x6A, 0x8A, 0xAA) : new Color(0x80, 0xA0, 0xC8));
                    int w = Math.max(1, (int) (r2.getX() - r.getX()));
                    g.drawRect((int) r.getX(), (int) r.getY(), w,
                            Math.max(1, (int) Math.ceil(r.getHeight()) - 1));
                } catch (BadLocationException ignored) {
                    // 無視。
                }
            };

    /** 行全体 (ビュー幅いっぱい) を塗る現在行ハイライトペインター。 */
    static final Highlighter.HighlightPainter CURRENT_LINE =
            (g, p0, p1, bounds, c) -> {
                try {
                    Rectangle2D r = c.modelToView2D(p0);
                    if (r == null) {
                        return;
                    }
                    g.setColor(EditorColors.currentLine());
                    g.fillRect(0, (int) r.getY(), c.getWidth(), (int) Math.ceil(r.getHeight()));
                } catch (BadLocationException ignored) {
                    // 無視 (致命的でない)。
                }
            };
}
