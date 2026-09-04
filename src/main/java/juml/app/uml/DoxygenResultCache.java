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
    /** 実行中のワーカー (プロジェクト切替時に取り消すため保持)。 */
    private SwingWorker<DoxModel, Void> worker;
    /** テスト用: doxygen 実行 + XML パースの差し替え (null なら実 doxygen を起動)。 */
    static volatile java.util.function.Function<File, DoxModel> analyzerForTest;
    /** 実行中/保持中の結果がどのルートのものか。プロジェクト切替の混線防止に使う。 */
    private File modelRoot;
    // 実行中に「別プロジェクト」の Run が来たときの保留 (1 スロット・最新のみ)。
    // これが無いと、切替直後の Run が握りつぶされ、新プロジェクトが解析されないまま残る。
    private File pendingRoot;
    private Runnable pendingOnStart;
    private Consumer<String> pendingOnError;
    private Runnable pendingOnFinally;

    /**
     * 保持している結果を破棄してリスナーへ通知する (プロジェクト切替時)。
     * 破棄しないと Doxygen/TODO/Groups タブが前プロジェクトの内容を
     * 新プロジェクトのものとして表示し続ける。
     */
    void clear() {
        model = null;
        modelRoot = null;
        // 実行中の doxygen は旧プロジェクト向けなので取り消す (bug-hunt R1: 巨大ツリーの
        // doxygen が完走するまで新プロジェクトの Run が待たされ、取り消す手段も無かった)。
        // done() で running が戻り、保留中の新プロジェクト解析があればそこから開始される。
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
            DoxygenRunner.cancelRunning();
        }
        // 保留中の別プロジェクト解析があれば、それも破棄する (更に別プロジェクトへ
        // 切り替わったので陳腐化している)。無効化ボタンが戻るよう後始末は呼ぶ。
        if (pendingOnFinally != null) {
            pendingOnFinally.run();
        }
        clearPending();
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private void clearPending() {
        pendingRoot = null;
        pendingOnStart = null;
        pendingOnError = null;
        pendingOnFinally = null;
    }

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
            if (root.equals(modelRoot)) {
                // 同じプロジェクトの二重起動 (別タブから Run 等): 既存ランの完了通知で
                // 結果を拾えるため、開始通知だけ返して即後始末する。
                if (onStart != null) {
                    onStart.run();
                }
                if (onFinally != null) {
                    onFinally.run();
                }
                return;
            }
            // 実行中に「別プロジェクト」の Run が来た: doxygen を二重起動できないため、
            // 実行中ランの完了後に回す (最新の 1 件のみ保持)。握りつぶすと新プロジェクトが
            // 解析されないまま残ってしまう。
            if (pendingOnFinally != null) {
                pendingOnFinally.run(); // 上書きされる古い保留の後始末を先に返す
            }
            pendingRoot = root;
            pendingOnStart = onStart;
            pendingOnError = onError;
            pendingOnFinally = onFinally;
            if (onStart != null) {
                onStart.run(); // 「実行中/待機中」表示は出しておく
            }
            return;
        }
        running = true;
        modelRoot = root;
        if (onStart != null) {
            onStart.run();
        }
        worker = new SwingWorker<DoxModel, Void>() {
            @Override
            protected DoxModel doInBackground() throws Exception {
                java.util.function.Function<File, DoxModel> stub = analyzerForTest;
                if (stub != null) {
                    return stub.apply(root);
                }
                // 出力キャッシュ利用: *.java 不変なら doxygen 再実行をスキップして XML を再パース。
                File xmlDir = DoxygenRunner.runCached(root, ErrorListener.silent());
                return DoxygenXmlParser.parse(xmlDir, ErrorListener.silent());
            }

            @Override
            protected void done() {
                running = false;
                try {
                    DoxModel m = get();
                    // 実行中にプロジェクトが切り替わり clear() された場合は結果を捨てる
                    // (前プロジェクトの解析結果を新プロジェクトへ注入しない)。
                    if (root.equals(modelRoot)) {
                        publishResult(m);
                    }
                } catch (java.util.concurrent.CancellationException ex) {
                    // プロジェクト切替 (clear) で取り消し済み: 旧プロジェクトの結果は捨てる。
                    juml.util.AppLog.info("DoxygenResultCache",
                            "Doxygen run cancelled (project switched): " + root.getAbsolutePath());
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    juml.util.AppLog.error(juml.util.ErrorCode.DIAG_003, "DoxygenResultCache",
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
                    // 実行中にプロジェクトが切り替わって保留になっていた解析を開始する。
                    // (running は上で false に戻しているので runAsync はそのまま走る。)
                    if (pendingRoot != null) {
                        File pr = pendingRoot;
                        Runnable ps = pendingOnStart;
                        Consumer<String> pe = pendingOnError;
                        Runnable pf = pendingOnFinally;
                        clearPending();
                        runAsync(pr, ps, pe, pf);
                    }
                }
            }
        };
        worker.execute();
    }

    /** 結果を保持し、全リスナーへ通知する。 */
    private void publishResult(DoxModel m) {
        this.model = m;
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
