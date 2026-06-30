// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * AndroidProjectAnalyzer のユニットテスト。
 *
 * <p>{@link TemporaryFolder} で Android Gradle プロジェクト構造を模して、
 * settings.gradle / モジュール毎の build.gradle / AndroidManifest.xml が
 * 解析対象になることを検証する。</p>
 */
public class AndroidProjectAnalyzerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File root;
    private File appDir;
    private File libDir;

    @Before
    public void setupProject() throws IOException {
        root = tmp.newFolder("MyApp");
        write(new File(root, "settings.gradle"),
                "include ':app', ':lib'\n");
        write(new File(root, "build.gradle"),
                "// project-level\n");

        appDir = new File(root, "app");
        assertTrue(appDir.mkdirs());
        write(new File(appDir, "build.gradle"),
                "plugins { id 'com.android.application' }\n"
                        + "android {\n"
                        + "  namespace 'com.example.app'\n"
                        + "  compileSdk 34\n"
                        + "  defaultConfig {\n"
                        + "    applicationId 'com.example.app'\n"
                        + "    minSdk 24\n"
                        + "    targetSdk 34\n"
                        + "  }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "  implementation project(':lib')\n"
                        + "  implementation 'androidx.appcompat:appcompat:1.6.1'\n"
                        + "}\n");

        File appManifestDir = new File(appDir, "src/main");
        assertTrue(appManifestDir.mkdirs());
        write(new File(appManifestDir, "AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"com.example.app\">\n"
                        + "  <uses-permission android:name=\"android.permission.INTERNET\"/>\n"
                        + "  <application android:name=\".App\">\n"
                        + "    <activity android:name=\".MainActivity\""
                        + " android:exported=\"true\">\n"
                        + "      <intent-filter>\n"
                        + "        <action android:name=\"android.intent.action.MAIN\"/>\n"
                        + "        <category android:name="
                        + "\"android.intent.category.LAUNCHER\"/>\n"
                        + "      </intent-filter>\n"
                        + "    </activity>\n"
                        + "    <service android:name=\".PushService\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n");

        libDir = new File(root, "lib");
        assertTrue(libDir.mkdirs());
        write(new File(libDir, "build.gradle"),
                "plugins { id 'com.android.library' }\n"
                        + "android {\n"
                        + "  namespace 'com.example.lib'\n"
                        + "  compileSdk 34\n"
                        + "}\n");

        File libManifestDir = new File(libDir, "src/main");
        assertTrue(libManifestDir.mkdirs());
        write(new File(libManifestDir, "AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"com.example.lib\">\n"
                        + "  <application/>\n"
                        + "</manifest>\n");
    }

    private static void write(File f, String content) throws IOException {
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullRoot() throws IOException {
        AndroidProjectAnalyzer.analyze(null);
    }

    @Test
    public void testFindsAllGradleAndManifest() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        assertNotNull(a.getRootSettings());
        assertTrue(a.getRootSettings().getSubprojects().contains("app"));
        assertTrue(a.getGradleByModule().containsKey("app"));
        assertTrue(a.getGradleByModule().containsKey("lib"));
        assertEquals(2, a.allManifests().size());
    }

    @Test
    public void testGradleParsedCorrectly() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        GradleProjectInfo app = a.getGradleByModule().get("app");
        assertEquals("com.example.app", app.getApplicationId());
        assertEquals(Integer.valueOf(24), app.getMinSdk());
        assertTrue(app.isAndroidApplication());
        GradleProjectInfo lib = a.getGradleByModule().get("lib");
        assertTrue(lib.isAndroidLibrary());
    }

    @Test
    public void testManifestParsedCorrectly() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        boolean foundActivity = false;
        boolean foundService = false;
        for (AndroidComponentInfo c : a.allComponents()) {
            if ("com.example.app.MainActivity".equals(c.getName())) {
                foundActivity = true;
                assertTrue(c.isLauncher());
            }
            if ("com.example.app.PushService".equals(c.getName())) {
                foundService = true;
            }
        }
        assertTrue(foundActivity);
        assertTrue(foundService);
    }

    @Test
    public void testListenerSummary() throws IOException {
        List<String> log = new ArrayList<>();
        AndroidProjectAnalyzer.analyze(root, ErrorListener.collecting(log));
        boolean summary = false;
        for (String s : log) {
            if (s.contains("android analyzer")) {
                summary = true;
            }
        }
        assertTrue("expected analyzer summary: " + log, summary);
    }

    @Test
    public void testFindComponentByFqn() throws IOException {
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        AndroidComponentInfo c = a.findComponentByFqn("com.example.app.MainActivity");
        assertNotNull(c);
        assertEquals(AndroidComponentInfo.Kind.ACTIVITY, c.getKind());
        assertNull(a.findComponentByFqn("nope.NotFound"));
    }

    @Test
    public void testInferModuleNameForRoot() {
        String name = AndroidProjectAnalyzer.inferModuleName(root,
                new File(root, "build.gradle"));
        assertEquals(":root", name);
    }

    /**
     * Gradle のサブプロジェクトのディレクトリ単体を開いたときの回帰テスト (end-to-end)。
     *
     * <p>親リポジトリの settings.gradle で {@code :feature:lib} として登録されたモジュールを、
     * その {@code feature/} ディレクトリだけをルートとして開くと、モジュール名はルート相対の
     * {@code "lib"} になる。一方で各モジュールの build.gradle は親リポジトリ向けに書かれており
     * {@code project(':feature:lib')} という絶対 Gradle パスで参照する。
     * このズレで完全一致だけではモジュール間 {@code project()} 依存エッジが脱落していた。
     * {@link AndroidProjectAnalyzer} → {@link PlantUmlGradleDependencyGraph} の経路で
     * エッジが復元されることを確認する。</p>
     */
    @Test
    public void testDependencyGraphLinksModulesWhenSubprojectDirOpened() throws IOException {
        File feature = tmp.newFolder("feature");
        File featApp = new File(feature, "app");
        File featLib = new File(feature, "lib");
        assertTrue(featApp.mkdirs());
        assertTrue(featLib.mkdirs());
        write(new File(featApp, "build.gradle"),
                "plugins { id 'com.android.application' }\n"
                        + "dependencies {\n"
                        + "  implementation project(':feature:lib')\n"
                        + "  implementation 'androidx.core:core:1.0.0'\n"
                        + "}\n");
        write(new File(featLib, "build.gradle"),
                "plugins { id 'com.android.library' }\n"
                        + "dependencies {\n"
                        + "  implementation 'androidx.annotation:annotation:1.0.0'\n"
                        + "}\n");

        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(feature);
        // モジュール名はルート相対 ("app" / "lib") に短縮される
        assertTrue(a.getGradleByModule().containsKey("app"));
        assertTrue(a.getGradleByModule().containsKey("lib"));

        String puml = PlantUmlGradleDependencyGraph.generate(a);
        assertTrue("module-to-module edge must survive root-relative naming: " + puml,
                java.util.regex.Pattern.compile("(?m)^M\\d+ --> M\\d+ : ")
                        .matcher(puml).find());
    }

    @Test
    public void testMultipleManifestsPerModule() throws IOException {
        // app モジュールに main + debug + 独自 flavor の 3 つの manifest を追加
        File appDebug = new File(appDir, "src/debug");
        assertTrue(appDebug.mkdirs());
        write(new File(appDebug, "AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"com.example.app\">\n"
                        + "  <application>\n"
                        + "    <receiver android:name=\".DebugReceiver\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n");
        File appProd = new File(appDir, "src/prod");
        assertTrue(appProd.mkdirs());
        write(new File(appProd, "AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"com.example.app\">\n"
                        + "  <application>\n"
                        + "    <service android:name=\".ProdService\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n");

        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        List<AndroidManifestInfo> mans = a.getManifestsByModule().get("app");
        assertNotNull(mans);
        assertEquals(3, mans.size());

        java.util.Set<String> sourceSets = new java.util.HashSet<>();
        for (AndroidManifestInfo m : mans) {
            sourceSets.add(m.getSourceSet());
        }
        assertTrue("main", sourceSets.contains("main"));
        assertTrue("debug", sourceSets.contains("debug"));
        assertTrue("prod", sourceSets.contains("prod"));
    }

    @Test
    public void testInferSourceSet() {
        File main = new File("/proj/app/src/main/AndroidManifest.xml");
        assertEquals("main", AndroidProjectAnalyzer.inferSourceSet(main));
        File debug = new File("/proj/app/src/debug/AndroidManifest.xml");
        assertEquals("debug", AndroidProjectAnalyzer.inferSourceSet(debug));
        File flavor = new File("/proj/app/src/prod/AndroidManifest.xml");
        assertEquals("prod", AndroidProjectAnalyzer.inferSourceSet(flavor));
        // src/ 配下でないファイルは "main" にフォールバック
        File stray = new File("/somewhere/else/AndroidManifest.xml");
        assertEquals("main", AndroidProjectAnalyzer.inferSourceSet(stray));
    }

    // --- layout 解析 (新機能) ---

    @Test
    public void testLayoutsByModuleIsAlwaysInitialized() throws IOException {
        // layout ファイルを置かない既存セットアップでも null にならず空 Map を返す
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        assertNotNull("layoutsByModule must not be null", a.getLayoutsByModule());
        assertTrue("既存 allManifests は従来通り動く", a.allManifests().size() >= 2);
        assertTrue("layout 無しでも allLayouts は空リスト", a.allLayouts().isEmpty());
    }

    @Test
    public void testLayoutsParsedFromResLayoutDir() throws IOException {
        File mainLayout = new File(appDir, "src/main/res/layout");
        assertTrue(mainLayout.mkdirs());
        write(new File(mainLayout, "activity_main.xml"),
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " android:id=\"@+id/root\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\">"
                        + "<TextView android:id=\"@+id/t\""
                        + " android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\""
                        + " android:text=\"hi\"/>"
                        + "</LinearLayout>");

        File landLayout = new File(appDir, "src/main/res/layout-land");
        assertTrue(landLayout.mkdirs());
        write(new File(landLayout, "activity_main.xml"),
                "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/>");

        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        List<AndroidLayoutInfo> appLayouts = a.getLayoutsByModule().get("app");
        assertNotNull(appLayouts);
        assertEquals(2, appLayouts.size());

        boolean foundPortrait = false;
        boolean foundLandscape = false;
        for (AndroidLayoutInfo l : appLayouts) {
            assertEquals("app", l.getModuleName());
            assertEquals("main", l.getSourceSet());
            assertNotNull(l.getRoot());
            if ("activity_main.xml".equals(l.getFileName())
                    && l.getConfigQualifier().isEmpty()) {
                foundPortrait = true;
                assertEquals("LinearLayout", l.getRoot().getTag());
                assertEquals(1, l.getRoot().getChildren().size());
            }
            if ("activity_main.xml".equals(l.getFileName())
                    && "land".equals(l.getConfigQualifier())) {
                foundLandscape = true;
                assertEquals("FrameLayout", l.getRoot().getTag());
            }
        }
        assertTrue("default layout found", foundPortrait);
        assertTrue("land layout found", foundLandscape);
    }

    @Test
    public void testLayoutsBuildStableKeys() throws IOException {
        File mainLayout = new File(appDir, "src/main/res/layout");
        assertTrue(mainLayout.mkdirs());
        write(new File(mainLayout, "screen.xml"),
                "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\"/>");
        AndroidProjectAnalysis a = AndroidProjectAnalyzer.analyze(root);
        AndroidLayoutInfo info = a.getLayoutsByModule().get("app").get(0);
        // key は module::sourceSet::qualifier::fileName 形式
        assertEquals("app::main::::screen.xml", info.getKey());
        AndroidLayoutInfo found = a.findLayoutByKey("app::main::::screen.xml");
        assertSame(info, found);
        assertNull(a.findLayoutByKey("not::a::real::key.xml"));
        assertNull(a.findLayoutByKey(null));
    }

    @Test
    public void testInferLayoutConfigQualifier() {
        File def = new File("/p/app/src/main/res/layout/x.xml");
        assertEquals("", AndroidProjectAnalyzer.inferLayoutConfigQualifier(def));
        File land = new File("/p/app/src/main/res/layout-land/x.xml");
        assertEquals("land", AndroidProjectAnalyzer.inferLayoutConfigQualifier(land));
        File multi = new File("/p/app/src/main/res/layout-sw600dp-v21/x.xml");
        assertEquals("sw600dp-v21",
                AndroidProjectAnalyzer.inferLayoutConfigQualifier(multi));
    }

    @Test
    public void testInferLayoutSourceSet() {
        File main = new File("/p/app/src/main/res/layout/x.xml");
        assertEquals("main", AndroidProjectAnalyzer.inferLayoutSourceSet(main));
        File debug = new File("/p/app/src/debug/res/layout-land/x.xml");
        assertEquals("debug", AndroidProjectAnalyzer.inferLayoutSourceSet(debug));
        File flavor = new File("/p/app/src/prod/res/layout/x.xml");
        assertEquals("prod", AndroidProjectAnalyzer.inferLayoutSourceSet(flavor));
        // 想定外パスは main フォールバック
        File stray = new File("/somewhere/x.xml");
        assertEquals("main", AndroidProjectAnalyzer.inferLayoutSourceSet(stray));
    }
}
