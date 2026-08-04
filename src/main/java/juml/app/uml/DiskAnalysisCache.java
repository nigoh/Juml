// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.DbBootstrap;
import juml.core.formats.uml.db.IndexDatabase;
import juml.core.formats.uml.db.IncrementalScanner;
import juml.core.formats.uml.db.IndexReader;
import juml.core.formats.uml.db.IndexWriter;
import juml.core.formats.uml.db.LegacyCacheArchiver;
import juml.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * プロジェクト解析結果 (Stage A ヘッダ + ソースファイルパス + モジュール紐付け) を
 * SQLite ベースで永続化するキャッシュ。{@link PersistentAnalysisCache} (TSV) の置換。
 *
 * <p>配置: {@code ~/.juml/cache/<shortHash>/index.db}
 * ({@link DbBootstrap#resolveDbFile(File, File)}).</p>
 *
 * <p>差分検出は DB 内 ({@code files.mtime}/{@code files.size}) で行うため、
 * プロジェクトルートだけで決まるディレクトリを 1 つ持ち回し続ける。
 * 旧 TSV ディレクトリは初回 open 時に {@link LegacyCacheArchiver} で
 * {@code .legacy-<ts>/} に退避する。</p>
 */
public final class DiskAnalysisCache {

    private static final String TOOL_VERSION = "juml-db-cache";

    private final File baseDir;
    private boolean legacyArchived;

    public DiskAnalysisCache() {
        this(defaultBaseDir());
    }

    public DiskAnalysisCache(File baseDir) {
        this.baseDir = baseDir;
    }

    /** {@code ~/.juml/cache} 等、OS に応じたキャッシュベースディレクトリ。 */
    public static File defaultBaseDir() {
        return DbBootstrap.defaultBaseDir();
    }

    /** ロード結果。 */
    public static final class Snapshot {
        private final List<JavaClassInfo> classes;
        private final ClassIndex index;

        public Snapshot(List<JavaClassInfo> classes, ClassIndex index) {
            this.classes = classes;
            this.index = index;
        }

        public List<JavaClassInfo> getClasses() {
            return classes;
        }

        public ClassIndex getIndex() {
            return index;
        }
    }

    /**
     * 指定プロジェクトの解析結果を DB から復元する (陳腐化チェックなし)。
     *
     * <p>DB が存在しない / classes が 0 件なら {@link Optional#empty()}。
     * 旧 TSV ディレクトリは見つけ次第退避する (初回 open 時のみ)。</p>
     */
    public Optional<Snapshot> load(File projectRoot, ProgressListener progress) {
        return load(projectRoot, progress, null);
    }

    /**
     * 指定プロジェクトの解析結果を DB から復元する。{@code currentJavaFiles} が
     * 非 null のときは、DB に記録した各ファイルの {@code mtime}/{@code size} と
     * 現在のファイルシステムを突き合わせ、追加/変更/削除が 1 件でもあれば
     * 「キャッシュミス」として {@link Optional#empty()} を返す (陳腐化した図を
     * 表示し続けないようにするため)。差分検出のためのファイル走査は full parse より
     * 十分に軽い。
     *
     * @param currentJavaFiles 現在 FS 上に存在する Java ソース一覧 (null で陳腐化チェック省略)
     */
    public Optional<Snapshot> load(File projectRoot, ProgressListener progress,
                                   List<File> currentJavaFiles) {
        ProgressListener prog = progress != null ? progress : ProgressListener.silent();
        archiveLegacyOnce();
        File dbFile = DbBootstrap.resolveDbFile(baseDir, projectRoot);
        if (!dbFile.isFile() || dbFile.length() == 0) {
            return Optional.empty();
        }
        prog.onProgress(0, -1, "Probing cache...");
        try (IndexDatabase db = IndexDatabase.openOrCreate(
                dbFile, projectRoot.getAbsolutePath(), TOOL_VERSION)) {
            IndexReader reader = new IndexReader(db.connection());
            int count = reader.classCount();
            if (count == 0) {
                return Optional.empty();
            }
            // 陳腐化チェック: DB 記録時から 1 ファイルでも追加/変更/削除されていたら
            // キャッシュを捨てて再解析させる (古いクラス一覧・消えたクラスを出さない)。
            if (currentJavaFiles != null) {
                IncrementalScanner.DiffResult diff = IncrementalScanner.diff(
                        db.connection(), projectRoot, IndexWriter.KIND_JAVA, currentJavaFiles);
                if (!diff.getAdded().isEmpty() || !diff.getModified().isEmpty()
                        || !diff.getDeletedPaths().isEmpty()) {
                    return Optional.empty();
                }
            }
            ClassIndex idx = reader.loadStageAClassIndex(projectRoot);
            List<JavaClassInfo> classes = idx.headers();
            prog.onProgress(count, count, "Loaded from cache");
            return Optional.of(new Snapshot(classes, idx));
        } catch (SQLException | IOException ex) {
            // 破損していたら無効化扱い (次回 save で上書きされる)
            return Optional.empty();
        }
    }

    /**
     * 解析結果を DB に保存する。既存内容は破棄してファイル単位で投入し直す。
     *
     * <p>ファイルごとに {@link IndexWriter#upsertFile} を 1 回ずつ呼ぶので、
     * 同 path の旧データは CASCADE で消えて新データで置き換わる。
     * ソースファイル不明なクラス (依存 JAR 由来など) は永続化対象外。</p>
     */
    public void save(File projectRoot, List<JavaClassInfo> classes, ClassIndex index)
            throws IOException {
        save(projectRoot, classes, index, null);
    }

    /**
     * 走査時点 (パース前) のソース状態。
     *
     * <p>陳腐化検出に使う {@code mtime}/{@code size} は<b>パースした内容と対になる値</b>
     * でなければならない。{@link #saveScanned} はこの値をそのまま DB へ書く。</p>
     */
    public static final class SourceStat {
        private final File file;
        private final long mtime;
        private final long size;

        public SourceStat(File file, long mtime, long size) {
            this.file = file;
            this.mtime = mtime;
            this.size = size;
        }

        public File getFile() {
            return file;
        }
    }

    /** {@code sources} の現在の mtime/size を採取する。<b>パースを始める前</b>に呼ぶこと。 */
    public static List<SourceStat> statAll(List<File> sources) {
        List<SourceStat> out = new ArrayList<>();
        if (sources != null) {
            for (File f : sources) {
                out.add(new SourceStat(f, f.lastModified(), f.length()));
            }
        }
        return out;
    }

    /**
     * 走査時点の状態 ({@link #statAll}) を添えて保存する。
     *
     * <p>{@code mtime}/{@code size} を保存時に採り直すと、<b>パース中に編集された
     * ファイル</b>について「新しい stat + 古い解析結果」を書いてしまう。次回ロードの
     * 陳腐化チェックは stat 一致で通ってしまうため、そのファイルは編集しても二度と
     * 再解析されない (恒久的に古い内容が出続ける)。走査時点の値を書けば、パース中の
     * 編集は次回「変更あり」と判定され、正しく再解析される。</p>
     */
    public void saveScanned(File projectRoot, List<JavaClassInfo> classes, ClassIndex index,
            List<SourceStat> scanned) throws IOException {
        saveInternal(projectRoot, classes, index, scanned);
    }

    /**
     * {@code allSources} を渡すと、クラスを 1 つも生まなかったソース
     * ({@code package-info.java} や空ファイル等) についても空の {@code files} 行を
     * 記録する。こうしないと陳腐化チェックで DB に無いこれらのファイルが毎回
     * 「追加された」と判定され、ディスクキャッシュが恒久的にヒットしなくなる。
     *
     * <p>この形は stat をここで採るため、パース中に編集されたファイルを取りこぼす。
     * 走査時点の stat を持っている呼び出し側は {@link #saveScanned} を使うこと。</p>
     */
    public void save(File projectRoot, List<JavaClassInfo> classes, ClassIndex index,
            List<File> allSources) throws IOException {
        saveInternal(projectRoot, classes, index, statAll(allSources));
    }

    private void saveInternal(File projectRoot, List<JavaClassInfo> classes, ClassIndex index,
            List<SourceStat> scanned) throws IOException {
        if (classes == null || index == null) {
            return;
        }
        archiveLegacyOnce();
        File dbFile = DbBootstrap.resolveDbFile(baseDir, projectRoot);
        ensureParent(dbFile);
        // 全件上書きするため、既存 DB は丸ごと破棄する (TSV 時代の挙動と同じ)。
        deleteQuietly(dbFile);
        deleteQuietly(new File(dbFile.getAbsolutePath() + "-wal"));
        deleteQuietly(new File(dbFile.getAbsolutePath() + "-shm"));

        try (IndexDatabase db = IndexDatabase.openOrCreate(
                dbFile, projectRoot.getAbsolutePath(), TOOL_VERSION)) {
            IndexWriter writer = new IndexWriter(db.connection());
            Map<File, List<JavaClassInfo>> byFile = groupBySourceFile(classes, index);
            Map<File, SourceStat> stats = new LinkedHashMap<>();
            // 走査対象だがクラスを生まなかったファイルも 0 クラス行として登録し、
            // 陳腐化チェックの「DB のファイル集合 == 走査したファイル集合」を保つ。
            if (scanned != null) {
                for (SourceStat st : scanned) {
                    stats.put(st.file, st);
                    byFile.putIfAbsent(st.file, java.util.Collections.emptyList());
                }
            }
            for (Map.Entry<File, List<JavaClassInfo>> e : byFile.entrySet()) {
                File source = e.getKey();
                String relPath = relativize(projectRoot, source);
                String module = moduleOf(e.getValue(), index);
                // 走査時点の値を最優先で使う。走査一覧に無いファイル (呼び出し側が
                // 一覧を渡さなかった場合) だけ現在値へフォールバックする。
                SourceStat st = stats.get(source);
                long mtime = st != null ? st.mtime : source.lastModified();
                long size = st != null ? st.size : source.length();
                writer.upsertFile(relPath, IndexWriter.KIND_JAVA, mtime, size,
                        module, null, e.getValue(), null);
            }
        } catch (SQLException ex) {
            throw new IOException("Failed to save analysis cache: " + ex.getMessage(), ex);
        }
    }

    /** 指定プロジェクトのキャッシュを削除 (再解析を強制したいとき)。 */
    public void invalidate(File projectRoot) {
        File dbFile = DbBootstrap.resolveDbFile(baseDir, projectRoot);
        deleteQuietly(dbFile);
        deleteQuietly(new File(dbFile.getAbsolutePath() + "-wal"));
        deleteQuietly(new File(dbFile.getAbsolutePath() + "-shm"));
    }

    // ---- internals ----

    private void archiveLegacyOnce() {
        if (legacyArchived) {
            return;
        }
        legacyArchived = true;
        if (!baseDir.isDirectory()) {
            return;
        }
        try {
            File archived = LegacyCacheArchiver.archiveLegacyDirs(baseDir);
            if (archived != null) {
                juml.util.AppLog.info("DiskAnalysisCache",
                        "Migrated legacy TSV cache to " + archived.getName() + "/ (will rescan)");
            }
        } catch (IOException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CACHE_003, "DiskAnalysisCache",
                    "Failed to archive legacy cache", ex);
        }
    }

    private static Map<File, List<JavaClassInfo>> groupBySourceFile(
            List<JavaClassInfo> classes, ClassIndex index) {
        Map<File, List<JavaClassInfo>> out = new LinkedHashMap<>();
        for (JavaClassInfo c : classes) {
            if (c == null || c.getQualifiedName() == null || c.getQualifiedName().isEmpty()) {
                continue;
            }
            File src = index.source(c.getQualifiedName()).orElse(null);
            if (src == null) {
                continue;
            }
            out.computeIfAbsent(src, k -> new ArrayList<>()).add(c);
        }
        return out;
    }

    private static String moduleOf(List<JavaClassInfo> classes, ClassIndex index) {
        for (JavaClassInfo c : classes) {
            String m = index.module(c.getQualifiedName()).orElse(null);
            if (m != null && !m.isEmpty()) {
                return m;
            }
        }
        return null;
    }

    private static String relativize(File projectRoot, File source) {
        String rootPath;
        String srcPath;
        try {
            rootPath = projectRoot.getCanonicalPath();
            srcPath = source.getCanonicalPath();
        } catch (IOException ex) {
            rootPath = projectRoot.getAbsolutePath();
            srcPath = source.getAbsolutePath();
        }
        String sep = File.separator;
        if (srcPath.startsWith(rootPath + sep)) {
            return srcPath.substring(rootPath.length() + sep.length());
        }
        if (srcPath.equals(rootPath)) {
            return "";
        }
        return srcPath;
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
    }

    private static void ensureParent(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + parent);
        }
    }
}
