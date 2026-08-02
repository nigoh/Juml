// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.UmlGenerator;
import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    public void classDiagramManifestMergeHonoursIncludeTests() throws Exception {
        // 回帰: UmlGenerator.mergeManifestInto が includeTests を持たない 2 引数版を
        // 呼んでいたため、--include-tests でテストクラスは図に出るのに、テストソースの
        // AndroidManifest が読まれずコンポーネント種別 (<<Activity>>) だけが付かなかった。
        File root = tmp.newFolder("manifest-proj");
        writeManifest(new File(root, "app/src/main"), "MainActivity");
        writeManifest(new File(root, "app/src/androidTest"), "InstrumentedActivity");

        List<JavaClassInfo> classes = new ArrayList<>();
        classes.add(classNamed("p", "MainActivity"));
        classes.add(classNamed("p", "InstrumentedActivity"));

        UmlGenerator.mergeManifestInto(classes, root, ErrorListener.silent(), true);
        assertEquals("本番の Activity は従来どおり種別が付く",
                "Activity", classes.get(0).getAndroidComponentType());
        assertEquals("--include-tests ならテストソースの Activity にも種別が付く",
                "Activity", classes.get(1).getAndroidComponentType());

        List<JavaClassInfo> defaults = new ArrayList<>();
        defaults.add(classNamed("p", "MainActivity"));
        defaults.add(classNamed("p", "InstrumentedActivity"));
        UmlGenerator.mergeManifestInto(defaults, root, ErrorListener.silent());
        assertEquals("Activity", defaults.get(0).getAndroidComponentType());
        assertNull("既定ではテストソースの manifest を読まない",
                defaults.get(1).getAndroidComponentType());
    }

    @Test
    public void projectScanPassesIncludeTestsToTheManifestMerge() throws Exception {
        // オーバーロードが正しいだけでは足りない: 走査経路が実際にそれを呼ぶことを固定する
        // (以前は 2 引数版を呼んでいたため、この配線が抜けていた)。
        File root = tmp.newFolder("scan-proj");
        writeManifest(new File(root, "app/src/main"), "MainActivity");
        writeManifest(new File(root, "app/src/androidTest"), "InstrumentedActivity");
        writeJava(new File(root, "app/src/main/java/p"), "MainActivity");
        writeJava(new File(root, "app/src/androidTest/java/p"), "InstrumentedActivity");

        juml.core.formats.java.AndroidProjectScanner.Options opts =
                new juml.core.formats.java.AndroidProjectScanner.Options();
        opts.includeTests = true;
        UmlGenerator.ProjectParseResult result = UmlGenerator.extractFromProjectDetailed(
                root, opts, ErrorListener.silent(), juml.util.ProgressListener.silent(),
                new juml.util.CancelToken(), true, UmlGenerator.ParseMode.FULL);

        JavaClassInfo instrumented = null;
        for (JavaClassInfo c : result.getClasses()) {
            if ("InstrumentedActivity".equals(c.getSimpleName())) {
                instrumented = c;
            }
        }
        assertNotNull("--include-tests でテストクラスが図に含まれること", instrumented);
        assertEquals("そのクラスにコンポーネント種別も付くこと",
                "Activity", instrumented.getAndroidComponentType());
    }

    private static void writeJava(File dir, String simpleName) throws Exception {
        assertTrue(dir.mkdirs() || dir.isDirectory());
        Files.write(new File(dir, simpleName + ".java").toPath(),
                ("package p;\npublic class " + simpleName + " {}\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static JavaClassInfo classNamed(String pkg, String simple) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(simple);
        c.setKind(JavaClassInfo.Kind.CLASS);
        return c;
    }

    private static void writeManifest(File sourceSetDir, String activity) throws Exception {
        assertTrue(sourceSetDir.mkdirs() || sourceSetDir.isDirectory());
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"p\">\n  <application>\n"
                + "    <activity android:name=\"." + activity + "\" />\n"
                + "  </application>\n</manifest>\n";
        Files.write(new File(sourceSetDir, "AndroidManifest.xml").toPath(),
                xml.getBytes(StandardCharsets.UTF_8));
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
