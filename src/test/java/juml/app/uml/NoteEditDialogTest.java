// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link NoteEditDialog} の GUI 構造テスト (headful ガード付き)。
 *
 * <p>JDialog を生成するため Display が必要。ヘッドレス環境では
 * {@link Assume#assumeFalse} でスキップする
 * ({@code xvfb-run -a ./gradlew test --tests "*.NoteEditDialogTest"} で実行)。</p>
 *
 * <p>{@link NoteEditDialog#parseTags} の pure ロジックテストは
 * {@link NoteEditDialogParseTagsTest} に分離してあり、headless でも実行できる。</p>
 *
 * <p>モーダルダイアログを別スレッドで非同期に開き、すぐに構造を検証して
 * {@code @After} で dispose することでテストスレッドをブロックしない。</p>
 */
public class NoteEditDialogTest {

    /** 開いたダイアログを @After で確実に閉じるためのリスト。 */
    private final List<Window> toDispose = new ArrayList<>();

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では JDialog の生成が失敗するためスキップ"
                        + " (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
    }

    @After
    public void cleanup() {
        GuiActionRunner.execute(() -> {
            for (Window w : toDispose) {
                if (w.isDisplayable()) {
                    w.dispose();
                }
            }
            return null;
        });
        toDispose.clear();
    }

    // =========================================================================
    // ダイアログ構造テスト
    // =========================================================================

    @Test
    public void dialog_hasOkButton() throws Exception {
        JDialog dlg = openDialogAsync("hello", Collections.emptyList());
        assertNotNull("ダイアログが開かれるべき", dlg);

        List<String> buttons = GuiActionRunner.execute(
                () -> collectButtonLabels((Container) dlg.getContentPane()));
        String okLabel = Messages.get("note.edit.ok");
        assertTrue("OK ボタンが存在するべき", buttons.contains(okLabel));
    }

    @Test
    public void dialog_hasCancelButton() throws Exception {
        JDialog dlg = openDialogAsync("hello", Collections.emptyList());
        assertNotNull("ダイアログが開かれるべき", dlg);

        List<String> buttons = GuiActionRunner.execute(
                () -> collectButtonLabels((Container) dlg.getContentPane()));
        String cancelLabel = Messages.get("note.edit.cancel");
        assertTrue("Cancel ボタンが存在するべき", buttons.contains(cancelLabel));
    }

    @Test
    public void dialog_escapeKeyIsRegistered() throws Exception {
        JDialog dlg = openDialogAsync("", Collections.emptyList());
        assertNotNull("ダイアログが開かれるべき", dlg);

        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        Object actionKey = GuiActionRunner.execute(() ->
                dlg.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(escape));
        assertNotNull("Escape キーがルートペインに登録されているべき", actionKey);
    }

    @Test
    public void dialog_defaultButtonIsOk() throws Exception {
        JDialog dlg = openDialogAsync("", Collections.emptyList());
        assertNotNull("ダイアログが開かれるべき", dlg);

        JButton defaultButton = GuiActionRunner.execute(
                () -> dlg.getRootPane().getDefaultButton());
        assertNotNull("デフォルトボタンが設定されているべき", defaultButton);
        assertEquals("デフォルトボタンは OK であるべき",
                Messages.get("note.edit.ok"), defaultButton.getText());
    }

    @Test
    public void dialog_okButtonOrderBeforeCancel() throws Exception {
        JDialog dlg = openDialogAsync("some text", Collections.emptyList());
        assertNotNull("ダイアログが開かれるべき", dlg);

        List<String> buttons = GuiActionRunner.execute(
                () -> collectButtonLabels((Container) dlg.getContentPane()));
        String okLabel = Messages.get("note.edit.ok");
        String cancelLabel = Messages.get("note.edit.cancel");
        int okIdx = buttons.indexOf(okLabel);
        int cancelIdx = buttons.indexOf(cancelLabel);
        assertTrue("OK ボタンが存在するべき", okIdx >= 0);
        assertTrue("Cancel ボタンが存在するべき", cancelIdx >= 0);
        assertTrue("OK ボタンは Cancel より前に配置されるべき", okIdx < cancelIdx);
    }

    @Test
    public void dialog_okButtonIsEnabledInitially() throws Exception {
        // 現状の NoteEditDialog は OK ボタンを常に有効にしている (入力検証なし)。
        // OK ボタンが初期状態で有効であることを確認する。
        JDialog dlg = openDialogAsync("initial text", Collections.emptyList());
        assertNotNull("ダイアログが開かれるべき", dlg);

        String okLabel = Messages.get("note.edit.ok");
        boolean enabled = GuiActionRunner.execute(() -> {
            for (JButton b : collectButtons((Container) dlg.getContentPane())) {
                if (okLabel.equals(b.getText())) {
                    return b.isEnabled();
                }
            }
            return false;
        });
        assertTrue("OK ボタンは初期状態で有効であるべき", enabled);
    }

    @Test
    public void dialog_okButtonEnabledWhenEmpty() throws Exception {
        // NoteEditDialog は現状「空テキスト」でも OK を押せる設計 (確定後に空メモは外側で削除)。
        // この挙動が意図通りであることを確認するリグレッションテスト。
        // もし将来 empty text で OK を disable する実装が入ったら、このテストを修正する。
        JDialog dlg = openDialogAsync("", Collections.emptyList());
        assertNotNull("ダイアログが開かれるべき", dlg);

        String okLabel = Messages.get("note.edit.ok");
        boolean enabled = GuiActionRunner.execute(() -> {
            for (JButton b : collectButtons((Container) dlg.getContentPane())) {
                if (okLabel.equals(b.getText())) {
                    return b.isEnabled();
                }
            }
            return false;
        });
        // 現在の実装では空テキストでも OK は有効。ヒントには「空のままキャンセルで削除」と記載。
        assertTrue("空テキストでも OK ボタンは有効であるべき (空確定=削除の設計)", enabled);
    }

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    /**
     * モーダルダイアログを別スレッドで開いて直ちに操作できる {@link JDialog} を返す。
     *
     * <p>{@link NoteEditDialog#edit} は {@link JDialog#setVisible(boolean)} でブロックするため、
     * {@link SwingUtilities#invokeLater} で EDT に積んだ後、ダイアログが showing になるまで
     * 期限付きポーリングで待つ。</p>
     */
    private JDialog openDialogAsync(String initial, List<String> initialTags) throws Exception {
        // invokeLater で EDT に積む (setVisible(true) はここでブロックするが EDT キューには入る)。
        SwingUtilities.invokeLater(() -> NoteEditDialog.edit(null, initial, initialTags));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            JDialog found = GuiActionRunner.execute(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog && w.isShowing()) {
                        return (JDialog) w;
                    }
                }
                return null;
            });
            if (found != null) {
                toDispose.add(found);
                return found;
            }
            Thread.sleep(50);
        }
        return null;
    }

    private static List<String> collectButtonLabels(Container root) {
        List<String> labels = new ArrayList<>();
        for (JButton b : collectButtons(root)) {
            labels.add(b.getText());
        }
        return labels;
    }

    private static List<JButton> collectButtons(Container root) {
        List<JButton> buttons = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JButton) {
                buttons.add((JButton) c);
            }
            if (c instanceof Container) {
                buttons.addAll(collectButtons((Container) c));
            }
        }
        return buttons;
    }
}
