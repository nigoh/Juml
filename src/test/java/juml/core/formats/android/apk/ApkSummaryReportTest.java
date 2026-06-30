// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.util.ErrorListener;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * {@link ApkSummaryReport} のユニットテスト。
 */
public class ApkSummaryReportTest {

    private static ApkAnalysis decoded() throws IOException {
        return ApktoolDecodedAnalyzer.analyze(
                new File("src/test/resources/samples/apk/decoded"), ErrorListener.silent());
    }

    @Test
    public void testContainsApktoolMeta() throws IOException {
        String md = ApkSummaryReport.toMarkdown(decoded());
        assertTrue(md.contains("example-release.apk"));
        assertTrue(md.contains("1.0.3"));
        assertTrue(md.contains("targetSdkVersion"));
    }

    @Test
    public void testContainsManifestComponents() throws IOException {
        String md = ApkSummaryReport.toMarkdown(decoded());
        assertTrue(md.contains("com.example.app"));
        assertTrue(md.contains("MainActivity"));
        assertTrue(md.contains("android.permission.INTERNET"));
    }

    @Test
    public void testContainsSmaliStats() throws IOException {
        String md = ApkSummaryReport.toMarkdown(decoded());
        assertTrue(md.contains("クラス総数"));
        assertTrue(md.contains("パッケージ別クラス数"));
        assertTrue(md.contains("com.example.app.util"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullThrows() {
        ApkSummaryReport.toMarkdown(null);
    }
}
