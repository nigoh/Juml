// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTreeFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.SettingManager;
import juml.app.uml.UmlMainFrame;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * UmlMainFrame (Swing) のスモークテスト。
 *
 * <p>AssertJ-Swing で Robot 経由でメインウィンドウを起動し、最小プロジェクトを
 * 開いてツリーが構築されるところまでを検証する。ヘッドレス環境
 * ({@link GraphicsEnvironment#isHeadless()}) では Robot が動かないため
 * テスト全体をスキップする。CI で実行する場合は xvfb-run でラップするか、
 * 仮想ディスプレイを起動した状態で {@code DISPLAY} を設定すること。</p>
 *
 * <p>テストの主目的は「ロードパイプライン (ProjectAnalysisCache + ProjectTreePanel)
 * が EDT 上で完走する」回帰検出。GUI の見た目を 1 ピクセルずつ検証する
 * 性質のものではない。</p>
 */
public class UmlMainFrameSwingTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private FrameFixture window;

    @Before
    public void setup() {
        Assume.assumeFalse("DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        // UmlMainFrame は Main.getSetting() 経由で SettingManager にアクセスするため、
        // テスト単独で構築する場合は明示的に初期化しておく必要がある。
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
    }

    @After
    public void teardown() {
        if (window != null) {
            window.cleanUp();
        }
        SettingManager.resetForTest();
    }

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /** Foo / Bar 2 クラスを持つ最小プロジェクトを生成。 */
    private File makeTinyProject() throws IOException {
        File root = tmp.newFolder("Tiny");
        File pkg = new File(root, "src/main/java/com/demo");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "Foo.java"),
                "package com.demo; public class Foo { public void hello() {} }");
        writeFile(new File(pkg, "Bar.java"),
                "package com.demo; public class Bar extends Foo {}");
        return root;
    }

    /**
     * プロジェクトのロード完了 (ツリーが表示され、ルート以下 2 行以上展開) を待ってツリー
     * フィクスチャを返す。ロードは非同期 SwingWorker のため、最初はツリーがまだ表示されて
     * おらず {@link org.assertj.swing.exception.ComponentLookupException} になりうる。
     * その間はリトライし、期限内にロードできなければ {@code null} を返す。
     *
     * <p>以前は {@code window.tree()} を {@code show()} 直後に 1 度だけ呼んでいたため、
     * ロードが間に合わないと即座に ComponentLookupException で落ちる競合があった。</p>
     */
    private JTreeFixture awaitLoadedTree() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                JTreeFixture tree = window.tree();
                int rows = GuiActionRunner.execute(() -> tree.target().getRowCount());
                if (rows >= 2) {
                    return tree;
                }
            } catch (org.assertj.swing.exception.ComponentLookupException notYet) {
                // ツリーがまだ表示されていない (非同期ロード中)。リトライする。
            }
            Thread.sleep(200);
        }
        return null;
    }

    @Test
    public void testMainFrameOpensAndLoadsProject() throws IOException, InterruptedException {
        File project = makeTinyProject();

        // EDT で UmlMainFrame を組み立て、初期プロジェクトを渡して構築する
        UmlMainFrame frame = GuiActionRunner.execute(
                () -> new UmlMainFrame(project));
        window = new FrameFixture(frame);
        window.show();

        // 非同期 SwingWorker でロードしているので、ツリーが表示されるまで待つ
        assertNotNull("プロジェクトツリーが構築されたはず", awaitLoadedTree());
    }

    /**
     * VS Code 風タブ中心モデルの回帰テスト: 左ペインのツリーで
     * 「モジュール / パッケージ / クラス」ノードをクリックすると、対応するダイアグラムタブが
     * 開かれてフォーカスされることを確認する。
     *
     * <p>旧モデルでは共有 Home ビューに "Scope: ..." を表示していたが、現在は各ノードが
     * 対等なタブ (= エディタ) として開く。</p>
     */
    @Test
    public void testTreeNodeClickOpensDiagramTab() throws Exception {
        File project = makeTinyProject();

        UmlMainFrame frame = GuiActionRunner.execute(
                () -> new UmlMainFrame(project));
        window = new FrameFixture(frame);
        window.show();

        // ロード完了 (ツリー表示 + ルート以下 2 行以上展開) を待ってツリーを取得
        JTreeFixture tree = awaitLoadedTree();
        assertNotNull("プロジェクトツリーが構築されたはず", tree);

        Object tabPane = getPrivate(frame, "tabPane");

        // モジュールノード ([module] ...) を選択 → ダイアグラムタブが開いてフォーカスされる
        TreePath modulePath = GuiActionRunner.execute(
                () -> findPathByPrefix(tree.target(), "[module]"));
        assertNotNull("module ノードが見つからない", modulePath);
        clickPath(tree, modulePath);
        assertTrue("モジュールクリックでダイアグラムタブが開かない",
                waitForDiagramTabFocused(tabPane, 5_000));

        // パッケージノード (com.demo (...)) を選択 → 別のタブが開いてフォーカスされる
        TreePath packagePath = GuiActionRunner.execute(
                () -> findPathByPrefix(tree.target(), "com.demo"));
        assertNotNull("package ノードが見つからない", packagePath);
        clickPath(tree, packagePath);
        assertTrue("パッケージクリックでダイアグラムタブが開かない",
                waitForDiagramTabFocused(tabPane, 5_000));

        // パッケージを展開してクラスノードを露出させた上でクリック
        GuiActionRunner.execute(() -> tree.target().expandPath(packagePath));
        TreePath classPath = GuiActionRunner.execute(
                () -> findPathByPrefix(tree.target(), "[C] Foo"));
        assertNotNull("class ノード [C] Foo が見つからない", classPath);
        clickPath(tree, classPath);
        assertTrue("クラスクリックでダイアグラムタブが開かない",
                waitForDiagramTabFocused(tabPane, 5_000));
        assertEquals("クラスタブのタイトルは Foo のはず", "Foo", focusedTabTitle(frame));
    }

    /** tabPane.dynamicTabFocused() が true になるまで待つ。 */
    private static boolean waitForDiagramTabFocused(Object tabPane, long timeoutMs)
            throws Exception {
        java.lang.reflect.Method m = tabPane.getClass().getMethod("dynamicTabFocused");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean focused = GuiActionRunner.execute(() -> {
                try {
                    return (Boolean) m.invoke(tabPane);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            if (focused) {
                return true;
            }
            Thread.sleep(80);
        }
        return false;
    }

    private static String focusedTabTitle(Object frame) throws Exception {
        Field f = frame.getClass().getDeclaredField("mainTabs");
        f.setAccessible(true);
        javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) f.get(frame);
        return GuiActionRunner.execute(() -> {
            int i = tabs.getSelectedIndex();
            return i < 0 ? "(none)" : tabs.getTitleAt(i);
        });
    }

    /** Swing ツリーで「先頭が prefix で始まるノード」最初のひとつへの TreePath を返す。 */
    private static TreePath findPathByPrefix(JTree tree, String prefix) {
        TreeModel model = tree.getModel();
        Object root = model.getRoot();
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath p = tree.getPathForRow(i);
            if (p == null) {
                continue;
            }
            Object last = p.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode) {
                Object u = ((DefaultMutableTreeNode) last).getUserObject();
                String text = String.valueOf(u);
                if (text.startsWith(prefix)) {
                    return p;
                }
            }
        }
        // 行未生成のサブツリーは expand してから再探索 (深さ 1 段だけ)
        for (int i = 0; i < model.getChildCount(root); i++) {
            tree.expandRow(i);
        }
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath p = tree.getPathForRow(i);
            if (p == null) {
                continue;
            }
            Object last = p.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode) {
                String text = String.valueOf(
                        ((DefaultMutableTreeNode) last).getUserObject());
                if (text.startsWith(prefix)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** EDT で TreePath を選択する (TreeSelectionListener を発火させるため)。 */
    private static void clickPath(JTreeFixture treeFixture, TreePath path) {
        GuiActionRunner.execute(() -> {
            treeFixture.target().setSelectionPath(path);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivate(Object target, String name)
            throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }
}
