// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

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
 * {@code refs} テーブル CRUD。
 *
 * <p>1 行 = {@link ReferenceKey} (callee 側) と {@link ReferenceSite} (caller 側) を
 * 展開した参照イベント。インデックス設計と運用方針はクラス Javadoc 参照。</p>
 *
 * <ul>
 *   <li>書き込み: {@link #insertBatch} で 1000 件単位のバッチ INSERT。
 *       file_id が同じ caller の参照は 1 TX 内に纏める運用にする。</li>
 *   <li>読み: {@link #sitesForKey} と {@link #sitesForClass}。
 *       Java 側で BFS する N-hop 引きは PR3 では実装しない (in-memory 経路で十分)。</li>
 * </ul>
 */
public final class RefsDao {

    private RefsDao() {
    }

    /** 1 件の参照を INSERT。バッチが必要なら {@link #insertBatch}。 */
    public static void insertOne(Connection conn, ReferenceKey key, ReferenceSite site,
            Long fileId) throws SQLException {
        try (PreparedStatement ps = prepareInsert(conn)) {
            bind(ps, key, site, fileId);
            ps.executeUpdate();
        }
    }

    /** バッチ INSERT。{@link Entry} のリストを受け取り 1 トランザクションで流す。 */
    public static int insertBatch(Connection conn, List<Entry> entries) throws SQLException {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int count = 0;
        try (PreparedStatement ps = prepareInsert(conn)) {
            for (Entry e : entries) {
                bind(ps, e.key, e.site, e.fileId);
                ps.addBatch();
                count++;
                if (count % 1000 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        return count;
    }

    /**
     * 指定 callee キーに紐付く全 site を取得 (登録順 = id 順)。
     */
    public static List<ReferenceSite> sitesForKey(Connection conn, ReferenceKey key)
            throws SQLException {
        List<ReferenceSite> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.caller_qn, r.caller_method, f.path, r.line_hint, r.ref_kind "
                + "FROM refs r LEFT JOIN files f ON r.file_id = f.id "
                + "WHERE r.callee_owner_qn = ? AND r.sym_kind = ? AND "
                + (key.getMember() == null ? "r.callee_member IS NULL "
                                            : "r.callee_member = ? ")
                + "ORDER BY r.id")) {
            ps.setString(1, key.getOwnerFqn());
            ps.setString(2, key.getKind().name());
            if (key.getMember() != null) {
                ps.setString(3, key.getMember());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(toSite(rs));
                }
            }
        }
        return out;
    }

    /**
     * 指定 FQN の callee_owner_qn を持つ全 site (class/method/field 横断、登録順)。
     * {@link juml.core.refs.ReferenceIndex#sitesForClass} と同等。
     */
    public static List<ReferenceSite> sitesForClass(Connection conn, String fqn)
            throws SQLException {
        List<ReferenceSite> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.caller_qn, r.caller_method, f.path, r.line_hint, r.ref_kind "
                + "FROM refs r LEFT JOIN files f ON r.file_id = f.id "
                + "WHERE r.callee_owner_qn = ? "
                + "ORDER BY r.id")) {
            ps.setString(1, fqn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(toSite(rs));
                }
            }
        }
        return out;
    }

    /**
     * 全 ref 行を {@code (key → sites)} の登録順マップで取得する。
     * {@link juml.core.formats.uml.db.IndexReader#loadReferenceIndex} で使う。
     */
    public static Map<ReferenceKey, List<ReferenceSite>> loadAll(Connection conn)
            throws SQLException {
        Map<ReferenceKey, List<ReferenceSite>> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT r.callee_owner_qn, r.callee_member, r.sym_kind, "
                + "       r.caller_qn, r.caller_method, f.path, r.line_hint, r.ref_kind, "
                + "       r.callee_signature "
                + "FROM refs r LEFT JOIN files f ON r.file_id = f.id "
                + "ORDER BY r.id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ReferenceKey key = toKey(rs);
                ReferenceSite site = toSiteFromAll(rs);
                out.computeIfAbsent(key, k -> new ArrayList<>()).add(site);
            }
        }
        return out;
    }

    /** refs テーブルの総行数。 */
    public static int count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM refs");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---- internals ----

    private static PreparedStatement prepareInsert(Connection conn) throws SQLException {
        return conn.prepareStatement(
                "INSERT INTO refs("
                + "callee_owner_qn, callee_member, sym_kind, "
                + "caller_qn, caller_method, file_id, line_hint, ref_kind, callee_signature) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    private static void bind(PreparedStatement ps, ReferenceKey key, ReferenceSite site,
            Long fileId) throws SQLException {
        ps.setString(1, key.getOwnerFqn());
        if (key.getMember() == null || key.getMember().isEmpty()) {
            ps.setNull(2, Types.VARCHAR);
        } else {
            ps.setString(2, key.getMember());
        }
        ps.setString(3, key.getKind().name());
        ps.setString(4, site.getCallerFqn() == null ? "" : site.getCallerFqn());
        if (site.getCallerMethod() == null || site.getCallerMethod().isEmpty()) {
            ps.setNull(5, Types.VARCHAR);
        } else {
            ps.setString(5, site.getCallerMethod());
        }
        if (fileId == null) {
            ps.setNull(6, Types.INTEGER);
        } else {
            ps.setLong(6, fileId);
        }
        ps.setInt(7, site.getLineHint());
        ps.setString(8, site.getKind().name());
        if (key.getSignature() == null || key.getSignature().isEmpty()) {
            ps.setNull(9, Types.VARCHAR);
        } else {
            ps.setString(9, key.getSignature());
        }
    }

    private static ReferenceKey toKey(ResultSet rs) throws SQLException {
        String ownerQn = rs.getString(1);
        String member = rs.getString(2);
        ReferenceKey.Kind kind;
        try {
            kind = ReferenceKey.Kind.valueOf(rs.getString(3));
        } catch (IllegalArgumentException ex) {
            kind = ReferenceKey.Kind.CLASS;
        }
        switch (kind) {
            case METHOD:
                String sig = rs.getString(9);
                return (sig == null || sig.isEmpty())
                        ? ReferenceKey.ofMethod(ownerQn, member)
                        : ReferenceKey.ofMethod(ownerQn, member, sig);
            case FIELD:
                return ReferenceKey.ofField(ownerQn, member);
            case CLASS:
            default:
                return ReferenceKey.ofClass(ownerQn);
        }
    }

    /** {@link #sitesForKey} / {@link #sitesForClass} の SELECT 結果から ReferenceSite を作る。 */
    private static ReferenceSite toSite(ResultSet rs) throws SQLException {
        String callerQn = rs.getString(1);
        String callerMethod = rs.getString(2);
        String filePath = rs.getString(3);
        int line = rs.getInt(4);
        ReferenceSite.Kind kind;
        try {
            kind = ReferenceSite.Kind.valueOf(rs.getString(5));
        } catch (IllegalArgumentException ex) {
            kind = ReferenceSite.Kind.CALL;
        }
        return new ReferenceSite(callerQn, callerMethod, filePath, line, kind);
    }

    /** {@link #loadAll} の SELECT 結果から ReferenceSite を作る (列オフセット違い)。 */
    private static ReferenceSite toSiteFromAll(ResultSet rs) throws SQLException {
        String callerQn = rs.getString(4);
        String callerMethod = rs.getString(5);
        String filePath = rs.getString(6);
        int line = rs.getInt(7);
        ReferenceSite.Kind kind;
        try {
            kind = ReferenceSite.Kind.valueOf(rs.getString(8));
        } catch (IllegalArgumentException ex) {
            kind = ReferenceSite.Kind.CALL;
        }
        return new ReferenceSite(callerQn, callerMethod, filePath, line, kind);
    }

    /** 1 行分のデータ ({@link #insertBatch} の入力)。 */
    public static final class Entry {
        public final ReferenceKey key;
        public final ReferenceSite site;
        public final Long fileId;

        public Entry(ReferenceKey key, ReferenceSite site, Long fileId) {
            this.key = key;
            this.site = site;
            this.fileId = fileId;
        }
    }
}
