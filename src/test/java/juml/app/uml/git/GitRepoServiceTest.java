// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link GitRepoService} の読み取り API を、テンポラリに作った実 git リポジトリで検証する。
 * (JGit のみ使用。git コマンド不要・headless で動く)
 */
public class GitRepoServiceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;
    private Git git;
    private RevCommit first;
    private RevCommit second;
    private GitRepoService service;

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("repo");
        git = Git.init().setDirectory(root).call();
        // CI/開発環境のグローバル設定に gpg.format=ssh があると JGit 6.x の
        // CommitCommand が解釈できず失敗するため、リポジトリ設定で無効化する。
        org.eclipse.jgit.lib.StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();
        writeFile("a.txt", "line1\n");
        git.add().addFilepattern(".").call();
        first = git.commit().setMessage("first commit")
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com").call();
        writeFile("a.txt", "line1\nline2\n");
        git.add().addFilepattern(".").call();
        second = git.commit().setMessage("second commit")
                .setAuthor("Bob", "bob@example.com")
                .setCommitter("Bob", "bob@example.com").call();
        git.tag().setName("v1.0").call();
        git.branchCreate().setName("feature").call();

        service = GitRepoService.open(root);
        assertNotNull("テスト用リポジトリを開けるはず", service);
    }

    private void writeFile(String name, String content) throws Exception {
        Files.write(new File(root, name).toPath(),
                content.getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() {
        if (service != null) {
            service.close();
        }
        if (git != null) {
            git.close();
        }
    }

    @Test
    public void open_findsRepositoryFromSubdirectory() throws Exception {
        File sub = new File(root, "src/deep");
        assertTrue(sub.mkdirs());
        GitRepoService fromSub = GitRepoService.open(sub);
        assertNotNull("サブディレクトリからでも .git を発見できるはず", fromSub);
        assertEquals(root.getCanonicalFile(),
                fromSub.workTree().getCanonicalFile());
        fromSub.close();
    }

    @Test
    public void open_returnsNullForNonRepository() throws Exception {
        assertNull(GitRepoService.open(tmp.newFolder("plain")));
        assertNull(GitRepoService.open(null));
    }

    @Test
    public void log_returnsCommitsNewestFirst() throws Exception {
        List<GitRepoService.CommitInfo> log =
                service.log(service.currentBranch(), 10);
        assertEquals(2, log.size());
        assertEquals("second commit", log.get(0).shortMessage);
        assertEquals("first commit", log.get(1).shortMessage);
        assertEquals("Bob", log.get(0).author);
        assertEquals(second.getName(), log.get(0).sha);
        assertEquals(7, log.get(0).shortSha.length());
    }

    @Test
    public void branchesAndTags_listCreatedRefs() throws Exception {
        List<String> branches = service.localBranches();
        assertTrue("feature ブランチが一覧に出るはず", branches.contains("feature"));
        assertTrue("既定ブランチが一覧に出るはず",
                branches.contains(service.currentBranch()));
        assertEquals(List.of("v1.0"), service.tags());
        assertTrue("リモート未設定ならリモートブランチは空",
                service.remoteBranches().isEmpty());
    }

    @Test
    public void fileLog_returnsOnlyCommitsTouchingPath() throws Exception {
        writeFile("b.txt", "other\n");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("add b")
                .setAuthor("Carol", "carol@example.com")
                .setCommitter("Carol", "carol@example.com").call();

        List<GitRepoService.CommitInfo> log =
                service.fileLog(service.currentBranch(), "a.txt", 10);
        assertEquals("a.txt に触れた 2 コミットだけが返るはず", 2, log.size());
    }

    @Test
    public void changesOf_reportsAddAndModify() throws Exception {
        List<GitRepoService.FileChange> initial = service.changesOf(first.getName());
        assertEquals(1, initial.size());
        assertEquals("ADD", initial.get(0).changeType);
        assertEquals("a.txt", initial.get(0).path);

        List<GitRepoService.FileChange> modified = service.changesOf(second.getName());
        assertEquals(1, modified.size());
        assertEquals("MODIFY", modified.get(0).changeType);
    }

    @Test
    public void diffOf_containsAddedLine() throws Exception {
        String diff = service.diffOf(second.getName(), "a.txt");
        assertTrue("unified diff に追加行が含まれるはず", diff.contains("+line2"));
        assertTrue(diff.contains("a.txt"));
    }

    @Test
    public void blame_attributesLinesToAuthors() throws Exception {
        List<GitRepoService.BlameLine> blame =
                service.blame(service.currentBranch(), "a.txt");
        assertEquals(2, blame.size());
        assertEquals("line1 は最初のコミットの作者に帰属するはず",
                "Alice", blame.get(0).author);
        assertEquals("line2 は 2 番目のコミットの作者に帰属するはず",
                "Bob", blame.get(1).author);
        assertEquals("line1", blame.get(0).content);
        assertEquals("line2", blame.get(1).content);
    }

    @Test
    public void fileContentAt_returnsBlobOfEachCommit() throws Exception {
        assertEquals("line1\n", service.fileContentAt(first.getName(), "a.txt"));
        assertEquals("line1\nline2\n",
                service.fileContentAt(second.getName(), "a.txt"));
        assertEquals("ブランチ名でも解決できるはず", "line1\nline2\n",
                service.fileContentAt(service.currentBranch(), "a.txt"));
    }

    @Test
    public void fileContentAt_returnsNullForMissingPathOrRev() throws Exception {
        assertNull("存在しないパスは null",
                service.fileContentAt(second.getName(), "nope.txt"));
        assertNull("最初のコミットにまだ無いファイルは null",
                service.fileContentAt(first.getName(), "b.txt"));
        assertNull("解決できない rev は null",
                service.fileContentAt("no-such-ref", "a.txt"));
        assertNull(service.fileContentAt(second.getName(), ""));
    }

    @Test
    public void parentOf_returnsFirstParentOrNull() throws Exception {
        assertEquals(first.getName(), service.parentOf(second.getName()));
        assertNull("初回コミットの親は null", service.parentOf(first.getName()));
        assertNull(service.parentOf("no-such-ref"));
    }

    @Test
    public void relativize_convertsAbsolutePathInsideWorkTree() throws Exception {
        assertEquals("a.txt", service.relativize(new File(root, "a.txt")));
        assertEquals("src/deep/x.java",
                service.relativize(new File(root, "src/deep/x.java")));
        assertNull("作業ツリー外のパスは null",
                service.relativize(tmp.newFile("outside.txt")));
    }
}
