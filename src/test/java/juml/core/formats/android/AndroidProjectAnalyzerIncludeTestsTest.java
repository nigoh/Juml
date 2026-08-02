// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code --include-tests} が Android プロジェクト解析まで届くことの回帰テスト。
 *
 * <p>{@link AndroidProjectAnalyzer#analyze(File, ErrorListener)} は走査オプションを
 * その場で組み立てており {@code includeTests} を持たなかったため、CLI の
 * {@code --include-tests} は Android 系モード ({@code --gradle} / {@code --manifest} /
 * ナビゲーショングラフ等) で<b>黙って無視され</b>、テストソース配下の gradle/manifest/
 * ナビゲーショングラフが常に落ちていた。指定したのに結果が変わらないため気付きにくい。</p>
 */
public class AndroidProjectAnalyzerIncludeTestsTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final String NAV_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
            + "    android:id=\"@+id/%s\" app:startDestination=\"@id/home\">\n"
            + "  <fragment android:id=\"@+id/home\" android:name=\"com.x.HomeFragment\"\n"
            + "      android:label=\"Home\" />\n"
            + "</navigation>\n";

    /** 本番ソースとテストソースの両方にナビゲーショングラフを置いたプロジェクト。 */
    private File projectWithTestSources() throws Exception {
        File root = tmp.newFolder("proj");
        for (String sourceSet : new String[] {"main", "androidTest"}) {
            File navDir = new File(root, "app/src/" + sourceSet + "/res/navigation");
            assertTrue(navDir.mkdirs());
            Files.write(new File(navDir, sourceSet + "_nav.xml").toPath(),
                    String.format(NAV_XML, sourceSet).getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    private static boolean hasGraphNamed(AndroidProjectAnalysis a, String fileName) {
        for (AndroidNavigationGraphInfo g : a.allNavigationGraphs()) {
            if (fileName.equals(g.getFileName())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void withoutIncludeTests_testSourcesAreSkipped() throws Exception {
        AndroidProjectAnalysis a =
                AndroidProjectAnalyzer.analyze(projectWithTestSources(), ErrorListener.silent());
        assertTrue("本番ソースは常に対象", hasGraphNamed(a, "main_nav.xml"));
        assertFalse("既定ではテストソースを除外する", hasGraphNamed(a, "androidTest_nav.xml"));
    }

    @Test
    public void withIncludeTests_testSourcesAreScanned() throws Exception {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(
                projectWithTestSources(), ErrorListener.silent(), true);
        assertTrue("本番ソースは常に対象", hasGraphNamed(a, "main_nav.xml"));
        assertTrue("--include-tests でテストソースも対象になること",
                hasGraphNamed(a, "androidTest_nav.xml"));
    }
}
