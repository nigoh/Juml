// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.junit.Test;

import javax.swing.text.StyleConstants;
import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * bug-hunt R3 で発見: unified diff のハンク本文にある {@code ---} / {@code +++} で始まる行が
 * ファイルヘッダ色 (灰) に誤分類され、削除/追加として読めなかった。
 */
public class GitDiffViewStyleTest {

    private static final String DIFF = String.join("\n",
            "diff --git a/x.md b/x.md",
            "--- a/x.md",
            "+++ b/x.md",
            "@@ -1,3 +1,3 @@",
            " context",
            "--- old horizontal rule",
            "+++ new horizontal rule",
            "");

    private static Color colorOfLineStartingWith(GitDiffView view, String prefix) {
        String text;
        try {
            text = view.getDocument().getText(0, view.getDocument().getLength());
        } catch (javax.swing.text.BadLocationException e) {
            throw new AssertionError(e);
        }
        int offset = text.indexOf(prefix, text.indexOf("@@"));
        return StyleConstants.getForeground(
                view.getStyledDocument().getCharacterElement(offset).getAttributes());
    }

    @Test
    public void hunkBodyLinesStartingWithDashesAreNotHeaderColoured() {
        GitDiffView view = new GitDiffView();
        view.setDiff(DIFF);
        Color removed = colorOfLineStartingWith(view, "--- old horizontal rule");
        Color added = colorOfLineStartingWith(view, "+++ new horizontal rule");
        assertNotEquals("ハンク本文の --- は削除行として着色されるはず", removed, added);
    }

    @Test
    public void fileHeaderLinesBeforeTheFirstHunkShareTheHeaderColour() {
        GitDiffView view = new GitDiffView();
        view.setDiff(DIFF);
        String text;
        try {
            text = view.getDocument().getText(0, view.getDocument().getLength());
        } catch (javax.swing.text.BadLocationException e) {
            throw new AssertionError(e);
        }
        Color minus = StyleConstants.getForeground(view.getStyledDocument()
                .getCharacterElement(text.indexOf("--- a/x.md")).getAttributes());
        Color plus = StyleConstants.getForeground(view.getStyledDocument()
                .getCharacterElement(text.indexOf("+++ b/x.md")).getAttributes());
        assertEquals("ハンク前の --- / +++ は同じヘッダ色のはず", minus, plus);
    }
}
