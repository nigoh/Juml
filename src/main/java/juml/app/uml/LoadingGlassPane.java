// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;

/**
 * プロジェクト解析中に {@link UmlMainFrame} の glass pane として全面に被せる
 * ローディングオーバーレイ。
 *
 * <p>stock_controller の {@code layer: overlay} 中央配置に相当する。半透明の暗幕を
 * 全面に描き、中央にブランドロゴ ({@link JumlLogo}) の回転アニメーションとステータス
 * 文言を載せる。解析中は背後の UI を誤操作させないよう、マウス/キーイベントを自身で
 * 消費する。</p>
 *
 * <p>アニメーションは GIF ではなく Java2D 描画を {@link Timer} で駆動する。フレーム
 * 更新のたびに glass pane 全体を {@code repaint()} するため、暗幕 (scrim) が部分再描画で
 * 消える従来の GIF 特有の問題は起きない。表示中だけタイマーを回し、非表示化で止める。</p>
 */
final class LoadingGlassPane extends JComponent {

    /** 背後 UI を覆う半透明の暗幕色。 */
    private static final Color SCRIM = new Color(0, 0, 0, 150);
    /** ロゴマークの一辺 (px)。 */
    private static final int MARK_SIZE = 72;
    /** アニメーションのフレーム間隔 (ms)。約 30fps。 */
    private static final int FRAME_MILLIS = 33;
    /** 1 回転にかけるフレーム数。 */
    private static final double ROTATION_FRAMES = 60.0;

    private final JButton cancelButton;
    private final Timer timer;
    private long tick;
    private Runnable cancelAction;
    private String status = Messages.get("loading.initial");

    LoadingGlassPane() {
        setOpaque(false);
        setVisible(false);
        setLayout(null);
        timer = new Timer(FRAME_MILLIS, e -> {
            tick++;
            repaint();
        });
        timer.setCoalesce(true);
        cancelButton = new JButton(Messages.get("loading.cancel"));
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> {
            if (cancelAction != null) {
                cancelAction.run();
            }
        });
        add(cancelButton);
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });
        addKeyListener(new KeyAdapter() { });
    }

    /** ステータス文言を更新する (stock_controller の {@code update_status} 相当)。 */
    void setStatus(String message) {
        status = message != null ? message : "";
        repaint();
    }

    /** キャンセルアクションを設定する。非 null ならキャンセルボタンを表示する。 */
    void setCancelAction(Runnable action) {
        cancelAction = action;
        cancelButton.setVisible(action != null);
    }

    /** オーバーレイを表示する。フォーカスを奪い、解析中のキーボードショートカットを遮断する。 */
    void showOverlay() {
        setVisible(true);
        setFocusable(true);
        requestFocusInWindow();
        timer.start();
    }

    /** オーバーレイを隠す。 */
    void hideOverlay() {
        timer.stop();
        cancelAction = null;
        cancelButton.setVisible(false);
        setFocusable(false);
        setVisible(false);
    }

    @Override
    public void doLayout() {
        if (cancelButton.isVisible()) {
            Dimension pref = cancelButton.getPreferredSize();
            int bx = (getWidth() - pref.width) / 2;
            int by = getHeight() / 2 + 70;
            cancelButton.setBounds(bx, by, pref.width, pref.height);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setColor(SCRIM);
            g2.fillRect(0, 0, w, h);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int cx = w / 2;
            int cy = h / 2 - 16;
            double phase = (tick % ROTATION_FRAMES) / ROTATION_FRAMES;
            double breath = 1.0 + 0.035 * Math.sin(tick * (2 * Math.PI / (ROTATION_FRAMES * 1.5)));
            JumlLogo.paintSpinner(g2, cx, cy, MARK_SIZE * 0.72, phase);
            JumlLogo.paintMark(g2, cx, cy, MARK_SIZE, breath);

            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            FontMetrics fm = g2.getFontMetrics();
            int textBaselineY = cy + (int) (MARK_SIZE * 0.72) + 24;
            g2.drawString(status, (w - fm.stringWidth(status)) / 2, textBaselineY + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
