// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassMemberReportTest {

    @Test
    public void render_csvHeaderAndSimpleClassNameRows() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package com.example.app;"
                + " public class Foo {"
                + " private final int count = 0;"
                + " public int add(int a, int b) { return a + b; } }");
        String csv = ClassMemberReport.render(classes);

        assertTrue(csv, csv.startsWith(String.join(",", MemberAnalysis.headers()) + "\n"));
        // クラスは単純名カラム + パッケージ別カラム (FQN/URI は使わない)
        assertTrue(csv, csv.contains("Foo,com.example.app,class,field,"));
        assertFalse(csv, csv.contains("com.example.app.Foo"));
        // フィールド行 (params が無い箇所は空セルマーカー "-")
        assertTrue(csv, csv.contains("field,private,count,int,-,final"));
        // メソッド行 (params にカンマを含むためクォートされる)
        assertTrue(csv, csv.contains("method,public,add,int,\"a: int, b: int\","));
    }

    @Test
    public void render_enumConstantsAsRows() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package x; public enum Color { RED, GREEN, BLUE }");
        String csv = ClassMemberReport.render(classes);
        // 空セルは "-" で埋まる (type/params/modifiers/構造カラムすべて該当なし)
        assertTrue(csv, csv.contains("Color,x,enum,enum-constant,public,RED,-,-,-"));
        assertTrue(csv, csv.contains("enum-constant,public,GREEN"));
        assertTrue(csv, csv.contains("enum-constant,public,BLUE"));
    }

    @Test
    public void render_emptyInput_emitsHeaderOnly() {
        String csv = ClassMemberReport.render(java.util.Collections.emptyList());
        assertTrue(csv, csv.startsWith(String.join(",", MemberAnalysis.headers())));
        // データ行なし
        assertTrue(csv, csv.trim().lines().count() == 1);
    }

    @Test
    public void render_structureColumns_extendsImplementsEnclosingOverrideCalls() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package com.example;"
                + " public class Service extends Base implements Runnable {"
                + "   @Override public void run() { helper.doWork(); }"
                + "   public static class Inner { public void ping() {} }"
                + " }"
                + " class Base {}");
        String csv = ClassMemberReport.render(classes);

        // run(): 継承・@Override・呼び出しが構造カラムに出る
        String[] run = cols(csv, "run");
        assertEquals(csv, MemberAnalysis.headers().size(), run.length);
        assertEquals(csv, "Base", run[10]);       // extends
        assertEquals(csv, "Runnable", run[11]);   // implements
        assertFalse(csv, run[12].isEmpty());      // line (FULL パースで取得済み)
        assertEquals(csv, "Override", run[13]);   // annotations
        assertEquals(csv, "yes", run[14]);        // overrides
        assertTrue(csv, Integer.parseInt(run[15]) >= 1); // calls (fan-out)
        assertFalse(csv, run[16].isEmpty());      // callees

        // Inner.ping(): enclosing が外側クラス名、@Override 無し・呼び出し無し
        String[] ping = cols(csv, "ping");
        assertEquals(csv, "Service", ping[9]);    // enclosing
        assertEquals(csv, "no", ping[14]);        // overrides
        assertEquals(csv, "0", ping[15]);         // calls (呼び出し 0 は "0"、空ではない)
        assertEquals(csv, "-", ping[16]);         // callees (該当なしは空セルマーカー)
    }

    @Test
    public void render_fieldRow_annotationsButNoMethodMetrics() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package x; public class C { @Deprecated private int n; }");
        String csv = ClassMemberReport.render(classes);

        String[] n = cols(csv, "n");
        assertEquals(csv, "field", n[3]);
        assertEquals(csv, "Deprecated", n[13]); // annotations
        assertEquals(csv, "-", n[12]);          // line (フィールドは行情報を持たない)
        assertEquals(csv, "-", n[14]);          // overrides
        assertEquals(csv, "-", n[15]);          // calls
        assertEquals(csv, "-", n[16]);          // callees
    }

    /** name 列 (6 列目) が一致する最初のデータ行を CSV 分割して返す。引数にカンマを含まない前提。 */
    private static String[] cols(String csv, String memberName) {
        for (String ln : csv.split("\n")) {
            String[] c = ln.split(",", -1);
            if (c.length >= 6 && c[5].equals(memberName)) {
                return c;
            }
        }
        return new String[0];
    }
}
