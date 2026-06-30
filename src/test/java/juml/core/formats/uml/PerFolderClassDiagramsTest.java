// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PerFolderClassDiagrams} のユニットテスト。
 *
 * <p>疑似的なプロジェクトツリーを {@link TemporaryFolder} 上に組み立て、
 * 出力ディレクトリにフォルダ階層を保ったまま {@code classes.puml} と
 * {@code classes.svg} が書き出されることを検証する。</p>
 */
public class PerFolderClassDiagramsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File f, String content) throws IOException {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void generatesOneDiagramPerFolderPreservingHierarchy() throws IOException {
        File project = tmp.newFolder("project");
        writeFile(new File(project, "com/example/ui/Foo.java"),
                "package com.example.ui; public class Foo {}");
        writeFile(new File(project, "com/example/data/Bar.java"),
                "package com.example.data; public class Bar {}");
        writeFile(new File(project, "com/example/data/Baz.java"),
                "package com.example.data; public class Baz {}");

        File out = tmp.newFolder("out");
        PerFolderClassDiagrams.Result result = PerFolderClassDiagrams.generate(
                project, out, null, null, false, null, ErrorListener.silent());

        assertEquals(2, result.getFolderCount());
        assertEquals(3, result.getClassCount());

        File uiPuml = new File(out, "com/example/ui/" + PerFolderClassDiagrams.PUML_NAME);
        File dataPuml = new File(out, "com/example/data/" + PerFolderClassDiagrams.PUML_NAME);
        assertTrue("ui classes.puml missing", uiPuml.isFile());
        assertTrue("data classes.puml missing", dataPuml.isFile());

        String uiContent = new String(Files.readAllBytes(uiPuml.toPath()),
                StandardCharsets.UTF_8);
        String dataContent = new String(Files.readAllBytes(dataPuml.toPath()),
                StandardCharsets.UTF_8);
        assertTrue("ui diagram should mention Foo", uiContent.contains("Foo"));
        assertFalse("ui diagram should not include Bar from another folder",
                uiContent.contains("class Bar") || uiContent.contains("class \"Bar\""));
        assertTrue("data diagram should mention Bar", dataContent.contains("Bar"));
        assertTrue("data diagram should mention Baz", dataContent.contains("Baz"));
    }

    @Test
    public void skipsFoldersWithoutSourceFiles() throws IOException {
        File project = tmp.newFolder("project");
        // No source files anywhere.
        new File(project, "com/example/empty").mkdirs();

        File out = tmp.newFolder("out");
        PerFolderClassDiagrams.Result result = PerFolderClassDiagrams.generate(
                project, out, null, null, false, null, ErrorListener.silent());

        assertEquals(0, result.getFolderCount());
        assertEquals(0, result.getClassCount());
        assertFalse(new File(out, "com/example/empty/" + PerFolderClassDiagrams.PUML_NAME)
                .exists());
    }

    @Test
    public void emitsAtRootWhenSourcesArePlacedDirectlyAtProjectRoot() throws IOException {
        File project = tmp.newFolder("project");
        writeFile(new File(project, "Standalone.java"),
                "public class Standalone {}");

        File out = tmp.newFolder("out");
        PerFolderClassDiagrams.Result result = PerFolderClassDiagrams.generate(
                project, out, null, null, false, null, ErrorListener.silent());

        assertEquals(1, result.getFolderCount());
        assertEquals(1, result.getClassCount());
        assertTrue(new File(out, PerFolderClassDiagrams.PUML_NAME).isFile());
    }
}
