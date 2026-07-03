// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一度開いたプロジェクトのパスとプロジェクト固有設定を SQLite で永続化するリポジトリ。
 *
 * <p>DB ファイル: {@code ~/.juml/projects.db}。
 * スキーマを変えたいときは {@link #SCHEMA_VERSION} をインクリメントすれば
 * 次回起動時に自動で再構築される (データは失われる)。</p>
 */
public final class ProjectRepository implements AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final String DB_FILENAME = "projects.db";
    private static final String JDBC_PREFIX = "jdbc:sqlite:";

    private static ProjectRepository instance;

    private final Connection connection;

    private ProjectRepository(Connection connection) {
        this.connection = connection;
    }

    // ---- ライフサイクル ----

    public static ProjectRepository initialize() {
        File dbFile = resolveDbFile();
        try {
            ensureParent(dbFile);
            loadDriver();
            Connection conn = DriverManager.getConnection(JDBC_PREFIX + dbFile.getAbsolutePath());
            applyPragmas(conn);
            ensureSchema(conn);
            instance = new ProjectRepository(conn);
        } catch (SQLException | IOException ex) {
            juml.util.AppLog.error(juml.util.ErrorCode.CFG_002, "ProjectRepository",
                    "SQLite init failed: " + dbFile.getAbsolutePath(), ex);
            instance = new ProjectRepository(null);
        }
        return instance;
    }

    public static ProjectRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ProjectRepository is not initialized");
        }
        return instance;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
    }

    // ---- プロジェクト操作 ----

    /**
     * プロジェクトを登録または last_opened_at を更新する (UPSERT)。
     * 読み込みに成功した直後に呼ぶこと。
     */
    public void touch(File projectRoot) {
        if (connection == null) return;
        String path = canonical(projectRoot);
        String name = projectRoot.getName();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO projects(path, name, last_opened_at, created_at) VALUES(?,?,?,?)"
                + " ON CONFLICT(path) DO UPDATE"
                + "   SET name = excluded.name, last_opened_at = excluded.last_opened_at")) {
            ps.setString(1, path);
            ps.setString(2, name);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (SQLException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_003, "ProjectRepository",
                    "Failed to record opened project", ex);
        }
    }

    /**
     * 最近開いたプロジェクト一覧から指定 id のエントリを削除する。
     * {@code project_settings} は {@code ON DELETE CASCADE} なので連鎖削除される。
     * ディスク上のプロジェクト本体には一切触れない。
     *
     * @return 1 件以上削除できたら {@code true}
     */
    public boolean deleteById(long id) {
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM projects WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_003, "ProjectRepository",
                    "Failed to delete project by id", ex);
            return false;
        }
    }

    /**
     * 最近開いたプロジェクト一覧から指定パスのエントリを削除する。
     * ディスク上のプロジェクト本体には一切触れない。
     *
     * @return 1 件以上削除できたら {@code true}
     */
    public boolean delete(File projectRoot) {
        if (connection == null || projectRoot == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM projects WHERE path = ?")) {
            ps.setString(1, canonical(projectRoot));
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_003, "ProjectRepository",
                    "Failed to delete project by path", ex);
            return false;
        }
    }

    /**
     * プロジェクト固有設定をまとめて保存する。
     * 既存エントリは上書き、不要なキーはそのまま残す (削除しない)。
     */
    public void saveSettings(File projectRoot, Map<String, String> settings) {
        if (connection == null || settings == null || settings.isEmpty()) return;
        Long id = findId(canonical(projectRoot));
        if (id == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO project_settings(project_id, key, value) VALUES(?,?,?)"
                + " ON CONFLICT(project_id, key) DO UPDATE SET value = excluded.value")) {
            for (Map.Entry<String, String> e : settings.entrySet()) {
                ps.setLong(1, id);
                ps.setString(2, e.getKey());
                ps.setString(3, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_003, "ProjectRepository",
                    "Failed to save project settings", ex);
        }
    }

    /**
     * プロジェクト固有設定を全件返す。プロジェクトが未登録なら空マップ。
     */
    public Map<String, String> loadSettings(File projectRoot) {
        Map<String, String> result = new LinkedHashMap<>();
        if (connection == null) return result;
        Long id = findId(canonical(projectRoot));
        if (id == null) return result;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT key, value FROM project_settings WHERE project_id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        } catch (SQLException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_003, "ProjectRepository",
                    "Failed to load project settings", ex);
        }
        return result;
    }

    /**
     * 最近開いたプロジェクトを新しい順に最大 {@code limit} 件返す。
     */
    public List<ProjectRecord> listRecent(int limit) {
        List<ProjectRecord> result = new ArrayList<>();
        if (connection == null) return result;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, path, name, last_opened_at FROM projects"
                + " ORDER BY last_opened_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ProjectRecord(
                            rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getLong(4)));
                }
            }
        } catch (SQLException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_003, "ProjectRepository",
                    "Failed to list recent projects", ex);
        }
        return result;
    }

    // ---- internals ----

    private Long findId(String path) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM projects WHERE path = ?")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    private static void ensureSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT value FROM meta WHERE key = 'schema_version'");
            if (rs.next()) {
                int have = Integer.parseInt(rs.getString(1));
                if (have == SCHEMA_VERSION) return;
            }
        } catch (SQLException ignored) {
            // meta テーブルが無い場合は初回
        }
        createSchema(conn);
    }

    private static void createSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS project_settings");
            st.executeUpdate("DROP TABLE IF EXISTS projects");
            st.executeUpdate("DROP TABLE IF EXISTS meta");
            st.executeUpdate(
                    "CREATE TABLE meta ("
                    + "  key TEXT PRIMARY KEY,"
                    + "  value TEXT NOT NULL"
                    + ")");
            st.executeUpdate(
                    "INSERT INTO meta(key, value) VALUES('schema_version', '"
                    + SCHEMA_VERSION + "')");
            st.executeUpdate(
                    "CREATE TABLE projects ("
                    + "  id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "  path TEXT UNIQUE NOT NULL,"
                    + "  name TEXT NOT NULL,"
                    + "  last_opened_at INTEGER NOT NULL,"
                    + "  created_at INTEGER NOT NULL"
                    + ")");
            st.executeUpdate(
                    "CREATE TABLE project_settings ("
                    + "  project_id INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,"
                    + "  key TEXT NOT NULL,"
                    + "  value TEXT NOT NULL,"
                    + "  PRIMARY KEY (project_id, key)"
                    + ")");
        }
    }

    private static void applyPragmas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA journal_mode = WAL")) {
                rs.next();
            }
            st.executeUpdate("PRAGMA synchronous = NORMAL");
            st.executeUpdate("PRAGMA foreign_keys = ON");
        }
    }

    private static void loadDriver() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("sqlite-jdbc driver not on classpath", ex);
        }
    }

    private static File resolveDbFile() {
        String os = System.getProperty("os.name", "").toLowerCase();
        File base;
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            base = (local != null && !local.isEmpty())
                    ? new File(local, "Juml")
                    : new File(System.getProperty("user.home", "."), ".juml");
        } else {
            base = new File(System.getProperty("user.home", "."), ".juml");
        }
        return new File(base, DB_FILENAME);
    }

    private static void ensureParent(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent);
        }
    }

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException ex) {
            return f.getAbsolutePath();
        }
    }
}
