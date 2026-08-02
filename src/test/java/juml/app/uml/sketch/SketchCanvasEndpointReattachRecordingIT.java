// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 実装確認の録画: クラス図デザイナーで関係線の端点ハンドルをドラッグして別ノードへ
 * 繋ぎ替える (endpoint reattach) 様子を GIF に残す。合否ではなく「動いて見える」記録が
 * 目的で、恒久的な回帰検証は {@link SketchCanvasEndpointReattachTest} が担う。
 *
 * <p>{@code SketchCanvas} はパッケージプライベートのため、録画ハーネスも
 * verify-recording スキルの既定 ({@code juml.gui.recording}) ではなく、既存の
 * reattach テストと同じ {@code juml.app.uml.sketch} パッケージに置く
 * (可視性の都合。{@link ScreenRecorder} 本体は引き続き {@code juml.gui.recording} に
 * 置き、ここからインポートして再利用する)。</p>
 *
 * <p>Xvfb 上で実行すること:
 * <pre>
 *   xvfb-run -a -s "-screen 0 1280x900x24" \
 *     ./gradlew test --tests 'juml.app.uml.sketch.SketchCanvasEndpointReattachRecordingIT'
 * </pre>
 */
public class SketchCanvasEndpointReattachRecordingIT {

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
                    new File("build/recordings/relation-endpoint-reattach.gif"));
            System.out.println("recorded: " + gif.getAbsolutePath()
                    + " (" + gif.length() + " bytes)");
        }
        if (frame != null) {
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    /** A(60,60) --> B(340,60) の関係 1 本と、離れた位置に付替え先候補 C(60,260) を持つモデル。 */
    private static SketchModel sampleModel() {
        SketchModel model = new SketchModel();
        model.getClasses().add(new SketchClass("A", SketchClass.Kind.CLASS, 60, 60));
        model.getClasses().add(new SketchClass("B", SketchClass.Kind.CLASS, 340, 60));
        model.getClasses().add(new SketchClass("C", SketchClass.Kind.CLASS, 60, 260));
        model.getRelations().add(
                new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "B", null));
        return model;
    }

    private void dispatch(SketchCanvas canvas, int id, int modifiersEx, Point p, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, p.x, p.y, 1, false, button)));
    }

    /** {@code from}→{@code to} を直線補間した中間点列 (ラバーバンドが動く様子の演出用)。 */
    private static Point[] interpolate(Point from, Point to, int steps) {
        Point[] pts = new Point[steps];
        for (int i = 0; i < steps; i++) {
            double t = (double) (i + 1) / steps;
            pts[i] = new Point(
                    (int) Math.round(from.x + (to.x - from.x) * t),
                    (int) Math.round(from.y + (to.y - from.y) * t));
        }
        return pts;
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
    public void recordDragEndpointOntoAnotherNode() throws Exception {
        AtomicInteger edits = new AtomicInteger();
        SketchCanvas.Listener listener = new SketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editRequested(SketchClass c) {
            }

            @Override public void addClassRequested(Point at) {
            }
        };

        SketchModel model = sampleModel();
        SketchRelation rel = model.getRelations().get(0);
        SketchClass c = model.getClasses().get(2);

        SketchCanvas[] canvasHolder = new SketchCanvas[1];
        frame = GuiActionRunner.execute(() -> {
            SketchCanvas canvas = new SketchCanvas(listener);
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSize(620, 430);
            JFrame f = new JFrame("Juml - relation endpoint reattach");
            f.add(canvas);
            f.setSize(640, 470);
            f.setLocation(0, 0);
            f.setVisible(true);
            canvasHolder[0] = canvas;
            return f;
        });
        SketchCanvas canvas = canvasHolder[0];
        awaitShowing(frame);

        // ウィンドウ表示直後は初回ペイントがまだ済んでいないことがあり、録画の先頭数フレームが
        // 空白になりうる。録画開始前に一呼吸置いて、初回ペイント後の状態から録り始める。
        Thread.sleep(300);
        Rectangle area = GuiActionRunner.execute(() -> frame.getBounds());
        recorder = new ScreenRecorder(area, 10);
        recorder.start();
        Thread.sleep(700); // 録画演出: 初期状態 (A --> B) を見せる

        Point leftAnchor = GuiActionRunner.execute(() -> canvas.endpointAnchorsForTest(rel)[0]);
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                leftAnchor, MouseEvent.BUTTON1);
        assertTrue("端点ドラッグが開始しているはず",
                GuiActionRunner.execute(() -> canvas.dragRelationForTest() == rel));

        Point insideC = new Point(c.getX() + 40, c.getY() + 30);
        for (Point p : interpolate(leftAnchor, insideC, 10)) {
            dispatch(canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, p, 0);
            Thread.sleep(70); // 録画演出: ラバーバンドが動く様子を見せる
        }
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, insideC, MouseEvent.BUTTON1);

        assertEquals("C へ繋ぎ替わるはず", "C", rel.getLeft());
        assertEquals("right 側は変わらないはず", "B", rel.getRight());
        assertTrue("modelEdited が飛ぶはず", edits.get() >= 1);

        Thread.sleep(1200); // 録画演出: 繋ぎ替え後 (C --> B) の最終状態を見せる
    }
}
