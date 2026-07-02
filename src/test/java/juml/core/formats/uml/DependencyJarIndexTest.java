// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;
import juml.core.formats.android.GradleDependency;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DependencyJarIndex} と {@link ExternalClassReader} のテスト。
 *
 * <p>テスト用に同梱した {@code src/test/resources/jars/sample-lib-1.0.jar} を
 * Gradle cache 風のディレクトリレイアウトに一時配置して、resolve / missing 判定が
 * 正しく動くことを担保する。</p>
 */
public class DependencyJarIndexTest {

    @Test
    public void testExternalClassReaderExtractsHeader() throws IOException {
        try (InputStream in = openResource("/jars/sample-lib-1.0.jar")) {
            // sample-lib-1.0.jar から Foo.class だけ取り出して ASM で解析
            byte[] fooBytes = readEntry(in, "com/example/Foo.class");
            assertNotNull(fooBytes);
            try (InputStream fooIn = new java.io.ByteArrayInputStream(fooBytes)) {
                JavaClassInfo info = ExternalClassReader.readHeader(fooIn, "test.jar");
                assertEquals("com.example", info.getPackageName());
                assertEquals("Foo", info.getSimpleName());
                assertEquals(JavaClassInfo.Origin.EXTERNAL_JAR, info.getOrigin());
                assertEquals("com.example.Bar", info.getSuperClass());
                // 公開 field "counter" (int) + 公開メソッド "run" + constructor
                boolean hasCounter = info.getFields().stream()
                        .anyMatch(f -> "counter".equals(f.getName()));
                assertTrue("Foo.counter field should be present", hasCounter);
                boolean hasRun = info.getMethods().stream()
                        .anyMatch(m -> "run".equals(m.getName()));
                assertTrue("Foo.run method should be present", hasRun);
            }
        }
    }

    @Test
    public void testResolveFromMockedGradleCache() throws IOException {
        Path tmp = Files.createTempDirectory("juml-deptest-");
        try {
            // ~/.gradle/caches/modules-2/files-2.1/com.example/sample-lib/1.0/<hash>/sample-lib-1.0.jar
            Path cacheJar = tmp.resolve(".gradle/caches/modules-2/files-2.1/"
                    + "com.example/sample-lib/1.0/h4sh/sample-lib-1.0.jar");
            Files.createDirectories(cacheJar.getParent());
            try (InputStream in = openResource("/jars/sample-lib-1.0.jar")) {
                Files.copy(in, cacheJar);
            }
            // user.home を一時ディレクトリへ向け、DependencyJarIndex.build を呼ぶ
            String origHome = System.getProperty("user.home");
            System.setProperty("user.home", tmp.toString());
            try {
                List<GradleDependency> deps = Collections.singletonList(
                        new GradleDependency("implementation", "com.example:sample-lib:1.0"));
                DependencyJarIndex idx = DependencyJarIndex.build(deps, null);
                assertEquals(2, idx.indexedClassCount());
                assertTrue("missing should be empty", idx.getMissingArtifacts().isEmpty());

                Optional<JavaClassInfo> foo = idx.resolve("Foo");
                assertTrue("simple-name resolution: Foo", foo.isPresent());
                assertEquals("com.example.Foo", foo.get().getPackageName() + "."
                        + foo.get().getSimpleName());
                assertEquals(JavaClassInfo.Origin.EXTERNAL_JAR, foo.get().getOrigin());

                Optional<JavaClassInfo> bar = idx.resolve("com.example.Bar");
                assertTrue("FQN resolution: Bar", bar.isPresent());
                assertEquals("Bar", bar.get().getSimpleName());
            } finally {
                System.setProperty("user.home", origHome);
            }
        } finally {
            // ベストエフォートでクリーンアップ
            deleteRecursively(tmp);
        }
    }

    @Test
    public void testMissingArtifactRecorded() {
        // 存在しない座標 → missingArtifacts に登録される
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("juml-deptest-empty-");
            String origHome = System.getProperty("user.home");
            System.setProperty("user.home", tmp.toString());
            try {
                List<GradleDependency> deps = Collections.singletonList(
                        new GradleDependency("implementation",
                                "com.nonexistent:fake:1.0"));
                DependencyJarIndex idx = DependencyJarIndex.build(deps, null);
                assertEquals(0, idx.indexedClassCount());
                assertFalse(idx.getMissingArtifacts().isEmpty());
                assertTrue(idx.getMissingArtifacts()
                        .contains("com.nonexistent:fake:1.0"));
                assertTrue(idx.isDeclaredButMissing("AnythingMissing"));
                assertFalse(idx.resolve("AnythingMissing").isPresent());
            } finally {
                System.setProperty("user.home", origHome);
            }
        } catch (IOException ex) {
            throw new AssertionError(ex);
        } finally {
            if (tmp != null) {
                deleteRecursively(tmp);
            }
        }
    }

    @Test
    public void testIndexLocalArtifact() throws IOException {
        // リポジトリ同梱 JAR (Gradle cache 外) を直接インデックスできること
        Path tmp = Files.createTempDirectory("juml-localjar-");
        try {
            Path localJar = tmp.resolve("libs/sample-lib-1.0.jar");
            Files.createDirectories(localJar.getParent());
            try (InputStream in = openResource("/jars/sample-lib-1.0.jar")) {
                Files.copy(in, localJar);
            }
            DependencyJarIndex idx = new DependencyJarIndex();
            assertTrue(idx.indexLocalArtifact(localJar, null));
            assertEquals(2, idx.indexedClassCount());
            assertTrue(idx.getMissingArtifacts().isEmpty());
            Optional<JavaClassInfo> foo = idx.resolve("com.example.Foo");
            assertTrue("local jar class should resolve", foo.isPresent());
            assertEquals(JavaClassInfo.Origin.EXTERNAL_JAR, foo.get().getOrigin());
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    public void testIndexLocalArtifactMissingFile() {
        // 実体の無いローカル JAR は missing として記録される
        DependencyJarIndex idx = new DependencyJarIndex();
        Path bogus = Path.of("no-such-dir", "no-such.jar");
        assertFalse(idx.indexLocalArtifact(bogus, null));
        assertEquals(0, idx.indexedClassCount());
        assertFalse(idx.getMissingArtifacts().isEmpty());
    }

    @Test
    public void testMissingPlaceholderHasMissingOrigin() {
        DependencyJarIndex idx = new DependencyJarIndex();
        JavaClassInfo p = idx.missingPlaceholder("com.foo.Bar");
        assertEquals(JavaClassInfo.Origin.MISSING_JAR, p.getOrigin());
        assertEquals("com.foo", p.getPackageName());
        assertEquals("Bar", p.getSimpleName());
    }

    @Test
    public void testProjectReferenceDependencyIsIgnored() {
        // project(':app') 形式は外部 JAR ではないのでスキップされる
        List<GradleDependency> deps = Collections.singletonList(
                new GradleDependency("implementation", "project(':app')"));
        DependencyJarIndex idx = DependencyJarIndex.build(deps, null);
        assertTrue(idx.getMissingArtifacts().isEmpty());
        assertEquals(0, idx.indexedClassCount());
    }

    // --- helpers ---

    private static InputStream openResource(String path) throws IOException {
        InputStream in = DependencyJarIndexTest.class.getResourceAsStream(path);
        if (in == null) {
            throw new IOException("test resource missing: " + path);
        }
        return in;
    }

    private static byte[] readEntry(InputStream jarStream, String entryName) throws IOException {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(jarStream)) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (entryName.equals(e.getName())) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] chunk = new byte[1024];
                    int n;
                    while ((n = zip.read(chunk)) > 0) {
                        out.write(chunk, 0, n);
                    }
                    return out.toByteArray();
                }
            }
        }
        return null;
    }

    private static void deleteRecursively(Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException ignore) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignore) {
            // best-effort cleanup
        }
        // unused param to silence unused-import warning on Paths
        Paths.get(".");
    }
}
