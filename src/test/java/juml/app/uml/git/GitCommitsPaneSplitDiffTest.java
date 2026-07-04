// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.CommitInfo;
import juml.app.uml.git.GitRepoService.FileChange;
import juml.app.uml.git.LineDiff.Row;
import juml.app.uml.git.LineDiff.Type;
import org.eclipse.jgit.api.Git;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * side-by-side diff の配線 ({@link GitCommitsPane#computeSplitRows}) を、
 * テンポラリの実 git リポジトリで検証する (Swing 非依存・headless で動く)。
 */
public class GitCommitsPaneSplitDiffTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;
    private Git git;
    private GitRepoService svc;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("repo");
        git = Git.init().setDirectory(root).call();
        org.eclipse.jgit.lib.StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();
        svc = GitRepoService.open(root);
    }

    @After
    public void tearDown() {
        if (svc != null) {
            svc.close();
        }
        if (git != null) {
            git.close();
        }
    }

    private void write(String name, String content) throws Exception {
        Files.write(new File(root, name).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private void commit(String msg) throws Exception {
        git.add().addFilepattern(".").call();
        git.commit().setMessage(msg)
                .setAuthor("Tester", "t@example.com")
                .setCommitter("Tester", "t@example.com").call();
    }

    private FileChange changeFor(CommitInfo c, String path) throws Exception {
        for (FileChange f : svc.changesOf(c.sha)) {
            if (path.equals(f.path) || path.equals(f.oldPath)) {
                return f;
            }
        }
        throw new AssertionError("no change for " + path);
    }

    @Test
    public void modifiedFile_alignsChangedLine() throws Exception {
        write("a.txt", "l1\nl2\nl3\n");
        commit("first");
        write("a.txt", "l1\nCHANGED\nl3\n");
        commit("second");

        CommitInfo head = svc.log("HEAD", 10).get(0);
        List<Row> rows = GitCommitsPane.computeSplitRows(svc, head, changeFor(head, "a.txt"));

        assertEquals(3, rows.size());
        assertEquals(Type.EQUAL, rows.get(0).type);
        assertEquals(Type.MODIFIED, rows.get(1).type);
        assertEquals("l2", rows.get(1).oldText);
        assertEquals("CHANGED", rows.get(1).newText);
        assertEquals(Type.EQUAL, rows.get(2).type);
    }

    @Test
    public void addedFile_hasNoOldSide() throws Exception {
        write("keep.txt", "x\n");
        commit("base");
        write("new.txt", "alpha\nbeta\n");
        commit("add new file");

        CommitInfo head = svc.log("HEAD", 10).get(0);
        List<Row> rows = GitCommitsPane.computeSplitRows(svc, head, changeFor(head, "new.txt"));

        assertEquals(2, rows.size());
        for (Row r : rows) {
            assertEquals(Type.ADDED, r.type);
            assertEquals(-1, r.oldLine);
            assertNull(r.oldText);
            assertTrue(r.newLine > 0);
        }
    }

    @Test
    public void deletedFile_hasNoNewSide() throws Exception {
        write("gone.txt", "one\ntwo\n");
        commit("base");
        git.rm().addFilepattern("gone.txt").call();
        git.commit().setMessage("delete file")
                .setAuthor("Tester", "t@example.com")
                .setCommitter("Tester", "t@example.com").call();

        CommitInfo head = svc.log("HEAD", 10).get(0);
        List<Row> rows = GitCommitsPane.computeSplitRows(svc, head, changeFor(head, "gone.txt"));

        assertEquals(2, rows.size());
        for (Row r : rows) {
            assertEquals(Type.REMOVED, r.type);
            assertEquals(-1, r.newLine);
            assertNull(r.newText);
            assertTrue(r.oldLine > 0);
        }
    }

    @Test
    public void firstCommitFile_isAllAdded() throws Exception {
        write("a.txt", "only\nlines\nhere\n");
        commit("initial");

        CommitInfo first = svc.log("HEAD", 10).get(0);
        List<Row> rows = GitCommitsPane.computeSplitRows(svc, first, changeFor(first, "a.txt"));

        assertEquals(3, rows.size());
        for (Row r : rows) {
            assertEquals(Type.ADDED, r.type);
        }
    }
}
