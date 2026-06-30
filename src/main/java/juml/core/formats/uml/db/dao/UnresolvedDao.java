// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code unresolved} テーブル CRUD。
 *
 * <p>{@link juml.core.refs.ReferenceIndex#addUnresolved} に積まれた
 * 名前解決失敗シンボル (FQN 化できなかった単純名など) の永続化先。診断・デバッグ用途。</p>
 */
public final class UnresolvedDao {

    private UnresolvedDao() {
    }

    /** 1 件 INSERT。 */
    public static void insert(Connection conn, String symbol, String callerQn, Long fileId)
            throws SQLException {
        if (symbol == null || symbol.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO unresolved(symbol, caller_qn, file_id) VALUES (?, ?, ?)")) {
            ps.setString(1, symbol);
            if (callerQn == null || callerQn.isEmpty()) {
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(2, callerQn);
            }
            if (fileId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setLong(3, fileId);
            }
            ps.executeUpdate();
        }
    }

    /** バッチ INSERT (caller_qn / file_id は null)。{@link juml.core.refs.ReferenceIndex#unresolved} 用。 */
    public static void insertSymbols(Connection conn, List<String> symbols) throws SQLException {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO unresolved(symbol, caller_qn, file_id) VALUES (?, NULL, NULL)")) {
            for (String s : symbols) {
                if (s == null || s.isEmpty()) {
                    continue;
                }
                ps.setString(1, s);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 登録された全シンボル一覧 (id 順)。重複は許容 (登録順を保持)。 */
    public static List<String> listAllSymbols(Connection conn) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT symbol FROM unresolved ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    /** 行数。 */
    public static int count(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM unresolved");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
