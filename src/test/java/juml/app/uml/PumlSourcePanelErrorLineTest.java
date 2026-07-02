// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link PumlSourcePanel#editorLineForError(int, int)} の行番号写像 (純関数, headless)。
 *
 * <p>PlantUML は prelude (!pragma layout / skinparam 等) を {@code @startuml} 直後へ
 * 挿入した「生成ソース」の行番号を返すため、エディタ上の行へ戻すには挿入行数を引く。
 * 実測: エディタ行 3 の構文エラーが、挿入 2 行を含む生成ソースでは行 5 と報告される。</p>
 */
public class PumlSourcePanelErrorLineTest {

    @Test
    public void subtractsInjectedLines() {
        // 生成ソースの行 5 - 挿入 2 行 = エディタ行 3。
        assertEquals(3, PumlSourcePanel.editorLineForError(5, 2));
    }

    @Test
    public void startumlLineIsUnchanged() {
        assertEquals(1, PumlSourcePanel.editorLineForError(1, 2));
    }

    @Test
    public void noInjectionKeepsLineNumber() {
        assertEquals(4, PumlSourcePanel.editorLineForError(4, 0));
    }

    @Test
    public void clampsToFirstLineWhenErrorFallsInInjectedPrelude() {
        assertEquals(1, PumlSourcePanel.editorLineForError(2, 5));
    }
}
