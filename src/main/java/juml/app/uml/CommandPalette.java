// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

/**
 * VS Code 風コマンドパレット (Ctrl+Shift+P)。登録された全コマンドを名前で絞り込み、
 * メニュー階層を覚えていなくても機能へ到達できるようにする。
 *
 * <p>入力でフィルタ (部分一致, 大文字小文字無視)、↑↓ で選択移動、Enter で実行、
 * Esc で閉じる。実行は各コマンドの {@link Command#action} を呼ぶだけ。</p>
 */
final class CommandPalette {

    /** パレットに並ぶ 1 コマンド (表示名 + ショートカット + 実行アクション)。 */
    static final class Command {
        final String label;
        /** 表示用ショートカット文字列 (例: "Ctrl+O")。無ければ null。 */
        final String shortcut;
        final Runnable action;

        Command(String label, Runnable action) {
            this(label, null, action);
        }

        Command(String label, String shortcut, Runnable action) {
            this.label = label;
            this.shortcut = shortcut;
            this.action = action;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private CommandPalette() {
    }

    /** モーダルでパレットを開く。owner ウィンドウ中央上寄りに表示する。 */
    static void show(Window owner, List<Command> commands) {
        // VS Code 同様、モーダルにせずフォーカスを失ったら閉じるオーバーレイ的挙動にする。
        JDialog dlg = new JDialog(owner, juml.util.Messages.get("palette.title"),
                Dialog.ModalityType.MODELESS);
        JTextField filter = new JTextField();
        filter.putClientProperty("JTextField.placeholderText",
                juml.util.Messages.get("palette.placeholder"));
        filter.setToolTipText(juml.util.Messages.get("palette.tooltip"));
        filter.getAccessibleContext().setAccessibleName("Filter commands");
        DefaultListModel<Command> model = new DefaultListModel<>();
        JList<Command> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Command list");
        list.setCellRenderer(new ShortcutRenderer());
        repopulate(model, list, commands, "");

        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                repopulate(model, list, commands, filter.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                repopulate(model, list, commands, filter.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                repopulate(model, list, commands, filter.getText());
            }
        });

        Runnable run = () -> {
            Command c = list.getSelectedValue();
            if (c != null) {
                dlg.dispose();
                SwingUtilities.invokeLater(c.action);
            }
        };

        filter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_ENTER) {
                    run.run();
                } else if (code == KeyEvent.VK_ESCAPE) {
                    dlg.dispose();
                } else if (code == KeyEvent.VK_DOWN) {
                    moveSelection(list, 1);
                } else if (code == KeyEvent.VK_UP) {
                    moveSelection(list, -1);
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    run.run();
                }
            }
        });
        // リストにフォーカスがある状態 (マウス選択後) でも Enter/Esc を効かせる。
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    run.run();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dlg.dispose();
                }
            }
        });
        // フォーカスを失ったら閉じる (VS Code のオーバーレイ的挙動)。
        dlg.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                dlg.dispose();
            }
        });

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(filter, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        JLabel hint = new JLabel(juml.util.Messages.get("palette.hint"));
        hint.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        java.awt.Color hintColor = javax.swing.UIManager.getColor("Label.disabledForeground");
        hint.setForeground(hintColor != null ? hintColor : new java.awt.Color(0x777777));
        root.add(hint, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.setSize(new Dimension(460, 420));
        positionTopCenter(dlg, owner);
        SwingUtilities.invokeLater(filter::requestFocusInWindow);
        dlg.setVisible(true);
    }

    /** VS Code 同様、オーナーウィンドウの上部中央 (上端から少し下) に配置する。 */
    private static void positionTopCenter(JDialog dlg, Window owner) {
        if (owner == null) {
            dlg.setLocationRelativeTo(null);
            return;
        }
        int x = owner.getX() + (owner.getWidth() - dlg.getWidth()) / 2;
        int y = owner.getY() + Math.max(40, owner.getHeight() / 12);
        dlg.setLocation(x, y);
    }

    private static void repopulate(DefaultListModel<Command> model, JList<Command> list,
                                   List<Command> commands, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        model.clear();
        for (Command c : commands) {
            if (q.isEmpty() || matches(c.label.toLowerCase(Locale.ROOT), q)) {
                model.addElement(c);
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    /**
     * VS Code 風のあいまい (subsequence) マッチ。クエリの各文字が対象文字列に
     * 順序を保って現れれば一致とみなす ("oppr" → "Open Project")。部分一致も内包する。
     */
    private static boolean matches(String haystack, String query) {
        int i = 0;
        for (int j = 0; j < haystack.length() && i < query.length(); j++) {
            if (haystack.charAt(j) == query.charAt(i)) {
                i++;
            }
        }
        return i == query.length();
    }

    private static void moveSelection(JList<Command> list, int delta) {
        int n = list.getModel().getSize();
        if (n == 0) {
            return;
        }
        int i = list.getSelectedIndex();
        // 選択が無い (クリアされた) 状態からは、下移動で先頭・上移動で末尾を選ぶ。
        // 「無選択を 0 とみなして delta を足す」と下移動で先頭を飛ばして 2 番目になる。
        int next = (i < 0) ? (delta > 0 ? 0 : n - 1) : Math.floorMod(i + delta, n);
        list.setSelectedIndex(next);
        list.ensureIndexIsVisible(list.getSelectedIndex());
    }

    /**
     * VS Code 風にコマンド名を左、ショートカットを右端 (淡色) に表示するセルレンダラ。
     */
    private static final class ShortcutRenderer implements ListCellRenderer<Command> {
        private final JPanel panel = new JPanel(new BorderLayout(12, 0));
        private final JLabel name = new JLabel();
        private final JLabel shortcut = new JLabel();

        ShortcutRenderer() {
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            panel.add(name, BorderLayout.CENTER);
            panel.add(shortcut, BorderLayout.EAST);
            shortcut.setOpaque(false);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Command> list,
                Command value, int index, boolean isSelected, boolean cellHasFocus) {
            name.setText(value != null ? value.label : "");
            shortcut.setText(value != null && value.shortcut != null ? value.shortcut : "");
            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            panel.setBackground(bg);
            name.setForeground(fg);
            Color subtle = UIManager.getColor("Label.disabledForeground");
            shortcut.setForeground(isSelected || subtle == null ? fg : subtle);
            return panel;
        }
    }
}
