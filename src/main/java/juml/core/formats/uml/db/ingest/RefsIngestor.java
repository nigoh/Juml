// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.ingest;

import juml.core.formats.uml.db.dao.RefsDao;
import juml.core.formats.uml.db.dao.UnresolvedDao;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * メモリ上の {@link ReferenceIndex} を {@code refs} / {@code unresolved} テーブルへ流し込む。
 *
 * <p>PR3 では既存のフルパース → メモリ ReferenceIndex 構築フローを残したまま、
 * 「結果を DB にも残す」経路を追加するだけ。PR4 で {@code ProjectAnalysisCache}
 * が DB 経路化すると、ここを経由して永続化されるようになる。</p>
 *
 * <p>{@code refs.file_id} はソース側 (caller 側) のファイル ID を指す。
 * caller ファイルを上書きすれば CASCADE でそのファイル発の ref が一斉に消える。
 * 解決は {@code classes} テーブルの {@code (qn, file_id)} マッピングを使って
 * 「caller_qn の所属ファイル」を引く。</p>
 */
public final class RefsIngestor {

    private RefsIngestor() {
    }

    /**
     * メモリ ReferenceIndex の中身を refs / unresolved テーブルに書き込む。
     *
     * <p>本メソッドは 1 トランザクションを開き、完了時にコミットする。
     * 既存の refs / unresolved 行は触らない (既存値の上書きや重複排除は呼び出し側の責務)。
     * 一般的な使い方は「テーブルを空のまま / 必要に応じて DELETE してから」 ingest する。</p>
     *
     * @return INSERT した refs 行数
     */
    public static int ingest(Connection conn, ReferenceIndex index) throws SQLException {
        if (index == null) {
            return 0;
        }
        Map<String, Long> callerFileId = loadCallerFqnToFileId(conn);
        List<RefsDao.Entry> entries = new ArrayList<>();
        for (ReferenceKey key : index.keys()) {
            for (ReferenceSite site : index.sites(key)) {
                Long fileId = callerFileId.get(site.getCallerFqn());
                entries.add(new RefsDao.Entry(key, site, fileId));
            }
        }
        boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        int inserted;
        try {
            inserted = RefsDao.insertBatch(conn, entries);
            UnresolvedDao.insertSymbols(conn, index.unresolved());
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prev);
        }
        return inserted;
    }

    /** {@code classes.qn → classes.file_id} のマップ。 */
    private static Map<String, Long> loadCallerFqnToFileId(Connection conn) throws SQLException {
        Map<String, Long> out = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT qn, file_id FROM classes WHERE file_id IS NOT NULL");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getLong(2));
            }
        }
        return out;
    }
}
