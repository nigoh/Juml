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
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramTabPane} の核心振る舞いをユニットテスト。
 *
 * <p>検証対象:
 * <ol>
 *   <li>同一キーで {@code addOrFocusTab} を 2 回呼ぶとタブ数が増えず既存タブにフォーカスする</li>
 *   <li>{@code closeActiveTab} 後に該当タブが閉じる</li>
 *   <li>{@code reopenLastClosedTab} で直前に閉じたタブが復元される</li>
 *   <li>固定タブ {@code fixedSuffix} 境界で動的タブが固定タブの手前に挿入される</li>
 * </ol>
 * </p>
 *
 * <p>{@link DiagramTab} は内部で Swing コンポーネントを多数生成するため、
 * ヘッドレス環境では {@link java.awt.HeadlessException} が発生する。
 * {@link Assume#assumeFalse} でガードして headless CI ではスキップする。</p>
 */
public class DiagramTabPaneTest {

    /** 固定タブ数 (ユーティリティタブ相当。動的タブはこの手前に挿入される)。 */
    private static final int FIXED = 2;

    private JTabbedPane tabs;
    private ProjectAnalysisCache cache;
    private DiagramTabPane pane;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() throws Exception {
        // ProjectAnalysisCache の isLoaded() が true を返すよう projectRoot をセット。
        // load() を呼ぶと実プロジェクト解析が走るため、最小侵襲のリフレクションで注入する。
        cache = new ProjectAnalysisCache();
        Field rootField = ProjectAnalysisCache.class.getDeclaredField("projectRoot");
        rootField.setAccessible(true);
        rootField.set(cache, new java.io.File(System.getProperty("java.io.tmpdir")));

        GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            // 固定タブを 2 本追加して fixedSuffix=2 の環境を作る。
            tabs.addTab("Utility1", new javax.swing.JPanel());
            tabs.addTab("Utility2", new javax.swing.JPanel());
            DiagramState state = new DiagramState();
            pane = new DiagramTabPane(tabs, FIXED, cache, state,
                    msg -> { }, zoom -> { });
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

    // -------------------------------------------------------------------------
    // (a) 同一キーで addOrFocusTab を 2 回呼ぶとタブ数が増えず既存タブにフォーカスする
    // -------------------------------------------------------------------------

    @Test
    public void addOrFocusTab_sameKey_doesNotDuplicateTab() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Foo");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);

        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(req);
        });
        int countAfterFirst = GuiActionRunner.execute(() -> tabs.getTabCount());

        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(req);
        });
        int countAfterSecond = GuiActionRunner.execute(() -> tabs.getTabCount());

        assertEquals("同一キーの 2 度目の addOrFocusTab でタブ数が増えてはならない",
                countAfterFirst, countAfterSecond);
        // 動的タブが 1 本だけ (固定 2 本 + 動的 1 本 = 3)。
        assertEquals("動的タブは 1 本のみ存在するはず", FIXED + 1, countAfterFirst);
    }

    @Test
    public void addOrFocusTab_sameKey_focusesExistingTab() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Bar");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);

        GuiActionRunner.execute(() -> pane.addOrFocusTab(req));

        // 別の操作で固定タブ（最後の固定タブ）にフォーカスを移してから再度呼ぶ。
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(tabs.getTabCount() - 1));
        GuiActionRunner.execute(() -> pane.addOrFocusTab(req));

        // 動的タブ（インデックス 0）にフォーカスが戻っているはず。
        int selected = GuiActionRunner.execute(() -> tabs.getSelectedIndex());
        assertEquals("同一キーの addOrFocusTab は既存動的タブをフォーカスするはず", 0, selected);
    }

    // -------------------------------------------------------------------------
    // (b) closeActiveTab 後に該当タブが閉じる
    // -------------------------------------------------------------------------

    @Test
    public void closeActiveTab_removesTab() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Baz");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);

        GuiActionRunner.execute(() -> pane.addOrFocusTab(req));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());

        GuiActionRunner.execute(() -> pane.closeActiveTab());
        int after = GuiActionRunner.execute(() -> tabs.getTabCount());

        assertEquals("closeActiveTab でタブ数が 1 減るはず", before - 1, after);
        assertEquals("全タブを閉じると固定タブのみが残るはず", FIXED, after);
    }

    @Test
    public void closeActiveTab_whenNoActiveTab_isNoOp() {
        // 動的タブが 1 本もない状態で closeActiveTab を呼んでも例外が起きないこと。
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        GuiActionRunner.execute(() -> pane.closeActiveTab());
        int after = GuiActionRunner.execute(() -> tabs.getTabCount());

        assertEquals("動的タブなしの closeActiveTab は no-op であるはず", before, after);
    }

    // -------------------------------------------------------------------------
    // (c) reopenLastClosedTab で直前に閉じたタブが復元される
    // -------------------------------------------------------------------------

    @Test
    public void reopenLastClosedTab_restoresClosedTab() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Reopen");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);

        GuiActionRunner.execute(() -> pane.addOrFocusTab(req));
        GuiActionRunner.execute(() -> pane.closeActiveTab());
        int afterClose = GuiActionRunner.execute(() -> tabs.getTabCount());

        // 閉じたタブを再オープン。
        GuiActionRunner.execute(() -> pane.reopenLastClosedTab());
        int afterReopen = GuiActionRunner.execute(() -> tabs.getTabCount());

        assertEquals("reopenLastClosedTab でタブ数が 1 増えるはず", afterClose + 1, afterReopen);
        assertTrue("再オープン後は動的タブがアクティブになるはず", pane.hasActiveTab());
    }

    @Test
    public void reopenLastClosedTab_whenHistoryEmpty_isNoOp() {
        // 閉じた履歴がない状態で呼んでもタブ数は変わらず例外も出ない。
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        GuiActionRunner.execute(() -> pane.reopenLastClosedTab());
        int after = GuiActionRunner.execute(() -> tabs.getTabCount());

        assertEquals("履歴なしの reopenLastClosedTab は no-op であるはず", before, after);
    }

    // -------------------------------------------------------------------------
    // (d) fixedSuffix 境界で動的タブが固定タブの手前に挿入される
    // -------------------------------------------------------------------------

    @Test
    public void openDiagram_insertsBeforeFixedSuffix() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Insert");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);

        GuiActionRunner.execute(() -> pane.addOrFocusTab(req));

        // 動的タブは fixedSuffix=2 の手前 (インデックス 0) に挿入されるはず。
        // 末尾 2 本は固定タブ (Utility1, Utility2)。
        int total = GuiActionRunner.execute(() -> tabs.getTabCount());
        String lastTitle = GuiActionRunner.execute(() -> tabs.getTitleAt(total - 1));
        String secondLastTitle = GuiActionRunner.execute(() -> tabs.getTitleAt(total - 2));

        assertEquals("最後のタブは固定タブ Utility2 であるはず", "Utility2", lastTitle);
        assertEquals("最後から 2 番目は固定タブ Utility1 であるはず", "Utility1", secondLastTitle);
        assertFalse("動的タブが固定タブより後ろに来てはならない",
                tabs.getComponentAt(0) instanceof javax.swing.JPanel
                        && "Utility1".equals(tabs.getTitleAt(0)));
    }

    @Test
    public void multipleDynamicTabs_allInsertBeforeFixed() {
        juml.core.formats.uml.JavaClassInfo c1 = classInfo("com.example.Alpha");
        juml.core.formats.uml.JavaClassInfo c2 = classInfo("com.example.Beta");
        juml.core.formats.uml.JavaClassInfo c3 = classInfo("com.example.Gamma");

        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(c1));
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(c2));
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(c3));
        });

        int total = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("動的 3 本 + 固定 2 本 = 5 タブになるはず", FIXED + 3, total);

        // 末尾 2 本は必ず固定タブ。
        String lastTitle = GuiActionRunner.execute(() -> tabs.getTitleAt(total - 1));
        String secondLastTitle = GuiActionRunner.execute(() -> tabs.getTitleAt(total - 2));
        assertEquals("末尾タブは Utility2", "Utility2", lastTitle);
        assertEquals("末尾から 2 番目は Utility1", "Utility1", secondLastTitle);
    }

    // -------------------------------------------------------------------------
    // (e) LRU 自動閉鎖: 上限タブ数を超えたとき最古の非アクティブタブが閉じられる
    // -------------------------------------------------------------------------

    /**
     * {@code DiagramTabPane} を LRU 上限付きで再構築し、上限+1 本のタブを追加したあと
     * 動的タブ数が上限以下に収まることを確認する。
     *
     * <p>{@link TabMemoryManager} はコンストラクタ時点の {@code juml.maxDiagramTabs}
     * を読むため、プロパティのセットは {@code DiagramTabPane} 生成より前に行う。
     * テスト用の {@link JTabbedPane} と {@link DiagramTabPane} をこのテスト専用に
     * 構築し、既存の {@link #pane} / {@link #tabs} を汚染しない。</p>
     */
    @Test
    public void lruAutoClose_reducesTabCountToWithinLimit() throws Exception {
        final int limit = 3;
        System.setProperty("juml.maxDiagramTabs", String.valueOf(limit));
        // プロパティ設定後に DiagramTabPane を生成する。
        final JTabbedPane lruTabs = GuiActionRunner.execute(() -> {
            JTabbedPane t = new JTabbedPane();
            t.addTab("Utility1", new javax.swing.JPanel());
            t.addTab("Utility2", new javax.swing.JPanel());
            return t;
        });
        final DiagramTabPane lruPane = GuiActionRunner.execute(() ->
                new DiagramTabPane(lruTabs, FIXED, cache, new DiagramState(),
                        msg -> { }, zoom -> { }));
        try {
            // 上限+1 本追加する。最後の 1 本を追加した瞬間に最古タブが LRU で閉じられる。
            for (int i = 0; i < limit + 1; i++) {
                final int idx = i;
                GuiActionRunner.execute(() ->
                        lruPane.addOrFocusTab(
                                TreeNodeOpenRequest.classNode(classInfo("com.lru.C" + idx))));
            }

            int dynamicCount = GuiActionRunner.execute(() ->
                    lruTabs.getTabCount() - FIXED);

            assertTrue(
                    "LRU 自動閉鎖後の動的タブ数 (" + dynamicCount + ") は上限 ("
                            + limit + ") 以下であるはず",
                    dynamicCount <= limit);
        } finally {
            System.clearProperty("juml.maxDiagramTabs");
            GuiActionRunner.execute(() -> lruTabs.removeAll());
        }
    }

    @Test
    public void lruAutoClose_doesNotCloseActiveTab() throws Exception {
        final int limit = 2;
        System.setProperty("juml.maxDiagramTabs", String.valueOf(limit));
        // プロパティ設定後に DiagramTabPane を生成する。
        final JTabbedPane lruTabs = GuiActionRunner.execute(() -> {
            JTabbedPane t = new JTabbedPane();
            t.addTab("Utility1", new javax.swing.JPanel());
            t.addTab("Utility2", new javax.swing.JPanel());
            return t;
        });
        final DiagramTabPane lruPane = GuiActionRunner.execute(() ->
                new DiagramTabPane(lruTabs, FIXED, cache, new DiagramState(),
                        msg -> { }, zoom -> { }));
        try {
            juml.core.formats.uml.JavaClassInfo c0 = classInfo("com.lru.Active0");
            juml.core.formats.uml.JavaClassInfo c1 = classInfo("com.lru.Active1");
            juml.core.formats.uml.JavaClassInfo c2 = classInfo("com.lru.Active2");

            GuiActionRunner.execute(() -> {
                lruPane.addOrFocusTab(TreeNodeOpenRequest.classNode(c0));
                lruPane.addOrFocusTab(TreeNodeOpenRequest.classNode(c1));
                lruPane.addOrFocusTab(TreeNodeOpenRequest.classNode(c2)); // アクティブ
            });

            // アクティブタブが残っていること (hasActiveTab = true)。
            boolean active = GuiActionRunner.execute(() -> lruPane.hasActiveTab());
            assertTrue("LRU 後もアクティブタブが残っているはず", active);

            // 動的タブ数が上限以下であること。
            int dynamicCount = GuiActionRunner.execute(() ->
                    lruTabs.getTabCount() - FIXED);
            assertTrue(
                    "LRU 後の動的タブ数 (" + dynamicCount + ") は上限 (" + limit + ") 以下であるはず",
                    dynamicCount <= limit);
        } finally {
            System.clearProperty("juml.maxDiagramTabs");
            GuiActionRunner.execute(() -> lruTabs.removeAll());
        }
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /**
     * テスト用の最小 JavaClassInfo を生成する。
     * {@link juml.core.formats.uml.JavaClassInfo#getQualifiedName()} は
     * {@code packageName + "." + simpleName} で構成されるため、両フィールドをセットする。
     */
    private static juml.core.formats.uml.JavaClassInfo classInfo(String fqn) {
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
}
