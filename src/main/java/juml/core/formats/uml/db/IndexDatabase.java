// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SQLite 解析インデックス DB のライフサイクル管理。
 *
 * <p>機能:</p>
 * <ul>
 *   <li>DB ファイルの open / close (JDBC コネクションの保持)</li>
 *   <li>起動時 PRAGMA (WAL / synchronous=NORMAL / foreign_keys=ON / mmap_size 等) の適用</li>
 *   <li>{@code meta.schema_version} の検査と「壊して作り直し」の実施
 *       (不一致時は {@code index.db} を {@code index.db.discarded-<ts>} へ rename して
 *        新しい空 DB を初期化する)</li>
 * </ul>
 *
 * <p>migration は持たない。スキーマを変えるときは {@link SchemaInitializer#SCHEMA_VERSION}
 * をインクリメントすれば、次回起動時に自動で再構築される。</p>
 */
public final class IndexDatabase implements AutoCloseable {

    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File dbFile;
    private final Connection connection;

    private IndexDatabase(File dbFile, Connection connection) {
        this.dbFile = dbFile;
        this.connection = connection;
    }

    /**
     * 既存 DB を開く (存在しない場合は新規作成して V1 スキーマで初期化)。
     *
     * <p>{@code schema_version} が現行 {@link SchemaInitializer#SCHEMA_VERSION} と
     * 異なる場合は、{@code index.db} を {@code index.db.discarded-<timestamp>} に
     * リネーム退避してから空 DB を作り直す。</p>
     */
    public static IndexDatabase openOrCreate(File dbFile, String projectRoot, String toolVersion)
            throws SQLException, IOException {
        ensureParent(dbFile);
        loadDriver();

        boolean fresh = !dbFile.exists() || dbFile.length() == 0;
        Connection conn = openConnection(dbFile);
        applyPragmas(conn);

        if (fresh) {
            SchemaInitializer.initialize(conn, projectRoot, toolVersion);
            return new IndexDatabase(dbFile, conn);
        }

        Integer have = readSchemaVersion(conn);
        if (have != null && have == SchemaInitializer.SCHEMA_VERSION) {
            return new IndexDatabase(dbFile, conn);
        }

        // 不一致 (have が null = meta テーブル無し、または旧バージョン)。
        // 退避 → 再構築。
        conn.close();
        File discarded = discardOldDb(dbFile, have);
        juml.util.AppLog.info("IndexDatabase", "schema_version mismatch (have="
                + (have == null ? "?" : have) + ", want=" + SchemaInitializer.SCHEMA_VERSION
                + "); discarded old DB to " + discarded.getName() + ", full rescan required");

        Connection fresh2 = openConnection(dbFile);
        applyPragmas(fresh2);
        SchemaInitializer.initialize(fresh2, projectRoot, toolVersion);
        return new IndexDatabase(dbFile, fresh2);
    }

    /** 既存接続を取り出す。{@link AutoCloseable} で close するまで有効。 */
    public Connection connection() {
        return connection;
    }

    public File dbFile() {
        return dbFile;
    }

    /** meta テーブルから値を取得 (無ければ {@code null})。 */
    public String getMeta(String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM meta WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** meta テーブルに UPSERT。 */
    public void putMeta(String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO meta(key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // ---- internals ----

    private static void loadDriver() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("sqlite-jdbc driver not on classpath", ex);
        }
    }

    private static Connection openConnection(File dbFile) throws SQLException {
        String url = JDBC_PREFIX + dbFile.getAbsolutePath();
        return DriverManager.getConnection(url);
    }

    private static void applyPragmas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            // journal_mode は値を返すクエリ。executeQuery で受ける。
            try (ResultSet rs = st.executeQuery("PRAGMA journal_mode = WAL")) {
                rs.next();
            }
            st.executeUpdate("PRAGMA synchronous = NORMAL");
            st.executeUpdate("PRAGMA foreign_keys = ON");
            st.executeUpdate("PRAGMA temp_store = MEMORY");
            st.executeUpdate("PRAGMA mmap_size = 268435456");
            st.executeUpdate("PRAGMA cache_size = -65536");
        }
    }

    private static Integer readSchemaVersion(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT value FROM meta WHERE key = 'schema_version'")) {
            if (rs.next()) {
                try {
                    return Integer.parseInt(rs.getString(1));
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
            return null;
        } catch (SQLException ex) {
            // meta テーブル自体が無い = 旧スキーマ / 壊れた DB
            return null;
        }
    }

    private static File discardOldDb(File dbFile, Integer have) throws IOException {
        String ts = LocalDateTime.now().format(TS_FMT);
        String suffix = have == null ? "unknown" : Integer.toString(have);
        File target = new File(dbFile.getParentFile(),
                dbFile.getName() + ".discarded-v" + suffix + "-" + ts);
        Path src = dbFile.toPath();
        Files.move(src, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        // WAL/SHM サイドファイルも一緒に退避 (存在すれば)
        moveIfExists(new File(dbFile.getAbsolutePath() + "-wal"),
                new File(target.getAbsolutePath() + "-wal"));
        moveIfExists(new File(dbFile.getAbsolutePath() + "-shm"),
                new File(target.getAbsolutePath() + "-shm"));
        return target;
    }

    private static void moveIfExists(File src, File dst) throws IOException {
        if (src.exists()) {
            Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void ensureParent(File dbFile) throws IOException {
        File parent = dbFile.getParentFile();
        if (parent == null || parent.exists()) {
            return;
        }
        Path parentPath = parent.toPath();
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
            Files.createDirectories(parentPath,
                    PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(parentPath);
        }
    }
}
