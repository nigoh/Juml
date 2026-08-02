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

/**
 * マインドマップデザイナーの編集ダイアログ (ノードのテキスト)。
 * UI 構築のみ担当し、適用もここで完結させる。
 */
final class MindmapSketchDialogs {

    private MindmapSketchDialogs() {
    }

    /**
     * ノードのテキスト編集ダイアログを表示し、OK なら {@code target} へ反映する。
     * 改行はノード行 ({@code <記号> テキスト}) を壊すため取り除く。マインドマップの
     * テキストは重複や記号に寛容なので、一意性チェックはせず形式のみ整える。
     *
     * @return 変更を適用したら true (キャンセル・空入力なら false)
     */
    static boolean editNode(Component parent, MindmapNode target) {
        JTextField textField = new JTextField(target.getText(), 24);
        JPanel panel = new JPanel(new GridLayout(1, 2, 6, 4));
        panel.add(new JLabel(Messages.get("sketch.mm.dlg.text")));
        panel.add(textField);
        int choice = JOptionPane.showConfirmDialog(parent, panel,
                Messages.get("sketch.mm.dlg.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        String text = sanitize(textField.getText());
        if (!isValidText(text)) {
            return false;
        }
        target.setText(text);
        return true;
    }

    /** ノードテキストから改行を除去し前後空白を落とす (ノード行構文の保全)。純関数。 */
    static String sanitize(String s) {
        return (s == null ? "" : s).replace("\r", "").replace("\n", " ").trim();
    }

    /** サニタイズ済みテキストが採用可能か (空文字は不可)。純関数。 */
    static boolean isValidText(String sanitized) {
        return sanitized != null && !sanitized.isEmpty();
    }
}
