// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
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
 * 配置図デザイナーの編集ダイアログ (ノードの id/種別/表示名・リンクの種別/ラベル)。
 * UI 構築のみ担当し、適用の可否 (id の重複チェック等) もここで完結させる。
 */
final class DeploySketchDialogs {

    /** PlantUML の id として安全な識別子。 */
    private static final Pattern ID = SketchIdentifier.BARE_PATTERN;

    private DeploySketchDialogs() {
    }

    /**
     * ノード編集ダイアログを表示し、OK なら {@code target} とリンク端点へ反映する。
     *
     * @return 変更を適用したら true (キャンセル・不正入力なら false)
     */
    static boolean editNode(Component parent, DeploySketchModel model, DeployNode target) {
        JTextField idField = new JTextField(target.getId(), 18);
        JComboBox<DeployNode.Kind> kindCombo =
                new JComboBox<>(DeployNode.Kind.values());
        kindCombo.setSelectedItem(target.getKind());
        JTextField labelField = new JTextField(
                target.getLabel() == null ? "" : target.getLabel(), 18);

        JPanel panel = new JPanel(new GridLayout(3, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.depl.dlg.id")));
        panel.add(idField);
        panel.add(new JLabel(Messages.get("sketch.depl.dlg.kind")));
        panel.add(kindCombo);
        panel.add(new JLabel(Messages.get("sketch.depl.dlg.label")));
        panel.add(labelField);

        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.depl.dlg.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String newId = idField.getText().trim();
        if (!isValidNewId(newId, model, target)) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("sketch.depl.dlg.idError"),
                    Messages.get("sketch.depl.dlg.title"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        model.renameNode(target, newId);
        target.setKind((DeployNode.Kind) kindCombo.getSelectedItem());
        String label = labelField.getText().trim();
        target.setLabel(label.isEmpty() || label.equals(newId) ? null : label);
        return true;
    }

    /**
     * 新しい id が採用可能か判定する (PlantUML の識別子として妥当であり、かつ
     * {@code current} 自身以外に同じ id を持つノードが存在しない)。モーダルに依存しない
     * 純粋な判定のみを行うため、ユニットテストから直接呼び出せる。
     */
    static boolean isValidNewId(String candidate, DeploySketchModel model, DeployNode current) {
        DeployNode same = model.findNode(candidate);
        return ID.matcher(candidate).matches() && (same == null || same == current);
    }

    /**
     * リンク編集ダイアログを表示し、OK なら {@code link} の種別・ラベルへ反映する。
     *
     * @return 変更を適用したら true (キャンセルなら false)
     */
    static boolean editLink(Component parent, DeployLink link) {
        JComboBox<DeployLink.Kind> kindCombo =
                new JComboBox<>(DeployLink.Kind.values());
        kindCombo.setSelectedItem(link.getKind());
        JTextField labelField = new JTextField(
                link.getLabel() == null ? "" : link.getLabel(), 18);
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.depl.dlg.relKind")));
        panel.add(kindCombo);
        panel.add(new JLabel(Messages.get("sketch.depl.dlg.relLabel")));
        panel.add(labelField);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.depl.dlg.relTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        link.setKind((DeployLink.Kind) kindCombo.getSelectedItem());
        String label = labelField.getText().trim();
        link.setLabel(label.isEmpty() ? null : label);
        return true;
    }
}
