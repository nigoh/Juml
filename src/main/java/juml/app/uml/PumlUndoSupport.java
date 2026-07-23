// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * PlantUML エディタ ({@link PumlSourcePanel}) の Undo/Redo サブシステム。
 *
 * <p>JTextPane は既定で undo を持たないため、ここで最低限の取り消し操作を足す。3 段構えで
 * 「まともに使える」粒度にする:</p>
 * <ul>
 *   <li>シンタックスハイライトの属性変更 (CHANGE) は undo 対象から除外する。</li>
 *   <li>コメント切替・インデント等の複数行操作は {@link #runAsCompound} で 1 手に束ねる。</li>
 *   <li>素の連続タイプ (1 文字ずつの挿入/削除) も「直前のタイプ塊」を 1 手に束ねる
 *       (VS Code 相当)。時間ギャップ・向きの変化・非連続オフセット・改行で区切る。</li>
 * </ul>
 */
final class PumlUndoSupport {

    /** 連続タイプを 1 手に束ねる最大間隔 (ms)。これを超える停止で区切る。 */
    private static final long TYPING_COALESCE_MS = 600;

    private final JTextPane textPane;
    private UndoManager undoManager;
    /** コメント切替・インデントを 1 手に束ねる複合編集 (null 以外の間はここへ束ねる)。 */
    private CompoundEdit activeCompound;
    /** 連続タイプ (1 文字ずつ) を 1 手に束ねる複合編集。 */
    private CompoundEdit typingBatch;
    /** 連続とみなす次の編集オフセット (挿入は末尾、削除は現在位置)。 */
    private int typingNextOffset;
    /** 直近のタイプ編集時刻 (ms)。この間隔を超えたら別の手として区切る。 */
    private long typingLastMs;
    /** 現在のタイプ・バッチの向き (true=挿入 / false=削除)。向きが変わったら区切る。 */
    private boolean typingInsert;

    PumlUndoSupport(JTextPane textPane) {
        this.textPane = textPane;
    }

    /** undo/redo がすでに配線済みか (多重インストール防止用)。 */
    boolean isInstalled() {
        return undoManager != null;
    }

    /**
     * Ctrl(⌘)+Z / Ctrl(⌘)+Y / Ctrl(⌘)+Shift+Z の undo/redo を配線する。多重呼び出しは無視。
     */
    void install() {
        if (undoManager != null) {
            return;
        }
        undoManager = new UndoManager();
        undoManager.setLimit(500);
        textPane.getDocument().addUndoableEditListener(e -> onEdit(e.getEdit()));
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        InputMap im = textPane.getInputMap();
        ActionMap am = textPane.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "juml-undo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask), "juml-redo");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK),
                "juml-redo");
        am.put("juml-undo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                undo();
            }
        });
        am.put("juml-redo", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                redo();
            }
        });
    }

    private void onEdit(UndoableEdit edit) {
        if (edit instanceof AbstractDocument.DefaultDocumentEvent
                && ((AbstractDocument.DefaultDocumentEvent) edit).getType()
                    == DocumentEvent.EventType.CHANGE) {
            return; // 属性変更 (ハイライト) は取り消し対象にしない
        }
        // コメント切替・インデント中はまとめて 1 手にする。
        if (activeCompound != null) {
            activeCompound.addEdit(edit);
            return;
        }
        // 連続タイプ (1 文字ずつ) は「直前のタイプ塊」を 1 手に束ねる (VS Code 相当)。
        if (edit instanceof AbstractDocument.DefaultDocumentEvent) {
            coalesceTypingEdit((AbstractDocument.DefaultDocumentEvent) edit);
        } else {
            flushTyping();
            undoManager.addEdit(edit);
        }
    }

    /** 進行中のタイプ塊を確定してから 1 手戻す。 */
    void undo() {
        flushTyping();
        if (undoManager != null && undoManager.canUndo()) {
            undoManager.undo();
        }
    }

    /** 進行中のタイプ塊を確定してから 1 手やり直す。 */
    void redo() {
        flushTyping();
        if (undoManager != null && undoManager.canRedo()) {
            undoManager.redo();
        }
    }

    /**
     * {@code mutation} 中のドキュメント編集を 1 回の Undo で戻せるよう束ねる。undo 未装備
     * (リードオンリー) の場合はそのまま実行する。
     */
    void runAsCompound(Runnable mutation) {
        if (undoManager == null) {
            mutation.run();
            return;
        }
        flushTyping(); // 進行中のタイプ塊は明示複合編集 (インデント等) と別の手にする
        activeCompound = new CompoundEdit();
        try {
            mutation.run();
        } finally {
            CompoundEdit ce = activeCompound;
            activeCompound = null;
            ce.end();
            if (ce.canUndo()) {
                undoManager.addEdit(ce);
            }
        }
    }

    /** 全文差し替え時に履歴を破棄する。進行中のタイプ塊も無効化する。 */
    void discardAll() {
        if (undoManager != null) {
            undoManager.discardAllEdits();
        }
        typingBatch = null;
    }

    /**
     * 連続タイプ (1 文字ずつの挿入/削除) を {@link #typingBatch} に束ねる。同じ向き・連続
     * オフセット・{@link #TYPING_COALESCE_MS} 以内・単一文字で、かつ改行でなければ直前の
     * バッチへ足す。それ以外は現在のバッチを確定して新しい手を始める。
     */
    private void coalesceTypingEdit(AbstractDocument.DefaultDocumentEvent e) {
        boolean insert = e.getType() == DocumentEvent.EventType.INSERT;
        int offset = e.getOffset();
        int len = e.getLength();
        long now = System.currentTimeMillis();
        boolean singleChar = len == 1;
        boolean newline = insert && singleChar && isNewlineInsert(offset);
        // 挿入は末尾に連続、削除は同位置 (前方 Delete) か 1 つ前 (後退 Backspace) を連続とみなす。
        boolean contiguous = insert
                ? offset == typingNextOffset
                : (offset == typingNextOffset || offset + 1 == typingNextOffset);
        boolean continues = typingBatch != null && singleChar && !newline
                && insert == typingInsert
                && now - typingLastMs <= TYPING_COALESCE_MS
                && contiguous;
        if (continues) {
            typingBatch.addEdit(e);
        } else {
            flushTyping();
            if (singleChar && !newline) {
                typingBatch = new CompoundEdit();
                typingBatch.addEdit(e);
                typingInsert = insert;
            } else {
                // 複数文字 (貼り付け・補完・スニペット) や改行は単独の 1 手にする。
                undoManager.addEdit(e);
            }
        }
        typingNextOffset = insert ? offset + len : offset;
        typingLastMs = now;
    }

    /** 進行中のタイプ・バッチがあれば確定して UndoManager へ 1 単位として渡す。 */
    void flushTyping() {
        if (typingBatch != null) {
            typingBatch.end();
            if (typingBatch.canUndo()) {
                undoManager.addEdit(typingBatch);
            }
            typingBatch = null;
        }
    }

    /** 指定位置に挿入された 1 文字が改行か (改行はタイプ・バッチの区切りにする)。 */
    private boolean isNewlineInsert(int offset) {
        try {
            return "\n".equals(textPane.getDocument().getText(offset, 1));
        } catch (BadLocationException ex) {
            return false;
        }
    }
}
