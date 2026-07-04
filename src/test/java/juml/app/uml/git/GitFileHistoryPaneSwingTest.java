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

import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link GitFileHistoryPane} の世代カウンタ分離 (historyGen / textGen) の回帰テスト。
 *
 * <p>以前は履歴一覧の読込 (loadHistory) と diff/blame の textArea 更新が単一の
 * {@code gen} カウンタを共有していたため、履歴読込中 (または読込直後) に diff/blame を
 * 操作すると、世代不一致で履歴読込の {@code done()} がサイレントに握りつぶされ、
 * 一覧が更新されないことがあった。現在は「リスト用 historyGen」と
 * 「textArea 用 textGen (diff/blame 共有)」に分離済みで、blame/diff の操作が
 * 履歴一覧の反映を妨げないことを保証する。</p>
 *
 * <p>ヘッドレス環境では Swing コンポーネント生成そのものが失敗しうるため
 * {@link org.junit.Assume} で skip する。xvfb-run 環境での実行を前提とする
 * ({@link GitUmlDiffDialogSwingTest} と同じ流儀)。</p>
 */
public class GitFileHistoryPaneSwingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String FILE_NAME = "a.txt";
    private static final long WORKER_TIMEOUT_MS = 30_000;

    private File root;
    private Git git;
    private GitRepoService service;
    private GitFileHistoryPane pane;
    private final List<String> statusMessages = new CopyOnWriteArrayList<>();

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

        writeFile("line1\n");
        git.add().addFilepattern(".").call();
        commit("first commit", "Alice", "alice@example.com");

        writeFile("line1\nline2\n");
        git.add().addFilepattern(".").call();
        commit("second commit", "Bob", "bob@example.com");

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
        final GitFileHistoryPane[] holder = new GitFileHistoryPane[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new GitFileHistoryPane(ctx));
        pane = holder[0];
    }

    private void writeFile(String content) throws Exception {
        Files.write(new File(root, FILE_NAME).toPath(),
                content.getBytes(StandardCharsets.UTF_8));
    }

    private void commit(String message, String author, String email) throws Exception {
        git.commit().setMessage(message)
                .setAuthor(author, email)
                .setCommitter(author, email).call();
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

    /**
     * コンポーネントツリーを再帰的に走査し、指定型の最初のインスタンスを返す
     * (見つからなければ null)。リフレクションは使わず公開ツリー探索のみで行う
     * ({@link GitUmlDiffDialogSwingTest#find} と同じ方式)。
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

    /** 公開ツリーを走査し、表示文字列が一致する最初の {@link JButton} を返す。 */
    private static JButton findButton(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && text.equals(((JButton) c).getText())) {
                return (JButton) c;
            }
            if (c instanceof Container) {
                JButton found = findButton((Container) c, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** パス欄にファイル名を入れる。 */
    private void setPath(String path) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = find(pane, JTextField.class);
            assertNotNull("パス入力欄が見つかるはず", field);
            field.setText(path);
        });
    }

    /** 指定ラベルのボタンを EDT 上でクリックする。 */
    private void click(String label) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = findButton(pane, label);
            assertNotNull("ボタン \"" + label + "\" が見つかるはず", button);
            button.doClick();
        });
    }

    /** 履歴一覧 (JList<CommitInfo>) の現在の件数を EDT 上で取得する。 */
    @SuppressWarnings("unchecked")
    private int historyListSize() throws Exception {
        final int[] holder = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            JList<CommitInfo> list = (JList<CommitInfo>) find(pane, JList.class);
            assertNotNull("履歴一覧の JList が見つかるはず", list);
            holder[0] = list.getModel().getSize();
        });
        return holder[0];
    }

    private String textAreaText() throws Exception {
        final String[] holder = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            JTextArea area = find(pane, JTextArea.class);
            assertNotNull("結果表示用の JTextArea が見つかるはず", area);
            holder[0] = area.getText();
        });
        return holder[0];
    }

    /**
     * 期限付きポーリングで、条件を満たすまで待つ。固定 sleep 一発や無限待ちにしない
     * ({@code GitUmlDiffDialogSwingTest#awaitPumlArea} の流儀に合わせる)。
     */
    private interface Condition {
        boolean check() throws Exception;
    }

    private static void awaitUntil(String failureMessage, Condition condition) throws Exception {
        long deadline = System.currentTimeMillis() + WORKER_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.check()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(failureMessage);
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: History クリックで履歴一覧が確実に populate される
    // -------------------------------------------------------------------------

    @Test
    public void historyButton_populatesHistoryList() throws Exception {
        setPath(FILE_NAME);
        click(Messages.get("git.file.history"));

        awaitUntil("History クリック後に履歴一覧が populate されなかった",
                () -> historyListSize() == 2);

        assertEquals("a.txt に触れた 2 コミットが一覧に出るはず", 2, historyListSize());
        assertTrue("読込完了のステータス通知があるはず",
                statusMessages.stream().anyMatch(m -> m.contains(FILE_NAME)));
    }

    // -------------------------------------------------------------------------
    // 回帰 (レース再現): History クリック直後 (完了前) に Blame をクリックしても、
    // 履歴一覧の populate が握りつぶされない
    // -------------------------------------------------------------------------

    /**
     * 世代カウンタ分離前の実装では、履歴読込 (loadHistory) と blame/diff が単一の
     * {@code gen} を共有していたため、履歴読込の SwingWorker が完了する前に
     * Blame をクリックしただけで {@code gen} がインクリメントされ、履歴読込の
     * {@code done()} が「古い世代」と判定されて historyModel が永遠に空のままになった。
     *
     * <p>ここでは History → Blame の 2 クリックを、完了を待たず連続して EDT 上で実行する。
     * どちらのクリックも「カウンタをインクリメントして SwingWorker#execute() を呼ぶ」
     * だけの軽い EDT 処理であり、実際の git 処理 (バックグラウンドスレッド) より
     * 大幅に速く終わるため、Blame 側のインクリメントは History 側の {@code done()} より
     * 必ず先に EDT 上で起こる。したがって、この 2 クリックの相対順序は環境の
     * スケジューリングに左右されず決定的に「レースを踏む」条件を再現できる
     * (= 実行タイミングに依存したフレーキーな待ち方をしていない)。</p>
     *
     * <p>世代分離後の現在の実装では historyGen と textGen が独立しているため、
     * Blame クリックが historyGen に影響せず、履歴一覧は正しく populate されるはず。</p>
     */
    @Test
    public void blameClickedImmediatelyAfterHistory_stillPopulatesHistoryList() throws Exception {
        setPath(FILE_NAME);

        click(Messages.get("git.file.history"));
        click(Messages.get("git.file.blame"));

        awaitUntil("blame の結果が textArea に反映されなかった",
                () -> textAreaText().contains("Alice") && textAreaText().contains("Bob"));
        awaitUntil("History 直後に Blame をクリックしても履歴一覧は populate されるはず"
                        + " (historyGen と textGen が分離されているため blame 操作の影響を受けない)",
                () -> historyListSize() == 2);

        assertEquals("blame と競合しても a.txt の 2 コミットが一覧に出るはず",
                2, historyListSize());
    }
}
