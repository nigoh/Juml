// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Juml のブランドロゴ ({@link JumlLogo}) を回転アークと呼吸スケールでアニメーションさせる
 * 再利用可能なローディング表示。起動スプラッシュ ({@link SplashWindow}) と共通で使い、
 * 見た目を統一する。
 *
 * <p>従来の GIF ラベルベースの実装を置き換える。Java2D 描画を {@link Timer} で駆動する
 * ため、外部 GIF アセットに依存せず任意サイズで破綻しない。可視状態でないときは
 * タイマーを止めて CPU を使わない ({@code addNotify}/{@code removeNotify} で制御)。</p>
 */
final class JumlLoadingView extends JComponent {

    /** アニメーションのフレーム間隔 (ms)。約 30fps。 */
    private static final int FRAME_MILLIS = 33;
    /** 継承パルスが子→親を 1 往きするフレーム数 (小さいほど速い)。 */
    private static final double PULSE_FRAMES = 40.0;
    /** 呼吸スケール 1 周期のフレーム数。 */
    private static final double BREATH_FRAMES = 90.0;
    /** ロゴマークの一辺 (px)。 */
    private final int markSize;
    /** ワードマーク "Juml" を描くか (スプラッシュでは true)。 */
    private final boolean showWordmark;

    private final Timer timer;
    private long tick;
    private String status;

    JumlLoadingView(String initialStatus, int markSize, boolean showWordmark) {
        this.markSize = markSize;
        this.showWordmark = showWordmark;
        this.status = initialStatus != null ? initialStatus : "";
        setOpaque(false);
        timer = new Timer(FRAME_MILLIS, e -> {
            tick++;
            repaint();
        });
        timer.setCoalesce(true);
    }

    /** ステータス文言を更新する。 */
    void setStatus(String message) {
        status = message != null ? message : "";
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int pad = 32;
        int textBlock = 24 + (showWordmark ? 40 : 0);
        int side = markSize + pad * 2;
        return new Dimension(Math.max(side, 260), markSize + pad * 2 + textBlock);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintAnimation(g2, getWidth(), getHeight());
        } finally {
            g2.dispose();
        }
    }

    /** ロゴ + 継承パルス + (任意で) ワードマーク + ステータスを描く共通ルーチン。 */
    void paintAnimation(Graphics2D g2, int w, int h) {
        double phase = (tick % PULSE_FRAMES) / PULSE_FRAMES;
        double breath = 1.0 + 0.03 * Math.sin(tick * (2 * Math.PI / BREATH_FRAMES));

        int cx = w / 2;
        int markCy = showWordmark ? h / 2 - 34 : h / 2 - 12;

        JumlLogo.paintMark(g2, cx, markCy, markSize, breath);
        JumlLogo.paintPulse(g2, cx, markCy, markSize, phase);

        // 親未装着時は getFont() が null になり得るためフォールバックする。
        Font base = getFont();
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        int textY = markCy + (int) (markSize * 0.55) + 14;
        Color fg = new Color(0xF5FAFF);
        if (showWordmark) {
            g2.setColor(fg);
            g2.setFont(base.deriveFont(Font.BOLD, markSize * 0.34f));
            textY = drawCentered(g2, "Juml", cx, textY + g2.getFontMetrics().getAscent());
            textY += 8;
        }
        g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 210));
        g2.setFont(base.deriveFont(Font.PLAIN, 13f));
        drawCentered(g2, status, cx, textY + g2.getFontMetrics().getAscent());
    }

    /** 文字列を水平中央に描き、その下端 y を返す。 */
    private static int drawCentered(Graphics2D g2, String text, int cx, int baselineY) {
        if (text == null || text.isEmpty()) {
            return baselineY;
        }
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text) / 2, baselineY);
        return baselineY + fm.getDescent();
    }
}
