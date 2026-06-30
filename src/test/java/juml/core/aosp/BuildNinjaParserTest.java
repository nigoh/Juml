// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * build.ninja パーサのテスト。
 */
public class BuildNinjaParserTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private BuildNinjaGraph parse(String content) throws IOException {
        File dir = tmp.newFolder("soong");
        File ninja = new File(dir, "build.ninja");
        Files.write(ninja.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return new BuildNinjaParser().analyzeProject(tmp.getRoot());
    }

    @Test
    public void parsesRulesAndBuildStatements() throws IOException {
        String src = "rule cc.compile\n"
                + "  command = clang $in -o $out\n"
                + "  description = CC $out\n"
                + "rule javac\n"
                + "  command = javac $in\n"
                + "\n"
                + "build out/foo.o: cc.compile src/foo.cpp\n"
                + "build out/bar.o: cc.compile src/bar.cpp\n"
                + "build out/app.jar: javac src/App.java\n";
        BuildNinjaGraph g = parse(src);
        assertEquals(3, g.getBuildStatements());
        assertEquals(3, g.getOutputTargets());
        assertEquals(2, g.getRules().get("cc.compile").getBuildCount());
        assertEquals(1, g.getRules().get("javac").getBuildCount());
        assertEquals("CC $out", g.getRules().get("cc.compile").getDescription());
        assertFalse(g.isTruncated());
    }

    @Test
    public void handlesImplicitAndOrderOnlyInputs() throws IOException {
        String src = "rule link\n  command = ld\n"
                + "build out/bin: link a.o b.o | header.h || gen_stamp\n";
        BuildNinjaGraph g = parse(src);
        assertEquals(1, g.getBuildStatements());
        // a.o / b.o / header.h / gen_stamp はすべて入力グループとして集約される
        assertTrue(g.getGroupEdges().size() >= 1);
    }

    @Test
    public void handlesLineContinuation() throws IOException {
        String src = "rule r\n  command = x\n"
                + "build out/x.o: r $\n"
                + "    src/x.cpp\n";
        BuildNinjaGraph g = parse(src);
        assertEquals(1, g.getBuildStatements());
        assertEquals(1, g.getRules().get("r").getBuildCount());
    }

    @Test
    public void aggregatesIntermediatesPathsByModule() {
        assertEquals("frameworks/base", BuildNinjaParser.groupOf(
                "out/soong/.intermediates/frameworks/base/framework/android_common/x.jar"));
        assertEquals("out", BuildNinjaParser.groupOf("out/host/linux-x86/bin/foo"));
        assertEquals("src", BuildNinjaParser.groupOf("src/foo.cpp"));
    }

    @Test
    public void truncatesAtLineLimit() throws IOException {
        StringBuilder sb = new StringBuilder("rule r\n  command = x\n");
        for (int i = 0; i < 50; i++) {
            sb.append("build out/o").append(i).append(": r src/s").append(i)
                    .append(".cpp\n");
        }
        File dir = tmp.newFolder("soong2");
        File ninja = new File(dir, "build.ninja");
        Files.write(ninja.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        BuildNinjaGraph g = new BuildNinjaParser().maxLines(5).analyzeProject(tmp.getRoot());
        assertTrue(g.isTruncated());
    }
}
