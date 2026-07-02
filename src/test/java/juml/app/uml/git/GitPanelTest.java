// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertNotNull;

/**
 * {@link GitPanel} の生成スモークテスト (i18n キー解決と Swing 構築が例外なく通ること)。
 * リポジトリ読み込みの実ロジックは {@link GitRepoServiceTest} 側で検証する。
 */
public class GitPanelTest {

    @Test
    public void construct_buildsWithoutRepository() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
        GitPanel panel = GuiActionRunner.execute(GitPanel::new);
        assertNotNull(panel);
        // リポジトリ未設定でもステータス通知の配線と再表示が例外なく動くこと。
        GuiActionRunner.execute(() -> {
            panel.setStatusReporter(msg -> { });
            panel.revalidate();
        });
    }
}
