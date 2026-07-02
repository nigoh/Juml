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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link VintfProjectScanner} のユニットテスト。候補ファイル名の判定と、
 * プロジェクト走査 (AndroidManifest.xml の除外 / AOSP 級除外ディレクトリ) を検証する。
 */
public class VintfProjectScannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String DEVICE_MANIFEST =
            "<manifest version=\"1.0\" type=\"device\">"
                    + "  <hal format=\"hidl\">"
                    + "    <name>android.hardware.audio</name>"
                    + "    <version>6.0</version>"
                    + "  </hal>"
                    + "</manifest>";

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            assertTrue(parent.mkdirs());
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void candidateNamesMatchVintfConventions() {
        assertTrue(VintfProjectScanner.isVintfCandidateName("manifest.xml"));
        assertTrue(VintfProjectScanner.isVintfCandidateName("manifest_media.xml"));
        assertTrue(VintfProjectScanner.isVintfCandidateName("compatibility_matrix.xml"));
        assertTrue(VintfProjectScanner.isVintfCandidateName("compatibility_matrix.8.xml"));
        // AndroidManifest.xml や無関係な XML は候補にしない
        assertFalse(VintfProjectScanner.isVintfCandidateName("AndroidManifest.xml"));
        assertFalse(VintfProjectScanner.isVintfCandidateName("strings.xml"));
        assertFalse(VintfProjectScanner.isVintfCandidateName("manifest.json"));
        assertFalse(VintfProjectScanner.isVintfCandidateName(null));
    }

    @Test
    public void analyzeProjectClassifiesAndSkipsNonVintf() throws IOException {
        File root = tmp.newFolder("proj");
        write(new File(root, "device/manifest.xml"), DEVICE_MANIFEST);
        write(new File(root, "compatibility_matrix.xml"),
                "<compatibility-matrix version=\"1.0\" type=\"framework\" level=\"6\">"
                        + "</compatibility-matrix>");
        // 候補名だが VINTF ではない (アプリ manifest 形式) → 除外される
        write(new File(root, "app/manifest.xml"),
                "<manifest package=\"com.example\"><application/></manifest>");

        List<VintfProjectScanner.Entry> entries =
                new VintfProjectScanner().analyzeProject(root);
        assertEquals(2, entries.size());
        int device = 0;
        int matrix = 0;
        for (VintfProjectScanner.Entry e : entries) {
            if (e.getManifest().getKind() == VintfManifest.Kind.DEVICE_MANIFEST) {
                device++;
                assertEquals(1, e.getManifest().getHals().size());
            } else if (e.getManifest().getKind()
                    == VintfManifest.Kind.COMPATIBILITY_MATRIX) {
                matrix++;
            }
        }
        assertEquals(1, device);
        assertEquals(1, matrix);
    }

    @Test
    public void analyzeProjectSkipsAospExcludedDirs() throws IOException {
        File root = tmp.newFolder("tree");
        write(new File(root, "manifest.xml"), DEVICE_MANIFEST);
        write(new File(root, "prebuilts/vintf/manifest.xml"), DEVICE_MANIFEST);
        write(new File(root, ".repo/manifest.xml"), DEVICE_MANIFEST);
        write(new File(root, "out/manifest.xml"), DEVICE_MANIFEST);

        List<VintfProjectScanner.Entry> entries =
                new VintfProjectScanner().analyzeProject(root);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).getFile().endsWith("manifest.xml"));
    }
}
