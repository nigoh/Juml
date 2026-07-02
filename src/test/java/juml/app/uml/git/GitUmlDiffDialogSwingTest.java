// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import juml.app.uml.SvgPreviewPanel;
import juml.util.Messages;

import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link GitUmlDiffDialog} の GUI 結合テスト。テンポラリの実 git リポジトリを対象に
 * ダイアログを生成し、内部 {@link javax.swing.SwingWorker} の完了後に「PlantUML」タブへ
 * 差分マーカー付きテキストが反映されること、「図」タブへ描画結果 (または失敗時の
 * フォールバック表示) が装着されることを、公開コンポーネントツリーの走査のみで検証する
 * (パッケージ private フィールドへのリフレクションは使わない)。
 *
 * <p>ヘッドレス環境では Swing コンポーネント生成そのものが失敗しうるため
 * {@link org.junit.Assume} で skip する。xvfb-run 環境での実行を前提とする。</p>
 */
public class GitUmlDiffDialogSwingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String REL_PATH = "src/main/java/com/demo/ClassA.java";
    private static final long WORKER_TIMEOUT_MS = 30_000;

    private File root;
    private Git git;
    private GitRepoService service;
    private RevCommit v1;
    private RevCommit v2;
    private RevCommit v3;
    private GitUmlDiffDialog dialog;

    private static final String SRC_V1 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  private String name;\n\n"
                    + "  public String greet() {\n"
                    + "    return \"hi\";\n"
                    + "  }\n"
                    + "}\n";

    private static final String SRC_V2 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  private String name;\n"
                    + "  private int count;\n\n"
                    + "  public String greet(String who) {\n"
                    + "    return \"hi \" + who;\n"
                    + "  }\n"
                    + "}\n\n"
                    + "class Extra {\n"
                    + "  void run() {}\n"
                    + "}\n";

    private static final String SRC_V3 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  private String name;\n\n"
                    + "  public String greet(String who) {\n"
                    + "    return \"hi \" + who;\n"
                    + "  }\n"
                    + "}\n\n"
                    + "class Extra {\n"
                    + "  void run() {}\n"
                    + "  void stop() {}\n"
                    + "}\n";

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

        writeFile(SRC_V1);
        git.add().addFilepattern(".").call();
        v1 = commit("v1: add ClassA");

        writeFile(SRC_V2);
        git.add().addFilepattern(".").call();
        v2 = commit("v2: change greet signature, add count, add Extra");

        writeFile(SRC_V3);
        git.add().addFilepattern(".").call();
        v3 = commit("v3: remove count, add Extra.stop");

        service = GitRepoService.open(root);
        assertNotNull("テスト用リポジトリを開けるはず", service);
    }

    private void writeFile(String content) throws IOException {
        File f = new File(root, REL_PATH);
        File parent = f.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private RevCommit commit(String message) throws Exception {
        return git.commit().setMessage(message)
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com").call();
    }

    @After
    public void tearDown() throws Exception {
        if (dialog != null) {
            SwingUtilities.invokeAndWait(dialog::dispose);
            dialog = null;
        }
        if (service != null) {
            service.close();
        }
        if (git != null) {
            git.close();
        }
    }

    /** EDT 上で {@link GitUmlDiffDialog} を生成する (コンストラクタが SwingWorker を起動する)。 */
    private GitUmlDiffDialog createDialog(String oldRev, String newRev, String newLabel)
            throws Exception {
        final GitUmlDiffDialog[] holder = new GitUmlDiffDialog[1];
        SwingUtilities.invokeAndWait(() -> holder[0] =
                new GitUmlDiffDialog(null, service, REL_PATH, oldRev, newRev, newLabel));
        return holder[0];
    }

    /**
     * コンポーネントツリーを再帰的に走査し、指定型の最初のインスタンスを返す
     * (見つからなければ null)。リフレクションは使わず公開ツリー探索のみで行う。
     */
    private static <T> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container) {
                T found = find((Container) c, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * SwingWorker 完了 (PlantUML テキストに {@code @startuml} が現れる) を期限付きで待ち、
     * 完了後の JTextArea 本体を返す。タイムアウトしたら null。
     */
    private static JTextArea awaitPumlArea(GitUmlDiffDialog dialog) throws Exception {
        long deadline = System.currentTimeMillis() + WORKER_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            final JTextArea[] holder = new JTextArea[1];
            SwingUtilities.invokeAndWait(() -> {
                JTextArea area = find(dialog.getContentPane(), JTextArea.class);
                if (area != null && area.getText() != null
                        && area.getText().contains("@startuml")) {
                    holder[0] = area;
                }
            });
            if (holder[0] != null) {
                return holder[0];
            }
            Thread.sleep(100);
        }
        return null;
    }

    /** 「図」タブ (JTabbedPane の 0 番目) のコンテンツパネルを返す。 */
    private static Container diagramTabContent(GitUmlDiffDialog dialog) throws Exception {
        final Container[] holder = new Container[1];
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tabs = find(dialog.getContentPane(), JTabbedPane.class);
            assertNotNull("タブペインが構築されているはず", tabs);
            holder[0] = (Container) tabs.getComponentAt(0);
        });
        return holder[0];
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: 1 コミット選択 (= 親コミットとの比較)
    // -------------------------------------------------------------------------

    @Test
    public void parentComparison_populatesPumlTabAndDiagramTab() throws Exception {
        dialog = createDialog(null, v2.getName(), v2.abbreviate(7).name());

        final String title = titleOf(dialog);
        assertTrue("ダイアログタイトルに relPath が含まれるはず: " + title,
                title.contains(REL_PATH));

        JTextArea puml = awaitPumlArea(dialog);
        assertNotNull("SwingWorker 完了までに PlantUML タブが埋まらなかった", puml);
        String text = textOf(puml);
        assertTrue("追加クラス Extra のステレオタイプが出るはず: " + text,
                text.contains("<<added>>"));
        assertTrue("変更クラス ClassA のステレオタイプが出るはず: " + text,
                text.contains("<<modified>>"));

        assertDiagramTabRenderedOrFellBackGracefully(dialog);
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: 2 コミット選択 (親コミットを飛び越えた比較)
    // -------------------------------------------------------------------------

    @Test
    public void explicitTwoCommitComparison_populatesPumlTab() throws Exception {
        dialog = createDialog(v1.getName(), v3.getName(), v3.abbreviate(7).name());

        JTextArea puml = awaitPumlArea(dialog);
        assertNotNull("SwingWorker 完了までに PlantUML タブが埋まらなかった", puml);
        String text = textOf(puml);
        assertTrue("v1 に無い Extra クラスは追加扱いのはず: " + text,
                text.contains("<<added>>"));
        assertTrue("greet() シグネチャ変更で ClassA は変更扱いのはず: " + text,
                text.contains("<<modified>>"));

        assertDiagramTabRenderedOrFellBackGracefully(dialog);
    }

    // -------------------------------------------------------------------------
    // 異常系: 存在しないパスは差分なし ("No structural changes" 相当の note) になる
    // -------------------------------------------------------------------------

    @Test
    public void missingPath_showsNoChangesNoteWithoutCrashing() throws Exception {
        String missing = "src/main/java/com/demo/NoSuchFile.java";
        final GitUmlDiffDialog[] holder = new GitUmlDiffDialog[1];
        SwingUtilities.invokeAndWait(() -> holder[0] =
                new GitUmlDiffDialog(null, service, missing,
                        v1.getName(), v2.getName(), v2.abbreviate(7).name()));
        dialog = holder[0];

        JTextArea puml = awaitPumlArea(dialog);
        // 存在しないパスの diff は空なので @startuml は出るが差分マーカーは出ない。
        // ここでは "@startuml" を含み例外なくワーカーが完了することのみ確認する
        // (差分なし用の note メッセージは PlantUmlStructureDiffDiagram 側で検証済み)。
        assertNotNull("存在しないパスでも SwingWorker は例外なく完了するはず", puml);
        assertTrue("差分なしのはずなので追加/変更ステレオタイプは出ない: " + textOf(puml),
                !textOf(puml).contains("<<added>>") && !textOf(puml).contains("<<modified>>"));

        assertDiagramTabRenderedOrFellBackGracefully(dialog);
    }

    /**
     * 図タブに {@link SvgPreviewPanel} が装着されている (レンダリング成功) か、
     * レンダリング失敗時のフォールバック {@link JLabel} が出ていることを確認する。
     * どちらであっても「例外で落ちてタブが空のまま」ではないことを保証する。
     */
    private void assertDiagramTabRenderedOrFellBackGracefully(GitUmlDiffDialog dialog)
            throws Exception {
        Container diagramTab = diagramTabContent(dialog);
        final SvgPreviewPanel[] svgHolder = new SvgPreviewPanel[1];
        final JLabel[] labelHolder = new JLabel[1];
        SwingUtilities.invokeAndWait(() -> {
            svgHolder[0] = find(diagramTab, SvgPreviewPanel.class);
            labelHolder[0] = find(diagramTab, JLabel.class);
        });
        if (svgHolder[0] == null) {
            assertNotNull("レンダリング失敗時はフォールバック JLabel が出るはず",
                    labelHolder[0]);
            final String labelText = textOf(labelHolder[0]);
            assertTrue("フォールバックラベルは renderFailed メッセージを含むはず: " + labelText,
                    labelText.contains(Messages.get("git.umldiff.renderFailed")));
        }
    }

    private static String titleOf(GitUmlDiffDialog dialog) throws Exception {
        final String[] holder = new String[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = dialog.getTitle());
        return holder[0];
    }

    private static String textOf(JTextArea area) throws Exception {
        final String[] holder = new String[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = area.getText());
        return holder[0];
    }

    private static String textOf(JLabel label) throws Exception {
        final String[] holder = new String[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = label.getText());
        return holder[0];
    }
}
