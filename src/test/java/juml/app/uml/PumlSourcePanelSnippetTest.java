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
 * {@link PumlSourcePanel} のスニペット挿入 (編集モード限定) を検証する GUI テスト。
 */
public class PumlSourcePanelSnippetTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void insertSnippet_inEditableMode_insertsAtCaret() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("@startuml\n@enduml\n");
            panel.insertSnippet("class Foo {\n}\n");
        });
        String text = GuiActionRunner.execute(panel::getText);
        assertTrue("スニペットが本文へ挿入されるはず: " + text, text.contains("class Foo {"));
    }

    @Test
    public void insertSnippet_whenReadOnly_isIgnored() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            // setEditable を呼ばない = リードオンリー。
            panel.setText("@startuml\n@enduml\n");
            panel.insertSnippet("class Foo {\n}\n");
        });
        String text = GuiActionRunner.execute(panel::getText);
        assertFalse("リードオンリーではスニペットを挿入しない", text.contains("class Foo {"));
    }

    @Test
    public void errorHighlight_validAndOutOfRangeLines_doNotThrow() {
        // エラー行ハイライトが範囲外・0・クリアでも例外を出さず、テキストを壊さないこと。
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        String original = "@startuml\nclass A\nclass B\n@enduml\n";
        GuiActionRunner.execute(() -> {
            panel.setText(original);
            panel.highlightErrorLine(2);      // 妥当な行
            panel.highlightErrorLine(999);    // 範囲外 → 無視
            panel.highlightErrorLine(0);      // 0 → 無視
            panel.clearErrorHighlight();      // 二重クリアも安全
            panel.clearErrorHighlight();
        });
        assertEquals("ハイライト操作はテキストを変更しない",
                original, GuiActionRunner.execute(panel::getText));
        assertEquals("最後がクリアなのでハイライトは 0 件",
                0, (int) GuiActionRunner.execute(panel::highlightCountForTest));
    }

    @Test
    public void errorHighlight_appliesThenClears_countGoesUpThenDown() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> panel.setText("a\nb\nc\n"));
        GuiActionRunner.execute(() -> panel.highlightErrorLine(2));
        assertEquals("妥当行のハイライトで 1 件",
                1, (int) GuiActionRunner.execute(panel::highlightCountForTest));
        // 別の行を再ハイライトしても件数は 1 (前のを消してから付ける = 古い行に残らない)。
        GuiActionRunner.execute(() -> panel.highlightErrorLine(1));
        assertEquals("再ハイライトでも 1 件 (古い行に残らない)",
                1, (int) GuiActionRunner.execute(panel::highlightCountForTest));
        GuiActionRunner.execute(panel::clearErrorHighlight);
        assertEquals("クリアで 0 件",
                0, (int) GuiActionRunner.execute(panel::highlightCountForTest));
    }

    @Test
    public void updateUI_reappliesErrorHighlightAfterThemeSwitch() throws Exception {
        // Look&Feel ライブ切替 (updateUI) 後もエラー行ハイライトが残る (現テーマ色で貼り直す)。
        // updateUI は invokeLater で貼り直すため、EDT を回してから件数を確認する。
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setText("a\nb\nc\n");
            panel.highlightErrorLine(2);
        });
        assertEquals("切替前は 1 件", 1,
                (int) GuiActionRunner.execute(panel::highlightCountForTest));
        GuiActionRunner.execute(panel::updateUI);
        GuiActionRunner.execute(() -> { }); // invokeLater の貼り直しを消化する
        assertEquals("テーマ切替後もエラー行ハイライトは残る (1 件)", 1,
                (int) GuiActionRunner.execute(panel::highlightCountForTest));
        // クリア後の updateUI では復活しない。
        GuiActionRunner.execute(panel::clearErrorHighlight);
        GuiActionRunner.execute(panel::updateUI);
        GuiActionRunner.execute(() -> { });
        assertEquals("クリア済みなら updateUI で復活しない (0 件)", 0,
                (int) GuiActionRunner.execute(panel::highlightCountForTest));
    }

    @Test
    public void setText_clearsPreviousErrorHighlight_indirectly() {
        // ハイライト中に setText で内容が変わっても壊れないこと (再描画経路の健全性)。
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setText("a\nb\nc\n");
            panel.highlightErrorLine(2);
            panel.setText("x\ny\n");
            panel.clearErrorHighlight();
        });
        assertEquals("x\ny\n", GuiActionRunner.execute(panel::getText));
    }
}
