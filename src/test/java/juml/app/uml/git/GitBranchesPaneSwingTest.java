// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.RefRow;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link GitBranchesPane} のツリー構築 (セクション分け・現在ブランチ強調) を、temp git repo で
 * 検証する Swing GUI テスト。描画そのものではなく、公開シーム経由でツリーモデルを見る。
 */
public class GitBranchesPaneSwingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;
    private Git git;
    private GitRepoService service;
    private GitBranchesPane pane;

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse("DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        root = tmp.newFolder("repo");
        git = Git.init().setDirectory(root).call();
        StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();
        Files.write(new File(root, "a.txt").toPath(), "x\n".getBytes(StandardCharsets.UTF_8));
        git.add().addFilepattern(".").call();
        git.commit().setMessage("first")
                .setAuthor("Alice", "a@example.com")
                .setCommitter("Alice", "a@example.com").call();
        git.tag().setName("v1.0").call();
        git.branchCreate().setName("feature").call();

        service = GitRepoService.open(root);
        assertNotNull(service);
        GitPanel.GitContext ctx = new GitPanel.GitContext() {
            @Override public GitRepoService service() {
                return service;
            }

            @Override public String selectedRef() {
                return "HEAD";
            }

            @Override public void reportStatus(String msg) {
            }

            @Override public void selectRef(String ref) {
            }
        };
        final GitBranchesPane[] holder = new GitBranchesPane[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new GitBranchesPane(ctx));
        pane = holder[0];
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
    public void loadForTest_buildsThreeSectionsAndMarksCurrentBranch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                pane.loadForTest(service);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        TreeModel model = pane.treeModelForTest();
        Object root = model.getRoot();
        assertEquals("Local / Remote / Tags の 3 セクション", 3, model.getChildCount(root));

        // ローカルセクション配下に現在ブランチ (current=true) がちょうど 1 本ある。
        Object localSection = model.getChild(root, 0);
        int currents = 0;
        boolean sawFeature = false;
        for (int i = 0; i < model.getChildCount(localSection); i++) {
            Object node = model.getChild(localSection, i);
            RefRow row = (RefRow) ((DefaultMutableTreeNode) node).getUserObject();
            if (row.current) {
                currents++;
            }
            if ("feature".equals(row.name)) {
                sawFeature = true;
            }
        }
        assertEquals("現在ブランチはちょうど 1 本", 1, currents);
        assertTrue("feature ブランチが一覧に出る", sawFeature);

        // タグセクションに v1.0 がある。
        Object tagSection = model.getChild(root, 2);
        assertEquals(1, model.getChildCount(tagSection));
        RefRow tag = (RefRow) ((DefaultMutableTreeNode)
                model.getChild(tagSection, 0)).getUserObject();
        assertEquals("v1.0", tag.name);
    }
}
