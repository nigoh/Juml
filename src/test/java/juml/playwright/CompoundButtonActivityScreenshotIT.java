// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.formats.uml.PlantUmlActivityDiagram;
import juml.core.formats.uml.PlantUmlRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class CompoundButtonActivityScreenshotIT {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Playwright playwright;
    private static Browser browser;

    @BeforeClass
    public static void setupBrowser() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
        } catch (Throwable ex) {
            Assume.assumeNoException("Playwright not available: " + ex.getMessage(), ex);
        }
    }

    @AfterClass
    public static void teardown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    public void screenshotCompoundButtonListenerActivity() throws Exception {
        String src = ""
                + "class SettingsFragment {\n"
                + "  private IService mService;\n"
                + "  void onViewCreated() {\n"
                + "    toggle.setOnCheckedChangeListener((b, checked) -> mService.update(checked));\n"
                + "  }\n"
                + "}";

        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);

        PlantUmlActivityDiagram.Options opts = new PlantUmlActivityDiagram.Options();
        String puml = PlantUmlActivityDiagram.generate(classes, "SettingsFragment", "onViewCreated", opts);
        System.out.println("=== Generated Activity PlantUML ===\n" + puml);

        // SVG レンダリング
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, bos);
        String svg = bos.toString(StandardCharsets.UTF_8.name());

        // HTML ラップ
        File html = tmp.newFile("compound_act_.html");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
            w.write("<!DOCTYPE html><html><head><meta charset='utf-8'>"
                    + "<title>CompoundButton Activity</title>"
                    + "<style>body { margin: 20px; background: white; font-family: sans-serif; }"
                    + "h2 { color: #333; }</style></head><body>"
                    + "<h2>setOnCheckedChangeListener アクティビティ図</h2>");
            w.write(svg);
            w.write("</body></html>");
        }

        File out = new File("build/playwright");
        out.mkdirs();
        File screenshot = new File(out, "compoundbutton-activity.png");

        try (Page page = browser.newPage()) {
            page.setViewportSize(800, 600);
            page.navigate(html.toURI().toString());
            page.waitForLoadState();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(screenshot.toPath())
                    .setType(ScreenshotType.PNG)
                    .setFullPage(true));
        }

        System.out.println("Screenshot saved: " + screenshot.getAbsolutePath());
        assertTrue("screenshot exists", screenshot.exists() && screenshot.length() > 0);
    }
}
