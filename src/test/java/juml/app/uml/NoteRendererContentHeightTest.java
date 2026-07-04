// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link NoteRenderer#contentHeight} の一貫性テスト (#39)。
 *
 * <p>必要高さ (完全レイアウト結果) を (幅, テキスト) 単位でキャッシュするようにしたので、
 * 同一入力での繰り返し呼び出しが同じ値を返し (キャッシュヒットでも計測結果と一致)、
 * テキスト/幅を変えると値が更新されることを検証する。JEditorPane の生成・計測は
 * headless でも動くため Display 依存の Assume は不要。</p>
 */
public class NoteRendererContentHeightTest {

    private static DiagramNote note(String text, double w, double h) {
        return new DiagramNote(0, 0, w, h, text);
    }

    @Test
    public void repeatedCallsReturnSameHeight() {
        NoteRenderer r = new NoteRenderer();
        DiagramNote n = note("line one\n\nline two\n\nline three", 120, 40);
        double first = r.contentHeight(n);
        double second = r.contentHeight(n);
        double third = r.contentHeight(n);
        assertEquals("同一 (幅, テキスト) の再計算はキャッシュヒットで同値", first, second, 0.0001);
        assertEquals(first, third, 0.0001);
        assertTrue("複数行の本文は正の高さになる", first > 0);
    }

    @Test
    public void editingTextUpdatesHeight() {
        NoteRenderer r = new NoteRenderer();
        DiagramNote n = note("short", 120, 40);
        double shortH = r.contentHeight(n);
        n.setText("much longer text\n\nwith several\n\nparagraphs here\n\nto force wrapping");
        double longH = r.contentHeight(n);
        assertTrue("本文を増やすと必要高さは増える (キャッシュがキーで無効化される): "
                + shortH + " -> " + longH, longH > shortH);
    }

    @Test
    public void emptyTextFallsBackToNoteHeight() {
        NoteRenderer r = new NoteRenderer();
        DiagramNote n = note("   ", 120, 55);
        assertEquals("空本文は付箋高さをそのまま返す", 55, r.contentHeight(n), 0.0001);
    }
}
