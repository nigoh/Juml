// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.db.IndexDatabase;
import juml.core.formats.uml.db.IndexReader;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IndexCommandTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File write(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return f;
    }

    @Test
    public void testIndexesGradleProjectAndPopulatesDb() throws Exception {
        File root = tmp.newFolder("proj");
        File dbFile = new File(tmp.newFolder("cache"), "index.db");
        write(new File(root, "settings.gradle"), "include ':app'\n");
        write(new File(root, "app/src/main/java/com/example/Hello.java"),
                "package com.example; public class Hello { public int x; public int get() { return x; } }");
        write(new File(root, "app/src/main/java/com/example/Bye.java"),
                "package com.example; public class Bye {}");
        // settings.gradle に出てこないファイルは対象外
        write(new File(root, "extra/Hidden.java"), "package extra; public class Hidden {}");

        IndexCommand.Result r = IndexCommand.run(root, dbFile, ErrorListener.silent());
        assertEquals(2, r.filesScanned);
        assertEquals(2, r.filesAdded);
        assertEquals(0, r.filesModified);
        assertTrue(dbFile.isFile());

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            IndexReader reader = new IndexReader(db.connection());
            assertEquals(2, reader.classCount());
            assertNotNull(reader.headerOf("com.example.Hello"));
            assertNotNull(reader.headerOf("com.example.Bye"));
        }
    }

    @Test
    public void testFallbackToRootScanWhenNoSettingsGradle() throws Exception {
        File root = tmp.newFolder("proj");
        File dbFile = new File(tmp.newFolder("cache"), "index.db");
        // settings.gradle なし → root 全走査
        write(new File(root, "src/A.java"), "package p; public class A {}");
        write(new File(root, "deep/inner/B.java"), "package p.deep; public class B {}");

        IndexCommand.Result r = IndexCommand.run(root, dbFile, ErrorListener.silent());
        assertEquals("fallback scan should pick up both files", 2, r.filesScanned);
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            assertEquals(2, new IndexReader(db.connection()).classCount());
        }
    }

    @Test
    public void testSecondRunWithNoChangesReportsAllUnchanged() throws Exception {
        File root = tmp.newFolder("proj");
        File dbFile = new File(tmp.newFolder("cache"), "index.db");
        write(new File(root, "settings.gradle"), "include ':app'\n");
        write(new File(root, "app/src/main/java/p/A.java"), "package p; public class A {}");

        IndexCommand.run(root, dbFile, ErrorListener.silent());
        IndexCommand.Result r2 = IndexCommand.run(root, dbFile, ErrorListener.silent());
        assertEquals(1, r2.filesScanned);
        assertEquals(0, r2.filesAdded);
        assertEquals(0, r2.filesModified);
        assertEquals(1, r2.filesUnchanged);
    }

    @Test
    public void testDeletedFilesAreRemovedFromDb() throws Exception {
        File root = tmp.newFolder("proj");
        File dbFile = new File(tmp.newFolder("cache"), "index.db");
        write(new File(root, "settings.gradle"), "include ':app'\n");
        File a = write(new File(root, "app/src/main/java/p/A.java"), "package p; public class A {}");
        File b = write(new File(root, "app/src/main/java/p/B.java"), "package p; public class B {}");

        IndexCommand.run(root, dbFile, ErrorListener.silent());
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            assertEquals(2, new IndexReader(db.connection()).classCount());
        }

        // B.java を削除して再実行
        assertTrue(b.delete());
        IndexCommand.Result r2 = IndexCommand.run(root, dbFile, ErrorListener.silent());
        assertEquals(1, r2.filesScanned);
        assertEquals(1, r2.filesDeleted);

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            IndexReader reader = new IndexReader(db.connection());
            assertEquals(1, reader.classCount());
            assertNotNull(reader.headerOf("p.A"));
        }
    }

    @Test
    public void testExecuteParsesArgsAndCreatesDbAtExplicitPath() throws Exception {
        File root = tmp.newFolder("proj");
        File dbFile = new File(tmp.newFolder("cache"), "explicit.db");
        write(new File(root, "settings.gradle"), "include ':app'\n");
        write(new File(root, "app/src/main/java/p/A.java"), "package p; public class A {}");

        int code = IndexCommand.execute(
                new String[]{"index", root.getAbsolutePath(), "--db", dbFile.getAbsolutePath()},
                ErrorListener.silent());
        assertEquals(0, code);
        assertTrue(dbFile.isFile());
    }

    @Test
    public void testExecuteMissingArgsReturnsErrorCode() throws Exception {
        assertEquals(2, IndexCommand.execute(new String[]{"index"}, ErrorListener.silent()));
        assertEquals(2, IndexCommand.execute(
                new String[]{"index", "/nonexistent/path/xyz"}, ErrorListener.silent()));
    }
}
