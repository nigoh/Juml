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
 * {@link PumlSourcePanel} のコード編集操作 (行コメント切替・ブロックインデント・全置換) を
 * 検証する GUI テスト。いずれも 1 手で戻せる (複合編集) ことも確認する。
 */
public class PumlSourcePanelEditTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static PumlSourcePanel editable(String text) {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText(text);
            panel.selectRangeForTest(0, panel.getText().length());
        });
        return panel;
    }

    @Test
    public void toggleComment_commentsThenUncomments() {
        PumlSourcePanel panel = editable("class A\nclass B\n");
        GuiActionRunner.execute(panel::toggleCommentForTest);
        assertEquals("' class A\n' class B\n", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(() -> panel.selectRangeForTest(0, panel.getText().length()));
        GuiActionRunner.execute(panel::toggleCommentForTest);
        assertEquals("class A\nclass B\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void toggleComment_isSingleUndo() {
        PumlSourcePanel panel = editable("class A\nclass B\n");
        GuiActionRunner.execute(panel::toggleCommentForTest);
        assertEquals("' class A\n' class B\n", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("複数行のコメント切替は 1 手で戻る",
                "class A\nclass B\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void indentAndOutdent_shiftLines() {
        PumlSourcePanel panel = editable("a\nb\n");
        GuiActionRunner.execute(() -> panel.indentSelectionForTest(false));
        assertEquals("  a\n  b\n", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(() -> panel.selectRangeForTest(0, panel.getText().length()));
        GuiActionRunner.execute(() -> panel.indentSelectionForTest(true));
        assertEquals("a\nb\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void indent_isSingleUndo() {
        PumlSourcePanel panel = editable("a\nb\n");
        GuiActionRunner.execute(() -> panel.indentSelectionForTest(false));
        assertEquals("  a\n  b\n", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("複数行インデントは 1 手で戻る",
                "a\nb\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void setText_resetsActiveFindBar() {
        // 回帰: setText は removeAllHighlights で検索ハイライトを消すが、find バーの
        // hits[]/件数表示を reset しないと、次候補ジャンプが旧オフセットを新文書へ適用して
        // キャレット誤配置や例外を起こす。setText 時に findBar.reset() されることを確認する。
        PumlSourcePanel panel = editable("foo bar foo baz foo\n");
        GuiActionRunner.execute(() -> {
            panel.selectRangeForTest(0, 3); // "foo" を検索クエリに
            panel.performEditorActionForTest("juml-find"); // Ctrl+F 相当
        });
        assertTrue("検索起動で find バーがアクティブになること",
                GuiActionRunner.execute(panel::findBarActiveForTest));

        GuiActionRunner.execute(() -> panel.setText("completely different text\n"));
        assertFalse("setText で find バーが reset (非アクティブ) されること",
                GuiActionRunner.execute(panel::findBarActiveForTest));
    }

    @Test
    public void replaceAll_replacesEveryMatch() {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("foo bar foo baz foo\n");
            panel.replaceAllForTest("foo", "qux");
        });
        assertEquals("qux bar qux baz qux\n", GuiActionRunner.execute(panel::getText));
    }
}
