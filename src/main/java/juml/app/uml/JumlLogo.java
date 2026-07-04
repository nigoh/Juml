// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
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
 * 作りで、遊び画像が混ざり「ふざけた」印象になりがちだった。これを廃し、ブランドを
 * 象徴する UML クラスボックス型のロゴマークと、その周囲を回るアーク (indeterminate
 * spinner) を Java2D で描くことで、任意 DPI・任意サイズで破綻せず一貫して洗練された
 * ローディング表現に統一する。</p>
 *
 * <p><b>ロゴマークの意匠</b>: Juml = Java + UML を象徴する「クラスボックス」。角丸タイルに
 * ブランド青のグラデーションを敷き、上部にクラス図の区切り線 (ヘッダコンパートメント) を
 * 引き、本体に頭文字「J」をベクターで描く。{@link MaterialIcons} と同じく 100 単位の
 * 正規化座標系で描いてから表示サイズへスケールするため、アイコン用途 (small) から
 * スプラッシュ用途 (large) まで同一コードで賄える。</p>
 *
 * <p>状態を持たない純粋な描画ユーティリティ。アニメーションの「時間」は呼び出し側が
 * {@code phase} (回転) と {@code breath} (拡縮) として渡す。</p>
 */
final class JumlLogo {

    /** ブランドの基準色 (VS Code アクセントに合わせた青)。{@link UiTheme#ACCENT} と同値。 */
    static final Color BRAND = new Color(0x007ACC);
    /** ロゴタイル上端側のグラデーション色 (明るい青)。 */
    private static final Color BRAND_LIGHT = new Color(0x2A9BE0);
    /** ロゴタイル下端側のグラデーション色 (濃い青)。 */
    private static final Color BRAND_DARK = new Color(0x005A9E);
    /** スピナーアークの色 (視認性の高い明るい水色)。 */
    private static final Color SPINNER = new Color(0x4FC1FF);
    /** マーク前景 (J・区切り線) の白。 */
    private static final Color MARK_FG = new Color(0xF5FAFF);

    /** 正規化座標系の 1 辺。すべての形状はこの座標で描いてからスケールする。 */
    private static final double GRID = 100.0;

    private JumlLogo() {
    }

    /**
     * ロゴマーク (クラスボックス + J) を、中心 {@code (cx, cy)}・1 辺 {@code size} px で描く。
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
     * ロゴマークの周囲を回る indeterminate スピナー (回転アーク) を描く。
     *
     * @param radius アークの半径 (px)。通常はマークの外接円より少し外側にする。
     * @param phase  0.0〜1.0 で 1 回転する連続位相。呼び出し側が時間から与える。
     */
    static void paintSpinner(Graphics2D g, double cx, double cy, double radius, double phase) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            applyQualityHints(g2);
            float stroke = (float) Math.max(2.0, radius * 0.12);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // 背景トラック (淡い全周リング) — 進捗感の土台。
            g2.setColor(new Color(SPINNER.getRed(), SPINNER.getGreen(), SPINNER.getBlue(), 45));
            g2.draw(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));

            // 前景アーク: 270 度を切り出し、位相に応じて時計回りに回す。
            double startDeg = -(phase * 360.0);
            g2.setColor(SPINNER);
            g2.draw(new Arc2D.Double(
                    cx - radius, cy - radius, radius * 2, radius * 2,
                    startDeg, -270.0, Arc2D.OPEN));
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

    /** 100 グリッド座標系でクラスボックス型ロゴマークを描く。 */
    private static void paintMarkInGrid(Graphics2D g) {
        // タイル (角丸): ブランド青の縦グラデーション。
        RoundRectangle2D tile = new RoundRectangle2D.Double(6, 6, 88, 88, 26, 26);
        g.setPaint(new GradientPaint(0, 6, BRAND_LIGHT, 0, 94, BRAND_DARK));
        g.fill(tile);

        // タイル縁: 一段濃い青で締める。
        g.setColor(BRAND_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(tile);

        // クラス図のヘッダ区切り線 (コンパートメント境界)。
        g.setColor(new Color(MARK_FG.getRed(), MARK_FG.getGreen(), MARK_FG.getBlue(), 200));
        g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(22, 34, 78, 34));

        // 頭文字「J」: 上部セリフバー + 縦ステム + 左下フック。
        g.setColor(MARK_FG);
        g.setStroke(new BasicStroke(11.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(46, 48, 72, 48)); // 上部の横棒
        GeneralPath j = new GeneralPath();
        j.moveTo(63, 48);
        j.lineTo(63, 68);
        j.curveTo(63, 80, 54, 84, 44, 82);
        j.curveTo(38, 81, 34, 77, 33, 72);
        g.draw(j);
    }

    /** アンチエイリアス等、ベクター描画の品質ヒントをまとめて適用する。 */
    private static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
