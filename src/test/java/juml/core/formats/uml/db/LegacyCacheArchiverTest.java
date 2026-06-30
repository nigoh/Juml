// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link LegacyCacheArchiver} が旧 TSV キャッシュディレクトリだけを
 * 退避し、SQLite ディレクトリ・空ディレクトリは無視することを検証する。
 */
public class LegacyCacheArchiverTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File touch(File parent, String name, String content) throws Exception {
        File f = new File(parent, name);
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return f;
    }

    @Test
    public void testLegacyDirIsArchived() throws Exception {
        File base = tmp.newFolder("cache");
        File legacy = new File(base, "abcdef0123456789");
        assertTrue(legacy.mkdir());
        touch(legacy, "manifest.txt", "cacheVersion=v1\n");
        touch(legacy, "classes.tsv", "fake\n");
        touch(legacy, "sources.tsv", "");

        File archive = LegacyCacheArchiver.archiveLegacyDirs(base);
        assertNotNull("legacy dir should be archived", archive);
        assertTrue(archive.isDirectory());
        assertTrue(archive.getName().startsWith(".legacy-"));
        assertFalse("legacy dir should no longer be at the original location",
                legacy.exists());

        File moved = new File(archive, "abcdef0123456789");
        assertTrue("legacy contents must be inside archive dir", moved.isDirectory());
        assertTrue(new File(moved, "manifest.txt").isFile());
        assertTrue(new File(moved, "classes.tsv").isFile());
    }

    @Test
    public void testSqliteDirIsNotArchived() throws Exception {
        File base = tmp.newFolder("cache");
        File sqliteDir = new File(base, "abcdef0123456789");
        assertTrue(sqliteDir.mkdir());
        touch(sqliteDir, "index.db", "");

        assertNull("no archive should be created", LegacyCacheArchiver.archiveLegacyDirs(base));
        assertTrue("sqlite dir must stay put", sqliteDir.isDirectory());
    }

    @Test
    public void testEmptyBaseDirIsNoop() throws Exception {
        File base = tmp.newFolder("cache");
        assertNull(LegacyCacheArchiver.archiveLegacyDirs(base));
    }

    @Test
    public void testMissingBaseDirIsNoop() throws Exception {
        File missing = new File(tmp.newFolder(), "never-created");
        assertNull(LegacyCacheArchiver.archiveLegacyDirs(missing));
    }

    @Test
    public void testIsLegacyTsvDirHeuristic() throws Exception {
        File a = tmp.newFolder("a");
        assertFalse("empty dir is not legacy", LegacyCacheArchiver.isLegacyTsvDir(a));

        touch(a, "manifest.txt", "");
        assertFalse("manifest alone is not enough", LegacyCacheArchiver.isLegacyTsvDir(a));

        touch(a, "classes.tsv", "");
        assertTrue("manifest + classes.tsv = legacy", LegacyCacheArchiver.isLegacyTsvDir(a));
    }
}
