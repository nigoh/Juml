// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * ユーザー定義スニペットの管理ダイアログ (一覧 + 編集 + 削除)。
 *
 * <p>登録の主な入口はエディタでの「選択範囲をスニペットとして登録」で、そちらは
 * トリガ名を尋ねるだけで済む。このダイアログは後から本文を直したい・要らなくなった
 * ものを消したいときのための場所。</p>
 */
final class PumlUserSnippetDialog extends JDialog {

    private final PumlUserSnippets store;
    private final DefaultListModel<PumlUserSnippets.Entry> model = new DefaultListModel<>();
    private final JList<PumlUserSnippets.Entry> list = new JList<>(model);
    private final JTextField trigger = new JTextField(16);
    private final JTextField label = new JTextField(24);
    private final JTextArea body = new JTextArea(10, 44);
    /** 編集中の項目の添字 (-1 は新規)。 */
    private int editing = -1;

    PumlUserSnippetDialog(JComponent owner, PumlUserSnippets store) {
        super(SwingUtilities.getWindowAncestor(owner),
                Messages.get("puml.userSnip.title"), ModalityType.APPLICATION_MODAL);
        this.store = store;
        setLayout(new BorderLayout(8, 8));

        for (PumlUserSnippets.Entry e : store.load()) {
            model.addElement(e);
        }
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jlist, value, index, selected, focused) -> {
            JLabel row = new JLabel(value.trigger() + "  —  " + value.label());
            row.setOpaque(true);
            row.setBackground(selected ? jlist.getSelectionBackground()
                    : jlist.getBackground());
            row.setForeground(selected ? jlist.getSelectionForeground()
                    : jlist.getForeground());
            row.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return row;
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelection();
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(240, 320));

        body.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        body.setLineWrap(false);

        JPanel fields = new JPanel(new GridLayout(2, 1, 4, 4));
        fields.add(labelled("puml.userSnip.trigger", trigger));
        fields.add(labelled("puml.userSnip.label", label));

        JPanel editor = new JPanel(new BorderLayout(4, 4));
        editor.add(fields, BorderLayout.NORTH);
        editor.add(new JScrollPane(body), BorderLayout.CENTER);
        JLabel hint = new JLabel(Messages.get("puml.userSnip.hint"));
        hint.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        editor.add(hint, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        center.add(listScroll, BorderLayout.WEST);
        center.add(editor, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JButton newOne = new JButton(Messages.get("puml.userSnip.new"));
        JButton apply = new JButton(Messages.get("puml.userSnip.apply"));
        JButton remove = new JButton(Messages.get("puml.userSnip.remove"));
        JButton close = new JButton(Messages.get("puml.userSnip.close"));
        newOne.addActionListener(e -> startNew());
        apply.addActionListener(e -> applyEdit());
        remove.addActionListener(e -> removeSelected());
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(newOne);
        buttons.add(apply);
        buttons.add(remove);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        pack();
        setLocationRelativeTo(owner);
    }

    private static JPanel labelled(String key, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        JLabel l = new JLabel(Messages.get(key) + ":");
        l.setLabelFor(field);
        field.getAccessibleContext().setAccessibleName(Messages.get(key));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    /** 選択中の項目を編集欄へ写す。 */
    private void loadSelection() {
        PumlUserSnippets.Entry sel = list.getSelectedValue();
        editing = list.getSelectedIndex();
        trigger.setText(sel == null ? "" : sel.trigger());
        label.setText(sel == null ? "" : sel.label());
        body.setText(sel == null ? "" : sel.body());
        body.setCaretPosition(0);
    }

    private void startNew() {
        list.clearSelection();
        editing = -1;
        trigger.setText("");
        label.setText("");
        body.setText("");
        trigger.requestFocusInWindow();
    }

    /** 編集内容を一覧へ反映して保存する。 */
    private void applyEdit() {
        String t = PumlUserSnippets.normalizeTrigger(trigger.getText());
        if (t.isEmpty() || body.getText().isEmpty()) {
            // トリガか本文が無いものは補完から引けない。黙って保存すると
            // 「登録したのに出てこない」になるので、理由を出して止める。
            JOptionPane.showMessageDialog(this, Messages.get("puml.userSnip.needBoth"),
                    Messages.get("puml.userSnip.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        PumlUserSnippets.Entry entry =
                new PumlUserSnippets.Entry(t, label.getText().strip(), body.getText());
        List<PumlUserSnippets.Entry> all = currentEntries();
        if (editing >= 0 && editing < all.size()) {
            all.set(editing, entry);
        } else {
            all.removeIf(e -> e.trigger().equalsIgnoreCase(t));
            all.add(entry);
        }
        persist(all);
    }

    private void removeSelected() {
        int idx = list.getSelectedIndex();
        if (idx < 0) {
            return;
        }
        List<PumlUserSnippets.Entry> all = currentEntries();
        all.remove(idx);
        persist(all);
        startNew();
    }

    private List<PumlUserSnippets.Entry> currentEntries() {
        List<PumlUserSnippets.Entry> all = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            all.add(model.get(i));
        }
        return all;
    }

    /** 保存して一覧を組み直す。保存できなければ場所つきで知らせる。 */
    private void persist(List<PumlUserSnippets.Entry> all) {
        if (!store.save(all)) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("puml.userSnip.saveFailed") + "\n" + store.file(),
                    Messages.get("puml.userSnip.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        model.clear();
        for (PumlUserSnippets.Entry e : store.load()) {
            model.addElement(e);
        }
    }
}
