// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceIndexBuilder;
import juml.util.ErrorListener;

import java.io.File;

/**
 * {@link ReferenceIndex} を {@link ProjectAnalysisCache} の上に lazy に構築・キャッシュする。
 *
 * <p>GUI の Impact / Reverse-Reference パネルが同じインデックスを共有することで
 * 二度構築を避ける。プロジェクトが切り替わったら自動的に再構築する。</p>
 *
 * <p>スレッド安全。{@link #get()} は呼び出し元のスレッドで構築するので、
 * 重い構築は SwingWorker などで実行することを推奨。</p>
 */
public final class ReferenceIndexCache {

    private final ProjectAnalysisCache projectCache;
    private ReferenceIndex cached;
    private File cachedFor;

    public ReferenceIndexCache(ProjectAnalysisCache projectCache) {
        if (projectCache == null) {
            throw new IllegalArgumentException("projectCache");
        }
        this.projectCache = projectCache;
    }

    /**
     * 現在のプロジェクトの {@link ReferenceIndex} を返す。
     * プロジェクトが未ロードなら null。プロジェクト切り替え時は再構築。
     */
    public synchronized ReferenceIndex get() {
        File root = projectCache.getProjectRoot();
        if (root == null || !projectCache.isLoaded()) {
            return null;
        }
        if (cached != null && root.equals(cachedFor)) {
            return cached;
        }
        ReferenceIndex idx = new ReferenceIndex();
        ReferenceIndexBuilder builder = new ReferenceIndexBuilder(
                idx,
                projectCache.getIndex(),
                projectCache.getDependencyIndex(),
                ErrorListener.silent());
        builder.addAll(projectCache.getClasses());
        this.cached = idx;
        this.cachedFor = root;
        return idx;
    }

    /** キャッシュを強制無効化する。プロジェクト再読み込み時に呼ぶ。 */
    public synchronized void invalidate() {
        this.cached = null;
        this.cachedFor = null;
    }

    /** 現在キャッシュされたインデックスがあるか? (構築せずに判定)。 */
    public synchronized boolean isReady() {
        File root = projectCache.getProjectRoot();
        return cached != null && root != null && root.equals(cachedFor);
    }
}
