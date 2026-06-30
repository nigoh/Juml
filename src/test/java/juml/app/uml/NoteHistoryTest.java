// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link NoteHistory} の Undo/Redo・上限破棄・coalesce まとめロジックを検証する。
 *
 * <p>{@link NotesSnapshot} はインスタンス同一性 (identity) だけで判別できれば十分なので、
 * 中身は空リストの別インスタンスを使う。Swing 依存がなくヘッドレスで完結する。</p>
 */
public class NoteHistoryTest {

    private static NotesSnapshot snap() {
        return new NotesSnapshot(Collections.emptyList(), Collections.emptyList());
    }

    @Test
    public void pushUndoRedo_roundTrip() {
        NoteHistory h = new NoteHistory(10);
        NotesSnapshot before = snap();
        NotesSnapshot current = snap();
        h.push(before);

        NotesSnapshot restored = h.undo(current);
        assertSame("undo は直前に push した変更前スナップショットを返すこと", before, restored);

        NotesSnapshot redone = h.redo(before);
        assertSame("redo は undo 時に渡した current を返すこと", current, redone);
    }

    @Test
    public void undo_onEmptyHistory_returnsNull() {
        NoteHistory h = new NoteHistory(10);
        assertNull("履歴が空なら undo は null", h.undo(snap()));
    }

    @Test
    public void redo_onEmptyHistory_returnsNull() {
        NoteHistory h = new NoteHistory(10);
        assertNull("redo スタックが空なら null", h.redo(snap()));
    }

    @Test
    public void push_clearsRedoStack() {
        NoteHistory h = new NoteHistory(10);
        h.push(snap());
        h.undo(snap());        // redo に 1 件積まれる
        h.push(snap());        // 新規 push で redo はクリアされる
        assertNull("新規 push 後は redo できないこと", h.redo(snap()));
    }

    @Test
    public void push_dropsOldestBeyondLimit() {
        NoteHistory h = new NoteHistory(3);
        for (int i = 0; i < 4; i++) {
            h.push(snap());
        }
        // 上限 3 なので最古 1 件は破棄され、undo は 3 回だけ成功する
        int undone = 0;
        while (h.undo(snap()) != null) {
            undone++;
        }
        assertEquals("上限を超えた最古の履歴は破棄されること", 3, undone);
    }

    @Test
    public void beginEdit_coalescesConsecutiveSameKey() {
        NoteHistory h = new NoteHistory(10);
        assertTrue("初回の編集は push すべき", h.beginEdit("move"));
        assertFalse("同じ coalesce キーが続く間はまとめる (push しない)", h.beginEdit("move"));
        assertTrue("キーが変われば push すべき", h.beginEdit("resize"));
        assertTrue("null キーは常に独立操作として push すべき", h.beginEdit(null));
        assertTrue("null が続いても常に push すべき", h.beginEdit(null));
    }

    @Test
    public void resetCoalesce_forcesNextEditToPush() {
        NoteHistory h = new NoteHistory(10);
        assertTrue(h.beginEdit("move"));
        h.resetCoalesce();
        assertTrue("resetCoalesce 後は同じキーでも push すべき", h.beginEdit("move"));
    }

    @Test
    public void clear_emptiesBothStacks() {
        NoteHistory h = new NoteHistory(10);
        h.push(snap());
        h.undo(snap());
        h.clear();
        assertNull("clear 後は undo できないこと", h.undo(snap()));
        assertNull("clear 後は redo できないこと", h.redo(snap()));
    }
}
