// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ApktoolYmlParser} のユニットテスト。
 */
public class ApktoolYmlParserTest {

    private static final String SAMPLE =
            "!!brut.androlib.meta.MetaInfo\n"
            + "apkFileName: app-release.apk\n"
            + "compressionType: false\n"
            + "doNotCompress:\n"
            + "- arsc\n"
            + "- png\n"
            + "- resources.arsc\n"
            + "isFrameworkApk: false\n"
            + "packageInfo:\n"
            + "  forcedPackageId: '127'\n"
            + "  renameManifestPackage: null\n"
            + "sdkInfo:\n"
            + "  minSdkVersion: '21'\n"
            + "  targetSdkVersion: '33'\n"
            + "sharedLibrary: false\n"
            + "sparseResources: false\n"
            + "usesFramework:\n"
            + "  ids:\n"
            + "  - 1\n"
            + "  tag: null\n"
            + "version: 2.9.3\n"
            + "versionInfo:\n"
            + "  versionCode: '42'\n"
            + "  versionName: '1.2.3'\n";

    @Test(expected = IllegalArgumentException.class)
    public void testNullThrows() {
        ApktoolYmlParser.parse(null);
    }

    @Test
    public void testTopLevel() {
        ApktoolYmlInfo info = ApktoolYmlParser.parse(SAMPLE);
        assertEquals("2.9.3", info.getApktoolVersion());
        assertEquals("app-release.apk", info.getApkFileName());
        assertFalse(info.isFrameworkApk());
        assertFalse(info.isSharedLibrary());
        assertFalse(info.isSparseResources());
    }

    @Test
    public void testSdkInfo() {
        ApktoolYmlInfo info = ApktoolYmlParser.parse(SAMPLE);
        assertEquals("21", info.getMinSdkVersion());
        assertEquals("33", info.getTargetSdkVersion());
    }

    @Test
    public void testVersionInfo() {
        ApktoolYmlInfo info = ApktoolYmlParser.parse(SAMPLE);
        assertEquals("42", info.getVersionCode());
        assertEquals("1.2.3", info.getVersionName());
    }

    @Test
    public void testDoNotCompressList() {
        ApktoolYmlInfo info = ApktoolYmlParser.parse(SAMPLE);
        assertEquals(3, info.getDoNotCompress().size());
        assertTrue(info.getDoNotCompress().contains("arsc"));
        assertTrue(info.getDoNotCompress().contains("resources.arsc"));
    }

    @Test
    public void testUsesFrameworkIds() {
        ApktoolYmlInfo info = ApktoolYmlParser.parse(SAMPLE);
        assertEquals(1, info.getUsesFrameworkIds().size());
        assertEquals("1", info.getUsesFrameworkIds().get(0));
    }

    @Test
    public void testFrameworkApkTrue() {
        ApktoolYmlInfo info = ApktoolYmlParser.parse(
                "isFrameworkApk: true\nversion: 2.7.0\n");
        assertTrue(info.isFrameworkApk());
        assertEquals("2.7.0", info.getApktoolVersion());
    }
}
