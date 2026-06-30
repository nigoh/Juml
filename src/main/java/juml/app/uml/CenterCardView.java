// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.CardLayout;

/**
 * 中央領域を「Welcome 空状態」と「ワークスペース (ツリー + タブ)」で切り替える
 * {@link CardLayout} コンテナ。既定では Welcome を表示する。
 *
 * <p>カード切り替えの定型処理を {@link UmlMainFrame} から切り出し、メインフレームを
 * 配線役に保つ (VS Code 風タブモデルの「Home タブ」ではない点に注意: 図ロジックは持たない)。</p>
 */
final class CenterCardView extends JPanel {

    private static final String WELCOME = "welcome";
    private static final String WORKSPACE = "workspace";

    private final WelcomePanel welcome;

    CenterCardView(JComponent workspace, WelcomePanel welcome) {
        super(new CardLayout());
        this.welcome = welcome;
        add(welcome, WELCOME);
        add(workspace, WORKSPACE);
    }

    /** プロジェクト未ロード時の Welcome を表示する (最近のプロジェクトを再読込)。 */
    void showWelcome() {
        welcome.refreshRecent();
        ((CardLayout) getLayout()).show(this, WELCOME);
    }

    /** ツリー + タブのワークスペースを表示する。 */
    void showWorkspace() {
        ((CardLayout) getLayout()).show(this, WORKSPACE);
    }
}
