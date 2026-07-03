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
            for (Map.Entry<File, List<JavaClassInfo>> e : byFile.entrySet()) {
                File source = e.getKey();
                String relPath = relativize(projectRoot, source);
                String module = moduleOf(e.getValue(), index);
                long mtime = source.lastModified();
                long size = source.length();
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
