// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.InputMap;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * PlantUML エディタのコード編集キー操作 (VS Code 相当) を提供する。
 *
 * <p>Enter の自動インデント、{@code ( { "} の自動閉じペア、閉じ文字のオーバータイプ、
 * {@code Alt+Up/Down} の行移動、{@code Shift+Alt+Up/Down} の行複製、
 * {@code Ctrl+Shift+K} の行削除を {@link #install(JTextPane, Consumer)} で配線する。</p>
 *
 * <p>編集計算はすべて純関数 ({@link Edit} を返す) として分離し、ヘッドレスでも
 * 単体テストできるようにしている。ドキュメントへの適用と Undo のグルーピングは
 * 呼び出し側から渡される compound ランナー経由で行う。</p>
 */
final class PumlEditorKeys {

    /** インデント 1 段分 (スペース 2 つ)。PumlSourcePanel の Tab インデントと揃える。 */
    static final String INDENT = "  ";

    private PumlEditorKeys() {
    }

    /**
     * テキストの一部置換と、適用後の選択範囲を表す編集指示。
     * {@code start..end} を {@code replacement} で置き換え、選択を
     * {@code selStart..selEnd} にする (キャレットは selEnd)。
     */
    static final class Edit {
        final int start;
        final int end;
        final String replacement;
        final int selStart;
        final int selEnd;

        Edit(int start, int end, String replacement, int selStart, int selEnd) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
            this.selStart = selStart;
            this.selEnd = selEnd;
        }
    }

    // -------------------------------------------------------------------------
    // 純ロジック: 行ユーティリティ
    // -------------------------------------------------------------------------

    /** {@code pos} を含む行の先頭オフセット。 */
    static int lineStart(String text, int pos) {
        int p = Math.max(0, Math.min(pos, text.length()));
        int nl = text.lastIndexOf('\n', p - 1);
        return nl < 0 ? 0 : nl + 1;
    }

    /** {@code pos} を含む行の終端 (改行を含む排他端。最終行は text.length())。 */
    static int lineEndExclusive(String text, int pos) {
        int p = Math.max(0, Math.min(pos, text.length()));
        int nl = text.indexOf('\n', p);
        return nl < 0 ? text.length() : nl + 1;
    }

    /** 行テキストの先頭空白 (インデント) 部分を返す。 */
    static String indentOf(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return line.substring(0, i);
    }

    /**
     * この行の直後を 1 段深くインデントすべきブロック開始行か。
     * {@code {} で終わる行 (クラス本体・package 等) と、シーケンス/アクティビティの
     * ブロックキーワード行 (alt/else/opt/loop/group/par/break/critical/if/while/
     * fork/split/repeat/box) を対象にする。
     */
    static boolean opensBlock(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("'")) {
            return false;
        }
        if (t.endsWith("{")) {
            return true;
        }
        String head = t;
        int sp = head.indexOf(' ');
        if (sp > 0) {
            head = head.substring(0, sp);
        }
        switch (head) {
            case "alt":
            case "else":
            case "opt":
            case "loop":
            case "group":
            case "par":
            case "break":
            case "critical":
            case "if":
            case "while":
            case "fork":
            case "split":
            case "repeat":
            case "box":
                return true;
            default:
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // 純ロジック: Enter 自動インデント
    // -------------------------------------------------------------------------

    /**
     * Enter キーの挿入内容を組み立てる。現在行のインデントを維持し、ブロック開始行の
     * 直後は 1 段深くする。キャレットが {@code {} と {@code }} の間にある場合は
     * 閉じ括弧を次々行へ送り、中間行へキャレットを置く (スマート展開)。
     */
    static Edit newlineAt(String text, int caret) {
        int ls = lineStart(text, caret);
        String beforeCaret = text.substring(ls, Math.min(caret, text.length()));
        String indent = indentOf(beforeCaret);
        boolean deeper = opensBlock(beforeCaret);
        char prev = caret > 0 ? text.charAt(caret - 1) : '\0';
        char next = caret < text.length() ? text.charAt(caret) : '\0';
        if (prev == '{' && next == '}') {
            String insert = "\n" + indent + INDENT + "\n" + indent;
            int at = caret + 1 + indent.length() + INDENT.length();
            return new Edit(caret, caret, insert, at, at);
        }
        String insert = "\n" + indent + (deeper ? INDENT : "");
        int at = caret + insert.length();
        return new Edit(caret, caret, insert, at, at);
    }

    // -------------------------------------------------------------------------
    // 純ロジック: 自動閉じペア / オーバータイプ
    // -------------------------------------------------------------------------

    /** {@code open} に対応する閉じ文字 (対象外は '\0')。 */
    static char closingFor(char open) {
        switch (open) {
            case '(': return ')';
            case '{': return '}';
            case '"': return '"';
            default:  return '\0';
        }
    }

    /**
     * 開き文字のタイプ: 直後が行末・空白・閉じ括弧なら閉じ文字とペアで挿入して
     * 間にキャレットを置く。そうでなければ 1 文字だけ挿入する。
     * 引用符は直後が同じ引用符ならオーバータイプ (挿入せず通過) し、行内に
     * 閉じられていない引用符があるとき (= 閉じの入力) は単独で挿入する。
     */
    static Edit typedOpen(String text, int caret, char open) {
        char close = closingFor(open);
        char next = caret < text.length() ? text.charAt(caret) : '\n';
        if (open == '"') {
            if (next == '"') {
                return new Edit(caret, caret, "", caret + 1, caret + 1);
            }
            // 行内でキャレットより前の引用符が奇数個 = 開いたまま。この入力は
            // 「閉じ」なのでペア挿入せず 1 文字だけ入れる (`"label` + `"` → `"label"`)。
            int count = 0;
            for (int i = lineStart(text, caret); i < caret; i++) {
                if (text.charAt(i) == '"') {
                    count++;
                }
            }
            if (count % 2 == 1) {
                return new Edit(caret, caret, "\"", caret + 1, caret + 1);
            }
        }
        boolean pair = next == '\n' || next == ' ' || next == '\t'
                || next == ')' || next == '}' || next == ']';
        if (pair) {
            return new Edit(caret, caret, "" + open + close, caret + 1, caret + 1);
        }
        return new Edit(caret, caret, String.valueOf(open), caret + 1, caret + 1);
    }

    // -------------------------------------------------------------------------
    // 純ロジック: 選択範囲を考慮した入力 (既定エディタの「選択置換」挙動を保つ)
    // -------------------------------------------------------------------------

    /** Enter: 選択があれば選択を削除してから自動インデント改行を挿入する。 */
    static Edit newlineFor(String text, int selStart, int selEnd) {
        if (selEnd > selStart) {
            String remaining = text.substring(0, selStart) + text.substring(selEnd);
            Edit e = newlineAt(remaining, selStart);
            return new Edit(selStart, selEnd, e.replacement, e.selStart, e.selEnd);
        }
        return newlineAt(text, selEnd);
    }

    /** 開き文字: 選択があれば選択テキストを対で囲む (VS Code の surround 挙動)。 */
    static Edit typedOpenFor(String text, int selStart, int selEnd, char open) {
        if (selEnd > selStart) {
            String inner = text.substring(selStart, selEnd);
            return new Edit(selStart, selEnd, open + inner + closingFor(open),
                    selStart + 1, selEnd + 1);
        }
        return typedOpen(text, selEnd, open);
    }

    /** 閉じ文字: 選択があれば選択を置換して挿入する。 */
    static Edit typedCloseFor(String text, int selStart, int selEnd, char close) {
        if (selEnd > selStart) {
            return new Edit(selStart, selEnd, String.valueOf(close),
                    selStart + 1, selStart + 1);
        }
        return typedClose(text, selEnd, close);
    }

    /** 閉じ文字のタイプ: 直後が同じ閉じ文字ならオーバータイプ、そうでなければ挿入。 */
    static Edit typedClose(String text, int caret, char close) {
        char next = caret < text.length() ? text.charAt(caret) : '\0';
        if (next == close) {
            return new Edit(caret, caret, "", caret + 1, caret + 1);
        }
        return new Edit(caret, caret, String.valueOf(close), caret + 1, caret + 1);
    }

    // -------------------------------------------------------------------------
    // 純ロジック: 行の移動 / 複製 / 削除
    // -------------------------------------------------------------------------

    /** 選択範囲にかかる行ブロック [start, endExclusive) を返す。 */
    private static int[] blockOf(String text, int selStart, int selEnd) {
        int a = Math.min(selStart, selEnd);
        int b = Math.max(selStart, selEnd);
        // 選択が次行の行頭ちょうどで終わる場合、その行は対象に含めない
        // (PumlSourcePanel のインデント/コメント切替と同じ規約)。
        if (b > a && b == lineStart(text, b)) {
            b--;
        }
        return new int[]{lineStart(text, a), lineEndExclusive(text, b)};
    }

    /**
     * 選択行ブロックを 1 行上/下と入れ替える。移動できない (先頭/末尾) 場合は null。
     * 選択範囲は移動後のブロックへ追従する。
     */
    static Edit moveLines(String text, int selStart, int selEnd, boolean up) {
        int[] block = blockOf(text, selStart, selEnd);
        int blockStart = block[0];
        int blockEnd = block[1];
        int a = Math.min(selStart, selEnd);
        int b = Math.max(selStart, selEnd);
        if (up) {
            if (blockStart == 0) {
                return null;
            }
            int prevStart = lineStart(text, blockStart - 1);
            String region = text.substring(prevStart, blockEnd);
            String rearranged = rotate(region, blockStart - prevStart);
            // ブロックは領域先頭 (prevStart) へ移る: 選択は前行の長さぶん平行移動する。
            int delta = prevStart - blockStart;
            int ns = clamp(a + delta, prevStart, prevStart + rearranged.length());
            int ne = clamp(b + delta, prevStart, prevStart + rearranged.length());
            return new Edit(prevStart, blockEnd, rearranged, ns, ne);
        }
        if (blockEnd >= text.length()) {
            return null;
        }
        int nextEnd = lineEndExclusive(text, blockEnd);
        String region = text.substring(blockStart, nextEnd);
        String rearranged = rotate(region, blockEnd - blockStart);
        // 移動後にブロックの前へ来る次行の長さ (EOF 行には改行が付与されるので +1)。
        int placedNextLen = (nextEnd - blockEnd) + (region.endsWith("\n") ? 0 : 1);
        int ns = clamp(a + placedNextLen, blockStart, blockStart + rearranged.length());
        int ne = clamp(b + placedNextLen, blockStart, blockStart + rearranged.length());
        return new Edit(blockStart, nextEnd, rearranged, ns, ne);
    }

    /**
     * 領域テキストの先頭 {@code headLen} 文字 (前半部) と残り (後半部) を入れ替える。
     * 領域末尾に改行が無い場合 (EOF)、入れ替え後も改行位置が行区切りとして正しくなるよう
     * 補正する (末尾改行なしを維持し、内部区切りに改行を置く)。
     */
    private static String rotate(String region, int headLen) {
        String head = region.substring(0, headLen);
        String tail = region.substring(headLen);
        boolean regionEndsWithNl = region.endsWith("\n");
        // head は常に改行で終わる (行境界で切っているため)。tail が EOF 行のときのみ
        // 改行なしで終わる。入れ替え後: tail が前へ来るので改行を付け、head の
        // 末尾改行を外して EOF 側へ置く。
        if (!regionEndsWithNl) {
            String headCore = head.endsWith("\n") ? head.substring(0, head.length() - 1) : head;
            return tail + "\n" + headCore;
        }
        return tail + head;
    }

    /** 選択行ブロックを直下へ複製し、選択を複製側へ移す。 */
    static Edit duplicateLines(String text, int selStart, int selEnd) {
        int[] block = blockOf(text, selStart, selEnd);
        int blockStart = block[0];
        int blockEnd = block[1];
        String blockText = text.substring(blockStart, blockEnd);
        int a = Math.min(selStart, selEnd);
        int b = Math.max(selStart, selEnd);
        String insert;
        int copyStart;
        if (blockText.endsWith("\n")) {
            insert = blockText;
            copyStart = blockEnd;
        } else {
            // EOF 行: 改行を挟んで複製する。
            insert = "\n" + blockText;
            copyStart = blockEnd + 1;
        }
        int ns = copyStart + (a - blockStart);
        int ne = copyStart + (b - blockStart);
        return new Edit(blockEnd, blockEnd, insert, ns, ne);
    }

    /** 選択行ブロックを削除する。EOF 行の削除は直前の改行も取り除く。 */
    static Edit deleteLines(String text, int selStart, int selEnd) {
        int[] block = blockOf(text, selStart, selEnd);
        int blockStart = block[0];
        int blockEnd = block[1];
        int removeStart = blockStart;
        if (blockEnd >= text.length() && !text.substring(blockStart, blockEnd).endsWith("\n")
                && blockStart > 0) {
            removeStart = blockStart - 1; // 直前の改行ごと消して末尾の空行を残さない
        }
        int caret = Math.min(removeStart, Math.max(0, text.length() - (blockEnd - removeStart)));
        return new Edit(removeStart, blockEnd, "", caret, caret);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // -------------------------------------------------------------------------
    // キー配線
    // -------------------------------------------------------------------------

    /**
     * 編集キーを {@code pane} へ配線する。{@code compound} は一連のドキュメント編集を
     * 1 個の Undo にまとめて実行するランナー (PumlSourcePanel#runAsCompound)。
     */
    static void install(JTextPane pane, Consumer<Runnable> compound) {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap im = pane.getInputMap();
        javax.swing.ActionMap am = pane.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "juml-newline");
        am.put("juml-newline", act(pane, compound,
                (text, sel) -> newlineFor(text, sel[0], sel[1])));

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK),
                "juml-line-up");
        am.put("juml-line-up", act(pane, compound,
                (text, sel) -> moveLines(text, sel[0], sel[1], true)));
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK),
                "juml-line-down");
        am.put("juml-line-down", act(pane, compound,
                (text, sel) -> moveLines(text, sel[0], sel[1], false)));

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,
                InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "juml-line-dup");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,
                InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "juml-line-dup");
        am.put("juml-line-dup", act(pane, compound,
                (text, sel) -> duplicateLines(text, sel[0], sel[1])));

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_K,
                menuMask | InputEvent.SHIFT_DOWN_MASK), "juml-line-del");
        am.put("juml-line-del", act(pane, compound,
                (text, sel) -> deleteLines(text, sel[0], sel[1])));

        // 自動閉じペア: 開き文字はペア挿入、閉じ文字はオーバータイプ。
        bindTyped(pane, im, am, compound, '(', "juml-open-paren", true);
        bindTyped(pane, im, am, compound, '{', "juml-open-brace", true);
        bindTyped(pane, im, am, compound, '"', "juml-open-quote", true);
        bindTyped(pane, im, am, compound, ')', "juml-close-paren", false);
        bindTyped(pane, im, am, compound, '}', "juml-close-brace", false);
    }

    private static void bindTyped(JTextPane pane, InputMap im, javax.swing.ActionMap am,
                                  Consumer<Runnable> compound, char ch, String key,
                                  boolean opening) {
        im.put(KeyStroke.getKeyStroke(ch), key);
        am.put(key, act(pane, compound, (text, sel) -> opening
                ? typedOpenFor(text, sel[0], sel[1], ch)
                : typedCloseFor(text, sel[0], sel[1], ch)));
    }

    /** 編集計算 (text, {selStart, caret}) → Edit。null なら何もしない。 */
    private interface EditFn {
        Edit compute(String text, int[] selection);
    }

    private static javax.swing.AbstractAction act(JTextPane pane, Consumer<Runnable> compound,
                                                  EditFn fn) {
        return new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!pane.isEditable()) {
                    return;
                }
                StyledDocument doc = pane.getStyledDocument();
                String text;
                try {
                    text = doc.getText(0, doc.getLength());
                } catch (BadLocationException ex) {
                    return;
                }
                int selStart = Math.min(pane.getSelectionStart(), pane.getSelectionEnd());
                int selEnd = Math.max(pane.getSelectionStart(), pane.getSelectionEnd());
                Edit edit = fn.compute(text, new int[]{selStart, selEnd});
                if (edit == null) {
                    return;
                }
                apply(pane, doc, compound, edit);
            }
        };
    }

    /** 編集指示をドキュメントへ適用し、選択範囲を復元する (1 手で戻せる)。 */
    static void apply(JTextPane pane, StyledDocument doc,
                      Consumer<Runnable> compound, Edit edit) {
        compound.accept(() -> {
            try {
                if (edit.end > edit.start) {
                    doc.remove(edit.start, edit.end - edit.start);
                }
                if (!edit.replacement.isEmpty()) {
                    doc.insertString(edit.start, edit.replacement, null);
                }
            } catch (BadLocationException ignored) {
                // 競合編集などで範囲がずれた場合は何もしない (致命的でない)。
            }
        });
        int len = doc.getLength();
        pane.select(Math.max(0, Math.min(edit.selStart, len)),
                Math.max(0, Math.min(edit.selEnd, len)));
    }
}
