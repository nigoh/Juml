// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.doxygen.DoxModel;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DoxygenResultCache} の監視可能ホルダとしての振る舞いを検証する。
 *
 * <p>doxygen 実行 ({@link DoxygenResultCache#runAsync}) は外部バイナリと
 * {@link javax.swing.SwingWorker} に依存するためテスト対象外とし、結果保持・
 * リスナー通知のロジック（{@code publishResult}）をリフレクションで直接駆動する。
 * Swing コンポーネントを持たないためヘッドレスで完結する。</p>
 */
public class DoxygenResultCacheTest {

    @Test
    public void freshCache_hasNoModelAndIsNotRunning() {
        DoxygenResultCache cache = new DoxygenResultCache();
        assertNull("未実行なら結果は null", cache.getModel());
        assertFalse("未実行なら running は false", cache.isRunning());
    }

    @Test
    public void addListener_ignoresNull() {
        DoxygenResultCache cache = new DoxygenResultCache();
        // null リスナーで例外を出さないこと（呼べることの確認）
        cache.addListener(null);
    }

    @Test
    public void publishResult_storesModelAndNotifiesListeners() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        AtomicInteger fires = new AtomicInteger(0);
        cache.addListener(fires::incrementAndGet);
        cache.addListener(fires::incrementAndGet);

        DoxModel model = new DoxModel();
        invokePublishResult(cache, model);

        assertSame("publishResult で結果が保持されること", model, cache.getModel());
        assertEquals("登録した全リスナーへ通知されること", 2, fires.get());
    }

    private static void invokePublishResult(DoxygenResultCache cache, DoxModel model)
            throws Exception {
        Method m = DoxygenResultCache.class.getDeclaredMethod("publishResult", DoxModel.class);
        m.setAccessible(true);
        m.invoke(cache, model);
    }

    /**
     * bug-hunt R1 で発見: プロジェクト切替 ({@code clear}) しても実行中の doxygen は走り続け、
     * 新プロジェクトの Run が完走まで待たされていた。取り消しで解析スレッドが割り込まれ、
     * 旧結果を保持せず後始末 (onFinally) が 1 回だけ呼ばれることを検証する。
     */
    @Test
    public void clear_cancelsRunningAnalysisAndDropsItsResult() throws Exception {
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean interrupted =
                new java.util.concurrent.atomic.AtomicBoolean();
        DoxygenResultCache.analyzerForTest = root -> {
            started.countDown();
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw new IllegalStateException("cancelled", e);
            }
            return new DoxModel();
        };
        DoxygenResultCache cache = new DoxygenResultCache();
        AtomicInteger finallyCount = new AtomicInteger();
        try {
            java.io.File root = new java.io.File(System.getProperty("java.io.tmpdir"));
            javax.swing.SwingUtilities.invokeAndWait(
                    () -> cache.runAsync(root, null, null, finallyCount::incrementAndGet));
            assertTrue("解析が開始されること", started.await(5, java.util.concurrent.TimeUnit.SECONDS));
            javax.swing.SwingUtilities.invokeAndWait(cache::clear);
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline
                    && (!interrupted.get() || cache.isRunning() || finallyCount.get() == 0)) {
                Thread.sleep(20); // 期限付きポーリング (取り消しは非同期に伝播する)
            }
            assertTrue("取り消しで解析スレッドが割り込まれること", interrupted.get());
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            assertFalse("取り消し後は running が戻ること", cache.isRunning());
            assertNull("取り消した解析の結果は保持しない", cache.getModel());
            assertEquals("onFinally は 1 回だけ呼ばれる", 1, finallyCount.get());
        } finally {
            DoxygenResultCache.analyzerForTest = null;
            release.countDown();
        }
    }
}
