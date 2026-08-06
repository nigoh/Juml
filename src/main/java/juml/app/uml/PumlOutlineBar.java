// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * 記号一覧から目的の宣言へ飛ぶインラインバー (VS Code の {@code Ctrl+Shift+O} 相当)。
 *
 * <p>行番号ジャンプ ({@link GotoLineBar}) は「行番号を知っている」ときにしか使えない。
 * 長い図で「あの participant はどこだったか」を探すには、宣言の一覧を名前で絞り込めた
 * ほうが速い。検索バー・行ジャンプバーと同じくエディタ下部に埋め込むインライン
 * ウィジェットで、モーダルにしないので図を見ながら絞り込める。</p>
 */
final class PumlOutlineBar extends JPanel {

    /** 一覧の高さ (行数)。画面を占有しすぎない範囲で一覧性を確保する。 */
    private static final int VISIBLE_ROWS = 6;

    private final IntConsumer onJump;
    private final Runnable onLayoutChange;
    private final JComponent target;
    private final JTextField filter;
    private final JLabel info;
    private final DefaultListModel<PumlSymbols.Symbol> model = new DefaultListModel<>();
    private final JList<PumlSymbols.Symbol> list = new JList<>(model);

    /** 現在の絞り込み対象 (バーを開いた時点の記号一覧)。 */
    private List<PumlSymbols.Symbol> all = new ArrayList<>();

    /**
     * @param onJump         選んだ記号の行 (1 始まり) を受け取る
     * @param onLayoutChange 表示/非表示でレイアウトを組み直す通知
     * @param target         閉じた後にフォーカスを戻す先 (通常はエディタ本体)
     */
    PumlOutlineBar(IntConsumer onJump, Runnable onLayoutChange, JComponent target) {
        super(new BorderLayout(4, 2));
        this.onJump = onJump;
        this.onLayoutChange = onLayoutChange;
        this.target = target;
        Color sep = javax.swing.UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                sep != null ? sep : new Color(0xCCCCCC)));

        JLabel label = new JLabel(Messages.get("puml.outline.label") + ":");
        filter = new JTextField(16);
        label.setLabelFor(filter);
        filter.getAccessibleContext().setAccessibleName(Messages.get("puml.outline.label"));
        info = new JLabel(" ");
        Color infoFg = javax.swing.UIManager.getColor("Label.disabledForeground");
        info.setForeground(infoFg != null ? infoFg : new Color(0x777777));

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        head.add(label);
        head.add(filter);
        head.add(info);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(VISIBLE_ROWS);
        list.setCellRenderer((jlist, value, index, selected, focused) -> {
            JLabel row = new JLabel(value.display() + "    " + value.kind()
                    + "  :" + value.line());
            row.setOpaque(true);
            row.setBackground(selected ? jlist.getSelectionBackground()
                    : jlist.getBackground());
            row.setForeground(selected ? jlist.getSelectionForeground()
                    : jlist.getForeground());
            row.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            return row;
        });
        // 一覧をクリックしたらそのまま飛ぶ (キーボードが主導線だがマウスも拒まない)。
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                commit();
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(320, 110));
        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                refilter();
            }
            @Override public void removeUpdate(DocumentEvent e) {
                refilter();
            }
            @Override public void changedUpdate(DocumentEvent e) {
                refilter();
            }
        });
        // 絞り込み欄にいるまま上下で選べるようにする (欄とリストを行き来させない)。
        filter.registerKeyboardAction(e -> move(1),
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), WHEN_FOCUSED);
        filter.registerKeyboardAction(e -> move(-1),
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), WHEN_FOCUSED);
        filter.registerKeyboardAction(e -> commit(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);
        filter.registerKeyboardAction(e -> close(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_FOCUSED);
        list.registerKeyboardAction(e -> commit(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_FOCUSED);
        list.registerKeyboardAction(e -> close(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_FOCUSED);
        setVisible(false);
    }

    /** 記号一覧を読み込んでバーを開く。記号が 1 つも無ければ開かない。 */
    void activate(String text) {
        all = PumlSymbols.declarations(text);
        if (all.isEmpty()) {
            close();
            return;
        }
        filter.setText("");
        refilter();
        setVisible(true);
        onLayoutChange.run();
        filter.requestFocusInWindow();
        filter.selectAll();
    }

    /** バーを閉じてエディタへフォーカスを戻す。 */
    void close() {
        if (!isVisible()) {
            return;
        }
        setVisible(false);
        onLayoutChange.run();
        if (target != null) {
            target.requestFocusInWindow();
        }
    }

    /** 絞り込み文字列で一覧を作り直す (部分一致・大小無視)。 */
    private void refilter() {
        String q = filter.getText().toLowerCase(Locale.ROOT).strip();
        model.clear();
        for (PumlSymbols.Symbol s : all) {
            if (q.isEmpty() || s.display().toLowerCase(Locale.ROOT).contains(q)
                    || s.kind().toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(s);
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        info.setText(java.text.MessageFormat.format(
                Messages.get("puml.outline.count"), model.size(), all.size()));
    }

    private void move(int delta) {
        if (model.isEmpty()) {
            return;
        }
        int idx = Math.max(0, Math.min(model.size() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(idx);
        list.ensureIndexIsVisible(idx);
    }

    /** 選択中の記号の行へ飛んでバーを閉じる。 */
    private void commit() {
        PumlSymbols.Symbol sel = list.getSelectedValue();
        if (sel != null) {
            onJump.accept(sel.line());
        }
        close();
    }

    /** テスト用: 現在絞り込まれている件数。 */
    int visibleCountForTest() {
        return model.size();
    }

    /** テスト用: 絞り込み文字列を設定する。 */
    void setFilterForTest(String q) {
        filter.setText(q);
    }

    /** テスト用: 先頭候補の行番号 (空なら 0)。 */
    int firstLineForTest() {
        return model.isEmpty() ? 0 : model.get(0).line();
    }
}
