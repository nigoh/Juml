// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.ProjectRecord;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「Open Project」ボタン押下時に表示するダイアログ。
 *
 * <p>保存済みプロジェクト一覧から選択するか、
 * ファイルチューザで新規パスを指定するかを選べる。</p>
 */
final class OpenProjectDialog extends JDialog {

    /** ダイアログが返す結果種別。 */
    enum Action { SELECTED, BROWSE, CANCEL }

    private Action action = Action.CANCEL;
    private File chosenRoot;

    private OpenProjectDialog(Frame owner, List<ProjectRecord> records,
                              Consumer<ProjectRecord> onDelete) {
        super(owner, Messages.get("dlg.openProject.title"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));

        // ── ヘッダ ──────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UiTheme.ACCENT); // アクセント色のヘッダ (VS Code 風)
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel(juml.util.Messages.get("dlg.openProject.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.NORTH);
        JLabel sub = new JLabel(juml.util.Messages.get("dlg.openProject.subtitle"));
        sub.setFont(sub.getFont().deriveFont(12f));
        sub.setForeground(new Color(0xE6F0F7));
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // ── 中央: プロジェクト一覧 ──────────────────────────
        JPanel center = new JPanel(new GridBagLayout());
        center.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 6, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;

        if (records.isEmpty()) {
            center.add(new JLabel("最近開いたプロジェクトはありません"), gbc);
        } else {
            center.add(new JLabel("最近開いたプロジェクト:"), gbc);

            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weighty = 1;

            DefaultListModel<ProjectRecord> model = new DefaultListModel<>();
            for (ProjectRecord rec : records) {
                model.addElement(rec);
            }
            JList<ProjectRecord> list = new JList<>(model);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new ProjectListRenderer());
            list.setVisibleRowCount(8);

            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(520, 220));
            center.add(scroll, gbc);

            // ── ボタン行 ──────────────────────────────────
            gbc.gridy = 2;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 0;
            gbc.insets = new Insets(10, 0, 0, 0);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

            JButton openSelected = new JButton("開く");
            openSelected.setEnabled(false);
            openSelected.addActionListener(e -> {
                ProjectRecord sel = list.getSelectedValue();
                if (sel != null && sel.root().isDirectory()) {
                    action = Action.SELECTED;
                    chosenRoot = sel.root();
                    dispose();
                }
            });

            // 一覧から削除（ディスク上のプロジェクト本体は削除しない）
            JButton remove = new JButton("一覧から削除");
            remove.setEnabled(false);
            remove.addActionListener(e -> removeSelected(list, model, onDelete));

            JButton browse = new JButton("新しいフォルダを参照...");
            browse.addActionListener(e -> {
                action = Action.BROWSE;
                dispose();
            });

            JButton cancel = new JButton("キャンセル");
            cancel.addActionListener(e -> dispose());

            list.addListSelectionListener(ev -> {
                ProjectRecord sel = list.getSelectedValue();
                openSelected.setEnabled(sel != null && sel.root().isDirectory());
                remove.setEnabled(sel != null);
            });
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        openSelected.doClick();
                    }
                }
            });
            // Delete / BackSpace キーで選択中の項目を一覧から削除
            list.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    int code = e.getKeyCode();
                    if ((code == java.awt.event.KeyEvent.VK_DELETE
                            || code == java.awt.event.KeyEvent.VK_BACK_SPACE)
                            && list.getSelectedValue() != null) {
                        removeSelected(list, model, onDelete);
                    }
                }
            });

            buttons.add(remove);
            buttons.add(openSelected);
            buttons.add(browse);
            buttons.add(cancel);
            // 先頭を初期選択して Enter で開けるようにし、Esc/Enter を統一設定する。
            if (list.getModel().getSize() > 0) {
                list.setSelectedIndex(0);
            }
            DialogUtils.installEscapeAndDefault(this, openSelected);
            center.add(buttons, gbc);
        }

        if (records.isEmpty()) {
            // 一覧なし → Browse / Cancel のみ
            gbc.gridy = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(10, 0, 0, 0);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

            JButton browse = new JButton("フォルダを参照...");
            browse.addActionListener(e -> {
                action = Action.BROWSE;
                dispose();
            });
            JButton cancel = new JButton("キャンセル");
            cancel.addActionListener(e -> dispose());
            buttons.add(browse);
            buttons.add(cancel);
            DialogUtils.installEscapeAndDefault(this, browse);
            center.add(buttons, gbc);
        }

        add(center, BorderLayout.CENTER);

        pack();
        setMinimumSize(new Dimension(540, 320));
        setLocationRelativeTo(owner);
    }

    /**
     * 選択中のプロジェクトを最近一覧から削除する。
     * 確認のうえ、リストモデルと永続化リポジトリ ({@code onDelete}) の両方から取り除く。
     * ディスク上のプロジェクト本体には触れない。
     */
    private void removeSelected(JList<ProjectRecord> list,
                                DefaultListModel<ProjectRecord> model,
                                Consumer<ProjectRecord> onDelete) {
        ProjectRecord sel = list.getSelectedValue();
        if (sel == null) return;
        int answer = JOptionPane.showConfirmDialog(
                this,
                "<html>「<b>" + ProjectListRenderer.escapeHtml(sel.getName())
                        + "</b>」を最近開いたプロジェクト一覧から削除しますか?<br>"
                        + "<font color='#888888'>※ ディスク上のフォルダやファイルは削除されません。</font></html>",
                "一覧から削除",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;

        int index = list.getSelectedIndex();
        if (onDelete != null) {
            onDelete.accept(sel);
        }
        model.removeElement(sel);
        // 削除後は近い位置の項目を選び直す（残っていれば）
        if (!model.isEmpty()) {
            list.setSelectedIndex(Math.min(index, model.getSize() - 1));
        } else {
            list.clearSelection();
        }
    }

    /**
     * ダイアログを表示し、ユーザーが選んだプロジェクトルートを返す。
     *
     * <ul>
     *   <li>保存済みプロジェクトを選んだ → そのパスを返す</li>
     *   <li>「新しいフォルダを参照」を選んだ → {@link JFileChooser} を開き選択パスを返す</li>
     *   <li>キャンセル → {@code null}</li>
     * </ul>
     *
     * @param onDelete 一覧から削除する際に呼ばれるコールバック (永続化層からの削除を委譲)。
     *                 {@code null} 可。
     */
    static File show(Frame owner, List<ProjectRecord> records, Consumer<ProjectRecord> onDelete) {
        OpenProjectDialog d = new OpenProjectDialog(owner, records, onDelete);
        d.setVisible(true);

        if (d.action == Action.SELECTED) {
            return d.chosenRoot;
        }
        if (d.action == Action.BROWSE) {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle(Messages.get("dlg.openAndroidGradle"));
            int r = fc.showOpenDialog(owner);
            return r == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
        }
        return null;
    }

    // ---- セルレンダラ ----

    private static final class ProjectListRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ProjectRecord) {
                ProjectRecord rec = (ProjectRecord) value;
                boolean exists = rec.root().isDirectory();
                setText("<html><b>" + escapeHtml(rec.getName()) + "</b>"
                        + "&nbsp;&nbsp;<font color='" + (isSelected ? "#cccccc" : "#888888") + "'>"
                        + escapeHtml(rec.getPath()) + "</font></html>");
                if (!exists) {
                    setForeground(isSelected ? Color.LIGHT_GRAY : Color.GRAY);
                }
                setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            }
            return this;
        }

        private static String escapeHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
