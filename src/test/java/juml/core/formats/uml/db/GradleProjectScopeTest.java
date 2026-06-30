// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GradleProjectScopeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static File mkdirs(File parent, String rel) {
        File d = new File(parent, rel);
        if (!d.exists()) {
            assertTrue(d.mkdirs());
        }
        return d;
    }

    private static void write(File f, String content) throws Exception {
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
    }

    @Test
    public void testFallbackWhenNoSettingsGradle() {
        File root = tmp.getRoot();
        GradleProjectScope.Scope scope = GradleProjectScope.resolve(root, ErrorListener.silent());
        assertTrue(scope.isFallback());
        assertTrue(scope.getPaths().isEmpty());
    }

    @Test
    public void testFallbackWhenSettingsHasNoSubprojects() throws Exception {
        File root = tmp.newFolder("proj");
        write(new File(root, "settings.gradle"), "rootProject.name = 'foo'\n");
        GradleProjectScope.Scope scope = GradleProjectScope.resolve(root, ErrorListener.silent());
        assertTrue(scope.isFallback());
    }

    @Test
    public void testResolvesSubprojectSourceDirs() throws Exception {
        File root = tmp.newFolder("proj");
        write(new File(root, "settings.gradle"),
                "include ':app'\n"
                + "include ':lib'\n");
        // app/src/main/java と lib/src/main/java を用意
        mkdirs(root, "app/src/main/java/com/example/app");
        mkdirs(root, "lib/src/main/java/com/example/lib");
        write(new File(root, "lib/src/main/AndroidManifest.xml"), "<manifest/>");
        mkdirs(root, "lib/src/main/aidl");

        GradleProjectScope.Scope scope = GradleProjectScope.resolve(root, ErrorListener.silent());
        assertFalse(scope.isFallback());
        // GradleScriptParser は ":" プレフィックスを取り除いて subprojects に格納する
        assertEquals(2, scope.getModuleNameToRelDir().size());
        assertEquals("app", scope.getModuleNameToRelDir().get("app"));
        assertEquals("lib", scope.getModuleNameToRelDir().get("lib"));

        Set<String> kinds = new HashSet<>();
        Set<String> modules = new HashSet<>();
        for (GradleProjectScope.ScopePath p : scope.getPaths()) {
            kinds.add(p.getKind().name());
            modules.add(p.getModuleName());
        }
        assertTrue(kinds.contains("JAVA"));
        assertTrue(kinds.contains("MANIFEST"));
        assertTrue(kinds.contains("AIDL"));
        assertTrue(modules.contains("app"));
        assertTrue(modules.contains("lib"));
    }

    @Test
    public void testNestedColonModulePath() throws Exception {
        File root = tmp.newFolder("proj");
        write(new File(root, "settings.gradle"),
                "include ':core:lib'\n");
        mkdirs(root, "core/lib/src/main/java/x");

        GradleProjectScope.Scope scope = GradleProjectScope.resolve(root, ErrorListener.silent());
        assertFalse(scope.isFallback());
        assertEquals("core/lib", scope.getModuleNameToRelDir().get("core:lib"));
    }

    @Test
    public void testSubprojectsWithoutSourceDirsAreSkipped() throws Exception {
        File root = tmp.newFolder("proj");
        write(new File(root, "settings.gradle"), "include ':orphan'\n");
        // :orphan のディレクトリ自体が無い → fallback 扱い
        GradleProjectScope.Scope scope = GradleProjectScope.resolve(root, ErrorListener.silent());
        assertTrue(scope.isFallback());
    }
}
