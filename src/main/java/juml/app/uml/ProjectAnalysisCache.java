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
 *
 * <p><b>並行性:</b> ロードはバックグラウンドの SwingWorker、参照は EDT と描画ワーカーから
 * 同時に行われる。結果一式は不変スナップショット ({@link Snapshot}) として単一の
 * volatile フィールドに発行し、読み手は常に「ある時点の一貫した組」を見る。
 * さらに {@link #clear()} が世代を進め、追い越された古いロードが遅れて結果を
 * 発行する (新しいプロジェクトの結果を古い結果で上書きする) ことを防ぐ。</p>
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

    /** ロード結果一式の不変スナップショット。読み手はこの単位で一貫性を得る。 */
    private static final class Snapshot {
        final File projectRoot;
        final AndroidProjectAnalysis analysis;
        final List<JavaClassInfo> classes;
        final ClassIndex index;
        final DependencyJarIndex dependencyIndex;
        /**
         * {@code classes} がヘッダのみ (Stage A) かどうか。{@code lazyDetails} ロードや
         * ディスクキャッシュ復元時に true。true のときメンバー情報が要る消費者は
         * {@link #getDetailedClasses()} を経由する。
         */
        final boolean lazy;

        Snapshot(File projectRoot, AndroidProjectAnalysis analysis,
                 List<JavaClassInfo> classes, ClassIndex index,
                 DependencyJarIndex dependencyIndex, boolean lazy) {
            this.projectRoot = projectRoot;
            this.analysis = analysis;
            this.classes = classes;
            this.index = index;
            this.dependencyIndex = dependencyIndex;
            this.lazy = lazy;
        }

        static Snapshot empty() {
            return new Snapshot(null, null, Collections.emptyList(),
                    new ClassIndex(), new DependencyJarIndex(), false);
        }
    }

    private volatile Snapshot snap = Snapshot.empty();
    /** {@link #clear()} のたびに進む世代。古いロードの遅延発行を弾くために使う。 */
    private long generation;
    private final DiskAnalysisCache disk;
    /** lazy=true 時、全クラスを Stage B 昇格した結果のメモ化 (初回 getDetailedClasses で構築)。 */
    private List<JavaClassInfo> detailedClasses;
    /** {@code detailedClasses} がどのスナップショットに対する昇格結果か。 */
    private Snapshot detailedFor;

    public ProjectAnalysisCache() {
        this(new DiskAnalysisCache());
    }

    public ProjectAnalysisCache(DiskAnalysisCache disk) {
        this.disk = disk;
    }

    /**
     * ディスクキャッシュの陳腐化チェック用に、現在のパース対象ソース一覧を走査する
     * (パースはしない)。save は {@code .java} だけでなく {@code .kt} / {@code .aidl}
     * から生成したクラスも {@code KIND_JAVA} で永続化するため、突き合わせる現在側も
     * 同じ拡張子を含める。{@code .java} だけに絞ると DB の {@code .kt} / {@code .aidl}
     * 行が毎回「削除された」と誤判定され、キャッシュが常にヒットしなくなる。
     */
    private static List<File> scanSourceFiles(File root,
            juml.core.formats.java.AndroidProjectScanner.Options scanOpts) {
        List<File> all = juml.core.formats.java.AndroidProjectScanner.scan(root, scanOpts);
        List<File> sources = new java.util.ArrayList<>();
        for (File f : all) {
            String name = f.getName();
            if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".aidl")) {
                sources.add(f);
            }
        }
        return sources;
    }

    /** 現在の世代を返す (ロード開始時に捕捉し、発行時に照合する)。 */
    private synchronized long currentGeneration() {
        return generation;
    }

    /**
     * 世代が変わっていなければスナップショットを発行する。
     *
     * @return 発行できた場合 true。追い越された古いロードなら false (結果は破棄)。
     */
    private synchronized boolean install(long gen, Snapshot s) {
        if (generation != gen) {
            return false;
        }
        this.snap = s;
        this.detailedClasses = null;
        this.detailedFor = null;
        return true;
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
        Snapshot cur = snap;
        if (cur.projectRoot != null && cur.projectRoot.equals(root)) {
            return;
        }
        long gen = currentGeneration();
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
        // lazyDetails=true でかつ Hit したら parse をスキップ。ただし DB 記録時から
        // ソースが 1 つでも追加/変更/削除されていたらキャッシュを捨てて再解析する
        // (陳腐化検出のための走査は full parse より十分軽い)。
        // 走査したソース一覧は陳腐化チェック (disk.load) と save の両方で使うため
        // 一度だけ求めて共有する。save 側に渡すことで 0 クラスのファイル
        // (package-info.java 等) も DB に記録され、毎回ミスするのを防ぐ。
        List<File> currentSources = null;
        if (o.lazyDetails && o.useDiskCache && disk != null) {
            try {
                currentSources = scanSourceFiles(root, scanOpts);
                if (c.isCancelled()) {
                    return;
                }
                Optional<DiskAnalysisCache.Snapshot> diskSnap =
                        disk.load(root, p, currentSources);
                if (diskSnap.isPresent()) {
                    // 依存 JAR インデックスは DB に持たないため、既に得ている analysis から
                    // 再構築する (parse はスキップ)。省くと 2 回目以降のロードで外部型の
                    // <<external>> 印が消え、初回ロードと図が食い違う。
                    DependencyJarIndex depIndex = UmlGenerator.buildDependencyIndex(root, a, l);
                    install(gen, new Snapshot(root, a, diskSnap.get().getClasses(),
                            diskSnap.get().getIndex(), depIndex, true));
                    return;
                }
            } catch (RuntimeException ex) {
                l.onError(juml.util.ErrorCode.CACHE_001, null, -1, "disk cache load failed: " + ex.getMessage());
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
        List<JavaClassInfo> classes = result.getClasses() != null
                ? result.getClasses() : Collections.emptyList();
        ClassIndex index = result.getIndex() != null ? result.getIndex() : new ClassIndex();
        DependencyJarIndex depIndex = result.getDependencyIndex() != null
                ? result.getDependencyIndex() : new DependencyJarIndex();
        if (!install(gen, new Snapshot(root, a, classes, index, depIndex, o.lazyDetails))) {
            // 追い越された古いロード。新しいプロジェクトの結果を汚さないよう破棄する。
            return;
        }

        // 解析成功後にディスクキャッシュを更新 (Stage A 情報を永続化)
        if (o.lazyDetails && o.useDiskCache && disk != null) {
            try {
                disk.save(root, classes, index, currentSources);
            } catch (IOException ex) {
                l.onError(juml.util.ErrorCode.CACHE_002, null, -1, "disk cache save failed: " + ex.getMessage());
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
        long gen = currentGeneration();
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        List<JavaClassInfo> infos = UmlGenerator.extractFromArchive(archive, l);
        ClassIndex idx = new ClassIndex();
        for (JavaClassInfo c : infos) {
            // source=null: 詳細昇格でソース再パースを試みない (バイナリ由来のため)
            idx.put(c, null, null);
        }
        // アーカイブはソース未登録のため昇格不可 (ヘッダ確定) → lazy=false
        install(gen, new Snapshot(archive, null, infos, idx, new DependencyJarIndex(), false));
    }

    /**
     * 現在のプロジェクトの解析キャッシュをメモリ・ディスク両方から破棄する。
     * 次回 {@link #load} で再解析が強制される。プロジェクト未読込なら no-op。
     */
    public void invalidate() {
        File root = snap.projectRoot;
        if (root != null && disk != null) {
            disk.invalidate(root);
        }
        clear();
    }

    /** キャッシュをクリアする (プロジェクトを閉じたとき等)。 */
    public synchronized void clear() {
        generation++;
        snap = Snapshot.empty();
        detailedClasses = null;
        detailedFor = null;
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
     * UI スレッドではなく SwingWorker から呼ぶことが望ましい。昇格中にプロジェクトが
     * 切り替わった場合、その昇格結果はメモ化しない (旧プロジェクトのクラス一覧が
     * 新プロジェクトの詳細として蘇るのを防ぐ)。</p>
     */
    public List<JavaClassInfo> getDetailedClasses() {
        Snapshot s = snap;
        if (!s.lazy) {
            return s.classes;
        }
        synchronized (this) {
            if (detailedClasses != null && detailedFor == s) {
                return detailedClasses;
            }
        }
        // モニタ外で昇格する (長時間のフルパース中に clear()/load() をブロックしない)。
        List<JavaClassInfo> out = new java.util.ArrayList<>(s.classes.size());
        for (JavaClassInfo c : s.classes) {
            if (c.isDetailed()) {
                out.add(c);
                continue;
            }
            JavaClassInfo d = s.index.detail(c.getQualifiedName(), ErrorListener.silent());
            out.add(d != null ? d : c);
        }
        synchronized (this) {
            if (snap == s) {
                detailedClasses = out;
                detailedFor = s;
            }
        }
        return out;
    }

    /** {@code classes} がヘッダのみ (Stage A) か。詳細が要る処理の分岐に使う。 */
    public boolean isLazy() {
        return snap.lazy;
    }

    /**
     * {@link #getDetailedClasses()} が再パースなしで即返せる状態か。
     * 非 lazy (FULL/アーカイブ) なら常に true。lazy でも一度昇格してメモ化済みなら true。
     * UI 側はこれを見て、false のときだけ背景スレッドで昇格すれば EDT を止めずに済む。
     */
    public synchronized boolean isDetailedReady() {
        Snapshot s = snap;
        return !s.lazy || (detailedClasses != null && detailedFor == s);
    }

    public File getProjectRoot() {
        return snap.projectRoot;
    }

    public AndroidProjectAnalysis getAnalysis() {
        return snap.analysis;
    }

    public List<JavaClassInfo> getClasses() {
        return snap.classes;
    }

    public ClassIndex getIndex() {
        return snap.index;
    }

    /** 依存 JAR/AAR の遅延解決インデックス (Gradle 宣言由来)。常に非 null。 */
    public DependencyJarIndex getDependencyIndex() {
        return snap.dependencyIndex;
    }

    /** 完全修飾名 → モジュール名のマップ (Gradle 解析由来)。 */
    public Map<String, String> getClassToModule() {
        return snap.index.moduleMap();
    }

    public boolean isLoaded() {
        return snap.projectRoot != null;
    }

    /**
     * テスト専用: 実解析を走らせずに「root が読込済み」の状態を作る
     * ({@code isLoaded()} が true になる)。本番コードから呼ばないこと。
     */
    synchronized void setLoadedRootForTest(File root) {
        generation++;
        snap = new Snapshot(root, null, Collections.emptyList(),
                new ClassIndex(), new DependencyJarIndex(), false);
        detailedClasses = null;
        detailedFor = null;
    }
}
