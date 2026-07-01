// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidLayoutInfo;
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
 * {@link LayoutFileChooserDialog} のフィルタ・選択・結果返却を検証するテスト。
 *
 * <p>JDialog の生成には {@code DISPLAY} が必要なため、ヘッドレス環境では
 * {@link org.junit.Assume} でスキップする。</p>
 */
public class LayoutFileChooserDialogTest {

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

    private LayoutFileChooserDialog buildDialog(List<AndroidLayoutInfo> layouts) {
        LayoutFileChooserDialog dlg = GuiActionRunner.execute(
                () -> new LayoutFileChooserDialog(null, layouts));
        toDispose.add(dlg);
        return dlg;
    }

    /** テスト用の最小 AndroidLayoutInfo を生成する。 */
    private static AndroidLayoutInfo layout(String module, String sourceSet, String fileName) {
        AndroidLayoutInfo info = new AndroidLayoutInfo();
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
    private static JTextField getFilterField(LayoutFileChooserDialog dlg) throws Exception {
        Field f = LayoutFileChooserDialog.class.getDeclaredField("filter");
        f.setAccessible(true);
        return (JTextField) f.get(dlg);
    }

    /** 現在の list モデルサイズを反射で取得する（絞り込み後の件数チェック）。 */
    @SuppressWarnings("unchecked")
    private static int getModelSize(LayoutFileChooserDialog dlg) throws Exception {
        Field f = LayoutFileChooserDialog.class.getDeclaredField("model");
        f.setAccessible(true);
        DefaultListModel<AndroidLayoutInfo> model =
                (DefaultListModel<AndroidLayoutInfo>) f.get(dlg);
        return model.getSize();
    }

    // -------------------------------------------------------------------------
    // テストケース
    // -------------------------------------------------------------------------

    @Test
    public void emptyList_candidateCountIsZero() {
        LayoutFileChooserDialog dlg = buildDialog(Collections.emptyList());
        assertEquals("空リストのとき getCandidateCount() = 0 のはず", 0,
                dlg.getCandidateCount());
    }

    @Test
    public void nonEmptyList_candidateCountMatchesInput() {
        List<AndroidLayoutInfo> layouts = Arrays.asList(
                layout(":app", "main", "activity_main.xml"),
                layout(":app", "main", "fragment_home.xml"),
                layout(":lib", "main", "item_card.xml"));
        LayoutFileChooserDialog dlg = buildDialog(layouts);
        assertEquals("getCandidateCount() は入力件数と同じ 3 のはず", 3,
                dlg.getCandidateCount());
    }

    @Test
    public void selectedKeyIsNullInitially() {
        List<AndroidLayoutInfo> layouts = Collections.singletonList(
                layout(":app", "main", "activity_main.xml"));
        LayoutFileChooserDialog dlg = buildDialog(layouts);
        assertNull("ダイアログ構築直後は getSelectedKey() = null のはず",
                dlg.getSelectedKey());
    }

    @Test
    public void cancelButton_keepsSelectedKeyNull() {
        List<AndroidLayoutInfo> layouts = Collections.singletonList(
                layout(":app", "main", "activity_main.xml"));
        LayoutFileChooserDialog dlg = buildDialog(layouts);

        JButton cancel = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), Messages.get("dlg.cancel")));
        assertNotNull("Cancel ボタンが存在するはず", cancel);

        GuiActionRunner.execute(() -> cancel.doClick());

        assertFalse("Cancel 後はダイアログが dispose されるはず", dlg.isDisplayable());
        assertNull("Cancel 後は getSelectedKey() = null のはず", dlg.getSelectedKey());
    }

    @Test
    public void okButton_withItemSelected_setsSelectedKey() {
        List<AndroidLayoutInfo> layouts = Collections.singletonList(
                layout(":app", "main", "activity_main.xml"));
        LayoutFileChooserDialog dlg = buildDialog(layouts);

        JButton ok = GuiActionRunner.execute(
                () -> findButton(dlg.getContentPane(), Messages.get("dlg.ok")));
        assertNotNull("OK ボタンが存在するはず", ok);

        GuiActionRunner.execute(() -> ok.doClick());

        assertFalse("OK 後はダイアログが dispose されるはず", dlg.isDisplayable());
        assertNotNull("OK 後は getSelectedKey() が非 null のはず", dlg.getSelectedKey());
        // キーは moduleName::sourceSet::configQualifier::fileName 形式
        assertTrue("getSelectedKey() は 'activity_main.xml' を含むはず",
                dlg.getSelectedKey().contains("activity_main.xml"));
    }

    @Test
    public void filterText_narrowsVisibleItems() throws Exception {
        List<AndroidLayoutInfo> layouts = Arrays.asList(
                layout(":app", "main", "activity_main.xml"),
                layout(":app", "main", "fragment_home.xml"),
                layout(":lib", "main", "item_card.xml"));
        LayoutFileChooserDialog dlg = buildDialog(layouts);

        // 絞り込み前: 3 件全表示
        assertEquals("フィルタ前は 3 件が表示されるはず", 3, getModelSize(dlg));

        // "activity" で絞り込む → 1 件のみ
        JTextField filterField = getFilterField(dlg);
        GuiActionRunner.execute(() -> filterField.setText("activity"));

        assertEquals("'activity' フィルタ後は 1 件のみ表示されるはず", 1, getModelSize(dlg));
    }

    @Test
    public void filterText_emptyQuery_showsAllItems() throws Exception {
        List<AndroidLayoutInfo> layouts = Arrays.asList(
                layout(":app", "main", "activity_main.xml"),
                layout(":lib", "main", "item_card.xml"));
        LayoutFileChooserDialog dlg = buildDialog(layouts);

        JTextField filterField = getFilterField(dlg);
        // 一度フィルタをかける
        GuiActionRunner.execute(() -> filterField.setText("activity"));
        assertEquals("フィルタ後は 1 件のはず", 1, getModelSize(dlg));

        // フィルタをクリア → 全件復元
        GuiActionRunner.execute(() -> filterField.setText(""));
        assertEquals("フィルタクリア後は全 2 件が復元されるはず", 2, getModelSize(dlg));
    }

    @Test
    public void filterText_moduleNameMatch_narrowsItems() throws Exception {
        List<AndroidLayoutInfo> layouts = Arrays.asList(
                layout(":app", "main", "main.xml"),
                layout(":lib", "main", "sub.xml"),
                layout(":feature", "main", "feat.xml"));
        LayoutFileChooserDialog dlg = buildDialog(layouts);

        JTextField filterField = getFilterField(dlg);
        // ":lib" モジュール名でフィルタ (':'は除く)
        GuiActionRunner.execute(() -> filterField.setText("lib"));
        assertEquals("モジュール名 'lib' でフィルタすると 1 件のはず", 1, getModelSize(dlg));
    }
}
