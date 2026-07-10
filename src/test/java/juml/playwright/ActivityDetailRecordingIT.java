// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * アクティビティ図の「処理の書き漏れ」修正 (代入文の Statement 化 + コメント欠落修正) を
 * before/after の webm 動画として記録する。
 *
 * <p>「修正前」は {@code Options.showAssignments=false} で再現する (このサンプルでは
 * 修正前の出力と一致する — 代入・インクリメント文が丸ごと欠落した状態)。
 * 生成物: {@code build/recordings/activity-detail-before-after.webm}</p>
 */
public class ActivityDetailRecordingIT {

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

    /** 代入・ループ・分岐・コメントを含む題材メソッド。 */
    private static final String SAMPLE_SRC = ""
            + "class Sample {\n"
            + "  private Helper helper;\n"
            + "  private int total;\n"
            + "  /** 全 statement 種を含むエントリメソッド。 */\n"
            + "  void run(int count) {\n"
            + "    // 前処理: counter を初期化する\n"
            + "    int counter = 0;\n"
            + "    total = 0;\n"
            + "    counter += 2;\n"
            + "    counter++;\n"
            + "    int j = 0;\n"
            + "    while (j < 3) {\n"
            + "      j = j + 1;\n"
            + "    }\n"
            + "    // 分岐: counter の大きさで振り分け\n"
            + "    if (counter > 5) {\n"
            + "      helper.big();\n"
            + "    } else {\n"
            + "      helper.small();\n"
            + "    }\n"
            + "    helper.done();\n"
            + "    total = counter + j;\n"
            + "  }\n"
            + "}\n";

    /** case アーム内 / 波括弧なし else のコメントを含む題材メソッド。 */
    private static final String COMMENTS_SRC = ""
            + "class Flow {\n"
            + "  void handle(int n) {\n"
            + "    switch (n) {\n"
            + "      case 1:\n"
            + "        // case アーム内コメント (以前は欠落)\n"
            + "        helper.one();\n"
            + "        break;\n"
            + "      default:\n"
            + "        // default アーム内コメント (以前は欠落)\n"
            + "        helper.other();\n"
            + "    }\n"
            + "    if (n > 0) {\n"
            + "      helper.inIf();\n"
            + "    } else\n"
            + "      // 波括弧なし else のコメント (以前は欠落)\n"
            + "      helper.inElse();\n"
            + "  }\n"
            + "}\n";

    @Test
    public void recordBeforeAfterActivityDetail() throws Exception {
        List<JavaClassInfo> sample = JavaStructureExtractor.extract(SAMPLE_SRC);
        // 修正前の再現: 代入・インクリメント文が欠落した状態
        PlantUmlActivityDiagram.Options before = new PlantUmlActivityDiagram.Options();
        before.showAssignments = false;
        before.includeLegend = false;
        String beforeSvg = renderSvg(
                PlantUmlActivityDiagram.generate(sample, "Sample", "run", before));
        // 修正後 (既定): 全処理を表示
        PlantUmlActivityDiagram.Options after = new PlantUmlActivityDiagram.Options();
        after.includeLegend = false;
        String afterSvg = renderSvg(
                PlantUmlActivityDiagram.generate(sample, "Sample", "run", after));

        List<JavaClassInfo> flow = JavaStructureExtractor.extract(COMMENTS_SRC);
        PlantUmlActivityDiagram.Options flowOpts = new PlantUmlActivityDiagram.Options();
        flowOpts.includeLegend = false;
        String commentsSvg = renderSvg(
                PlantUmlActivityDiagram.generate(flow, "Flow", "handle", flowOpts));

        File html = tmp.newFile("activity_detail_.html");
        try (Writer w = new OutputStreamWriter(
                new FileOutputStream(html), StandardCharsets.UTF_8)) {
            w.write("<!DOCTYPE html><html><head><meta charset='utf-8'>"
                    + "<title>Activity detail before/after</title>"
                    + "<style>body{margin:20px;background:white;font-family:sans-serif}"
                    + "h1{font-size:22px} h2{font-size:18px;margin-top:28px}"
                    + ".row{display:flex;gap:24px;align-items:flex-start}"
                    + ".col{flex:1} "
                    + ".before{border:3px solid #cc3333;padding:8px;border-radius:6px}"
                    + ".after{border:3px solid #33aa33;padding:8px;border-radius:6px}"
                    + ".cap{font-weight:bold;margin:4px 0 8px}"
                    + ".before .cap{color:#cc3333} .after .cap{color:#33aa33}"
                    + "svg{max-width:100%;height:auto}</style></head><body>");
            w.write("<h1>アクティビティ図: 処理の書き漏れ修正 (before / after)</h1>");
            w.write("<div class='row'>");
            w.write("<div class='col before'><div class='cap'>修正前: "
                    + "代入・インクリメント文が欠落 (total = 0; / counter += 2; / counter++;"
                    + " が出ない。while 本体が空に見える)</div>" + beforeSvg + "</div>");
            w.write("<div class='col after'><div class='cap'>修正後: "
                    + "すべての処理がフローに出る</div>" + afterSvg + "</div>");
            w.write("</div>");
            w.write("<h2>コメント欠落の修正: case アーム内 / 波括弧なし else "
                    + "(修正前はどちらも note が出なかった)</h2>");
            w.write("<div class='after' style='max-width:60%'>" + commentsSvg + "</div>");
            w.write("</body></html>");
        }

        File out = new File("build/recordings");
        out.mkdirs();
        Path video;
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 720)
                .setRecordVideoDir(Paths.get("build/recordings"))
                .setRecordVideoSize(1280, 720))) {
            Page page = ctx.newPage();
            page.navigate(html.toURI().toString());
            page.waitForLoadState();
            page.waitForTimeout(2500);
            // before/after を見比べたあと、ページ末尾のコメント修正例までスクロール
            int height = ((Number) page.evaluate("document.body.scrollHeight")).intValue();
            for (int y = 0; y <= height; y += 120) {
                page.evaluate("window.scrollTo(0, " + y + ")");
                page.waitForTimeout(180);
            }
            page.waitForTimeout(1500);
            video = page.video().path();
        }
        File dest = new File(out, "activity-detail-before-after.webm");
        Files.move(video, dest.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("video: " + dest.getAbsolutePath()
                + " (" + dest.length() + " bytes)");
        assertTrue("video exists", dest.exists() && dest.length() > 0);
    }

    private static String renderSvg(String puml) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, bos);
        return bos.toString(StandardCharsets.UTF_8.name());
    }
}
