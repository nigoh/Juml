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


    /**
     * 同梱サンプル JAR に「壊れた .class」と「multi-release の複製」を足した JAR を組む。
     */
    private static File jarWithBadEntryAndVersionedCopy() throws IOException {
        java.util.Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                Files.newInputStream(copyJarToTemp().toPath()))) {
            java.util.zip.ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    entries.put(e.getName(), zip.readAllBytes());
                }
            }
        }
        Path out = Files.createTempFile("juml-archive-mixed-", ".jar");
        out.toFile().deleteOnExit();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                Files.newOutputStream(out))) {
            // 壊れたクラスを先頭に置く (中断すると以降が 1 つも読めなくなるため)。
            zos.putNextEntry(new java.util.zip.ZipEntry("com/example/Broken.class"));
            zos.write("this is not a class file".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
            for (java.util.Map.Entry<String, byte[]> en : entries.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(en.getKey()));
                zos.write(en.getValue());
                zos.closeEntry();
                if (en.getKey().endsWith(".class")) {
                    zos.putNextEntry(new java.util.zip.ZipEntry(
                            "META-INF/versions/9/" + en.getKey()));
                    zos.write(en.getValue());
                    zos.closeEntry();
                }
            }
        }
        return out.toFile();
    }

    /**
     * bug-hunt R4 で発見: 1 つの不良 .class で読み取り全体が中断し、以降のクラスが失われて
     * いた。また multi-release JAR の版ごとの複製をそのまま読み、同じクラスが重複していた。
     */
    @Test
    public void skipsBrokenEntriesAndVersionedDuplicates() throws IOException {
        List<String> reported = new java.util.ArrayList<>();
        List<JavaClassInfo> infos = ArchiveClassReader.readJar(
                jarWithBadEntryAndVersionedCopy(),
                (code, source, line, message) -> reported.add(String.valueOf(message)));
        assertEquals("壊れたエントリの後ろのクラスも読めること", 2, infos.size());
        assertTrue("Foo present", infos.stream().anyMatch(c -> "Foo".equals(c.getSimpleName())));
        assertTrue("Bar present", infos.stream().anyMatch(c -> "Bar".equals(c.getSimpleName())));
        assertTrue("不良エントリは通知されること: " + reported,
                reported.stream().anyMatch(m -> m.contains("Broken.class")));
    }
}
