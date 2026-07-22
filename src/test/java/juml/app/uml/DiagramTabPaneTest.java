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
        // @Before メソッドの実行順は JUnit 4 では未定義のため、requireDisplay に頼らず
        // setUp() 自体もヘッドレスガードを入れて順序依存を除去する。
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
        // ProjectAnalysisCache の isLoaded() が true を返すようテスト用フックで注入する
        // (load() を呼ぶと実プロジェクト解析が走るため)。
        cache = new ProjectAnalysisCache();
        cache.setLoadedRootForTest(new java.io.File(System.getProperty("java.io.tmpdir")));

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
        assertTrue("再オープン後は動的タブがアクティブになるはず",
                GuiActionRunner.execute(() -> pane.hasActiveTab()));
    }

    @Test
    public void reopenClosedDiagramTab_whileProjectUnloaded_keepsHistory() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.KeepHist");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);
        GuiActionRunner.execute(() -> pane.addOrFocusTab(req));
        GuiActionRunner.execute(() -> pane.closeActiveTab());
        assertEquals(1, (int) GuiActionRunner.execute(() -> pane.closedTabHistorySize()));

        // プロジェクト未ロード (再読込中/キャンセル後相当) では openDiagram が no-op に
        // なるため、履歴を消費せず案内だけ出す。
        cache.clear();
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        GuiActionRunner.execute(() -> pane.reopenLastClosedTab());
        assertEquals("未ロード中はタブを復元しない", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("履歴は消費されず残るはず", 1,
                (int) GuiActionRunner.execute(() -> pane.closedTabHistorySize()));

        // 読み込みが完了すれば同じ履歴から復元できる。
        cache.setLoadedRootForTest(new java.io.File(System.getProperty("java.io.tmpdir")));
        GuiActionRunner.execute(() -> pane.reopenLastClosedTab());
        assertEquals(before + 1, (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
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
        // lruTabs の生成は juml.maxDiagramTabs に依存しない (try 外で構築)。
        final JTabbedPane lruTabs = GuiActionRunner.execute(() -> {
            JTabbedPane t = new JTabbedPane();
            t.addTab("Utility1", new javax.swing.JPanel());
            t.addTab("Utility2", new javax.swing.JPanel());
            return t;
        });
        try {
            // setProperty を try 内に移動し、finally で必ず clearProperty される保証を得る。
            System.setProperty("juml.maxDiagramTabs", String.valueOf(limit));
            // プロパティ設定後に DiagramTabPane を生成する。
            final DiagramTabPane lruPane = GuiActionRunner.execute(() ->
                    new DiagramTabPane(lruTabs, FIXED, cache, new DiagramState(),
                            msg -> { }, zoom -> { }));
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
        // lruTabs の生成は juml.maxDiagramTabs に依存しない (try 外で構築)。
        final JTabbedPane lruTabs = GuiActionRunner.execute(() -> {
            JTabbedPane t = new JTabbedPane();
            t.addTab("Utility1", new javax.swing.JPanel());
            t.addTab("Utility2", new javax.swing.JPanel());
            return t;
        });
        try {
            // setProperty を try 内に移動し、finally で必ず clearProperty される保証を得る。
            System.setProperty("juml.maxDiagramTabs", String.valueOf(limit));
            // プロパティ設定後に DiagramTabPane を生成する。
            final DiagramTabPane lruPane = GuiActionRunner.execute(() ->
                    new DiagramTabPane(lruTabs, FIXED, cache, new DiagramState(),
                            msg -> { }, zoom -> { }));
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
    // (f) Round 1 新挙動: dynamicTabCount / closedTabHistorySize / hasTabsToRightOfActive
    // -------------------------------------------------------------------------

    @Test
    public void dynamicTabCount_initiallyZero() {
        int count = GuiActionRunner.execute(() -> pane.dynamicTabCount());
        assertEquals("初期状態では動的タブは 0 枚のはず", 0, count);
    }

    @Test
    public void dynamicTabCount_afterOpeningTabs_returnsCorrectCount() {
        juml.core.formats.uml.JavaClassInfo c1 = classInfo("com.example.A");
        juml.core.formats.uml.JavaClassInfo c2 = classInfo("com.example.B");

        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(c1));
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(c2));
        });

        int count = GuiActionRunner.execute(() -> pane.dynamicTabCount());
        assertEquals("2 枚タブを開いたあとは dynamicTabCount() = 2 のはず", 2, count);
    }

    @Test
    public void closedTabHistorySize_initiallyZero() {
        int size = GuiActionRunner.execute(() -> pane.closedTabHistorySize());
        assertEquals("初期状態では閉じタブ履歴は 0 件のはず", 0, size);
    }

    @Test
    public void closedTabHistorySize_afterClosingTab_incrementsByOne() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Hist");
        GuiActionRunner.execute(() -> pane.addOrFocusTab(TreeNodeOpenRequest.classNode(ci)));
        GuiActionRunner.execute(() -> pane.closeActiveTab());

        int size = GuiActionRunner.execute(() -> pane.closedTabHistorySize());
        assertEquals("タブを 1 枚閉じたあとは closedTabHistorySize() = 1 のはず", 1, size);
    }

    @Test
    public void hasTabsToRightOfActive_falseWhenNoTabFocused() {
        // 動的タブ未選択 (ユーティリティタブが選択中) → false
        boolean result = GuiActionRunner.execute(() -> pane.hasTabsToRightOfActive());
        assertFalse("動的タブ未選択のとき hasTabsToRightOfActive() は false のはず", result);
    }

    @Test
    public void hasTabsToRightOfActive_falseWhenSingleDynamicTab() {
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Solo");
        GuiActionRunner.execute(() -> pane.addOrFocusTab(TreeNodeOpenRequest.classNode(ci)));

        // 1 枚しかないので右隣は存在しない
        boolean result = GuiActionRunner.execute(() -> pane.hasTabsToRightOfActive());
        assertFalse("動的タブが 1 枚 (右端) のとき hasTabsToRightOfActive() は false のはず",
                result);
    }

    @Test
    public void hasTabsToRightOfActive_trueWhenFirstOfTwoDynamicTabsActive() {
        juml.core.formats.uml.JavaClassInfo c1 = classInfo("com.example.Left");
        juml.core.formats.uml.JavaClassInfo c2 = classInfo("com.example.Right");

        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(c1)); // 左 (最初)
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(c2)); // 右 (後から)
            // 左 (インデックス 0) をアクティブに戻す
            tabs.setSelectedIndex(0);
        });

        boolean result = GuiActionRunner.execute(() -> pane.hasTabsToRightOfActive());
        assertTrue("左端タブが選択中のとき hasTabsToRightOfActive() は true のはず", result);
    }

    // -------------------------------------------------------------------------
    // (g) closeActiveTab(): ユーティリティタブ選択中はタブを閉じない
    // -------------------------------------------------------------------------

    @Test
    public void closeActiveTab_utilityTabSelected_doesNotReduceTabCount() {
        // 動的タブを 1 枚追加してからユーティリティタブへ移動
        juml.core.formats.uml.JavaClassInfo ci = classInfo("com.example.Util");
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(TreeNodeOpenRequest.classNode(ci));
            // ユーティリティタブ (末尾) を選択する
            tabs.setSelectedIndex(tabs.getTabCount() - 1);
        });

        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        GuiActionRunner.execute(() -> pane.closeActiveTab());
        int after = GuiActionRunner.execute(() -> tabs.getTabCount());

        assertEquals("ユーティリティタブ選択中の closeActiveTab はタブ数を変えてはならない",
                before, after);
    }

    // -------------------------------------------------------------------------
    // (h) バルククローズ: closeAllTabs / closeOtherTabsExceptActive / closeTabsToRightOfActive
    //     DiagramTabPane.java:504-558 の package-private メソッドを同一パッケージから直接呼ぶ。
    // -------------------------------------------------------------------------

    @Test
    public void closeAllTabs_removesAllDynamicTabs() {
        // Arrange: 3 枚の動的タブを追加
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.CloseAll1")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.CloseAll2")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.CloseAll3")));
        });
        assertEquals("前提: 動的 3 枚 + 固定 2 枚 = 5 タブ", FIXED + 3,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));

        // Act
        GuiActionRunner.execute(() -> pane.closeAllTabs());

        // Assert: 固定タブのみが残る
        int remaining = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("closeAllTabs() 後は固定タブのみが残るはず", FIXED, remaining);
        assertFalse("closeAllTabs() 後は動的タブが存在しないはず",
                GuiActionRunner.execute(() -> pane.hasActiveTab()));
    }

    @Test
    public void closeOtherTabsExceptActive_keepsOnlyActiveTab() {
        // Arrange: A, B, C を追加 → 最後に追加した C がアクティブ
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.OtherA")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.OtherB")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.OtherC")));
        });

        // Act
        GuiActionRunner.execute(() -> pane.closeOtherTabsExceptActive());

        // Assert: アクティブタブ(C) 1 枚 + 固定タブ 2 枚
        int total = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("closeOtherTabsExceptActive() 後は動的 1 枚 + 固定 2 枚 = 3 のはず",
                FIXED + 1, total);
        assertTrue("closeOtherTabsExceptActive() 後もアクティブタブが残るはず",
                GuiActionRunner.execute(() -> pane.hasActiveTab()));
    }

    /**
     * ユーティリティタブ選択中に {@code closeOtherTabsExceptActive()} を呼ぶと
     * {@code activeKey=null} で {@code closeOtherTabs(null)} が走るため
     * 全動的タブが閉じる。これは「Close Others」の文脈では副作用に見えるが、
     * 現行の実装仕様として本テストで固定する。
     * {@code DiagramTabPane.java:524-534} の null 処理と合わせて読むこと。
     */
    @Test
    public void closeOtherTabsExceptActive_whenUtilityTabSelected_closesAllDynamic() {
        // Arrange: 2 枚追加してからユーティリティタブへ移動
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.UtilSel1")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.UtilSel2")));
            // ユーティリティタブ (末尾) を選択する
            tabs.setSelectedIndex(tabs.getTabCount() - 1);
        });

        // Act
        GuiActionRunner.execute(() -> pane.closeOtherTabsExceptActive());

        // Assert: activeKey=null → closeOtherTabs(null) で全動的タブが閉じる
        int dynamicCount = GuiActionRunner.execute(() -> pane.dynamicTabCount());
        assertEquals(
                "ユーティリティタブ選択中は activeKey=null → 全動的タブが閉じるはず (仕様)", 0,
                dynamicCount);
    }

    @Test
    public void closeTabsToRightOfActive_removesOnlyRightSideTabs() {
        // Arrange: A(0), B(1), C(2) を追加し、中央タブ B をアクティブにする
        //   insertAt = tabCount - fixedSuffix なので末尾固定タブの直前に積まれる。
        //   追加後: [A(0), B(1), C(2), Utility1(3), Utility2(4)]、C が選択中。
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.RightA")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.RightB")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.RightC")));
            // 中央タブ B (index 1) をアクティブにする
            tabs.setSelectedIndex(1);
        });

        // Act
        GuiActionRunner.execute(() -> pane.closeTabsToRightOfActive());

        // Assert: C が閉じ、A と B が残る → 動的 2 枚 + 固定 2 枚 = 4
        int total = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals(
                "closeTabsToRightOfActive() は B より右の C を閉じ、4 タブが残るはず",
                FIXED + 2, total);
    }

    @Test
    public void closeTabsToRightOfActive_whenLastTabActive_isNoOp() {
        // Arrange: A, B, C を追加 → C が最後に追加されアクティブ (右端動的タブ)
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.LastA")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.LastB")));
            pane.addOrFocusTab(
                    TreeNodeOpenRequest.classNode(classInfo("com.example.LastC")));
        });
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());

        // Act: C は動的タブの右端 → closeTabsToRight(C_key) のループで i > idx が成立しない
        GuiActionRunner.execute(() -> pane.closeTabsToRightOfActive());

        // Assert: タブ数が変わらない
        int after = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("右端タブが選択中のとき closeTabsToRightOfActive() は no-op のはず",
                before, after);
    }

    // -------------------------------------------------------------------------
    // (i) メソッド図の図種トグル (SEQUENCE ⇄ ACTIVITY) はタブを複製せずその場で切替
    // -------------------------------------------------------------------------

    @Test
    public void switchActiveMethodKind_togglesInPlaceWithoutAddingTab() {
        TreeNodeOpenRequest seq = methodReq("com.example.Svc", "handle", DiagramKind.SEQUENCE);
        GuiActionRunner.execute(() -> pane.addOrFocusTab(seq));

        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("前提: シーケンス図タブがアクティブ", DiagramKind.SEQUENCE,
                GuiActionRunner.execute(() -> pane.activeTabKind()));

        // シーケンス → アクティビティへその場切替。
        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.ACTIVITY));

        int after = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("図種トグルではタブ数が増えてはならない (複製しない)", before, after);
        assertEquals("トグル後はアクティビティ図になっているはず", DiagramKind.ACTIVITY,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
        // 由来ノード (ツリー同期用) の図種も更新されているはず。
        assertEquals("focusedTabRequest の図種も ACTIVITY に更新されるはず", DiagramKind.ACTIVITY,
                GuiActionRunner.execute(() -> pane.focusedTabRequest().kind));
    }

    @Test
    public void switchActiveMethodKind_togglesBackToSequence() {
        TreeNodeOpenRequest seq = methodReq("com.example.Svc2", "run", DiagramKind.SEQUENCE);
        GuiActionRunner.execute(() -> pane.addOrFocusTab(seq));
        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.ACTIVITY));
        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.SEQUENCE));

        assertEquals("往復トグル後はシーケンス図に戻るはず", DiagramKind.SEQUENCE,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
        assertEquals("往復してもタブは 1 枚のまま", FIXED + 1,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
    }

    @Test
    public void switchActiveMethodKind_switchesToCallGraphInPlace() {
        // シーケンス → コールグラフもタブを複製せずその場で切替できること
        // (メソッド系トグルはトップツールバーから外し、タブ上部の切替バーへ一本化)。
        TreeNodeOpenRequest seq = methodReq("com.example.Cg", "start", DiagramKind.SEQUENCE);
        GuiActionRunner.execute(() -> pane.addOrFocusTab(seq));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());

        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.CALLGRAPH));

        assertEquals("コールグラフへの切替でタブ数は増えない (複製しない)", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("トグル後はコールグラフになっているはず", DiagramKind.CALLGRAPH,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
        assertEquals("focusedTabRequest の図種も CALLGRAPH に更新されるはず", DiagramKind.CALLGRAPH,
                GuiActionRunner.execute(() -> pane.focusedTabRequest().kind));
    }

    @Test
    public void switchActiveMethodKind_cyclesSequenceActivityCallGraph() {
        // シーケンス → アクティビティ → コールグラフ → シーケンス と 3 図種を巡回できること。
        TreeNodeOpenRequest seq = methodReq("com.example.Cycle", "loop", DiagramKind.SEQUENCE);
        GuiActionRunner.execute(() -> pane.addOrFocusTab(seq));

        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.ACTIVITY));
        assertEquals(DiagramKind.ACTIVITY, GuiActionRunner.execute(() -> pane.activeTabKind()));
        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.CALLGRAPH));
        assertEquals(DiagramKind.CALLGRAPH, GuiActionRunner.execute(() -> pane.activeTabKind()));
        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.SEQUENCE));
        assertEquals(DiagramKind.SEQUENCE, GuiActionRunner.execute(() -> pane.activeTabKind()));

        assertEquals("3 図種を巡回してもタブは 1 枚のまま", FIXED + 1,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
    }

    @Test
    public void switchActiveMethodKind_sameKind_isNoOp() {
        TreeNodeOpenRequest seq = methodReq("com.example.Svc3", "exec", DiagramKind.SEQUENCE);
        GuiActionRunner.execute(() -> pane.addOrFocusTab(seq));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());

        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.SEQUENCE));

        assertEquals("同一図種への切替は no-op でタブ数を変えない", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("図種は SEQUENCE のまま", DiagramKind.SEQUENCE,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    @Test
    public void switchActiveMethodKind_whenTargetKindTabExists_focusesItInstead() {
        // 同じメソッドのシーケンス図とアクティビティ図を別々に開く (別タブ)。
        TreeNodeOpenRequest seq = methodReq("com.example.Dup", "call", DiagramKind.SEQUENCE);
        TreeNodeOpenRequest act = methodReq("com.example.Dup", "call", DiagramKind.ACTIVITY);
        GuiActionRunner.execute(() -> {
            pane.addOrFocusTab(seq);
            pane.addOrFocusTab(act);
            // シーケンス図タブ (index 0) をアクティブに戻す。
            tabs.setSelectedIndex(0);
        });
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("前提: シーケンス図がアクティブ", DiagramKind.SEQUENCE,
                GuiActionRunner.execute(() -> pane.activeTabKind()));

        // アクティビティへ切替 → 既存のアクティビティ図タブがあるので複製せずそこへフォーカス。
        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.ACTIVITY));

        assertEquals("既存タブがある場合は複製せずタブ数据え置き", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("既存のアクティビティ図タブへフォーカスが移るはず", DiagramKind.ACTIVITY,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    @Test
    public void switchActiveMethodKind_onClassTab_isNoOp() {
        // メソッド図でないタブ (クラス図) では図種トグルは効かない。
        TreeNodeOpenRequest cls = TreeNodeOpenRequest.classNode(classInfo("com.example.Plain"));
        GuiActionRunner.execute(() -> pane.addOrFocusTab(cls));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());

        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.ACTIVITY));

        assertEquals("クラス図タブでの図種トグルは no-op", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("クラス図のままであるはず", DiagramKind.CLASS,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    // -------------------------------------------------------------------------
    // (j) レイアウト図の図種トグル (レイアウト ⇄ 画面 ⇄ 実寸) もタブを複製せずその場で切替
    // -------------------------------------------------------------------------

    @Test
    public void switchActiveLayoutKind_togglesInPlaceWithoutAddingTab() {
        GuiActionRunner.execute(() -> openLayoutTab(DiagramKind.LAYOUT, "app::main:::activity_main.xml"));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("前提: レイアウト図タブがアクティブ", DiagramKind.LAYOUT,
                GuiActionRunner.execute(() -> pane.activeTabKind()));

        // レイアウト → 実寸へその場切替。
        GuiActionRunner.execute(() -> pane.switchActiveLayoutKind(DiagramKind.LAYOUT_RENDER));

        assertEquals("図種トグルではタブ数が増えてはならない (複製しない)", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("トグル後は実寸図になっているはず", DiagramKind.LAYOUT_RENDER,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    @Test
    public void switchActiveLayoutKind_cyclesLayoutScreenRender() {
        GuiActionRunner.execute(() -> openLayoutTab(DiagramKind.LAYOUT, "app::main:::activity_cycle.xml"));

        GuiActionRunner.execute(() -> pane.switchActiveLayoutKind(DiagramKind.LAYOUT_SCREEN));
        assertEquals(DiagramKind.LAYOUT_SCREEN, GuiActionRunner.execute(() -> pane.activeTabKind()));
        GuiActionRunner.execute(() -> pane.switchActiveLayoutKind(DiagramKind.LAYOUT_RENDER));
        assertEquals(DiagramKind.LAYOUT_RENDER, GuiActionRunner.execute(() -> pane.activeTabKind()));
        GuiActionRunner.execute(() -> pane.switchActiveLayoutKind(DiagramKind.LAYOUT));
        assertEquals(DiagramKind.LAYOUT, GuiActionRunner.execute(() -> pane.activeTabKind()));

        assertEquals("3 図種を巡回してもタブは 1 枚のまま", FIXED + 1,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
    }

    @Test
    public void switchActiveLayoutKind_whenTargetKindTabExists_focusesItInstead() {
        // 同じレイアウトファイルの レイアウト図 と 実寸図 を別々に開く (別タブ)。
        GuiActionRunner.execute(() -> {
            openLayoutTab(DiagramKind.LAYOUT, "app::main:::dup.xml");
            openLayoutTab(DiagramKind.LAYOUT_RENDER, "app::main:::dup.xml");
            tabs.setSelectedIndex(0); // レイアウト図タブへ戻す
        });
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("前提: レイアウト図がアクティブ", DiagramKind.LAYOUT,
                GuiActionRunner.execute(() -> pane.activeTabKind()));

        GuiActionRunner.execute(() -> pane.switchActiveLayoutKind(DiagramKind.LAYOUT_RENDER));

        assertEquals("既存タブがある場合は複製せずタブ数据え置き", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("既存の実寸図タブへフォーカスが移るはず", DiagramKind.LAYOUT_RENDER,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    @Test
    public void switchActiveLayoutKind_onClassTab_isNoOp() {
        TreeNodeOpenRequest cls = TreeNodeOpenRequest.classNode(classInfo("com.example.Plain2"));
        GuiActionRunner.execute(() -> pane.addOrFocusTab(cls));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());

        GuiActionRunner.execute(() -> pane.switchActiveLayoutKind(DiagramKind.LAYOUT_RENDER));

        assertEquals("クラス図タブでのレイアウトトグルは no-op", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("クラス図のままであるはず", DiagramKind.CLASS,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    // -------------------------------------------------------------------------
    // (k) 図タブを別ウィンドウへ「移動」する (VS Code の Move into New Window 相当)
    // -------------------------------------------------------------------------

    @Test
    public void moveActiveTabToNewWindow_detachesTabAndEmitsHandle() {
        java.util.List<DiagramTabPane.TabHandle> moved = new java.util.ArrayList<>();
        GuiActionRunner.execute(() -> pane.setOnMoveToNewWindow(moved::add));
        TreeNodeOpenRequest cls = TreeNodeOpenRequest.classNode(classInfo("com.example.Move"));
        GuiActionRunner.execute(() -> pane.addOrFocusTab(cls));
        assertEquals("前提: 動的タブ 1 枚", FIXED + 1,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));

        GuiActionRunner.execute(() -> pane.moveActiveTabToNewWindow());

        assertEquals("移動元からタブが剥がれる", FIXED,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("移動ハンドルが 1 つ渡る", 1, moved.size());
        assertEquals("ハンドルの図種はクラス図", DiagramKind.CLASS, moved.get(0).spec.getKind());
    }

    @Test
    public void openFromHandle_recreatesTabInTargetPane() {
        java.util.List<DiagramTabPane.TabHandle> moved = new java.util.ArrayList<>();
        GuiActionRunner.execute(() -> pane.setOnMoveToNewWindow(moved::add));
        GuiActionRunner.execute(() ->
                pane.addOrFocusTab(TreeNodeOpenRequest.classNode(classInfo("com.example.Round"))));
        GuiActionRunner.execute(() -> pane.moveActiveTabToNewWindow());
        assertEquals(FIXED, (int) GuiActionRunner.execute(() -> tabs.getTabCount()));

        // 別ウィンドウの受け取り相当: 同じペインへハンドルから開き直せること (往復)。
        GuiActionRunner.execute(() -> pane.openFromHandle(moved.get(0)));

        assertEquals("ハンドルから図タブが再生成される", FIXED + 1,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("再生成された図種はクラス図", DiagramKind.CLASS,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    @Test
    public void moveActiveTabToNewWindow_editorTab_isNoOp() {
        java.util.List<DiagramTabPane.TabHandle> moved = new java.util.ArrayList<>();
        GuiActionRunner.execute(() -> {
            pane.setOnMoveToNewWindow(moved::add);
            pane.openPumlEditor("@startuml\nclass A\n@enduml", null);
        });
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());

        GuiActionRunner.execute(() -> pane.moveActiveTabToNewWindow());

        assertEquals("自由編集エディタタブは別ウィンドウへ移動しない (剥がれない)", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertTrue("エディタタブでは移動ハンドルが発行されない", moved.isEmpty());
    }

    @Test
    public void switchActiveMethodKind_whenTargetOpenInOtherWindow_focusesInsteadOfSwitching() {
        // その場切替も openDiagram と同じくウィンドウ横断の重複を避ける。切替先キーが別ウィンドウに
        // あると crossWindowFocus が true を返し、切替せず (二重束縛せず) 元図種のまま留まる。
        java.util.Set<String> focusedKeys = new java.util.HashSet<>();
        boolean[] active = {false};
        GuiActionRunner.execute(() -> pane.setCrossWindowFocus(key -> {
            if (!active[0]) {
                return false; // 初期オープンは通常どおり (別ウィンドウ扱いしない)
            }
            focusedKeys.add(key);
            return true; // 切替時のみ「別ウィンドウに既存」を模擬
        }));
        TreeNodeOpenRequest seq = methodReq("com.example.Xwin", "run", DiagramKind.SEQUENCE);
        GuiActionRunner.execute(() -> pane.addOrFocusTab(seq));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        active[0] = true;

        GuiActionRunner.execute(() -> pane.switchActiveMethodKind(DiagramKind.ACTIVITY));

        assertEquals("別ウィンドウに切替先があれば新規に切替えない", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("切替されず元のシーケンス図のまま", DiagramKind.SEQUENCE,
                GuiActionRunner.execute(() -> pane.activeTabKind()));
        assertFalse("crossWindowFocus が切替先キーで問い合わせられた", focusedKeys.isEmpty());
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** テスト用にレイアウト系図タブを開く (DiagramController.openLayout* と同じキー体系)。 */
    private void openLayoutTab(DiagramKind kind, String layoutKey) {
        String key = DiagramTabSupport.layoutTabKey(kind, layoutKey);
        String label = DiagramTabSupport.layoutTabLabel(kind, layoutKey);
        DiagramRequest spec = DiagramTabSupport.layoutRequest(kind, layoutKey, null);
        pane.openDiagram(key, label, TreeNodeIcon.COMPONENT_GROUP, spec, null);
    }

    /** テスト用のメソッド図オープンリクエストを生成する。 */
    private static TreeNodeOpenRequest methodReq(String ownerFqn, String method,
                                                 DiagramKind kind) {
        juml.core.formats.uml.JavaMethodInfo mi = new juml.core.formats.uml.JavaMethodInfo();
        mi.setName(method);
        return TreeNodeOpenRequest.method(classInfo(ownerFqn), mi, kind);
    }

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
