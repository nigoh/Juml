// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.List;
import java.util.regex.Pattern;

/**
 * シーケンス図デザイナーの編集ダイアログ (メッセージ / 参加者)。
 * UI 構築のみ担当し、適用の可否 (名前の重複チェック等) もここで完結させる。
 */
final class SeqSketchDialogs {

    /** PlantUML の参加者名として安全な識別子 (引用符なしで書ける範囲)。 */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$.]*");

    private SeqSketchDialogs() {
    }

    /**
     * メッセージ編集ダイアログを表示し、OK なら {@code target} へ反映する。
     * 「この後で activate / deactivate」のチェックは、メッセージ直後に続く
     * activate/deactivate 項目の有無と連動する (チェック変更で項目を挿入・削除する)。
     *
     * @return 変更を適用したら true (キャンセルなら false)
     */
    static boolean editMessage(Component parent, SeqSketchModel model, SeqItem target) {
        String[] names = model.getParticipants().stream()
                .map(SeqParticipant::getName).toArray(String[]::new);
        JComboBox<String> fromCombo = new JComboBox<>(names);
        fromCombo.setSelectedItem(target.getFrom());
        JComboBox<String> toCombo = new JComboBox<>(names);
        toCombo.setSelectedItem(target.getTo());
        JComboBox<SeqItem.Arrow> arrowCombo = new JComboBox<>(SeqItem.Arrow.values());
        arrowCombo.setSelectedItem(target.getArrow());
        JTextField labelField = new JTextField(
                target.getLabel() == null ? "" : target.getLabel(), 20);
        JCheckBox activateBox = new JCheckBox(Messages.get("sketch.seq.dlg.activate"),
                hasAdjacent(model, target, SeqItem.Kind.ACTIVATE, target.getTo()));
        JCheckBox deactivateBox = new JCheckBox(Messages.get("sketch.seq.dlg.deactivate"),
                hasAdjacent(model, target, SeqItem.Kind.DEACTIVATE, target.getFrom()));

        JPanel panel = new JPanel(new GridLayout(6, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.seq.dlg.from")));
        panel.add(fromCombo);
        panel.add(new JLabel(Messages.get("sketch.seq.dlg.to")));
        panel.add(toCombo);
        panel.add(new JLabel(Messages.get("sketch.seq.dlg.arrow")));
        panel.add(arrowCombo);
        panel.add(new JLabel(Messages.get("sketch.seq.dlg.label")));
        panel.add(labelField);
        panel.add(activateBox);
        panel.add(new JLabel());
        panel.add(deactivateBox);
        panel.add(new JLabel());

        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.seq.dlg.msgTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION || fromCombo.getSelectedItem() == null
                || toCombo.getSelectedItem() == null) {
            return false;
        }
        // 端点変更前に旧 activate/deactivate 隣接項目を外し、変更後に付け直す。
        setAdjacent(model, target, SeqItem.Kind.ACTIVATE, target.getTo(), false);
        setAdjacent(model, target, SeqItem.Kind.DEACTIVATE, target.getFrom(), false);
        target.setFrom((String) fromCombo.getSelectedItem());
        target.setTo((String) toCombo.getSelectedItem());
        target.setArrow((SeqItem.Arrow) arrowCombo.getSelectedItem());
        String label = labelField.getText().trim();
        target.setLabel(label.isEmpty() ? null : label);
        setAdjacent(model, target, SeqItem.Kind.DEACTIVATE, target.getFrom(),
                deactivateBox.isSelected());
        setAdjacent(model, target, SeqItem.Kind.ACTIVATE, target.getTo(),
                activateBox.isSelected());
        return true;
    }

    /** メッセージ直後 (次のメッセージまで) に指定種別・対象の項目があるか。 */
    private static boolean hasAdjacent(SeqSketchModel model, SeqItem message,
                                       SeqItem.Kind kind, String targetName) {
        return findAdjacent(model, message, kind, targetName) != null;
    }

    private static SeqItem findAdjacent(SeqSketchModel model, SeqItem message,
                                        SeqItem.Kind kind, String targetName) {
        List<SeqItem> items = model.getItems();
        for (int i = items.indexOf(message) + 1; i >= 1 && i < items.size(); i++) {
            SeqItem m = items.get(i);
            if (m.getKind() == SeqItem.Kind.MESSAGE) {
                return null;
            }
            if (m.getKind() == kind && m.getTarget().equals(targetName)) {
                return m;
            }
        }
        return null;
    }

    /** メッセージ直後の activate/deactivate 項目を有無に合わせて挿入・削除する。 */
    private static void setAdjacent(SeqSketchModel model, SeqItem message,
                                    SeqItem.Kind kind, String targetName, boolean present) {
        SeqItem existing = findAdjacent(model, message, kind, targetName);
        if (present && existing == null) {
            SeqItem inserted = kind == SeqItem.Kind.ACTIVATE
                    ? SeqItem.activate(targetName) : SeqItem.deactivate(targetName);
            model.getItems().add(model.getItems().indexOf(message) + 1, inserted);
        } else if (!present && existing != null) {
            model.getItems().remove(existing);
        }
    }

    /**
     * 参加者編集ダイアログを表示し、OK なら {@code target} と項目の端点名へ反映する。
     *
     * @return 変更を適用したら true (キャンセル・不正入力なら false)
     */
    static boolean editParticipant(Component parent, SeqSketchModel model,
                                   SeqParticipant target) {
        JTextField nameField = new JTextField(target.getName(), 20);
        JComboBox<SeqParticipant.Kind> kindCombo =
                new JComboBox<>(SeqParticipant.Kind.values());
        kindCombo.setSelectedItem(target.getKind());
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.seq.dlg.partName")));
        panel.add(nameField);
        panel.add(new JLabel(Messages.get("sketch.seq.dlg.partKind")));
        panel.add(kindCombo);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.seq.dlg.partTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String newName = nameField.getText().trim();
        SeqParticipant sameName = model.findParticipant(newName);
        if (!NAME.matcher(newName).matches() || (sameName != null && sameName != target)) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("sketch.seq.dlg.nameError"),
                    Messages.get("sketch.seq.dlg.partTitle"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        model.renameParticipant(target, newName);
        target.setKind((SeqParticipant.Kind) kindCombo.getSelectedItem());
        // GUI から種別を確定した参加者は明示宣言として保存する。
        target.setDeclared(true);
        return true;
    }
}
