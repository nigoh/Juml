// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link PlantUmlDeepLinkDiagram} のユニットテスト。
 */
public class PlantUmlDeepLinkDiagramTest {

    private static AndroidProjectAnalysis buildAnalysis() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.x");

        // App Link (https) — autoVerify=true
        AndroidComponentInfo webEntry = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.WebEntry");
        webEntry.setExported(true);
        AndroidIntentFilter web = new AndroidIntentFilter();
        web.setAutoVerify(true);
        web.getActions().add("android.intent.action.VIEW");
        web.getCategories().add("android.intent.category.DEFAULT");
        web.getCategories().add("android.intent.category.BROWSABLE");
        AndroidDataSpec ws = new AndroidDataSpec();
        ws.setScheme("https");
        ws.setHost("example.com");
        ws.setPathPrefix("/share");
        web.getDataSpecs().add(ws);
        webEntry.getIntentFilters().add(web);

        // Custom scheme deep link
        AndroidComponentInfo customEntry = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.CustomEntry");
        AndroidIntentFilter custom = new AndroidIntentFilter();
        custom.getActions().add("android.intent.action.VIEW");
        custom.getCategories().add("android.intent.category.BROWSABLE");
        AndroidDataSpec cs = new AndroidDataSpec();
        cs.setScheme("myapp");
        cs.setHost("open");
        custom.getDataSpecs().add(cs);
        customEntry.getIntentFilters().add(custom);

        // mimeType-only filter (URI なし)
        AndroidComponentInfo mimeEntry = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.MimeEntry");
        AndroidIntentFilter mime = new AndroidIntentFilter();
        mime.getActions().add("android.intent.action.VIEW");
        mime.getCategories().add("android.intent.category.BROWSABLE");
        AndroidDataSpec ms = new AndroidDataSpec();
        ms.setMimeType("application/pdf");
        mime.getDataSpecs().add(ms);
        mimeEntry.getIntentFilters().add(mime);

        m.getActivities().add(webEntry);
        m.getActivities().add(customEntry);
        m.getActivities().add(mimeEntry);
        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        return a;
    }

    @Test
    public void testNullInputThrows() {
        try {
            PlantUmlDeepLinkDiagram.generate(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }

    @Test
    public void testEmptyAnalysisProducesPlaceholder() {
        String puml = PlantUmlDeepLinkDiagram.generate(new AndroidProjectAnalysis());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("no deep link intent-filter found"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testWebAppLinkRendered() {
        String puml = PlantUmlDeepLinkDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("Web (http/https)"));
        assertTrue(puml, puml.contains("https://example.com/share*"));
        assertTrue(puml, puml.contains("<<applink>>"));
        assertTrue(puml, puml.contains("<<autoVerify>>"));
        assertTrue(puml, puml.contains("autoVerify"));
    }

    @Test
    public void testCustomSchemeRendered() {
        String puml = PlantUmlDeepLinkDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("Custom scheme: myapp://"));
        assertTrue(puml, puml.contains("myapp://open"));
        assertTrue(puml, puml.contains("<<deeplink>>"));
    }

    @Test
    public void testMimeOnlyRendered() {
        String puml = PlantUmlDeepLinkDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("MIME-only"));
        assertTrue(puml, puml.contains("application/pdf"));
        assertTrue(puml, puml.contains("<<mime>>"));
    }

    @Test
    public void testMimeOnlySuppressed() {
        PlantUmlDeepLinkDiagram.Options o = new PlantUmlDeepLinkDiagram.Options();
        o.showMimeOnly = false;
        String puml = PlantUmlDeepLinkDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("MIME-only"));
    }

    @Test
    public void testActivityNodeContainsShortName() {
        String puml = PlantUmlDeepLinkDiagram.generate(buildAnalysis());
        // FQN ではなく短縮名で表示する
        assertTrue(puml, puml.contains("WebEntry"));
        assertTrue(puml, puml.contains("CustomEntry"));
    }

    @Test
    public void testLegendByDefault() {
        String puml = PlantUmlDeepLinkDiagram.generate(buildAnalysis());
        assertTrue(puml, puml.contains("legend top left"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlDeepLinkDiagram.Options o = new PlantUmlDeepLinkDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlDeepLinkDiagram.generate(buildAnalysis(), o);
        assertFalse(puml, puml.contains("legend top left"));
    }

    @Test
    public void testNonDeepLinkActivityIgnored() {
        // VIEW + BROWSABLE を持たない通常 Activity は無視される
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        AndroidManifestInfo m = new AndroidManifestInfo();
        m.setPackageName("com.x");
        AndroidComponentInfo activity = new AndroidComponentInfo(
                AndroidComponentInfo.Kind.ACTIVITY, "com.x.MainActivity");
        AndroidIntentFilter launcher = new AndroidIntentFilter();
        launcher.getActions().add("android.intent.action.MAIN");
        launcher.getCategories().add("android.intent.category.LAUNCHER");
        activity.getIntentFilters().add(launcher);
        m.getActivities().add(activity);
        List<AndroidManifestInfo> list = new ArrayList<>();
        list.add(m);
        a.getManifestsByModule().put("app", list);
        String puml = PlantUmlDeepLinkDiagram.generate(a);
        assertTrue(puml, puml.contains("no deep link intent-filter found"));
    }
}
