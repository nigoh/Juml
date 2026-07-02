// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.regex.Pattern;

/**
 * GUI デザイナーのクラス編集ダイアログ (名前・種別・フィールド・メソッド)。
 * UI 構築のみ担当し、適用の可否 (名前の重複チェック等) もここで完結させる。
 */
final class SketchEditDialogs {

    /** PlantUML のクラス名として安全な識別子 (引用符なしで書ける範囲)。 */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$.]*");

    private SketchEditDialogs() {
    }

    /**
     * クラス編集ダイアログを表示し、OK なら {@code target} と関係の端点名へ反映する。
     *
     * @return 変更を適用したら true (キャンセル・不正入力なら false)
     */
    static boolean editClass(Component parent, SketchModel model, SketchClass target) {
        JTextField nameField = new JTextField(target.getName(), 20);
        JComboBox<SketchClass.Kind> kindCombo =
                new JComboBox<>(SketchClass.Kind.values());
        kindCombo.setSelectedItem(target.getKind());
        JTextArea fieldsArea = new JTextArea(String.join("\n", target.getFields()), 6, 28);
        JTextArea methodsArea = new JTextArea(String.join("\n", target.getMethods()), 6, 28);

        JPanel top = new JPanel(new GridLayout(2, 2, 6, 4));
        top.add(new JLabel(Messages.get("sketch.dlg.name")));
        top.add(nameField);
        top.add(new JLabel(Messages.get("sketch.dlg.kind")));
        top.add(kindCombo);

        JPanel body = new JPanel(new GridLayout(1, 2, 8, 0));
        body.add(titled(fieldsArea, Messages.get("sketch.dlg.fields")));
        body.add(titled(methodsArea, Messages.get("sketch.dlg.methods")));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);

        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.dlg.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String newName = nameField.getText().trim();
        SketchClass sameName = model.findClass(newName);
        if (!NAME.matcher(newName).matches()
                || (sameName != null && sameName != target)) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("sketch.dlg.nameError"),
                    Messages.get("sketch.dlg.title"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        model.renameClass(target, newName);
        target.setKind((SketchClass.Kind) kindCombo.getSelectedItem());
        applyLines(target.getFields(), fieldsArea.getText());
        applyLines(target.getMethods(), methodsArea.getText());
        return true;
    }

    private static JScrollPane titled(JTextArea area, String title) {
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createTitledBorder(title));
        return sp;
    }

    /** テキストエリアの内容 (1 行 1 メンバー) をリストへ反映する。空行は捨てる。 */
    private static void applyLines(java.util.List<String> target, String text) {
        target.clear();
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                target.add(t);
            }
        }
    }
}
