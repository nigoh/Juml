// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JDialog;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * {@link DiagramTabPane} の一括クローズ (Close All / Close To Right) が未保存確認で
 * Cancel されたとき「1 つも閉じない」ことを検証する回帰テスト。
 *
 * <p>{@code closeTab(tab, key, recordForReopen)} は真偽値を返すようになり
 * (DiagramTabPane.java の private closeTab 群参照)、{@code closeOtherTabs}/
 * {@code closeTabsToRight} は最初の Cancel でループを中断する。修正前は Cancel しても
 * そこまでの分だけ閉じてしまう「部分クローズ」だった。</p>
 *
 * <p>未保存確認 ({@code confirmDiscardEdits}) は {@link javax.swing.JOptionPane} の
 * モーダルをハードコードで呼ぶため、確認方法を注入するテスト用の口が無い
 * ({@code confirmDiscardAllEdits(ToIntFunction)} と異なり、この経路には無い)。
 * そこで実際にモーダルダイアログを表示させ、表示された {@link JDialog} へ直接
 * {@link WindowEvent#WINDOW_CLOSING} をディスパッチして Cancel 相当
 * ({@code JOptionPane.CLOSED_OPTION}) を発生させる。実 OS のウィンドウマネージャや
 * キーボードフォーカスには依存しない Java レベルの決定的な手法なので、
 * {@code xvfb-run} (WM 無し) でも動作する。</p>
 */
public class DiagramTabPaneBulkCloseCancelTest {

    private static final int FIXED = 2;
    private static final String PUML = "@startuml\nclass A\n@enduml\n";
    private static final String EDITED = "@startuml\nclass Edited\n@enduml\n";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private JTabbedPane tabs;
    private DiagramTabPane pane;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境ではモーダルダイアログの表示検証ができないためスキップ"
                        + " (xvfb-run -a gradle test でラップしてください)",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            tabs.addTab("Utility1", new javax.swing.JPanel());
            tabs.addTab("Utility2", new javax.swing.JPanel());
            // エディタタブはプロジェクト未ロードでも開けるため cache は素のままでよい。
            pane = new DiagramTabPane(tabs, FIXED, new ProjectAnalysisCache(),
                    new DiagramState(), msg -> { }, zoom -> { });
        });
    }

    @After
    public void tearDown() {
        GuiActionRunner.execute(() -> {
            if (tabs != null) {
                tabs.removeAll();
            }
        });
    }

    /** 未保存 (dirty) な .puml エディタタブを 1 枚開く。 */
    private void openDirtyEditorTab(File file) {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, file));
        GuiActionRunner.execute(() -> pane.setActiveEditorText(EDITED));
    }

    /** 未保存マークの付かない (dirty でない) .puml エディタタブを 1 枚開く。 */
    private void openCleanEditorTab(File file) {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, file));
    }

    @Test
    public void closeAllTabs_cancelOnFirstDirtyTab_abortsWithoutClosingAny() throws Exception {
        // Arrange: 未保存タブ 1 枚を先頭に、保存済みタブ 2 枚を続けて開く
        // (挿入順: dirty1, clean2, clean3)。
        //
        // closeOtherTabs(null) は openTabs (LinkedHashMap = 挿入順) を先頭から
        // 走査するため、確認ダイアログは dirty1 の 1 回だけ発生する
        // (ダイアログを複数出すと、後続を Cancel し忘れて EDT が確認待ちのまま
        // 固まり、テストの意図が壊れるため 1 回に絞っている)。
        openDirtyEditorTab(tmp.newFile("a.puml"));
        openCleanEditorTab(tmp.newFile("b.puml"));
        openCleanEditorTab(tmp.newFile("c.puml"));
        int before = GuiActionRunner.execute(() -> pane.dynamicTabCount());
        assertEquals("前提: 動的タブ 3 枚", 3, before);

        // Act: closeAllTabs() を EDT へ非同期投入し、表示された確認ダイアログを
        // Cancel 相当 (WINDOW_CLOSING) で閉じる。
        SwingUtilities.invokeLater(pane::closeAllTabs);
        Window dlg = waitForVisibleDialog(5_000);
        assertNotNull("未保存確認ダイアログが表示されるはず", dlg);
        dismissAsCancel(dlg);

        // Assert: 先頭 (dirty1) で Cancel されたので 1 つも閉じない。
        // (修正前は dirty1 だけ残し clean2/clean3 は閉じてしまう「部分クローズ」だった。)
        int after = GuiActionRunner.execute(() -> pane.dynamicTabCount());
        assertEquals("Cancel で一括クローズは 1 つも閉じてはならない (部分クローズ禁止)",
                before, after);
    }

    @Test
    public void closeTabsToRightOfActive_cancelOnDirtyRightTab_abortsWithoutClosingAny()
            throws Exception {
        // Arrange: A(アクティブ/保存済み) - B(保存済み) - C(未保存, 右端) の順で開く。
        //
        // closeTabsToRight は右側の対象を「視覚上の右端から左へ」向かって 1 枚ずつ
        // 閉じる (DiagramTabPane.java の victims 逆順ループ参照)。よって右端の
        // C を未保存にしておけば、確認ダイアログは C の 1 回だけで済む
        // (B まで未保存にすると C→B の順で 2 回ダイアログが出てしまい、
        // 2 回目を Cancel し忘れると EDT が固まってテストの意図が壊れる)。
        openCleanEditorTab(tmp.newFile("left.puml"));
        openCleanEditorTab(tmp.newFile("mid.puml"));
        openDirtyEditorTab(tmp.newFile("right.puml"));
        int before = GuiActionRunner.execute(() -> pane.dynamicTabCount());
        assertEquals("前提: 動的タブ 3 枚", 3, before);

        // 左端 (A, index 0) をアクティブにしてから「右側をすべて閉じる」を実行する。
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(0));

        // Act: 右端 (未保存) の確認ダイアログが出たところを Cancel する。
        SwingUtilities.invokeLater(pane::closeTabsToRightOfActive);
        Window dlg = waitForVisibleDialog(5_000);
        assertNotNull("未保存確認ダイアログが表示されるはず", dlg);
        dismissAsCancel(dlg);

        // Assert: 右端で Cancel されたので 1 つも閉じない。
        // (修正前は右端が残り、その左の保存済みタブは閉じてしまう「部分クローズ」だった。)
        int after = GuiActionRunner.execute(() -> pane.dynamicTabCount());
        assertEquals("右側クローズが Cancel されたら 1 つも閉じてはならない (部分クローズ禁止)",
                before, after);
    }

    /** 表示中の {@link JDialog} を待つ (期限付きポーリング)。無ければ null。 */
    private static Window waitForVisibleDialog(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) {
                    return w;
                }
            }
            Thread.sleep(30);
        }
        return null;
    }

    /**
     * ダイアログを Cancel 相当 (CLOSED_OPTION) で閉じる。実 OS のウィンドウマネージャ/
     * キーボードフォーカスに依存せず、Java レベルで {@code WINDOW_CLOSING} を直接
     * ディスパッチする ({@code JOptionPane} の既定動作: 閉じるボタン/Escape と同じ扱い)。
     */
    private static void dismissAsCancel(Window dlg) throws InterruptedException {
        SwingUtilities.invokeLater(() ->
                dlg.dispatchEvent(new WindowEvent(dlg, WindowEvent.WINDOW_CLOSING)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (dlg.isShowing() && System.currentTimeMillis() < deadline) {
            Thread.sleep(30);
        }
    }
}
