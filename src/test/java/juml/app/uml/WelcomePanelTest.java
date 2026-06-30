// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Welcome 空状態パネルと中央カード切替の headless スモークテスト。
 *
 * <p>ProjectRepository 未初期化でも例外なく構築でき (最近のプロジェクトは空表示)、
 * カード切替が落ちないことを確認する。Swing ウィジェット構築は headless で可能。</p>
 */
public class WelcomePanelTest {

    @Test
    public void buildsWithoutRepositoryAndRefreshes() {
        AtomicInteger open = new AtomicInteger();
        WelcomePanel wp = new WelcomePanel(open::incrementAndGet, open::incrementAndGet, f -> { });
        assertNotNull(wp);
        // ProjectRepository 未初期化でも refreshRecent は例外を投げない (空表示にフォールバック)。
        wp.refreshRecent();
    }

    @Test
    public void centerCardSwitchingDoesNotThrow() {
        WelcomePanel wp = new WelcomePanel(() -> { }, () -> { }, f -> { });
        JPanel workspace = new JPanel();
        CenterCardView cards = new CenterCardView(workspace, wp);
        // 既定は Welcome。切替が例外なく動く。
        assertTrue(cards.getLayout() instanceof CardLayout);
        cards.showWorkspace();
        cards.showWelcome();
        cards.showWorkspace();
    }
}
