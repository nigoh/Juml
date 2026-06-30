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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code components} テーブル CRUD。
 *
 * <p>Manifest 由来 (MANIFEST) とソース継承由来 (SUPERCLASS) を統合する集約テーブル。
 * 「Activity 一覧」「Fragment 一覧」のような集計クエリ用。
 * UNIQUE 制約は {@code (comp_type, class_qn)}。同じクラスを Manifest からも
 * super 経由でも検出した場合、{@link #upsert} は {@code detection_src = 'BOTH'} に
 * 昇格する。</p>
 */
public final class ComponentsDao {

    /** 検出源。 */
    public static final String SRC_MANIFEST = "MANIFEST";
    public static final String SRC_SUPERCLASS = "SUPERCLASS";
    public static final String SRC_BOTH = "BOTH";

    /** 1 行。 */
    public static final class Row {
        public final long id;
        public final String compType;
        public final String classQn;
        public final String detectionSrc;
        public final Long manifestId;
        public final Boolean exported;
        public final String permission;
        public final Boolean enabled;

        public Row(long id, String compType, String classQn, String detectionSrc,
                Long manifestId, Boolean exported, String permission, Boolean enabled) {
            this.id = id;
            this.compType = compType;
            this.classQn = classQn;
            this.detectionSrc = detectionSrc;
            this.manifestId = manifestId;
            this.exported = exported;
            this.permission = permission;
            this.enabled = enabled;
        }
    }

    private ComponentsDao() {
    }

    /**
     * UPSERT。{@code (comp_type, class_qn)} で既存なら detection_src を統合する。
     *
     * <p>新規 INSERT 時は与えられた {@code detectionSrc} をそのまま使う。
     * 既存行があり、保存済み {@code detection_src} と新しい {@code detectionSrc} が
     * 異なれば {@code BOTH} に昇格させる。Manifest 由来の属性
     * (manifest_id / exported / permission / enabled) は与えられた値で
     * 上書きする (null は上書きしない)。</p>
     *
     * @return 行の id
     */
    public static long upsert(Connection conn, String compType, String classQn,
            String detectionSrc, Long manifestId, Boolean exported,
            String permission, Boolean enabled) throws SQLException {
        Row existing = findByTypeAndClass(conn, compType, classQn);
        if (existing == null) {
            return insert(conn, compType, classQn, detectionSrc, manifestId,
                    exported, permission, enabled);
        }
        String mergedSrc = mergeSrc(existing.detectionSrc, detectionSrc);
        update(conn, existing.id, mergedSrc,
                manifestId != null ? manifestId : existing.manifestId,
                exported != null ? exported : existing.exported,
                permission != null ? permission : existing.permission,
                enabled != null ? enabled : existing.enabled);
        return existing.id;
    }

    /** 件数。 */
    public static int count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM components");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** {@code comp_type} 別の件数 (例: ACTIVITY/SERVICE/FRAGMENT 等)。 */
    public static Map<String, Integer> countByType(Connection conn) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT comp_type, COUNT(*) FROM components GROUP BY comp_type ORDER BY comp_type");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getInt(2));
            }
        }
        return out;
    }

    /** {@code comp_type} 指定で {@code class_qn} 一覧を昇順で取得。 */
    public static List<String> listClassQnsByType(Connection conn, String compType)
            throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT class_qn FROM components WHERE comp_type = ? ORDER BY class_qn")) {
            ps.setString(1, compType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** {@code (comp_type, class_qn)} で 1 件取得 (なければ null)。 */
    public static Row findByTypeAndClass(Connection conn, String compType, String classQn)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, comp_type, class_qn, detection_src, manifest_id, "
                + "exported, permission, enabled "
                + "FROM components WHERE comp_type = ? AND class_qn = ?")) {
            ps.setString(1, compType);
            ps.setString(2, classQn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? toRow(rs) : null;
            }
        }
    }

    /** {@code comp_type} 指定の全行 (class_qn 昇順)。 */
    public static List<Row> listByType(Connection conn, String compType) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, comp_type, class_qn, detection_src, manifest_id, "
                + "exported, permission, enabled "
                + "FROM components WHERE comp_type = ? ORDER BY class_qn")) {
            ps.setString(1, compType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(toRow(rs));
                }
            }
        }
        return out;
    }

    // ---- internals ----

    private static long insert(Connection conn, String compType, String classQn,
            String detectionSrc, Long manifestId, Boolean exported,
            String permission, Boolean enabled) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO components("
                + "comp_type, class_qn, detection_src, manifest_id, "
                + "exported, permission, enabled) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, compType);
            ps.setString(2, classQn);
            ps.setString(3, detectionSrc);
            if (manifestId == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setLong(4, manifestId);
            }
            setNullableBool(ps, 5, exported);
            if (permission == null || permission.isEmpty()) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, permission);
            }
            setNullableBool(ps, 7, enabled);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated id for component "
                + compType + ":" + classQn);
    }

    private static void update(Connection conn, long id, String detectionSrc,
            Long manifestId, Boolean exported, String permission, Boolean enabled)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE components SET detection_src = ?, manifest_id = ?, "
                + "exported = ?, permission = ?, enabled = ? WHERE id = ?")) {
            ps.setString(1, detectionSrc);
            if (manifestId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setLong(2, manifestId);
            }
            setNullableBool(ps, 3, exported);
            if (permission == null || permission.isEmpty()) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, permission);
            }
            setNullableBool(ps, 5, enabled);
            ps.setLong(6, id);
            ps.executeUpdate();
        }
    }

    private static String mergeSrc(String existing, String newSrc) {
        if (existing == null || existing.isEmpty()) {
            return newSrc;
        }
        if (newSrc == null || newSrc.isEmpty() || existing.equals(newSrc)) {
            return existing;
        }
        return SRC_BOTH;
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        long mid = rs.getLong(5);
        Long manifestId = rs.wasNull() ? null : mid;
        int exp = rs.getInt(6);
        Boolean exported = rs.wasNull() ? null : exp != 0;
        int ena = rs.getInt(8);
        Boolean enabled = rs.wasNull() ? null : ena != 0;
        return new Row(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                manifestId,
                exported,
                rs.getString(7),
                enabled
        );
    }

    private static void setNullableBool(PreparedStatement ps, int idx, Boolean value)
            throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value ? 1 : 0);
        }
    }
}
