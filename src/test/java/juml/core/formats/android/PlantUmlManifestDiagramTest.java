// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * PlantUmlManifestDiagram のユニットテスト。
 */
public class PlantUmlManifestDiagramTest {

    private static AndroidProjectAnalysis buildAnalysis() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.x");
        m.setApplicationClass("com.x.MyApp");
        m.setApplicationLabel("@string/app_name");
        m.setApplicationTheme("@style/AppTheme");
        m.setApplicationDebuggable(Boolean.TRUE);
        m.setApplicationAllowBackup(Boolean.FALSE);
        m.getApplicationMetaData().put("com.google.android.geo.API_KEY", "AIza...");

        AndroidComponentInfo activity = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.MainActivity");
        activity.setExported(true);
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.getActions().add("android.intent.action.MAIN");
        f.getCategories().add("android.intent.category.LAUNCHER");
        activity.getIntentFilters().add(f);
        m.getActivities().add(activity);

        AndroidComponentInfo service = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.SERVICE, "com.x.PushService");
        m.getServices().add(service);

        AndroidComponentInfo receiver = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.RECEIVER, "com.x.BootReceiver");
        m.getReceivers().add(receiver);

        AndroidPermissionInfo perm = new AndroidPermissionInfo("android.permission.INTERNET");
        m.getPermissions().add(perm);
        m.getFeatures().add("android.hardware.camera");

        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        return a;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        PlantUmlManifestDiagram.generate(null);
    }

    @Test
    public void testEmptyAnalysis() {
        String puml = PlantUmlManifestDiagram.generate(new AndroidProjectAnalysis());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("no AndroidManifest.xml found"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testApplicationBlock() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("rectangle \"Application"));
        assertTrue(puml, puml.contains("package: com.x"));
        assertTrue(puml, puml.contains("class: com.x.MyApp"));
        assertTrue(puml, puml.contains("theme: @style/AppTheme"));
        assertTrue(puml, puml.contains("debuggable: true"));
        assertTrue(puml, puml.contains("allowBackup: false"));
        assertTrue(puml, puml.contains("<<application>>"));
    }

    @Test
    public void testApplicationMetaDataIncluded() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("com.google.android.geo.API_KEY"));
    }

    @Test
    public void testApplicationMetaDataSuppressed() {
        PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
        o.showMetaData = false;
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("com.google.android.geo.API_KEY"));
    }

    @Test
    public void testComponentsGrouped() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("package \"Activities (1)\""));
        assertTrue(puml, puml.contains("package \"Services (1)\""));
        assertTrue(puml, puml.contains("package \"Receivers (1)\""));
        assertTrue(puml, puml.contains("MainActivity"));
        assertTrue(puml, puml.contains("PushService"));
        assertTrue(puml, puml.contains("BootReceiver"));
    }

    @Test
    public void testActivityStereotype() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("<<Activity>>"));
        assertTrue(puml, puml.contains("<<Service>>"));
        assertTrue(puml, puml.contains("<<BroadcastReceiver>>"));
    }

    @Test
    public void testLauncherAndExported() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("<<launcher>>"));
        assertTrue(puml, puml.contains("#LightYellow"));
    }

    @Test
    public void testGroupByModuleByDefault() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("package \"module: app\""));
    }

    @Test
    public void testGroupByModuleDisabled() {
        PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
        o.groupByModule = false;
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("package \"module: app\""));
        // 中身は引き続き存在
        assertTrue(puml, puml.contains("MainActivity"));
    }

    @Test
    public void testApplicationContainsComponents() {
        // Application ノードからグループに *-- 矢印が引かれる
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        // "APPx *-- Gy" のような構成
        assertTrue(puml, puml.matches("(?s).*APP\\d+ \\*-- G\\d+.*"));
    }

    @Test
    public void testPermissionsRendered() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("package \"uses-permission\""));
        assertTrue(puml, puml.contains("[INTERNET]"));
        assertTrue(puml, puml.contains("<<permission>>"));
    }

    @Test
    public void testPermissionsSuppressed() {
        PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
        o.showPermissions = false;
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("uses-permission"));
    }

    @Test
    public void testFeaturesRendered() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("package \"uses-feature\""));
        assertTrue(puml, puml.contains("camera"));
        assertTrue(puml, puml.contains("<<feature>>"));
    }

    @Test
    public void testFeaturesSuppressed() {
        PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
        o.showFeatures = false;
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("uses-feature"));
    }

    @Test
    public void testLegendByDefault() {
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("legend top left"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlManifestDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("legend top left"));
    }

    @Test
    public void testSourceSetStereotype() {
        // main 以外の sourceSet があると <<src:debug>> 等が付く
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo debug = new AndroidManifestInfo();
        debug.setPackageName("com.x");
        debug.setSourceSet("debug");
        debug.getActivities().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.DebugActivity"));
        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(debug);
        a.getManifestsByModule().put("app", list);
        String puml = PlantUmlManifestDiagram.generate(a);
        assertTrue(puml, puml.contains("<<src:debug>>"));
        // sourceSet 表示が Application ヘッダにも入る
        assertTrue(puml, puml.contains("Application [debug]"));
    }

    @Test
    public void testMainSourceSetHasNoStereotype() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("p");
        m.setSourceSet("main");
        m.getActivities().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "p.A"));
        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        String puml = PlantUmlManifestDiagram.generate(a);
        assertFalse(puml, puml.contains("<<src:main>>"));
        assertFalse(puml, puml.contains("Application [main]"));
    }

    @Test
    public void testMultipleManifestsInModule() {
        // 同一モジュール内に main / debug の 2 manifest がある場合、
        // それぞれが別 Application ノードとして並ぶ。
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo main = new AndroidManifestInfo();
        main.setPackageName("com.x");
        main.setSourceSet("main");
        main.getActivities().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.A"));
        AndroidManifestInfo debug = new AndroidManifestInfo();
        debug.setPackageName("com.x");
        debug.setSourceSet("debug");
        debug.getReceivers().add(new AndroidComponentInfo(
                AndroidComponentInfo.Kind.RECEIVER, "com.x.DebugReceiver"));
        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(main);
        list.add(debug);
        a.getManifestsByModule().put("app", list);
        String puml = PlantUmlManifestDiagram.generate(a);
        // Application ノードが 2 個 (main + debug) 並ぶ
        int appCount = puml.split("rectangle \"Application").length - 1;
        assertEquals(2, appCount);
    }
}
