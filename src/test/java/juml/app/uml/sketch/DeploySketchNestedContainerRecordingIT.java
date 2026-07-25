// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import juml.gui.recording.ScreenRecorder;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JFrame;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 実装確認の録画: 配置図デザイナーで入れ子コンテナ ({@code node "X" { ... }}) を含む
 * PlantUML テキストをロードし、コンテナ枠+子ノード+リンクが描画される様子と、リンク端点を
 * 入れ子コンテナ内の子ノードへドラッグ&ドロップで付け替える様子を GIF に残す。合否ではなく
 * 「動いて見える」記録が目的で、恒久的な回帰検証は
 * {@link DeploySketchCodecTest#parse_nestedContainer_isSupportedAndBuildsHierarchy} と
 * {@link DeploySketchLinkReattachTest#dragEndpointHandle_ontoChildNodeInsideContainer_reattachesToChild}
 * が担う。
 *
 * <p>読み込むテキストは実アプリの配置図テンプレート ({@link PumlTemplate#DEPLOYMENT}) を
 * そのまま使う (Internet --> "Web Server" { artifact app.war } --> db)。</p>
 *
 * <p>{@code DeploySketchCanvas} はパッケージプライベートのため、録画ハーネスも
 * verify-recording スキルの既定 ({@code juml.gui.recording}) ではなく、既存の reattach
 * テストと同じ {@code juml.app.uml.sketch} パッケージに置く (可視性の都合。
 * {@link ScreenRecorder} 本体は引き続き {@code juml.gui.recording} に置く)。</p>
 *
 * <p>Xvfb 上で実行すること:
 * <pre>
 *   xvfb-run -a -s "-screen 0 1280x900x24" \
 *     ./gradlew test --tests 'juml.app.uml.sketch.DeploySketchNestedContainerRecordingIT'
 * </pre>
 */
public class DeploySketchNestedContainerRecordingIT {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では録画不可 (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
    }

    private JFrame frame;
    private ScreenRecorder recorder;

    @After
    public void tearDown() throws Exception {
        if (recorder != null) {
            File gif = recorder.stopAndSave(
                    new File("build/recordings/deploy-nested-container.gif"));
            System.out.println("recorded: " + gif.getAbsolutePath()
                    + " (" + gif.length() + " bytes)");
        }
        if (frame != null) {
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    private void dispatch(DeploySketchCanvas canvas, int id, int modifiersEx,
                           int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    private static void awaitShowing(JFrame f) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(GuiActionRunner.execute(() -> f.isShowing()))) {
                return;
            }
            Thread.sleep(50);
        }
    }

    @Test
    public void recordLoadNestedContainerThenReattachEndpointToChild() throws Exception {
        AtomicInteger edits = new AtomicInteger();
        DeploySketchCanvas.Listener listener = new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(DeployNode n) {
            }
        };

        DeploySketchCanvas[] canvasHolder = new DeploySketchCanvas[1];
        frame = GuiActionRunner.execute(() -> {
            DeploySketchCanvas canvas = new DeploySketchCanvas(listener);
            // 初期状態: 空の配置図 (ロード前の様子を見せる)
            canvas.setModel(new DeploySketchModel(), true, List.of());
            canvas.setSize(700, 480);
            JFrame f = new JFrame("Juml - deploy nested container");
            f.add(canvas);
            f.setSize(720, 520);
            f.setLocation(0, 0);
            f.setVisible(true);
            canvasHolder[0] = canvas;
            return f;
        });
        DeploySketchCanvas canvas = canvasHolder[0];
        awaitShowing(frame);

        // ウィンドウ表示直後は初回ペイントがまだ済んでいないことがあり、録画の先頭数フレームが
        // 空白になりうる。録画開始前に一呼吸置いて、初回ペイント後の状態から録り始める。
        Thread.sleep(300);
        Rectangle area = GuiActionRunner.execute(() -> frame.getBounds());
        recorder = new ScreenRecorder(area, 10);
        recorder.start();
        Thread.sleep(600); // 録画演出: 空の配置図を見せる

        // 実アプリの配置図テンプレートをロード → コンテナ枠 (Web Server) + 子ノード
        // (app.war) + リンク (Internet --> Web Server --> db) が描画される。
        String templateText = PumlTemplate.DEPLOYMENT.body();
        DeploySketchCodec.ParseResult parsed = DeploySketchCodec.parse(templateText);
        assertTrue("配置図テンプレートは入れ子込みで全行対応構文のはず: " + parsed.unsupportedLines,
                parsed.isFullySupported());
        GuiActionRunner.execute(() -> canvas.setModel(
                parsed.model, parsed.isFullySupported(), parsed.unsupportedLines));

        DeployNode webServer = parsed.model.getNodes().stream()
                .filter(n -> "Web Server".equals(n.getLabel())).findFirst().orElse(null);
        assertNotNull("Web Server コンテナが見つかるはず", webServer);
        assertTrue("Web Server はコンテナ化されているはず", webServer.isContainer());
        DeployNode appWar = webServer.getChildren().get(0);

        DeployLink toContainer = parsed.model.getLinks().stream()
                .filter(l -> webServer.getId().equals(l.getTo()))
                .findFirst().orElse(null);
        assertNotNull("Web Server 宛のリンク (Internet --> Web Server) が見つかるはず",
                toContainer);
        String fromId = toContainer.getFrom();

        Thread.sleep(1300); // 録画演出: コンテナ+子ノード+リンクが描画された状態を見せる

        // リンクの to 側端点 (Web Server コンテナの縁、Internet 側を向いた edgePoint) を、
        // 入れ子コンテナ内の子ノード (app.war) へドラッグして付け替える。掴む座標は
        // 見た目のコンテナ矩形の辺ではなく、実際に描画されるハンドル位置
        // ({@link DeploySketchLinkHandles#endpointsOf}) をそのまま使う (コンテナは兄弟の
        // クラウドノードと高さが異なり中心 y がずれるため、矩形から手計算すると外れる)。
        Point[] eps = GuiActionRunner.execute(() -> DeploySketchLinkHandles.endpointsOf(
                parsed.model, toContainer, canvas.layoutForTest()));
        assertNotNull("リンクの端点が解決できるはず", eps);
        int handleX = eps[1].x;
        int handleY = eps[1].y;

        Rectangle childRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(appWar));
        int targetX = childRect.x + childRect.width / 2;
        int targetY = childRect.y + childRect.height / 2;

        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                handleX, handleY, MouseEvent.BUTTON1);
        assertEquals("端点ドラッグが開始しているはず", toContainer,
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            int x = handleX + (targetX - handleX) * i / steps;
            int y = handleY + (targetY - handleY) * i / steps;
            dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, x, y, 0);
            Thread.sleep(70); // 録画演出: ラバーバンドがコンテナ内の子ノードへ動く様子を見せる
        }
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, targetX, targetY, MouseEvent.BUTTON1);

        assertEquals("入れ子コンテナ内の子ノード app.war へ付け替わるはず",
                appWar.getId(), toContainer.getTo());
        assertEquals("from 側 (Internet) は変わらないはず", fromId, toContainer.getFrom());
        assertTrue("modelEdited が飛ぶはず", edits.get() >= 1);

        Thread.sleep(1300); // 録画演出: 付け替え後の最終状態を見せる
    }
}
