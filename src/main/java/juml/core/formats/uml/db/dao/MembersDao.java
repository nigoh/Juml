// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;
import juml.core.formats.uml.db.CsvCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code fields} / {@code methods} の CRUD と {@link JavaFieldInfo} /
 * {@link JavaMethodInfo} との相互変換。
 *
 * <p>メソッド本体 (statement ツリー / bodyComments) は永続化しない。
 * Stage B の詳細はソースを再パースして取得する設計のため、DB には
 * シグネチャと修飾子・アノテーション・コメントまでに留める。</p>
 *
 * <p>{@code isStatic} / {@code isFinal} / {@code isConstructor} のような
 * boolean フラグは {@code modifiers} カラムに {@code "static,final"} 形式で
 * 積み、復元時にフラグへ戻す ({@link JavaClassInfo} の {@code modifiers}
 * リストと同居しても順序が安定するよう、フラグ系を先頭に置く)。</p>
 */
public final class MembersDao {

    /** {@code modifiers} カラムに boolean を畳み込む際に使う予約名。 */
    private static final String FLAG_STATIC = "static";
    private static final String FLAG_FINAL = "final";
    private static final String FLAG_CTOR = "<ctor>";

    private MembersDao() {
    }

    /** クラスに紐付くフィールド群を INSERT (バッチ)。 */
    public static void insertFields(Connection conn, long classId, List<JavaFieldInfo> fields)
            throws SQLException {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO fields(class_id, name, type, visibility, modifiers, annotations) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            for (JavaFieldInfo f : fields) {
                ps.setLong(1, classId);
                ps.setString(2, nullSafe(f.getName()));
                setNullable(ps, 3, f.getType());
                ps.setString(4, f.getVisibility() != null ? f.getVisibility().name() : "PACKAGE");
                ps.setString(5, encodeFieldModifiers(f));
                ps.setString(6, CsvCodec.join(f.getAnnotations()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** クラスに紐付くメソッド群を INSERT (バッチ)。 */
    public static void insertMethods(Connection conn, long classId, List<JavaMethodInfo> methods)
            throws SQLException {
        if (methods == null || methods.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO methods("
                + "class_id, name, return_type, visibility, modifiers, annotations, "
                + "param_types, param_names, throws_types, is_abstract, comment) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (JavaMethodInfo m : methods) {
                ps.setLong(1, classId);
                ps.setString(2, nullSafe(m.getName()));
                setNullable(ps, 3, m.getReturnType());
                ps.setString(4, m.getVisibility() != null ? m.getVisibility().name() : "PACKAGE");
                ps.setString(5, encodeMethodModifiers(m));
                ps.setString(6, CsvCodec.join(m.getAnnotations()));
                ps.setString(7, CsvCodec.join(m.getParameterTypes()));
                ps.setString(8, CsvCodec.join(m.getParameterNames()));
                ps.setString(9, CsvCodec.join(m.getThrowsTypes()));
                ps.setInt(10, m.isAbstract() ? 1 : 0);
                setNullable(ps, 11, m.getComment());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 指定 class_id のフィールドを並び順 (id 順) に取得。 */
    public static List<JavaFieldInfo> loadFields(Connection conn, long classId) throws SQLException {
        List<JavaFieldInfo> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, type, visibility, modifiers, annotations "
                + "FROM fields WHERE class_id = ? ORDER BY id")) {
            ps.setLong(1, classId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rowToField(rs));
                }
            }
        }
        return out;
    }

    /** 指定 class_id のメソッドを並び順 (id 順) に取得。 */
    public static List<JavaMethodInfo> loadMethods(Connection conn, long classId) throws SQLException {
        List<JavaMethodInfo> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, return_type, visibility, modifiers, annotations, "
                + "param_types, param_names, throws_types, is_abstract, comment "
                + "FROM methods WHERE class_id = ? ORDER BY id")) {
            ps.setLong(1, classId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rowToMethod(rs));
                }
            }
        }
        return out;
    }

    // ---- encode/decode ----

    private static String encodeFieldModifiers(JavaFieldInfo f) {
        List<String> all = new ArrayList<>();
        if (f.isStatic()) {
            all.add(FLAG_STATIC);
        }
        if (f.isFinal()) {
            all.add(FLAG_FINAL);
        }
        return CsvCodec.join(all);
    }

    private static String encodeMethodModifiers(JavaMethodInfo m) {
        List<String> all = new ArrayList<>();
        if (m.isStatic()) {
            all.add(FLAG_STATIC);
        }
        if (m.isConstructor()) {
            all.add(FLAG_CTOR);
        }
        return CsvCodec.join(all);
    }

    private static JavaFieldInfo rowToField(ResultSet rs) throws SQLException {
        JavaFieldInfo f = new JavaFieldInfo();
        f.setName(rs.getString(1));
        f.setType(rs.getString(2));
        f.setVisibility(parseVisibility(rs.getString(3)));
        for (String mod : CsvCodec.split(rs.getString(4))) {
            if (FLAG_STATIC.equals(mod)) {
                f.setStatic(true);
            } else if (FLAG_FINAL.equals(mod)) {
                f.setFinal(true);
            }
        }
        for (String anno : CsvCodec.split(rs.getString(5))) {
            f.getAnnotations().add(anno);
        }
        return f;
    }

    private static JavaMethodInfo rowToMethod(ResultSet rs) throws SQLException {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(rs.getString(1));
        m.setReturnType(rs.getString(2));
        m.setVisibility(parseVisibility(rs.getString(3)));
        for (String mod : CsvCodec.split(rs.getString(4))) {
            if (FLAG_STATIC.equals(mod)) {
                m.setStatic(true);
            } else if (FLAG_CTOR.equals(mod)) {
                m.setConstructor(true);
            }
        }
        for (String anno : CsvCodec.split(rs.getString(5))) {
            m.getAnnotations().add(anno);
        }
        for (String pt : CsvCodec.split(rs.getString(6))) {
            m.getParameterTypes().add(pt);
        }
        for (String pn : CsvCodec.split(rs.getString(7))) {
            m.getParameterNames().add(pn);
        }
        for (String th : CsvCodec.split(rs.getString(8))) {
            m.getThrowsTypes().add(th);
        }
        m.setAbstract(rs.getInt(9) != 0);
        m.setComment(rs.getString(10));
        return m;
    }

    private static Visibility parseVisibility(String name) {
        if (name == null || name.isEmpty()) {
            return Visibility.PACKAGE;
        }
        try {
            return Visibility.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return Visibility.PACKAGE;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static void setNullable(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null || value.isEmpty()) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }
}
