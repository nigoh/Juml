// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.regex.Pattern;

/**
 * 状態遷移図デザイナーの編集ダイアログ (状態の改名・遷移ラベル)。
 * UI 構築のみ担当し、適用の可否 (名前の重複チェック等) もここで完結させる。
 */
final class StateSketchDialogs {

    /** PlantUML の状態名として安全な識別子 (引用符なしで書ける範囲)。 */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$]*");

    private StateSketchDialogs() {
    }

    /**
     * 状態の改名ダイアログを表示し、OK なら {@code target} と遷移端点名へ反映する。
     *
     * @return 変更を適用したら true (キャンセル・不正入力なら false)
     */
    static boolean editState(Component parent, StateSketchModel model, StateNode target) {
        JTextField nameField = new JTextField(target.getName(), 20);
        JPanel panel = new JPanel(new GridLayout(1, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.state.dlg.name")));
        panel.add(nameField);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.state.dlg.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String newName = nameField.getText().trim();
        StateNode same = model.findState(newName);
        if (!NAME.matcher(newName).matches() || (same != null && same != target)) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("sketch.state.dlg.nameError"),
                    Messages.get("sketch.state.dlg.title"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        model.renameState(target, newName);
        return true;
    }

    /**
     * 遷移ラベルの編集ダイアログを表示し、OK なら {@code transition} のラベルへ反映する。
     *
     * @return 変更を適用したら true (キャンセルなら false)
     */
    static boolean editTransition(Component parent, StateTransition transition) {
        JTextField labelField = new JTextField(
                transition.getLabel() == null ? "" : transition.getLabel(), 20);
        JPanel panel = new JPanel(new GridLayout(1, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.state.dlg.label")));
        panel.add(labelField);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.state.dlg.transitionTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String label = labelField.getText().trim();
        transition.setLabel(label.isEmpty() ? null : label);
        return true;
    }
}
