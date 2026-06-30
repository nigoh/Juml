// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.app.uml.DiagramKind;
import juml.app.uml.DiagramRequest;
import juml.app.uml.DiagramScope;
import juml.app.uml.DiagramService;
import juml.app.uml.PlantUmlSvgRenderer;
import juml.app.uml.ProjectAnalysisCache;
import juml.core.formats.android.AndroidLayoutInfo;
import juml.core.formats.android.AndroidNavigationGraphInfo;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GUI の描画パイプライン (プロジェクトをディスクから {@link ProjectAnalysisCache} で
 * ロード → 図種ごとに {@link DiagramService} で PlantUML 生成 →
 * {@link PlantUmlSvgRenderer} で Batik SVG にレンダリング) を、現実的なマルチモジュール
 * Android プロジェクトに対して全図種で通す end-to-end 統合テスト。
 *
 * <p>{@code UmlMainFrame.refreshDiagramNow} が SwingWorker 内で実行するのとまったく同じ
 * 連鎖を、Swing/Robot を使わずヘッドレスで再現する。図種を 1 つ追加・変更したときに
 * 「ロード〜描画」のどこかが壊れる回帰を広く検出することが目的。</p>
 *
 * <p>シーケンス/アクティビティ/コールグラフ図には起点 {@code Class.method} を、
 * レイアウト/ナビゲーション図には解析結果から取り出したキーを与える。</p>
 */
public class DiagramPipelineE2ETest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 全図種にデータが行き渡るマルチモジュール Android プロジェクトを生成する。
     * app (application) は lib (library) に project 依存し、Java クラス・メソッド・
     * AndroidManifest・layout・navigation を含む。
     */
    private File buildProject() throws IOException {
        File root = tmp.newFolder("PipelineSample");
        write(new File(root, "settings.gradle"),
                "rootProject.name = 'PipelineSample'\ninclude ':app', ':lib'\n");

        // --- app module ---
        write(new File(root, "app/build.gradle"),
                "plugins { id 'com.android.application' }\n"
                        + "android { namespace 'com.demo.app' }\n"
                        + "dependencies {\n"
                        + "  implementation project(':lib')\n"
                        + "  implementation 'androidx.core:core:1.12.0'\n"
                        + "}\n");
        write(new File(root, "app/src/main/AndroidManifest.xml"),
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"com.demo.app\">\n"
                        + "  <uses-permission android:name=\"android.permission.INTERNET\"/>\n"
                        + "  <application android:name=\".DemoApp\">\n"
                        + "    <activity android:name=\".MainActivity\" android:exported=\"true\">\n"
                        + "      <intent-filter>\n"
                        + "        <action android:name=\"android.intent.action.MAIN\"/>\n"
                        + "        <category android:name=\"android.intent.category.LAUNCHER\"/>\n"
                        + "      </intent-filter>\n"
                        + "    </activity>\n"
                        + "    <service android:name=\".SyncService\"/>\n"
                        + "  </application>\n"
                        + "</manifest>\n");
        write(new File(root, "app/src/main/java/com/demo/app/MainActivity.java"),
                "package com.demo.app;\n"
                        + "import com.demo.lib.BaseActivity;\n"
                        + "import com.demo.lib.Helper;\n"
                        + "/** Entry activity. */\n"
                        + "public class MainActivity extends BaseActivity {\n"
                        + "  private final Helper helper = new Helper();\n"
                        + "  public void onCreate() {\n"
                        + "    setup();\n"
                        + "    helper.greet(\"hi\");\n"
                        + "  }\n"
                        + "  void setup() {\n"
                        + "    for (int i = 0; i < 3; i++) {\n"
                        + "      if (i % 2 == 0) { helper.greet(\"even\"); } else { helper.greet(\"odd\"); }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");
        write(new File(root, "app/src/main/res/layout/activity_main.xml"),
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " android:layout_width=\"match_parent\""
                        + " android:layout_height=\"match_parent\">\n"
                        + "  <TextView android:id=\"@+id/title\""
                        + " android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\"/>\n"
                        + "  <Button android:id=\"@+id/ok\""
                        + " android:layout_width=\"wrap_content\""
                        + " android:layout_height=\"wrap_content\"/>\n"
                        + "</LinearLayout>\n");
        write(new File(root, "app/src/main/res/navigation/nav_graph.xml"),
                "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                        + " app:startDestination=\"@id/home\">\n"
                        + "  <fragment android:id=\"@+id/home\""
                        + " android:name=\"com.demo.app.HomeFragment\">\n"
                        + "    <action android:id=\"@+id/toDetail\" app:destination=\"@id/detail\"/>\n"
                        + "  </fragment>\n"
                        + "  <fragment android:id=\"@+id/detail\""
                        + " android:name=\"com.demo.app.DetailFragment\"/>\n"
                        + "</navigation>\n");

        // --- lib module ---
        write(new File(root, "lib/build.gradle"),
                "plugins { id 'com.android.library' }\n"
                        + "android { namespace 'com.demo.lib' }\n");
        write(new File(root, "lib/src/main/java/com/demo/lib/BaseActivity.java"),
                "package com.demo.lib;\n"
                        + "public class BaseActivity { protected void onStart() {} }\n");
        write(new File(root, "lib/src/main/java/com/demo/lib/Helper.java"),
                "package com.demo.lib;\n"
                        + "/** Shared helper used across the app. */\n"
                        + "public class Helper {\n"
                        + "  public void greet(String who) { System.out.println(who); }\n"
                        + "}\n");
        return root;
    }

    private static void assertRenders(String label, DiagramRequest req,
                                      ProjectAnalysisCache cache) throws IOException {
        String puml = DiagramService.generatePuml(req, cache);
        assertNotNull(label + ": puml", puml);
        // コールグラフは @startwbs (WBS 図) を使うため @startuml 固定では判定しない
        assertTrue(label + ": diagram header (@start...) missing", puml.contains("@start"));
        PlantUmlSvgRenderer.RenderedSvg svg = PlantUmlSvgRenderer.render(puml);
        assertNotNull(label + ": RenderedSvg null", svg);
        assertNotNull(label + ": GraphicsNode null", svg.getRoot());
        assertTrue(label + ": width should be > 0", svg.getWidth() > 0);
        assertTrue(label + ": height should be > 0", svg.getHeight() > 0);
        assertNotNull(label + ": linkAreas null", svg.getLinkAreas());
    }

    @Test
    public void testEveryDiagramKindLoadsAndRendersToSvg() throws IOException {
        File project = buildProject();
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(project, ErrorListener.silent(), null, null, null);
        assertTrue("project should load", cache.isLoaded());
        assertFalse("classes should be parsed", cache.getClasses().isEmpty());

        // 起点不要の図種
        for (DiagramKind k : new DiagramKind[]{
                DiagramKind.CLASS, DiagramKind.PACKAGE, DiagramKind.COMPONENT,
                DiagramKind.DEPENDENCY, DiagramKind.MANIFEST, DiagramKind.COMMON,
                DiagramKind.MODULE, DiagramKind.INHERITANCE}) {
            boolean wantLinks = (k == DiagramKind.CLASS || k == DiagramKind.INHERITANCE);
            assertRenders(k.name(),
                    new DiagramRequest(k, null, null, true, null, wantLinks), cache);
        }

        // メソッド起点の図種 (Class.method)
        assertRenders("SEQUENCE", new DiagramRequest(
                DiagramKind.SEQUENCE, "MainActivity", "onCreate", true, null, false), cache);
        assertRenders("ACTIVITY",
                DiagramRequest.forActivity("MainActivity", "setup", true), cache);
        assertRenders("CALLGRAPH",
                DiagramRequest.forCallGraph("MainActivity", "onCreate", true), cache);

        // スコープ付きクラス図 (ツリーのパッケージ/クラス選択相当)
        assertRenders("CLASS-scope-package", new DiagramRequest(DiagramKind.CLASS,
                null, null, true,
                DiagramScope.builder().includePackage("com.demo.app").build(), true), cache);
        assertRenders("CLASS-scope-seed", new DiagramRequest(DiagramKind.CLASS,
                null, null, true,
                DiagramScope.builder().seed("com.demo.app.MainActivity").neighborHops(1).build(),
                true), cache);

        // レイアウト図 / ナビゲーション図 (解析結果からキーを取得)
        assertNotNull(cache.getAnalysis());
        assertFalse("layouts should be found", cache.getAnalysis().allLayouts().isEmpty());
        AndroidLayoutInfo layout = cache.getAnalysis().allLayouts().get(0);
        assertRenders("LAYOUT", DiagramRequest.forLayout(layout.getKey(), true), cache);

        assertFalse("navigation graphs should be found",
                cache.getAnalysis().allNavigationGraphs().isEmpty());
        AndroidNavigationGraphInfo nav = cache.getAnalysis().allNavigationGraphs().get(0);
        assertRenders("NAVIGATION", DiagramRequest.forNavigationGraph(nav.getKey(), true), cache);
    }

    /**
     * 依存グラフがモジュール間 {@code project()} 依存を描くこと (app → lib) を
     * ロード経路まで含めて確認する。
     */
    @Test
    public void testDependencyGraphShowsModuleEdge() throws IOException {
        File project = buildProject();
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(project, ErrorListener.silent(), null, null, null);
        String puml = DiagramService.generatePuml(
                new DiagramRequest(DiagramKind.DEPENDENCY), cache);
        assertTrue("module-to-module edge expected: " + puml,
                java.util.regex.Pattern.compile("(?m)^M\\d+ --> M\\d+ : ")
                        .matcher(puml).find());
    }
}
