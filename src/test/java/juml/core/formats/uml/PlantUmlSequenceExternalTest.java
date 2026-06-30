// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;
import juml.core.formats.android.GradleDependency;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * 依存 JAR 由来クラスのシーケンス図描画テスト。
 * EXTERNAL_JAR / MISSING_JAR の origin が participant 装飾に反映されることを確認する。
 */
public class PlantUmlSequenceExternalTest {

    @Test
    public void testExternalParticipantHasStereotype() throws IOException {
        Path tmp = Files.createTempDirectory("juml-seqext-");
        try {
            // Gradle cache 風に sample-lib-1.0.jar を配置
            Path cacheJar = tmp.resolve(".gradle/caches/modules-2/files-2.1/"
                    + "com.example/sample-lib/1.0/h/sample-lib-1.0.jar");
            Files.createDirectories(cacheJar.getParent());
            try (InputStream in = getClass().getResourceAsStream("/jars/sample-lib-1.0.jar")) {
                Files.copy(in, cacheJar);
            }
            String origHome = System.getProperty("user.home");
            System.setProperty("user.home", tmp.toString());
            try {
                DependencyJarIndex idx = DependencyJarIndex.build(
                        Collections.singletonList(new GradleDependency(
                                "implementation", "com.example:sample-lib:1.0")),
                        null);

                String src = ""
                        + "class App {\n"
                        + "  private Foo foo;\n"
                        + "  void start() { foo.run(); }\n"
                        + "}";
                List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
                PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
                opts.dependencyIndex = idx;

                String diagram = PlantUmlSequenceDiagram.generate(
                        classes, "App", "start", opts);
                assertTrue("Foo should be marked <<external>>: \n" + diagram,
                        diagram.contains("participant \"Foo\" <<external>>"));
            } finally {
                System.setProperty("user.home", origHome);
            }
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    public void testMissingParticipantHasMissingMarker() throws IOException {
        Path tmp = Files.createTempDirectory("juml-seqmiss-");
        try {
            String origHome = System.getProperty("user.home");
            System.setProperty("user.home", tmp.toString());
            try {
                // 依存は宣言されているが JAR は配置していない
                DependencyJarIndex idx = DependencyJarIndex.build(
                        Collections.singletonList(new GradleDependency(
                                "implementation", "com.nonexistent:fake:1.0")),
                        null);

                String src = ""
                        + "class App {\n"
                        + "  private FakeService svc;\n"
                        + "  void start() { svc.invoke(); }\n"
                        + "}";
                List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
                PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
                opts.dependencyIndex = idx;

                String diagram = PlantUmlSequenceDiagram.generate(
                        classes, "App", "start", opts);
                assertTrue("FakeService should be marked <<missing>>: \n" + diagram,
                        diagram.contains("participant \"FakeService\" <<missing>>"));
                assertTrue("FakeService missing should use LightYellow: \n" + diagram,
                        diagram.contains("<<missing>> #LightYellow"));
            } finally {
                System.setProperty("user.home", origHome);
            }
        } finally {
            deleteRecursively(tmp);
        }
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
    }
}
