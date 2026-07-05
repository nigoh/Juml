// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramTabPane} ↔ {@link DiagramState} / ステータスバー連動の回帰テスト。
 *
 * <p>検証対象 (いずれも Round 1 の修正: DiagramTabPane.java の
 * {@code setStatus} / {@code mirrorToState} / {@code handleTabSelectionChanged}):
 * <ol>
 *   <li>非アクティブタブの描画完了は共有ステータスバーを上書きしない</li>
 *   <li>ユーティリティタブ選択時に {@code state.sequenceHiddenParticipants} がクリアされる</li>
 *   <li>別題材の新規シーケンス図に前タブの隠し participant が漏れない</li>
 *   <li>非シーケンスの動的タブへ切替でも隠し participant がクリアされる</li>
 * </ol></p>
 *
 * <p>{@link DiagramState} のフィールドは package-private のためリフレクション不要で
 * 直接観測できる。{@link DiagramTabPaneTest} と同じ流儀 (headless Assume /
 * {@code cache.setLoadedRootForTest}) に倣う。</p>
 */
public class DiagramTabPaneStateSyncTest {

    private static final int FIXED = 2;

    private JTabbedPane tabs;
    private ProjectAnalysisCache cache;
    private DiagramState state;
    private DiagramTabPane pane;
    private final List<String> statusMessages =
            Collections.synchronizedList(new ArrayList<>());

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        cache = new ProjectAnalysisCache();
        cache.setLoadedRootForTest(new java.io.File(System.getProperty("java.io.tmpdir")));

        GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            tabs.addTab("Utility1", new javax.swing.JPanel());
            tabs.addTab("Utility2", new javax.swing.JPanel());
            state = new DiagramState();
            pane = new DiagramTabPane(tabs, FIXED, cache, state,
                    statusMessages::add, zoom -> { });
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
    // (1) 非アクティブタブの描画完了は共有ステータスバーを上書きしない
    // -------------------------------------------------------------------------

    @Test
    public void inactiveTabRenderCompletion_doesNotOverwriteActiveTabStatus() throws Exception {
        // メッセージ本文はロケール依存 (日本語/英語) だが、タブラベル ("StatusA"/"StatusB")
        // は翻訳されずそのまま埋め込まれるため、それを目印に「どちらのタブ由来か」を
        // ロケールに依存せず判定する。
        JavaClassInfo a = classInfo("com.example.StatusA");
        JavaClassInfo b = classInfo("com.example.StatusB");

        // A を開く (A がアクティブ) → 非同期の描画結果が届いて落ち着くまで待つ。
        GuiActionRunner.execute(() -> pane.addOrFocusTab(TreeNodeOpenRequest.classNode(a)));
        awaitStable(5_000, 400);

        // B を開く (B がアクティブになる) → 同様に落ち着くまで待つ。
        GuiActionRunner.execute(() -> pane.addOrFocusTab(TreeNodeOpenRequest.classNode(b)));
        awaitStable(5_000, 400);

        // A へフォーカスを戻す (B は非アクティブになる)。
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(0));
        awaitStable(5_000, 400);
        int marker = statusMessages.size();

        // すべての開いているタブを再描画する (A: アクティブ, B: 非アクティブ)。
        GuiActionRunner.execute(pane::rerenderAllTabs);
        awaitStable(5_000, 400);

        List<String> added = new ArrayList<>(
                statusMessages.subList(marker, statusMessages.size()));
        assertTrue("再描画後に何らかのステータス更新が共有ステータスバーに届くはず: " + added,
                !added.isEmpty());
        boolean mentionsA = added.stream().anyMatch(m -> m.contains("StatusA"));
        boolean mentionsB = added.stream().anyMatch(m -> m.contains("StatusB"));
        assertTrue("アクティブタブ A の再描画結果は共有ステータスバーに届くはず: " + added, mentionsA);
        assertFalse("非アクティブタブ B の再描画完了が共有ステータスバーへ漏れてはならない: " + added,
                mentionsB);
    }

    /**
     * ステータスメッセージの件数が {@code stableMs} の間変化しなくなるまで待つ
     * (期限付きポーリング)。非同期の {@code SwingWorker} 完了を、固定 sleep ではなく
     * 「安定するまで待つ」形で捕捉するためのヘルパ。
     */
    private void awaitStable(long timeoutMs, long stableMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int lastSize = statusMessages.size();
        long stableSince = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            int size = statusMessages.size();
            if (size != lastSize) {
                lastSize = size;
                stableSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stableSince >= stableMs) {
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // (2)〜(4) state.sequenceHiddenParticipants の漏れ防止
    // -------------------------------------------------------------------------

    @Test
    public void utilityTabSelection_clearsSequenceHiddenParticipants() {
        TreeNodeOpenRequest reqA = methodReq("com.example.Svc", "handle", DiagramKind.SEQUENCE);
        DiagramRequest specA = sequenceSpec(reqA, "HiddenActor");
        GuiActionRunner.execute(() -> pane.openDiagram(reqA.tabKey(), reqA.displayLabel(),
                DiagramTabSupport.iconFor(reqA), specA, reqA));

        assertEquals("シーケンスタブを開いた直後は隠し participant が state に反映されるはず",
                Collections.singleton("HiddenActor"),
                GuiActionRunner.execute(() -> new LinkedHashSet<>(state.sequenceHiddenParticipants)));

        // ユーティリティタブ (末尾固定タブ) へ切替。
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(tabs.getTabCount() - 1));

        assertTrue("ユーティリティタブ選択時に隠し participant はクリアされるはず (state 漏れ防止)",
                GuiActionRunner.execute(() -> state.sequenceHiddenParticipants.isEmpty()));
    }

    @Test
    public void newSequenceTabAfterUtilityTab_doesNotInheritPreviousHiddenParticipants() {
        // 実際のユーザーフロー (DiagramController#buildSequenceRequest) では、新規
        // シーケンス図のリクエストは「現在の state.sequenceHiddenParticipants」を種にして
        // 組み立てられる (メソッド図の 3 図種を同じ絞り込みで揃えるための仕様)。
        // そのため、タブ切替時に state がクリアされていないと、無関係な新規シーケンス図
        // にまで前タブの隠し participant が種として渡ってしまう。ここでは
        // buildSequenceRequest と同じ「現在の state から種を作る」処理をそのまま再現し、
        // 実際の漏れ経路を再現する。
        TreeNodeOpenRequest reqA = methodReq("com.example.Svc2", "run", DiagramKind.SEQUENCE);
        DiagramRequest specA = sequenceSpec(reqA, "Ghost");
        GuiActionRunner.execute(() -> pane.openDiagram(reqA.tabKey(), reqA.displayLabel(),
                DiagramTabSupport.iconFor(reqA), specA, reqA));

        // いったんユーティリティタブへ (修正前はここで隠し participant が残ってしまっていた)。
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(tabs.getTabCount() - 1));

        // 別題材の新規シーケンス図: buildSequenceRequest と同じ流儀で、
        // 「いま現在の state.sequenceHiddenParticipants」を種にリクエストを組み立てる。
        TreeNodeOpenRequest reqB = methodReq("com.example.Other", "call", DiagramKind.SEQUENCE);
        DiagramRequest specB = GuiActionRunner.execute(() -> {
            Set<String> seed = state.sequenceHiddenParticipants.isEmpty()
                    ? null : new LinkedHashSet<>(state.sequenceHiddenParticipants);
            return new DiagramRequest(DiagramKind.SEQUENCE, reqB.classInfo.getSimpleName(),
                    reqB.methodInfo.getName(), true, null, false, null, seed);
        });
        assertTrue("種にする時点で前タブの隠し participant が state に残っていてはならない: "
                + specB.getSequenceHiddenParticipants(),
                specB.getSequenceHiddenParticipants().isEmpty());

        GuiActionRunner.execute(() -> pane.openDiagram(reqB.tabKey(), reqB.displayLabel(),
                DiagramTabSupport.iconFor(reqB), specB, reqB));

        assertTrue("別題材の新規シーケンス図に前タブの隠し participant が漏れてはならない",
                GuiActionRunner.execute(() -> state.sequenceHiddenParticipants.isEmpty()));
    }

    @Test
    public void switchingToNonSequenceDynamicTab_clearsSequenceHiddenParticipants() {
        TreeNodeOpenRequest reqA = methodReq("com.example.Svc3", "exec", DiagramKind.SEQUENCE);
        DiagramRequest specA = sequenceSpec(reqA, "Hidden1", "Hidden2");
        GuiActionRunner.execute(() -> pane.openDiagram(reqA.tabKey(), reqA.displayLabel(),
                DiagramTabSupport.iconFor(reqA), specA, reqA));

        JavaClassInfo classB = classInfo("com.example.PlainClass");
        GuiActionRunner.execute(() -> pane.addOrFocusTab(TreeNodeOpenRequest.classNode(classB)));

        assertTrue("非シーケンスの動的タブへ切替でも隠し participant はクリアされるはず",
                GuiActionRunner.execute(() -> state.sequenceHiddenParticipants.isEmpty()));
    }

    // -------------------------------------------------------------------------
    // (#40) スコープはタブ固有: activeTabSpec() がフォーカス中タブの scope を返す
    // -------------------------------------------------------------------------

    @Test
    public void activeTabSpec_reflectsFocusedTabScopeWithoutBleeding() {
        DiagramScope scopeA = DiagramScope.builder().includePackage("com.a").build();
        JavaClassInfo a = classInfo("com.a.Alpha");
        JavaClassInfo b = classInfo("com.b.Beta");
        DiagramRequest specA = new DiagramRequest(DiagramKind.CLASS, null, null, true,
                scopeA, false);
        DiagramRequest specB = new DiagramRequest(DiagramKind.CLASS, null, null, true,
                null, false);

        // スコープ付きタブ A を開く (A がアクティブ)。
        TreeNodeOpenRequest reqA = TreeNodeOpenRequest.classNode(a);
        GuiActionRunner.execute(() -> pane.openDiagram("A", "Alpha",
                DiagramTabSupport.iconFor(reqA), specA, reqA));
        assertEquals("アクティブタブ A の spec は scopeA を持つ", scopeA,
                GuiActionRunner.execute(() -> pane.activeTabSpec().getScope()));

        // スコープ無しタブ B を開く (B がアクティブ)。A のスコープが漏れてはならない。
        TreeNodeOpenRequest reqB = TreeNodeOpenRequest.classNode(b);
        GuiActionRunner.execute(() -> pane.openDiagram("B", "Beta",
                DiagramTabSupport.iconFor(reqB), specB, reqB));
        assertNull("別タブ B に A のスコープが漏れてはならない",
                GuiActionRunner.execute(() -> pane.activeTabSpec().getScope()));

        // A へ戻すと再び scopeA が見える (タブ固有に保持されている)。
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(0));
        assertEquals("A へ戻すとタブ固有の scopeA が復活する", scopeA,
                GuiActionRunner.execute(() -> pane.activeTabSpec().getScope()));
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    private static TreeNodeOpenRequest methodReq(String ownerFqn, String method,
                                                 DiagramKind kind) {
        JavaMethodInfo mi = new JavaMethodInfo();
        mi.setName(method);
        return TreeNodeOpenRequest.method(classInfo(ownerFqn), mi, kind);
    }

    private static DiagramRequest sequenceSpec(TreeNodeOpenRequest req, String... hidden) {
        Set<String> h = hidden.length == 0 ? null : new LinkedHashSet<>(Arrays.asList(hidden));
        return new DiagramRequest(DiagramKind.SEQUENCE, req.classInfo.getSimpleName(),
                req.methodInfo.getName(), true, null, false, null, h);
    }

    private static JavaClassInfo classInfo(String fqn) {
        JavaClassInfo ci = new JavaClassInfo();
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
