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
}
