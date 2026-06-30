// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.util.ErrorListener;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * {@link SmaliStructureReport} のユニットテスト。
 */
public class SmaliStructureReportTest {

    private static ApkAnalysis decoded() throws IOException {
        return ApktoolDecodedAnalyzer.analyze(
                new File("src/test/resources/samples/apk/decoded"), ErrorListener.silent());
    }

    @Test
    public void listsClassesWithMembers() throws IOException {
        String md = SmaliStructureReport.toMarkdown(decoded());
        assertTrue(md.contains("# smali クラス構造"));
        assertTrue(md.contains("## com.example.app"));
        assertTrue(md.contains("MainActivity"));
        assertTrue(md.contains("extends `androidx.appcompat.app.AppCompatActivity`"));
        assertTrue(md.contains("implements `android.view.View$OnClickListener`"));
    }

    @Test
    public void showsFieldsAndMethods() throws IOException {
        String md = SmaliStructureReport.toMarkdown(decoded());
        assertTrue(md.contains("presenter: com.example.app.MainPresenter"));
        assertTrue(md.contains("onClick(View)"));
    }

    @Test
    public void interfaceShown() throws IOException {
        String md = SmaliStructureReport.toMarkdown(decoded());
        assertTrue(md.contains("### interface Repository"));
        assertTrue(md.contains("fetch(): String"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullThrows() {
        SmaliStructureReport.toMarkdown((ApkAnalysis) null);
    }
}
