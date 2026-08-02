// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 解析が失敗/キャンセルで終わったときの後始末の回帰テスト。
 *
 * <p>この経路では解析キャッシュを空にするのに、開いていた図タブと共有 {@link DiagramState}
 * はそのまま残っていた。前のプロジェクトを読み込み済みの状態で新しいプロジェクトのロードに
 * 失敗すると、タブは旧プロジェクトのラベルのまま空のキャッシュを参照することになり、
 * 再描画 (F5 / スタイル変更 / 図種切替) で空図や別図が出る。ツリーはロード開始時点で
 * クリア済みなので、タブ側も同じ「未ロード」状態へ揃える必要がある。</p>
 */
public class ProjectLoaderAbortCleanupTest {

    private DiagramState state;
    private AtomicInteger aborted;
    private ProjectLoader loader;

    @Before
    public void setUp() {
        state = new DiagramState();
        aborted = new AtomicInteger();
        ProjectLoaderDeps deps = new ProjectLoaderDeps();
        deps.cache = new ProjectAnalysisCache();
        deps.refIndexCache = new ReferenceIndexCache(new ProjectAnalysisCache());
        deps.state = state;
        deps.treePanel = new ProjectTreePanel();
        deps.manifestSummaryPanel = new ManifestSummaryPanel();
        deps.loadProgress = new JProgressBar();
        deps.cancelLoadingItem = new JMenuItem();
        deps.statusLabel = new JLabel();
        deps.parentFrame = null;
        deps.cancelTokenSetter = token -> { };
        deps.projectRootSetter = root -> { };
        deps.onLoadSuccess = root -> { };
        deps.onLoadAborted = aborted::incrementAndGet;
        loader = new ProjectLoader(deps);
    }

    @Test
    public void abortCleanupResetsSharedDiagramState() {
        state.sequenceEntry = "Foo.bar";
        state.activityEntry = "Foo.baz";
        state.callGraphEntry = "Foo.qux";
        state.sequenceHiddenParticipants.add("Hidden");
        state.currentScope = DiagramScope.builder().includePackage("com.a").build();

        loader.abortCleanup();

        assertNull("旧プロジェクトのシーケンス題材を残さない", state.sequenceEntry);
        assertNull("旧プロジェクトのアクティビティ題材を残さない", state.activityEntry);
        assertNull("旧プロジェクトのコールグラフ題材を残さない", state.callGraphEntry);
        assertTrue("旧プロジェクトの participant フィルタを残さない",
                state.sequenceHiddenParticipants.isEmpty());
        assertNull("旧プロジェクトのスコープを残さない", state.currentScope);
    }

    @Test
    public void abortCleanupNotifiesTheHost() {
        // タブ・別ウィンドウ・Doxygen 結果の破棄は UmlMainFrame 側で行うため、
        // 通知が確実に飛ぶことをここで固定する。
        loader.abortCleanup();
        assertEquals(1, aborted.get());
    }

    @Test
    public void abortCleanupWorksWithoutCallback() {
        // コールバック未設定 (CLI 由来の組み立てなど) でも NPE にならないこと。
        ProjectLoaderDeps deps = new ProjectLoaderDeps();
        deps.cache = new ProjectAnalysisCache();
        deps.refIndexCache = new ReferenceIndexCache(new ProjectAnalysisCache());
        deps.state = new DiagramState();
        deps.treePanel = new ProjectTreePanel();
        deps.manifestSummaryPanel = new ManifestSummaryPanel();
        deps.loadProgress = new JProgressBar();
        deps.cancelLoadingItem = new JMenuItem();
        deps.statusLabel = new JLabel();
        deps.cancelTokenSetter = token -> { };
        deps.projectRootSetter = root -> { };
        deps.onLoadSuccess = root -> { };
        new ProjectLoader(deps).abortCleanup();
    }
}
