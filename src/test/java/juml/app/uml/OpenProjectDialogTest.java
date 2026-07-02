// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.ProjectRecord;
import juml.util.Messages;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JButton;
import javax.swing.JDialog;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link OpenProjectDialog} の公開振る舞いテスト。
 *
 * <p>コンストラクタが {@code private} かつ {@code show()} がモーダルブロックするため、
 * リフレクションで直接コンストラクタを呼び出し、{@code setVisible(true)} を呼ばずに
 * ボタン操作の副作用（dispose / action / chosenRoot）を検証する。</p>
 *
 * <p>JDialog の生成には {@code DISPLAY} が必要なため、ヘッドレス環境では
 * {@link Assume} でスキップする。{@code xvfb-run} でラップして実行すること。</p>
 */
public class OpenProjectDialogTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final List<JDialog> toDispose = new ArrayList<>();

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境ではスキップ (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
    }

    @After
    public void cleanup() {
        GuiActionRunner.execute(() -> {
            for (JDialog dlg : toDispose) {
                if (dlg.isDisplayable()) {
                    dlg.dispose();
                }
            }
        });
        toDispose.clear();
    }

    // -------------------------------------------------------------------------
    // プライベートコンストラクタ呼び出しヘルパ
    // -------------------------------------------------------------------------

    /**
     * {@code OpenProjectDialog(Frame, List, Consumer)} の private コンストラクタを
     * EDT 上で呼び出す。返却したインスタンスは後始末リストに追加する。
     */
    @SuppressWarnings("unchecked")
    private OpenProjectDialog buildDialog(List<ProjectRecord> records,
                                          Consumer<ProjectRecord> onDelete) throws Exception {
        Constructor<OpenProjectDialog> ctor = OpenProjectDialog.class.getDeclaredConstructor(
                java.awt.Frame.class, List.class, Consumer.class);
        ctor.setAccessible(true);
        OpenProjectDialog dlg = GuiActionRunner.execute(() -> {
            try {
                return ctor.newInstance(null, records, onDelete);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        toDispose.add(dlg);
        return dlg;
    }

    /** {@link OpenProjectDialog#action} フィールドを返す（private へのアクセス）。 */
    private static OpenProjectDialog.Action getAction(OpenProjectDialog dlg) throws Exception {
        Field f = OpenProjectDialog.class.getDeclaredField("action");
        f.setAccessible(true);
        return (OpenProjectDialog.Action) f.get(dlg);
    }

    /** {@link OpenProjectDialog#chosenRoot} フィールドを返す（private へのアクセス）。 */
    private static File getChosenRoot(OpenProjectDialog dlg) throws Exception {
        Field f = OpenProjectDialog.class.getDeclaredField("chosenRoot");
        f.setAccessible(true);
        return (File) f.get(dlg);
    }

    // -------------------------------------------------------------------------
    // コンポーネントツリー探索ヘルパ
    // -------------------------------------------------------------------------

    /**
     * コンテナを再帰的に探索して、指定ラベルの {@link JButton} を探す。
     * 見つからなければ {@code null}。
     */
    private static JButton findButton(Container root, String label) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && label.equals(((JButton) c).getText())) {
                return (JButton) c;
            }
            if (c instanceof Container) {
                JButton found = findButton((Container) c, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // テストケース
    // -------------------------------------------------------------------------

    /**
     * 空リストで構築したとき、Browse (フォルダ参照) と Cancel ボタンのみが存在し
     * Open ボタンは存在しないことを確認する。
     */
    @Test
    public void emptyList_hasBrowseAndCancelButNoOpenButton() throws Exception {
        OpenProjectDialog dlg = buildDialog(Collections.emptyList(), null);

        String browseLabel = Messages.get("dlg.openProject.browse");
        String cancelLabel = Messages.get("dlg.openProject.cancel");
        String openLabel = Messages.get("dlg.openProject.open");

        JButton browse = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), browseLabel));
        JButton cancel = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), cancelLabel));
        JButton open = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), openLabel));

        assertNotNull("空リストのとき Browse ボタンが存在するはず", browse);
        assertNotNull("空リストのとき Cancel ボタンが存在するはず", cancel);
        assertNull("空リストのとき Open ボタンは存在しないはず", open);
    }

    /**
     * 非空リストで構築したとき、Open ボタンが存在することを確認する。
     * ルートディレクトリが実在しない場合 Open ボタンは無効。
     */
    @Test
    public void nonEmptyList_openButtonExistsButDisabledWhenDirMissing() throws Exception {
        List<ProjectRecord> records = Collections.singletonList(
                new ProjectRecord(1L, "/nonexistent/path", "TestProj", System.currentTimeMillis()));

        OpenProjectDialog dlg = buildDialog(records, null);

        String openLabel = Messages.get("dlg.openProject.open");
        JButton open = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), openLabel));
        assertNotNull("非空リストのとき Open ボタンが存在するはず", open);
        assertFalse("存在しないパスのとき Open ボタンは無効のはず",
                GuiActionRunner.execute(() -> open.isEnabled()));
    }

    /**
     * 実在するディレクトリを持つレコードで構築したとき、
     * 先頭レコードが自動選択されて Open ボタンが有効になることを確認する。
     */
    @Test
    public void nonEmptyList_openButtonEnabledWhenDirExists() throws Exception {
        File existingDir = tmp.newFolder("ExistingProject");
        List<ProjectRecord> records = Collections.singletonList(
                new ProjectRecord(1L, existingDir.getAbsolutePath(), "ExistingProj",
                        System.currentTimeMillis()));

        OpenProjectDialog dlg = buildDialog(records, null);

        String openLabel = Messages.get("dlg.openProject.open");
        JButton open = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), openLabel));
        assertNotNull("Open ボタンが存在するはず", open);
        assertTrue("実在ディレクトリのとき先頭レコード選択で Open ボタンは有効のはず",
                GuiActionRunner.execute(() -> open.isEnabled()));
    }

    /**
     * Cancel ボタンをクリックするとダイアログが dispose され、
     * action が {@link OpenProjectDialog.Action#CANCEL} のままであることを確認する。
     */
    @Test
    public void cancelButton_disposesDialogWithCancelAction() throws Exception {
        OpenProjectDialog dlg = buildDialog(Collections.emptyList(), null);

        String cancelLabel = Messages.get("dlg.openProject.cancel");
        JButton cancel = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), cancelLabel));
        assertNotNull("Cancel ボタンが存在するはず", cancel);

        GuiActionRunner.execute(() -> cancel.doClick());

        assertFalse("Cancel 後にダイアログは dispose されるはず", dlg.isDisplayable());
        assertEquals("Cancel 後の action は CANCEL のはず",
                OpenProjectDialog.Action.CANCEL, getAction(dlg));
    }

    /**
     * Browse ボタン (空リスト版) をクリックすると、ダイアログが dispose され
     * action が {@link OpenProjectDialog.Action#BROWSE} に設定されることを確認する。
     */
    @Test
    public void browseButton_emptyList_setsBrowseActionAndDisposes() throws Exception {
        OpenProjectDialog dlg = buildDialog(Collections.emptyList(), null);

        String browseLabel = Messages.get("dlg.openProject.browse");
        JButton browse = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), browseLabel));
        assertNotNull("Browse ボタンが存在するはず", browse);

        GuiActionRunner.execute(() -> browse.doClick());

        assertFalse("Browse クリック後にダイアログは dispose されるはず", dlg.isDisplayable());
        assertEquals("Browse クリック後の action は BROWSE のはず",
                OpenProjectDialog.Action.BROWSE, getAction(dlg));
    }

    /**
     * Browse ボタン (非空リスト版) をクリックすると、ダイアログが dispose され
     * action が {@link OpenProjectDialog.Action#BROWSE} に設定されることを確認する。
     */
    @Test
    public void browseButton_nonEmptyList_setsBrowseActionAndDisposes() throws Exception {
        List<ProjectRecord> records = Collections.singletonList(
                new ProjectRecord(1L, "/nonexistent", "Proj", System.currentTimeMillis()));

        OpenProjectDialog dlg = buildDialog(records, null);

        String browseNewLabel = Messages.get("dlg.openProject.browseNew");
        JButton browse = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), browseNewLabel));
        assertNotNull("Browse (新しいフォルダ) ボタンが存在するはず", browse);

        GuiActionRunner.execute(() -> browse.doClick());

        assertFalse("Browse クリック後にダイアログは dispose されるはず", dlg.isDisplayable());
        assertEquals("Browse クリック後の action は BROWSE のはず",
                OpenProjectDialog.Action.BROWSE, getAction(dlg));
    }

    /**
     * 実在ディレクトリを持つレコードで Open ボタンをクリックすると、
     * action が {@link OpenProjectDialog.Action#SELECTED}、
     * chosenRoot がそのディレクトリになり、ダイアログが dispose されることを確認する。
     */
    @Test
    public void openButton_setsSelectedActionAndChosenRoot() throws Exception {
        File existingDir = tmp.newFolder("SelectedProject");
        List<ProjectRecord> records = Collections.singletonList(
                new ProjectRecord(1L, existingDir.getAbsolutePath(), "SelectedProj",
                        System.currentTimeMillis()));

        OpenProjectDialog dlg = buildDialog(records, null);

        String openLabel = Messages.get("dlg.openProject.open");
        JButton open = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), openLabel));
        assertNotNull("Open ボタンが存在するはず", open);
        assertTrue("Open ボタンは有効のはず", GuiActionRunner.execute(() -> open.isEnabled()));

        GuiActionRunner.execute(() -> open.doClick());

        assertFalse("Open クリック後にダイアログは dispose されるはず", dlg.isDisplayable());
        assertEquals("Open クリック後の action は SELECTED のはず",
                OpenProjectDialog.Action.SELECTED, getAction(dlg));
        assertEquals("chosenRoot は選択したディレクトリのはず",
                existingDir, getChosenRoot(dlg));
    }

    /**
     * onDelete コールバックが設定されたとき、Remove ボタンが存在し有効であることを確認する。
     * 削除確認ダイアログ (JOptionPane) が出るため実際の削除フローはここでは検証しない
     * （headful のモーダル多重ネストを避けるため）。
     */
    @Test
    public void removeButton_existsAndEnabled_whenItemSelected() throws Exception {
        List<ProjectRecord> records = Collections.singletonList(
                new ProjectRecord(1L, "/any/path", "Proj", System.currentTimeMillis()));
        AtomicBoolean deleteCalled = new AtomicBoolean(false);
        Consumer<ProjectRecord> onDelete = rec -> deleteCalled.set(true);

        OpenProjectDialog dlg = buildDialog(records, onDelete);

        String removeLabel = Messages.get("dlg.openProject.remove");
        JButton remove = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), removeLabel));
        assertNotNull("Remove from list ボタンが存在するはず", remove);
        // 先頭レコードが自動選択されるため Remove は有効のはず
        assertTrue("先頭選択中は Remove ボタンが有効のはず",
                GuiActionRunner.execute(() -> remove.isEnabled()));
    }
}
