// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;

final class DialogUtils {

    private DialogUtils() {}

    /**
     * 保存先ファイルが既に存在する場合に上書き確認ダイアログを出す。
     * Swing の {@link javax.swing.JFileChooser} は上書き確認をしないため、
     * 保存経路は必ず「最終的な保存先が確定した後」(拡張子補完後) にこれを通すこと。
     *
     * @return 書き込んでよければ true (未存在ファイルは常に true)
     */
    static boolean confirmOverwrite(java.awt.Component parent, java.io.File target) {
        if (target == null || !target.exists()) {
            return true;
        }
        return confirmDestructive(parent,
                java.text.MessageFormat.format(
                        juml.util.Messages.get("dialog.overwrite.message"),
                        target.getAbsolutePath()),
                juml.util.Messages.get("dialog.overwrite.title"));
    }

    /**
     * 破壊的操作 (上書き / 全消去 / 全タブクローズ 等) の Yes/No 確認ダイアログ。
     * 既定ボタン・初期フォーカスを「No」側に置き、うっかり Enter を押しただけで
     * 破壊操作が実行されるのを防ぐ (非破壊の確認は従来どおり Yes 既定でよい)。
     *
     * @return ユーザーが Yes を選んだら true
     */
    static boolean confirmDestructive(java.awt.Component parent, String message, String title) {
        String yes = uiText("OptionPane.yesButtonText", "Yes");
        String no = uiText("OptionPane.noButtonText", "No");
        Object[] options = {yes, no};
        int r = javax.swing.JOptionPane.showOptionDialog(parent, message, title,
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE,
                null, options, no);
        return r == 0; // options[0] == Yes
    }

    private static String uiText(String key, String fallback) {
        String s = javax.swing.UIManager.getString(key);
        return s != null ? s : fallback;
    }

    /**
     * OK / Cancel ボタンを右寄せパネルにまとめる。順序は OK → Cancel で統一。
     * 追加ボタン ({@code extras}) がある場合は OK の前に並べる。
     */
    static JPanel buildButtonPanel(JButton ok, JButton cancel, JButton... extras) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        for (JButton b : extras) {
            panel.add(b);
        }
        panel.add(ok);
        panel.add(cancel);
        return panel;
    }

    /**
     * ダイアログに Escape-で閉じる と Enter-でOK の2つをまとめて設定する。
     * 全ダイアログから共通で呼ぶことでキーボード操作を統一する。
     */
    static void installEscapeAndDefault(JDialog dlg, JButton okButton) {
        dlg.getRootPane().registerKeyboardAction(
                e -> dlg.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        if (okButton != null) {
            dlg.getRootPane().setDefaultButton(okButton);
        }
    }
}
