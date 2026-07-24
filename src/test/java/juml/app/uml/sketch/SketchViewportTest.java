// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * {@link SketchViewport} のズーム計算 (クランプ・座標逆変換・サイズ拡縮) と、実際の
 * {@link JViewport} を伴うパン・カーソル中心ズーム・Ctrl+0 リセットの結線を検証する。
 */
public class SketchViewportTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static SketchViewport newViewport() {
        return GuiActionRunner.execute(() -> new SketchViewport(new JPanel()));
    }

    @Test
    public void setZoom_clampsToRange() {
        SketchViewport v = newViewport();
        GuiActionRunner.execute(() -> v.setZoom(100.0));
        assertEquals(SketchViewport.MAX_ZOOM, v.zoom(), 1e-9);
        GuiActionRunner.execute(() -> v.setZoom(0.001));
        assertEquals(SketchViewport.MIN_ZOOM, v.zoom(), 1e-9);
    }

    @Test
    public void toModel_invertsZoom() {
        SketchViewport v = newViewport();
        GuiActionRunner.execute(() -> v.setZoom(2.0));
        Point m = v.toModel(new Point(100, 50));
        assertEquals(50, m.x);
        assertEquals(25, m.y);
    }

    @Test
    public void toModel_atDefaultZoomIsIdentity() {
        SketchViewport v = newViewport();
        Point m = v.toModel(new Point(37, 91));
        assertEquals(37, m.x);
        assertEquals(91, m.y);
    }

    @Test
    public void scaled_multipliesPreferredSize() {
        SketchViewport v = newViewport();
        GuiActionRunner.execute(() -> v.setZoom(2.0));
        Dimension d = v.scaled(new Dimension(400, 300));
        assertEquals(800, d.width);
        assertEquals(600, d.height);
    }

    @Test
    public void anchorAdjustedViewPos_keepsCursorPointFixed() {
        // カーソル中心ズーム: 2倍ズームでカーソル (200,200)・viewport 原点なら、
        // 同じモデル点を維持するため viewport を (200,200) へスクロールする。
        Point p = SketchViewport.anchorAdjustedViewPos(new Point(200, 200), new Point(0, 0), 2.0);
        assertEquals(200, p.x);
        assertEquals(200, p.y);
        // 等倍 (factor=1.0) は viewport を動かさない。
        Point same = SketchViewport.anchorAdjustedViewPos(new Point(50, 60), new Point(10, 20), 1.0);
        assertEquals(10, same.x);
        assertEquals(20, same.y);
        // 縮小 (factor=0.5): カーソル (200,200)・view (100,100) → (0,0)。
        Point out = SketchViewport.anchorAdjustedViewPos(new Point(200, 200), new Point(100, 100), 0.5);
        assertEquals(0, out.x);
        assertEquals(0, out.y);
    }

    // --- 実 JViewport を伴う結線テスト (パン / カーソル中心ズーム / Ctrl+0 リセット) ---
    //
    // モデル座標系のサイズをズームに応じて拡縮する軽量ダミーキャンバス。実際の
    // SketchCanvas 系と同じく getPreferredSize() を SketchViewport.scaled() 経由にすることで、
    // revalidate() 後に JViewport.getViewSize() がズーム後のサイズへ追従する。
    private static final class ZoomAwareCanvas extends JComponent {
        private final Dimension modelSize;
        private SketchViewport viewport;

        ZoomAwareCanvas(Dimension modelSize) {
            this.modelSize = modelSize;
            setFocusable(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return viewport != null ? viewport.scaled(modelSize) : modelSize;
        }
    }

    /** JFrame + JScrollPane + ZoomAwareCanvas + SketchViewport の一式。 */
    private static final class Fixture {
        final JFrame frame;
        final JScrollPane scrollPane;
        final ZoomAwareCanvas canvas;
        final SketchViewport viewport;

        Fixture(JFrame frame, JScrollPane scrollPane, ZoomAwareCanvas canvas, SketchViewport viewport) {
            this.frame = frame;
            this.scrollPane = scrollPane;
            this.canvas = canvas;
            this.viewport = viewport;
        }

        JViewport swingViewport() {
            return scrollPane.getViewport();
        }
    }

    /** モデルサイズより小さいフレームでスクロール可能な状態を作り、画面へ実際に表示する。 */
    private static Fixture buildRealViewport(int frameW, int frameH, Dimension modelSize) {
        return GuiActionRunner.execute(() -> {
            ZoomAwareCanvas canvas = new ZoomAwareCanvas(modelSize);
            SketchViewport viewport = new SketchViewport(canvas);
            canvas.viewport = viewport;
            JScrollPane scrollPane = new JScrollPane(canvas);
            JFrame frame = new JFrame();
            frame.add(scrollPane);
            frame.setSize(frameW, frameH);
            frame.setLocation(0, 0);
            frame.setVisible(true);
            canvas.requestFocusInWindow();
            return new Fixture(frame, scrollPane, canvas, viewport);
        });
    }

    private static void dispatchMouse(Component target, int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> target.dispatchEvent(new MouseEvent(
                target, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    private static void dispatchCtrlWheel(Component target, int x, int y, int wheelRotation) {
        GuiActionRunner.execute(() -> target.dispatchEvent(new MouseWheelEvent(
                target, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK, x, y, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, wheelRotation)));
    }

    /**
     * {@code zoomAround} 内の viewport 位置確定は {@code SwingUtilities.invokeLater} で
     * 遅延されるため、固定 sleep ではなく期限付きポーリングで最終状態を待つ。
     */
    private static Point awaitViewPosition(JViewport vp, Point expected, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Point last = GuiActionRunner.execute(vp::getViewPosition);
        while (System.currentTimeMillis() < deadline) {
            last = GuiActionRunner.execute(vp::getViewPosition);
            if (last.equals(expected)) {
                return last;
            }
            Thread.sleep(20);
        }
        return last;
    }

    @Test
    public void pan_middleDrag_movesViewportPosition() {
        Fixture fx = buildRealViewport(300, 220, new Dimension(2000, 1500));
        try {
            Point before = GuiActionRunner.execute(() -> fx.swingViewport().getViewPosition());
            assertEquals("初期スクロール位置は原点のはず", new Point(0, 0), before);

            // 中ボタンで (150,120) → (110,90) へドラッグ (左へ 40px・上へ 30px)。
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON2_DOWN_MASK,
                    150, 120, MouseEvent.BUTTON2);
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON2_DOWN_MASK,
                    110, 90, 0);
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_RELEASED, 0, 110, 90, MouseEvent.BUTTON2);

            Point after = GuiActionRunner.execute(() -> fx.swingViewport().getViewPosition());
            assertEquals("左へ 40px ドラッグした分だけ viewport が右へスクロールするはず",
                    new Point(40, 30), after);
        } finally {
            GuiActionRunner.execute(() -> fx.frame.dispose());
        }
    }

    @Test
    public void pan_middleDrag_clampsAtViewportEdges() {
        Fixture fx = buildRealViewport(300, 220, new Dimension(2000, 1500));
        try {
            JViewport vp = fx.swingViewport();
            Dimension viewSize = GuiActionRunner.execute(vp::getViewSize);
            Dimension extent = GuiActionRunner.execute(vp::getExtentSize);
            int maxX = Math.max(0, viewSize.width - extent.width);
            int maxY = Math.max(0, viewSize.height - extent.height);

            // 右下方向へ大きくドラッグ (中ボタン) → viewSize - extent でクランプされるはず。
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON2_DOWN_MASK,
                    150, 120, MouseEvent.BUTTON2);
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON2_DOWN_MASK,
                    -9000, -9000, 0);
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_RELEASED, 0, -9000, -9000, MouseEvent.BUTTON2);
            Point maxed = GuiActionRunner.execute(vp::getViewPosition);
            assertEquals("右方向は viewSize - extent でクランプされるはず", maxX, maxed.x);
            assertEquals("下方向は viewSize - extent でクランプされるはず", maxY, maxed.y);

            // 左上方向へ大きくドラッグ → 0 でクランプされるはず。
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON2_DOWN_MASK,
                    150, 120, MouseEvent.BUTTON2);
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON2_DOWN_MASK,
                    9000, 9000, 0);
            dispatchMouse(fx.canvas, MouseEvent.MOUSE_RELEASED, 0, 9000, 9000, MouseEvent.BUTTON2);
            Point zeroed = GuiActionRunner.execute(vp::getViewPosition);
            assertEquals("左方向は 0 でクランプされるはず", 0, zeroed.x);
            assertEquals("上方向は 0 でクランプされるはず", 0, zeroed.y);
        } finally {
            GuiActionRunner.execute(() -> fx.frame.dispose());
        }
    }

    @Test
    public void ctrlWheel_zoomAroundCursor_updatesZoomAndViewportAsync() throws InterruptedException {
        Fixture fx = buildRealViewport(300, 220, new Dimension(2000, 1500));
        try {
            // 先にスクロールしておき、カーソル中心ズームでその注視点がずれないことを確認する。
            Point startView = new Point(100, 80);
            GuiActionRunner.execute(() -> fx.swingViewport().setViewPosition(startView));
            assertEquals(1.0, GuiActionRunner.execute(fx.viewport::zoom), 1e-9);

            Point anchor = new Point(150, 110);
            // Ctrl+ホイール上回転 (wheelRotation < 0) は zoom * 1.1 に相当する
            // (SketchViewport.onWheel / STEP と同じ定数)。
            double expectedZoom = 1.0 * 1.1;
            Point expectedRaw = SketchViewport.anchorAdjustedViewPos(anchor, startView, 1.1);

            dispatchCtrlWheel(fx.canvas, anchor.x, anchor.y, -1);

            assertEquals("Ctrl+ホイールでズームが変わるはず",
                    expectedZoom, GuiActionRunner.execute(fx.viewport::zoom), 1e-9);

            Dimension viewSize = GuiActionRunner.execute(() -> fx.swingViewport().getViewSize());
            Dimension extent = GuiActionRunner.execute(() -> fx.swingViewport().getExtentSize());
            int expX = Math.max(0, Math.min(expectedRaw.x, Math.max(0, viewSize.width - extent.width)));
            int expY = Math.max(0, Math.min(expectedRaw.y, Math.max(0, viewSize.height - extent.height)));
            Point expected = new Point(expX, expY);

            Point finalPos = awaitViewPosition(fx.swingViewport(), expected, 5_000);
            assertEquals("カーソル中心ズーム後の viewport 位置が注視点維持の計算どおりになるはず",
                    expected, finalPos);
        } finally {
            GuiActionRunner.execute(() -> fx.frame.dispose());
        }
    }

    @Test
    public void ctrlZero_resetsZoomToOne() {
        Fixture fx = buildRealViewport(300, 220, new Dimension(2000, 1500));
        try {
            GuiActionRunner.execute(() -> fx.viewport.setZoom(2.4));
            assertEquals(2.4, GuiActionRunner.execute(fx.viewport::zoom), 1e-9);

            // Ctrl+0 は InputMap/ActionMap バインディング (WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)。
            // 実 OS フォーカス取得は xvfb 下でフレーキーなため、バインディングを直接解決して
            // Action を起動する (フォーカス非依存で「Ctrl+0 が zoom-reset に割り当てられている」
            // ことと、その Action が zoom を 1.0 へ戻すことを検証する)。
            int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            Object key = GuiActionRunner.execute(() -> fx.canvas.getInputMap(
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_0, menuMask)));
            assertEquals("Ctrl+0 が zoom-reset アクションに割り当てられているはず",
                    "sketch-zoom-reset", key);
            Action action = GuiActionRunner.execute(() -> fx.canvas.getActionMap().get(key));
            assertNotNull("zoom-reset アクションが登録されているはず", action);

            GuiActionRunner.execute(() -> action.actionPerformed(
                    new ActionEvent(fx.canvas, ActionEvent.ACTION_PERFORMED, "sketch-zoom-reset")));

            assertEquals("Ctrl+0 でズームが等倍へ戻るはず",
                    1.0, GuiActionRunner.execute(fx.viewport::zoom), 1e-9);
        } finally {
            GuiActionRunner.execute(() -> fx.frame.dispose());
        }
    }
}
