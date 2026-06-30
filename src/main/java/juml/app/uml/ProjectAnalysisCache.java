// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.android.AndroidProjectAnalyzer;
import juml.core.formats.java.AndroidProjectScanner;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.DependencyJarIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.UmlGenerator;
import juml.util.CancelToken;
import juml.util.ErrorListener;
import juml.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 開いているプロジェクトの解析結果 (Android プロジェクト解析 + UML 用クラス情報)
 * をプロジェクトルート単位でキャッシュする。
 *
 * <p>図種を切り替えるたびに再解析するのを避けるため、{@link #load(File, ErrorListener)}
 * は同じプロジェクトルートに対して 1 回だけ解析を実行し、それ以降は
 * メモ化された結果を {@link #getAnalysis()} / {@link #getClasses()} で返す。</p>
 *
 * <p>大規模プロジェクト向けに、進捗・キャンセル・ヘッダのみロード (Stage A) を
 * 受け取る {@link #load(File, ErrorListener, ProgressListener, CancelToken, LoadOptions)}
 * を提供する。{@link ClassIndex} はモジュール紐付けとオンデマンド Stage B 昇格に使う。</p>
 */
public final class ProjectAnalysisCache {

    /** プロジェクトロードオプション。 */
    public static final class LoadOptions {
        /** 取り込みファイル数上限 (負値で無制限)。 */
        public int maxFiles = -1;
        /** Kotlin (.kt) ファイルもスキャンに含める (パースはしない)。 */
        public boolean includeKotlin = false;
        /** AOSP 級プロジェクト向けの追加除外ディレクトリを有効化する。 */
        public boolean useAospDefaults = false;
        /**
         * 詳細パース (フィールド・メソッド・呼び出し列・コメント) を遅延する。
         * true ならヘッダのみ取得し、必要なクラスだけ {@link ClassIndex#detail} で昇格させる。
         */
        public boolean lazyDetails = false;
        /** ディスクキャッシュを利用する (lazyDetails と組み合わせて意味がある)。 */
        public boolean useDiskCache = true;
    }

    private File projectRoot;
    private AndroidProjectAnalysis analysis;
    private List<JavaClassInfo> classes = Collections.emptyList();
    private ClassIndex index = new ClassIndex();
    private DependencyJarIndex dependencyIndex = new DependencyJarIndex();
    private final DiskAnalysisCache disk;
    /**
     * {@code classes} がヘッダのみ (Stage A) かどうか。{@code lazyDetails} ロードや
     * ディスクキャッシュ復元時に true。true のときメンバー情報が要る消費者は
     * {@link #getDetailedClasses()} を経由する。
     */
    private boolean lazy = false;
    /** lazy=true 時、全クラスを Stage B 昇格した結果のメモ化 (初回 getDetailedClasses で構築)。 */
    private List<JavaClassInfo> detailedClasses;

    public ProjectAnalysisCache() {
        this(new DiskAnalysisCache());
    }

    public ProjectAnalysisCache(DiskAnalysisCache disk) {
        this.disk = disk;
    }

    /**
     * プロジェクトを解析してキャッシュする。すでに同じルートで解析済みなら何もしない。
     *
     * @param root     プロジェクトルート (Gradle / Android プロジェクトのトップ)
     * @param listener 解析中の警告を受け取るリスナー。null なら silent。
     */
    public void load(File root, ErrorListener listener) throws IOException {
        load(root, listener, null, null, null);
    }

    /**
     * 進捗・キャンセル・ロードオプション付きの解析。
     *
     * @param progress 進捗リスナー (null なら silent)
     * @param cancel   キャンセルトークン (null なら NONE)
     * @param options  ロードオプション (null ならデフォルト)
     */
    public void load(File root, ErrorListener listener, ProgressListener progress,
                     CancelToken cancel, LoadOptions options) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root is null");
        }
        if (projectRoot != null && projectRoot.equals(root)) {
            return;
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        ProgressListener p = progress != null ? progress : ProgressListener.silent();
        CancelToken c = cancel != null ? cancel : CancelToken.NONE;
        LoadOptions o = options != null ? options : new LoadOptions();

        p.onProgress(0, -1, "Analyzing project...");
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root, l);
        if (c.isCancelled()) {
            return;
        }

        AndroidProjectScanner.Options scanOpts = new AndroidProjectScanner.Options();
        scanOpts.maxFiles = o.maxFiles;
        scanOpts.includeKotlin = o.includeKotlin;
        scanOpts.useAospDefaults = o.useAospDefaults;
        scanOpts.cancelToken = c;
        scanOpts.includeAidl = true;

        // ディスクキャッシュは Stage A 用の永続化のみサポート。
        // lazyDetails=true でかつ Hit したら parse をスキップ。
        if (o.lazyDetails && o.useDiskCache && disk != null) {
            try {
                Optional<DiskAnalysisCache.Snapshot> snap = disk.load(root, p);
                if (snap.isPresent()) {
                    this.projectRoot = root;
                    this.analysis = a;
                    this.classes = snap.get().getClasses();
                    this.index = snap.get().getIndex();
                    this.lazy = true; // ディスクキャッシュは Stage A スナップショット
                    this.detailedClasses = null;
                    return;
                }
            } catch (RuntimeException ex) {
                l.onError(null, -1, "disk cache load failed: " + ex.getMessage());
            }
        }

        UmlGenerator.ParseMode mode = o.lazyDetails
                ? UmlGenerator.ParseMode.HEADERS_ONLY
                : UmlGenerator.ParseMode.FULL;
        UmlGenerator.ProjectParseResult result = UmlGenerator.extractFromProjectDetailed(
                root, scanOpts, l, p, c, true, mode);
        if (c.isCancelled()) {
            return;
        }
        this.projectRoot = root;
        this.analysis = a;
        this.classes = result.getClasses() != null ? result.getClasses() : Collections.emptyList();
        this.index = result.getIndex() != null ? result.getIndex() : new ClassIndex();
        this.dependencyIndex = result.getDependencyIndex() != null
                ? result.getDependencyIndex() : new DependencyJarIndex();
        this.lazy = o.lazyDetails; // HEADERS_ONLY なら Stage A
        this.detailedClasses = null;

        // 解析成功後にディスクキャッシュを更新 (Stage A 情報を永続化)
        if (o.lazyDetails && o.useDiskCache && disk != null) {
            try {
                disk.save(root, this.classes, this.index);
            } catch (IOException ex) {
                l.onError(null, -1, "disk cache save failed: " + ex.getMessage());
            }
        }
    }

    /**
     * 任意パスの {@code .jar}/{@code .aar}/{@code .class} (またはそれらを含むフォルダ) を
     * 解析対象として読み込み、プロジェクトロード時と同じく {@link #getClasses()} /
     * {@link #getIndex()} で参照できる状態にする。
     *
     * <p>アーカイブ由来クラスはバイトコードヘッダ抽出 ({@link UmlGenerator#extractFromArchive})
     * のみで、ソース再パースの対象にはならない (ソース未登録のため {@link ClassIndex#detail}
     * はヘッダをそのまま返す)。Android プロジェクト解析 (manifest 等) は伴わないため
     * {@link #getAnalysis()} は null になる。</p>
     */
    public void loadFromArchive(File archive, ErrorListener listener) throws IOException {
        if (archive == null) {
            throw new IllegalArgumentException("archive is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        List<JavaClassInfo> infos = UmlGenerator.extractFromArchive(archive, l);
        ClassIndex idx = new ClassIndex();
        for (JavaClassInfo c : infos) {
            // source=null: 詳細昇格でソース再パースを試みない (バイナリ由来のため)
            idx.put(c, null, null);
        }
        this.projectRoot = archive;
        this.analysis = null;
        this.classes = infos;
        this.index = idx;
        this.dependencyIndex = new DependencyJarIndex();
        this.lazy = false; // アーカイブはソース未登録のため昇格不可 (ヘッダ確定)
        this.detailedClasses = null;
    }

    /**
     * 現在のプロジェクトの解析キャッシュをメモリ・ディスク両方から破棄する。
     * 次回 {@link #load} で再解析が強制される。プロジェクト未読込なら no-op。
     */
    public void invalidate() {
        if (projectRoot != null && disk != null) {
            disk.invalidate(projectRoot);
        }
        clear();
    }

    /** キャッシュをクリアする (プロジェクトを閉じたとき等)。 */
    public void clear() {
        projectRoot = null;
        analysis = null;
        classes = Collections.emptyList();
        index = new ClassIndex();
        dependencyIndex = new DependencyJarIndex();
        lazy = false;
        detailedClasses = null;
    }

    /**
     * メンバー (フィールド/メソッド/呼び出し) まで含む詳細クラス一覧を返す。
     *
     * <p>通常ロード (FULL) では {@link #getClasses()} と同一インスタンスを返す (no-op)。
     * lazyDetails / ディスクキャッシュ復元で {@code classes} がヘッダのみ (Stage A) の
     * ときは、{@link ClassIndex#detail} で全クラスを Stage B 昇格して返し、結果を
     * メモ化する。メンバーを表示・集計する消費者 (メンバー一覧・関数一覧・
     * シーケンス起点ピッカー等) はこちらを使うこと。</p>
     *
     * <p>大規模プロジェクトでは初回呼び出しが全ソース再パースを伴うため、
     * UI スレッドではなく SwingWorker から呼ぶことが望ましい。</p>
     */
    public synchronized List<JavaClassInfo> getDetailedClasses() {
        if (!lazy) {
            return classes;
        }
        if (detailedClasses != null) {
            return detailedClasses;
        }
        List<JavaClassInfo> out = new java.util.ArrayList<>(classes.size());
        for (JavaClassInfo c : classes) {
            if (c.isDetailed()) {
                out.add(c);
                continue;
            }
            JavaClassInfo d = index.detail(c.getQualifiedName(), ErrorListener.silent());
            out.add(d != null ? d : c);
        }
        detailedClasses = out;
        return out;
    }

    /** {@code classes} がヘッダのみ (Stage A) か。詳細が要る処理の分岐に使う。 */
    public boolean isLazy() {
        return lazy;
    }

    /**
     * {@link #getDetailedClasses()} が再パースなしで即返せる状態か。
     * 非 lazy (FULL/アーカイブ) なら常に true。lazy でも一度昇格してメモ化済みなら true。
     * UI 側はこれを見て、false のときだけ背景スレッドで昇格すれば EDT を止めずに済む。
     */
    public synchronized boolean isDetailedReady() {
        return !lazy || detailedClasses != null;
    }

    public File getProjectRoot() {
        return projectRoot;
    }

    public AndroidProjectAnalysis getAnalysis() {
        return analysis;
    }

    public List<JavaClassInfo> getClasses() {
        return classes;
    }

    public ClassIndex getIndex() {
        return index;
    }

    /** 依存 JAR/AAR の遅延解決インデックス (Gradle 宣言由来)。常に非 null。 */
    public DependencyJarIndex getDependencyIndex() {
        return dependencyIndex;
    }

    /** 完全修飾名 → モジュール名のマップ (Gradle 解析由来)。 */
    public Map<String, String> getClassToModule() {
        return index.moduleMap();
    }

    public boolean isLoaded() {
        return projectRoot != null;
    }
}
