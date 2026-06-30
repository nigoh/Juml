// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ArchiveClassReader} と {@link UmlGenerator#extractFromArchive} のテスト。
 *
 * <p>同梱の {@code src/test/resources/jars/sample-lib-1.0.jar}
 * (com.example.Foo extends com.example.Bar / com.example.Bar) を一時ファイルへ
 * 展開して、任意パスの .jar から直接 ClassInfo を抽出できることを検証する。</p>
 */
public class ArchiveClassReaderTest {

    private static File copyJarToTemp() throws IOException {
        Path tmp = Files.createTempFile("juml-archive-", ".jar");
        try (InputStream in = ArchiveClassReaderTest.class
                .getResourceAsStream("/jars/sample-lib-1.0.jar")) {
            if (in == null) {
                throw new IOException("test resource missing: /jars/sample-lib-1.0.jar");
            }
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp.toFile();
    }

    @Test
    public void readsAllClassesFromJar() throws IOException {
        File jar = copyJarToTemp();
        List<JavaClassInfo> infos = ArchiveClassReader.readJar(jar, null);
        assertEquals(2, infos.size());
        boolean hasFoo = infos.stream().anyMatch(c -> "Foo".equals(c.getSimpleName()));
        boolean hasBar = infos.stream().anyMatch(c -> "Bar".equals(c.getSimpleName()));
        assertTrue("Foo present", hasFoo);
        assertTrue("Bar present", hasBar);
        for (JavaClassInfo c : infos) {
            assertEquals(JavaClassInfo.Origin.EXTERNAL_JAR, c.getOrigin());
            assertEquals("com.example", c.getPackageName());
        }
    }

    @Test
    public void fooKeepsSuperclassFqn() throws IOException {
        File jar = copyJarToTemp();
        JavaClassInfo foo = ArchiveClassReader.readJar(jar, null).stream()
                .filter(c -> "Foo".equals(c.getSimpleName()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("com.example.Bar", foo.getSuperClass());
    }

    @Test
    public void readAutoDetectsJarExtension() throws IOException {
        File jar = copyJarToTemp();
        List<JavaClassInfo> infos = ArchiveClassReader.read(jar, null);
        assertEquals(2, infos.size());
    }

    @Test
    public void isArchiveInputDetectsJar() throws IOException {
        File jar = copyJarToTemp();
        assertTrue(ArchiveClassReader.isArchiveInput(jar));
    }

    @Test
    public void extractFromArchiveEndToEnd() throws IOException {
        File jar = copyJarToTemp();
        List<JavaClassInfo> infos = UmlGenerator.extractFromArchive(jar, null);
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("Foo"));
        assertTrue(puml.contains("Bar"));
    }

    @Test
    public void readDirectoryCollectsArchives() throws IOException {
        File jar = copyJarToTemp();
        Path dir = Files.createTempDirectory("juml-archive-dir-");
        Path inside = dir.resolve("sample.jar");
        Files.copy(jar.toPath(), inside);
        try {
            List<JavaClassInfo> infos = ArchiveClassReader.read(dir.toFile(), null);
            assertFalse(infos.isEmpty());
            assertEquals(2, infos.size());
        } finally {
            Files.deleteIfExists(inside);
            Files.deleteIfExists(dir);
        }
    }
}
