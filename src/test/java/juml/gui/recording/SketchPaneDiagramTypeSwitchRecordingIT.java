// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import juml.app.uml.PumlTemplate;
import juml.app.uml.sketch.SketchPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JFrame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;

/**
 * 実装確認の録画: {@link SketchPane} にクラス図→シーケンス図→配置図 (入れ子コンテナ込み)
 * のテキストを順に {@link SketchPane#loadFrom(String)} し、図種が自動判定されてツールバー
 * とキャンバスが切り替わる様子を GIF に残す。合否ではなく「動いて見える」記録が目的で、
 * 図種自動判定そのものの回帰検証は {@code SketchDiagramTypeTest} が担う。
 *
 * <p>{@link SketchPane} / {@link SketchPane#loadFrom(String)} は公開 API のため、
 * verify-recording スキルの既定どおり {@code juml.gui.recording} パッケージに置ける
 * (対して {@code SketchCanvas} 直叩きの他 2 本は package-private API が必要で
 * {@code juml.app.uml.sketch} パッケージに置いている)。</p>
 *
 * <p>Xvfb 上で実行すること:
 * <pre>
 *   xvfb-run -a -s "-screen 0 1280x900x24" \
 *     ./gradlew test --tests 'juml.gui.recording.SketchPaneDiagramTypeSwitchRecordingIT'
 * </pre>
 */
public class SketchPaneDiagramTypeSwitchRecordingIT {

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
                    new File("build/recordings/sketch-pane-diagram-type-switch.gif"));
            System.out.println("recorded: " + gif.getAbsolutePath()
                    + " (" + gif.length() + " bytes)");
        }
        if (frame != null) {
            GuiActionRunner.execute(() -> frame.dispose());
        }
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
    public void recordSwitchingBetweenDiagramTypes() throws Exception {
        SketchPane[] paneHolder = new SketchPane[1];
        frame = GuiActionRunner.execute(() -> {
            SketchPane pane = new SketchPane();
            JFrame f = new JFrame("Juml - sketch pane diagram type switch");
            f.add(pane);
            f.setSize(900, 640);
            f.setLocation(0, 0);
            f.setVisible(true);
            paneHolder[0] = pane;
            return f;
        });
        SketchPane pane = paneHolder[0];
        awaitShowing(frame);

        // ウィンドウ表示直後は初回ペイントがまだ済んでいないことがあり、録画の先頭数フレームが
        // 空白になりうる。録画開始前に一呼吸置いて、初回ペイント後の状態から録り始める。
        Thread.sleep(300);
        Rectangle area = GuiActionRunner.execute(() -> frame.getBounds());
        recorder = new ScreenRecorder(area, 10);
        recorder.start();
        Thread.sleep(900); // 録画演出: 初期状態 (既定のクラス図デザイナー) を見せる

        // クラス図 → シーケンス図 → 配置図 (入れ子コンテナ込み) の順にロードし、
        // 図種判定でツールバー/キャンバスが切り替わる様子を録る。
        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.CLASS.body()));
        Thread.sleep(1400);

        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.SEQUENCE.body()));
        Thread.sleep(1400);

        GuiActionRunner.execute(() -> pane.loadFrom(PumlTemplate.DEPLOYMENT.body()));
        Thread.sleep(1600); // 録画演出: 配置図 (入れ子コンテナ) の最終状態を見せる
    }
}
