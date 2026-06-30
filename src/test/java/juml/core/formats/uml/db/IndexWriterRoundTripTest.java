// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link IndexWriter} → {@link IndexReader} の round-trip と、
 * 同じ path の再投入で CASCADE 削除が効くことを検証する。
 */
public class IndexWriterRoundTripTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File newDbFile() throws Exception {
        return new File(tmp.newFolder("cache"), "index.db");
    }

    @Test
    public void testHeaderRoundTrip() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        JavaClassInfo info = sampleClassWithEverything();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            new IndexWriter(db.connection()).upsertFile(
                    "src/main/java/com/example/Hello.java",
                    IndexWriter.KIND_JAVA, 1000L, 2048L,
                    ":app", "app",
                    Collections.singletonList(info), null);

            IndexReader reader = new IndexReader(db.connection());
            JavaClassInfo got = reader.headerOf("com.example.Hello");
            assertNotNull(got);
            assertEquals("Hello", got.getSimpleName());
            assertEquals("com.example", got.getPackageName());
            assertEquals(JavaClassInfo.Kind.CLASS, got.getKind());
            assertEquals("BaseHello", got.getSuperClass());
            assertEquals(Arrays.asList("Runnable", "java.io.Serializable"), got.getInterfaces());
            assertEquals(Arrays.asList("public", "final"), got.getModifiers());
            assertEquals(Arrays.asList("Deprecated", "SuppressWarnings"), got.getAnnotations());
            assertEquals("AAOS_SERVICE", got.getAaosCategory());
            assertEquals("Activity", got.getAndroidComponentType());
            assertEquals(Arrays.asList("Fragment"), got.getJetpackStereotypes());
            assertEquals(JavaClassInfo.Origin.SOURCE, got.getOrigin());
            assertEquals("greeting class", got.getComment());
            // Stage A としてロードされる
            assertFalse("loaded as Stage A header", got.isDetailed());
            assertEquals(Arrays.asList("java.util.List", "static java.lang.Math.PI"),
                    got.getImports());
        }
    }

    @Test
    public void testFieldAndMethodRoundTrip() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        JavaClassInfo info = sampleClassWithEverything();
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            new IndexWriter(db.connection()).upsertFile(
                    "src/main/java/com/example/Hello.java",
                    IndexWriter.KIND_JAVA, 1000L, 2048L, ":app", "app",
                    Collections.singletonList(info), null);

            IndexReader reader = new IndexReader(db.connection());

            List<JavaFieldInfo> fields = reader.fieldsOf("com.example.Hello");
            assertEquals(2, fields.size());
            JavaFieldInfo f0 = fields.get(0);
            assertEquals("CONSTANT", f0.getName());
            assertEquals("int", f0.getType());
            assertEquals(Visibility.PUBLIC, f0.getVisibility());
            assertTrue(f0.isStatic());
            assertTrue(f0.isFinal());
            assertEquals(Arrays.asList("MyAnno"), f0.getAnnotations());

            JavaFieldInfo f1 = fields.get(1);
            assertEquals("name", f1.getName());
            assertEquals("String", f1.getType());
            assertEquals(Visibility.PRIVATE, f1.getVisibility());
            assertFalse(f1.isStatic());
            assertFalse(f1.isFinal());

            List<JavaMethodInfo> methods = reader.methodsOf("com.example.Hello");
            assertEquals(2, methods.size());
            JavaMethodInfo m0 = methods.get(0);
            assertEquals("Hello", m0.getName());
            assertTrue("constructor flag preserved", m0.isConstructor());
            assertEquals(Visibility.PUBLIC, m0.getVisibility());
            assertEquals(Arrays.asList("String"), m0.getParameterTypes());
            assertEquals(Arrays.asList("name"), m0.getParameterNames());

            JavaMethodInfo m1 = methods.get(1);
            assertEquals("greet", m1.getName());
            assertEquals("String", m1.getReturnType());
            assertEquals(Visibility.PUBLIC, m1.getVisibility());
            assertTrue(m1.isStatic());
            assertEquals(Arrays.asList("Override"), m1.getAnnotations());
            assertEquals(Arrays.asList("String", "int"), m1.getParameterTypes());
            assertEquals(Arrays.asList("salutation", "times"), m1.getParameterNames());
            assertEquals(Arrays.asList("IllegalArgumentException"), m1.getThrowsTypes());
            assertEquals("repeats salutation", m1.getComment());
        }
    }

    @Test
    public void testClassIndexFromDbRehydratesSourceAndModule() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            new IndexWriter(db.connection()).upsertFile(
                    "src/main/java/com/example/Hello.java",
                    IndexWriter.KIND_JAVA, 1L, 1L, ":app", "app",
                    Collections.singletonList(sampleClassWithEverything()), null);
            new IndexWriter(db.connection()).upsertFile(
                    "lib/src/main/java/com/example/lib/Util.java",
                    IndexWriter.KIND_JAVA, 1L, 1L, ":lib", "lib",
                    Collections.singletonList(simpleClass("com.example.lib", "Util")), null);

            IndexReader reader = new IndexReader(db.connection());
            ClassIndex idx = reader.loadStageAClassIndex(projectRoot);

            assertEquals(2, idx.size());
            assertTrue(idx.qualifiedNames().contains("com.example.Hello"));
            assertTrue(idx.qualifiedNames().contains("com.example.lib.Util"));
            assertEquals(":app", idx.module("com.example.Hello").orElse(null));
            assertEquals(":lib", idx.module("com.example.lib.Util").orElse(null));

            File helloSource = idx.source("com.example.Hello").orElse(null);
            assertNotNull(helloSource);
            assertEquals(new File(projectRoot, "src/main/java/com/example/Hello.java").getAbsolutePath(),
                    helloSource.getAbsolutePath());
        }
    }

    @Test
    public void testReupsertFileReplacesClassesViaCascade() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            IndexWriter writer = new IndexWriter(db.connection());
            // 初回: Hello が宣言されている
            writer.upsertFile(
                    "src/Hello.java", IndexWriter.KIND_JAVA, 1L, 1L,
                    null, null,
                    Collections.singletonList(simpleClass("com.example", "Hello")), null);
            assertEquals(1, new IndexReader(db.connection()).classCount());

            // 同じファイルが Bye に書き換わった想定で再投入
            writer.upsertFile(
                    "src/Hello.java", IndexWriter.KIND_JAVA, 2L, 2L,
                    null, null,
                    Collections.singletonList(simpleClass("com.example", "Bye")), null);

            IndexReader reader = new IndexReader(db.connection());
            assertEquals("old class must be CASCADE-deleted", 1, reader.classCount());
            assertNull(reader.headerOf("com.example.Hello"));
            assertNotNull(reader.headerOf("com.example.Bye"));

            // 子テーブルも消えていること (空けば 0 件)
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT COUNT(*) FROM fields");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    public void testEmptyClassesIsAllowed() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            new IndexWriter(db.connection()).upsertFile(
                    "src/Empty.java", IndexWriter.KIND_JAVA, 1L, 0L,
                    null, null, Collections.<JavaClassInfo>emptyList(), "no top-level class");

            IndexReader reader = new IndexReader(db.connection());
            assertEquals(0, reader.classCount());
            assertEquals(1, reader.filesByKind(IndexWriter.KIND_JAVA).size());
        }
    }

    @Test
    public void testModulesAreUpserted() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            IndexWriter writer = new IndexWriter(db.connection());
            writer.upsertFile("a.java", IndexWriter.KIND_JAVA, 1L, 1L, ":app", "app",
                    Collections.singletonList(simpleClass("p", "A")), null);
            writer.upsertFile("b.java", IndexWriter.KIND_JAVA, 1L, 1L, ":app", "app",
                    Collections.singletonList(simpleClass("p", "B")), null);

            IndexReader reader = new IndexReader(db.connection());
            assertEquals(1, reader.modules().size());
            assertEquals("app", reader.modules().get(":app"));
        }
    }

    // ---- fixtures ----

    private static JavaClassInfo sampleClassWithEverything() {
        JavaClassInfo c = new JavaClassInfo();
        c.setSimpleName("Hello");
        c.setPackageName("com.example");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setSuperClass("BaseHello");
        c.getInterfaces().add("Runnable");
        c.getInterfaces().add("java.io.Serializable");
        c.getModifiers().add("public");
        c.getModifiers().add("final");
        c.getAnnotations().add("Deprecated");
        c.getAnnotations().add("SuppressWarnings");
        c.setAaosCategory("AAOS_SERVICE");
        c.setAndroidComponentType("Activity");
        c.getJetpackStereotypes().add("Fragment");
        c.setOrigin(JavaClassInfo.Origin.SOURCE);
        c.setComment("greeting class");
        c.getImports().add("java.util.List");
        c.getImports().add("static java.lang.Math.PI");

        JavaFieldInfo f0 = new JavaFieldInfo();
        f0.setName("CONSTANT");
        f0.setType("int");
        f0.setVisibility(Visibility.PUBLIC);
        f0.setStatic(true);
        f0.setFinal(true);
        f0.getAnnotations().add("MyAnno");
        c.getFields().add(f0);

        JavaFieldInfo f1 = new JavaFieldInfo();
        f1.setName("name");
        f1.setType("String");
        f1.setVisibility(Visibility.PRIVATE);
        c.getFields().add(f1);

        JavaMethodInfo ctor = new JavaMethodInfo();
        ctor.setName("Hello");
        ctor.setVisibility(Visibility.PUBLIC);
        ctor.setConstructor(true);
        ctor.getParameterTypes().add("String");
        ctor.getParameterNames().add("name");
        c.getMethods().add(ctor);

        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("greet");
        m.setReturnType("String");
        m.setVisibility(Visibility.PUBLIC);
        m.setStatic(true);
        m.getAnnotations().add("Override");
        m.getParameterTypes().add("String");
        m.getParameterTypes().add("int");
        m.getParameterNames().add("salutation");
        m.getParameterNames().add("times");
        m.getThrowsTypes().add("IllegalArgumentException");
        m.setComment("repeats salutation");
        c.getMethods().add(m);
        return c;
    }

    private static JavaClassInfo simpleClass(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setOrigin(JavaClassInfo.Origin.SOURCE);
        return c;
    }
}
