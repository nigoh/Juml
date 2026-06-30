// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.List;

/**
 * 付箋とコネクタの一括スナップショット。{@link NoteHistory} の Undo/Redo 単位。
 * 中身は呼び出し側 ({@link DiagramNotesLayer}) がディープコピーして渡す。
 */
final class NotesSnapshot {

    final List<DiagramNote> notes;
    final List<DiagramConnector> connectors;

    NotesSnapshot(List<DiagramNote> notes, List<DiagramConnector> connectors) {
        this.notes = notes;
        this.connectors = connectors;
    }
}
