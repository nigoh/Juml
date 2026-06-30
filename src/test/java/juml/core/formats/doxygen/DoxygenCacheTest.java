// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DoxygenCache} のユニットテスト (doxygen 実行は伴わない)。
 */
public class DoxygenCacheTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void shortHashIsStableForSamePath() {
        File root = tmp.getRoot();
        assertEquals(DoxygenCache.shortHash(root), DoxygenCache.shortHash(root));
    }

    @Test
    public void signatureChangesWhenJavaFilesChange() throws IOException {
        File root = tmp.newFolder("proj");
        String empty = DoxygenCache.signature(root, "/usr/bin/doxygen");

        File src = new File(root, "Foo.java");
        Files.writeString(src.toPath(), "class Foo {}", StandardCharsets.UTF_8);
        String withOne = DoxygenCache.signature(root, "/usr/bin/doxygen");
        assertNotEquals(empty, withOne);

        // doxygen パスが変われば署名も変わる。
        assertNotEquals(withOne, DoxygenCache.signature(root, "/opt/doxygen"));
    }

    @Test
    public void signatureSkipsBuildAndTestDirs() throws IOException {
        File root = tmp.newFolder("proj2");
        String base = DoxygenCache.signature(root, "d");

        File buildDir = new File(root, "build");
        buildDir.mkdirs();
        Files.writeString(new File(buildDir, "Gen.java").toPath(), "class Gen {}",
                StandardCharsets.UTF_8);
        // build/ 配下の .java は無視されるため署名は不変。
        assertEquals(base, DoxygenCache.signature(root, "d"));
    }

    @Test
    public void isFreshRequiresStampAndIndex() throws IOException {
        File cacheDir = tmp.newFolder("cache");
        String sig = "1:2:d";
        assertFalse("stamp/index 無しは fresh でない", DoxygenCache.isFresh(cacheDir, sig));

        File xmlDir = new File(cacheDir, "xml");
        xmlDir.mkdirs();
        Files.writeString(new File(xmlDir, "index.xml").toPath(), "<x/>", StandardCharsets.UTF_8);
        DoxygenCache.writeStamp(cacheDir, sig);
        assertTrue("一致する署名なら fresh", DoxygenCache.isFresh(cacheDir, sig));
        assertFalse("署名が違えば fresh でない", DoxygenCache.isFresh(cacheDir, "9:9:d"));
    }
}
