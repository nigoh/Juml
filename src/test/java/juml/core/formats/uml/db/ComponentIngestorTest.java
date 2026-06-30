// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.uml.AndroidSuperclassDetector;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.dao.ComponentsDao;
import juml.core.formats.uml.db.ingest.ComponentIngestor;
import juml.core.formats.uml.db.ingest.ManifestIngestor;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ComponentIngestorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static JavaClassInfo cls(String pkg, String name, String superClass) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setSuperClass(superClass);
        return c;
    }

    private File newDbFile() throws Exception {
        return new File(tmp.newFolder("cache"), "index.db");
    }

    @Test
    public void testIngestsFragmentDetectedBySuperclass() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            ClassIndex idx = new ClassIndex();
            idx.put(cls("app", "HomeFragment", "androidx.fragment.app.Fragment"), null, null);
            idx.put(cls("app", "Util", "java.lang.Object"), null, null);

            int written = ComponentIngestor.ingest(db.connection(), idx);
            assertEquals(1, written);

            // Manifest にも書かれない Fragment が components に登場する
            List<String> fragments = ComponentsDao.listClassQnsByType(db.connection(), "Fragment");
            assertEquals(1, fragments.size());
            assertEquals("app.HomeFragment", fragments.get(0));

            ComponentsDao.Row row = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Fragment", "app.HomeFragment");
            assertEquals(ComponentsDao.SRC_SUPERCLASS, row.detectionSrc);
            assertNull(row.manifestId);
        }
    }

    @Test
    public void testMergesManifestAndSuperclassDetectionToBOTH() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            // 先に Manifest から MainActivity を登録
            AndroidManifestInfo m = new AndroidManifestInfo();
            m.setPackageName("app");
            AndroidComponentInfo a = new AndroidComponentInfo(
                    AndroidComponentInfo.Kind.ACTIVITY, "app.MainActivity");
            a.setExported(true);
            m.getActivities().add(a);
            long manifestId = ManifestIngestor.ingest(db.connection(), m, null);

            // 次にソース継承で同じクラスを検出して投入
            Map<String, AndroidSuperclassDetector.ComponentKind> detected = new LinkedHashMap<>();
            detected.put("app.MainActivity", AndroidSuperclassDetector.ComponentKind.ACTIVITY);
            ComponentIngestor.ingest(db.connection(), detected);

            ComponentsDao.Row row = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Activity", "app.MainActivity");
            assertNotNull(row);
            assertEquals(ComponentsDao.SRC_BOTH, row.detectionSrc);
            assertEquals((Long) manifestId, row.manifestId);
            assertEquals("manifest 由来の exported 属性は保持される", Boolean.TRUE, row.exported);
        }
    }

    @Test
    public void testSecondManifestAfterSuperclassIngestPreservesSrcBoth() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            // 逆順 (super 先, manifest 後) でも BOTH に昇格すること
            Map<String, AndroidSuperclassDetector.ComponentKind> detected = new LinkedHashMap<>();
            detected.put("app.MainActivity", AndroidSuperclassDetector.ComponentKind.ACTIVITY);
            ComponentIngestor.ingest(db.connection(), detected);

            AndroidManifestInfo m = new AndroidManifestInfo();
            m.setPackageName("app");
            AndroidComponentInfo a = new AndroidComponentInfo(
                    AndroidComponentInfo.Kind.ACTIVITY, "app.MainActivity");
            a.setExported(false);
            m.getActivities().add(a);
            ManifestIngestor.ingest(db.connection(), m, null);

            ComponentsDao.Row row = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Activity", "app.MainActivity");
            assertEquals(ComponentsDao.SRC_BOTH, row.detectionSrc);
            assertEquals(Boolean.FALSE, row.exported);
        }
    }

    @Test
    public void testEmptyClassIndexIsNoop() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            assertEquals(0, ComponentIngestor.ingest(db.connection(), new ClassIndex()));
            assertEquals(0, ComponentsDao.count(db.connection()));
        }
    }

    @Test
    public void testActivityServiceFragmentCountedByType() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            ClassIndex idx = new ClassIndex();
            idx.put(cls("app", "MainActivity", "android.app.Activity"), null, null);
            idx.put(cls("app", "WorkService", "android.app.Service"), null, null);
            idx.put(cls("app", "HomeFragment", "androidx.fragment.app.Fragment"), null, null);
            idx.put(cls("app", "DetailFragment", "androidx.fragment.app.Fragment"), null, null);

            ComponentIngestor.ingest(db.connection(), idx);

            Map<String, Integer> counts = ComponentsDao.countByType(db.connection());
            assertEquals((Integer) 1, counts.get("Activity"));
            assertEquals((Integer) 1, counts.get("Service"));
            assertEquals((Integer) 2, counts.get("Fragment"));
        }
    }
}
