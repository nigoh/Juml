// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「Close All Tabs」の確認分岐 {@link UmlMainFrame#runCloseAllWithConfirm} と、
 * タブヘッダ右クリック経路 {@link DiagramTabPane#requestCloseAll} の委譲を検証する。
 *
 * <p>確認ロジックは {@code UmlMainFrame} から切り出した静的シームであり、
 * フレームをフル起動せずに単体で検証できる (headless CI でも実行可能):</p>
 * <ol>
 *   <li>動的タブ 2 枚以上 + YES → 全部閉じる</li>
 *   <li>動的タブ 2 枚以上 + NO → 閉じない</li>
 *   <li>動的タブ 1 枚 → 確認なしで閉じる</li>
 * </ol>
 *
 * <p>右クリック経路のテストのみ実際の Swing コンポーネントを生成するため
 * headless 環境では {@link Assume} でスキップする。</p>
 */
public class UmlMainFrameCloseAllConfirmTest {

    /** 固定ユーティリティタブ数 (動的タブはこの手前に挿入される)。 */
    private static final int FIXED = 2;

    /** GUI テストで生成した JTabbedPane。tearDown で破棄する。 */
    private JTabbedPane tabs;

    @After
    public void tearDown() {
        if (tabs != null) {
            GuiActionRunner.execute(() -> tabs.removeAll());
            tabs = null;
        }
    }

    // -------------------------------------------------------------------------
    // (1) 切り出した確認分岐ロジックの単体テスト (headless でも実行される)
    // -------------------------------------------------------------------------

    @Test
    public void twoOrMoreTabs_confirmYes_closesAll() {
        List<Integer> confirmedCounts = new ArrayList<>();
        AtomicInteger closed = new AtomicInteger();

        UmlMainFrame.runCloseAllWithConfirm(3, count -> {
            confirmedCounts.add(count);
            return true; // YES
        }, closed::incrementAndGet);

        assertEquals("2 枚以上では確認関数が 1 回だけ呼ばれるはず",
                1, confirmedCounts.size());
        assertEquals("確認関数には動的タブ数が渡されるはず",
                Integer.valueOf(3), confirmedCounts.get(0));
        assertEquals("YES ならクローズ操作が実行されるはず", 1, closed.get());
    }

    @Test
    public void twoOrMoreTabs_confirmNo_doesNotClose() {
        AtomicInteger confirmCalls = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();

        UmlMainFrame.runCloseAllWithConfirm(2, count -> {
            confirmCalls.incrementAndGet();
            return false; // NO
        }, closed::incrementAndGet);

        assertEquals("ちょうど 2 枚でも確認関数が呼ばれるはず", 1, confirmCalls.get());
        assertEquals("NO ならクローズ操作は実行されないはず", 0, closed.get());
    }

    @Test
    public void singleTab_closesWithoutConfirm() {
        AtomicInteger confirmCalls = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();

        UmlMainFrame.runCloseAllWithConfirm(1, count -> {
            confirmCalls.incrementAndGet();
            return false; // 呼ばれた場合に検出できるよう NO を返す
        }, closed::incrementAndGet);

        assertEquals("1 枚のときは確認関数を呼ばないはず", 0, confirmCalls.get());
        assertEquals("1 枚のときは確認なしで閉じるはず", 1, closed.get());
    }

    @Test
    public void zeroTabs_closesWithoutConfirm() {
        AtomicInteger confirmCalls = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();

        UmlMainFrame.runCloseAllWithConfirm(0, count -> {
            confirmCalls.incrementAndGet();
            return false;
        }, closed::incrementAndGet);

        assertEquals("0 枚のときは確認関数を呼ばないはず", 0, confirmCalls.get());
        assertEquals("0 枚でもクローズ操作 (no-op) は実行されるはず", 1, closed.get());
    }

    // -------------------------------------------------------------------------
    // (2) タブヘッダ右クリック「Close All」経路の委譲 (要ディスプレイ)
    // -------------------------------------------------------------------------

    @Test
    public void requestCloseAll_withHandler_delegatesInsteadOfClosing() throws Exception {
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());

        DiagramTabPane pane = newPaneWithTabs("com.example.Foo", "com.example.Bar");
        AtomicInteger handlerCalls = new AtomicInteger();
        // NO 相当: ハンドラは呼ばれるが closeAllTabs() を実行しない
        GuiActionRunner.execute(() ->
                pane.setCloseAllRequestHandler(handlerCalls::incrementAndGet));

        GuiActionRunner.execute(() -> pane.requestCloseAll());

        assertEquals("ハンドラ設定時は requestCloseAll がハンドラへ委譲するはず",
                1, handlerCalls.get());
        assertEquals("ハンドラが閉じない限りタブは残るはず (確認で NO 相当)",
                2, (int) GuiActionRunner.execute(() -> pane.dynamicTabCount()));

        // YES 相当: ハンドラ側が確認の末に closeAllTabs() を呼ぶケース
        GuiActionRunner.execute(() ->
                pane.setCloseAllRequestHandler(pane::closeAllTabs));
        GuiActionRunner.execute(() -> pane.requestCloseAll());

        assertEquals("ハンドラが closeAllTabs を呼べば全タブが閉じるはず",
                0, (int) GuiActionRunner.execute(() -> pane.dynamicTabCount()));
        assertFalse("動的タブは残っていないはず",
                GuiActionRunner.execute(() -> pane.hasActiveTab()));
    }

    @Test
    public void requestCloseAll_withoutHandler_closesAllForBackwardCompat() throws Exception {
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());

        DiagramTabPane pane = newPaneWithTabs("com.example.Alpha", "com.example.Beta");
        assertTrue("前提: 動的タブが 2 枚開いているはず",
                GuiActionRunner.execute(() -> pane.dynamicTabCount() == 2));

        // ハンドラ未設定 → 従来どおり確認なしで全タブを閉じる (後方互換)
        GuiActionRunner.execute(() -> pane.requestCloseAll());

        assertEquals("ハンドラ未設定なら requestCloseAll は直接全タブを閉じるはず",
                0, (int) GuiActionRunner.execute(() -> pane.dynamicTabCount()));
        int totalTabs = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("固定ユーティリティタブは残るはず", FIXED, totalTabs);
    }

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    /** 固定タブ 2 本 + 指定クラスの動的タブを開いた DiagramTabPane を作る。 */
    private DiagramTabPane newPaneWithTabs(String... classFqns) throws Exception {
        // ProjectAnalysisCache.isLoaded() が true を返すようテスト用フックで注入する
        // (load() を呼ぶと実プロジェクト解析が走るため)。
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.setLoadedRootForTest(new java.io.File(System.getProperty("java.io.tmpdir")));

        DiagramTabPane pane = GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            tabs.addTab("Utility1", new javax.swing.JPanel());
            tabs.addTab("Utility2", new javax.swing.JPanel());
            return new DiagramTabPane(tabs, FIXED, cache, new DiagramState(),
                    msg -> { }, zoom -> { });
        });
        for (String fqn : classFqns) {
            GuiActionRunner.execute(() ->
                    pane.addOrFocusTab(TreeNodeOpenRequest.classNode(classInfo(fqn))));
        }
        assertEquals("前提: 指定した枚数の動的タブが開いているはず",
                classFqns.length,
                (int) GuiActionRunner.execute(() -> pane.dynamicTabCount()));
        return pane;
    }

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
