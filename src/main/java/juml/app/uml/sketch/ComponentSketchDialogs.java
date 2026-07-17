// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.regex.Pattern;

/**
 * コンポーネント図デザイナーの編集ダイアログ (要素の id/種別/表示名・関係の種別/ラベル)。
 * UI 構築のみ担当し、適用の可否 (id の重複チェック等) もここで完結させる。
 */
final class ComponentSketchDialogs {

    /** PlantUML の id として安全な識別子。 */
    private static final Pattern ID = Pattern.compile("[A-Za-z_$][\\w$]*");

    private ComponentSketchDialogs() {
    }

    /**
     * 要素編集ダイアログを表示し、OK なら {@code target} と関係端点へ反映する。
     *
     * @return 変更を適用したら true (キャンセル・不正入力なら false)
     */
    static boolean editNode(Component parent, ComponentSketchModel model, ComponentNode target) {
        JTextField idField = new JTextField(target.getId(), 18);
        JComboBox<ComponentNode.Kind> kindCombo =
                new JComboBox<>(ComponentNode.Kind.values());
        kindCombo.setSelectedItem(target.getKind());
        JTextField labelField = new JTextField(
                target.getLabel() == null ? "" : target.getLabel(), 18);

        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.comp.dlg.id")));
        panel.add(idField);
        panel.add(new JLabel(Messages.get("sketch.comp.dlg.kind")));
        panel.add(kindCombo);
        panel.add(new JLabel(Messages.get("sketch.comp.dlg.label")));
        panel.add(labelField);

        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.comp.dlg.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String newId = idField.getText().trim();
        ComponentNode same = model.findNode(newId);
        if (!ID.matcher(newId).matches() || (same != null && same != target)) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("sketch.comp.dlg.idError"),
                    Messages.get("sketch.comp.dlg.title"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        model.renameNode(target, newId);
        target.setKind((ComponentNode.Kind) kindCombo.getSelectedItem());
        String label = labelField.getText().trim();
        target.setLabel(label.isEmpty() || label.equals(newId) ? null : label);
        return true;
    }

    /**
     * 関係編集ダイアログを表示し、OK なら {@code rel} の種別・ラベルへ反映する。
     *
     * @return 変更を適用したら true (キャンセルなら false)
     */
    static boolean editRelation(Component parent, ComponentRelation rel) {
        JComboBox<ComponentRelation.Kind> kindCombo =
                new JComboBox<>(ComponentRelation.Kind.values());
        kindCombo.setSelectedItem(rel.getKind());
        JTextField labelField = new JTextField(
                rel.getLabel() == null ? "" : rel.getLabel(), 18);
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.comp.dlg.relKind")));
        panel.add(kindCombo);
        panel.add(new JLabel(Messages.get("sketch.comp.dlg.relLabel")));
        panel.add(labelField);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.comp.dlg.relTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        rel.setKind((ComponentRelation.Kind) kindCombo.getSelectedItem());
        String label = labelField.getText().trim();
        rel.setLabel(label.isEmpty() ? null : label);
        return true;
    }
}
