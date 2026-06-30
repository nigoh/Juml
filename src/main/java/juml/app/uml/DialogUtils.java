// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

final class DialogUtils {

    private DialogUtils() {}

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
