// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.regex.Pattern;

/**
 * ER 図デザイナーの編集ダイアログ (エンティティの表示名・別名・列、リレーションの
 * カーディナリティ・ラベル)。UI 構築のみ担当し、適用の可否 (別名の重複チェック等) も
 * ここで完結させる。
 */
final class ErSketchDialogs {

    /** PlantUML の別名 (alias) として安全な識別子。 */
    private static final Pattern ALIAS = Pattern.compile("[A-Za-z_$][\\w$]*");

    private ErSketchDialogs() {
    }

    /**
     * エンティティ編集ダイアログを表示し、OK なら {@code target} と関係端点へ反映する。
     *
     * @return 変更を適用したら true (キャンセル・不正入力なら false)
     */
    static boolean editEntity(Component parent, ErSketchModel model,
                              ErSketchModel.Entity target) {
        JTextField nameField = new JTextField(
                target.getDisplayName() == null ? "" : target.getDisplayName(), 18);
        JTextField aliasField = new JTextField(target.getAlias(), 18);

        DefaultTableModel columns = new DefaultTableModel(new Object[]{
                Messages.get("sketch.er.dlg.colPk"),
                Messages.get("sketch.er.dlg.colName"),
                Messages.get("sketch.er.dlg.colType")}, 0) {
            @Override public Class<?> getColumnClass(int col) {
                return col == 0 ? Boolean.class : String.class;
            }
        };
        for (ErSketchModel.Column c : target.getColumns()) {
            columns.addRow(new Object[]{c.isPrimaryKey(), c.getName(), c.getType()});
        }
        JTable table = new JTable(columns);
        table.setPreferredScrollableViewportSize(new Dimension(340, 140));

        JPanel panel = buildEntityPanel(nameField, aliasField, table, columns);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.er.dlg.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        String newAlias = aliasField.getText().trim();
        ErSketchModel.Entity same = model.findEntity(newAlias);
        if (!ALIAS.matcher(newAlias).matches() || (same != null && same != target)) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("sketch.er.dlg.aliasError"),
                    Messages.get("sketch.er.dlg.title"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        model.renameEntity(target, newAlias);
        String name = nameField.getText().trim();
        target.setDisplayName(name.isEmpty() || name.equals(newAlias) ? null : name);
        applyColumns(target, columns);
        return true;
    }

    /** 表示名・別名フィールドと列テーブル・列追加/削除ボタンを組み立てる。 */
    private static JPanel buildEntityPanel(JTextField nameField, JTextField aliasField,
                                           JTable table, DefaultTableModel columns) {
        JPanel top = new JPanel(new GridLayout(2, 2, 6, 4));
        top.add(new JLabel(Messages.get("sketch.er.dlg.name")));
        top.add(nameField);
        top.add(new JLabel(Messages.get("sketch.er.dlg.alias")));
        top.add(aliasField);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createTitledBorder(Messages.get("sketch.er.dlg.colName")));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton add = new JButton(Messages.get("sketch.er.dlg.addColumn"));
        add.addActionListener(e -> columns.addRow(new Object[]{Boolean.FALSE, "column", ""}));
        JButton remove = new JButton(Messages.get("sketch.er.dlg.removeColumn"));
        remove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                if (table.isEditing()) {
                    table.getCellEditor().stopCellEditing();
                }
                columns.removeRow(row);
            }
        });
        buttons.add(add);
        buttons.add(remove);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(top, BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    /** テーブルの行内容を {@code target} の列リストへ反映する (名前が空の行は捨てる)。 */
    private static void applyColumns(ErSketchModel.Entity target, DefaultTableModel columns) {
        target.getColumns().clear();
        for (int row = 0; row < columns.getRowCount(); row++) {
            String name = str(columns.getValueAt(row, 1)).trim();
            if (name.isEmpty()) {
                continue;
            }
            boolean pk = Boolean.TRUE.equals(columns.getValueAt(row, 0));
            String type = str(columns.getValueAt(row, 2)).trim();
            target.getColumns().add(new ErSketchModel.Column(pk, name, type));
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /**
     * リレーション編集ダイアログを表示し、OK なら {@code rel} のカーディナリティ・
     * ラベルへ反映する。
     *
     * @return 変更を適用したら true (キャンセルなら false)
     */
    static boolean editRelation(Component parent, ErSketchModel.Relation rel) {
        JComboBox<ErSketchModel.Cardinality> leftCombo =
                new JComboBox<>(ErSketchModel.Cardinality.values());
        leftCombo.setSelectedItem(rel.getLeftCard());
        JComboBox<ErSketchModel.Cardinality> rightCombo =
                new JComboBox<>(ErSketchModel.Cardinality.values());
        rightCombo.setSelectedItem(rel.getRightCard());
        JTextField labelField = new JTextField(
                rel.getLabel() == null ? "" : rel.getLabel(), 18);

        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.er.dlg.relLeft")));
        panel.add(leftCombo);
        panel.add(new JLabel(Messages.get("sketch.er.dlg.relRight")));
        panel.add(rightCombo);
        panel.add(new JLabel(Messages.get("sketch.er.dlg.relLabel")));
        panel.add(labelField);

        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.er.dlg.relTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        rel.setLeftCard((ErSketchModel.Cardinality) leftCombo.getSelectedItem());
        rel.setRightCard((ErSketchModel.Cardinality) rightCombo.getSelectedItem());
        String label = labelField.getText().trim();
        rel.setLabel(label.isEmpty() ? null : label);
        return true;
    }
}
