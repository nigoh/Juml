// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlSourcePanel} のコードペイン化 (シンタックスハイライト + 行番号ガター) を
 * 検証する GUI テスト。行番号の追従とトークン着色が効いていることを確認する。
 */
public class PumlSourcePanelHighlightTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void setText_appliesSyntaxHighlight() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("@startuml\nclass Foo\nFoo --> Bar\n@enduml\n");
            panel.applyHighlightForTest();
        });
        assertTrue("キーワード/矢印などが基準色と異なる色で着色されるはず",
                GuiActionRunner.execute(panel::hasColoredRunForTest));
    }

    @Test
    public void readOnlyPane_alsoHighlights() {
        // 生成ソース表示 (リードオンリー) でもハイライトが効く。
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setText("@startuml\ninterface I\n@enduml\n");
            panel.applyHighlightForTest();
        });
        assertTrue(GuiActionRunner.execute(panel::hasColoredRunForTest));
    }

    @Test
    public void gutterTracksLineCount() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> panel.setText("a\nb\nc\n"));
        // "a\nb\nc\n" は 3 行 + 末尾の空行 = 4 要素。
        assertEquals(4, (int) GuiActionRunner.execute(panel::gutterLineCountForTest));
    }

    @Test
    public void bracketMatch_highlightsPairAtCaret() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("class A {\n}\n");
            // "{" は index 8。その直後 (caret 9) にキャレットを置くと対応 "}" と 2 件強調。
            panel.selectRangeForTest(9, 9);
        });
        assertEquals("対応括弧は 2 件強調される", 2,
                (int) GuiActionRunner.execute(panel::bracketMatchCountForTest));
    }

    @Test
    public void bracketMatch_noBracketAtCaret_hasNoHighlight() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("class A\n");
            panel.selectRangeForTest(2, 2);
        });
        assertEquals(0, (int) GuiActionRunner.execute(panel::bracketMatchCountForTest));
    }

    @Test
    public void emptyText_hasNoColoredRuns() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setText("");
            panel.applyHighlightForTest();
        });
        assertFalse(GuiActionRunner.execute(panel::hasColoredRunForTest));
    }
}
