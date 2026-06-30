// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code external_endpoints} テーブル CRUD。
 *
 * <p>Intent / Manifest / AIDL の各経路から検出された「外部境界」を 1 テーブルに
 * 統合する。レコードは {@code source_kind} で出所を区別し、
 * {@code from_qn} (発火側) → {@code to_qn} (到達側) の有向辺として扱える。
 * PR7 で予定している「外部境界俯瞰図」のソースになる。</p>
 */
public final class EndpointsDao {

    /** source_kind の予約値。 */
    public static final String KIND_MANIFEST_ACTIVITY = "MANIFEST_ACTIVITY";
    public static final String KIND_MANIFEST_SERVICE = "MANIFEST_SERVICE";
    public static final String KIND_MANIFEST_RECEIVER = "MANIFEST_RECEIVER";
    public static final String KIND_MANIFEST_PROVIDER = "MANIFEST_PROVIDER";
    public static final String KIND_INTENT_START_ACTIVITY = "INTENT_START_ACTIVITY";
    public static final String KIND_INTENT_START_FOR_RESULT = "INTENT_START_FOR_RESULT";
    public static final String KIND_INTENT_SET_CLASS = "INTENT_SET_CLASS";
    public static final String KIND_INTENT_OTHER = "INTENT_OTHER";
    public static final String KIND_AIDL_INTERFACE = "AIDL_INTERFACE";
    public static final String KIND_AIDL_BINDING = "AIDL_BINDING";

    /** 1 行。 */
    public static final class Row {
        public final long id;
        public final String sourceKind;
        public final String fromQn;
        public final String fromMethod;
        public final String toQn;
        public final String attributes;
        public final Long fileId;
        public final int lineHint;

        public Row(long id, String sourceKind, String fromQn, String fromMethod,
                String toQn, String attributes, Long fileId, int lineHint) {
            this.id = id;
            this.sourceKind = sourceKind;
            this.fromQn = fromQn;
            this.fromMethod = fromMethod;
            this.toQn = toQn;
            this.attributes = attributes;
            this.fileId = fileId;
            this.lineHint = lineHint;
        }
    }

    private EndpointsDao() {
    }

    /** 1 件 INSERT。 */
    public static void insert(Connection conn, String sourceKind, String fromQn,
            String fromMethod, String toQn, String attributes, Long fileId,
            int lineHint) throws SQLException {
        if (toQn == null || toQn.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO external_endpoints("
                + "source_kind, from_qn, from_method, to_qn, attributes, file_id, line_hint) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, sourceKind);
            setNullable(ps, 2, fromQn);
            setNullable(ps, 3, fromMethod);
            ps.setString(4, toQn);
            setNullable(ps, 5, attributes);
            if (fileId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setLong(6, fileId);
            }
            ps.setInt(7, lineHint);
            ps.executeUpdate();
        }
    }

    /** 件数。 */
    public static int count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM external_endpoints");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** source_kind 別の件数。 */
    public static Map<String, Integer> countByKind(Connection conn) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT source_kind, COUNT(*) FROM external_endpoints "
                + "GROUP BY source_kind ORDER BY source_kind");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getInt(2));
            }
        }
        return out;
    }

    /** 全行を id 順で取得 (PlantUmlExternalBoundaryDiagram 用)。 */
    public static List<Row> listAll(Connection conn) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, source_kind, from_qn, from_method, to_qn, attributes, "
                + "file_id, line_hint FROM external_endpoints ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(toRow(rs));
            }
        }
        return out;
    }

    /** {@code to_qn} を指定して受信側 endpoint を取得 (変更影響分析用)。 */
    public static List<Row> listIncoming(Connection conn, String toQn) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, source_kind, from_qn, from_method, to_qn, attributes, "
                + "file_id, line_hint FROM external_endpoints "
                + "WHERE to_qn = ? ORDER BY id")) {
            ps.setString(1, toQn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(toRow(rs));
                }
            }
        }
        return out;
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        long fid = rs.getLong(7);
        Long fileId = rs.wasNull() ? null : fid;
        return new Row(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                fileId,
                rs.getInt(8)
        );
    }

    private static void setNullable(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }
}
