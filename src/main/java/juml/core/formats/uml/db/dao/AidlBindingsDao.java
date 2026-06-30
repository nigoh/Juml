// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code aidl_bindings} テーブル CRUD。
 *
 * <p>1 行 = (AIDL interface FQN, それを実装する Stub の FQN)。
 * 同じ AIDL を複数のクラスが実装するケース (multi-process 等) は
 * {@code UNIQUE(aidl_qn, impl_qn)} で許容される。</p>
 */
public final class AidlBindingsDao {

    /** 1 行。 */
    public static final class Row {
        public final long id;
        public final String aidlQn;
        public final String implQn;

        public Row(long id, String aidlQn, String implQn) {
            this.id = id;
            this.aidlQn = aidlQn;
            this.implQn = implQn;
        }
    }

    private AidlBindingsDao() {
    }

    /** INSERT (重複時は無視)。 */
    public static void upsert(Connection conn, String aidlQn, String implQn) throws SQLException {
        if (aidlQn == null || aidlQn.isEmpty() || implQn == null || implQn.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO aidl_bindings(aidl_qn, impl_qn) VALUES (?, ?)")) {
            ps.setString(1, aidlQn);
            ps.setString(2, implQn);
            ps.executeUpdate();
        }
    }

    /** 件数。 */
    public static int count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM aidl_bindings");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 指定 AIDL を実装するクラス一覧。 */
    public static List<String> implementersOf(Connection conn, String aidlQn) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT impl_qn FROM aidl_bindings WHERE aidl_qn = ? ORDER BY impl_qn")) {
            ps.setString(1, aidlQn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** 指定実装クラスが実装している AIDL 一覧 (通常 1 件)。 */
    public static List<String> aidlsOf(Connection conn, String implQn) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT aidl_qn FROM aidl_bindings WHERE impl_qn = ? ORDER BY aidl_qn")) {
            ps.setString(1, implQn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** 全 binding を id 順に取得。 */
    public static List<Row> listAll(Connection conn) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, aidl_qn, impl_qn FROM aidl_bindings ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Row(rs.getLong(1), rs.getString(2), rs.getString(3)));
            }
        }
        return out;
    }
}
