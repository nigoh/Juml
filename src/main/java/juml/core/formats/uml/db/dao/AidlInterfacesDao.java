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
 * {@code aidl_interfaces} / {@code aidl_methods} の CRUD。
 *
 * <p>1 .aidl interface = 1 {@code aidl_interfaces} 行 + 0+ {@code aidl_methods} 行。
 * 関連する {@code classes} 行 ({@code Kind.AIDL_INTERFACE}) を削除すると
 * {@code aidl_interfaces.class_id} の CASCADE で自動消去される設計。</p>
 */
public final class AidlInterfacesDao {

    /** aidl_interfaces 1 行。 */
    public static final class InterfaceRow {
        public final long id;
        public final Long classId;
        public final String packageName;
        public final String simpleName;

        public InterfaceRow(long id, Long classId, String packageName, String simpleName) {
            this.id = id;
            this.classId = classId;
            this.packageName = packageName;
            this.simpleName = simpleName;
        }
    }

    /** aidl_methods 1 行。 */
    public static final class MethodRow {
        public final long id;
        public final long aidlId;
        public final String name;
        public final boolean oneway;
        public final String returnType;
        public final String paramSig;

        public MethodRow(long id, long aidlId, String name, boolean oneway,
                String returnType, String paramSig) {
            this.id = id;
            this.aidlId = aidlId;
            this.name = name;
            this.oneway = oneway;
            this.returnType = returnType;
            this.paramSig = paramSig;
        }
    }

    private AidlInterfacesDao() {
    }

    /** 1 interface 行を INSERT。新 id を返す。 */
    public static long insertInterface(Connection conn, Long classId,
            String packageName, String simpleName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO aidl_interfaces(class_id, package_name, simple_name) "
                + "VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            if (classId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setLong(1, classId);
            }
            ps.setString(2, packageName == null ? "" : packageName);
            ps.setString(3, simpleName == null ? "" : simpleName);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated id for aidl_interfaces");
    }

    /** メソッド 1 件を INSERT。 */
    public static void insertMethod(Connection conn, long aidlId, String name,
            boolean oneway, String returnType, String paramSig) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO aidl_methods(aidl_id, name, oneway, return_type, param_sig) "
                + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, aidlId);
            ps.setString(2, name == null ? "" : name);
            ps.setInt(3, oneway ? 1 : 0);
            if (returnType == null || returnType.isEmpty()) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, returnType);
            }
            if (paramSig == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, paramSig);
            }
            ps.executeUpdate();
        }
    }

    /** interface 件数。 */
    public static int countInterfaces(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM aidl_interfaces");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 全 interface (id 順)。 */
    public static List<InterfaceRow> listAllInterfaces(Connection conn) throws SQLException {
        List<InterfaceRow> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, class_id, package_name, simple_name "
                + "FROM aidl_interfaces ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long cid = rs.getLong(2);
                Long classId = rs.wasNull() ? null : cid;
                out.add(new InterfaceRow(rs.getLong(1), classId,
                        rs.getString(3), rs.getString(4)));
            }
        }
        return out;
    }

    /** 指定 interface の全 method (id 順)。 */
    public static List<MethodRow> listMethods(Connection conn, long aidlId) throws SQLException {
        List<MethodRow> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, aidl_id, name, oneway, return_type, param_sig "
                + "FROM aidl_methods WHERE aidl_id = ? ORDER BY id")) {
            ps.setLong(1, aidlId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MethodRow(
                            rs.getLong(1), rs.getLong(2),
                            rs.getString(3), rs.getInt(4) != 0,
                            rs.getString(5), rs.getString(6)));
                }
            }
        }
        return out;
    }
}
