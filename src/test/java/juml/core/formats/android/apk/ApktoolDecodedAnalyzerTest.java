// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.util.ErrorListener;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ApktoolDecodedAnalyzer} のユニットテスト。
 * フィクスチャ: {@code src/test/resources/samples/apk/decoded}。
 */
public class ApktoolDecodedAnalyzerTest {

    private static File decodedDir() {
        return new File("src/test/resources/samples/apk/decoded");
    }

    @Test
    public void testDetection() {
        assertTrue(ApktoolDecodedAnalyzer.isApktoolDecodedDir(decodedDir()));
        assertFalse(ApktoolDecodedAnalyzer.isApktoolDecodedDir(new File("src/main/java")));
        assertFalse(ApktoolDecodedAnalyzer.isApktoolDecodedDir(null));
    }

    @Test
    public void testApktoolAndManifestParsed() throws IOException {
        ApkAnalysis a = ApktoolDecodedAnalyzer.analyze(decodedDir(), ErrorListener.silent());
        assertNotNull(a.getApktoolInfo());
        assertEquals("example-release.apk", a.getApktoolInfo().getApkFileName());
        assertEquals("1.0.3", a.getApktoolInfo().getVersionName());
        assertNotNull(a.getManifest());
        assertEquals("com.example.app", a.getManifest().getPackageName());
        assertEquals("com.example.app", a.getApplicationPackage());
    }

    @Test
    public void testSmaliClassesCollectedAcrossMultidex() throws IOException {
        ApkAnalysis a = ApktoolDecodedAnalyzer.analyze(decodedDir(), ErrorListener.silent());
        // synthetic Repository$1 は既定で除外 -> App, MainActivity, MainPresenter, Repository
        assertEquals(4, a.classCount());
        boolean hasRepo = a.getClasses().stream()
                .anyMatch(c -> c.getClassName().equals("com.example.app.util.Repository"));
        assertTrue("multidex smali_classes2 should be scanned", hasRepo);
    }

    @Test
    public void testSyntheticIncludedWhenRequested() throws IOException {
        ApktoolDecodedAnalyzer.Options o = new ApktoolDecodedAnalyzer.Options();
        o.includeSynthetic = true;
        ApkAnalysis a = ApktoolDecodedAnalyzer.analyze(decodedDir(),
                ErrorListener.silent(), o);
        assertEquals(5, a.classCount());
    }

    @Test
    public void testPackagePrefixFilter() throws IOException {
        ApktoolDecodedAnalyzer.Options o = new ApktoolDecodedAnalyzer.Options();
        o.packagePrefix = "com.example.app.util";
        ApkAnalysis a = ApktoolDecodedAnalyzer.analyze(decodedDir(),
                ErrorListener.silent(), o);
        assertEquals(1, a.classCount());
        assertEquals("com.example.app.util.Repository",
                a.getClasses().get(0).getClassName());
    }

    @Test
    public void testMaxClassesCap() throws IOException {
        ApktoolDecodedAnalyzer.Options o = new ApktoolDecodedAnalyzer.Options();
        o.maxClasses = 2;
        ApkAnalysis a = ApktoolDecodedAnalyzer.analyze(decodedDir(),
                ErrorListener.silent(), o);
        assertEquals(2, a.classCount());
    }

    @Test
    public void testClassCountByPackage() throws IOException {
        ApkAnalysis a = ApktoolDecodedAnalyzer.analyze(decodedDir(), ErrorListener.silent());
        assertEquals(Integer.valueOf(3),
                a.classCountByPackage().get("com.example.app"));
    }
}
