// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JComponent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.function.BooleanSupplier;

/**
 * {@link JTextComponent} の各行に合わせて行番号を描く row header。
 *
 * <p>各行の y 座標は {@code modelToView2D} で取得するため、フォントや行高の差異が
 * あっても本文と整列する。VS Code 風のソースビューア ({@link JavaSourcePanel}) と
 * 自由編集 PlantUML エディタ ({@link PumlSourcePanel}) で共有する。</p>
 *
 * <p>装飾は {@link javax.swing.text.StyleConstants#setForeground} のみを用いる前提
 * (段落の {@code spaceAbove} 等を変えると modelToView2D の整列が崩れる)。</p>
 */
final class LineNumberGutter extends JComponent {

    private final JTextComponent target;
    /** キャレット行を強調表示するか (プレースホルダ表示中などは false にできる)。 */
    private final BooleanSupplier emphasizeCaretLine;

    LineNumberGutter(JTextComponent target, BooleanSupplier emphasizeCaretLine) {
        this.target = target;
        this.emphasizeCaretLine = emphasizeCaretLine;
    }

    /** 行数変化・テーマ切替後に再レイアウト・再描画する。 */
    void refresh() {
        revalidate();
        repaint();
    }

    private int lineCount() {
        return target.getDocument().getDefaultRootElement().getElementCount();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(target.getFont());
        int digits = Math.max(3, String.valueOf(Math.max(lineCount(), 1)).length());
        int w = fm.charWidth('0') * digits + 14;
        int h = Math.max(target.getHeight(), target.getPreferredSize().height);
        return new Dimension(w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        Rectangle clip = g2.getClipBounds();
        g2.setColor(EditorColors.gutterBackground());
        g2.fillRect(clip.x, clip.y, clip.width, clip.height);

        Element root = target.getDocument().getDefaultRootElement();
        int lines = root.getElementCount();
        if (lines <= 0) {
            return;
        }
        Font f = target.getFont();
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics(f);
        int w = getWidth();
        int caretLine = emphasizeCaretLine.getAsBoolean()
                ? root.getElementIndex(target.getCaretPosition()) : -1;

        int startLine = 0;
        try {
            int off = target.viewToModel2D(new Point2D.Double(0, clip.y));
            startLine = Math.max(0, root.getElementIndex(off));
        } catch (RuntimeException ignored) {
            startLine = 0;
        }
        for (int line = startLine; line < lines; line++) {
            Element el = root.getElement(line);
            Rectangle2D r;
            try {
                r = target.modelToView2D(el.getStartOffset());
            } catch (BadLocationException ex) {
                break;
            }
            if (r == null) {
                break;
            }
            int top = (int) r.getY();
            if (top > clip.y + clip.height) {
                break;
            }
            String num = String.valueOf(line + 1);
            int sw = fm.stringWidth(num);
            g2.setColor(line == caretLine
                    ? EditorColors.text() : EditorColors.gutterForeground());
            g2.drawString(num, w - sw - 8, top + fm.getAscent());
        }
    }
}
