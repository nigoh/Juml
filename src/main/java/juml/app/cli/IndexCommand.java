// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.aaos.AidlBindingResolver;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidManifestParser;
import juml.core.formats.java.AndroidProjectScanner;
import juml.core.formats.uml.AidlParser;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.UmlGenerator;
import juml.core.formats.uml.db.DbBootstrap;
import juml.core.formats.uml.db.GradleProjectScope;
import juml.core.formats.uml.db.IncrementalScanner;
import juml.core.formats.uml.db.IndexDatabase;
import juml.core.formats.uml.db.IndexReader;
import juml.core.formats.uml.db.IndexWriter;
import juml.core.formats.uml.db.dao.FilesDao;
import juml.core.formats.uml.db.ingest.AidlIngestor;
import juml.core.formats.uml.db.ingest.ComponentIngestor;
import juml.core.formats.uml.db.ingest.EndpointAggregator;
import juml.core.formats.uml.db.ingest.ManifestIngestor;
import juml.core.screen.IntentNavigationDetector;
import juml.core.screen.ScreenTransition;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code java -jar Juml.jar index <root>} サブコマンドの実装。
 *
 * <p>事前フルスキャンに加えて、2 回目以降はファイル mtime/size 差分で
 * 変更されたファイルだけを再パースして DB に流し込む (incremental 更新)。</p>
 *
 * <p>本コマンドが書き込むテーブル:</p>
 * <ul>
 *   <li>classes / fields / methods / class_interfaces / class_imports — .java と .aidl の構造</li>
 *   <li>files / modules — 入力ファイル台帳</li>
 *   <li>manifests / intent_filters — AndroidManifest.xml の宣言</li>
 *   <li>components — Manifest 宣言 + ソース継承 (Activity/Service/Fragment/...) 集約</li>
 *   <li>aidl_interfaces / aidl_methods / aidl_bindings — AIDL 構造と Stub 実装紐付け</li>
 *   <li>external_endpoints — Intent / Manifest / AIDL 経由の外部境界俯瞰</li>
 * </ul>
 *
 * <p>呼び出し例:</p>
 * <pre>
 *   java -jar Juml.jar index /path/to/Car
 *   java -jar Juml.jar index /path/to/Car --db /tmp/x.db
 * </pre>
 */
public final class IndexCommand {

    private static final String TOOL_VERSION = "juml-index-cmd";

    /** コマンド結果。stderr 報告用。 */
    public static final class Result {
        public final int javaScanned;
        public final int aidlScanned;
        public final int manifestScanned;
        /** PR5 互換: java + aidl + manifest の合算ファイル数。 */
        public final int filesScanned;
        public final int filesAdded;
        public final int filesModified;
        public final int filesUnchanged;
        public final int filesDeleted;
        public final long elapsedMs;
        public final File dbFile;

        /** 増分スキャンのファイル増減カウント (added/modified/unchanged/deleted) を束ねる。 */
        public static final class FileDelta {
            public final int added;
            public final int modified;
            public final int unchanged;
            public final int deleted;

            public FileDelta(int added, int modified, int unchanged, int deleted) {
                this.added = added;
                this.modified = modified;
                this.unchanged = unchanged;
                this.deleted = deleted;
            }
        }

        Result(int javaScanned, int aidlScanned, int manifestScanned,
                FileDelta delta, long elapsedMs, File dbFile) {
            this.javaScanned = javaScanned;
            this.aidlScanned = aidlScanned;
            this.manifestScanned = manifestScanned;
            this.filesScanned = javaScanned + aidlScanned + manifestScanned;
            this.filesAdded = delta.added;
            this.filesModified = delta.modified;
            this.filesUnchanged = delta.unchanged;
            this.filesDeleted = delta.deleted;
            this.elapsedMs = elapsedMs;
            this.dbFile = dbFile;
        }
    }

    /** {@link GradleProjectScope} 解決後のターゲット 3 種類。 */
    static final class Targets {
        final List<File> javaFiles = new ArrayList<>();
        final List<File> aidlFiles = new ArrayList<>();
        final List<File> manifestFiles = new ArrayList<>();
    }

    private IndexCommand() {
    }

    /**
     * {@code [index, <root>, ...]} を受け取り index を実行する。
     *
     * @param args     {@code main(String[])} に渡された配列全体
     * @param listener 警告のリスナー
     * @return exit code (成功 = 0)
     */
    public static int execute(String[] args, ErrorListener listener) throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        File root = null;
        File explicitDb = null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--db".equals(a) || "-d".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("error: --db requires a path");
                    return 2;
                }
                explicitDb = new File(args[++i]);
            } else if (a.startsWith("--db=")) {
                explicitDb = new File(a.substring("--db=".length()));
            } else if (root == null) {
                root = new File(a);
            } else {
                System.err.println("error: unexpected argument: " + a);
                return 2;
            }
        }
        if (root == null) {
            System.err.println("usage: index <projectRoot> [--db <path>]");
            return 2;
        }
        if (!root.isDirectory()) {
            System.err.println("error: not a directory: " + root);
            return 2;
        }

        File dbFile = explicitDb != null
                ? explicitDb
                : DbBootstrap.resolveDbFile(root);
        Result result = run(root, dbFile, l);
        System.err.println("[juml] index complete: db=" + result.dbFile
                + " java=" + result.javaScanned
                + " aidl=" + result.aidlScanned
                + " manifest=" + result.manifestScanned
                + " (+" + result.filesAdded
                + " ~" + result.filesModified
                + " =" + result.filesUnchanged
                + " -" + result.filesDeleted + ")"
                + " elapsed=" + result.elapsedMs + "ms");
        return 0;
    }

    /** プログラム的に呼べる主処理。テスト・組み込み用。 */
    public static Result run(File projectRoot, File dbFile, ErrorListener listener)
            throws IOException {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        long start = System.currentTimeMillis();

        Targets targets = listTargets(projectRoot, l);

        ensureParent(dbFile);
        int added = 0;
        int modified = 0;
        int unchanged = 0;
        int deleted = 0;
        try (IndexDatabase db = IndexDatabase.openOrCreate(
                dbFile, projectRoot.getAbsolutePath(), TOOL_VERSION)) {

            // 入力ファイル別に diff + 再投入
            ScanCounts java = scanAndUpsert(db, projectRoot, IndexWriter.KIND_JAVA,
                    targets.javaFiles, IndexCommand::ingestJava, l);
            ScanCounts aidl = scanAndUpsert(db, projectRoot, IndexWriter.KIND_AIDL,
                    targets.aidlFiles, IndexCommand::ingestAidl, l);
            ScanCounts mani = scanAndUpsert(db, projectRoot, IndexWriter.KIND_MANIFEST,
                    targets.manifestFiles, IndexCommand::ingestManifest, l);
            added = java.added + aidl.added + mani.added;
            modified = java.modified + aidl.modified + mani.modified;
            unchanged = java.unchanged + aidl.unchanged + mani.unchanged;
            deleted = java.deleted + aidl.deleted + mani.deleted;

            // 集約フェーズ: 全 file レベル投入が終わってから、binding / endpoint / component の
            // 集約テーブルを作り直す (冪等性のため毎回 wipe → 再構築)。
            runAggregations(db, projectRoot, l);
        } catch (SQLException ex) {
            throw new IOException("Index failed: " + ex.getMessage(), ex);
        }
        long elapsed = System.currentTimeMillis() - start;
        return new Result(
                targets.javaFiles.size(), targets.aidlFiles.size(), targets.manifestFiles.size(),
                new Result.FileDelta(added, modified, unchanged, deleted), elapsed, dbFile);
    }

    // ---- target listing ----

    /** {@link GradleProjectScope} 経由で対象パスを決定し、Java / AIDL / Manifest を別個に列挙。 */
    static Targets listTargets(File projectRoot, ErrorListener listener) {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        Targets t = new Targets();
        GradleProjectScope.Scope scope = GradleProjectScope.resolve(projectRoot, l);

        if (scope.isFallback()) {
            // settings.gradle 無し: ルート全走査
            AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
            opts.includeAidl = false;
            opts.includeKotlin = false;
            t.javaFiles.addAll(AndroidProjectScanner.scan(projectRoot, opts));
            walkByExtension(projectRoot, ".aidl", t.aidlFiles);
            walkManifest(projectRoot, t.manifestFiles);
            return t;
        }

        // Gradle 推定: モジュールごとの src/*/java / aidl / AndroidManifest.xml を走査
        for (GradleProjectScope.ScopePath sp : scope.getPaths()) {
            switch (sp.getKind()) {
                case JAVA:
                    walkByExtension(sp.getPath(), ".java", t.javaFiles);
                    break;
                case AIDL:
                    walkByExtension(sp.getPath(), ".aidl", t.aidlFiles);
                    break;
                case MANIFEST:
                    if (sp.getPath().isFile()) {
                        t.manifestFiles.add(sp.getPath());
                    }
                    break;
                default:
                    break;
            }
        }
        dedupe(t.javaFiles);
        dedupe(t.aidlFiles);
        dedupe(t.manifestFiles);
        return t;
    }

    /** PR5 互換: Java ファイルだけ返す。 */
    static List<File> listJavaFiles(File projectRoot, ErrorListener listener) {
        return listTargets(projectRoot, listener).javaFiles;
    }

    private static void walkByExtension(File dir, String suffix, List<File> sink) {
        if (dir == null) {
            return;
        }
        if (dir.isFile()) {
            if (dir.getName().endsWith(suffix)) {
                sink.add(dir);
            }
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory()) {
                walkByExtension(f, suffix, sink);
            } else if (f.isFile() && f.getName().endsWith(suffix)) {
                sink.add(f);
            }
        }
    }

    /** ルート以下を再帰走査して AndroidManifest.xml を集める (fallback 用)。 */
    private static void walkManifest(File dir, List<File> sink) {
        if (dir == null) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory()) {
                walkManifest(f, sink);
            } else if (f.isFile() && "AndroidManifest.xml".equals(f.getName())) {
                sink.add(f);
            }
        }
    }

    private static void dedupe(List<File> files) {
        Set<File> seen = new LinkedHashSet<>(files);
        files.clear();
        files.addAll(seen);
    }

    // ---- scan + upsert per kind ----

    private static final class ScanCounts {
        int added;
        int modified;
        int unchanged;
        int deleted;
    }

    /** kind 別の diff + 再投入を共通化したヘルパー。 */
    private static ScanCounts scanAndUpsert(IndexDatabase db, File projectRoot, String kind,
            List<File> current, FileIngestor ingestor, ErrorListener listener) throws SQLException {
        ScanCounts c = new ScanCounts();
        IncrementalScanner.DiffResult diff = IncrementalScanner.diff(
                db.connection(), projectRoot, kind, current);
        c.added = diff.getAdded().size();
        c.modified = diff.getModified().size();
        c.unchanged = diff.getUnchanged().size();
        c.deleted = diff.getDeletedPaths().size();

        for (String path : diff.getDeletedPaths()) {
            try {
                FilesDao.delete(db.connection(), path);
            } catch (SQLException ex) {
                listener.onError(path, -1, "failed to delete row: " + ex.getMessage());
            }
        }

        IndexWriter writer = new IndexWriter(db.connection());
        for (File f : diff.getStale()) {
            ingestor.ingest(db, writer, projectRoot, f, listener);
        }
        return c;
    }

    /** 1 ファイル分の取込み戦略 (kind ごとに違う)。 */
    @FunctionalInterface
    interface FileIngestor {
        void ingest(IndexDatabase db, IndexWriter writer, File projectRoot, File source,
                ErrorListener listener) throws SQLException;
    }

    // ---- per-file ingestion strategies ----

    private static void ingestJava(IndexDatabase db, IndexWriter writer, File projectRoot,
            File source, ErrorListener listener) {
        String relPath = IncrementalScanner.relativize(projectRoot, source);
        long mtime = source.lastModified();
        long size = source.length();
        List<JavaClassInfo> classes = Collections.emptyList();
        String parseError = null;
        try {
            String src = AndroidProjectScanner.readFile(source);
            classes = UmlGenerator.extractFromSource(src, source.getName(), listener);
        } catch (IOException ex) {
            parseError = "read failed: " + ex.getMessage();
        } catch (RuntimeException ex) {
            parseError = "parse failed: " + ex.getMessage();
        }
        try {
            writer.upsertFile(relPath, IndexWriter.KIND_JAVA, mtime, size,
                    null, null, classes, parseError);
        } catch (SQLException ex) {
            listener.onError(relPath, -1, "upsert failed: " + ex.getMessage());
        }
    }

    private static void ingestAidl(IndexDatabase db, IndexWriter writer, File projectRoot,
            File source, ErrorListener listener) throws SQLException {
        String relPath = IncrementalScanner.relativize(projectRoot, source);
        long mtime = source.lastModified();
        long size = source.length();
        List<JavaClassInfo> classes = Collections.emptyList();
        String parseError = null;
        try {
            String src = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
            classes = AidlParser.parse(src, listener);
        } catch (IOException ex) {
            parseError = "read failed: " + ex.getMessage();
        } catch (RuntimeException ex) {
            parseError = "parse failed: " + ex.getMessage();
        }
        writer.upsertFile(relPath, IndexWriter.KIND_AIDL, mtime, size,
                null, null, classes, parseError);
        // aidl_interfaces / aidl_methods への詳細投入
        AidlIngestor.ingestInterfaces(db.connection(), classes);
    }

    private static void ingestManifest(IndexDatabase db, IndexWriter writer, File projectRoot,
            File source, ErrorListener listener) throws SQLException {
        String relPath = IncrementalScanner.relativize(projectRoot, source);
        long mtime = source.lastModified();
        long size = source.length();
        AndroidManifestInfo manifest = null;
        String parseError = null;
        try {
            String src = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
            manifest = AndroidManifestParser.parse(src, listener);
        } catch (IOException ex) {
            parseError = "read failed: " + ex.getMessage();
        } catch (RuntimeException ex) {
            parseError = "parse failed: " + ex.getMessage();
        }
        writer.upsertFile(relPath, IndexWriter.KIND_MANIFEST, mtime, size,
                null, null, Collections.emptyList(), parseError);
        if (manifest != null) {
            Long fileId = lookupFileId(db.connection(), relPath);
            ManifestIngestor.ingest(db.connection(), manifest, fileId);
        }
    }

    // ---- post-process aggregation ----

    /**
     * 全 file 取込み完了後に、集約テーブル (components / aidl_bindings /
     * external_endpoints) を作り直す。冪等性確保のため毎回 wipe → 再構築する。
     * 重い処理ではなく、テーブル横断の単純 SELECT + Java 側計算が主。
     */
    private static void runAggregations(IndexDatabase db, File projectRoot,
            ErrorListener listener) throws SQLException {
        wipeAggregates(db.connection());

        IndexReader reader = new IndexReader(db.connection());
        ClassIndex classIndex = reader.loadStageAClassIndex(projectRoot);
        List<JavaClassInfo> allClasses = classIndex.headers();

        // 1) Manifest 由来の境界を external_endpoints に再投入
        for (FileRowInfo info : listManifestFileInfo(db.connection())) {
            AndroidManifestInfo manifest = parseManifestFromDisk(projectRoot, info.path, listener);
            if (manifest != null) {
                EndpointAggregator.ingestManifest(db.connection(), manifest, info.fileId);
            }
        }

        // 2) AIDL binding 解決 + 境界登録
        AidlBindingResolver resolver = new AidlBindingResolver();
        Map<String, List<juml.core.aaos.AidlBinding>> bindings = resolver.resolve(allClasses);
        AidlIngestor.ingestBindings(db.connection(), bindings);
        EndpointAggregator.ingestAidl(db.connection(), allClasses, bindings);

        // 3) ソース継承で Activity/Service/Fragment 検出 → components に追記
        ComponentIngestor.ingest(db.connection(), classIndex);

        // 4) Intent 由来の遷移を external_endpoints に追加
        try {
            IntentNavigationDetector detector = new IntentNavigationDetector();
            List<ScreenTransition> transitions = detector.analyzeProject(projectRoot);
            EndpointAggregator.ingestIntentTransitions(db.connection(), transitions);
        } catch (IOException ex) {
            listener.onError(null, -1,
                    "intent navigation analysis failed: " + ex.getMessage());
        }
    }

    /** 集約テーブルを TRUNCATE (SQLite に TRUNCATE は無いので DELETE)。 */
    private static void wipeAggregates(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM external_endpoints");
            st.executeUpdate("DELETE FROM aidl_bindings");
            // components は SUPERCLASS 由来の行だけ消す (MANIFEST 由来は
            // manifests の CASCADE で別管理。BOTH 行はソース継承部分を取り消し
            // して MANIFEST 由来に戻す)。
            st.executeUpdate("DELETE FROM components WHERE detection_src = 'SUPERCLASS'");
            st.executeUpdate("UPDATE components SET detection_src = 'MANIFEST' "
                    + "WHERE detection_src = 'BOTH'");
        }
    }

    private static final class FileRowInfo {
        final long fileId;
        final String path;

        FileRowInfo(long fileId, String path) {
            this.fileId = fileId;
            this.path = path;
        }
    }

    private static List<FileRowInfo> listManifestFileInfo(Connection conn) throws SQLException {
        List<FileRowInfo> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, path FROM files WHERE kind = ? ORDER BY id")) {
            ps.setString(1, IndexWriter.KIND_MANIFEST);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new FileRowInfo(rs.getLong(1), rs.getString(2)));
                }
            }
        }
        return out;
    }

    private static AndroidManifestInfo parseManifestFromDisk(File projectRoot, String relPath,
            ErrorListener listener) {
        File f;
        try {
            f = new File(projectRoot, relPath).getCanonicalFile();
            if (!f.getPath().startsWith(projectRoot.getCanonicalPath() + File.separator)) {
                listener.onError(relPath, -1, "path traversal rejected: " + relPath);
                return null;
            }
        } catch (IOException ex) {
            listener.onError(relPath, -1, "path resolution failed: " + ex.getMessage());
            return null;
        }
        if (!f.isFile()) {
            return null;
        }
        try {
            String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            return AndroidManifestParser.parse(src, listener);
        } catch (IOException ex) {
            listener.onError(relPath, -1, "manifest re-read failed: " + ex.getMessage());
            return null;
        } catch (RuntimeException ex) {
            listener.onError(relPath, -1, "manifest re-parse failed: " + ex.getMessage());
            return null;
        }
    }

    private static Long lookupFileId(Connection conn, String relPath) throws SQLException {
        FilesDao.FileRow row = FilesDao.findByPath(conn, relPath);
        return row == null ? null : row.id;
    }

    // ---- helpers ----

    private static void ensureParent(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + parent);
        }
    }
}
