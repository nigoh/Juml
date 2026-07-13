// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link DetachedDiagramWindows} と {@link DiagramTabPane} の「図タブを別ウィンドウへ移動」
 * 連携を検証する。移動元からタブが剥がれ、別ウィンドウが 1 つ開くこと、そのウィンドウを
 * 破棄すると管理から外れることを確認する。
 *
 * <p>実 JFrame を生成するためヘッドレス環境ではスキップする。</p>
 */
public class DetachedDiagramWindowsTest {

    private static final int FIXED = 1;

    private JTabbedPane tabs;
    private ProjectAnalysisCache cache;
    private DiagramTabPane pane;
    private DetachedDiagramWindows windows;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        cache = new ProjectAnalysisCache();
        cache.setLoadedRootForTest(new java.io.File(System.getProperty("java.io.tmpdir")));
        GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            tabs.addTab("Utility", new javax.swing.JPanel());
            pane = new DiagramTabPane(tabs, FIXED, cache, new DiagramState(),
                    msg -> { }, zoom -> { });
            windows = new DetachedDiagramWindows(cache, null, pane, () -> false, 0, 0,
                    pane.notesBinder());
            pane.setOnMoveToNewWindow(windows::moveToNewWindow);
            pane.setCrossWindowFocus(k -> windows.focusExistingElsewhere(pane, k));
        });
    }

    @After
    public void tearDown() {
        GuiActionRunner.execute(() -> {
            if (windows != null) {
                windows.closeAll();
            }
            if (tabs != null) {
                tabs.removeAll();
            }
        });
    }

    private juml.core.formats.uml.JavaClassInfo classInfo(String fqn) {
        juml.core.formats.uml.JavaClassInfo ci = new juml.core.formats.uml.JavaClassInfo();
        int dot = fqn.lastIndexOf('.');
        if (dot >= 0) {
            ci.setPackageName(fqn.substring(0, dot));
            ci.setSimpleName(fqn.substring(dot + 1));
        } else {
            ci.setSimpleName(fqn);
        }
        return ci;
    }

    @Test
    public void moveToNewWindow_detachesFromSourceAndOpensOneWindow() {
        GuiActionRunner.execute(() ->
                pane.addOrFocusTab(TreeNodeOpenRequest.classNode(classInfo("com.example.Detach"))));
        assertEquals("前提: 動的タブ 1 枚", FIXED + 1,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertFalse("前提: 別ウィンドウは無い",
                GuiActionRunner.execute(() -> windows.hasOpenWindows()));

        GuiActionRunner.execute(() -> pane.moveActiveTabToNewWindow());

        assertEquals("移動元からタブが剥がれる", FIXED,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertTrue("別ウィンドウが 1 つ開く",
                GuiActionRunner.execute(() -> windows.hasOpenWindows()));
    }

    @Test
    public void closeAll_disposesDetachedWindows() {
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(classInfo("com.example.Close")));
            pane.moveActiveTabToNewWindow();
        });
        assertTrue("前提: 別ウィンドウが開いている",
                GuiActionRunner.execute(() -> windows.hasOpenWindows()));

        GuiActionRunner.execute(() -> windows.closeAll());

        assertFalse("closeAll で別ウィンドウが無くなる",
                GuiActionRunner.execute(() -> windows.hasOpenWindows()));
    }

    @Test
    public void reopeningMovedDiagram_focusesDetachedWindow_insteadOfDuplicating() {
        // 図を別ウィンドウへ移動 → メインで同じ図を開き直しても重複タブを作らず、
        // 既存の別ウィンドウへフォーカス委譲する (同一 notes キー二重束縛による付箋消失防止)。
        TreeNodeOpenRequest req =
                TreeNodeOpenRequest.classNode(classInfo("com.example.Dup"));
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(req);
            pane.moveActiveTabToNewWindow();
        });
        assertEquals("移動後メインは動的タブ 0", FIXED,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertTrue(GuiActionRunner.execute(() -> windows.hasOpenWindows()));

        // メインで同じキーを開き直す。
        GuiActionRunner.execute(() -> pane.addOrFocusTab(req));

        assertEquals("メインに重複タブを作らない (別ウィンドウへフォーカス委譲)", FIXED,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertTrue("別ウィンドウは残ったまま",
                GuiActionRunner.execute(() -> windows.hasOpenWindows()));
    }

    @Test
    public void moveToNewWindow_whenCacheNotLoaded_isNoOp() {
        // タブを開いた後にキャッシュを未ロード状態へ (プロジェクト再ロード相当)。
        GuiActionRunner.execute(() ->
                pane.addOrFocusTab(TreeNodeOpenRequest.classNode(classInfo("com.example.Reload"))));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        cache.clear(); // isLoaded() == false (プロジェクト(再)ロード中と同じ状態)

        GuiActionRunner.execute(() -> pane.moveActiveTabToNewWindow());

        assertEquals("ロード中はタブを剥がさない (図を失わない)", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertFalse("ロード中は別ウィンドウを開かない",
                GuiActionRunner.execute(() -> windows.hasOpenWindows()));
    }
}
