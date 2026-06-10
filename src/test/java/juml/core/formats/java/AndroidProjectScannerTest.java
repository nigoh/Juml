// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AndroidProjectScanner のユニットテスト。
 *
 * <p>JUnit 4 の TemporaryFolder で Android Gradle プロジェクトを模した
 * ディレクトリ構造を組み立て、スキャンが期待通りに動作することを検証する。</p>
 */
public class AndroidProjectScannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;

    @Before
    public void setupProject() throws IOException {
        root = tmp.newFolder("MyApp");
        // 標準 Android Gradle レイアウト
        File mainJava = new File(root, "app/src/main/java/com/example/app");
        assertTrue(mainJava.mkdirs());
        writeFile(new File(mainJava, "MainActivity.java"),
                "package com.example.app;\n"
                        + "public class MainActivity { void onCreate() {} }");
        writeFile(new File(mainJava, "Util.java"),
                "package com.example.app;\n"
                        + "public class Util { static int x() { return 1; } }");

        File testJava = new File(root, "app/src/test/java/com/example/app");
        assertTrue(testJava.mkdirs());
        writeFile(new File(testJava, "MainActivityTest.java"),
                "package com.example.app;\n"
                        + "public class MainActivityTest { void t() {} }");

        File androidTest = new File(root, "app/src/androidTest/java/com/example/app");
        assertTrue(androidTest.mkdirs());
        writeFile(new File(androidTest, "UiTest.java"),
                "package com.example.app;\n"
                        + "public class UiTest { void t() {} }");

        // 除外されるディレクトリ
        File buildDir = new File(root, "app/build/generated/source");
        assertTrue(buildDir.mkdirs());
        writeFile(new File(buildDir, "Generated.java"),
                "public class Generated {}");

        File gradleDir = new File(root, ".gradle/some/path");
        assertTrue(gradleDir.mkdirs());
        writeFile(new File(gradleDir, "Cached.java"), "// junk");

        File idea = new File(root, ".idea");
        assertTrue(idea.mkdirs());
        writeFile(new File(idea, "WontScan.java"), "// junk");

        // 非 Java ファイル
        writeFile(new File(mainJava, "Notes.kt"), "fun foo() {}");
        writeFile(new File(mainJava, "strings.xml"), "<x/>");
    }

    @After
    public void teardown() {
        // TemporaryFolder が自動削除する
    }

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private static String basename(File f) {
        return f.getName();
    }

    // --- 入力バリデーション ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullRoot() {
        AndroidProjectScanner.scan(null);
    }

    @Test
    public void testNonExistentRoot() {
        List<File> result = AndroidProjectScanner.scan(new File("/does-not-exist-xyz"));
        assertTrue(result.isEmpty());
    }

    // --- 基本動作 ---

    @Test
    public void testDefaultScanFindsMainJavaOnly() {
        List<File> files = AndroidProjectScanner.scan(root);
        assertEquals("expected 2 main java files, got: " + files, 2, files.size());
        for (File f : files) {
            String path = f.getPath().replace(File.separatorChar, '/');
            assertTrue(path, path.contains("src/main/java"));
            assertTrue(path, path.endsWith(".java"));
        }
    }

    @Test
    public void testExcludeBuildAndIdeFolders() {
        List<File> files = AndroidProjectScanner.scan(root);
        for (File f : files) {
            String path = f.getPath().replace(File.separatorChar, '/');
            assertFalse("build dir not excluded: " + path, path.contains("/build/"));
            assertFalse("gradle dir not excluded: " + path, path.contains("/.gradle/"));
            assertFalse("idea dir not excluded: " + path, path.contains("/.idea/"));
        }
    }

    @Test
    public void testIncludeTests() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeTests = true;
        List<File> files = AndroidProjectScanner.scan(root, o);
        // main 2 + test 1 + androidTest 1 = 4
        assertEquals("expected 4 files, got: " + files, 4, files.size());
        boolean foundTest = false;
        boolean foundAndroidTest = false;
        for (File f : files) {
            String p = f.getPath().replace(File.separatorChar, '/');
            if (p.contains("/src/test/")) {
                foundTest = true;
            }
            if (p.contains("/src/androidTest/")) {
                foundAndroidTest = true;
            }
        }
        assertTrue(foundTest);
        assertTrue(foundAndroidTest);
    }

    @Test
    public void testIncludeKotlin() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeKotlin = true;
        List<File> files = AndroidProjectScanner.scan(root, o);
        boolean foundKt = false;
        for (File f : files) {
            if (f.getName().endsWith(".kt")) {
                foundKt = true;
            }
        }
        assertTrue("expected at least one .kt file", foundKt);
    }

    @Test
    public void testCustomExcludedDirs() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.excludedDirs = new HashSet<>(AndroidProjectScanner.DEFAULT_EXCLUDED_DIRS);
        o.excludedDirs.add("app");
        List<File> files = AndroidProjectScanner.scan(root, o);
        // "app" を追加除外したので main の Java ファイルも見つからない
        assertTrue("expected no files when app/ excluded, got: " + files,
                files.isEmpty());
    }

    @Test
    public void testEmptyExcludedDirsScansEverything() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.excludedDirs = new HashSet<>();
        List<File> files = AndroidProjectScanner.scan(root, o);
        // 除外がないので build/.gradle/.idea 配下も含まれる
        boolean foundBuild = false;
        boolean foundGradleDir = false;
        for (File f : files) {
            String p = f.getPath().replace(File.separatorChar, '/');
            if (p.contains("/build/")) {
                foundBuild = true;
            }
            if (p.contains("/.gradle/")) {
                foundGradleDir = true;
            }
        }
        assertTrue("expected build/ to be included: " + files, foundBuild);
        assertTrue("expected .gradle/ to be included: " + files, foundGradleDir);
    }

    @Test
    public void testMaxDepth() {
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.maxDepth = 1;
        List<File> files = AndroidProjectScanner.scan(root, o);
        // 深さ 1 では app/src/... に到達できない
        assertTrue("expected no files with maxDepth=1", files.isEmpty());
    }

    @Test
    public void testScanSingleFile() throws IOException {
        File single = tmp.newFile("OneShot.java");
        writeFile(single, "class C {}");
        List<File> files = AndroidProjectScanner.scan(single);
        assertEquals(1, files.size());
        assertEquals(single, files.get(0));
    }

    @Test
    public void testScanSingleNonJavaFile() throws IOException {
        File single = tmp.newFile("Notes.txt");
        writeFile(single, "ignore");
        List<File> files = AndroidProjectScanner.scan(single);
        assertTrue(files.isEmpty());
    }

    @Test
    public void testReadFileUtf8() throws IOException {
        File f = tmp.newFile("Hello.java");
        writeFile(f, "class A { /* 日本語コメント */ }");
        String content = AndroidProjectScanner.readFile(f);
        assertTrue(content.contains("日本語コメント"));
    }

    @Test
    public void testFilesAreSorted() {
        List<File> files = AndroidProjectScanner.scan(root);
        for (int i = 1; i < files.size(); i++) {
            assertTrue("files not sorted: " + files.get(i - 1) + " vs " + files.get(i),
                    files.get(i - 1).compareTo(files.get(i)) <= 0);
        }
    }

    @Test
    public void testIncludeGradleAndManifest() throws IOException {
        // ルート直下に build.gradle と AndroidManifest.xml を追加
        writeFile(new File(root, "build.gradle"), "// root\n");
        File appMain = new File(root, "app/src/main");
        // (setupProject で app/src/main は作成済み)
        writeFile(new File(appMain, "AndroidManifest.xml"), "<manifest package='p'/>");

        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeGradle = true;
        o.includeManifest = true;
        List<File> files = AndroidProjectScanner.scan(root, o);
        boolean foundGradle = false;
        boolean foundManifest = false;
        for (File f : files) {
            if (f.getName().equals("build.gradle")) {
                foundGradle = true;
            }
            if (f.getName().equals("AndroidManifest.xml")) {
                foundManifest = true;
            }
        }
        assertTrue("expected build.gradle", foundGradle);
        assertTrue("expected AndroidManifest.xml", foundManifest);
    }

    @Test
    public void testGradleAndManifestNotIncludedByDefault() throws IOException {
        writeFile(new File(root, "build.gradle"), "// root\n");
        List<File> files = AndroidProjectScanner.scan(root);
        for (File f : files) {
            assertFalse("build.gradle should not be returned by default: " + f,
                    f.getName().equals("build.gradle"));
        }
    }

    // --- res/layout 走査 (includeLayout) ---

    private void prepareLayoutFiles() throws IOException {
        // res/layout/, res/layout-land/, res/layout-v21/ の 3 ファイル
        File mainLayout = new File(root, "app/src/main/res/layout");
        assertTrue(mainLayout.mkdirs());
        writeFile(new File(mainLayout, "activity_main.xml"),
                "<LinearLayout/>");
        writeFile(new File(mainLayout, "fragment_list.xml"),
                "<FrameLayout/>");

        File landLayout = new File(root, "app/src/main/res/layout-land");
        assertTrue(landLayout.mkdirs());
        writeFile(new File(landLayout, "activity_main.xml"),
                "<LinearLayout/>");

        File v21Layout = new File(root, "app/src/main/res/layout-sw600dp-v21");
        assertTrue(v21Layout.mkdirs());
        writeFile(new File(v21Layout, "wide_only.xml"),
                "<FrameLayout/>");

        // res/drawable/, res/values/ のノイズ XML (拾わせない)
        File drawable = new File(root, "app/src/main/res/drawable");
        assertTrue(drawable.mkdirs());
        writeFile(new File(drawable, "ic_launcher.xml"), "<vector/>");
        File values = new File(root, "app/src/main/res/values");
        assertTrue(values.mkdirs());
        writeFile(new File(values, "strings.xml"),
                "<resources/>");
        // res/layout/ 直下の非 XML (拾わせない)
        writeFile(new File(mainLayout, "thumb.png"), "PNG");
    }

    @Test
    public void testLayoutNotIncludedByDefault() throws IOException {
        prepareLayoutFiles();
        // includeLayout デフォルト false なので従来通り Java のみ
        List<File> files = AndroidProjectScanner.scan(root);
        for (File f : files) {
            assertFalse("layout xml should not be returned by default: " + f,
                    f.getName().endsWith(".xml"));
        }
    }

    @Test
    public void testIncludeLayoutPicksUpVariants() throws IOException {
        prepareLayoutFiles();
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeLayout = true;
        List<File> files = AndroidProjectScanner.scan(root, o);

        int layoutCount = 0;
        boolean foundMain = false;
        boolean foundLand = false;
        boolean foundV21 = false;
        for (File f : files) {
            String p = f.getPath().replace(File.separatorChar, '/');
            if (p.endsWith(".xml") && (p.contains("/res/layout/")
                    || p.contains("/res/layout-"))) {
                layoutCount++;
                if (p.contains("/res/layout/activity_main.xml")) {
                    foundMain = true;
                }
                if (p.contains("/res/layout-land/activity_main.xml")) {
                    foundLand = true;
                }
                if (p.contains("/res/layout-sw600dp-v21/wide_only.xml")) {
                    foundV21 = true;
                }
            }
            // res/drawable/, res/values/ の XML は拾われない
            assertFalse("drawable xml leaked: " + p,
                    p.contains("/res/drawable/") && p.endsWith(".xml"));
            assertFalse("values xml leaked: " + p,
                    p.contains("/res/values/") && p.endsWith(".xml"));
            // PNG は拾われない
            assertFalse("png leaked: " + p, p.endsWith(".png"));
        }
        assertEquals("layout 配下の xml 数", 4, layoutCount);
        assertTrue(foundMain);
        assertTrue(foundLand);
        assertTrue(foundV21);
    }

    // --- HIDL (.hal) 走査 (includeHidl) ---

    @Test
    public void testIncludeHidlPicksUpHalFiles() throws IOException {
        File hidlDir = new File(root, "hidl/android/hardware/foo");
        assertTrue(hidlDir.mkdirs());
        writeFile(new File(hidlDir, "IFoo.hal"),
                "package android.hardware.foo@1.0;\ninterface IFoo {};");
        writeFile(new File(hidlDir, "IBar.hal"),
                "package android.hardware.foo@1.0;\ninterface IBar {};");

        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeHidl = true;
        List<File> files = AndroidProjectScanner.scan(root, o);
        int found = 0;
        for (File f : files) {
            if (f.getName().endsWith(".hal")) {
                found++;
            }
        }
        assertEquals(2, found);
    }

    @Test
    public void testHidlNotIncludedByDefault() throws IOException {
        File hidlDir = new File(root, "hidl/android/hardware/foo");
        assertTrue(hidlDir.mkdirs());
        writeFile(new File(hidlDir, "IFoo.hal"),
                "package android.hardware.foo@1.0;\ninterface IFoo {};");
        List<File> files = AndroidProjectScanner.scan(root);
        for (File f : files) {
            assertFalse(".hal should not be returned by default: " + f,
                    f.getName().endsWith(".hal"));
        }
    }

    // --- VINTF (manifest.xml / compatibility_matrix.xml) 走査 (includeVintf) ---

    @Test
    public void testIncludeVintfPicksUpManifestAndMatrix() throws IOException {
        File deviceDir = new File(root, "device/vendor/product");
        assertTrue(deviceDir.mkdirs());
        writeFile(new File(deviceDir, "manifest.xml"),
                "<manifest type=\"device\" version=\"1.0\"/>");
        writeFile(new File(deviceDir, "compatibility_matrix.xml"),
                "<compatibility-matrix type=\"framework\" version=\"1.0\" level=\"6\"/>");

        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeVintf = true;
        List<File> files = AndroidProjectScanner.scan(root, o);
        boolean foundManifest = false;
        boolean foundMatrix = false;
        for (File f : files) {
            if (f.getName().equals("manifest.xml")) {
                foundManifest = true;
            }
            if (f.getName().equals("compatibility_matrix.xml")) {
                foundMatrix = true;
            }
        }
        assertTrue("expected manifest.xml", foundManifest);
        assertTrue("expected compatibility_matrix.xml", foundMatrix);
    }

    @Test
    public void testVintfNotIncludedByDefault() throws IOException {
        File deviceDir = new File(root, "device/vendor/product");
        assertTrue(deviceDir.mkdirs());
        writeFile(new File(deviceDir, "manifest.xml"),
                "<manifest type=\"device\" version=\"1.0\"/>");
        List<File> files = AndroidProjectScanner.scan(root);
        for (File f : files) {
            assertFalse("manifest.xml should not be returned by default: " + f,
                    f.getName().equals("manifest.xml"));
        }
    }

    @Test
    public void testVintfDoesNotShadowAndroidManifest() throws IOException {
        // AndroidManifest.xml と VINTF manifest.xml は別ファイルとして
        // それぞれ別 option で拾われる
        File appMain = new File(root, "app/src/main");
        writeFile(new File(appMain, "AndroidManifest.xml"),
                "<manifest package=\"com.x\"/>");
        File deviceDir = new File(root, "device/vendor/product");
        assertTrue(deviceDir.mkdirs());
        writeFile(new File(deviceDir, "manifest.xml"),
                "<manifest type=\"device\" version=\"1.0\"/>");

        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeVintf = true;
        // includeManifest=false なので AndroidManifest.xml は拾われない
        List<File> files = AndroidProjectScanner.scan(root, o);
        boolean foundAndroid = false;
        boolean foundVintf = false;
        for (File f : files) {
            if (f.getName().equals("AndroidManifest.xml")) {
                foundAndroid = true;
            }
            if (f.getName().equals("manifest.xml")) {
                foundVintf = true;
            }
        }
        assertFalse(foundAndroid);
        assertTrue(foundVintf);
    }

    // --- AOSP 流テストレイアウトの除外 ---

    @Test
    public void testAospStyleTestDirsExcludedByDefault() throws IOException {
        // AOSP 流: ルート直下 tests/、snake_case の *_test、camelCase の *Test
        File aospTests = new File(root, "tests/SomeApp/src/com/example");
        assertTrue(aospTests.mkdirs());
        writeFile(new File(aospTests, "Helper.java"), "public class Helper {}");

        File unitTest = new File(root, "carservice_unit_test/src/com/example");
        assertTrue(unitTest.mkdirs());
        writeFile(new File(unitTest, "Sample.java"), "public class Sample {}");

        File camelTest = new File(root, "CarLibUnitTest/src/com/example");
        assertTrue(camelTest.mkdirs());
        writeFile(new File(camelTest, "Sample2.java"), "public class Sample2 {}");

        File hostside = new File(root, "hostsidetests/src/com/example");
        assertTrue(hostside.mkdirs());
        writeFile(new File(hostside, "HostHelper.java"), "public class HostHelper {}");

        List<File> files = AndroidProjectScanner.scan(root);
        for (File f : files) {
            String p = f.getPath().replace(File.separatorChar, '/');
            assertFalse("AOSP test dir not excluded: " + p, p.contains("/tests/"));
            assertFalse("snake_case test dir not excluded: " + p,
                    p.contains("carservice_unit_test"));
            assertFalse("camelCase test dir not excluded: " + p,
                    p.contains("CarLibUnitTest"));
            assertFalse("hostsidetests not excluded: " + p,
                    p.contains("hostsidetests"));
        }

        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeTests = true;
        List<File> all = AndroidProjectScanner.scan(root, o);
        boolean foundAosp = false;
        for (File f : all) {
            if (f.getPath().replace(File.separatorChar, '/').contains("/tests/")) {
                foundAosp = true;
            }
        }
        assertTrue("--include-tests must restore AOSP tests/", foundAosp);
    }

    @Test
    public void testDirNamesContainingTestAsSubstringAreNotExcluded() throws IOException {
        // "latest" / "contest" のように test を部分文字列として含むだけの
        // ディレクトリは除外しない (単語単位の判定)
        File latest = new File(root, "app/src/main/java/com/example/latest");
        assertTrue(latest.mkdirs());
        writeFile(new File(latest, "LatestInfo.java"), "public class LatestInfo {}");

        List<File> files = AndroidProjectScanner.scan(root);
        boolean found = false;
        for (File f : files) {
            if (f.getName().equals("LatestInfo.java")) {
                found = true;
            }
        }
        assertTrue("dir 'latest' must not be treated as test dir", found);
    }

    @Test
    public void testTestFileNameExcludedEvenOutsideTestDir() throws IOException {
        // テストディレクトリ外に置かれた *Test.java もファイル名で除外する
        File mainJava = new File(root, "app/src/main/java/com/example/app");
        writeFile(new File(mainJava, "StrayUnitTest.java"),
                "public class StrayUnitTest {}");
        writeFile(new File(mainJava, "Contest.java"),
                "public class Contest {}");

        List<File> files = AndroidProjectScanner.scan(root);
        boolean foundStray = false;
        boolean foundContest = false;
        for (File f : files) {
            if (f.getName().equals("StrayUnitTest.java")) {
                foundStray = true;
            }
            if (f.getName().equals("Contest.java")) {
                foundContest = true;
            }
        }
        assertFalse("*Test.java must be excluded by default", foundStray);
        assertTrue("Contest.java must not be excluded", foundContest);

        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.includeTests = true;
        boolean restored = false;
        for (File f : AndroidProjectScanner.scan(root, o)) {
            if (f.getName().equals("StrayUnitTest.java")) {
                restored = true;
            }
        }
        assertTrue("--include-tests must restore *Test.java", restored);
    }

    @Test
    public void testIsTestFileName() {
        assertTrue(AndroidProjectScanner.isTestFileName("FooTest.java"));
        assertTrue(AndroidProjectScanner.isTestFileName("FooTests.java"));
        assertTrue(AndroidProjectScanner.isTestFileName("FooTestCase.java"));
        assertTrue(AndroidProjectScanner.isTestFileName("FooTest.kt"));
        assertFalse(AndroidProjectScanner.isTestFileName("Contest.java"));
        assertFalse(AndroidProjectScanner.isTestFileName("TestHelper.java"));
        assertFalse(AndroidProjectScanner.isTestFileName("Foo.java"));
    }

}
