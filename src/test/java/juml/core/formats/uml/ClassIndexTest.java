// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ClassIndex の Stage A 保持と Stage B 昇格 (detail) のテスト。
 */
public class ClassIndexTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    @Test
    public void testPutAndLookup() {
        ClassIndex index = new ClassIndex();
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.foo");
        c.setSimpleName("Bar");
        c.setDetailed(false);
        index.put(c, null, ":app");

        assertEquals(1, index.size());
        Optional<JavaClassInfo> h = index.header("com.foo.Bar");
        assertTrue(h.isPresent());
        assertFalse(h.get().isDetailed());
        assertEquals(":app", index.module("com.foo.Bar").orElse(null));
    }

    @Test
    public void testDetailPromotesToStageB() throws IOException {
        File src = tmp.newFile("Foo.java");
        writeFile(src,
                "package com.example;\n"
                        + "public class Foo {\n"
                        + "  int counter;\n"
                        + "  public int incr() { counter++; return counter; }\n"
                        + "}\n");

        // Stage A としてヘッダだけ登録
        ClassIndex index = new ClassIndex();
        JavaClassInfo header = JavaStructureExtractor.extractHeadersOnly(
                juml.core.formats.java.AndroidProjectScanner.readFile(src),
                ErrorListener.silent()).get(0);
        index.put(header, src, ":app");
        assertFalse("登録直後はヘッダのみ", header.isDetailed());
        assertTrue("Stage A は fields 空", header.getFields().isEmpty());

        // detail() でフルパース化
        JavaClassInfo full = index.detail("com.example.Foo", ErrorListener.silent());
        assertNotNull(full);
        assertTrue("Stage B では detailed=true", full.isDetailed());
        assertEquals("counter 1 件のフィールドが復活", 1, full.getFields().size());
        assertEquals("incr メソッドが復活", 1, full.getMethods().size());

        // 2 回目は同じインスタンスをキャッシュから返す
        JavaClassInfo again = index.detail("com.example.Foo", ErrorListener.silent());
        assertTrue("キャッシュ再ヒット", full == again);
    }

    @Test
    public void testDetailCacheIsBoundedAndEvictsOldest() throws IOException {
        // 上限 2 件の詳細キャッシュ。3 つの別ファイルを詳細化すると、最も古いものが退避され
        // キャッシュ件数は上限を超えない (無制限蓄積によるメモリ肥大を防ぐ)。
        ClassIndex index = new ClassIndex(2);
        for (int i = 0; i < 3; i++) {
            File src = tmp.newFile("C" + i + ".java");
            writeFile(src,
                    "package com.example;\n"
                            + "public class C" + i + " {\n"
                            + "  int v" + i + ";\n"
                            + "  public int get" + i + "() { return v" + i + "; }\n"
                            + "}\n");
            JavaClassInfo header = JavaStructureExtractor.extractHeadersOnly(
                    juml.core.formats.java.AndroidProjectScanner.readFile(src),
                    ErrorListener.silent()).get(0);
            index.put(header, src, ":app");
            JavaClassInfo full = index.detail("com.example.C" + i, ErrorListener.silent());
            assertTrue("詳細化されている", full.isDetailed());
        }
        assertTrue("キャッシュは上限 2 件以下に保たれる: " + index.detailedCacheSize(),
                index.detailedCacheSize() <= 2);
        // 退避されても detail() は再パースで正しく復元できる (純キャッシュなので欠損なし)
        JavaClassInfo c0 = index.detail("com.example.C0", ErrorListener.silent());
        assertNotNull(c0);
        assertEquals(1, c0.getMethods().size());
    }

    @Test
    public void testDetailMissingQnReturnsNull() {
        ClassIndex index = new ClassIndex();
        assertEquals(null, index.detail("nope.NotThere", ErrorListener.silent()));
    }

    @Test
    public void testMergeCombinesIndexes() {
        ClassIndex a = new ClassIndex();
        JavaClassInfo ac = new JavaClassInfo();
        ac.setPackageName("p"); ac.setSimpleName("A");
        a.put(ac, null, ":m1");

        ClassIndex b = new ClassIndex();
        JavaClassInfo bc = new JavaClassInfo();
        bc.setPackageName("p"); bc.setSimpleName("B");
        b.put(bc, null, ":m2");

        a.merge(b);
        assertEquals(2, a.size());
        assertEquals(":m2", a.module("p.B").orElse(null));
    }
}
