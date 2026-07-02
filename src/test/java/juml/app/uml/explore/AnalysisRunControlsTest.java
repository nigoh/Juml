// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import org.junit.Test;

import javax.swing.SwingWorker;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link AnalysisRunControls} の状態遷移とキャンセル委譲 (Swing コンポーネント生成のみで
 * 表示は伴わないため headless で動く)。解析パネル 4 つが共有する再入・キャンセル制御の要。
 */
public class AnalysisRunControlsTest {

    @Test
    public void initialState_cancelDisabled_progressHidden() {
        AnalysisRunControls c = new AnalysisRunControls();
        assertFalse("初期はキャンセル無効", c.cancelButton().isEnabled());
        assertFalse("初期は進捗バー非表示", c.progressBar().isVisible());
    }

    @Test
    public void started_thenFinished_togglesControls() {
        AnalysisRunControls c = new AnalysisRunControls();
        SwingWorker<Void, Void> w = noopWorker();
        c.started(w);
        assertTrue("実行中はキャンセル有効", c.cancelButton().isEnabled());
        assertTrue("実行中は進捗バー表示", c.progressBar().isVisible());
        c.finished();
        assertFalse("終了でキャンセル無効に戻る", c.cancelButton().isEnabled());
        assertFalse("終了で進捗バー非表示に戻る", c.progressBar().isVisible());
    }

    @Test
    public void cancelButtonClick_cancelsActiveWorker() {
        AnalysisRunControls c = new AnalysisRunControls();
        // doInBackground でブロックし続けるワーカー = cancel(true) で isCancelled() が立つ。
        final CountDownLatch release = new CountDownLatch(1);
        SwingWorker<Void, Void> w = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws InterruptedException {
                release.await();
                return null;
            }
        };
        w.execute();
        c.started(w);
        c.cancelButton().doClick();
        release.countDown();
        assertTrue("Cancel ボタンで実行中ワーカーが取り消される", w.isCancelled());
    }

    @Test
    public void cancelButtonClick_withoutActiveWorker_isSafe() {
        AnalysisRunControls c = new AnalysisRunControls();
        c.cancelButton().doClick(); // active が null でも例外を出さない
        c.finished();               // 二重終了も安全
    }

    private static SwingWorker<Void, Void> noopWorker() {
        return new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                return null;
            }
        };
    }
}
