// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.CancelToken;
import juml.util.ErrorListener;
import juml.util.ProgressListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * リポジトリに同梱されたローカル JAR ({@code libs/*.jar} +
 * {@code files(...)} / {@code fileTree(...)} 宣言) が
 * {@link DependencyJarIndex} へ索引されることの統合テスト。
 */
public class UmlGeneratorLocalJarTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private static void copyResource(String resource, File dest) throws IOException {
        try (InputStream in = UmlGeneratorLocalJarTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("test resource missing: " + resource);
            }
            Files.copy(in, dest.toPath());
        }
    }

    /** files('libs/x.jar') 宣言のあるプロジェクトでローカル JAR のクラスが解決できる。 */
    @Test
    public void testFilesDeclarationResolvesLocalJar() throws IOException {
        File root = createProject("dependencies {\n"
                + "  implementation files('libs/sample-lib-1.0.jar')\n"
                + "}\n");
        UmlGenerator.ProjectParseResult r = parse(root);
        assertTrue("local jar class resolves via files()",
                r.getDependencyIndex().resolve("com.example.Foo").isPresent());
        assertEquals(JavaClassInfo.Origin.EXTERNAL_JAR,
                r.getDependencyIndex().resolve("com.example.Foo").get().getOrigin());
        assertTrue("missing should be empty",
                r.getDependencyIndex().getMissingArtifacts().isEmpty());
    }

    /** fileTree(dir: 'libs') 宣言でも同様に解決できる。 */
    @Test
    public void testFileTreeDeclarationResolvesLocalJar() throws IOException {
        File root = createProject("dependencies {\n"
                + "  implementation fileTree(dir: 'libs', include: ['*.jar'])\n"
                + "}\n");
        UmlGenerator.ProjectParseResult r = parse(root);
        assertTrue("local jar class resolves via fileTree()",
                r.getDependencyIndex().resolve("com.example.Bar").isPresent());
    }

    /** 宣言が無くても慣習の libs/ ディレクトリは保険として索引される。 */
    @Test
    public void testConventionalLibsDirIsIndexedWithoutDeclaration() throws IOException {
        File root = createProject("dependencies {\n"
                + "}\n");
        UmlGenerator.ProjectParseResult r = parse(root);
        assertTrue("libs/ jar indexed without declaration",
                r.getDependencyIndex().resolve("com.example.Foo").isPresent());
    }

    /** 宣言された files(...) の実体が無ければ missing として記録される。 */
    @Test
    public void testMissingLocalJarRecorded() throws IOException {
        File root = createProject("dependencies {\n"
                + "  implementation files('vendor/ghost.jar')\n"
                + "}\n");
        UmlGenerator.ProjectParseResult r = parse(root);
        boolean recorded = r.getDependencyIndex().getMissingArtifacts().stream()
                .anyMatch(s -> s.endsWith("ghost.jar"));
        assertTrue("missing local jar should be recorded", recorded);
    }

    // --- helpers ---

    /** build.gradle + libs/sample-lib-1.0.jar + Foo を継承する 1 クラスの最小プロジェクト。 */
    private File createProject(String dependenciesBlock) throws IOException {
        File root = tmp.newFolder("LocalJarProj");
        writeFile(new File(root, "build.gradle"),
                "plugins { id 'java' }\n" + dependenciesBlock);
        File libs = new File(root, "libs");
        assertTrue(libs.mkdirs());
        copyResource("/jars/sample-lib-1.0.jar",
                new File(libs, "sample-lib-1.0.jar"));
        File srcDir = new File(root, "src/main/java/com/demo");
        assertTrue(srcDir.mkdirs());
        writeFile(new File(srcDir, "App.java"),
                "package com.demo;\n"
                        + "import com.example.Foo;\n"
                        + "public class App extends Foo {\n"
                        + "  public void go() { new Foo().run(); }\n"
                        + "}\n");
        return root;
    }

    private static UmlGenerator.ProjectParseResult parse(File root) throws IOException {
        return UmlGenerator.extractFromProjectDetailed(
                root, null, ErrorListener.silent(), ProgressListener.silent(),
                CancelToken.NONE, false, UmlGenerator.ParseMode.FULL);
    }
}
