// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlSourcePanel} の入力補完 (Ctrl+Space) の統合を検証する GUI テスト。
 */
public class PumlSourcePanelCompletionTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void keywordCompletion_insertsRemainderAtCaret() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("part");
            panel.selectRangeForTest(4, 4); // キャレットを末尾へ
        });
        assertTrue("補完候補があるはず",
                GuiActionRunner.execute(panel::completionCandidateCountForTest) > 0);
        GuiActionRunner.execute(() -> panel.applyCompletionForTest("participant"));
        assertEquals("participant", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void bufferIdentifierCompletion_insertsRemainder() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("class Foobar\nFoo");
            panel.selectRangeForTest(panel.getText().length(), panel.getText().length());
            panel.applyCompletionForTest("Foobar");
        });
        assertEquals("class Foobar\nFoobar", GuiActionRunner.execute(panel::getText));
    }
}
