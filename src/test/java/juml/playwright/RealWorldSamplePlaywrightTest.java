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
import juml.core.formats.uml.AidlParser;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.PlantUmlPackageDiagram;
import juml.core.formats.uml.PlantUmlRenderer;
import juml.core.formats.uml.PlantUmlSequenceDiagram;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 実 OSS ソース ({@code samples/easypermissions/} と {@code samples/aidl/}) を入力に
 * クラス図 / シーケンス図 / パッケージ図を生成し、Playwright + Chromium で実際に
 * レンダリングして「期待要素が描かれているか」と「スクリーンショットが取れるか」を
 * 検証する E2E テスト。
 *
 * <p>環境条件は {@link PlantUmlSvgPlaywrightTest} と同じ (Chromium 自動 DL、headless)。
 * 取得できないオフライン環境では {@link Assume} でクラス全体スキップ。</p>
 *
 * <p>各テストは {@code build/playwright/real-world/} 配下に PNG を保存する。
 * ファイル名は判別しやすく:</p>
 * <ul>
 *   <li>{@code class-diagram-easypermissions.png} — easypermissions + AIDL のクラス図</li>
 *   <li>{@code sequence-MainActivity.onCreate.png} — MainActivity.onCreate を起点</li>
 *   <li>{@code package-diagram-easypermissions.png} — パッケージ図</li>
 *   <li>{@code class-diagram-aidl-only.png} — AIDL 単体のクラス図</li>
 * </ul>
 */
public class RealWorldSamplePlaywrightTest {

    private static Playwright playwright;
    private static Browser browser;
    private static File screenshotDir;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void setupBrowser() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
        } catch (Throwable ex) {
            Assume.assumeNoException(
                    "Playwright Chromium not available: " + ex.getMessage(), ex);
        }
        screenshotDir = new File("build/playwright/real-world");
        if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
            throw new IllegalStateException(
                    "could not create screenshot dir: " + screenshotDir);
        }
    }

    @AfterClass
    public static void teardownBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream in =
                RealWorldSamplePlaywrightTest.class.getResourceAsStream(path)) {
            assertNotNull("test resource not found on classpath: " + path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String renderSvg(String puml) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, bos);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private String wrapSvgAsHtml(String svg, String pageTitle) throws IOException {
        File html = tmp.newFile("page.html");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(html),
                StandardCharsets.UTF_8)) {
            w.write("<!doctype html><html><head><meta charset='utf-8'>"
                    + "<title>" + pageTitle + "</title>"
                    + "<style>body { margin: 16px; background: white;"
                    + " font-family: sans-serif; }"
                    + " h1 { font-size: 14px; color: #555;"
                    + " border-bottom: 1px solid #ccc; padding-bottom: 4px;"
                    + " margin: 0 0 12px 0; }</style>"
                    + "</head><body>");
            w.write("<h1>" + pageTitle + "</h1>");
            w.write(svg);
            w.write("</body></html>");
        }
        return html.toURI().toString();
    }

    /** 実サンプル全部 (Java + AIDL) を 1 つのリストに集める。 */
    private List<JavaClassInfo> loadAllSampleClasses() throws IOException {
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(JavaStructureExtractor.extract(
                loadResource("/samples/easypermissions/MainActivity.java")));
        all.addAll(JavaStructureExtractor.extract(
                loadResource("/samples/easypermissions/EasyPermissions.java")));
        all.addAll(JavaStructureExtractor.extract(
                loadResource("/samples/easypermissions/AfterPermissionGranted.java")));
        all.addAll(JavaStructureExtractor.extract(
                loadResource("/samples/easypermissions/AppSettingsDialog.java")));
        all.addAll(AidlParser.parse(
                loadResource("/samples/aidl/IRemoteService.aidl")));
        return all;
    }

    private void saveScreenshot(Page page, String fileName) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(new File(screenshotDir, fileName).toPath())
                .setType(ScreenshotType.PNG)
                .setFullPage(true));
    }

    // ---- (1) easypermissions + AIDL のクラス図 ---------------------------

    @Test
    public void testClassDiagramFromEasyPermissions() throws IOException {
        List<JavaClassInfo> classes = loadAllSampleClasses();

        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = true;
        String puml = PlantUmlClassDiagram.generate(classes, o);
        String svg = renderSvg(puml);
        assertTrue("SVG should be non-empty", svg.length() > 200);

        String url = wrapSvgAsHtml(svg,
                "Class diagram: googlesamples/easypermissions + AOSP IRemoteService");
        try (Page page = browser.newPage()) {
            page.setViewportSize(1400, 1200);
            page.navigate(url);
            page.waitForLoadState();

            // 主要クラス名がレンダリングされていること
            String body = page.locator("body").innerText();
            assertTrue("MainActivity rendered: " + body, body.contains("MainActivity"));
            assertTrue("EasyPermissions rendered: " + body,
                    body.contains("EasyPermissions"));
            assertTrue("AfterPermissionGranted rendered: " + body,
                    body.contains("AfterPermissionGranted"));
            assertTrue("AppSettingsDialog rendered: " + body,
                    body.contains("AppSettingsDialog"));
            assertTrue("IRemoteService (AIDL) rendered: " + body,
                    body.contains("IRemoteService"));

            saveScreenshot(page, "class-diagram-easypermissions.png");
        }
    }

    // ---- (2) MainActivity.onCreate のシーケンス図 -------------------------

    @Test
    public void testSequenceDiagramFromMainActivityOnCreate() throws IOException {
        List<JavaClassInfo> classes = loadAllSampleClasses();

        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.title = "MainActivity.onCreate (real-world)";
        o.includeLegend = false;
        o.maxDepth = 3;
        String puml = PlantUmlSequenceDiagram.generate(
                classes, "MainActivity", "onCreate", o);
        assertTrue("expected @startuml in: " + puml.substring(0,
                Math.min(80, puml.length())), puml.contains("@startuml"));

        String svg = renderSvg(puml);
        String url = wrapSvgAsHtml(svg, "Sequence diagram: MainActivity.onCreate");
        try (Page page = browser.newPage()) {
            page.setViewportSize(1400, 900);
            page.navigate(url);
            page.waitForLoadState();

            String body = page.locator("body").innerText();
            assertTrue("entry class MainActivity should appear: " + body,
                    body.contains("MainActivity"));
            // onCreate のラベルが participant メッセージとして出ているはず
            assertTrue("onCreate label should appear: " + body,
                    body.contains("onCreate"));

            saveScreenshot(page, "sequence-MainActivity.onCreate.png");
        }
    }

    // ---- (3) easypermissions のパッケージ図 -------------------------------

    @Test
    public void testPackageDiagramFromEasyPermissions() throws IOException {
        List<JavaClassInfo> classes = loadAllSampleClasses();

        String puml = PlantUmlPackageDiagram.generate(classes);
        String svg = renderSvg(puml);
        String url = wrapSvgAsHtml(svg, "Package diagram: easypermissions samples");

        try (Page page = browser.newPage()) {
            page.setViewportSize(1200, 700);
            page.navigate(url);
            page.waitForLoadState();

            String body = page.locator("body").innerText();
            // パッケージ名そのものがノードとして出るはず
            assertTrue("pub.devrel.easypermissions package should appear: " + body,
                    body.contains("pub.devrel.easypermissions"));

            saveScreenshot(page, "package-diagram-easypermissions.png");
        }
    }

    // ---- (4) AIDL 単体のクラス図 ------------------------------------------

    @Test
    public void testClassDiagramFromAidlOnly() throws IOException {
        List<JavaClassInfo> classes = AidlParser.parse(
                loadResource("/samples/aidl/IRemoteService.aidl"));

        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlClassDiagram.generate(classes, o);
        String svg = renderSvg(puml);
        String url = wrapSvgAsHtml(svg, "Class diagram: AOSP IRemoteService.aidl");

        try (Page page = browser.newPage()) {
            page.setViewportSize(800, 500);
            page.navigate(url);
            page.waitForLoadState();

            String body = page.locator("body").innerText();
            assertTrue("IRemoteService rendered: " + body,
                    body.contains("IRemoteService"));
            assertTrue("registerCallback method rendered: " + body,
                    body.contains("registerCallback"));
            assertTrue("unregisterCallback method rendered: " + body,
                    body.contains("unregisterCallback"));

            saveScreenshot(page, "class-diagram-aidl-only.png");
        }
    }
}
