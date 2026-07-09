// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridLayout;

/**
 * アクティビティ図デザイナーの編集ダイアログ (アクション / 分岐)。
 * UI 構築のみ担当し、適用もここで完結させる。
 */
final class ActivitySketchDialogs {

    private ActivitySketchDialogs() {
    }

    /**
     * アクション編集ダイアログを表示し、OK なら {@code target} へ反映する。
     * セミコロンや改行は PlantUML の 1 行アクション ({@code :text;}) を壊すため取り除く。
     *
     * @return 変更を適用したら true (キャンセル・空入力なら false)
     */
    static boolean editAction(Component parent, ActivityNode target) {
        JTextField textField = new JTextField(target.getText(), 24);
        JPanel panel = new JPanel(new GridLayout(1, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.act.dlg.actionText")));
        panel.add(textField);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.act.dlg.actionTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String text = sanitize(textField.getText());
        if (text.isEmpty()) {
            return false;
        }
        target.setText(text);
        return true;
    }

    /**
     * 分岐 (IF) 編集ダイアログを表示し、OK なら {@code target} へ反映する。
     * 「else ブランチあり」を外すと else 節を中のノードごと取り除く。
     *
     * @return 変更を適用したら true (キャンセル・空条件なら false)
     */
    static boolean editIf(Component parent, ActivityNode target) {
        JTextField condField = new JTextField(target.getCondition(), 24);
        JTextField thenField = new JTextField(
                target.getThenLabel() == null ? "" : target.getThenLabel(), 24);
        JTextField elseField = new JTextField(
                target.getElseLabel() == null ? "" : target.getElseLabel(), 24);
        JCheckBox hasElse = new JCheckBox(Messages.get("sketch.act.dlg.hasElse"),
                target.getElseBranch() != null);
        JPanel panel = new JPanel(new GridLayout(4, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.act.dlg.condition")));
        panel.add(condField);
        panel.add(new JLabel(Messages.get("sketch.act.dlg.thenLabel")));
        panel.add(thenField);
        panel.add(new JLabel(Messages.get("sketch.act.dlg.elseLabel")));
        panel.add(elseField);
        panel.add(hasElse);
        panel.add(new JLabel());
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.act.dlg.ifTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String cond = sanitizeParen(condField.getText());
        if (cond.isEmpty()) {
            return false;
        }
        target.setCondition(cond);
        String thenLabel = sanitizeParen(thenField.getText());
        target.setThenLabel(thenLabel.isEmpty() ? null : thenLabel);
        String elseLabel = sanitizeParen(elseField.getText());
        target.setElseLabel(elseLabel.isEmpty() ? null : elseLabel);
        if (hasElse.isSelected()) {
            target.ensureElseBranch();
        } else {
            target.removeElseBranch();
        }
        return true;
    }

    /** アクション本文からセミコロン・改行を除去する (1 行アクション構文の保全)。 */
    private static String sanitize(String s) {
        return (s == null ? "" : s).replace(";", "").replace("\n", " ").trim();
    }

    /** 条件・ラベルから括弧・改行を除去する ({@code if (...) then (...)} 構文の保全)。 */
    private static String sanitizeParen(String s) {
        return (s == null ? "" : s).replace("(", "").replace(")", "")
                .replace("\n", " ").trim();
    }
}
