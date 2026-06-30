// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;

import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.List;
import java.util.function.Consumer;

/**
 * lazy (Stage A) ロード時に {@link ProjectAnalysisCache#getDetailedClasses()} の初回呼び出しが
 * 全クラス再パースを伴いうるため、EDT を止めずに詳細クラス一覧を用意する補助。
 *
 * <p>詳細が既に揃っていれば ({@link ProjectAnalysisCache#isDetailedReady()}) 同期的に
 * 即コールバックする。未昇格なら {@link SwingWorker} で背景昇格し、その間 owner に
 * ウェイトカーソルを出してから、完了時に EDT でコールバックを呼ぶ。</p>
 *
 * <p>Members / Functions タブは個別に SwingWorker 化済みのため、ここでは起点ピッカー・
 * エンティティ検索・各種エクスポートなど「詳細が要るがまだ非同期化していない」呼び出しを
 * 1 行で包めるようにする。</p>
 */
final class LazyDetail {

    private LazyDetail() {
    }

    /**
     * 詳細クラス一覧を用意してから {@code onReady} を EDT で呼ぶ。
     *
     * @param cache   解析キャッシュ
     * @param owner   ウェイトカーソルを出す対象 (null 可)
     * @param onReady 詳細クラス一覧を受け取るコールバック (常に EDT で実行)
     */
    static void withDetailedClasses(ProjectAnalysisCache cache, Component owner,
                                    Consumer<List<JavaClassInfo>> onReady) {
        if (cache == null) {
            return;
        }
        if (cache.isDetailedReady()) {
            onReady.accept(cache.getDetailedClasses());
            return;
        }
        if (owner != null) {
            owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        new SwingWorker<List<JavaClassInfo>, Void>() {
            @Override
            protected List<JavaClassInfo> doInBackground() {
                return cache.getDetailedClasses();
            }

            @Override
            protected void done() {
                if (owner != null) {
                    owner.setCursor(Cursor.getDefaultCursor());
                }
                try {
                    onReady.accept(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ex) {
                    // 昇格失敗時は何も表示しない (元の呼び出しは no-op で安全)
                }
            }
        }.execute();
    }
}
