// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.ingest;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.dao.ClassesDao;
import juml.core.formats.uml.db.dao.MembersDao;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link JavaClassInfo} を {@code classes / class_interfaces / class_imports /
 * fields / methods} テーブル群へ書き込むアダプタ。
 *
 * <p>呼び出し側は事前に file 行を {@code files} テーブルに INSERT (もしくは
 * {@link juml.core.formats.uml.db.dao.FilesDao#replace replace}) し、
 * 取得した {@code fileId} を渡すこと。クラス削除は file 行の DELETE による
 * CASCADE で行われる前提なので、本クラスは INSERT のみ提供する。</p>
 */
public final class ClassIngestor {

    private ClassIngestor() {
    }

    /** 1 クラス分を classes / fields / methods に書き込む。 */
    public static long ingest(Connection conn, JavaClassInfo info, Long fileId) throws SQLException {
        long classId = ClassesDao.insert(conn, info, fileId);
        MembersDao.insertFields(conn, classId, info.getFields());
        MembersDao.insertMethods(conn, classId, info.getMethods());
        return classId;
    }
}
