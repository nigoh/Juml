// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.CancelToken;
import juml.util.ErrorListener;
import juml.util.ProgressListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * UmlGenerator.extractFromProjectDetailed の並列パース・進捗・キャンセル・
 * Stage A モード切替テスト。
 */
public class UmlGeneratorParallelTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private File createProject(int classes) throws IOException {
        File root = tmp.newFolder("Proj");
        File dir = new File(root, "src/main/java/com/example");
        assertTrue(dir.mkdirs());
        for (int i = 0; i < classes; i++) {
            writeFile(new File(dir, "C" + i + ".java"),
                    "package com.example; public class C" + i + " {\n"
                            + "  int v;\n  public int incr() { v++; return v; }\n}");
        }
        return root;
    }

    @Test
    public void testParallelMatchesSerial() throws IOException {
        File root = createProject(30);
        // 並列 FULL
        UmlGenerator.ProjectParseResult parallel = UmlGenerator.extractFromProjectDetailed(
                root, null, ErrorListener.silent(), ProgressListener.silent(),
                CancelToken.NONE, false, UmlGenerator.ParseMode.FULL);
        // 既存 (シングルスレッドだった) extractFromProject
        List<JavaClassInfo> serial = UmlGenerator.extractFromProject(root, null,
                ErrorListener.silent(), false);
        Set<String> pNames = new HashSet<>();
        Set<String> sNames = new HashSet<>();
        for (JavaClassInfo c : parallel.getClasses()) {
            pNames.add(c.getQualifiedName());
        }
        for (JavaClassInfo c : serial) {
            sNames.add(c.getQualifiedName());
        }
        assertEquals("並列とシリアルで結果集合が一致", sNames, pNames);
    }

    @Test
    public void testProgressEmittedPerFile() throws IOException {
        File root = createProject(10);
        AtomicInteger calls = new AtomicInteger();
        UmlGenerator.extractFromProjectDetailed(root, null,
                ErrorListener.silent(),
                (done, total, msg) -> {
                    if (done > 0 && total > 0) {
                        calls.incrementAndGet();
                    }
                },
                CancelToken.NONE, false, UmlGenerator.ParseMode.FULL);
        assertEquals("10 ファイル分の進捗イベント", 10, calls.get());
    }

    @Test
    public void testHeadersOnlyMode() throws IOException {
        File root = createProject(5);
        UmlGenerator.ProjectParseResult r = UmlGenerator.extractFromProjectDetailed(
                root, null, ErrorListener.silent(), ProgressListener.silent(),
                CancelToken.NONE, false, UmlGenerator.ParseMode.HEADERS_ONLY);
        for (JavaClassInfo c : r.getClasses()) {
            assertFalse("Stage A モードでは detailed=false", c.isDetailed());
            assertTrue("Stage A モードではメソッド空", c.getMethods().isEmpty());
            assertTrue("Stage A モードではフィールド空", c.getFields().isEmpty());
        }
        // ClassIndex 経由で detail 化できる
        for (String qn : r.getIndex().qualifiedNames()) {
            JavaClassInfo d = r.getIndex().detail(qn, ErrorListener.silent());
            assertTrue("detail 後は detailed=true", d.isDetailed());
            assertEquals("incr 1 件のはず", 1, d.getMethods().size());
        }
    }

    @Test
    public void testIndexCarriesModuleMap() throws IOException {
        File root = createProject(3);
        UmlGenerator.ProjectParseResult r = UmlGenerator.extractFromProjectDetailed(
                root, null, ErrorListener.silent(), ProgressListener.silent(),
                CancelToken.NONE, false, UmlGenerator.ParseMode.FULL);
        for (JavaClassInfo c : r.getClasses()) {
            String mod = r.getIndex().module(c.getQualifiedName()).orElse(null);
            assertTrue("module は推定されているはず: " + mod,
                    mod != null && !mod.isEmpty());
        }
    }

    @Test
    public void testCancelBeforeStartReturnsEmpty() throws IOException {
        File root = createProject(50);
        CancelToken c = new CancelToken();
        c.cancel();
        UmlGenerator.ProjectParseResult r = UmlGenerator.extractFromProjectDetailed(
                root, null, ErrorListener.silent(), ProgressListener.silent(),
                c, false, UmlGenerator.ParseMode.FULL);
        assertTrue("即時キャンセルなら結果空", r.getClasses().isEmpty());
    }
}
