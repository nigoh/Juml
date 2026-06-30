// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.CsvCodec;

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
 * {@code classes} / {@code class_interfaces} / {@code class_imports} の CRUD と
 * {@link JavaClassInfo} (ヘッダ) との相互変換。
 *
 * <p>本 DAO はクラスの「宣言情報」だけを扱う。fields / methods は
 * {@link MembersDao}。メソッド本体 (statement ツリー) は永続化しない
 * (Stage B はソース再パースで再現する設計)。</p>
 */
public final class ClassesDao {

    private ClassesDao() {
    }

    /** {@link JavaClassInfo} を classes/class_interfaces/class_imports に INSERT。新 id を返す。 */
    public static long insert(Connection conn, JavaClassInfo info, Long fileId) throws SQLException {
        long classId;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO classes("
                + "qn, simple_name, package_name, kind, enclosing, super_class, "
                + "modifiers, annotations, aaos_category, android_comp, jetpack_stereo, "
                + "origin, jar_path, detailed, comment, file_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, info.getQualifiedName());
            ps.setString(2, info.getSimpleName());
            ps.setString(3, info.getPackageName() == null ? "" : info.getPackageName());
            ps.setString(4, info.getKind() != null ? info.getKind().name() : "CLASS");
            setNullable(ps, 5, emptyToNull(info.getEnclosingClass()));
            setNullable(ps, 6, info.getSuperClass());
            ps.setString(7, CsvCodec.join(info.getModifiers()));
            ps.setString(8, CsvCodec.join(info.getAnnotations()));
            setNullable(ps, 9, info.getAaosCategory());
            setNullable(ps, 10, info.getAndroidComponentType());
            ps.setString(11, CsvCodec.join(info.getJetpackStereotypes()));
            ps.setString(12, info.getOrigin() != null ? info.getOrigin().name() : "SOURCE");
            setNullable(ps, 13, info.getJarPath());
            ps.setInt(14, info.isDetailed() ? 1 : 0);
            setNullable(ps, 15, info.getComment());
            if (fileId == null) {
                ps.setNull(16, Types.INTEGER);
            } else {
                ps.setLong(16, fileId);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to retrieve generated id for class "
                            + info.getQualifiedName());
                }
                classId = keys.getLong(1);
            }
        }
        insertInterfaces(conn, classId, info.getInterfaces());
        insertImports(conn, classId, info.getImports());
        return classId;
    }

    /** classes の全 id を取得 (qn 順)。 */
    public static List<Long> listAllIds(Connection conn) throws SQLException {
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM classes ORDER BY qn");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getLong(1));
            }
        }
        return out;
    }

    /** 全クラスを {@link JavaClassInfo} (ヘッダ部) として復元。並び順は qn 昇順。 */
    public static List<JavaClassInfo> loadAllHeaders(Connection conn) throws SQLException {
        List<Row> rows = loadRows(conn,
                "SELECT id, qn, simple_name, package_name, kind, enclosing, super_class, "
                + "modifiers, annotations, aaos_category, android_comp, jetpack_stereo, "
                + "origin, jar_path, detailed, comment, file_id "
                + "FROM classes ORDER BY qn");
        List<JavaClassInfo> out = new ArrayList<>(rows.size());
        Map<Long, JavaClassInfo> byId = new LinkedHashMap<>();
        for (Row r : rows) {
            JavaClassInfo info = toJavaClassInfo(r);
            out.add(info);
            byId.put(r.id, info);
        }
        attachInterfaces(conn, byId);
        attachImports(conn, byId);
        return out;
    }

    /** 1 件取得 (なければ null)。 */
    public static JavaClassInfo loadHeader(Connection conn, String qn) throws SQLException {
        Row r;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, qn, simple_name, package_name, kind, enclosing, super_class, "
                + "modifiers, annotations, aaos_category, android_comp, jetpack_stereo, "
                + "origin, jar_path, detailed, comment, file_id "
                + "FROM classes WHERE qn = ?")) {
            ps.setString(1, qn);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                r = toRow(rs);
            }
        }
        JavaClassInfo info = toJavaClassInfo(r);
        Map<Long, JavaClassInfo> single = new LinkedHashMap<>();
        single.put(r.id, info);
        attachInterfaces(conn, single);
        attachImports(conn, single);
        return info;
    }

    /** {@code (qn → file_id)} のマップ。ソースファイル復元に使う。 */
    public static Map<String, Long> qnToFileId(Connection conn) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT qn, file_id FROM classes WHERE file_id IS NOT NULL ORDER BY qn");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
        }
        return out;
    }

    /** {@code (qn → module_id)} のマップ。 */
    public static Map<String, Long> qnToModuleId(Connection conn) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT c.qn, f.module_id FROM classes c "
                + "JOIN files f ON c.file_id = f.id "
                + "WHERE f.module_id IS NOT NULL ORDER BY c.qn");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
        }
        return out;
    }

    // ---- private helpers ----

    private static void insertInterfaces(Connection conn, long classId, List<String> interfaces)
            throws SQLException {
        if (interfaces == null || interfaces.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO class_interfaces(class_id, iface_qn) VALUES (?, ?)")) {
            for (String iface : interfaces) {
                if (iface == null || iface.isEmpty()) {
                    continue;
                }
                ps.setLong(1, classId);
                ps.setString(2, iface);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertImports(Connection conn, long classId, List<String> imports)
            throws SQLException {
        if (imports == null || imports.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO class_imports(class_id, imp, is_static) "
                + "VALUES (?, ?, ?)")) {
            for (String imp : imports) {
                if (imp == null || imp.isEmpty()) {
                    continue;
                }
                ps.setLong(1, classId);
                ps.setString(2, imp);
                ps.setInt(3, imp.startsWith("static ") ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void attachInterfaces(Connection conn, Map<Long, JavaClassInfo> byId)
            throws SQLException {
        if (byId.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT class_id, iface_qn FROM class_interfaces "
                + "WHERE class_id IN (" + placeholders(byId.size()) + ") "
                + "ORDER BY class_id, iface_qn")) {
            int i = 1;
            for (Long id : byId.keySet()) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JavaClassInfo c = byId.get(rs.getLong(1));
                    if (c != null) {
                        c.getInterfaces().add(rs.getString(2));
                    }
                }
            }
        }
    }

    private static void attachImports(Connection conn, Map<Long, JavaClassInfo> byId)
            throws SQLException {
        if (byId.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT class_id, imp FROM class_imports "
                + "WHERE class_id IN (" + placeholders(byId.size()) + ") "
                + "ORDER BY class_id, imp")) {
            int i = 1;
            for (Long id : byId.keySet()) {
                ps.setLong(i++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JavaClassInfo c = byId.get(rs.getLong(1));
                    if (c != null) {
                        c.getImports().add(rs.getString(2));
                    }
                }
            }
        }
    }

    private static List<Row> loadRows(Connection conn, String sql) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(toRow(rs));
            }
        }
        return out;
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        Row r = new Row();
        r.id = rs.getLong(1);
        r.qn = rs.getString(2);
        r.simpleName = rs.getString(3);
        r.packageName = rs.getString(4);
        r.kind = rs.getString(5);
        r.enclosing = rs.getString(6);
        r.superClass = rs.getString(7);
        r.modifiers = rs.getString(8);
        r.annotations = rs.getString(9);
        r.aaosCategory = rs.getString(10);
        r.androidComp = rs.getString(11);
        r.jetpackStereo = rs.getString(12);
        r.origin = rs.getString(13);
        r.jarPath = rs.getString(14);
        r.detailed = rs.getInt(15) != 0;
        r.comment = rs.getString(16);
        long fid = rs.getLong(17);
        r.fileId = rs.wasNull() ? null : fid;
        return r;
    }

    private static JavaClassInfo toJavaClassInfo(Row r) {
        JavaClassInfo info = new JavaClassInfo();
        info.setSimpleName(r.simpleName);
        info.setPackageName(r.packageName);
        try {
            info.setKind(JavaClassInfo.Kind.valueOf(r.kind));
        } catch (IllegalArgumentException ex) {
            info.setKind(JavaClassInfo.Kind.CLASS);
        }
        info.setEnclosingClass(emptyToNull(r.enclosing));
        info.setSuperClass(emptyToNull(r.superClass));
        for (String m : CsvCodec.split(r.modifiers)) {
            info.getModifiers().add(m);
        }
        for (String a : CsvCodec.split(r.annotations)) {
            info.getAnnotations().add(a);
        }
        info.setAaosCategory(emptyToNull(r.aaosCategory));
        info.setAndroidComponentType(emptyToNull(r.androidComp));
        for (String s : CsvCodec.split(r.jetpackStereo)) {
            info.getJetpackStereotypes().add(s);
        }
        try {
            info.setOrigin(JavaClassInfo.Origin.valueOf(r.origin));
        } catch (IllegalArgumentException ex) {
            info.setOrigin(JavaClassInfo.Origin.SOURCE);
        }
        info.setJarPath(emptyToNull(r.jarPath));
        // classes テーブルは Stage A 情報 (ヘッダ + import + interface) しか持たない。
        // 復元側は必ず Stage A 扱いとし、Stage B 詳細が要る場合は呼び出し側で
        // ClassIndex#detail(qn, ...) によるソース再パースを行う。
        info.setDetailed(false);
        info.setComment(emptyToNull(r.comment));
        return info;
    }

    private static void setNullable(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    /** classes 行の生データ。 */
    private static final class Row {
        long id;
        String qn;
        String simpleName;
        String packageName;
        String kind;
        String enclosing;
        String superClass;
        String modifiers;
        String annotations;
        String aaosCategory;
        String androidComp;
        String jetpackStereo;
        String origin;
        String jarPath;
        boolean detailed;
        String comment;
        Long fileId;
    }
}
