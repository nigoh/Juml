// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code manifests} テーブル CRUD。1 ファイル = 1 行。
 *
 * <p>{@code file_id} は {@code files} 行への UNIQUE 参照。Manifest ファイルが
 * 再投入されると {@code FilesDao.replace} で旧行が CASCADE 削除され、
 * manifests / components / intent_filters も一斉に消える設計。</p>
 */
public final class ManifestsDao {

    /** manifests 行。 */
    public static final class Row {
        public final long id;
        public final Long fileId;
        public final String packageName;
        public final String sourceSet;
        public final Integer minSdk;
        public final Integer targetSdk;
        public final Integer maxSdk;
        public final String applicationClass;

        public Row(long id, Long fileId, String packageName, String sourceSet,
                Integer minSdk, Integer targetSdk, Integer maxSdk, String applicationClass) {
            this.id = id;
            this.fileId = fileId;
            this.packageName = packageName;
            this.sourceSet = sourceSet;
            this.minSdk = minSdk;
            this.targetSdk = targetSdk;
            this.maxSdk = maxSdk;
            this.applicationClass = applicationClass;
        }
    }

    private ManifestsDao() {
    }

    /** 1 行 INSERT。新 id を返す。 */
    public static long insert(Connection conn, Long fileId, String packageName,
            String sourceSet, Integer minSdk, Integer targetSdk, Integer maxSdk,
            String applicationClass) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO manifests("
                + "file_id, package_name, source_set, min_sdk, target_sdk, max_sdk, application_class) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            if (fileId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setLong(1, fileId);
            }
            ps.setString(2, packageName == null ? "" : packageName);
            ps.setString(3, sourceSet == null ? "main" : sourceSet);
            setNullableInt(ps, 4, minSdk);
            setNullableInt(ps, 5, targetSdk);
            setNullableInt(ps, 6, maxSdk);
            if (applicationClass == null || applicationClass.isEmpty()) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, applicationClass);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated id for manifest");
    }

    /** 全 manifests 行を id 順に取得。 */
    public static List<Row> listAll(Connection conn) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, file_id, package_name, source_set, "
                + "min_sdk, target_sdk, max_sdk, application_class "
                + "FROM manifests ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(toRow(rs));
            }
        }
        return out;
    }

    /** {@code file_id} で 1 件取得 (なければ null)。 */
    public static Row findByFileId(Connection conn, long fileId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, file_id, package_name, source_set, "
                + "min_sdk, target_sdk, max_sdk, application_class "
                + "FROM manifests WHERE file_id = ?")) {
            ps.setLong(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toRow(rs) : null;
            }
        }
    }

    /** 件数。 */
    public static int count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM manifests");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        long fid = rs.getLong(2);
        Long fileId = rs.wasNull() ? null : fid;
        return new Row(
                rs.getLong(1),
                fileId,
                rs.getString(3),
                rs.getString(4),
                getNullableInt(rs, 5),
                getNullableInt(rs, 6),
                getNullableInt(rs, 7),
                rs.getString(8)
        );
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static Integer getNullableInt(ResultSet rs, int idx) throws SQLException {
        int v = rs.getInt(idx);
        return rs.wasNull() ? null : v;
    }
}
