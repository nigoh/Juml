// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.SettingManager;
import juml.app.uml.DiagramKind;
import juml.app.uml.TreeNodeOpenRequest;
import juml.app.uml.UmlMainFrame;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 動的タブ機能の E2E 統合テスト。
 *
 * <p>ツリーノードをタブとして開く ({@code addOrFocusTab}) → タブにフォーカスが移ると
 * 左ツリーの由来ノードがハイライトされる (タブ ↔ リスト連動)、同一ノードの再オープンは
 * 既存タブにフォーカスするだけ (重複なし)、という挙動を実フレームで検証する。</p>
 *
 * <p>ヘッドレス環境では {@link Assume} でスキップ。{@code xvfb-run -a ./gradlew test} で実行。</p>
 */
public class UmlMainFrameTabLinkageIT {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private UmlMainFrame frame;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
    }

    @After
    public void teardown() {
        if (frame != null) {
            GuiActionRunner.execute(frame::dispose);
            frame = null;
        }
        SettingManager.resetForTest();
    }

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private File buildProject() throws IOException {
        File root = tmp.newFolder("TabSample");
        write(new File(root, "settings.gradle"), "rootProject.name = 'TabSample'\n");
        File pkg = new File(root, "src/main/java/com/demo");
        write(new File(pkg, "Foo.java"),
                "package com.demo; public class Foo { public void hello() {} }");
        write(new File(pkg, "Bar.java"),
                "package com.demo; public class Bar extends Foo {}");
        return root;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    private void openInNewTab(Object controller, TreeNodeOpenRequest req) throws Exception {
        Method m = controller.getClass().getMethod("onTreeOpenInNewTab", TreeNodeOpenRequest.class);
        GuiActionRunner.execute(() -> {
            try {
                m.invoke(controller, req);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static String selectionLabel(JTree tree) {
        return GuiActionRunner.execute(() -> {
            TreePath p = tree.getSelectionPath();
            return p == null ? "(none)" : String.valueOf(p.getLastPathComponent());
        });
    }

    private void selectKind(Object controller, DiagramKind kind) throws Exception {
        Method m = controller.getClass().getMethod("selectDiagramKind", DiagramKind.class);
        GuiActionRunner.execute(() -> {
            try {
                m.invoke(controller, kind);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static String focusedTitle(JTabbedPane tabs) {
        return GuiActionRunner.execute(() -> {
            int i = tabs.getSelectedIndex();
            return i < 0 ? "(none)" : tabs.getTitleAt(i);
        });
    }

    /**
     * 条件が成立するまで最大 {@code timeoutMs} だけ期限付きでポーリングする。
     *
     * <p>タブ生成・フォーカス連動 (ツリーハイライト) は EDT 上のリスナ経由で
     * 非同期に確定するため、固定 {@code Thread.sleep} で待つと遅い CI では
     * 回帰でなくても落ちる (flaky)。条件が満たされ次第すぐ返すことで、速い環境では
     * 高速・遅い環境では十分待つ、という両立をさせる。条件が満たされなくても例外は
     * 投げず、呼び出し側の assert に詳細な失敗メッセージを任せる
     * ({@code UmlMainFrameSwingTest#awaitLoadedTree} と同じ方針)。</p>
     */
    private static void await(long timeoutMs, BooleanSupplier cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
    }

    /**
     * 非同期ロード完了後、既定タブ生成などの後続処理が落ち着くまで「タブ数が 2 連続で
     * 同じ値になる (quiescent)」状態を期限付きで待つ。固定 {@code sleep(500)} を
     * 環境非依存の収束待ちへ置き換えるためのヘルパー。
     */
    private static void awaitTabCountQuiescent(JTabbedPane tabs, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int prev = -1;
        while (System.currentTimeMillis() < deadline) {
            int now = GuiActionRunner.execute(tabs::getTabCount);
            if (now >= 1 && now == prev) {
                return;
            }
            prev = now;
            Thread.sleep(80);
        }
    }

    @Test
    public void tabFocusHighlightsSourceNodeAndDedupes() throws Exception {
        File project = buildProject();
        frame = GuiActionRunner.execute(() -> {
            UmlMainFrame f = new UmlMainFrame(project);
            f.setSize(1200, 800);
            f.setVisible(true);
            return f;
        });

        Object cache = field(frame, "cache");
        Method isLoaded = cache.getClass().getMethod("isLoaded");
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline
                && !(Boolean) isLoaded.invoke(cache)) {
            Thread.sleep(150);
        }
        assertTrue("project should load", (Boolean) isLoaded.invoke(cache));

        Object controller = field(frame, "controller");
        JTabbedPane mainTabs = field(frame, "mainTabs");
        // 既定タブ生成が落ち着くまで収束待ち (固定 sleep(500) の置き換え)。
        awaitTabCountQuiescent(mainTabs, 8_000);
        Object treePanel = field(frame, "treePanel");
        JTree tree = field(treePanel, "tree");
        Object tabPane = field(frame, "tabPane");
        Map<?, ?> openTabs = field(tabPane, "openTabs");

        @SuppressWarnings("unchecked")
        List<JavaClassInfo> classes = (List<JavaClassInfo>) cache.getClass()
                .getMethod("getClasses").invoke(cache);
        JavaClassInfo foo = classes.stream()
                .filter(c -> "Foo".equals(c.getSimpleName())).findFirst().orElseThrow();
        JavaClassInfo bar = classes.stream()
                .filter(c -> "Bar".equals(c.getSimpleName())).findFirst().orElseThrow();

        // 起動直後に既定タブ (Common) が 1 枚開いているため、ここを基準に増分で検証する。
        int baseTabs = GuiActionRunner.execute(mainTabs::getTabCount);
        int baseOpen = openTabs.size();

        // --- open Foo as a tab; it is auto-focused -> tree should highlight Foo ---
        openInNewTab(controller, TreeNodeOpenRequest.classNode(foo));
        await(8_000, () -> openTabs.size() == baseOpen + 1
                && selectionLabel(tree).contains("Foo"));
        assertEquals("one more dynamic tab expected", baseOpen + 1, openTabs.size());
        assertEquals("tab count should grow by one",
                baseTabs + 1, (int) GuiActionRunner.execute(mainTabs::getTabCount));
        assertTrue("focusing Foo tab should highlight Foo node, got " + selectionLabel(tree),
                selectionLabel(tree).contains("Foo"));

        // --- open Bar as a tab; focus moves to Bar -> tree should highlight Bar ---
        openInNewTab(controller, TreeNodeOpenRequest.classNode(bar));
        await(8_000, () -> openTabs.size() == baseOpen + 2
                && selectionLabel(tree).contains("Bar"));
        assertEquals(baseOpen + 2, openTabs.size());
        assertTrue("focusing Bar tab should highlight Bar node, got " + selectionLabel(tree),
                selectionLabel(tree).contains("Bar"));

        // --- reopen Foo: existing tab is re-focused, no new tab, tree back to Foo ---
        openInNewTab(controller, TreeNodeOpenRequest.classNode(foo));
        await(8_000, () -> openTabs.size() == baseOpen + 2
                && selectionLabel(tree).contains("Foo"));
        assertEquals("reopening Foo must not create a new tab", baseOpen + 2, openTabs.size());
        assertTrue("re-focusing Foo tab should highlight Foo node, got " + selectionLabel(tree),
                selectionLabel(tree).contains("Foo"));
    }

    @Test
    public void toolbarActsOnFocusedMethodTab() throws Exception {
        File project = buildProject();
        frame = GuiActionRunner.execute(() -> {
            UmlMainFrame f = new UmlMainFrame(project);
            f.setSize(1200, 800);
            f.setVisible(true);
            return f;
        });

        Object cache = field(frame, "cache");
        Method isLoaded = cache.getClass().getMethod("isLoaded");
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline && !(Boolean) isLoaded.invoke(cache)) {
            Thread.sleep(150);
        }
        assertTrue((Boolean) isLoaded.invoke(cache));

        Object controller = field(frame, "controller");
        JTabbedPane mainTabs = field(frame, "mainTabs");
        // 既定タブ生成が落ち着くまで収束待ち (固定 sleep(500) の置き換え)。
        awaitTabCountQuiescent(mainTabs, 8_000);
        Object tabPane = field(frame, "tabPane");
        Map<?, ?> openTabs = field(tabPane, "openTabs");

        @SuppressWarnings("unchecked")
        List<JavaClassInfo> classes = (List<JavaClassInfo>) cache.getClass()
                .getMethod("getClasses").invoke(cache);
        JavaClassInfo foo = classes.stream()
                .filter(c -> "Foo".equals(c.getSimpleName())).findFirst().orElseThrow();
        JavaMethodInfo hello = foo.getMethods().get(0);

        // 起動直後に既定タブ (Common) が 1 枚開いているため、ここを基準に増分で検証する。
        int baseOpen = openTabs.size();

        // open Foo.hello as a sequence tab
        openInNewTab(controller, TreeNodeOpenRequest.method(foo, hello, DiagramKind.SEQUENCE));
        await(8_000, () -> openTabs.size() == baseOpen + 1);
        assertEquals(baseOpen + 1, openTabs.size());

        // with the method tab focused, the toolbar opens the same method's other diagrams
        selectKind(controller, DiagramKind.ACTIVITY);
        await(8_000, () -> openTabs.size() == baseOpen + 2
                && focusedTitle(mainTabs).contains("(act)"));
        assertEquals("Activity should open as a new tab", baseOpen + 2, openTabs.size());
        assertTrue("focused tab should be the activity view, got " + focusedTitle(mainTabs),
                focusedTitle(mainTabs).contains("(act)"));

        selectKind(controller, DiagramKind.CALLGRAPH);
        await(8_000, () -> openTabs.size() == baseOpen + 3
                && focusedTitle(mainTabs).contains("(cg)"));
        assertEquals("Call graph should open as a new tab", baseOpen + 3, openTabs.size());
        assertTrue("focused tab should be the call-graph view, got " + focusedTitle(mainTabs),
                focusedTitle(mainTabs).contains("(cg)"));

        // re-selecting an already-open kind just focuses the existing tab (dedupe)
        selectKind(controller, DiagramKind.ACTIVITY);
        await(4_000, () -> focusedTitle(mainTabs).contains("(act)"));
        assertEquals("re-selecting Activity must not create a new tab", baseOpen + 3, openTabs.size());
        assertTrue(focusedTitle(mainTabs).contains("(act)"));
    }
}
