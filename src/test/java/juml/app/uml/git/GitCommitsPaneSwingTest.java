// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import juml.app.uml.git.GitRepoService.CommitInfo;
import juml.util.Messages;

import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link GitCommitsPane} の Swing GUI テスト。以下の新機能を公開振る舞いとテスト専用の
 * package-private シーム経由で検証する (リフレクションは使わない)。
 *
 * <ul>
 *   <li>diff トグル (Unified / Side by side) による {@code diffCards} の切替と、
 *       {@link SideBySideDiffView} への行流し込み</li>
 *   <li>変更ファイル右クリック相当の「UML Diff」起動が {@link GitUmlDiffDialog}
 *       (モードレス {@link javax.swing.JDialog}) を実際に開くこと</li>
 *   <li>.java でないファイルでは UML Diff が起動せず {@code git.umldiff.javaOnly}
 *       のステータス通知になること</li>
 * </ul>
 *
 * <p>ヘッドレス環境では Swing コンポーネント生成そのものが失敗しうるため
 * {@link org.junit.Assume} で skip する。xvfb-run 環境での実行を前提とする
 * ({@link GitFileHistoryPaneSwingTest} / {@link GitUmlDiffDialogSwingTest} と同じ流儀)。</p>
 */
public class GitCommitsPaneSwingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String JAVA_PATH = "src/main/java/com/demo/ClassA.java";
    private static final String TEXT_PATH = "a.txt";

    private static final String SRC_V1 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  public String greet() {\n"
                    + "    return \"hi\";\n"
                    + "  }\n"
                    + "}\n";

    private static final String SRC_V2 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  public String greet(String who) {\n"
                    + "    return \"hi \" + who;\n"
                    + "  }\n"
                    + "}\n";

    private File root;
    private Git git;
    private GitRepoService service;
    private GitCommitsPane pane;
    private final List<String> statusMessages = new CopyOnWriteArrayList<>();
    private final List<Window> toDispose = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse("DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());

        root = tmp.newFolder("repo");
        git = Git.init().setDirectory(root).call();
        // CI/開発環境のグローバル設定に gpg.format=ssh があると JGit 6.x の
        // CommitCommand が解釈できず失敗するため、リポジトリ設定で無効化する。
        StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();

        writeJava(SRC_V1);
        writeText("line1\n");
        git.add().addFilepattern(".").call();
        commit("v1: add ClassA and a.txt");

        writeJava(SRC_V2);
        writeText("line1\nline2\n");
        git.add().addFilepattern(".").call();
        commit("v2: change greet signature and append a.txt");

        service = GitRepoService.open(root);
        assertNotNull("テスト用リポジトリを開けるはず", service);

        final String ref = service.currentBranch();
        final GitRepoService svc = service;
        GitPanel.GitContext ctx = new GitPanel.GitContext() {
            @Override public GitRepoService service() {
                return svc;
            }

            @Override public String selectedRef() {
                return ref;
            }

            @Override public void reportStatus(String msg) {
                statusMessages.add(msg);
            }

            @Override public void selectRef(String r) {
                // テストでは基準 ref 切替をサポートしない (no-op)。
            }
        };
        final GitCommitsPane[] holder = new GitCommitsPane[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new GitCommitsPane(ctx));
        pane = holder[0];
    }

    private void writeJava(String content) throws Exception {
        File f = new File(root, JAVA_PATH);
        File parent = f.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new java.io.IOException("mkdirs failed: " + parent);
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeText(String content) throws Exception {
        Files.write(new File(root, TEXT_PATH).toPath(),
                content.getBytes(StandardCharsets.UTF_8));
    }

    private void commit(String message) throws Exception {
        git.commit().setMessage(message)
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com").call();
    }

    @After
    public void tearDown() throws Exception {
        for (Window w : toDispose) {
            SwingUtilities.invokeAndWait(w::dispose);
        }
        if (service != null) {
            service.close();
        }
        if (git != null) {
            git.close();
        }
    }

    /** EDT 上で {@code loadForTest} を呼び、先頭コミット (= HEAD) を選択状態にする。 */
    private void load() throws Exception {
        final String ref = service.currentBranch();
        SwingUtilities.invokeAndWait(() -> {
            try {
                pane.loadForTest(service, ref);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * コンポーネントツリーを再帰的に走査し、指定ラベルの {@link JToggleButton} を返す
     * (見つからなければ null)。リフレクションは使わず公開ツリー探索のみで行う
     * ({@link GitFileHistoryPaneSwingTest#findButton} と同じ方式)。
     */
    private static JToggleButton findToggle(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JToggleButton && text.equals(((JToggleButton) c).getText())) {
                return (JToggleButton) c;
            }
            if (c instanceof Container) {
                JToggleButton found = findToggle((Container) c, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void clickToggle(String label) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JToggleButton button = findToggle(pane, label);
            assertNotNull("トグルボタン \"" + label + "\" が見つかるはず", button);
            button.doClick();
        });
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: split モードで読み込むと split カードが表示され、行が入る
    // -------------------------------------------------------------------------

    @Test
    public void splitMode_showsSplitCardWithRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> pane.setDiffModeForTest("split"));
        load();

        final boolean[] splitVisible = new boolean[1];
        final int[] rowCount = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            splitVisible[0] = pane.isSplitVisibleForTest();
            rowCount[0] = pane.splitRowCountForTest();
        });

        assertTrue("split モードでは split カードが表示されるはず", splitVisible[0]);
        assertTrue("split ビューには diff 行が入っているはず (rowCount=" + rowCount[0] + ")",
                rowCount[0] > 0);
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: unified ⇔ split のトグルで diffCards の可視カードが切り替わる
    // -------------------------------------------------------------------------

    @Test
    public void toggleButtons_switchVisibleDiffCard() throws Exception {
        load();

        final boolean[] initiallySplit = new boolean[1];
        SwingUtilities.invokeAndWait(() -> initiallySplit[0] = pane.isSplitVisibleForTest());
        assertFalse("初期状態は unified (split は非表示) のはず", initiallySplit[0]);

        clickToggle(Messages.get("git.diff.split"));
        final boolean[] afterSplitClick = new boolean[1];
        SwingUtilities.invokeAndWait(() -> afterSplitClick[0] = pane.isSplitVisibleForTest());
        assertTrue("Side by side ボタン押下で split カードに切り替わるはず", afterSplitClick[0]);

        clickToggle(Messages.get("git.diff.unified"));
        final boolean[] afterUnifiedClick = new boolean[1];
        SwingUtilities.invokeAndWait(() -> afterUnifiedClick[0] = pane.isSplitVisibleForTest());
        assertFalse("Unified ボタン押下で unified カードに戻るはず", afterUnifiedClick[0]);
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: .java の変更ファイルを選択して UML Diff を起動するとダイアログが開く
    // -------------------------------------------------------------------------

    @Test
    public void openUmlDiff_javaFile_opensGitUmlDiffDialog() throws Exception {
        load();

        final boolean[] found = new boolean[1];
        SwingUtilities.invokeAndWait(() -> found[0] = pane.selectFileForTest(JAVA_PATH));
        assertTrue(".java の変更ファイルが一覧に見つかるはず", found[0]);

        List<Window> before = currentWindows();
        SwingUtilities.invokeAndWait(pane::openUmlDiffForTest);

        Window opened = awaitNewWindow(before);
        assertNotNull("UML Diff ダイアログが開かれるはず", opened);
        assertTrue("開いたウィンドウは JDialog のはず", opened instanceof javax.swing.JDialog);
        toDispose.add(opened);

        final String title = titleOf((javax.swing.JDialog) opened);
        assertTrue("ダイアログタイトルに対象パスが含まれるはず: " + title,
                title.contains(JAVA_PATH));
    }

    // -------------------------------------------------------------------------
    // 異常系: .java でないファイル (a.txt) では UML Diff は起動せず javaOnly 通知になる
    // -------------------------------------------------------------------------

    @Test
    public void openUmlDiff_nonJavaFile_doesNotOpenDialogAndReportsJavaOnly() throws Exception {
        load();

        final boolean[] found = new boolean[1];
        SwingUtilities.invokeAndWait(() -> found[0] = pane.selectFileForTest(TEXT_PATH));
        assertTrue("a.txt の変更ファイルが一覧に見つかるはず", found[0]);

        List<Window> before = currentWindows();
        SwingUtilities.invokeAndWait(pane::openUmlDiffForTest);

        // ダイアログ生成は同期処理なので、非同期待ちは不要 (ポーリングせず即時比較でよい)。
        assertEquals(".java 以外では新しいウィンドウは開かないはず",
                before.size(), currentWindows().size());
        assertTrue("javaOnly のステータス通知が出るはず",
                statusMessages.contains(Messages.get("git.umldiff.javaOnly")));
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: .java の変更ファイルで「図で左右比較」を開くとダイアログが出る
    // -------------------------------------------------------------------------

    @Test
    public void openDiagramCompare_javaFile_opensSideBySideDialog() throws Exception {
        load();

        final boolean[] found = new boolean[1];
        SwingUtilities.invokeAndWait(() -> found[0] = pane.selectFileForTest(JAVA_PATH));
        assertTrue(".java の変更ファイルが見つかるはず", found[0]);

        List<Window> before = currentWindows();
        SwingUtilities.invokeAndWait(pane::openDiagramCompareForTest);

        Window opened = awaitNewWindow(before);
        assertNotNull("図の左右比較ダイアログが開かれるはず", opened);
        assertTrue("開いたウィンドウは JDialog のはず", opened instanceof javax.swing.JDialog);
        toDispose.add(opened);
        final String title = titleOf((javax.swing.JDialog) opened);
        assertTrue("ダイアログタイトルに対象パスが含まれるはず: " + title,
                title.contains(JAVA_PATH));
    }

    // -------------------------------------------------------------------------
    // 2 コミット選択で「選択同士」の比較コンテキストが組まれる (vs 親でなく)
    // -------------------------------------------------------------------------

    @Test
    public void twoCommitSelection_buildsPairCompareContext() throws Exception {
        load();
        List<CommitInfo> log = service.log(service.currentBranch(), 10);
        assertTrue("2 コミット以上あるはず", log.size() >= 2);
        final String newerSha = log.get(0).sha;   // 行 0 = 最新
        final String olderSha = log.get(1).sha;   // 行 1 = 1 つ前

        SwingUtilities.invokeAndWait(() -> pane.selectCommitRowsForTest(0, 1));

        final String[] oldRev = new String[1];
        final String[] newRev = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            oldRev[0] = pane.cmpOldRevForTest();
            newRev[0] = pane.cmpNewRevForTest();
        });
        assertEquals("比較元は古い方のコミット (親ではなく選択同士)", olderSha, oldRev[0]);
        assertEquals("比較先は新しい方のコミット", newerSha, newRev[0]);
    }

    private static List<Window> currentWindows() throws Exception {
        final List<Window> holder = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> holder.addAll(List.of(Window.getWindows())));
        return holder;
    }

    /**
     * 期限付きポーリングで、{@code before} に無かった新規ウィンドウが現れるのを待つ
     * (固定 sleep 一発や無限待ちにしない)。ダイアログ生成は同期だが、環境差を吸収する
     * ための小さな安全マージンとして待つ。
     */
    private static Window awaitNewWindow(List<Window> before) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            final Window[] holder = new Window[1];
            SwingUtilities.invokeAndWait(() -> {
                for (Window w : Window.getWindows()) {
                    if (!before.contains(w)) {
                        holder[0] = w;
                        return;
                    }
                }
            });
            if (holder[0] != null) {
                return holder[0];
            }
            Thread.sleep(50);
        }
        return null;
    }

    private static String titleOf(javax.swing.JDialog dialog) throws Exception {
        final String[] holder = new String[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = dialog.getTitle());
        return holder[0];
    }
}
