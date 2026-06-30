// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.dao.ClassesDao;
import juml.core.formats.uml.db.dao.FilesDao;
import juml.core.formats.uml.db.dao.MembersDao;
import juml.core.formats.uml.db.dao.ModulesDao;
import juml.core.formats.uml.db.dao.RefsDao;
import juml.core.formats.uml.db.dao.UnresolvedDao;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite インデックスからの読み取り API。
 *
 * <p>主用途は (1) Stage A 相当の {@link ClassIndex} をまるごと復元すること、
 * (2) 個別 qn による参照引き ({@link #headerOf}, {@link #methodsOf} 等)。
 * 書き込みは {@link IndexWriter} 側、コネクションのライフサイクルは
 * {@link IndexDatabase} 側で管理する。</p>
 */
public final class IndexReader {

    private final Connection conn;

    public IndexReader(Connection conn) {
        this.conn = conn;
    }

    /**
     * DB の classes 全件を読み出して {@link ClassIndex} を組み立てる。
     *
     * <p>各クラスは Stage A ヘッダ + import + interface まで復元する。
     * fields / methods は含めない (Stage B で必要なときだけ {@link ClassIndex#detail}
     * 経由でソース再パースする既存設計を踏襲)。</p>
     *
     * @param projectRoot ソースファイル復元の起点 (files.path は root 相対)
     */
    public ClassIndex loadStageAClassIndex(File projectRoot) throws SQLException {
        ClassIndex idx = new ClassIndex();
        Map<String, JavaClassInfo> all = new LinkedHashMap<>();
        for (JavaClassInfo info : ClassesDao.loadAllHeaders(conn)) {
            // ClassesDao が Stage A (detailed=false) で復元する
            all.put(info.getQualifiedName(), info);
        }
        Map<String, String> qnToPath = qnToFilePath();
        Map<Long, String> moduleNameById = moduleNameById();
        Map<String, Long> qnToModuleId = ClassesDao.qnToModuleId(conn);

        for (Map.Entry<String, JavaClassInfo> e : all.entrySet()) {
            String qn = e.getKey();
            JavaClassInfo info = e.getValue();
            String relPath = qnToPath.get(qn);
            File source = (relPath == null || relPath.isEmpty())
                    ? null
                    : resolveSource(projectRoot, relPath);
            Long modId = qnToModuleId.get(qn);
            String module = modId == null ? null : moduleNameById.get(modId);
            idx.put(info, source, module);
        }
        return idx;
    }

    /** 1 件の {@link JavaClassInfo} (ヘッダ + import + interface) を引く。なければ null。 */
    public JavaClassInfo headerOf(String qn) throws SQLException {
        return ClassesDao.loadHeader(conn, qn);
    }

    /** 指定 qn のクラスに属するフィールドを id 順に取得。 */
    public java.util.List<juml.core.formats.uml.JavaFieldInfo> fieldsOf(String qn)
            throws SQLException {
        Long classId = classIdOf(qn);
        if (classId == null) {
            return java.util.Collections.emptyList();
        }
        return MembersDao.loadFields(conn, classId);
    }

    /** 指定 qn のクラスに属するメソッドを id 順に取得。 */
    public java.util.List<juml.core.formats.uml.JavaMethodInfo> methodsOf(String qn)
            throws SQLException {
        Long classId = classIdOf(qn);
        if (classId == null) {
            return java.util.Collections.emptyList();
        }
        return MembersDao.loadMethods(conn, classId);
    }

    /** classes テーブルの総数。 */
    public int classCount() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM classes");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** files テーブルの (path → row) 一覧 (差分検出用)。 */
    public Map<String, FilesDao.FileRow> filesByKind(String kind) throws SQLException {
        return FilesDao.listByKind(conn, kind);
    }

    /** 登録済みモジュール名一覧。 */
    public Map<String, String> modules() throws SQLException {
        return ModulesDao.listAll(conn);
    }

    /**
     * DB の refs / unresolved 全件からメモリ {@link ReferenceIndex} を再構成する。
     *
     * <p>{@link juml.core.formats.uml.db.ingest.RefsIngestor#ingest} と対になる
     * 双方向 round-trip 用 API。{@code ImpactAnalyzer} など既存メモリ前提のコードは
     * これを介して DB バックのデータを使える (PR4 で本格活用)。</p>
     */
    public ReferenceIndex loadReferenceIndex() throws SQLException {
        ReferenceIndex index = new ReferenceIndex();
        Map<ReferenceKey, List<ReferenceSite>> all = RefsDao.loadAll(conn);
        for (Map.Entry<ReferenceKey, List<ReferenceSite>> e : all.entrySet()) {
            for (ReferenceSite site : e.getValue()) {
                index.addReference(e.getKey(), site);
            }
        }
        for (String sym : UnresolvedDao.listAllSymbols(conn)) {
            index.addUnresolved(sym);
        }
        return index;
    }

    /** refs テーブルの総行数 (診断用)。 */
    public int referenceCount() throws SQLException {
        return RefsDao.count(conn);
    }

    // ---- private ----

    private Long classIdOf(String qn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM classes WHERE qn = ?")) {
            ps.setString(1, qn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private Map<String, String> qnToFilePath() throws SQLException {
        Map<String, String> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT c.qn, f.path FROM classes c "
                + "JOIN files f ON c.file_id = f.id ORDER BY c.qn");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        }
        return out;
    }

    private Map<Long, String> moduleNameById() throws SQLException {
        Map<Long, String> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM modules");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getLong(1), rs.getString(2));
            }
        }
        return out;
    }

    private static File resolveSource(File projectRoot, String relPath) {
        File f = new File(relPath);
        if (f.isAbsolute()) {
            return f;
        }
        if (projectRoot == null) {
            return f;
        }
        try {
            File resolved = new File(projectRoot, relPath).getCanonicalFile();
            if (!resolved.getPath().startsWith(projectRoot.getCanonicalPath() + File.separator)) {
                return null;
            }
            return resolved;
        } catch (IOException ex) {
            return null;
        }
    }
}
