// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PlantUmlSvgRenderer.SvgTextItem;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 表示中の UML 図 ({@link SvgPreviewPanel}) に対するインクリメンタル検索バー
 * (ブラウザの {@code Ctrl+F} 相当)。
 *
 * <p>SVG 内のテキスト要素 ({@link SvgTextItem}) を対象に部分一致で検索し、
 * ヒット箇所を図上に矩形ハイライトする。{@code Enter}/{@code Shift+Enter} で
 * 前後のヒットへズーム移動し、{@code Esc} で閉じる。検索状態 (ヒット矩形・件数) は
 * このクラスが完結して管理し、表示は {@link SvgPreviewPanel#setSearchHighlights} に委譲する。</p>
 */
final class DiagramFindBar extends JPanel {

    private final SvgPreviewPanel target;
    private final Runnable onLayoutChange;
    private final JTextField field;
    private final JLabel info;
    /** ヒット矩形 (SVG 座標) と元テキストの組。 */
    private final List<Rectangle2D> hits = new ArrayList<>();
    private int index = -1;

    DiagramFindBar(SvgPreviewPanel target, Runnable onLayoutChange) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        this.target = target;
        this.onLayoutChange = onLayoutChange;
        Color sep = javax.swing.UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                sep != null ? sep : new Color(0xCCCCCC)));

        JLabel lbl = new JLabel(Messages.get("diagram.find") + ":");
        field = new JTextField(20);
        field.putClientProperty("JTextField.placeholderText",
                Messages.get("diagram.find.placeholder"));
        info = new JLabel(" ");
        Color infoFg = javax.swing.UIManager.getColor("Label.disabledForeground");
        info.setForeground(infoFg != null ? infoFg : new Color(0x777777));
        JButton prev = new JButton(MaterialIcons.of(MaterialIcons.Glyph.CHEVRON_UP, 16));
        prev.setToolTipText(Messages.get("source.find.prev"));
        JButton next = new JButton(MaterialIcons.of(MaterialIcons.Glyph.CHEVRON_DOWN, 16));
        next.setToolTipText(Messages.get("source.find.next"));
        JButton close = new JButton(MaterialIcons.of(MaterialIcons.Glyph.CLOSE, 16));
        close.setToolTipText(Messages.get("source.find.close"));
        prev.addActionListener(e -> move(-1));
        next.addActionListener(e -> move(1));
        close.addActionListener(e -> close());
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { run(field.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { run(field.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { run(field.getText()); }
        });
        field.registerKeyboardAction(e -> move(1),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
        field.registerKeyboardAction(e -> move(-1),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                JComponent.WHEN_FOCUSED);
        field.registerKeyboardAction(e -> close(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

        add(lbl);
        add(field);
        add(prev);
        add(next);
        add(info);
        add(close);
        setVisible(false);
    }

    /** 検索バーを表示してフォーカスを移す。 */
    void activate() {
        setVisible(true);
        layoutChanged();
        field.requestFocusInWindow();
        field.selectAll();
        run(field.getText());
    }

    /** 検索バーを閉じてハイライトを消す。 */
    void close() {
        setVisible(false);
        clearHits();
        layoutChanged();
        target.requestFocusInWindow();
    }

    /** 図の差し替え時などに検索状態を完全リセットする。 */
    void reset() {
        field.setText("");
        clearHits();
        if (isVisible()) {
            setVisible(false);
            layoutChanged();
        }
    }

    private void layoutChanged() {
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }

    private void clearHits() {
        hits.clear();
        index = -1;
        info.setText(" ");
        target.clearSearchHighlights();
    }

    private void run(String query) {
        hits.clear();
        index = -1;
        if (query == null || query.isEmpty()) {
            info.setText(" ");
            target.clearSearchHighlights();
            return;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        for (SvgTextItem item : target.getTextItems()) {
            String text = item.getText();
            if (text == null || !text.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            hits.add(rectFor(item));
            if (hits.size() > 5000) {
                break; // 過剰ヒット時の保護
            }
        }
        if (hits.isEmpty()) {
            info.setText(Messages.get("source.find.none"));
            target.clearSearchHighlights();
            return;
        }
        index = 0;
        target.setSearchHighlights(hits, index);
        showCurrent();
    }

    /** テキスト要素のアンカー (ベースライン始点) からハイライト矩形を組み立てる。 */
    private static Rectangle2D rectFor(SvgTextItem item) {
        double h = item.getHeight() > 0 ? item.getHeight() : 16;
        double w = item.getWidth() > 0 ? item.getWidth()
                : Math.max(8, item.getText().length() * 7.0);
        // SVG の text の y はベースライン。上端へ少し持ち上げて文字全体を囲う。
        double top = item.getY() - h * 0.8;
        return new Rectangle2D.Double(item.getX(), top, w, h);
    }

    private void move(int delta) {
        if (hits.isEmpty()) {
            return;
        }
        index = (index + delta + hits.size()) % hits.size();
        target.setSearchHighlights(hits, index);
        showCurrent();
    }

    private void showCurrent() {
        if (index < 0 || index >= hits.size()) {
            return;
        }
        target.scrollSvgRectToVisible(hits.get(index));
        info.setText((index + 1) + " / " + hits.size());
    }
}
