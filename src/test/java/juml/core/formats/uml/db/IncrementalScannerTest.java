// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.JavaClassInfo;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IncrementalScannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File touch(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return f;
    }

    private static JavaClassInfo cls(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setOrigin(JavaClassInfo.Origin.SOURCE);
        return c;
    }

    @Test
    public void testAllFilesAddedOnEmptyDb() throws Exception {
        File dbFile = new File(tmp.newFolder("c"), "index.db");
        File root = tmp.newFolder("proj");
        File a = touch(new File(root, "src/A.java"), "x");
        File b = touch(new File(root, "src/B.java"), "y");
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            IncrementalScanner.DiffResult r = IncrementalScanner.diff(
                    db.connection(), root, IndexWriter.KIND_JAVA, Arrays.asList(a, b));
            assertEquals(2, r.getAdded().size());
            assertTrue(r.getModified().isEmpty());
            assertTrue(r.getUnchanged().isEmpty());
            assertTrue(r.getDeletedPaths().isEmpty());
        }
    }

    @Test
    public void testUnchangedMatchedByMtimeAndSize() throws Exception {
        File dbFile = new File(tmp.newFolder("c"), "index.db");
        File root = tmp.newFolder("proj");
        File a = touch(new File(root, "src/A.java"), "x");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            IndexWriter w = new IndexWriter(db.connection());
            w.upsertFile("src/A.java", IndexWriter.KIND_JAVA,
                    a.lastModified(), a.length(), null, null,
                    Collections.singletonList(cls("p", "A")), null);

            IncrementalScanner.DiffResult r = IncrementalScanner.diff(
                    db.connection(), root, IndexWriter.KIND_JAVA, Collections.singletonList(a));
            assertEquals(1, r.getUnchanged().size());
            assertTrue(r.getAdded().isEmpty());
            assertTrue(r.getModified().isEmpty());
        }
    }

    @Test
    public void testModifiedDetectedBySizeChange() throws Exception {
        File dbFile = new File(tmp.newFolder("c"), "index.db");
        File root = tmp.newFolder("proj");
        File a = touch(new File(root, "src/A.java"), "x");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            IndexWriter w = new IndexWriter(db.connection());
            w.upsertFile("src/A.java", IndexWriter.KIND_JAVA,
                    a.lastModified(), a.length(), null, null,
                    Collections.singletonList(cls("p", "A")), null);

            // ファイル内容を変更してサイズを変える (mtime も変わる可能性が高いが、size 不一致だけでも検出されること)
            touch(a, "xx");

            IncrementalScanner.DiffResult r = IncrementalScanner.diff(
                    db.connection(), root, IndexWriter.KIND_JAVA, Collections.singletonList(a));
            assertEquals(1, r.getModified().size());
            assertEquals(0, r.getUnchanged().size());
        }
    }

    @Test
    public void testDeletedPathsAppearWhenFileMissingFromCurrentList() throws Exception {
        File dbFile = new File(tmp.newFolder("c"), "index.db");
        File root = tmp.newFolder("proj");
        File a = touch(new File(root, "src/A.java"), "x");
        File b = touch(new File(root, "src/B.java"), "y");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            IndexWriter w = new IndexWriter(db.connection());
            w.upsertFile("src/A.java", IndexWriter.KIND_JAVA, a.lastModified(), a.length(),
                    null, null, Collections.singletonList(cls("p", "A")), null);
            w.upsertFile("src/B.java", IndexWriter.KIND_JAVA, b.lastModified(), b.length(),
                    null, null, Collections.singletonList(cls("p", "B")), null);

            // 現在の FS には A.java しか無いと宣言
            IncrementalScanner.DiffResult r = IncrementalScanner.diff(
                    db.connection(), root, IndexWriter.KIND_JAVA, Collections.singletonList(a));
            Set<String> deleted = new HashSet<>(r.getDeletedPaths());
            assertEquals(Collections.singleton("src/B.java"), deleted);
        }
    }

    @Test
    public void testStaleAggregatesAddedAndModified() throws Exception {
        File dbFile = new File(tmp.newFolder("c"), "index.db");
        File root = tmp.newFolder("proj");
        File a = touch(new File(root, "src/A.java"), "x");
        File b = touch(new File(root, "src/B.java"), "y");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "test")) {
            new IndexWriter(db.connection()).upsertFile(
                    "src/A.java", IndexWriter.KIND_JAVA, a.lastModified(), a.length(),
                    null, null, Collections.singletonList(cls("p", "A")), null);

            touch(a, "xxx"); // modified
            // b は added

            IncrementalScanner.DiffResult r = IncrementalScanner.diff(
                    db.connection(), root, IndexWriter.KIND_JAVA, Arrays.asList(a, b));
            assertEquals(2, r.getStale().size());
            List<String> staleNames = r.getStale().stream()
                    .map(File::getName).collect(Collectors.toList());
            assertTrue(staleNames.contains("A.java"));
            assertTrue(staleNames.contains("B.java"));
        }
    }
}
