// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.CancelToken;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;
import java.io.File;
import java.util.function.Consumer;

/**
 * {@link ProjectLoader} が必要とするキャッシュ・UI 参照・コールバックを 1 つにまとめた
 * 受け渡し用ホルダ。コンストラクタ引数の肥大化 (ParameterNumber) を避けるため、
 * 各依存をフィールド代入で設定してから {@link ProjectLoader} に渡す。
 */
public final class ProjectLoaderDeps {
    public ProjectAnalysisCache cache;
    public ReferenceIndexCache refIndexCache;
    public DiagramState state;
    public ProjectTreePanel treePanel;
    public ManifestSummaryPanel manifestSummaryPanel;
    public JProgressBar loadProgress;
    public LoadingGlassPane loadingOverlay;
    public JMenuItem cancelLoadingItem;
    public JLabel statusLabel;
    public JFrame parentFrame;
    public Consumer<CancelToken> cancelTokenSetter;
    public Consumer<File> projectRootSetter;
    public Consumer<File> onLoadSuccess;
}
