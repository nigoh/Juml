// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.List;
import java.util.function.Consumer;

/**
 * PlantUML エディタの「書いた後を直す」コマンド 3 つ:
 * リネーム (F2 / Shift+F6)・クイックフィックス (Alt+Enter)・整形 (Shift+Alt+F)。
 *
 * <p>{@link PumlSourcePanel} の外に置くのは配線を薄く保つため。計算は
 * {@link PumlRename} / {@link PumlDiagnostics} / {@link PumlFormatter} の純関数が担い、
 * ここはダイアログとドキュメント適用だけを受け持つ。</p>
 */
final class PumlEditorCommands {

    private final JTextPane pane;
    /** 一連の編集を 1 個の Undo に束ねるランナー。 */
    private final Consumer<Runnable> compound;

    PumlEditorCommands(JTextPane pane, Consumer<Runnable> compound) {
        this.pane = pane;
        this.compound = compound;
    }

    /** キーを配線する。F2 (VS Code) と Shift+F6 (IntelliJ) はどちらの癖でも引けるよう両方通す。 */
    void install(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "juml-rename");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F6, InputEvent.SHIFT_DOWN_MASK),
                "juml-rename");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK),
                "juml-quickfix");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "juml-format");
        am.put("juml-rename", action(this::renameViaDialog));
        am.put("juml-quickfix", action(this::quickFixAtCaret));
        am.put("juml-format", action(this::formatDocument));
    }

    private static javax.swing.AbstractAction action(Runnable r) {
        return new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        };
    }

    private String text() {
        StyledDocument doc = pane.getStyledDocument();
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // リネーム
    // -------------------------------------------------------------------------

    /** キャレット位置の語の新しい名前を尋ね、通れば一括で置き換える。 */
    void renameViaDialog() {
        if (!pane.isEditable()) {
            return;
        }
        String text = text();
        String word = PumlRename.wordAt(text, pane.getCaretPosition());
        if (word.isEmpty()) {
            return;
        }
        int count = PumlRename.occurrences(text, word).size();
        if (count == 0) {
            return;
        }
        Object input = JOptionPane.showInputDialog(pane,
                MessageFormat.format(Messages.get("puml.rename.prompt"), word, count),
                Messages.get("puml.rename.title"), JOptionPane.QUESTION_MESSAGE,
                null, null, word);
        if (input == null) {
            return;
        }
        String newName = input.toString().strip();
        if (newName.isEmpty() || newName.equals(word)) {
            return;
        }
        if (!PumlRename.isValidNewName(newName)) {
            // 黙って無視すると「押したのに何も起きない」になる。弾いた理由を見せる。
            JOptionPane.showMessageDialog(pane, Messages.get("puml.rename.invalid"),
                    Messages.get("puml.rename.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        renameTo(newName);
    }

    /**
     * キャレット位置の語を {@code newName} へ一括で置き換える (ダイアログ抜きの実体)。
     *
     * @return 置き換えた箇所数 (0 なら何もしなかった)
     */
    int renameTo(String newName) {
        if (!pane.isEditable() || !PumlRename.isValidNewName(newName)) {
            return 0;
        }
        String text = text();
        String word = PumlRename.wordAt(text, pane.getCaretPosition());
        if (word.isEmpty() || word.equals(newName)) {
            return 0;
        }
        List<int[]> occ = PumlRename.occurrences(text, word);
        if (occ.isEmpty()) {
            return 0;
        }
        StyledDocument doc = pane.getStyledDocument();
        compound.accept(() -> {
            // 後ろから置き換えて、前方のオフセットがずれないようにする。
            for (int i = occ.size() - 1; i >= 0; i--) {
                int[] r = occ.get(i);
                try {
                    doc.remove(r[0], r[1] - r[0]);
                    doc.insertString(r[0], newName, null);
                } catch (BadLocationException ignored) {
                    // 競合編集で範囲がずれた 1 件は諦める (残りは置き換わる)。
                }
            }
        });
        return occ.size();
    }

    // -------------------------------------------------------------------------
    // クイックフィックス
    // -------------------------------------------------------------------------

    /**
     * キャレット行の「閉じ忘れ」指摘に終端を挿し込む。
     *
     * @return 修正を適用したか
     */
    boolean quickFixAtCaret() {
        if (!pane.isEditable()) {
            return false;
        }
        String text = text();
        int line = pane.getDocument().getDefaultRootElement()
                .getElementIndex(pane.getCaretPosition()) + 1;
        for (PumlDiagnostics.Diagnostic d : PumlDiagnostics.analyze(text)) {
            if (d.line() != line) {
                continue;
            }
            PumlEditorKeys.Edit fix = PumlDiagnostics.closerEdit(text, d);
            if (fix != null) {
                PumlEditorKeys.apply(pane, pane.getStyledDocument(), compound, fix);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // 整形
    // -------------------------------------------------------------------------

    /**
     * 本文全体を再インデントする。変わった行だけを置き換え、キャレットは同じ行に留める。
     *
     * @return 1 行でも変わったか
     */
    boolean formatDocument() {
        if (!pane.isEditable()) {
            return false;
        }
        String text = text();
        String formatted = PumlFormatter.format(text);
        if (formatted.equals(text)) {
            return false;
        }
        String[] before = text.split("\n", -1);
        String[] after = formatted.split("\n", -1);
        StyledDocument doc = pane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        // キャレットは行内の位置ごと再現する (行頭からの桁は字下げ変更ぶんずれてよい)。
        int caretLine = root.getElementIndex(pane.getCaretPosition());
        int caretCol = pane.getCaretPosition() - root.getElement(caretLine).getStartOffset();
        compound.accept(() -> {
            for (int i = Math.min(before.length, after.length) - 1; i >= 0; i--) {
                if (before[i].equals(after[i]) || i >= root.getElementCount()) {
                    continue;
                }
                int start = root.getElement(i).getStartOffset();
                try {
                    doc.remove(start, before[i].length());
                    doc.insertString(start, after[i], null);
                } catch (BadLocationException ignored) {
                    // 行境界がずれた 1 行は諦める (残りの行は整う)。
                }
            }
        });
        Element line = root.getElement(Math.min(caretLine, root.getElementCount() - 1));
        int len = line.getEndOffset() - 1 - line.getStartOffset();
        pane.setCaretPosition(line.getStartOffset() + Math.max(0, Math.min(caretCol, len)));
        return true;
    }
}
