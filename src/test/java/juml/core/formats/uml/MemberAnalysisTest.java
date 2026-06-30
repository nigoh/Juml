// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MemberAnalysisTest {

    private static final String SRC =
            "package p;"
            + " public class Calc {"
            + "   public int fact(int n) {"
            + "     if (n <= 1) { return 1; }"
            + "     return n * fact(n - 1);"
            + "   }"
            + "   public int sum(java.util.List<Integer> xs) {"
            + "     int t = 0;"
            + "     for (Integer x : xs) { t = t + x; }"
            + "     try { t = t / xs.size(); } catch (ArithmeticException e) { t = 0; }"
            + "     return t;"
            + "   }"
            + "   public int getValue() { return value; }"
            + "   public void setValue(int v) { this.value = v; }"
            + "   private int value;"
            + " }";

    @Test
    public void rows_controlFlowAndRecursionMetrics() {
        List<String[]> rows = MemberAnalysis.rows(JavaStructureExtractor.extract(SRC));

        String[] fact = row(rows, "fact");
        assertEquals("1", cell(fact, MemberAnalysis.Col.BRANCHES));   // 1 つの if
        assertEquals("2", cell(fact, MemberAnalysis.Col.RETURNS));    // return 2 箇所
        assertEquals("yes", cell(fact, MemberAnalysis.Col.RECURSIVE));
        assertTrue(Integer.parseInt(cell(fact, MemberAnalysis.Col.SELF_CALLS)) >= 1);
        assertTrue(Integer.parseInt(cell(fact, MemberAnalysis.Col.USED_BY)) >= 1);
        assertTrue(cell(fact, MemberAnalysis.Col.CALLED_METHODS).contains("fact"));

        String[] sum = row(rows, "sum");
        assertEquals("1", cell(sum, MemberAnalysis.Col.LOOPS));       // foreach
        assertEquals("1", cell(sum, MemberAnalysis.Col.TRIES));
        assertEquals("1", cell(sum, MemberAnalysis.Col.CATCHES));
        assertEquals("no", cell(sum, MemberAnalysis.Col.RECURSIVE));
    }

    @Test
    public void rows_roleInference() {
        List<String[]> rows = MemberAnalysis.rows(JavaStructureExtractor.extract(SRC));
        assertEquals("getter", cell(row(rows, "getValue"), MemberAnalysis.Col.ROLE));
        assertEquals("setter", cell(row(rows, "setValue"), MemberAnalysis.Col.ROLE));
        assertEquals("other", cell(row(rows, "sum"), MemberAnalysis.Col.ROLE));
    }

    @Test
    public void rows_fieldRowsLeaveMethodMetricsEmpty() {
        List<String[]> rows = MemberAnalysis.rows(JavaStructureExtractor.extract(SRC));
        String[] value = row(rows, "value");
        assertEquals("field", cell(value, MemberAnalysis.Col.MEMBER));
        assertEquals("", cell(value, MemberAnalysis.Col.BRANCHES));
        assertEquals("", cell(value, MemberAnalysis.Col.ROLE));
        assertEquals("", cell(value, MemberAnalysis.Col.RECURSIVE));
    }

    @Test
    public void rows_capturesDeclarationComment() {
        List<String[]> rows = MemberAnalysis.rows(JavaStructureExtractor.extract(
                "package p; public class D {"
                + " /** 合計を返す。 */ public int total() { return 0; }"
                + " /** 件数 */ private int count; }"));
        assertTrue(cell(row(rows, "total"), MemberAnalysis.Col.COMMENT).contains("合計を返す"));
        assertTrue(cell(row(rows, "count"), MemberAnalysis.Col.COMMENT).contains("件数"));
    }

    @Test
    public void headersAndLegendCoverEveryColumn() {
        assertEquals(MemberAnalysis.Col.values().length, MemberAnalysis.headers().size());
        assertEquals(MemberAnalysis.Col.values().length, MemberAnalysis.legend().size());
    }

    private static String[] row(List<String[]> rows, String name) {
        int ni = MemberAnalysis.Col.NAME.ordinal();
        for (String[] r : rows) {
            if (r[ni].equals(name)) {
                return r;
            }
        }
        throw new AssertionError("row not found: " + name);
    }

    private static String cell(String[] row, MemberAnalysis.Col col) {
        return row[col.ordinal()];
    }
}
