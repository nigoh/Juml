// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.db.IndexDatabase;
import juml.core.formats.uml.db.dao.AidlBindingsDao;
import juml.core.formats.uml.db.dao.AidlInterfacesDao;
import juml.core.formats.uml.db.dao.ComponentsDao;
import juml.core.formats.uml.db.dao.EndpointsDao;
import juml.core.formats.uml.db.dao.ManifestsDao;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code IndexCommand.run} の AIDL / Manifest / Components / Endpoints までを含む
 * 統合 e2e テスト。小さい Gradle プロジェクトを実際に作って index する。
 */
public class IndexCommandAggregateTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File write(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return f;
    }

    /** AIDL / Manifest / Java (Activity + Fragment) を含む小さい Gradle プロジェクト。 */
    private File buildSampleProject() throws Exception {
        File root = tmp.newFolder("proj");
        write(new File(root, "settings.gradle"), "include ':app'\n");

        // AndroidManifest.xml: MainActivity を宣言
        write(new File(root, "app/src/main/AndroidManifest.xml"),
                "<?xml version='1.0' encoding='utf-8'?>"
                + "<manifest xmlns:android='http://schemas.android.com/apk/res/android' "
                + "package='com.example.app'>"
                + "  <application android:name='com.example.app.MyApp'>"
                + "    <activity android:name='com.example.app.MainActivity' "
                + "              android:exported='true'>"
                + "      <intent-filter>"
                + "        <action android:name='android.intent.action.MAIN'/>"
                + "        <category android:name='android.intent.category.LAUNCHER'/>"
                + "      </intent-filter>"
                + "    </activity>"
                + "    <service android:name='com.example.app.WorkService'/>"
                + "  </application>"
                + "</manifest>");

        // Java: MainActivity (extends Activity), HomeFragment (extends Fragment)
        write(new File(root, "app/src/main/java/com/example/app/MainActivity.java"),
                "package com.example.app; "
                + "import android.app.Activity; "
                + "public class MainActivity extends Activity {}");
        write(new File(root, "app/src/main/java/com/example/app/HomeFragment.java"),
                "package com.example.app; "
                + "import androidx.fragment.app.Fragment; "
                + "public class HomeFragment extends Fragment {}");
        write(new File(root, "app/src/main/java/com/example/app/WorkService.java"),
                "package com.example.app; "
                + "import android.app.Service; "
                + "import android.content.Intent; "
                + "import android.os.IBinder; "
                + "public class WorkService extends Service { "
                + "  public IBinder onBind(Intent i) { return null; } "
                + "}");

        // .aidl 1 件
        write(new File(root, "app/src/main/aidl/com/example/app/ICarFoo.aidl"),
                "package com.example.app; "
                + "interface ICarFoo { "
                + "  int doSync(String name); "
                + "  oneway void notify(int n); "
                + "}");
        return root;
    }

    @Test
    public void testRunPopulatesAllAggregateTables() throws Exception {
        File root = buildSampleProject();
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        IndexCommand.Result result = IndexCommand.run(root, dbFile, ErrorListener.silent());

        // 3 Java + 1 AIDL + 1 Manifest = 5
        assertEquals(3, result.javaScanned);
        assertEquals(1, result.aidlScanned);
        assertEquals(1, result.manifestScanned);
        assertEquals(5, result.filesScanned);

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "t")) {
            // manifests: 1 行
            assertEquals(1, ManifestsDao.count(db.connection()));

            // components: Manifest 由来 (Activity, Service) + ソース継承由来 (Fragment).
            // MainActivity と WorkService は両方検出されるので detection_src=BOTH。
            Map<String, Integer> byType = ComponentsDao.countByType(db.connection());
            assertEquals((Integer) 1, byType.get("Activity"));
            assertEquals((Integer) 1, byType.get("Service"));
            assertEquals("Fragment は Manifest に無く SUPERCLASS のみで検出",
                    (Integer) 1, byType.get("Fragment"));

            ComponentsDao.Row mainActivity = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Activity", "com.example.app.MainActivity");
            assertNotNull(mainActivity);
            assertEquals(ComponentsDao.SRC_BOTH, mainActivity.detectionSrc);

            ComponentsDao.Row workService = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Service", "com.example.app.WorkService");
            assertNotNull(workService);
            assertEquals(ComponentsDao.SRC_BOTH, workService.detectionSrc);

            ComponentsDao.Row homeFragment = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Fragment", "com.example.app.HomeFragment");
            assertNotNull(homeFragment);
            assertEquals(ComponentsDao.SRC_SUPERCLASS, homeFragment.detectionSrc);

            // aidl_interfaces: 1 件
            assertEquals(1, AidlInterfacesDao.countInterfaces(db.connection()));
            List<AidlInterfacesDao.InterfaceRow> ifaces =
                    AidlInterfacesDao.listAllInterfaces(db.connection());
            assertEquals("com.example.app", ifaces.get(0).packageName);
            assertEquals("ICarFoo", ifaces.get(0).simpleName);

            // external_endpoints: Manifest 由来 + AIDL_INTERFACE (binding は実装が無いので 0)
            Map<String, Integer> epByKind = EndpointsDao.countByKind(db.connection());
            assertEquals((Integer) 1, epByKind.get(EndpointsDao.KIND_MANIFEST_ACTIVITY));
            assertEquals((Integer) 1, epByKind.get(EndpointsDao.KIND_MANIFEST_SERVICE));
            assertEquals((Integer) 1, epByKind.get(EndpointsDao.KIND_AIDL_INTERFACE));
        }
    }

    @Test
    public void testRerunIsIdempotentForAggregates() throws Exception {
        File root = buildSampleProject();
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        IndexCommand.run(root, dbFile, ErrorListener.silent());
        // 同じプロジェクトで再実行しても集約テーブル件数は変わらない (重複しない)
        IndexCommand.run(root, dbFile, ErrorListener.silent());

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "t")) {
            assertEquals(1, ManifestsDao.count(db.connection()));
            assertEquals(1, AidlInterfacesDao.countInterfaces(db.connection()));
            Map<String, Integer> byType = ComponentsDao.countByType(db.connection());
            assertEquals((Integer) 1, byType.get("Activity"));
            assertEquals((Integer) 1, byType.get("Service"));
            assertEquals((Integer) 1, byType.get("Fragment"));

            // external_endpoints も Manifest 由来 2 + AIDL_INTERFACE 1 で計 3 件
            assertEquals(3, EndpointsDao.count(db.connection()));
        }
    }

    @Test
    public void testManifestRemovalCascadesEverything() throws Exception {
        File root = buildSampleProject();
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        IndexCommand.run(root, dbFile, ErrorListener.silent());

        // AndroidManifest.xml を削除して再実行
        File manifest = new File(root, "app/src/main/AndroidManifest.xml");
        assertTrue(manifest.delete());

        IndexCommand.Result r2 = IndexCommand.run(root, dbFile, ErrorListener.silent());
        assertEquals("manifest scanned should be 0 after deletion", 0, r2.manifestScanned);
        assertTrue("at least 1 file should be reported as deleted", r2.filesDeleted >= 1);

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "t")) {
            // manifests テーブルが空 (CASCADE で消える)
            assertEquals(0, ManifestsDao.count(db.connection()));
            // Activity / Service は SUPERCLASS 由来でだけ残る
            ComponentsDao.Row activity = ComponentsDao.findByTypeAndClass(
                    db.connection(), "Activity", "com.example.app.MainActivity");
            assertNotNull(activity);
            assertEquals(ComponentsDao.SRC_SUPERCLASS, activity.detectionSrc);

            // external_endpoints の Manifest 由来は消え、AIDL_INTERFACE だけ残る
            Map<String, Integer> ep = EndpointsDao.countByKind(db.connection());
            assertTrue("no MANIFEST_ACTIVITY rows expected",
                    !ep.containsKey(EndpointsDao.KIND_MANIFEST_ACTIVITY));
            assertEquals((Integer) 1, ep.get(EndpointsDao.KIND_AIDL_INTERFACE));
        }
    }

    @Test
    public void testEmptyAidlBindingsWhenNoStubExists() throws Exception {
        File root = buildSampleProject();
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        IndexCommand.run(root, dbFile, ErrorListener.silent());

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, root.getAbsolutePath(), "t")) {
            // Stub 実装クラスを足していないので aidl_bindings は 0 件
            assertEquals(0, AidlBindingsDao.count(db.connection()));
        }
    }
}
