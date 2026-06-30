// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 付箋編集の Undo/Redo 履歴。各エントリは付箋 + コネクタの {@link NotesSnapshot} で、
 * スナップショットの取得/適用は呼び出し側 ({@link DiagramNotesLayer}) が担う。
 *
 * <p>連続操作 (矢印キーの連続移動など) を 1 つの Undo にまとめるための
 * coalesce キーを保持する。{@link #beginEdit} が「今回 push すべきか」を返し、
 * 同じキーが続く間は履歴を増やさない。</p>
 */
final class NoteHistory {

    private final Deque<NotesSnapshot> undo = new ArrayDeque<>();
    private final Deque<NotesSnapshot> redo = new ArrayDeque<>();
    private final int limit;
    /** 連続操作をまとめるためのキー。null は独立操作。 */
    private String lastCoalesce;

    NoteHistory(int limit) {
        this.limit = limit;
    }

    /**
     * 編集開始を通知し、新たに履歴へ積むべきかを返す。
     * {@code coalesce} が直前と同じなら false (まとめる)、null または異なれば true。
     */
    boolean beginEdit(String coalesce) {
        boolean push = coalesce == null || !coalesce.equals(lastCoalesce);
        lastCoalesce = coalesce;
        return push;
    }

    /** 変更前スナップショットを Undo に積む (Redo はクリア、上限超過分は古い順に破棄)。 */
    void push(NotesSnapshot snapshot) {
        undo.push(snapshot);
        while (undo.size() > limit) {
            undo.removeLast();
        }
        redo.clear();
    }

    /** ドラッグ確定など、{@link #beginEdit} を介さず push した後の連結状態をリセットする。 */
    void resetCoalesce() {
        lastCoalesce = null;
    }

    /**
     * 取り消す。{@code current} を Redo に積み、復元すべきスナップショットを返す。
     * 履歴が無ければ null。
     */
    NotesSnapshot undo(NotesSnapshot current) {
        if (undo.isEmpty()) {
            return null;
        }
        redo.push(current);
        lastCoalesce = null;
        return undo.pop();
    }

    /**
     * やり直す。{@code current} を Undo に積み、復元すべきスナップショットを返す。
     * 履歴が無ければ null。
     */
    NotesSnapshot redo(NotesSnapshot current) {
        if (redo.isEmpty()) {
            return null;
        }
        undo.push(current);
        lastCoalesce = null;
        return redo.pop();
    }

    /** 履歴を全消去する (図のロード時など)。 */
    void clear() {
        undo.clear();
        redo.clear();
        lastCoalesce = null;
    }
}
