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

    @Test
    public void preferenceXmlScanHonoursIncludeTests() throws Exception {
        // 回帰: --settings のうち res/xml 側だけ includeTests を無視しており、
        // 指定していないのにテストソースの設定定義がレポートへ混ざっていた。
        File root = tmp.newFolder("prefs-proj");
        writePrefXml(new File(root, "app/src/main/res/xml"), "main_prefs", "main_key");
        writePrefXml(new File(root, "app/src/androidTest/res/xml"), "test_prefs", "test_key");

        juml.core.formats.android.settings.PreferencesXmlParser parser =
                new juml.core.formats.android.settings.PreferencesXmlParser();
        List<String> defaults = keysOf(parser.analyzeProject(root));
        assertTrue("本番の設定は含まれる: " + defaults, defaults.contains("main_key"));
        assertFalse("既定ではテストソースの設定を含めない: " + defaults,
                defaults.contains("test_key"));

        List<String> withTests = keysOf(parser.analyzeProject(root, true));
        assertTrue(withTests.contains("main_key"));
        assertTrue("--include-tests ならテストソースの設定も含める: " + withTests,
                withTests.contains("test_key"));
    }

    @Test
    public void projectRootNamedLikeTestsIsStillAnalyzed() throws Exception {
        // 回帰: テストディレクトリ除外をルート自身にも掛けていたため、利用者が
        // 指定したパスの名前が "…Tests" や "carservice_unit_test" だと、その中身が
        // 本番ソースであっても走査が即打ち切られ、結果が黙って空になっていた。
        // AndroidProjectScanner は以前からルート自身を除外判定にかけていない。
        for (String rootName : new String[] {"MyAppTests", "carservice_unit_test", "tests"}) {
            File root = tmp.newFolder(rootName);
            writePrefXml(new File(root, "app/src/main/res/xml"), "prefs", "main_key");

            List<String> keys = keysOf(
                    new juml.core.formats.android.settings.PreferencesXmlParser()
                            .analyzeProject(root, false));
            assertTrue("ルート名が " + rootName + " でも中身を解析すること: " + keys,
                    keys.contains("main_key"));
        }
    }

    @Test
    public void symlinkedProjectRootIsWalked() throws Exception {
        // 回帰: リンクを辿らない走査にリンクのルートを渡すと「ルートをファイルとして
        // 1 件訪問して終わり」になり、結果が黙って空になっていた
        // (~/work -> /mnt/src/work のような貼り方は普通)。
        File real = tmp.newFolder("realproj");
        writePrefXml(new File(real, "app/src/main/res/xml"), "prefs", "main_key");
        File link = new File(tmp.getRoot(), "linkproj");
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), real.toPath());
        } catch (UnsupportedOperationException | java.io.IOException noSymlink) {
            return;
        }

        var parser = new juml.core.formats.android.settings.PreferencesXmlParser();
        assertEquals("リンク経由でも実体と同じ結果になること",
                keysOf(parser.analyzeProject(real, false)),
                keysOf(parser.analyzeProject(link, false)));
        assertTrue(keysOf(parser.analyzeProject(link, false)).contains("main_key"));
    }

    @Test
    public void generatedOutputDirectoriesAreExcluded() throws Exception {
        // 回帰: 除外名を 4 つしか見ていなかったため、out/ bin/ .idea/ .cxx/ にある
        // 生成物のコピーが settings.md へ二重計上され、同じ実行のクラス図とも
        // 食い違っていた。Java 側の走査と同じ集合を使う。
        File root = tmp.newFolder("genproj");
        writePrefXml(new File(root, "app/src/main/res/xml"), "prefs", "main_key");
        for (String dir : new String[] {"out/target/res/xml", "bin/res/xml",
                                        ".idea/res/xml", ".cxx/res/xml", "build/res/xml"}) {
            writePrefXml(new File(root, dir), "prefs", dir.replace('/', '_'));
        }

        List<String> keys = keysOf(
                new juml.core.formats.android.settings.PreferencesXmlParser()
                        .analyzeProject(root, false));
        assertEquals("本番の定義だけが残ること: " + keys, List.of("main_key"), keys);
    }

    @Test
    public void unreadableDirectoryDoesNotAbortTheWholeScan() throws Exception {
        // 回帰: SimpleFileVisitor の既定は visitFileFailed で例外を投げ直すため、
        // 読めないディレクトリが 1 つあるだけで --settings 全体が
        // AccessDeniedException で落ち、レポートが 1 行も出なかった。
        File root = tmp.newFolder("guarded");
        writePrefXml(new File(root, "app/src/main/res/xml"), "prefs", "main_key");
        File secret = new File(root, "secret/inner");
        assertTrue(secret.mkdirs());
        try {
            java.nio.file.Files.setPosixFilePermissions(secret.getParentFile().toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException notPosix) {
            return;
        }
        try {
            org.junit.Assume.assumeFalse("root では権限が効かない",
                    java.nio.file.Files.isReadable(secret.getParentFile().toPath()));

            List<String> keys = keysOf(
                    new juml.core.formats.android.settings.PreferencesXmlParser()
                            .analyzeProject(root, false));
            assertTrue("読めない枝を飛ばして続行すること: " + keys, keys.contains("main_key"));
        } finally {
            java.nio.file.Files.setPosixFilePermissions(secret.getParentFile().toPath(),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    public void symlinkedRootIsWalkedByTheJavaScanTooSoBothHalvesAgree() throws Exception {
        // 回帰: res/xml 側だけルートを実体へ解決すると、--settings は結果を返すのに
        // 同じプロジェクトのクラス図は空、という食い違いが起きる。両方が同じ規律で
        // 動くことを固定する。
        File real = tmp.newFolder("agree-real");
        writePrefXml(new File(real, "app/src/main/res/xml"), "prefs", "main_key");
        writeJava(new File(real, "app/src/main/java/p"), "MainActivity");
        File link = new File(tmp.getRoot(), "agree-link");
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), real.toPath());
        } catch (UnsupportedOperationException | java.io.IOException noSymlink) {
            return;
        }

        var opts = new juml.core.formats.java.AndroidProjectScanner.Options();
        int javaViaReal = juml.core.formats.java.AndroidProjectScanner.scan(real, opts).size();
        int javaViaLink = juml.core.formats.java.AndroidProjectScanner.scan(link, opts).size();
        assertTrue("実体経由では Java ソースが見つかる前提", javaViaReal > 0);
        assertEquals("リンク経由でも同じ数の Java ソースを見つけること",
                javaViaReal, javaViaLink);

        var parser = new juml.core.formats.android.settings.PreferencesXmlParser();
        assertEquals("設定側とクラス図側が同じプロジェクトについて食い違わないこと",
                keysOf(parser.analyzeProject(real, false)),
                keysOf(parser.analyzeProject(link, false)));
    }

    private static List<String> keysOf(
            List<juml.core.formats.android.settings.PreferenceXmlEntry> entries) {
        List<String> keys = new ArrayList<>();
        for (juml.core.formats.android.settings.PreferenceXmlEntry e : entries) {
            keys.add(e.key);
        }
        return keys;
    }

    private static void writePrefXml(File dir, String fileName, String key) throws Exception {
        assertTrue(dir.mkdirs() || dir.isDirectory());
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "  <SwitchPreference android:key=\"" + key + "\" android:title=\"T\" />\n"
                + "</PreferenceScreen>\n";
        Files.write(new File(dir, fileName + ".xml").toPath(),
                xml.getBytes(StandardCharsets.UTF_8));
    }
}
