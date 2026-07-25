// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link CommandPalette#show} の実配線 (Enter/Esc/矢印移動/ダブルクリック/フォーカス喪失/
 * フィルタ絞り込み) を検証する (headful ガード付き)。
 *
 * <p>{@code matches} のあいまい一致ロジック自体は {@link CommandPaletteFilterTest} が
 * リフレクションで直接検証している。こちらは {@code show()} を実際に呼び、パッケージ内から
 * package-private な {@link CommandPalette.Command} を組み立てて配線 (KeyListener /
 * MouseListener / WindowFocusListener / DocumentListener) が実際に動くことを固定する。</p>
 *
 * <p>{@code show()} は {@link java.awt.Dialog.ModalityType#MODELESS} で開くためブロックしない。
 * よって直接呼んだ後、{@link Window#getWindows()} を期限付きポーリングして開いたダイアログを
 * 拾う ({@link NoteEditDialogTest} と同じパターン)。キー操作は実 OS フォーカスに依存する
 * {@code Robot} 経由の {@link KeyEvent} dispatch を避け、登録済み {@link KeyListener} /
 * {@link MouseListener} / {@link WindowFocusListener} を直接起動する
 * ({@link SketchViewportTest} の InputMap/ActionMap 直接解決と同じ焦点非依存の考え方)。</p>
 */
public class CommandPaletteTest {

    /** テスト内で開いた Window (owner フレーム含む) を @After で確実に破棄するためのリスト。 */
    private final List<Window> toDispose = new ArrayList<>();
    private JFrame owner;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では JDialog/JFrame の生成が失敗するためスキップ"
                        + " (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        owner = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.setSize(900, 700);
            f.setLocation(0, 0);
            f.setVisible(true);
            return f;
        });
        toDispose.add(owner);
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
    // (1) Enter で選択中コマンドの action が実行される
    // =========================================================================

    @Test
    public void enter_runsSelectedCommandAction() throws Exception {
        AtomicInteger executed = new AtomicInteger();
        List<CommandPalette.Command> commands = List.of(
                new CommandPalette.Command("Alpha Command", executed::incrementAndGet));

        JDialog dlg = openPaletteAsync(commands);
        assertNotNull("パレットダイアログが開かれるべき", dlg);
        JTextField filter = requireFilterField(dlg);

        // 空フィルタでは repopulate により先頭 (唯一のコマンド) が自動選択されているはず。
        assertEquals("空フィルタでは唯一のコマンドが自動選択されているはず",
                0, (int) GuiActionRunner.execute(() -> requireCommandList(dlg).getSelectedIndex()));

        GuiActionRunner.execute(() -> {
            firePressed(filter, KeyEvent.VK_ENTER);
            return null;
        });

        awaitTrue("Enter で選択中コマンドの action (SwingUtilities.invokeLater 経由) が"
                        + "実行されるべき",
                () -> executed.get() == 1, 3_000);
        assertEquals("action は 1 回だけ実行されるべき", 1, executed.get());
        assertFalse("Enter 実行後、パレットは dispose されるべき",
                GuiActionRunner.execute(dlg::isDisplayable));
    }

    // =========================================================================
    // (2) Esc でダイアログが dispose される
    // =========================================================================

    @Test
    public void esc_disposesDialogWithoutRunningAction() throws Exception {
        AtomicInteger executed = new AtomicInteger();
        List<CommandPalette.Command> commands = List.of(
                new CommandPalette.Command("Alpha Command", executed::incrementAndGet));

        JDialog dlg = openPaletteAsync(commands);
        assertNotNull("パレットダイアログが開かれるべき", dlg);
        JTextField filter = requireFilterField(dlg);

        GuiActionRunner.execute(() -> {
            firePressed(filter, KeyEvent.VK_ESCAPE);
            return null;
        });

        awaitTrue("Esc でダイアログが dispose されるべき",
                () -> !GuiActionRunner.execute(dlg::isDisplayable), 3_000);
        assertEquals("Esc はコマンドを実行しないはず", 0, executed.get());
    }

    // =========================================================================
    // (3) 無選択状態から Down で先頭・Up で末尾へ回り込む (moveSelection の既知バグ固定)
    // =========================================================================

    @Test
    public void moveSelection_fromNoSelection_downGoesToFirstAndUpWrapsToLast() throws Exception {
        List<CommandPalette.Command> commands = List.of(
                new CommandPalette.Command("Alpha", () -> { }),
                new CommandPalette.Command("Bravo", () -> { }),
                new CommandPalette.Command("Charlie", () -> { }));

        JDialog dlg = openPaletteAsync(commands);
        assertNotNull("パレットダイアログが開かれるべき", dlg);
        JTextField filter = requireFilterField(dlg);
        JList<CommandPalette.Command> list = requireCommandList(dlg);

        // 無選択状態を明示的に作る (クリアされた状態からの移動を検証するため)。
        GuiActionRunner.execute(() -> {
            list.clearSelection();
            return null;
        });
        assertEquals(-1, (int) GuiActionRunner.execute(list::getSelectedIndex));

        GuiActionRunner.execute(() -> {
            firePressed(filter, KeyEvent.VK_DOWN);
            return null;
        });
        assertEquals("無選択から Down は先頭 (index 0) を選ぶべき (下移動で2番目に"
                        + "飛んでしまう旧バグの固定)",
                0, (int) GuiActionRunner.execute(list::getSelectedIndex));

        GuiActionRunner.execute(() -> {
            list.clearSelection();
            return null;
        });
        assertEquals(-1, (int) GuiActionRunner.execute(list::getSelectedIndex));

        GuiActionRunner.execute(() -> {
            firePressed(filter, KeyEvent.VK_UP);
            return null;
        });
        assertEquals("無選択から Up は末尾 (index n-1) を選ぶべき",
                2, (int) GuiActionRunner.execute(list::getSelectedIndex));
    }

    // =========================================================================
    // (4) フィルタ文字入力で該当コマンドのみ残り先頭が自動選択される
    // =========================================================================

    @Test
    public void filterTyping_narrowsListToMatchesAndAutoSelectsFirst() throws Exception {
        List<CommandPalette.Command> commands = List.of(
                new CommandPalette.Command("Open Project", () -> { }),
                new CommandPalette.Command("Save As", () -> { }),
                new CommandPalette.Command("Close Tab", () -> { }));

        JDialog dlg = openPaletteAsync(commands);
        assertNotNull("パレットダイアログが開かれるべき", dlg);
        JTextField filter = requireFilterField(dlg);
        JList<CommandPalette.Command> list = requireCommandList(dlg);

        assertEquals("空フィルタでは全コマンドが並ぶはず",
                3, (int) GuiActionRunner.execute(() -> list.getModel().getSize()));

        // "op" は subsequence マッチで "Open Project" のみに一致 ("Save As"/"Close Tab" には
        // 'o'→'p' の順で現れない)。DocumentListener 経由の repopulate が実配線されていることを
        // 確認する。
        GuiActionRunner.execute(() -> {
            filter.setText("op");
            return null;
        });

        assertEquals("'op' に一致する 'Open Project' のみ残るはず",
                1, (int) GuiActionRunner.execute(() -> list.getModel().getSize()));
        String remaining = GuiActionRunner.execute(() -> list.getModel().getElementAt(0).label);
        assertEquals("残る唯一のコマンドは 'Open Project' のはず", "Open Project", remaining);
        assertEquals("絞り込み後は先頭が自動選択されるはず",
                0, (int) GuiActionRunner.execute(list::getSelectedIndex));
        assertEquals("選択中コマンドも 'Open Project' のはず", "Open Project",
                GuiActionRunner.execute(() -> list.getSelectedValue().label));
    }

    // =========================================================================
    // ダブルクリックで選択中コマンドの action が実行される (登録済み MouseListener を直接起動)
    // =========================================================================

    @Test
    public void doubleClickOnListItem_runsAction() throws Exception {
        AtomicInteger executed = new AtomicInteger();
        List<CommandPalette.Command> commands = List.of(
                new CommandPalette.Command("Alpha", () -> { }),
                new CommandPalette.Command("Bravo", executed::incrementAndGet));

        JDialog dlg = openPaletteAsync(commands);
        assertNotNull("パレットダイアログが開かれるべき", dlg);
        JList<CommandPalette.Command> list = requireCommandList(dlg);

        GuiActionRunner.execute(() -> {
            list.setSelectedIndex(1);
            return null;
        });

        // シングルクリック (clickCount=1) では実行されないはず。
        GuiActionRunner.execute(() -> {
            fireClicked(list, 1);
            return null;
        });
        assertEquals("シングルクリックではコマンドは実行されないはず", 0, executed.get());

        // ダブルクリック (clickCount=2) で実行される。
        GuiActionRunner.execute(() -> {
            fireClicked(list, 2);
            return null;
        });

        awaitTrue("ダブルクリックで選択中コマンドの action が実行されるべき",
                () -> executed.get() == 1, 3_000);
        assertFalse("ダブルクリック実行後、パレットは dispose されるべき",
                GuiActionRunner.execute(dlg::isDisplayable));
    }

    // =========================================================================
    // (5) フォーカス喪失で自動クローズする
    //
    // 実 OS フォーカスの奪い合いは xvfb 下でウィンドウマネージャ依存でフレーキーになりやすい
    // ため、実フォーカス遷移には依存せず、dlg に登録済みの WindowFocusListener
    // (windowLostFocus) を直接起動して「配線されている」ことを焦点非依存で固定する。
    // =========================================================================

    @Test
    public void windowLostFocus_disposesDialog() throws Exception {
        List<CommandPalette.Command> commands = List.of(
                new CommandPalette.Command("Alpha", () -> { }));

        JDialog dlg = openPaletteAsync(commands);
        assertNotNull("パレットダイアログが開かれるべき", dlg);

        GuiActionRunner.execute(() -> {
            WindowEvent evt = new WindowEvent(dlg, WindowEvent.WINDOW_LOST_FOCUS);
            for (WindowFocusListener wl : dlg.getWindowFocusListeners()) {
                wl.windowLostFocus(evt);
            }
            return null;
        });

        assertFalse("windowLostFocus でダイアログが dispose されるべき",
                GuiActionRunner.execute(dlg::isDisplayable));
    }

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    /**
     * {@link CommandPalette#show} を直接呼ぶ (同一パッケージなので package-private アクセス可)。
     * MODELESS なので {@code setVisible(true)} はブロックしない。呼び出し直後にダイアログが
     * {@link Window#getWindows()} へ現れるまで期限付きポーリングで待つ。
     */
    private JDialog openPaletteAsync(List<CommandPalette.Command> commands) throws InterruptedException {
        GuiActionRunner.execute(() -> {
            CommandPalette.show(owner, commands);
            return null;
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            JDialog found = GuiActionRunner.execute(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof JDialog && w != owner && w.isShowing()) {
                        return (JDialog) w;
                    }
                }
                return null;
            });
            if (found != null) {
                toDispose.add(found);
                return found;
            }
            Thread.sleep(30);
        }
        return null;
    }

    private static JTextField requireFilterField(JDialog dlg) {
        JTextField f = GuiActionRunner.execute(
                () -> findFirst((Container) dlg.getContentPane(), JTextField.class));
        assertNotNull("フィルタ用 JTextField が見つかるべき", f);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static JList<CommandPalette.Command> requireCommandList(JDialog dlg) {
        JList<?> l = GuiActionRunner.execute(
                () -> findFirst((Container) dlg.getContentPane(), JList.class));
        assertNotNull("コマンド一覧 JList が見つかるべき", l);
        return (JList<CommandPalette.Command>) l;
    }

    private static <T extends Component> T findFirst(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container) {
                T found = findFirst((Container) c, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 実 OS フォーカス下での {@code KeyEvent} dispatch (Robot 等) を避け、{@code target} に
     * 登録済みの {@link KeyListener} を直接起動する (フォーカス非依存)。
     */
    private static void firePressed(Component target, int keyCode) {
        KeyEvent evt = new KeyEvent(target, KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
        for (KeyListener kl : target.getKeyListeners()) {
            kl.keyPressed(evt);
        }
    }

    /** 登録済みの {@link MouseListener} を直接起動して {@code clickCount} 回クリックを模する。 */
    private static void fireClicked(Component target, int clickCount) {
        MouseEvent evt = new MouseEvent(target, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 5, 5, clickCount, false);
        for (MouseListener ml : target.getMouseListeners()) {
            ml.mouseClicked(evt);
        }
    }

    /**
     * 固定 {@code sleep} 一発ではなく期限付きポーリングで条件成立を待つ
     * ({@code SwingUtilities.invokeLater} 経由の非同期完了待ち)。
     */
    private static void awaitTrue(String message, BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(message, condition.getAsBoolean());
    }
}
