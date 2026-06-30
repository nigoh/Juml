// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db.ingest;

import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidIntentFilter;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.uml.db.dao.ComponentsDao;
import juml.core.formats.uml.db.dao.IntentFiltersDao;
import juml.core.formats.uml.db.dao.ManifestsDao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * {@link AndroidManifestInfo} を {@code manifests / components / intent_filters} に
 * 書き戻すアダプタ。
 *
 * <p>パース処理 ({@link juml.core.formats.android.AndroidManifestParser}) は触らず、
 * 「結果オブジェクト → DB 行」の変換だけを担う。Components の {@code detection_src} は
 * 必ず {@link ComponentsDao#SRC_MANIFEST}。Fragment はここでは入らない
 * ({@link juml.core.formats.uml.AndroidSuperclassDetector} 経由で
 * {@link ComponentIngestor} が追加する)。</p>
 */
public final class ManifestIngestor {

    private ManifestIngestor() {
    }

    /** 1 manifest 分を一括 INSERT。manifest が null なら no-op。 */
    public static long ingest(Connection conn, AndroidManifestInfo manifest, Long fileId)
            throws SQLException {
        if (manifest == null) {
            return -1L;
        }
        long manifestId = ManifestsDao.insert(
                conn,
                fileId,
                manifest.getPackageName(),
                manifest.getSourceSet(),
                manifest.getMinSdkVersion(),
                manifest.getTargetSdkVersion(),
                manifest.getMaxSdkVersion(),
                manifest.getApplicationClass()
        );

        ingestComponents(conn, manifestId, "Activity", manifest.getActivities());
        ingestComponents(conn, manifestId, "Service", manifest.getServices());
        ingestComponents(conn, manifestId, "Receiver", manifest.getReceivers());
        ingestComponents(conn, manifestId, "Provider", manifest.getProviders());
        return manifestId;
    }

    private static void ingestComponents(Connection conn, long manifestId,
            String compType, List<AndroidComponentInfo> components) throws SQLException {
        if (components == null || components.isEmpty()) {
            return;
        }
        for (AndroidComponentInfo c : components) {
            String classQn = c.getName();
            if (classQn == null || classQn.isEmpty()) {
                continue;
            }
            long componentId = ComponentsDao.upsert(conn, compType, classQn,
                    ComponentsDao.SRC_MANIFEST, manifestId,
                    c.getExported(), c.getPermission(), c.getEnabled());
            ingestFilters(conn, componentId, c.getIntentFilters());
        }
    }

    private static void ingestFilters(Connection conn, long componentId,
            List<AndroidIntentFilter> filters) throws SQLException {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (int i = 0; i < filters.size(); i++) {
            AndroidIntentFilter f = filters.get(i);
            IntentFiltersDao.insert(conn, componentId, i,
                    f.getActions(),
                    f.getCategories(),
                    f.getDataSchemes(),
                    f.getDataMimeTypes());
        }
    }
}
