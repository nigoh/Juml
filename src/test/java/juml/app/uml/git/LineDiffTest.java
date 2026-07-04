// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.LineDiff.Row;
import juml.app.uml.git.LineDiff.Type;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link LineDiff} の行整列を純ロジックとして手厚く検証する (Swing 非依存)。
 */
public class LineDiffTest {

    private static long count(List<Row> rows, Type t) {
        return rows.stream().filter(r -> r.type == t).count();
    }

    @Test
    public void toLines_dropsTrailingNewlineButKeepsBlankMiddle() {
        assertEquals(List.of("a", "", "b"), LineDiff.toLines("a\n\nb\n"));
        assertEquals(List.of("a"), LineDiff.toLines("a"));
        assertTrue(LineDiff.toLines("").isEmpty());
        assertTrue(LineDiff.toLines(null).isEmpty());
    }

    @Test
    public void toLines_stripsCarriageReturn() {
        assertEquals(List.of("a", "b"), LineDiff.toLines("a\r\nb\r\n"));
    }

    @Test
    public void identical_allEqualRowsInOrder() {
        List<Row> rows = LineDiff.compute("a\nb\nc\n", "a\nb\nc\n");
        assertEquals(3, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            assertEquals(Type.EQUAL, r.type);
            assertEquals(i + 1, r.oldLine);
            assertEquals(i + 1, r.newLine);
            assertEquals(r.oldText, r.newText);
        }
    }

    @Test
    public void pureAddition_leftEmptyRightAdded() {
        List<Row> rows = LineDiff.compute("a\nb\n", "a\nx\ny\nb\n");
        assertEquals(2, count(rows, Type.ADDED));
        assertEquals(2, count(rows, Type.EQUAL));
        assertEquals(0, count(rows, Type.REMOVED));
        for (Row r : rows) {
            if (r.type == Type.ADDED) {
                assertEquals(-1, r.oldLine);
                assertNull(r.oldText);
                assertTrue(r.newLine > 0);
            }
        }
    }

    @Test
    public void pureDeletion_rightEmptyLeftRemoved() {
        List<Row> rows = LineDiff.compute("a\nx\ny\nb\n", "a\nb\n");
        assertEquals(2, count(rows, Type.REMOVED));
        assertEquals(2, count(rows, Type.EQUAL));
        assertEquals(0, count(rows, Type.ADDED));
        for (Row r : rows) {
            if (r.type == Type.REMOVED) {
                assertEquals(-1, r.newLine);
                assertNull(r.newText);
                assertTrue(r.oldLine > 0);
            }
        }
    }

    @Test
    public void replacedLine_pairsAsModified() {
        // 中央の 1 行だけ差し替え → EQUAL, MODIFIED, EQUAL に整列する。
        List<Row> rows = LineDiff.compute("a\nB\nc\n", "a\nX\nc\n");
        assertEquals(3, rows.size());
        assertEquals(Type.EQUAL, rows.get(0).type);
        assertEquals(Type.MODIFIED, rows.get(1).type);
        assertEquals("B", rows.get(1).oldText);
        assertEquals("X", rows.get(1).newText);
        assertEquals(2, rows.get(1).oldLine);
        assertEquals(2, rows.get(1).newLine);
        assertEquals(Type.EQUAL, rows.get(2).type);
    }

    @Test
    public void unevenBlock_pairsThenRemainder() {
        // 2 行削除 + 3 行追加 → 2 つ MODIFIED、1 つ ADDED。
        List<Row> rows = LineDiff.compute("a\nB\nC\nd\n", "a\nX\nY\nZ\nd\n");
        assertEquals(2, count(rows, Type.MODIFIED));
        assertEquals(1, count(rows, Type.ADDED));
        assertEquals(0, count(rows, Type.REMOVED));
        assertEquals(2, count(rows, Type.EQUAL));
    }

    @Test
    public void fromToEmpty_areAllAddedOrRemoved() {
        assertEquals(3, count(LineDiff.compute("", "a\nb\nc\n"), Type.ADDED));
        assertEquals(3, count(LineDiff.compute("a\nb\nc\n", ""), Type.REMOVED));
        assertTrue(LineDiff.compute("", "").isEmpty());
    }

    @Test
    public void oldAndNewLineNumbersAreMonotonic() {
        List<Row> rows = LineDiff.compute("a\nb\nc\nd\ne\n", "a\nc\nX\nd\ne\nf\n");
        int lastOld = 0;
        int lastNew = 0;
        for (Row r : rows) {
            if (r.oldLine > 0) {
                assertTrue("old 行番号は単調増加", r.oldLine > lastOld);
                lastOld = r.oldLine;
            }
            if (r.newLine > 0) {
                assertTrue("new 行番号は単調増加", r.newLine > lastNew);
                lastNew = r.newLine;
            }
        }
    }

    @Test
    public void equalRowsPreserveEveryCommonLineExactlyOnce() {
        // EQUAL 行の旧テキストを順に並べると、共通部分列に一致するはず。
        List<Row> rows = LineDiff.compute("x\na\ny\nb\n", "a\nb\nz\n");
        StringBuilder common = new StringBuilder();
        for (Row r : rows) {
            if (r.type == Type.EQUAL) {
                common.append(r.oldText);
            }
        }
        assertEquals("ab", common.toString());
    }
}
