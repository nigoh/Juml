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
 * オブジェクト図デザイナーの編集ダイアログ (オブジェクトの名前/ステレオタイプ/属性・
 * リンクの種別/ラベル)。UI 構築のみ担当し、適用の可否 (名前の重複チェック等) もここで
 * 完結させる。属性は 1 行 1 属性 ({@code name = value}) のテキストで編集する。
 */
final class ObjectSketchDialogs {

    /** PlantUML のオブジェクト名として安全な識別子 (引用符なしで書ける範囲)。 */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$.]*");

    private ObjectSketchDialogs() {
    }

    /**
     * オブジェクト編集ダイアログを表示し、OK なら {@code target} とリンク端点名へ反映する。
     *
     * @return 変更を適用したら true (キャンセル・不正入力なら false)
     */
    static boolean editObject(Component parent, ObjectSketchModel model, ObjectInstance target) {
        JTextField nameField = new JTextField(target.getName(), 20);
        JTextField stereoField = new JTextField(
                target.getStereotype() == null ? "" : target.getStereotype(), 20);
        JTextArea attrsArea = new JTextArea(
                String.join("\n", target.getAttributes()), 8, 28);

        JPanel top = new JPanel(new GridLayout(2, 2, 6, 4));
        top.add(new JLabel(Messages.get("sketch.obj.dlg.name")));
        top.add(nameField);
        top.add(new JLabel(Messages.get("sketch.obj.dlg.stereotype")));
        top.add(stereoField);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(top, BorderLayout.NORTH);
        panel.add(titled(attrsArea, Messages.get("sketch.obj.dlg.attributes")),
                BorderLayout.CENTER);

        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.obj.dlg.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String newName = nameField.getText().trim();
        ObjectInstance sameName = model.findObject(newName);
        if (!NAME.matcher(newName).matches()
                || (sameName != null && sameName != target)) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("sketch.obj.dlg.nameError"),
                    Messages.get("sketch.obj.dlg.title"), JOptionPane.WARNING_MESSAGE);
            return false;
        }
        model.renameObject(target, newName);
        String stereo = stereoField.getText().trim();
        target.setStereotype(stereo.isEmpty() ? null : stereo);
        applyLines(target.getAttributes(), attrsArea.getText());
        return true;
    }

    /**
     * リンク編集ダイアログを表示し、OK なら {@code link} の種別・ラベルへ反映する。
     *
     * @return 変更を適用したら true (キャンセルなら false)
     */
    static boolean editLink(Component parent, ObjectLink link) {
        JComboBox<ObjectLink.Kind> kindCombo =
                new JComboBox<>(ObjectLink.Kind.values());
        kindCombo.setSelectedItem(link.getKind());
        JTextField labelField = new JTextField(
                link.getLabel() == null ? "" : link.getLabel(), 20);
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.obj.dlg.linkKind")));
        panel.add(kindCombo);
        panel.add(new JLabel(Messages.get("sketch.obj.dlg.linkLabel")));
        panel.add(labelField);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.obj.dlg.linkTitle"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        link.setKind((ObjectLink.Kind) kindCombo.getSelectedItem());
        String label = labelField.getText().trim();
        link.setLabel(label.isEmpty() ? null : label);
        return true;
    }

    private static JScrollPane titled(JTextArea area, String title) {
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(BorderFactory.createTitledBorder(title));
        return sp;
    }

    /** テキストエリアの内容 (1 行 1 属性) をリストへ反映する。空行は捨てる。 */
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
