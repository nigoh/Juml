// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.aaos.AidlBinding;
import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.dao.EndpointsDao;
import juml.core.formats.uml.db.ingest.EndpointAggregator;
import juml.core.screen.ScreenTransition;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EndpointAggregatorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File newDbFile() throws Exception {
        return new File(tmp.newFolder("cache"), "index.db");
    }

    @Test
    public void testManifestComponentsBecomeEndpoints() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            AndroidManifestInfo m = new AndroidManifestInfo();
            AndroidComponentInfo activity = new AndroidComponentInfo(
                    AndroidComponentInfo.Kind.ACTIVITY, "app.MainActivity");
            activity.setExported(true);
            activity.setPermission("PERM");
            m.getActivities().add(activity);
            m.getServices().add(new AndroidComponentInfo(
                    AndroidComponentInfo.Kind.SERVICE, "app.WorkService"));

            int written = EndpointAggregator.ingestManifest(db.connection(), m, null);
            assertEquals(2, written);

            Map<String, Integer> byKind = EndpointsDao.countByKind(db.connection());
            assertEquals((Integer) 1, byKind.get(EndpointsDao.KIND_MANIFEST_ACTIVITY));
            assertEquals((Integer) 1, byKind.get(EndpointsDao.KIND_MANIFEST_SERVICE));

            // attributes に exported / permission が乗っている
            List<EndpointsDao.Row> activities = EndpointsDao.listIncoming(
                    db.connection(), "app.MainActivity");
            assertEquals(1, activities.size());
            assertTrue(activities.get(0).attributes.contains("exported=true"));
            assertTrue(activities.get(0).attributes.contains("permission=PERM"));
        }
    }

    @Test
    public void testIntentTransitionsBecomeEndpoints() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            List<ScreenTransition> ts = Arrays.asList(
                    new ScreenTransition("app.A", "onClick", "app.B",
                            "A.java", 42, ScreenTransition.Kind.START_ACTIVITY),
                    new ScreenTransition("app.C", "doIt", "app.D",
                            "C.java", 10, ScreenTransition.Kind.SET_CLASS));
            int written = EndpointAggregator.ingestIntentTransitions(db.connection(), ts);
            assertEquals(2, written);

            Map<String, Integer> byKind = EndpointsDao.countByKind(db.connection());
            assertEquals((Integer) 1, byKind.get(EndpointsDao.KIND_INTENT_START_ACTIVITY));
            assertEquals((Integer) 1, byKind.get(EndpointsDao.KIND_INTENT_SET_CLASS));

            List<EndpointsDao.Row> incomingB = EndpointsDao.listIncoming(db.connection(), "app.B");
            assertEquals(1, incomingB.size());
            assertEquals("app.A", incomingB.get(0).fromQn);
            assertEquals("onClick", incomingB.get(0).fromMethod);
            assertEquals(42, incomingB.get(0).lineHint);
        }
    }

    @Test
    public void testAidlInterfaceAndBindingBecomeEndpoints() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            JavaClassInfo iface = new JavaClassInfo();
            iface.setPackageName("car");
            iface.setSimpleName("ICarFoo");
            iface.setKind(JavaClassInfo.Kind.AIDL_INTERFACE);

            Map<String, List<AidlBinding>> bindings = new HashMap<>();
            bindings.put("car.ICarFoo", Collections.singletonList(
                    new AidlBinding("car.ICarFoo", "car.impl.CarFooImpl", "CarFooImpl.java")));

            int written = EndpointAggregator.ingestAidl(db.connection(),
                    Collections.singletonList(iface), bindings);
            assertEquals(2, written);

            Map<String, Integer> byKind = EndpointsDao.countByKind(db.connection());
            assertEquals((Integer) 1, byKind.get(EndpointsDao.KIND_AIDL_INTERFACE));
            assertEquals((Integer) 1, byKind.get(EndpointsDao.KIND_AIDL_BINDING));

            // AIDL_INTERFACE と AIDL_BINDING どちらも to_qn=AIDL FQN なので listIncoming は 2 件
            List<EndpointsDao.Row> ifaceRows = EndpointsDao.listIncoming(
                    db.connection(), "car.ICarFoo");
            assertEquals(2, ifaceRows.size());
            assertTrue(ifaceRows.stream().anyMatch(
                    r -> EndpointsDao.KIND_AIDL_INTERFACE.equals(r.sourceKind)));
            assertTrue(ifaceRows.stream().anyMatch(
                    r -> EndpointsDao.KIND_AIDL_BINDING.equals(r.sourceKind)));

            // AIDL_BINDING は from_qn = 実装, to_qn = AIDL FQN
            List<EndpointsDao.Row> all = EndpointsDao.listAll(db.connection());
            EndpointsDao.Row binding = null;
            for (EndpointsDao.Row r : all) {
                if (EndpointsDao.KIND_AIDL_BINDING.equals(r.sourceKind)) {
                    binding = r;
                    break;
                }
            }
            assertEquals("car.impl.CarFooImpl", binding.fromQn);
            assertEquals("car.ICarFoo", binding.toQn);
            assertTrue(binding.attributes.contains("impl_file=CarFooImpl.java"));
        }
    }

    @Test
    public void testEmptyInputsAreNoop() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            assertEquals(0, EndpointAggregator.ingestManifest(db.connection(), null, null));
            assertEquals(0, EndpointAggregator.ingestIntentTransitions(
                    db.connection(), Collections.emptyList()));
            assertEquals(0, EndpointAggregator.ingestAidl(
                    db.connection(), Collections.emptyList(), null));
            assertEquals(0, EndpointsDao.count(db.connection()));
        }
    }

    @Test
    public void testCombinedAggregateGivesSingleListing() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            AndroidManifestInfo m = new AndroidManifestInfo();
            m.getActivities().add(new AndroidComponentInfo(
                    AndroidComponentInfo.Kind.ACTIVITY, "app.A"));

            ScreenTransition t = new ScreenTransition("app.X", "go", "app.A",
                    "X.java", -1, ScreenTransition.Kind.START_ACTIVITY);

            EndpointAggregator.ingestManifest(db.connection(), m, null);
            EndpointAggregator.ingestIntentTransitions(db.connection(), Collections.singletonList(t));

            // app.A への流入 = manifest 宣言 + Intent 起動 = 2 件
            List<EndpointsDao.Row> incoming = EndpointsDao.listIncoming(db.connection(), "app.A");
            assertEquals(2, incoming.size());
        }
    }
}
