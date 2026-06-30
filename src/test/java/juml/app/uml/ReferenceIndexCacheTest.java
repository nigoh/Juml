// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.refs.ReferenceIndex;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link ReferenceIndexCache} の lazy 構築と無効化のテスト。
 */
public class ReferenceIndexCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeProject(String relPath, String content) throws IOException {
        File f = new File(tmp.getRoot(), relPath);
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
        return f;
    }

    @Test
    public void returnsNullWhenNotLoaded() {
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        ReferenceIndexCache c = new ReferenceIndexCache(pc);
        assertNull(c.get());
        assertFalse(c.isReady());
    }

    @Test
    public void buildsIndexAfterLoad() throws IOException {
        writeProject("src/A.java", "package x; class A { void run() {} }");
        writeProject("src/B.java",
                "package x; class B { private A a; void f(){ a.run(); } }");
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        pc.load(tmp.getRoot(), ErrorListener.silent());

        ReferenceIndexCache c = new ReferenceIndexCache(pc);
        ReferenceIndex idx = c.get();
        assertNotNull(idx);
        assertTrue(c.isReady());
        // 2 度目の get は同じインスタンスを返す
        assertSame(idx, c.get());
    }

    @Test
    public void invalidateForcesRebuild() throws IOException {
        writeProject("src/A.java", "package x; class A {}");
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        pc.load(tmp.getRoot(), ErrorListener.silent());
        ReferenceIndexCache c = new ReferenceIndexCache(pc);

        ReferenceIndex first = c.get();
        assertNotNull(first);
        c.invalidate();
        assertFalse(c.isReady());
        ReferenceIndex second = c.get();
        assertNotNull(second);
        // インスタンスが入れ替わったこと
        org.junit.Assert.assertNotSame(first, second);
    }
}
