// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidIntentFilter;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.uml.db.dao.ComponentsDao;
import juml.core.formats.uml.db.dao.IntentFiltersDao;
import juml.core.formats.uml.db.dao.ManifestsDao;
import juml.core.formats.uml.db.ingest.ManifestIngestor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ManifestIngestorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static AndroidComponentInfo activity(String name, boolean exported,
            AndroidIntentFilter... filters) {
        AndroidComponentInfo a = new AndroidComponentInfo(AndroidComponentInfo.Kind.ACTIVITY, name);
        a.setExported(exported);
        for (AndroidIntentFilter f : filters) {
            a.getIntentFilters().add(f);
        }
        return a;
    }

    private static AndroidIntentFilter filter(List<String> actions, List<String> categories) {
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.getActions().addAll(actions);
        f.getCategories().addAll(categories);
        return f;
    }

    private File newDbFile() throws Exception {
        return new File(tmp.newFolder("cache"), "index.db");
    }

    @Test
    public void testIngestsMinimumManifest() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            AndroidManifestInfo m = new AndroidManifestInfo();
            m.setPackageName("com.example.app");
            m.setMinSdkVersion(21);
            m.setTargetSdkVersion(34);
            m.setApplicationClass("com.example.app.MyApp");
            m.getActivities().add(activity("com.example.app.MainActivity", true));

            long manifestId = ManifestIngestor.ingest(db.connection(), m, null);
            assertTrue(manifestId > 0);

            assertEquals(1, ManifestsDao.count(db.connection()));
            assertEquals(1, ComponentsDao.count(db.connection()));

            // Activity 一覧
            List<String> activities = ComponentsDao.listClassQnsByType(db.connection(), "Activity");
            assertEquals(Arrays.asList("com.example.app.MainActivity"), activities);

            ComponentsDao.Row row = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Activity", "com.example.app.MainActivity");
            assertNotNull(row);
            assertEquals(ComponentsDao.SRC_MANIFEST, row.detectionSrc);
            assertEquals(Boolean.TRUE, row.exported);
            assertEquals((Long) manifestId, row.manifestId);
        }
    }

    @Test
    public void testIngestsIntentFiltersWithCsvEncoding() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            AndroidManifestInfo m = new AndroidManifestInfo();
            m.setPackageName("p");
            AndroidIntentFilter f1 = filter(
                    Arrays.asList("android.intent.action.MAIN"),
                    Arrays.asList("android.intent.category.LAUNCHER"));
            AndroidIntentFilter f2 = filter(
                    Arrays.asList("android.intent.action.VIEW", "android.intent.action.EDIT"),
                    Arrays.asList("android.intent.category.DEFAULT"));
            m.getActivities().add(activity("p.MainActivity", true, f1, f2));

            ManifestIngestor.ingest(db.connection(), m, null);

            ComponentsDao.Row comp = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Activity", "p.MainActivity");
            List<IntentFiltersDao.Row> filters =
                    IntentFiltersDao.listByComponent(db.connection(), comp.id);
            assertEquals(2, filters.size());
            assertEquals(Arrays.asList("android.intent.action.MAIN"), filters.get(0).actions);
            assertEquals(Arrays.asList("android.intent.category.LAUNCHER"), filters.get(0).categories);
            assertEquals(Arrays.asList("android.intent.action.VIEW",
                    "android.intent.action.EDIT"), filters.get(1).actions);

            // action LIKE 検索 (componentIdsWithAction)
            List<Long> componentIds = IntentFiltersDao.componentIdsWithAction(
                    db.connection(), "android.intent.action.VIEW");
            assertEquals(1, componentIds.size());
            assertEquals((Long) comp.id, componentIds.get(0));
        }
    }

    @Test
    public void testIngestsAllFourComponentKinds() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            AndroidManifestInfo m = new AndroidManifestInfo();
            m.setPackageName("p");
            m.getActivities().add(activity("p.A", true));
            m.getServices().add(new AndroidComponentInfo(AndroidComponentInfo.Kind.SERVICE, "p.S"));
            m.getReceivers().add(new AndroidComponentInfo(AndroidComponentInfo.Kind.RECEIVER, "p.R"));
            m.getProviders().add(new AndroidComponentInfo(AndroidComponentInfo.Kind.PROVIDER, "p.P"));

            ManifestIngestor.ingest(db.connection(), m, null);

            assertEquals(4, ComponentsDao.count(db.connection()));
            // comp_type 別件数
            assertEquals((Integer) 1, ComponentsDao.countByType(db.connection()).get("Activity"));
            assertEquals((Integer) 1, ComponentsDao.countByType(db.connection()).get("Service"));
            assertEquals((Integer) 1, ComponentsDao.countByType(db.connection()).get("Receiver"));
            assertEquals((Integer) 1, ComponentsDao.countByType(db.connection()).get("Provider"));
        }
    }

    @Test
    public void testNullManifestIsNoop() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            assertEquals(-1L, ManifestIngestor.ingest(db.connection(), null, null));
            assertEquals(0, ManifestsDao.count(db.connection()));
        }
    }
}
