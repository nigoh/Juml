// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * .intermediates アナライザのテスト。
 */
public class IntermediatesAnalyzerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private void touch(String relPath) throws IOException {
        File f = new File(tmp.getRoot(), relPath);
        f.getParentFile().mkdirs();
        java.nio.file.Files.write(f.toPath(), "x".getBytes());
    }

    private IntermediateModule find(IntermediatesInventory inv, String name) {
        for (IntermediateModule m : inv.getModules()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    @Test
    public void detectsModuleNameVariantAndKind() throws IOException {
        touch("out/soong/.intermediates/frameworks/base/framework/"
                + "android_common/combined/framework.jar");
        touch("out/soong/.intermediates/frameworks/base/framework/"
                + "android_common/javac/classes.dex");
        IntermediatesInventory inv =
                new IntermediatesAnalyzer().analyzeProject(tmp.getRoot());
        IntermediateModule fw = find(inv, "framework");
        assertNotNull(fw);
        assertEquals("frameworks/base", fw.getModulePath());
        assertTrue(fw.getVariants().contains("android_common"));
        assertEquals(2, fw.getTotalFiles());
        assertEquals(Integer.valueOf(1), fw.getKindCounts().get("jar"));
        assertEquals("java", fw.getCategory());
    }

    @Test
    public void groupsMultipleVariantsOfSameModule() throws IOException {
        touch("out/soong/.intermediates/system/core/libutils/"
                + "android_arm64_armv8-a_shared/libutils.so");
        touch("out/soong/.intermediates/system/core/libutils/"
                + "android_arm64_armv8-a_static/libutils.a");
        IntermediatesInventory inv =
                new IntermediatesAnalyzer().analyzeProject(tmp.getRoot());
        IntermediateModule lib = find(inv, "libutils");
        assertNotNull(lib);
        assertEquals(2, lib.getVariants().size());
        assertEquals("native", lib.getCategory());
        assertEquals(2, lib.getTotalFiles());
    }

    @Test
    public void emptyWhenNoIntermediates() throws IOException {
        touch("src/main/java/Foo.java");
        IntermediatesInventory inv =
                new IntermediatesAnalyzer().analyzeProject(tmp.getRoot());
        assertTrue(inv.getModules().isEmpty());
        assertFalse(inv.isTruncated());
    }

    @Test
    public void variantHeuristicRecognizesCommonForms() {
        assertTrue(IntermediatesAnalyzer.isVariantSegment("android_common"));
        assertTrue(IntermediatesAnalyzer.isVariantSegment(
                "android_arm64_armv8-a_shared"));
        assertTrue(IntermediatesAnalyzer.isVariantSegment("linux_glibc_x86_64"));
        assertFalse(IntermediatesAnalyzer.isVariantSegment("framework"));
        assertFalse(IntermediatesAnalyzer.isVariantSegment("base"));
    }
}
