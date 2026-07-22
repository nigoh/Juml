// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.integration;

import juml.app.uml.DiagramKind;
import juml.app.uml.DiagramRequest;
import juml.app.uml.DiagramService;
import juml.app.uml.PlantUmlSvgRenderer;
import juml.app.uml.ProjectAnalysisCache;
import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * E2E ({@link DiagramPipelineE2ETest}) が未カバーの図種を、AOSP / レイアウト系の合成
 * フィクスチャで実際に生成 → 実レンダリングし、「描けるか / 空か / 落ちるか」の成熟度を
 * 実測するプローブ。対象: CYCLES / LAYOUT_SCREEN / LAYOUT_RENDER / RESOURCE_LINK /
 * SCREEN_FLOW / SOONG / BUILD_NINJA / INTERMEDIATES。
 *
 * <p>各図種の生成 PlantUML が @startuml/@enduml で囲まれ、SVG レンダリングが例外なく
 * 幅・高さ &gt; 0 になることを固定する (空・不正入力でクラッシュしないことの回帰防止)。
 * 実行結果は標準出力へ「図種 → 本文行数 / SVG サイズ」で出し、成熟度判断の材料にする。</p>
 */
public class DiagramMaturityProbeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    /** AOSP / Android リソースの入力を一通り含む合成プロジェクトを作る。 */
    private File buildAospProject() throws IOException {
        File root = tmp.newFolder("AospSample");
        // Gradle 構造 (依存グラフ・パッケージ循環用の Java も置く)
        write(new File(root, "settings.gradle"), "rootProject.name='AospSample'\ninclude ':app'\n");
        write(new File(root, "app/build.gradle"),
                "plugins { id 'com.android.application' }\nandroid { namespace 'com.demo' }\n");
        // パッケージ循環 (a→b→a) を作って CYCLES に材料を与える
        write(new File(root, "app/src/main/java/com/demo/a/Alpha.java"),
                "package com.demo.a;\nimport com.demo.b.Beta;\n"
                        + "public class Alpha { Beta b; }\n");
        write(new File(root, "app/src/main/java/com/demo/b/Beta.java"),
                "package com.demo.b;\nimport com.demo.a.Alpha;\n"
                        + "public class Beta { Alpha a; void go(){ new Alpha(); } }\n");
        // AndroidManifest + Activity (SCREEN_FLOW / RESOURCE_LINK の起点)
        write(new File(root, "app/src/main/AndroidManifest.xml"),
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android' "
                        + "package='com.demo'>\n<application>\n"
                        + "<activity android:name='.a.Alpha'>\n<intent-filter>\n"
                        + "<action android:name='android.intent.action.VIEW'/>\n"
                        + "<category android:name='android.intent.category.BROWSABLE'/>\n"
                        + "<data android:scheme='https' android:host='demo.example'/>\n"
                        + "</intent-filter>\n</activity>\n</application>\n</manifest>\n");
        // layout XML (LAYOUT_SCREEN / LAYOUT_RENDER / RESOURCE_LINK)
        write(new File(root, "app/src/main/res/layout/activity_main.xml"),
                "<LinearLayout xmlns:android='http://schemas.android.com/apk/res/android' "
                        + "android:layout_width='match_parent' android:layout_height='match_parent' "
                        + "android:orientation='vertical'>\n"
                        + "<TextView android:id='@+id/title' android:layout_width='match_parent' "
                        + "android:layout_height='wrap_content' android:text='@string/app_name'/>\n"
                        + "<Button android:id='@+id/ok' android:layout_width='120dp' "
                        + "android:layout_height='48dp' android:text='OK'/>\n"
                        + "</LinearLayout>\n");
        write(new File(root, "app/src/main/res/values/strings.xml"),
                "<resources><string name='app_name'>Demo</string></resources>\n");
        // コードから R.layout / R.string を参照 (RESOURCE_LINK の紐づけ材料)
        write(new File(root, "app/src/main/java/com/demo/a/Ui.java"),
                "package com.demo.a;\npublic class Ui {\n"
                        + "  void f(){ int x = R.layout.activity_main; int y = R.string.app_name; }\n"
                        + "  static final class R { static final class layout { static int "
                        + "activity_main; } static final class string { static int app_name; } }\n}\n");
        // Android.bp (SOONG)
        write(new File(root, "Android.bp"),
                "cc_library {\n  name: \"libdemo\",\n  srcs: [\"a.c\"],\n"
                        + "  shared_libs: [\"liblog\"],\n}\n"
                        + "cc_binary {\n  name: \"demo\",\n  shared_libs: [\"libdemo\"],\n}\n");
        // build.ninja (BUILD_NINJA)
        write(new File(root, "out/soong/build.ninja"),
                "rule cc\n  command = clang $in -o $out\n"
                        + "build out/a.o: cc a.c\n"
                        + "build out/demo: cc out/a.o\n");
        // .intermediates (INTERMEDIATES)
        write(new File(root, "out/target/product/generic/obj/SHARED_LIBRARIES/"
                + "libdemo_intermediates/libdemo.so"), "\0\0binary\0\0");
        write(new File(root, "out/target/product/generic/obj/EXECUTABLES/"
                + "demo_intermediates/demo"), "\0\0binary\0\0");
        return root;
    }

    private String probe(String label, DiagramRequest req, ProjectAnalysisCache cache)
            throws IOException {
        String puml;
        try {
            puml = DiagramService.generatePuml(req, cache);
        } catch (RuntimeException ex) {
            System.out.println("[maturity] " + label + " -> GENERATE THREW: " + ex);
            throw ex;
        }
        // LAYOUT_RENDER は実寸 SVG を直接生成する (PlantUML を経由しない) 図種。
        String trimmed = puml.stripLeading();
        boolean directSvg = trimmed.startsWith("<?xml") || trimmed.startsWith("<svg");
        int lines = puml.split("\n", -1).length;
        if (directSvg) {
            assertTrue(label + ": direct SVG should contain <svg", puml.contains("<svg"));
            assertTrue(label + ": direct SVG should be non-trivial", puml.length() > 200);
            System.out.printf("[maturity] %-16s lines=%3d directSVG bytes=%d%n",
                    label, lines, puml.length());
            return puml;
        }
        assertTrue(label + ": puml should start with @start: " + firstLine(puml),
                puml.contains("@start"));
        assertTrue(label + ": puml should end with @end", puml.contains("@end"));
        var svg = PlantUmlSvgRenderer.render(puml);
        System.out.printf("[maturity] %-16s lines=%3d svg=%.0fx%.0f%n",
                label, lines, svg.getWidth(), svg.getHeight());
        assertTrue(label + ": svg width > 0", svg.getWidth() > 0);
        assertTrue(label + ": svg height > 0", svg.getHeight() > 0);
        return puml;
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, Math.min(nl, 80));
    }

    @Test
    public void uncoveredKindsRenderOrDegradeGracefully() throws IOException {
        File project = buildAospProject();
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(project, ErrorListener.silent(), null, null, null);
        assertTrue("project should load", cache.isLoaded());

        // 起点不要 (プロジェクトルート経由)
        probe("CYCLES", new DiagramRequest(DiagramKind.CYCLES), cache);
        probe("RESOURCE_LINK", new DiagramRequest(DiagramKind.RESOURCE_LINK), cache);
        probe("SCREEN_FLOW", new DiagramRequest(DiagramKind.SCREEN_FLOW), cache);
        probe("SOONG", new DiagramRequest(DiagramKind.SOONG), cache);
        probe("BUILD_NINJA", new DiagramRequest(DiagramKind.BUILD_NINJA), cache);
        probe("INTERMEDIATES", new DiagramRequest(DiagramKind.INTERMEDIATES), cache);

        // レイアウトキー経由
        var analysis = cache.getAnalysis();
        if (analysis != null && !analysis.allLayouts().isEmpty()) {
            String key = analysis.allLayouts().get(0).getKey();
            probe("LAYOUT_SCREEN", DiagramRequest.forLayoutScreen(key, true), cache);
            probe("LAYOUT_RENDER", DiagramRequest.forLayoutRender(key, true), cache);
        } else {
            System.out.println("[maturity] LAYOUT_SCREEN/RENDER -> no layouts parsed (input gap)");
        }
    }
}
