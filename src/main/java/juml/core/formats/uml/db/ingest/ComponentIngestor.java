// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.ingest;

import juml.core.formats.uml.AndroidSuperclassDetector;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.db.dao.ComponentsDao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * ソース継承 (extends) で判定した Android コンポーネント種別を
 * {@code components} テーブルにマージするアダプタ。
 *
 * <p>Manifest 由来は {@link ManifestIngestor} が先に書き込んでいる前提。
 * 本 ingestor は {@link AndroidSuperclassDetector} の判定結果を流し込み、
 * 既に Manifest 由来で登録済みのクラスが居れば {@code detection_src} を
 * {@link ComponentsDao#SRC_BOTH} に昇格させる。</p>
 *
 * <p>Manifest にも書かれない Fragment はここで初めて {@code components} に
 * 登場する (Fragment 一覧クエリの主用途)。</p>
 */
public final class ComponentIngestor {

    private ComponentIngestor() {
    }

    /** {@link ClassIndex} から Superclass 検出結果を集めて DB に流し込む。 */
    public static int ingest(Connection conn, ClassIndex index) throws SQLException {
        if (index == null) {
            return 0;
        }
        Map<String, AndroidSuperclassDetector.ComponentKind> detected =
                AndroidSuperclassDetector.detect(index);
        return ingest(conn, detected);
    }

    /** すでに走査済みの判定結果を DB に流し込む (テスト用)。 */
    public static int ingest(Connection conn,
            Map<String, AndroidSuperclassDetector.ComponentKind> detected) throws SQLException {
        if (detected == null || detected.isEmpty()) {
            return 0;
        }
        int written = 0;
        for (Map.Entry<String, AndroidSuperclassDetector.ComponentKind> e : detected.entrySet()) {
            String compType = compTypeOf(e.getValue());
            if (compType == null) {
                continue;
            }
            ComponentsDao.upsert(conn, compType, e.getKey(),
                    ComponentsDao.SRC_SUPERCLASS, null, null, null, null);
            written++;
        }
        return written;
    }

    /**
     * {@link AndroidSuperclassDetector.ComponentKind} → {@code components.comp_type} 値。
     * Manifest 系の表記 (Activity/Service/Receiver/Provider) に合わせる。Fragment は追加。
     */
    public static String compTypeOf(AndroidSuperclassDetector.ComponentKind kind) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case APPLICATION:
                return "Application";
            case ACTIVITY:
                return "Activity";
            case SERVICE:
                return "Service";
            case RECEIVER:
                return "Receiver";
            case PROVIDER:
                return "Provider";
            case FRAGMENT:
                return "Fragment";
            default:
                return null;
        }
    }
}
