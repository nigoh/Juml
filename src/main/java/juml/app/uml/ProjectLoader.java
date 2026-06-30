// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.CancelToken;
import juml.util.ErrorListener;
import juml.util.Messages;
import juml.util.ProgressListener;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.io.File;
import java.util.function.Consumer;

/**
 * プロジェクト解析の SwingWorker ライフサイクルを管理するクラス。
 *
 * <p>解析の開始・進捗更新・完了/失敗後の後処理を担う。
 * UI 状態（スコープ・図種）の変更は {@code onLoadSuccess} コールバック経由で
 * 呼び出し側（UmlMainFrame）が行う。</p>
 */
public final class ProjectLoader {

    private final ProjectAnalysisCache cache;
    private final ReferenceIndexCache refIndexCache;
    private final DiagramState state;
    private final ProjectTreePanel treePanel;
    private final ManifestSummaryPanel manifestSummaryPanel;
    private final JProgressBar loadProgress;
    private final LoadingGlassPane loadingOverlay;
    private final JMenuItem cancelLoadingItem;
    private final JLabel statusLabel;
    private final JFrame parentFrame;
    private final Consumer<CancelToken> cancelTokenSetter;
    private final Consumer<File> projectRootSetter;
    private final Consumer<File> onLoadSuccess;

    private SwingWorker<?, ?> activeWorker;
    private CancelToken activeCancelToken;

    public ProjectLoader(ProjectLoaderDeps deps) {
        this.cache = deps.cache;
        this.refIndexCache = deps.refIndexCache;
        this.state = deps.state;
        this.treePanel = deps.treePanel;
        this.manifestSummaryPanel = deps.manifestSummaryPanel;
        this.loadProgress = deps.loadProgress;
        this.loadingOverlay = deps.loadingOverlay;
        this.cancelLoadingItem = deps.cancelLoadingItem;
        this.statusLabel = deps.statusLabel;
        this.parentFrame = deps.parentFrame;
        this.cancelTokenSetter = deps.cancelTokenSetter;
        this.projectRootSetter = deps.projectRootSetter;
        this.onLoadSuccess = deps.onLoadSuccess;
    }

    /** プロジェクト解析を開始する。EDT から呼ぶこと。 */
    public void start(File root) {
        cancelActiveWorker();
        // AOSP 級ツリーを検出したら、巨大な非ソースディレクトリ (out/, prebuilts/,
        // .repo/ など) を走査対象から除外して解析時間・メモリを抑える。
        final boolean aosp = isAospTree(root);
        statusLabel.setText("Analyzing " + root.getName() + " ...");
        treePanel.clear();
        manifestSummaryPanel.setText("");
        loadProgress.setVisible(true);
        loadProgress.setIndeterminate(true);
        loadProgress.setString("Scanning...");
        if (loadingOverlay != null) {
            loadingOverlay.setStatus(aosp
                    ? "AOSP を検出: ビルド成果物を除外して解析中..."
                    : "解析中...");
            loadingOverlay.showOverlay();
        }
        if (aosp) {
            statusLabel.setText("AOSP project detected — excluding build outputs"
                    + " (out/, out-soong/, prebuilts/, .repo/) from analysis...");
        }
        cancelLoadingItem.setEnabled(true);
        final CancelToken cancel = new CancelToken();
        activeCancelToken = cancel;
        cancelTokenSetter.accept(cancel);
        if (loadingOverlay != null) {
            loadingOverlay.setCancelAction(cancel::cancel);
        }
        final ProgressListener prog = ProgressListener.throttled(
                (done, total, message) -> SwingUtilities.invokeLater(
                        () -> updateLoadProgress(done, total, message)),
                150L);
        final ProjectAnalysisCache.LoadOptions options =
                new ProjectAnalysisCache.LoadOptions();
        options.useAospDefaults = aosp;
        // AOSP 級ツリーは初回フルパースが重い。ヘッダのみ (Stage A) で即ツリー表示し、
        // 詳細はクラス展開・図描画時にオンデマンド昇格する。ディスクキャッシュにより
        // 2 回目以降の起動はさらに高速化する (通常プロジェクトは従来どおり FULL)。
        options.lazyDetails = aosp;
        options.useDiskCache = true;
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private Throwable error;
            private boolean cancelled;
            private java.util.List<juml.core.aosp.AndroidBpModule> bpModules =
                    java.util.Collections.emptyList();

            @Override
            protected Void doInBackground() {
                try {
                    cache.clear();
                    refIndexCache.invalidate();
                    cache.load(root, ErrorListener.silent(), prog, cancel, options);
                    cancelled = cancel.isCancelled();
                    // 左ツリー用に Android.bp (Soong Blueprint) も走査する。
                    // 重い処理になり得るのでバックグラウンドで実行し、失敗してもロードは継続。
                    if (!cancelled) {
                        try {
                            bpModules = new juml.core.aosp.AndroidBpParser().analyzeProject(root);
                        } catch (RuntimeException bpEx) {
                            bpModules = java.util.Collections.emptyList();
                            juml.util.AppLog.warn("ProjectLoader",
                                    "Android.bp scan failed; continuing without Soong modules",
                                    bpEx);
                        }
                    }
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                activeWorker = null;
                activeCancelToken = null;
                cancelTokenSetter.accept(null);
                cancelLoadingItem.setEnabled(false);
                if (loadingOverlay != null) {
                    loadingOverlay.hideOverlay();
                }
                loadProgress.setVisible(false);
                loadProgress.setIndeterminate(false);
                loadProgress.setValue(0);
                loadProgress.setString(null);
                if (error != null) {
                    juml.util.AppLog.error("ProjectLoader",
                            "Project analysis failed: " + root.getAbsolutePath(), error);
                    JOptionPane.showMessageDialog(parentFrame,
                            juml.util.Messages.get("dialog.analyzeFailed.message")
                                    + "\n" + error.getMessage(),
                            juml.util.Messages.get("dialog.analyzeFailed.title"),
                            JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText(" ");
                    return;
                }
                if (cancelled) {
                    statusLabel.setText(Messages.get("status.cancelled"));
                    return;
                }
                projectRootSetter.accept(root);
                treePanel.populate(cache.getAnalysis(), cache.getClasses(),
                        root.getName(), cache.getClassToModule(), cache.getIndex());
                // Android.bp モジュールをツリー最上位の Soong グループとして可視化する。
                treePanel.addSoongModules(bpModules, root.getAbsolutePath());
                state.sequenceEntry = null;
                state.activityEntry = null;
                state.callGraphEntry = null;
                state.sequenceHiddenParticipants.clear();
                state.currentScope = null;
                StringBuilder st = new StringBuilder();
                st.append("Analyzed ").append(cache.getClasses().size())
                        .append(" class(es) from ").append(root.getAbsolutePath());
                int missing = cache.getDependencyIndex().getMissingArtifacts().size();
                if (missing > 0) {
                    st.append(" — ").append(missing).append(" dependency(ies) not resolved");
                }
                statusLabel.setText(st.toString());
                onLoadSuccess.accept(root);
            }
        };
        activeWorker = worker;
        worker.execute();
    }

    /**
     * .jar/.aar/.class (またはそれらを含むフォルダ) を解析対象として開く。EDT から呼ぶこと。
     * SwingWorker でバイトコードヘッダを抽出し、完了後はプロジェクトロードと同じ後処理
     * (ツリー再構築・既定タブ表示) を行う。
     */
    public void startArchive(File archive) {
        cancelActiveWorker();
        statusLabel.setText("Reading " + archive.getName() + " ...");
        treePanel.clear();
        manifestSummaryPanel.setText("");
        loadProgress.setVisible(true);
        loadProgress.setIndeterminate(true);
        loadProgress.setString("Reading bytecode...");
        if (loadingOverlay != null) {
            loadingOverlay.setStatus("解析中...");
            loadingOverlay.showOverlay();
        }
        cancelLoadingItem.setEnabled(true);
        final CancelToken cancel = new CancelToken();
        activeCancelToken = cancel;
        cancelTokenSetter.accept(cancel);
        if (loadingOverlay != null) {
            loadingOverlay.setCancelAction(cancel::cancel);
        }
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            private Throwable error;
            private boolean cancelled;

            @Override
            protected Void doInBackground() {
                try {
                    cache.clear();
                    refIndexCache.invalidate();
                    cache.loadFromArchive(archive, ErrorListener.silent());
                    cancelled = cancel.isCancelled();
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                activeWorker = null;
                activeCancelToken = null;
                cancelTokenSetter.accept(null);
                cancelLoadingItem.setEnabled(false);
                if (loadingOverlay != null) {
                    loadingOverlay.hideOverlay();
                }
                loadProgress.setVisible(false);
                loadProgress.setIndeterminate(false);
                loadProgress.setString(null);
                if (error != null) {
                    juml.util.AppLog.error("ProjectLoader",
                            "Archive read failed: " + archive.getAbsolutePath(), error);
                    JOptionPane.showMessageDialog(parentFrame,
                            Messages.get("archive.readFailed") + error.getMessage(),
                            Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText(" ");
                    return;
                }
                if (cancelled) {
                    statusLabel.setText(Messages.get("status.cancelled"));
                    return;
                }
                projectRootSetter.accept(archive);
                treePanel.populate(cache.getAnalysis(), cache.getClasses(),
                        archive.getName(), cache.getClassToModule(), cache.getIndex());
                state.sequenceEntry = null;
                state.activityEntry = null;
                state.callGraphEntry = null;
                state.sequenceHiddenParticipants.clear();
                state.currentScope = null;
                statusLabel.setText("Read " + cache.getClasses().size()
                        + " class(es) from " + archive.getAbsolutePath());
                onLoadSuccess.accept(archive);
            }
        };
        activeWorker = worker;
        worker.execute();
    }

    private void cancelActiveWorker() {
        if (activeCancelToken != null) {
            activeCancelToken.cancel();
        }
        if (activeWorker != null && !activeWorker.isDone()) {
            activeWorker.cancel(true);
        }
        activeWorker = null;
        activeCancelToken = null;
        cancelTokenSetter.accept(null);
    }

    /**
     * 与えられたルートが AOSP (Android Open Source Project) チェックアウトらしいかを
     * 浅いチェックだけで判定する (ディレクトリ全走査はしない)。
     *
     * <p>判定シグナル: {@code .repo/} がある / {@code build/envsetup.sh} がある /
     * {@code build/soong/} がある / AOSP 典型のトップレベルディレクトリ
     * (frameworks, packages, system, hardware, vendor, bionic, bootable) が
     * 3 つ以上そろっている、のいずれか。</p>
     */
    static boolean isAospTree(File root) {
        if (root == null || !root.isDirectory()) {
            return false;
        }
        if (new File(root, ".repo").isDirectory()
                || new File(root, "build/envsetup.sh").isFile()
                || new File(root, "build/soong").isDirectory()) {
            return true;
        }
        String[] markers = {
            "frameworks", "packages", "system", "hardware",
            "vendor", "bionic", "bootable",
        };
        int hits = 0;
        for (String m : markers) {
            if (new File(root, m).isDirectory() && ++hits >= 3) {
                return true;
            }
        }
        return false;
    }

    void updateLoadProgress(int done, int total, String message) {
        if (total > 0) {
            if (loadProgress.isIndeterminate()) {
                loadProgress.setIndeterminate(false);
            }
            loadProgress.setMaximum(total);
            loadProgress.setValue(Math.min(done, total));
            loadProgress.setString(done + "/" + total);
            statusLabel.setText("Analyzing " + done + "/" + total
                    + (message != null && !message.isEmpty() ? " — " + message : ""));
            if (loadingOverlay != null) {
                loadingOverlay.setStatus("解析中... " + done + "/" + total);
            }
        } else {
            loadProgress.setIndeterminate(true);
            loadProgress.setString(message != null ? message : "Scanning...");
            if (message != null) {
                statusLabel.setText(message);
            }
            if (loadingOverlay != null && message != null && !message.isEmpty()) {
                loadingOverlay.setStatus(message);
            }
        }
    }
}
