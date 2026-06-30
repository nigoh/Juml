// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JComponent;
import javax.swing.JSplitPane;
import java.awt.Dimension;

/**
 * ウィンドウ操作系のヘルパ。コマンドパレット (Ctrl+Shift+P) とサイドバートグル (Ctrl+B) は
 * 発見可能性のため View メニューのアクセラレータで登録する (メニューとキーの単一ソース)。
 * ここではメニュー項目から呼ばれるサイドバー折りたたみロジックだけを提供する。
 */
final class AppShortcuts {

    private static final String SAVED_DIVIDER = "juml.savedDivider";

    private AppShortcuts() {
    }

    /** 左ペインを 0 幅に畳む / 直前の幅へ戻す。状態は split のクライアントプロパティに保持。 */
    static void toggleSidebar(JSplitPane split) {
        int loc = split.getDividerLocation();
        if (loc > 2) {
            split.putClientProperty(SAVED_DIVIDER, loc);
            // 一部 L&F は left の minimumSize で 0 まで畳めないため明示的に 0 を許可する。
            java.awt.Component left = split.getLeftComponent();
            if (left instanceof JComponent) {
                left.setMinimumSize(new Dimension(0, 0));
            }
            split.setDividerLocation(0);
        } else {
            Object saved = split.getClientProperty(SAVED_DIVIDER);
            split.setDividerLocation(saved instanceof Integer ? (Integer) saved : 280);
        }
    }
}
