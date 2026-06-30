// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class MethodUsageReportTest {

    @Test
    public void render_listsClassesMethodsAndSignatures() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package x; public class Foo { public int add(int a, int b) { return a + b; } }");
        String md = MethodUsageReport.render(classes, null, Collections.emptyList());
        assertTrue(md, md.contains("x.Foo"));
        assertTrue(md, md.contains("add(a: int, b: int): int"));
        // refIndex 無しなら利用側なし・直接呼び出し
        assertTrue(md, md.contains("利用側"));
        assertTrue(md, md.contains("実行条件"));
        // 空欄は理由付きで表記し、算出ロジック節を末尾に出す
        assertTrue(md, md.contains("呼び出し元なし"));
        assertTrue(md, md.contains("算出ロジック"));
    }

    @Test
    public void render_csvFormat_emitsHeaderAndMethodRows() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package x; public class Foo { public int add(int a, int b) { return a + b; } }");
        String csv = MethodUsageReport.render(classes, null, Collections.emptyList(),
                MethodUsageReport.Format.CSV);
        assertTrue(csv, csv.startsWith(
                "区分,クラス,クラス名,種別,関数,ファイル,行,利用側,実行条件,理由"));
        // 署名にカンマを含むためクォートされる
        assertTrue(csv, csv.contains("\"+ add(a: int, b: int): int\""));
        // class 列は FQN、class_name 列は単純名
        assertTrue(csv, csv.contains("method,x.Foo,Foo,CLASS,"));
        // reason 列に空欄理由が入る
        assertTrue(csv, csv.contains("呼び出し元なし"));
    }

    @Test
    public void render_includesClickListenerHandlersAsListeners() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "class Screen { void setup() {"
                + " button.setOnClickListener(v -> submit()); } void submit() {} }");
        String md = MethodUsageReport.render(classes, null, Collections.emptyList());
        assertTrue(md, md.contains("[listener]"));
        // setOnClickListener のラムダは SAM 解決で onClick 名になる
        assertTrue(md, md.contains("onClick"));
        assertTrue(md, md.contains("クリック"));
    }
}
