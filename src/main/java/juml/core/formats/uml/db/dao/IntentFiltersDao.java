// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import juml.core.formats.uml.db.CsvCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code intent_filters} テーブル CRUD。
 *
 * <p>1 filter = 1 行。{@code actions} / {@code categories} / {@code data_schemes}
 * / {@code data_mime_types} は CSV 連結で保持する ({@link CsvCodec})。
 * 「BOOT_COMPLETED で起動される全 Receiver」のような検索は
 * {@code actions LIKE '%BOOT_COMPLETED%'} で行う想定。</p>
 */
public final class IntentFiltersDao {

    /** 1 行 (decode 済み)。 */
    public static final class Row {
        public final long id;
        public final long componentId;
        public final int filterIndex;
        public final List<String> actions;
        public final List<String> categories;
        public final List<String> dataSchemes;
        public final List<String> dataMimeTypes;

        public Row(long id, long componentId, int filterIndex,
                List<String> actions, List<String> categories,
                List<String> dataSchemes, List<String> dataMimeTypes) {
            this.id = id;
            this.componentId = componentId;
            this.filterIndex = filterIndex;
            this.actions = actions;
            this.categories = categories;
            this.dataSchemes = dataSchemes;
            this.dataMimeTypes = dataMimeTypes;
        }
    }

    private IntentFiltersDao() {
    }

    /** 1 件 INSERT。 */
    public static void insert(Connection conn, long componentId, int filterIndex,
            List<String> actions, List<String> categories,
            List<String> dataSchemes, List<String> dataMimeTypes) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO intent_filters("
                + "component_id, filter_index, actions, categories, "
                + "data_schemes, data_mime_types) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, componentId);
            ps.setInt(2, filterIndex);
            setCsv(ps, 3, actions);
            setCsv(ps, 4, categories);
            setCsv(ps, 5, dataSchemes);
            setCsv(ps, 6, dataMimeTypes);
            ps.executeUpdate();
        }
    }

    /** {@code component_id} 指定の全 filter を filter_index 順に取得。 */
    public static List<Row> listByComponent(Connection conn, long componentId) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, component_id, filter_index, actions, categories, "
                + "data_schemes, data_mime_types "
                + "FROM intent_filters WHERE component_id = ? ORDER BY filter_index")) {
            ps.setLong(1, componentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(toRow(rs));
                }
            }
        }
        return out;
    }

    /** 件数。 */
    public static int count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM intent_filters");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Action 文字列を含む filter を持つ component_id 一覧。 */
    public static List<Long> componentIdsWithAction(Connection conn, String action)
            throws SQLException {
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT component_id FROM intent_filters "
                + "WHERE actions = ? OR actions LIKE ? OR actions LIKE ? OR actions LIKE ?")) {
            String escaped = action.replace("\\", "\\\\").replace(",", "\\,");
            ps.setString(1, escaped);
            ps.setString(2, escaped + ",%");
            ps.setString(3, "%," + escaped + ",%");
            ps.setString(4, "%," + escaped);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getLong(1));
                }
            }
        }
        return out;
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        return new Row(
                rs.getLong(1),
                rs.getLong(2),
                rs.getInt(3),
                decodeCsv(rs.getString(4)),
                decodeCsv(rs.getString(5)),
                decodeCsv(rs.getString(6)),
                decodeCsv(rs.getString(7))
        );
    }

    private static void setCsv(PreparedStatement ps, int idx, List<String> values) throws SQLException {
        String s = CsvCodec.join(values);
        if (s == null || s.isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, s);
        }
    }

    private static List<String> decodeCsv(String s) {
        if (s == null || s.isEmpty()) {
            return Collections.emptyList();
        }
        return CsvCodec.split(s);
    }
}
