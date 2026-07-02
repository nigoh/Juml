// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidNavigationGraphInfo;
import juml.util.Messages;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link NavigationFileChooserDialog} のフィルタ・選択・結果返却を検証するテスト。
 *
 * <p>{@link LayoutFileChooserDialogTest} と同じ構成で、
 * {@link AndroidNavigationGraphInfo} を対象とする。JDialog の生成には {@code DISPLAY} が
 * 必要なため、ヘッドレス環境では {@link org.junit.Assume} でスキップする。</p>
 */
public class NavigationFileChooserDialogTest {

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
    // ファクトリ / ヘルパ
    // -------------------------------------------------------------------------

    private NavigationFileChooserDialog buildDialog(
            List<AndroidNavigationGraphInfo> graphs) {
        NavigationFileChooserDialog dlg = GuiActionRunner.execute(
                () -> new NavigationFileChooserDialog(null, graphs));
        toDispose.add(dlg);
        return dlg;
    }

    /** テスト用の最小 AndroidNavigationGraphInfo を生成する。 */
    private static AndroidNavigationGraphInfo graph(String module, String sourceSet,
                                                    String fileName) {
        AndroidNavigationGraphInfo info = new AndroidNavigationGraphInfo();
        info.setModuleName(module);
        info.setSourceSet(sourceSet);
        info.setFileName(fileName);
        return info;
    }

    /** ボタンをラベルで探す (再帰)。 */
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

    /** フィルタフィールド {@code filter} を反射で取得する。 */
    private static JTextField getFilterField(NavigationFileChooserDialog dlg) throws Exception {
        Field f = NavigationFileChooserDialog.class.getDeclaredField("filter");
        f.setAccessible(true);
        return (JTextField) f.get(dlg);
    }

    /**
     * 現在の list モデルサイズを EDT 上で取得する（絞り込み後の件数チェック）。
     * {@link DefaultListModel} は Swing コンポーネントに属するため EDT から読む。
     */
    @SuppressWarnings("unchecked")
    private static int getModelSize(NavigationFileChooserDialog dlg) {
        try {
            Field f = NavigationFileChooserDialog.class.getDeclaredField("model");
            f.setAccessible(true);
            DefaultListModel<AndroidNavigationGraphInfo> model =
                    (DefaultListModel<AndroidNavigationGraphInfo>) f.get(dlg);
            return model.getSize();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("model フィールドへのアクセス失敗", e);
        }
    }

    // -------------------------------------------------------------------------
    // テストケース
    // -------------------------------------------------------------------------

    @Test
    public void emptyList_candidateCountIsZero() {
        NavigationFileChooserDialog dlg = buildDialog(Collections.emptyList());
        assertEquals("空リストのとき getCandidateCount() = 0 のはず", 0,
                dlg.getCandidateCount());
    }

    @Test
    public void nonEmptyList_candidateCountMatchesInput() {
        List<AndroidNavigationGraphInfo> graphs = Arrays.asList(
                graph(":app", "main", "nav_main.xml"),
                graph(":app", "main", "nav_auth.xml"),
                graph(":feature", "main", "nav_feature.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);
        assertEquals("getCandidateCount() は入力件数と同じ 3 のはず", 3,
                dlg.getCandidateCount());
    }

    @Test
    public void selectedKeyIsNullInitially() {
        List<AndroidNavigationGraphInfo> graphs = Collections.singletonList(
                graph(":app", "main", "nav_main.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);
        assertNull("ダイアログ構築直後は getSelectedKey() = null のはず",
                dlg.getSelectedKey());
    }

    @Test
    public void cancelButton_keepsSelectedKeyNull() {
        List<AndroidNavigationGraphInfo> graphs = Collections.singletonList(
                graph(":app", "main", "nav_main.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);

        JButton cancel = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), Messages.get("dlg.cancel")));
        assertNotNull("Cancel ボタンが存在するはず", cancel);

        GuiActionRunner.execute(() -> cancel.doClick());

        assertFalse("Cancel 後はダイアログが dispose されるはず", dlg.isDisplayable());
        assertNull("Cancel 後は getSelectedKey() = null のはず", dlg.getSelectedKey());
    }

    @Test
    public void okButton_withItemSelected_setsSelectedKey() {
        List<AndroidNavigationGraphInfo> graphs = Collections.singletonList(
                graph(":app", "main", "nav_main.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);

        JButton ok = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), Messages.get("dlg.ok")));
        assertNotNull("OK ボタンが存在するはず", ok);

        GuiActionRunner.execute(() -> ok.doClick());

        assertFalse("OK 後はダイアログが dispose されるはず", dlg.isDisplayable());
        assertNotNull("OK 後は getSelectedKey() が非 null のはず", dlg.getSelectedKey());
        // キーは moduleName::sourceSet::fileName 形式
        assertTrue("getSelectedKey() は 'nav_main.xml' を含むはず",
                dlg.getSelectedKey().contains("nav_main.xml"));
    }

    @Test
    public void filterText_narrowsVisibleItems() throws Exception {
        List<AndroidNavigationGraphInfo> graphs = Arrays.asList(
                graph(":app", "main", "nav_main.xml"),
                graph(":app", "main", "nav_auth.xml"),
                graph(":feature", "main", "nav_feature.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);

        // 絞り込み前: 3 件全表示 (DefaultListModel は Swing 属なので EDT から読む)
        int sizeBefore = GuiActionRunner.execute(() -> getModelSize(dlg));
        assertEquals("フィルタ前は 3 件が表示されるはず", 3, sizeBefore);

        // "auth" で絞り込む → 1 件のみ
        JTextField filterField = getFilterField(dlg);
        GuiActionRunner.execute(() -> filterField.setText("auth"));

        int sizeAfter = GuiActionRunner.execute(() -> getModelSize(dlg));
        assertEquals("'auth' フィルタ後は 1 件のみ表示されるはず", 1, sizeAfter);
    }

    @Test
    public void filterText_emptyQuery_showsAllItems() throws Exception {
        List<AndroidNavigationGraphInfo> graphs = Arrays.asList(
                graph(":app", "main", "nav_main.xml"),
                graph(":feature", "main", "nav_feature.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);

        JTextField filterField = getFilterField(dlg);
        // 一度フィルタをかける
        GuiActionRunner.execute(() -> filterField.setText("main_only"));
        // → 0 件
        assertEquals("存在しないキーワードでは 0 件のはず",
                0, (int) GuiActionRunner.execute(() -> getModelSize(dlg)));

        // フィルタをクリア → 全件復元
        GuiActionRunner.execute(() -> filterField.setText(""));
        assertEquals("フィルタクリア後は全 2 件が復元されるはず",
                2, (int) GuiActionRunner.execute(() -> getModelSize(dlg)));
    }

    @Test
    public void filterText_moduleNameMatch_narrowsItems() throws Exception {
        List<AndroidNavigationGraphInfo> graphs = Arrays.asList(
                graph(":app", "main", "nav_app.xml"),
                graph(":feature", "main", "nav_feature.xml"),
                graph(":core", "main", "nav_core.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);

        JTextField filterField = getFilterField(dlg);
        GuiActionRunner.execute(() -> filterField.setText("feature"));
        assertEquals("モジュール名 'feature' でフィルタすると 1 件のはず",
                1, (int) GuiActionRunner.execute(() -> getModelSize(dlg)));
    }

    /**
     * filter フィールドで Enter キーを押すと選択中の候補で確定するキーボード経路の検証。
     * {@link KeyEvent#KEY_PRESSED} イベントを EDT 上でディスパッチして経路を検証する。
     */
    @Test
    public void filterField_enterKey_commitsSelection() throws Exception {
        List<AndroidNavigationGraphInfo> graphs = Arrays.asList(
                graph(":app", "main", "nav_main.xml"));
        NavigationFileChooserDialog dlg = buildDialog(graphs);

        // filter フィールドを反射で取得 (ダイアログ構築直後はリスト先頭が選択済み)
        JTextField filterField = getFilterField(dlg);

        // Enter キーで KeyListener が commit() を呼ぶ経路を検証する。
        // 非表示コンポーネントへの dispatchEvent は KeyboardFocusManager に
        // 横取りされて届かないため、登録済みリスナーを EDT 上で直接起動する。
        GuiActionRunner.execute(() -> {
            KeyEvent ke = new KeyEvent(
                    filterField, KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), 0,
                    KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED);
            for (java.awt.event.KeyListener kl : filterField.getKeyListeners()) {
                kl.keyPressed(ke);
            }
        });

        assertFalse("Enter キー後はダイアログが dispose されるはず", dlg.isDisplayable());
        assertNotNull("Enter キー後は getSelectedKey() が非 null のはず", dlg.getSelectedKey());
        assertTrue("Enter キーで選択されたキーは 'nav_main.xml' を含むはず",
                dlg.getSelectedKey().contains("nav_main.xml"));
    }
}
