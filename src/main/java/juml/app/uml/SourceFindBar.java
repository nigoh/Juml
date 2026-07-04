// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

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
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link JavaSourcePanel} のソース内インクリメンタル検索バー (VS Code の {@code Ctrl+F} 相当)。
 *
 * <p>対象の {@link JTextComponent} に対しヒット箇所を全件ハイライトし、
 * {@code Enter}/{@code Shift+Enter} で前後移動、{@code Esc} で閉じる。
 * 検索状態 (ハイライト・件数) はこのクラスが完結して管理する。</p>
 */
final class SourceFindBar extends JPanel {

    private static Color searchHitColor() {
        return EditorColors.isDark() ? new Color(0x61, 0x4D, 0x0A) : new Color(0xFF, 0xE9, 0xA8);
    }

    private final JTextComponent target;
    private final Runnable onLayoutChange;
    private final JTextField field;
    private final JLabel info;
    private final List<int[]> hits = new ArrayList<>(); // {start, end}
    private final List<Object> tags = new ArrayList<>();
    private int index = -1;
    /**
     * インクリメンタル検索の起点となるキャレット位置 (バー起動時に固定)。
     * showCurrent() がヒット末尾へキャレットを動かすため、run() で live キャレットを
     * 読むと入力の 1 文字ごとに「現在ヒット」が次の出現へ進んでしまう。起点を固定して防ぐ。
     */
    private int searchAnchor = -1;

    SourceFindBar(JTextComponent target, Runnable onLayoutChange) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        this.target = target;
        this.onLayoutChange = onLayoutChange;
        Color sep = javax.swing.UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                sep != null ? sep : new Color(0xCCCCCC)));

        JLabel lbl = new JLabel(Messages.get("source.find") + ":");
        field = new JTextField(20);
        field.putClientProperty("JTextField.placeholderText",
                Messages.get("source.find.placeholder"));
        field.putClientProperty("JTextField.showClearButton", true);
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

    /** 検索バーを表示してフォーカスを移し、選択文字列があれば初期クエリにする。 */
    void activate() {
        setVisible(true);
        layoutChanged();
        // 検索の起点を今のキャレット位置に固定する (以降のインクリメンタル入力で
        // showCurrent がキャレットを動かしても、初期ヒット選択はここを基準に保つ)。
        searchAnchor = target.getCaretPosition();
        String sel = target.getSelectedText();
        if (sel != null && !sel.isEmpty() && !sel.contains("\n")) {
            field.setText(sel);
        }
        field.requestFocusInWindow();
        field.selectAll();
        run(field.getText());
    }

    /** 検索バーを閉じてハイライトを消す。 */
    void close() {
        setVisible(false);
        clearHighlights();
        layoutChanged();
        target.requestFocusInWindow();
    }

    /** ファイル切替時などに検索状態を完全リセットする。 */
    void reset() {
        field.setText("");
        clearHighlights();
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

    private void clearHighlights() {
        Highlighter h = target.getHighlighter();
        if (h != null) {
            for (Object tag : tags) {
                h.removeHighlight(tag);
            }
        }
        tags.clear();
        hits.clear();
        index = -1;
        info.setText(" ");
    }

    private void run(String query) {
        clearHighlights();
        if (query == null || query.isEmpty()) {
            return;
        }
        String text;
        try {
            text = target.getDocument().getText(0, target.getDocument().getLength());
        } catch (BadLocationException ex) {
            return;
        }
        // 大文字小文字を無視した走査は regionMatches で行い、オフセットは常に元テキスト
        // 基準にする。toLowerCase は長さが変わることがあり (例: U+0130 "İ" → 2 文字)、
        // 小文字化したコピーのオフセットを元ドキュメントに適用すると、以降のヒットの
        // 強調・選択が全て 1 文字ずつずれる。
        Highlighter h = target.getHighlighter();
        Highlighter.HighlightPainter painter =
                new DefaultHighlighter.DefaultHighlightPainter(searchHitColor());
        int qlen = query.length();
        int from = 0;
        while (from + qlen <= text.length()) {
            int p = indexOfIgnoreCase(text, query, from);
            if (p < 0) {
                break;
            }
            int end = p + qlen;
            hits.add(new int[]{p, end});
            try {
                tags.add(h.addHighlight(p, end, painter));
            } catch (BadLocationException ignored) {
                // 範囲外は無視。
            }
            from = end;
            if (hits.size() > 5000) {
                break; // 過剰ヒット時の保護
            }
        }
        if (hits.isEmpty()) {
            info.setText(Messages.get("source.find.none"));
            return;
        }
        int caret = searchAnchor >= 0 ? searchAnchor : target.getCaretPosition();
        index = 0;
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i)[0] >= caret) {
                index = i;
                break;
            }
        }
        showCurrent();
    }

    /** {@code from} 以降で大文字小文字を無視して {@code needle} を探す (元テキスト基準)。 */
    private static int indexOfIgnoreCase(String text, String needle, int from) {
        int max = text.length() - needle.length();
        for (int i = Math.max(0, from); i <= max; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private void move(int delta) {
        if (hits.isEmpty()) {
            return;
        }
        index = (index + delta + hits.size()) % hits.size();
        showCurrent();
    }

    private void showCurrent() {
        if (index < 0 || index >= hits.size()) {
            return;
        }
        int[] hit = hits.get(index);
        try {
            Rectangle2D r = target.modelToView2D(hit[0]);
            if (r != null) {
                target.scrollRectToVisible(r.getBounds());
            }
            target.getCaret().setSelectionVisible(true);
            target.setCaretPosition(hit[0]);
            target.moveCaretPosition(hit[1]);
        } catch (BadLocationException ignored) {
            // 無視。
        }
        info.setText((index + 1) + " / " + hits.size());
    }
}
