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
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.PlantUmlRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 生成された PlantUML SVG をブラウザ (Playwright + Chromium) でレンダリングし、
 * DOM 構造と表示サイズを検証する E2E テスト。
 *
 * <p>このテストは Chromium バイナリと <code>~/.cache/ms-playwright</code> へのアクセス
 * を要求する。Playwright 初回起動時に Chromium が自動ダウンロードされる (~150MB)。
 * オフライン / ネットワーク制限環境では {@link Playwright#create()} が失敗するため、
 * {@link Assume} でテスト全体をスキップする。</p>
 *
 * <p>視覚回帰: スクリーンショットは <code>build/playwright/svg.png</code> に保存され、
 * テスト失敗時の調査に利用できる。閾値ベースのピクセル比較ではなく「画像が
 * 取得できて空でないこと」までを検証する (PlantUML/Smetana 出力は環境依存で
 * ピクセルが微妙に変わりやすいため)。</p>
 */
public class PlantUmlSvgPlaywrightTest {

    private static Playwright playwright;
    private static Browser browser;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void setupBrowser() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
        } catch (Throwable ex) {
            // Chromium ダウンロード/起動に失敗 (オフライン環境など) はテスト全体スキップ
            Assume.assumeNoException(
                    "Playwright Chromium not available: " + ex.getMessage(), ex);
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

    /** PlantUML テキストから SVG を文字列で生成。 */
    private String renderSvg(String puml) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, bos);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    /** SVG を含む最小 HTML を一時ファイルに書き出して file:// URL を返す。 */
    private String wrapSvgAsHtml(String svg) throws IOException {
        File html = tmp.newFile("page.html");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(html),
                StandardCharsets.UTF_8)) {
            w.write("<!doctype html><html><head><meta charset='utf-8'>"
                    + "<title>Juml SVG Test</title>"
                    + "<style>body { margin: 0; background: white; }</style>"
                    + "</head><body>");
            w.write(svg);
            w.write("</body></html>");
        }
        return html.toURI().toString();
    }

    @Test
    public void testClassDiagramRendersClassNamesInBrowser() throws IOException {
        // 1. 2 クラスの最小プロジェクトから ClassInfo を抽出
        List<JavaClassInfo> classes = new java.util.ArrayList<>();
        classes.addAll(JavaStructureExtractor.extract(
                "package com.demo; public class Alpha { void hello() {} }"));
        classes.addAll(JavaStructureExtractor.extract(
                "package com.demo; public class Beta extends Alpha {}"));

        // 2. PlantUML テキスト → SVG
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlClassDiagram.generate(classes, o);
        String svg = renderSvg(puml);
        assertTrue("SVG が <svg> タグで始まる",
                svg.contains("<svg") || svg.contains("<?xml"));

        // 3. Chromium で開いてレンダリング
        String url = wrapSvgAsHtml(svg);
        try (Page page = browser.newPage()) {
            page.navigate(url);
            page.waitForLoadState();

            // 4. SVG 要素がページに存在する
            int svgCount = page.locator("svg").count();
            assertEquals("SVG 要素が 1 つ描画されている", 1, svgCount);

            // 5. クラス名がテキストノードとして出ている
            String bodyText = page.locator("body").innerText();
            assertTrue("Alpha が描画される: " + bodyText, bodyText.contains("Alpha"));
            assertTrue("Beta が描画される: " + bodyText, bodyText.contains("Beta"));

            // 6. スクリーンショットを保存 (調査用、視覚回帰の基準データに使える)
            File out = new File("build/playwright");
            out.mkdirs();
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(new File(out, "class-diagram.png").toPath())
                    .setType(ScreenshotType.PNG)
                    .setFullPage(true));
        }
    }

    @Test
    public void testScopedDiagramOmitsFilteredClasses() throws IOException {
        // スコープ機能のスモーク: filter で 1 クラスだけ残ったときに SVG にも反映されるか
        List<JavaClassInfo> classes = new java.util.ArrayList<>();
        classes.addAll(JavaStructureExtractor.extract(
                "package a; public class Keep {}"));
        classes.addAll(JavaStructureExtractor.extract(
                "package b; public class Drop {}"));

        // 「a パッケージのみ」相当の手動フィルタ (DiagramService 経由でも同じ)
        List<JavaClassInfo> filtered = new java.util.ArrayList<>();
        for (JavaClassInfo c : classes) {
            if ("a".equals(c.getPackageName())) {
                filtered.add(c);
            }
        }
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        String svg = renderSvg(PlantUmlClassDiagram.generate(filtered, o));
        String url = wrapSvgAsHtml(svg);
        try (Page page = browser.newPage()) {
            page.navigate(url);
            page.waitForLoadState();
            String body = page.locator("body").innerText();
            assertTrue("Keep は表示", body.contains("Keep"));
            assertTrue("Drop はスコープ外で非表示", !body.contains("Drop"));
        }
    }
}
