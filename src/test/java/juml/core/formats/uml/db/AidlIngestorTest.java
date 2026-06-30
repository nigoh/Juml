// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.aaos.AidlBinding;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.db.dao.AidlBindingsDao;
import juml.core.formats.uml.db.dao.AidlInterfacesDao;
import juml.core.formats.uml.db.ingest.AidlIngestor;
import juml.core.formats.uml.db.ingest.ClassIngestor;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AidlIngestorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File newDbFile() throws Exception {
        return new File(tmp.newFolder("cache"), "index.db");
    }

    private static JavaClassInfo aidlIface(String pkg, String name, JavaMethodInfo... methods) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.AIDL_INTERFACE);
        c.setOrigin(JavaClassInfo.Origin.SOURCE);
        Collections.addAll(c.getMethods(), methods);
        return c;
    }

    private static JavaMethodInfo method(String name, String returnType, boolean oneway,
            String paramType, String paramName) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(name);
        m.setReturnType(returnType);
        if (oneway) {
            m.getAnnotations().add("oneway");
        }
        if (paramType != null) {
            m.getParameterTypes().add(paramType);
        }
        if (paramName != null) {
            m.getParameterNames().add(paramName);
        }
        return m;
    }

    @Test
    public void testIngestsInterfaceAndMethods() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            JavaClassInfo iface = aidlIface("car", "ICarFoo",
                    method("doSync", "int", false, "String", "name"),
                    method("notify", "void", true, "int", "n"));

            int written = AidlIngestor.ingest(db.connection(),
                    Collections.singletonList(iface), null);
            assertEquals(1, written);

            assertEquals(1, AidlInterfacesDao.countInterfaces(db.connection()));
            List<AidlInterfacesDao.InterfaceRow> all =
                    AidlInterfacesDao.listAllInterfaces(db.connection());
            assertEquals("car", all.get(0).packageName);
            assertEquals("ICarFoo", all.get(0).simpleName);

            List<AidlInterfacesDao.MethodRow> methods =
                    AidlInterfacesDao.listMethods(db.connection(), all.get(0).id);
            assertEquals(2, methods.size());
            assertEquals("doSync", methods.get(0).name);
            assertEquals("int", methods.get(0).returnType);
            assertEquals("String name", methods.get(0).paramSig);
            assertEquals(false, methods.get(0).oneway);

            assertEquals("notify", methods.get(1).name);
            assertEquals(true, methods.get(1).oneway);
        }
    }

    @Test
    public void testInterfaceClassIdResolvedFromClassesTable() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            // 先に classes 行を作っておく (実プロジェクトでは ClassIngestor 経由)
            JavaClassInfo iface = aidlIface("car", "ICarFoo");
            long classId = ClassIngestor.ingest(db.connection(), iface, null);

            AidlIngestor.ingestInterfaces(db.connection(),
                    Collections.singletonList(iface));

            AidlInterfacesDao.InterfaceRow row =
                    AidlInterfacesDao.listAllInterfaces(db.connection()).get(0);
            assertNotNull(row.classId);
            assertEquals(Long.valueOf(classId), row.classId);
        }
    }

    @Test
    public void testNonAidlClassesAreSkipped() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            JavaClassInfo plain = new JavaClassInfo();
            plain.setPackageName("p");
            plain.setSimpleName("PlainClass");
            plain.setKind(JavaClassInfo.Kind.CLASS);
            JavaClassInfo iface = aidlIface("p", "IBar");

            int written = AidlIngestor.ingestInterfaces(db.connection(),
                    Arrays.asList(plain, iface));
            assertEquals(1, written);
        }
    }

    @Test
    public void testBindingsAreUpserted() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            Map<String, List<AidlBinding>> bindings = new HashMap<>();
            bindings.put("car.ICarFoo", Arrays.asList(
                    new AidlBinding("car.ICarFoo", "car.impl.CarFooImpl", "CarFooImpl.java"),
                    new AidlBinding("car.ICarFoo", "car.impl.CarFooDebugImpl", "CarFooDebugImpl.java")
            ));
            bindings.put("car.ICarBar", Collections.singletonList(
                    new AidlBinding("car.ICarBar", "car.impl.CarBarImpl", "CarBarImpl.java")
            ));

            int written = AidlIngestor.ingestBindings(db.connection(), bindings);
            assertEquals(3, written);

            // 同じ binding を再度流しても UNIQUE で 1 件のまま
            AidlIngestor.ingestBindings(db.connection(), bindings);
            assertEquals("duplicate bindings are ignored", 3,
                    AidlBindingsDao.count(db.connection()));

            assertEquals(2, AidlBindingsDao.implementersOf(db.connection(), "car.ICarFoo").size());
            assertTrue(AidlBindingsDao.implementersOf(db.connection(), "car.ICarFoo")
                    .contains("car.impl.CarFooImpl"));
            assertEquals(Collections.singletonList("car.ICarBar"),
                    AidlBindingsDao.aidlsOf(db.connection(), "car.impl.CarBarImpl"));
        }
    }

    @Test
    public void testCombinedIngest() throws Exception {
        File dbFile = newDbFile();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "test")) {
            JavaClassInfo iface = aidlIface("car", "ICarFoo",
                    method("doSync", "int", false, "String", "name"));
            Map<String, List<AidlBinding>> bindings = new HashMap<>();
            bindings.put("car.ICarFoo", Collections.singletonList(
                    new AidlBinding("car.ICarFoo", "car.impl.CarFooImpl", "CarFooImpl.java")));

            int written = AidlIngestor.ingest(db.connection(),
                    Collections.singletonList(iface), bindings);
            assertEquals(2, written);
            assertEquals(1, AidlInterfacesDao.countInterfaces(db.connection()));
            assertEquals(1, AidlBindingsDao.count(db.connection()));
        }
    }
}
