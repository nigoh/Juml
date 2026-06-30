# 録画ハーネス スニペット集（verify-recording 用）

そのまま貼って使える雛形。**外部エンコーダ不要**の方式 A（GIF）と、Playwright の
方式 B（webm）。SPDX/Copyright ヘッダ・headless skip・EDT 規律・`cleanUp()`・checkstyle
警告 0 を厳守すること（既存テストに倣う）。生成物は `build/recordings/` に集約する。

---

## 方式 A: アニメ GIF レコーダ（純 Java / `java.awt.Robot` + ImageIO）

### A-1. 再利用ヘルパー `ScreenRecorder`

`src/test/java/juml/gui/recording/ScreenRecorder.java`

```java
// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 対象矩形を一定間隔でキャプチャし、停止時にアニメ GIF として書き出す簡易レコーダ。
 * 外部エンコーダ不要。Xvfb 上で動かすこと（headless では Robot が使えない）。
 */
public final class ScreenRecorder {

    private final Robot robot;
    private final Rectangle area;
    private final int delayMs;
    private final List<BufferedImage> frames = new ArrayList<>();
    private volatile boolean running;
    private Thread thread;

    public ScreenRecorder(Rectangle area, int fps) throws AWTException {
        this.robot = new Robot();
        this.area = area;
        this.delayMs = Math.max(1, 1000 / Math.max(1, fps));
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            while (running) {
                frames.add(robot.createScreenCapture(area));
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "screen-recorder");
        thread.setDaemon(true);
        thread.start();
    }

    /** 録画を止めて GIF を書き出す。書き出したファイルを返す。 */
    public synchronized File stopAndSave(File out) throws Exception {
        running = false;
        if (thread != null) {
            thread.join(2000);
        }
        out.getParentFile().mkdirs();
        writeGif(frames, delayMs, out);
        return out;
    }

    private static void writeGif(List<BufferedImage> imgs, int delayMs, File out) throws Exception {
        if (imgs.isEmpty()) {
            throw new IllegalStateException("no frames captured (Xvfb 上で動かしたか確認)");
        }
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(Files.newOutputStream(out.toPath()))) {
            writer.setOutput(ios);
            ImageWriteParam params = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            boolean first = true;
            for (BufferedImage img : imgs) {
                BufferedImage rgb = toIndexedFriendly(img);
                IIOMetadata meta = writer.getDefaultImageMetadata(
                        javax.imageio.ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB),
                        params);
                configureFrame(meta, delayMs, first);
                writer.writeToSequence(new IIOImage(rgb, null, meta), params);
                first = false;
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage toIndexedFriendly(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(src, 0, 0, null);
        return rgb;
    }

    private static void configureFrame(IIOMetadata meta, int delayMs, boolean first) throws Exception {
        String fmt = meta.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(fmt);

        IIOMetadataNode gce = child(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(Math.max(1, delayMs / 10))); // 1/100s 単位
        gce.setAttribute("transparentColorIndex", "0");

        if (first) {
            IIOMetadataNode appExts = child(root, "ApplicationExtensions");
            IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
            appExt.setAttribute("applicationID", "NETSCAPE");
            appExt.setAttribute("authenticationCode", "2.0");
            appExt.setUserObject(new byte[] {0x1, 0x0, 0x0}); // ループ無限
            appExts.appendChild(appExt);
        }
        meta.setFromTree(fmt, root);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
```

### A-2. 録画する JUnit テストの雛形

`src/test/java/juml/gui/recording/FeatureRecordingIT.java`

```java
// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;

/**
 * 実装確認の様子を GIF で残すテスト（合否ではなく「動いて見える」記録が目的）。
 * Xvfb 上で実行すること:
 *   xvfb-run -a -s "-screen 0 1280x900x24" \
 *     ./gradlew test --tests 'juml.gui.recording.FeatureRecordingIT'
 */
public class FeatureRecordingIT {

    private FrameFixture window;
    private ScreenRecorder recorder;

    @Before
    public void setUp() {
        Assume.assumeFalse("headless では録画不可（xvfb-run でラップ）",
                GraphicsEnvironment.isHeadless());
        // TODO: 既存 GUI テストに倣って UmlMainFrame 等を EDT で生成し window を組む
        // window = new FrameFixture(robot, GuiActionRunner.execute(() -> new UmlMainFrame()));
        // window.show();
    }

    @After
    public void tearDown() throws Exception {
        if (recorder != null) {
            File gif = recorder.stopAndSave(new File("build/recordings/feature.gif"));
            System.out.println("recorded: " + gif.getAbsolutePath());
        }
        if (window != null) {
            window.cleanUp();
        }
    }

    @Test
    public void recordFeatureScenario() throws Exception {
        Rectangle area = GuiActionRunner.execute(() -> window.target().getBounds());
        recorder = new ScreenRecorder(area, 10); // 10fps
        recorder.start();

        // === ここで「確認したいシナリオ」を操作する（EDT 規律厳守） ===
        // 例: タブを開く・ダイアログを出す・エクスポートする
        // window.menuItem("open").click();
        // 待ちは固定 sleep ではなく既存の期限付きポーリングに倣う
        // ============================================================
    }
}
```

> 注意: 上の雛形の TODO は対象機能に合わせて埋める。`UmlMainFrame` の組み立て・待ち方は
> `src/test/java/juml/gui/UmlMainFrameSwingTest.java`（EDT 包み + 期限付きポーリング +
> cleanUp の手本）をそのまま参考にする。

---

## 方式 B: Playwright webm レコーダ（図出力 / Web）

`src/test/java/juml/playwright/DiagramRecordingIT.java`

```java
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
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** 図出力(HTML/SVG)の「動き」を webm 録画する。headless 可。 */
public class DiagramRecordingIT {

    private static Playwright playwright;
    private static Browser browser;

    @BeforeClass
    public static void setup() {
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
    public void recordDiagram() throws Exception {
        // TODO: 既存 *ScreenshotIT に倣って PlantUML→SVG→HTML ラップを用意する
        File html = File.createTempFile("diagram_rec_", ".html");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(html), StandardCharsets.UTF_8)) {
            w.write("<!DOCTYPE html><html><body><h2>demo</h2><!-- SVG here --></body></html>");
        }

        File dir = new File("build/recordings");
        dir.mkdirs();

        BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1000, 700)
                .setRecordVideoDir(Paths.get("build/recordings"))
                .setRecordVideoSize(1000, 700));
        Path video;
        try (Page page = ctx.newPage()) {
            page.navigate(html.toURI().toString());
            page.waitForLoadState();
            // === 「動き」を作る操作（ズーム/スクロール/差し替え） ===
            page.mouse().wheel(0, 300);
            page.waitForTimeout(500); // 録画用の短い演出待ちは可（テスト合否には使わない）
            // ====================================================
            video = page.video().path(); // close 前に予約
        }
        ctx.close(); // ← ここで webm が flush される

        File webm = video.toFile();
        System.out.println("recorded: " + webm.getAbsolutePath());
        assertTrue("webm exists", webm.exists() && webm.length() > 0);
    }
}
```

> `setRecordVideoDir` を付けた context は `close()` で初めて webm が確定する。
> `page.video().path()` は close 前に取得しておくこと。

---

## 実行コマンドまとめ

```sh
# 方式 A（Xvfb 必須・GIF）
xvfb-run -a -s "-screen 0 1280x900x24" \
  ./gradlew test --tests 'juml.gui.recording.*'

# 方式 B（headless 可・webm）
./gradlew test --tests 'juml.playwright.*Recording*'

# 生成物
ls -la build/recordings/
```

- `build/` は git 管理外。録画はレビュー用の一時成果物としてコミットしない。
- skip された場合（headless で A を直叩き / Playwright 取得失敗）は、何が skip されたかを報告に明記。
