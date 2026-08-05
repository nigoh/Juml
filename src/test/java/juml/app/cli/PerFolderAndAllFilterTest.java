// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.util.ErrorListener;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * クラス図の CLI スコープフィルタが {@code -c} 以外のコマンドでも効くことの回帰テスト。
 *
 * <p>{@code applyCliClassFilters} は素の {@code -c} からしか呼ばれていなかったため、
 * {@code -c --per-folder} では {@code --exclude-package} も {@code --exclude-name-regex} も
 * <b>黙って何もしなかった</b>。さらに走査オプションを {@code null} 固定で渡していたため
 * {@code --include-tests} も無視され、テストソースのフォルダが 1 枚も出力されなかった。</p>
 *
 * <p>実バイナリで計測した修正前の挙動: {@code --exclude-package com.other} を付けても
 * {@code com/other/classes.puml} が出力され、{@code --include-tests} を付けても
 * {@code src/test/...} は出力されなかった。</p>
 */
public class PerFolderAndAllFilterTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private File project;

    @Before
    public void makeProject() throws Exception {
        project = tmp.newFolder("proj");
        write("src/main/java/com/example/Alpha.java",
                "package com.example;\npublic class Alpha { public void go() { } }\n");
        write("src/main/java/com/other/Gamma.java",
                "package com.other;\npublic class Gamma { public void x() { } }\n");
        write("src/test/java/com/example/AlphaTest.java",
                "package com.example;\npublic class AlphaTest { public void t() { } }\n");
    }

    private void write(String relative, String content) throws Exception {
        Path p = project.toPath().resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    /** 出力ディレクトリ配下の *.puml をプロジェクト相対パスで列挙する。 */
    private static List<String> pumlPaths(File outDir) throws Exception {
        List<String> out = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(outDir.toPath())) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (p.toString().endsWith(".puml")) {
                    out.add(outDir.toPath().relativize(p).toString().replace('\\', '/'));
                }
            }
        }
        return out;
    }

    private UmlOverrides overridesExcludingOtherPackage() {
        UmlOverrides o = new UmlOverrides();
        o.excludedPackages.add("com.other");
        return o;
    }

    private File runPerFolder(UmlOverrides overrides, boolean includeTests) throws Exception {
        File outDir = tmp.newFolder("out-" + System.nanoTime());
        CliContext ctx = new CliContext(project, outDir, ErrorListener.silent(),
                Boolean.FALSE, false, overrides, includeTests);
        UmlCommands.handleClassDiagramsPerFolder(ctx);
        return outDir;
    }

    @Test
    public void perFolderHonoursExcludePackage() throws Exception {
        List<String> before = pumlPaths(runPerFolder(new UmlOverrides(), false));
        assertTrue("前提: 除外しなければ com/other も出ること: " + before,
                before.stream().anyMatch(p -> p.contains("com/other")));

        List<String> after = pumlPaths(runPerFolder(overridesExcludingOtherPackage(), false));
        assertFalse("--exclude-package したパッケージのフォルダは出ないこと: " + after,
                after.stream().anyMatch(p -> p.contains("com/other")));
        assertTrue("除外していないパッケージは残ること: " + after,
                after.stream().anyMatch(p -> p.contains("com/example")));
    }

    @Test
    public void perFolderHonoursExcludeNameRegex() throws Exception {
        UmlOverrides o = new UmlOverrides();
        o.excludeNameRegex = "Gamma";
        File outDir = runPerFolder(o, false);

        for (String rel : pumlPaths(outDir)) {
            String text = new String(Files.readAllBytes(new File(outDir, rel).toPath()),
                    StandardCharsets.UTF_8);
            assertFalse("--exclude-name-regex に一致するクラスは描かれないこと: " + rel,
                    text.contains("Gamma"));
        }
    }

    @Test
    public void perFolderHonoursIncludeTests() throws Exception {
        List<String> without = pumlPaths(runPerFolder(new UmlOverrides(), false));
        assertFalse("既定ではテストソースは出ないこと: " + without,
                without.stream().anyMatch(p -> p.contains("src/test")));

        List<String> with = pumlPaths(runPerFolder(new UmlOverrides(), true));
        assertTrue("--include-tests でテストソースのフォルダも出ること: " + with,
                with.stream().anyMatch(p -> p.contains("src/test")));
    }
}
