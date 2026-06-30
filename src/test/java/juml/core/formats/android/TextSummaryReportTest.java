// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TextSummaryReport のユニットテスト。
 */
public class TextSummaryReportTest {

    private static AndroidProjectAnalysis build() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();

        GradleProjectInfo settings = new GradleProjectInfo();
        settings.getSubprojects().add("app");
        settings.getSubprojects().add("lib");
        a.setRootSettings(settings);

        GradleProjectInfo app = new GradleProjectInfo();
        app.setModuleName("app");
        app.getPlugins().add("com.android.application");
        app.setApplicationId("com.example.app");
        app.setNamespace("com.example.app");
        app.setMinSdk(24);
        app.setTargetSdk(34);
        app.setCompileSdk(34);
        app.setVersionCode(1);
        app.setVersionName("1.0");
        app.getBuildTypes().put("release", new GradleBuildType("release"));
        app.getProductFlavors().put("prod", new GradleProductFlavor("prod"));
        app.getDependencies().add(new GradleDependency("implementation",
                "androidx.appcompat:appcompat:1.6.1"));
        a.getGradleByModule().put("app", app);

        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.example.app");
        m.setApplicationClass("com.example.app.App");
        AndroidComponentInfo ac = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.example.app.MainActivity");
        ac.setExported(true);
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.getActions().add("android.intent.action.MAIN");
        f.getCategories().add("android.intent.category.LAUNCHER");
        ac.getIntentFilters().add(f);
        m.getActivities().add(ac);
        m.getPermissions().add(new AndroidPermissionInfo("android.permission.INTERNET"));
        m.getFeatures().add("android.hardware.camera");
        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        return a;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        TextSummaryReport.toMarkdown(null);
    }

    @Test
    public void testEmptyAnalysis() {
        String md = TextSummaryReport.toMarkdown(new AndroidProjectAnalysis());
        assertTrue(md, md.contains("# Android Project Summary"));
        assertTrue(md, md.contains("(no build.gradle files parsed)"));
        assertTrue(md, md.contains("(no AndroidManifest.xml components)"));
    }

    @Test
    public void testProjectInfoSection() {
        String md = TextSummaryReport.toMarkdown(build());
        assertTrue(md, md.contains("# Android Project Summary"));
        assertTrue(md, md.contains("## Modules"));
        assertTrue(md, md.contains("settings.gradle includes:"));
        assertTrue(md, md.contains("- app"));
        assertTrue(md, md.contains("- lib"));
    }

    @Test
    public void testModuleSection() {
        String md = TextSummaryReport.toMarkdown(build());
        assertTrue(md, md.contains("### `app`"));
        assertTrue(md, md.contains("**Android Application**"));
        assertTrue(md, md.contains("Application ID: `com.example.app`"));
        assertTrue(md, md.contains("minSdk: `24`"));
        assertTrue(md, md.contains("com.android.application"));
    }

    @Test
    public void testBuildTypesAndFlavors() {
        String md = TextSummaryReport.toMarkdown(build());
        assertTrue(md, md.contains("**Build Types:**"));
        assertTrue(md, md.contains("`release`"));
        assertTrue(md, md.contains("**Product Flavors:**"));
        assertTrue(md, md.contains("`prod`"));
    }

    @Test
    public void testDependenciesTable() {
        String md = TextSummaryReport.toMarkdown(build());
        assertTrue(md, md.contains("**Dependencies:**"));
        assertTrue(md, md.contains("androidx.appcompat:appcompat:1.6.1"));
        assertTrue(md, md.contains("implementation"));
    }

    @Test
    public void testComponentsSection() {
        String md = TextSummaryReport.toMarkdown(build());
        assertTrue(md, md.contains("## Components"));
        assertTrue(md, md.contains("**Activities:**"));
        assertTrue(md, md.contains("com.example.app.MainActivity"));
        assertTrue(md, md.contains("*(exported)*"));
        assertTrue(md, md.contains("*(launcher)*"));
    }

    @Test
    public void testPermissionsSection() {
        String md = TextSummaryReport.toMarkdown(build());
        assertTrue(md, md.contains("## Permissions"));
        assertTrue(md, md.contains("android.permission.INTERNET"));
    }

    @Test
    public void testFeaturesSection() {
        String md = TextSummaryReport.toMarkdown(build());
        assertTrue(md, md.contains("## Features"));
        assertTrue(md, md.contains("android.hardware.camera"));
    }

    @Test
    public void testMultipleManifestsLabelled() {
        // 同モジュールに main + debug の 2 manifest があるケース
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo main = new AndroidManifestInfo();
        main.setPackageName("p");
        main.setSourceSet("main");
        main.getActivities().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "p.MainActivity"));
        AndroidManifestInfo debug = new AndroidManifestInfo();
        debug.setPackageName("p");
        debug.setSourceSet("debug");
        debug.getReceivers().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.RECEIVER, "p.DebugReceiver"));
        java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
        list.add(main);
        list.add(debug);
        a.getManifestsByModule().put("app", list);

        String md = TextSummaryReport.toMarkdown(a);
        // モジュールヘッダに manifest 数が出る
        assertTrue(md, md.contains("Module `app` — 2 manifests"));
        // 各 manifest が sourceSet 名でラベル付け
        assertTrue(md, md.contains("#### sourceSet `main`"));
        assertTrue(md, md.contains("#### sourceSet `debug`"));
    }
}
