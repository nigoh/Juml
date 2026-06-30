// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code files} テーブル CRUD。
 *
 * <p>{@code files} は ".java / .aidl / AndroidManifest.xml / build.gradle ..." を
 * 一様に扱う入力ファイル台帳。classes / refs / manifests など下流テーブルの
 * {@code file_id} を {@code ON DELETE CASCADE} で連鎖削除するため、
 * 「同じ path の行をいったん削除 → 新しい行を INSERT」で原子的に差し替える
 * パターンで使う ({@link #replace})。</p>
 */
public final class FilesDao {

    /** files テーブルの 1 行。 */
    public static final class FileRow {
        public final long id;
        public final String path;
        public final Long moduleId;
        public final String kind;
        public final long mtime;
        public final long size;
        public final long parsedAt;
        public final String parseError;

        public FileRow(long id, String path, Long moduleId, String kind,
                long mtime, long size, long parsedAt, String parseError) {
            this.id = id;
            this.path = path;
            this.moduleId = moduleId;
            this.kind = kind;
            this.mtime = mtime;
            this.size = size;
            this.parsedAt = parsedAt;
            this.parseError = parseError;
        }
    }

    private FilesDao() {
    }

    /**
     * 同じ path の既存行を削除して新規行を INSERT し、新しい id を返す。
     *
     * <p>子テーブル ({@code classes} / {@code refs} / ...) は {@code ON DELETE CASCADE}
     * で一括除去される。呼び出し側は新しい id に対して子行を INSERT し直すだけでよい。</p>
     */
    public static long replace(Connection conn, String path, Long moduleId, String kind,
            long mtime, long size, String parseError) throws SQLException {
        delete(conn, path);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO files(path, module_id, kind, mtime, size, parsed_at, parse_error) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, path);
            if (moduleId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setLong(2, moduleId);
            }
            ps.setString(3, kind);
            ps.setLong(4, mtime);
            ps.setLong(5, size);
            ps.setLong(6, System.currentTimeMillis());
            if (parseError == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, parseError);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated id for file " + path);
    }

    /** path で 1 行削除。存在しなければ no-op。 */
    public static void delete(Connection conn, String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM files WHERE path = ?")) {
            ps.setString(1, path);
            ps.executeUpdate();
        }
    }

    /** path で 1 行検索 (なければ null)。 */
    public static FileRow findByPath(Connection conn, String path) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, path, module_id, kind, mtime, size, parsed_at, parse_error "
                + "FROM files WHERE path = ?")) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toRow(rs) : null;
            }
        }
    }

    /** kind 別の (path → row) 一覧 (登録順)。差分検出で使う。 */
    public static Map<String, FileRow> listByKind(Connection conn, String kind) throws SQLException {
        Map<String, FileRow> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, path, module_id, kind, mtime, size, parsed_at, parse_error "
                + "FROM files WHERE kind = ? ORDER BY id")) {
            ps.setString(1, kind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FileRow row = toRow(rs);
                    out.put(row.path, row);
                }
            }
        }
        return out;
    }

    private static FileRow toRow(ResultSet rs) throws SQLException {
        long mid = rs.getLong(3);
        Long moduleId = rs.wasNull() ? null : mid;
        return new FileRow(
                rs.getLong(1),
                rs.getString(2),
                moduleId,
                rs.getString(4),
                rs.getLong(5),
                rs.getLong(6),
                rs.getLong(7),
                rs.getString(8)
        );
    }
}
