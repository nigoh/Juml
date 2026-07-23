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
    public void typing_coalescesIntoSingleUndo() {
        // 成熟度: 連続タイプは「直前のタイプ塊」を 1 手で戻せること (VS Code 相当)。
        // 以前は 1 文字ごとに Undo 単位が分かれ、11 文字打つと Ctrl+Z ×11 が必要だった。
        PumlSourcePanel panel = editable("");
        GuiActionRunner.execute(() -> panel.typeForTest("participant"));
        assertEquals("participant", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("連続タイプは 1 手の Undo でまとめて戻る",
                "", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void typing_isSeparateUndoPerLine() {
        // 改行でタイプ塊を区切る (VS Code も行単位で戻せる)。1 手 Undo は最後の行の
        // タイプ塊だけを戻し、前の行は残る。
        PumlSourcePanel panel = editable("");
        GuiActionRunner.execute(() -> panel.typeForTest("ab\ncd"));
        assertEquals("ab\ncd", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("1 手 Undo は 2 行目のタイプ塊だけを戻す",
                "ab\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void tab_withSingleLineSelection_indentsLine() {
        // 成熟度: 単一行内に選択があって Tab を押したら、その行を字下げする (VS Code 相当)。
        // 以前は insertSnippet(INDENT) で選択を残したままキャレット位置へ空白が割り込んでいた。
        PumlSourcePanel panel = editable("class A\n");
        GuiActionRunner.execute(() -> {
            panel.selectRangeForTest(6, 7); // 単一行内で "A" を選択
            panel.performEditorActionForTest("juml-indent"); // Tab
        });
        assertEquals("選択があれば行頭が字下げされる (空白割込みでない)",
                "  class A\n", GuiActionRunner.execute(panel::getText));
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
