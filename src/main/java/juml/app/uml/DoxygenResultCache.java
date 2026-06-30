// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.doxygen.DoxModel;
import juml.core.formats.doxygen.DoxygenRunner;
import juml.core.formats.doxygen.DoxygenXmlParser;
import juml.util.ErrorListener;

import javax.swing.SwingWorker;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1 回の doxygen 実行結果 ({@link DoxModel}) を Doxygen タブと TODO タブで共有するための
 * 監視可能なホルダ兼共有ランナー。
 *
 * <p>どちらのタブの「Run Doxygen」からでも {@link #runAsync} を呼べば、結果を保持して
 * 登録済みリスナー全員に通知する。これにより両タブが同じ 1 回の解析結果を使い回し、
 * doxygen を二重に起動しない。</p>
 */
final class DoxygenResultCache {

    private DoxModel model;
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean running;

    /** 最後に解析した結果。未実行なら null。 */
    DoxModel getModel() {
        return model;
    }

    /** doxygen 実行中か (重複起動の抑止に使う)。 */
    boolean isRunning() {
        return running;
    }

    /** 結果更新時に呼ぶリスナーを登録する (各タブが自身の表示を作り直す)。 */
    void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * doxygen を {@link SwingWorker} 上で実行し、成功したら結果を保持してリスナーへ通知する。
     *
     * @param root      プロジェクトルート
     * @param onStart   実行開始時 (EDT)。ボタン無効化・ステータス表示などに使う。
     * @param onError   失敗時 (EDT)。メッセージを受け取る。
     * @param onFinally 成否によらず最後に呼ぶ (EDT)。ボタン再有効化などに使う。
     */
    void runAsync(File root, Runnable onStart, Consumer<String> onError, Runnable onFinally) {
        if (running) {
            return;
        }
        running = true;
        if (onStart != null) {
            onStart.run();
        }
        new SwingWorker<DoxModel, Void>() {
            @Override
            protected DoxModel doInBackground() throws Exception {
                // 出力キャッシュ利用: *.java 不変なら doxygen 再実行をスキップして XML を再パース。
                File xmlDir = DoxygenRunner.runCached(root, ErrorListener.silent());
                return DoxygenXmlParser.parse(xmlDir, ErrorListener.silent());
            }

            @Override
            protected void done() {
                running = false;
                try {
                    publishResult(get());
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    juml.util.AppLog.error("DoxygenResultCache",
                            "Doxygen execution/parsing failed: " + root.getAbsolutePath(), cause);
                    if (onError != null) {
                        onError.accept(cause.getMessage());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    if (onError != null) {
                        onError.accept("Interrupted.");
                    }
                } finally {
                    if (onFinally != null) {
                        onFinally.run();
                    }
                }
            }
        }.execute();
    }

    /** 結果を保持し、全リスナーへ通知する。 */
    private void publishResult(DoxModel m) {
        this.model = m;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
