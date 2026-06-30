// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.ingest;

import juml.core.aaos.AidlBinding;
import juml.core.aaos.AidlBindingResolver;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.db.dao.AidlBindingsDao;
import juml.core.formats.uml.db.dao.AidlInterfacesDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AIDL interface とその実装 binding を {@code aidl_interfaces / aidl_methods /
 * aidl_bindings} に書き込むアダプタ。
 *
 * <p>入力:</p>
 * <ul>
 *   <li>{@link JavaClassInfo} のコレクション — {@code Kind.AIDL_INTERFACE} のもの
 *       が aidl_interfaces に登録対象</li>
 *   <li>(オプション) すでに走査済みクラス全体 — {@link AidlBindingResolver}
 *       で AIDL ↔ Stub 実装の紐付けを抽出し aidl_bindings に書き込み</li>
 * </ul>
 *
 * <p>classes テーブルへの登録は {@link ClassIngestor} が行うため、AidlIngestor は
 * 後段で {@code aidl_interfaces.class_id} を {@code classes.qn} 経由で解決する。
 * 既に classes 行が存在しない AIDL (依存 JAR 等) は {@code class_id = NULL} で
 * 登録する。</p>
 */
public final class AidlIngestor {

    private AidlIngestor() {
    }

    /**
     * {@link JavaClassInfo}({@code AIDL_INTERFACE}) のリストを aidl_interfaces /
     * aidl_methods に流す。{@link AidlBindingResolver#resolve} の結果が与えられた
     * 場合は aidl_bindings にも書く。
     *
     * @return INSERT した interface 行数 + binding 行数の合算
     */
    public static int ingest(Connection conn, Collection<JavaClassInfo> classes,
            Map<String, List<AidlBinding>> bindings) throws SQLException {
        int interfaces = ingestInterfaces(conn, classes);
        int bindRows = ingestBindings(conn, bindings);
        return interfaces + bindRows;
    }

    /** AIDL interface 部分だけを書く (binding は別呼び出し用)。 */
    public static int ingestInterfaces(Connection conn, Collection<JavaClassInfo> classes)
            throws SQLException {
        if (classes == null || classes.isEmpty()) {
            return 0;
        }
        Map<String, Long> qnToClassId = loadQnToClassId(conn);
        int count = 0;
        for (JavaClassInfo c : classes) {
            if (c == null || c.getKind() != JavaClassInfo.Kind.AIDL_INTERFACE) {
                continue;
            }
            String qn = c.getQualifiedName();
            Long classId = qnToClassId.get(qn);
            long aidlId = AidlInterfacesDao.insertInterface(
                    conn, classId, c.getPackageName(), c.getSimpleName());
            ingestMethods(conn, aidlId, c.getMethods());
            count++;
        }
        return count;
    }

    /** {@link AidlBindingResolver#resolve} の出力を aidl_bindings に流す。 */
    public static int ingestBindings(Connection conn, Map<String, List<AidlBinding>> bindings)
            throws SQLException {
        if (bindings == null || bindings.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<String, List<AidlBinding>> e : bindings.entrySet()) {
            List<AidlBinding> list = e.getValue();
            if (list == null) {
                continue;
            }
            for (AidlBinding b : list) {
                AidlBindingsDao.upsert(conn, b.getAidlInterfaceFqn(), b.getImplementationFqn());
                count++;
            }
        }
        return count;
    }

    private static void ingestMethods(Connection conn, long aidlId, List<JavaMethodInfo> methods)
            throws SQLException {
        if (methods == null || methods.isEmpty()) {
            return;
        }
        for (JavaMethodInfo m : methods) {
            if (m == null || m.getName() == null) {
                continue;
            }
            // AIDL の oneway 検出: 既存パーサは AIDL を Java として再解釈するため、
            // oneway は annotations 列に擬似アノテーションとして残ることがある。
            // JavaMethodInfo に modifiers list は無いので annotations だけを見る。
            boolean oneway = containsCaseInsensitive(m.getAnnotations(), "oneway");
            String paramSig = buildParamSig(m);
            AidlInterfacesDao.insertMethod(conn, aidlId, m.getName(), oneway,
                    m.getReturnType(), paramSig);
        }
    }

    private static String buildParamSig(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        List<String> types = m.getParameterTypes();
        List<String> names = m.getParameterNames();
        int n = Math.max(types == null ? 0 : types.size(), names == null ? 0 : names.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String t = (types != null && i < types.size()) ? types.get(i) : "";
            String nm = (names != null && i < names.size()) ? names.get(i) : "";
            if (!t.isEmpty()) {
                sb.append(t);
            }
            if (!nm.isEmpty()) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                sb.append(nm);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static boolean containsCaseInsensitive(List<String> list, String needle) {
        if (list == null) {
            return false;
        }
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Long> loadQnToClassId(Connection conn) throws SQLException {
        Map<String, Long> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT qn, id FROM classes");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
        }
        return out;
    }
}
