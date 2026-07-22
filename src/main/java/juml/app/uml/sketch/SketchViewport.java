// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * 図形デザイナー共通のキャンバスズーム/パン機構。
 *
 * <p>{@code Ctrl+ホイール} でズーム (0.25x〜3.0x)、中ボタンドラッグでパン
 * (JScrollPane の viewport を平行移動)。各キャンバスは
 * (1) {@link #applyTransform(Graphics2D)} をモデル描画前に適用し、
 * (2) マウス座標を {@link #toModel(Point)} でモデル座標へ逆変換し、
 * (3) {@link #scaled(Dimension)} で preferredSize を拡縮する。</p>
 *
 * <p>ホイールリスナーを付けるとスクロールが素通りしなくなるため、Ctrl なしの
 * ホイールは親 (JScrollPane) へ再送してスクロールを維持する。</p>
 */
final class SketchViewport {

    static final double MIN_ZOOM = 0.25;
    static final double MAX_ZOOM = 3.0;
    private static final double STEP = 1.1;

    private final JComponent target;
    private double zoom = 1.0;
    /** 中ボタンパンの開始点 (スクリーン座標) と開始時の viewport 位置。 */
    private Point panStartScreen;
    private Point panStartView;

    SketchViewport(JComponent target) {
        this.target = target;
        target.addMouseWheelListener(this::onWheel);
        MouseAdapter pan = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panStartScreen = e.getLocationOnScreen();
                    JViewport vp = viewportOf();
                    panStartView = vp != null ? vp.getViewPosition() : null;
                }
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (panStartScreen == null || panStartView == null) {
                    return;
                }
                JViewport vp = viewportOf();
                if (vp == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                int nx = panStartView.x - (now.x - panStartScreen.x);
                int ny = panStartView.y - (now.y - panStartScreen.y);
                Dimension view = vp.getViewSize();
                Dimension extent = vp.getExtentSize();
                nx = Math.max(0, Math.min(nx, Math.max(0, view.width - extent.width)));
                ny = Math.max(0, Math.min(ny, Math.max(0, view.height - extent.height)));
                vp.setViewPosition(new Point(nx, ny));
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    panStartScreen = null;
                    panStartView = null;
                }
            }
        };
        target.addMouseListener(pan);
        target.addMouseMotionListener(pan);

        // 発見可能性: 操作方法をホバー툴チップで明示する (ズーム/パン/微調整には
        // メニュー項目が無いため、キーだけ知っている人以外にも届くようにする)。
        target.setToolTipText(juml.util.Messages.get("sketch.viewport.tip"));

        // ズームリセット (Ctrl+0): 縮小しすぎて図が見失われても等倍へ戻せるようにする。
        // UI 上にリセット手段が無いと MIN_ZOOM まで縮めたとき戻し方が分からなくなる。
        int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im =
                target.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        javax.swing.ActionMap am = target.getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, menuMask),
                "sketch-zoom-reset");
        am.put("sketch-zoom-reset", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                setZoom(1.0);
            }
        });
    }

    private void onWheel(MouseWheelEvent e) {
        if (e.isControlDown()) {
            setZoom(e.getWheelRotation() < 0 ? zoom * STEP : zoom / STEP);
            e.consume();
            return;
        }
        // 通常ホイールは親 (JScrollPane) へ転送してスクロールを維持する。
        java.awt.Container parent = target.getParent();
        if (parent != null) {
            parent.dispatchEvent(SwingUtilities.convertMouseEvent(target, e, parent));
        }
    }

    double zoom() {
        return zoom;
    }

    void setZoom(double z) {
        double clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        if (clamped == zoom) {
            return;
        }
        zoom = clamped;
        target.revalidate();
        target.repaint();
    }

    /** モデル描画用のスケールを適用する (オーバーレイは適用前の Graphics で描く)。 */
    void applyTransform(Graphics2D g2) {
        g2.scale(zoom, zoom);
    }

    /** ビュー座標 (マウスイベント) をモデル座標へ逆変換する。 */
    Point toModel(Point view) {
        return new Point((int) Math.round(view.x / zoom), (int) Math.round(view.y / zoom));
    }

    /** モデル座標系の推奨サイズを現在ズームのビュー座標系へ拡縮する。 */
    Dimension scaled(Dimension modelSize) {
        return new Dimension((int) Math.ceil(modelSize.width * zoom),
                (int) Math.ceil(modelSize.height * zoom));
    }

    private JViewport viewportOf() {
        return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, target);
    }
}
