// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.dao.FilesDao;
import juml.core.formats.uml.db.dao.ModulesDao;
import juml.core.formats.uml.db.ingest.ClassIngestor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 1 ファイル分の解析結果を 1 トランザクションで DB に差し替える高レベル API。
 *
 * <p>差し替え戦略:</p>
 * <ol>
 *   <li>{@code files} 行を path で DELETE → CASCADE で classes / fields /
 *       methods / class_interfaces / class_imports が消える</li>
 *   <li>{@code files} 行を INSERT</li>
 *   <li>各 {@link JavaClassInfo} を {@link ClassIngestor} で classes 配下に INSERT</li>
 * </ol>
 *
 * <p>SQLite ライタは並列化しても busy になりやすいので、複数スレッドから呼ぶ場合は
 * 呼び出し側で逐次化すること (将来の {@code IncrementalScanner} がそうする予定)。</p>
 */
public final class IndexWriter {

    /** files テーブルに格納する種別。 */
    public static final String KIND_JAVA = "JAVA";
    public static final String KIND_AIDL = "AIDL";
    public static final String KIND_MANIFEST = "MANIFEST";
    public static final String KIND_XML = "XML";
    public static final String KIND_GRADLE = "GRADLE";
    public static final String KIND_BP = "BP";

    private final Connection conn;

    public IndexWriter(Connection conn) {
        this.conn = conn;
    }

    /**
     * 1 ファイル分の解析結果を 1 TX で差し替える。
     *
     * @param path        プロジェクトルートからの相対パス (files テーブルの key)
     * @param kind        ファイル種別 ({@link #KIND_JAVA} 等)
     * @param mtime       ファイル mtime (ms)
     * @param size        ファイルサイズ
     * @param moduleName  Gradle モジュール名 (例 ":app")。null なら未紐付け
     * @param modulePath  モジュールルートの相対パス (新規登録時のみ使用)
     * @param classes     ファイル内のクラス一覧 (空 OK = parse error 等)
     * @param parseError  パース失敗時のメッセージ。成功なら null
     */
    public synchronized void upsertFile(
            String path,
            String kind,
            long mtime,
            long size,
            String moduleName,
            String modulePath,
            List<JavaClassInfo> classes,
            String parseError) throws SQLException {
        boolean prev = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            Long moduleId = (moduleName == null || moduleName.isEmpty())
                    ? null
                    : ModulesDao.upsert(conn, moduleName, modulePath == null ? "" : modulePath);
            long fileId = FilesDao.replace(conn, path, moduleId, kind, mtime, size, parseError);
            if (classes != null) {
                for (JavaClassInfo c : classes) {
                    if (c != null) {
                        ClassIngestor.ingest(conn, c, fileId);
                    }
                }
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prev);
        }
    }
}
