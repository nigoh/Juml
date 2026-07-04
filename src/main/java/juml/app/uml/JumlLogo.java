// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Juml のブランドロゴと、そのローディングアニメーションを Java2D で描く、
 * 外部アセット非依存のベクター描画エンジン。
 *
 * <p>従来のローディングは差し替え可能な GIF ({@code loading.gif} 等) をランダム再生する
 * 作りで、遊び画像が混ざり「ふざけた」印象になりがちだった。これを廃し、Juml = Java + UML を
 * 象徴する<b>ミニ UML クラス図</b>をロゴマークとして Java2D で描く。</p>
 *
 * <p><b>ロゴマークの意匠</b>: 2 つのクラスボックス (青の子・紫の親) を UML の<b>継承 (汎化)
 * 矢印</b>でつなぐ。親ボックスの背後には半透明のオフセット層を敷き、「重なり／レイヤー」の
 * 奥行きを出す。全形状は塗りで前後関係を表現するため<b>背景色に依存せず</b>、暗幕・スプラッシュ・
 * ライトテーマ・透過アイコンのいずれでも破綻しない。{@link MaterialIcons} と同じく 100 単位の
 * 正規化座標系で描いてから表示サイズへスケールするので、アイコン用途からスプラッシュ用途まで
 * 同一コードで賄える。</p>
 *
 * <p>状態を持たない純粋な描画ユーティリティ。アニメーションの「時間」は呼び出し側が
 * {@code phase} (継承エッジ上を流れるパルスの位置) と {@code breath} (拡縮) として渡す。</p>
 */
final class JumlLogo {

    /** 子クラスボックスのブランド青。 */
    static final Color BRAND = new Color(0x2F81F7);
    /** 親クラスボックスのアクセント紫。 */
    private static final Color BRAND2 = new Color(0x7C5CFF);
    /** クラスボックスのヘッダ区切り線 (白の半透明)。 */
    private static final Color HEADER = new Color(255, 255, 255, 205);
    /** 継承エッジ・矢印の色 (淡いグレー)。 */
    private static final Color EDGE = new Color(0xC9D3E0);
    /** エッジ上を流れるパルスのコア色。 */
    private static final Color PULSE_CORE = new Color(0xEAF4FF);

    /** 正規化座標系の 1 辺。すべての形状はこの座標で描いてからスケールする。 */
    private static final double GRID = 100.0;

    // 継承エッジの端点 (子ボックス上辺 → 親ボックス左下)。パルスもこの線上を流れる。
    private static final double EDGE_X0 = 40;
    private static final double EDGE_Y0 = 60;
    private static final double EDGE_X1 = 58;
    private static final double EDGE_Y1 = 48;

    private JumlLogo() {
    }

    /**
     * ロゴマーク (継承でつながる 2 クラスボックス) を、中心 {@code (cx, cy)}・1 辺 {@code size}
     * px で描く。
     *
     * @param breath 呼吸スケール係数 (通常 1.0 前後)。1.0 で等倍。
     */
    static void paintMark(Graphics2D g, double cx, double cy, double size, double breath) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            applyQualityHints(g2);
            double s = size * breath;
            g2.translate(cx - s / 2.0, cy - s / 2.0);
            g2.scale(s / GRID, s / GRID);
            paintMarkInGrid(g2);
        } finally {
            g2.dispose();
        }
    }

    /**
     * 継承エッジ上を子→親へ流れる「解析中」のパルスを描く。マークの静止した可読性を保ったまま
     * 動きを与える indeterminate インジケータ。
     *
     * @param phase 0.0〜1.0。0 で子側、1 で親側。呼び出し側が時間から与える。
     */
    static void paintPulse(Graphics2D g, double cx, double cy, double size, double phase) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            applyQualityHints(g2);
            g2.translate(cx - size / 2.0, cy - size / 2.0);
            g2.scale(size / GRID, size / GRID);

            double t = clamp01(phase);
            double px = EDGE_X0 + (EDGE_X1 - EDGE_X0) * t;
            double py = EDGE_Y0 + (EDGE_Y1 - EDGE_Y0) * t;
            double fade = 1.0 - Math.abs(t - 0.5) * 0.7; // 端で少し淡く

            // 外周グロー (多重円で柔らかく)。
            for (int i = 3; i >= 1; i--) {
                int alpha = (int) Math.max(0, (70.0 / i) * fade);
                g2.setColor(new Color(0x4F, 0xC1, 0xFF, alpha));
                double r = 3.0 * i;
                g2.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
            }
            // コア。
            g2.setColor(PULSE_CORE);
            g2.fill(new Ellipse2D.Double(px - 2.4, py - 2.4, 4.8, 4.8));
        } finally {
            g2.dispose();
        }
    }

    /**
     * ロゴマークを指定 px の正方形 {@link BufferedImage} に描いて返す。
     * ウィンドウアイコン ({@code setIconImage}) などの静的用途向け。
     */
    static BufferedImage renderMarkImage(int px) {
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            paintMark(g2, px / 2.0, px / 2.0, px, 1.0);
        } finally {
            g2.dispose();
        }
        return img;
    }

    /** 100 グリッド座標系でミニ UML クラス図型ロゴマークを描く。 */
    private static void paintMarkInGrid(Graphics2D g) {
        // 親ボックス背後のゴースト層 (半透明・オフセット) = 重なり／レイヤーの奥行き。
        g.setColor(new Color(BRAND2.getRed(), BRAND2.getGreen(), BRAND2.getBlue(), 70));
        g.fill(new RoundRectangle2D.Double(56, 8, 38, 30, 8, 8));

        // 継承エッジ (子→親)。矢印の底で止める。
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(EDGE_X0, EDGE_Y0, EDGE_X1, EDGE_Y1));

        // 継承 (汎化) の三角矢頭を親側へ。
        Graphics2D gt = (Graphics2D) g.create();
        try {
            gt.translate(60, 46);
            gt.rotate(Math.toRadians(-33));
            GeneralPath tri = new GeneralPath();
            tri.moveTo(0, 0);
            tri.lineTo(-9, -6.5);
            tri.lineTo(-9, 6.5);
            tri.closePath();
            gt.setColor(EDGE);
            gt.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gt.draw(tri);
        } finally {
            gt.dispose();
        }

        // 親ボックス (紫・塗り) → 背後ゴーストを自然に隠す。
        filledClassBox(g, 50, 12, 38, 30, BRAND2);
        // 子ボックス (青・塗り)。
        filledClassBox(g, 10, 58, 40, 30, BRAND);
    }

    /** 塗りつぶしのクラスボックス (角丸 + ヘッダ区切り線) を描く。 */
    private static void filledClassBox(Graphics2D g, double x, double y, double w, double h,
            Color fill) {
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 8, 8));
        g.setColor(HEADER);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x + 6, y + h * 0.40, x + w - 6, y + h * 0.40));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** アンチエイリアス等、ベクター描画の品質ヒントをまとめて適用する。 */
    private static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
