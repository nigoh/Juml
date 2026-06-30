// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@code modules} テーブル CRUD。Gradle 由来のモジュール ":app" 等を保持する。 */
public final class ModulesDao {

    private ModulesDao() {
    }

    /**
     * モジュール名に対応する id を返す。存在しなければ INSERT する。
     *
     * @param name モジュール名 (例: ":app")
     * @param path プロジェクトルートからの相対ディレクトリ
     */
    public static long upsert(Connection conn, String name, String path) throws SQLException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("module name must not be empty");
        }
        Long existing = findId(conn, name);
        if (existing != null) {
            return existing;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO modules(name, path) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, path == null ? "" : path);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated id for module " + name);
    }

    /** 名前で id を引く (なければ null)。 */
    public static Long findId(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM modules WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** 全モジュールを {@code (name → path)} で取得 (登録順)。 */
    public static Map<String, String> listAll(Connection conn) throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, path FROM modules ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        }
        return out;
    }
}
