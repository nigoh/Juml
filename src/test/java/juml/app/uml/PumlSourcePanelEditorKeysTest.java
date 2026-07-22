// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;

/**
 * {@link PumlSourcePanel} に配線された VS Code 相当の編集キー
 * ({@link PumlEditorKeys}) の統合テスト (GUI, headless-skip)。
 *
 * <p>Enter 自動インデント・自動閉じペア・行複製/移動/削除がドキュメントへ正しく
 * 適用され、複合編集として 1 手で Undo できることを固定する。</p>
 */
public class PumlSourcePanelEditorKeysTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static PumlSourcePanel editable(String text, int caret) {
        PumlSourcePanel panel = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText(text);
            panel.setCaretForTest(caret);
        });
        return panel;
    }

    @Test
    public void newline_keepsIndentOfCurrentLine() {
        PumlSourcePanel panel = editable("  class A\n", 9);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-newline"));
        assertEquals("  class A\n  \n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void newline_indentsDeeperAfterBlockKeyword() {
        PumlSourcePanel panel = editable("alt ok\n", 6);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-newline"));
        assertEquals("alt ok\n  \n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void typedOpenParen_insertsPairAndCaretBetween() {
        PumlSourcePanel panel = editable("note \n", 5);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-open-paren"));
        assertEquals("note ()\n", GuiActionRunner.execute(panel::getText));
        assertEquals(6, (int) GuiActionRunner.execute(panel::caretForTest));
    }

    @Test
    public void duplicateLine_copiesCurrentLineBelow_andUndoesInOneStep() {
        PumlSourcePanel panel = editable("class A\nclass B\n", 0);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-line-dup"));
        assertEquals("class A\nclass A\nclass B\n", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("class A\nclass B\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void moveLineDown_swapsLines_andUndoesInOneStep() {
        PumlSourcePanel panel = editable("class A\nclass B\n", 0);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-line-down"));
        assertEquals("class B\nclass A\n", GuiActionRunner.execute(panel::getText));
        GuiActionRunner.execute(panel::undoForTest);
        assertEquals("class A\nclass B\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void deleteLine_removesCurrentLine() {
        PumlSourcePanel panel = editable("class A\nclass B\n", 2);
        GuiActionRunner.execute(() -> panel.performEditorActionForTest("juml-line-del"));
        assertEquals("class B\n", GuiActionRunner.execute(panel::getText));
    }

    @Test
    public void editorActions_doNothingWhenReadOnly() {
        PumlSourcePanel readOnly = GuiActionRunner.execute(PumlSourcePanel::new);
        GuiActionRunner.execute(() -> readOnly.setText("class A\n"));
        // リードオンリー表示にはエディタキーが配線されない (アクション未登録 = 無害)。
        GuiActionRunner.execute(() -> readOnly.performEditorActionForTest("juml-line-del"));
        assertEquals("class A\n", GuiActionRunner.execute(readOnly::getText));
    }
}
