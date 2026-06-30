// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.Icon;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * マテリアルデザイン (Material Symbols / outlined) 風のベクターアイコンを
 * Java2D で描画する、外部アセット非依存のアイコン体系。
 *
 * <p><b>設計方針</b>:</p>
 * <ul>
 *   <li><b>24dp グリッド</b>: 各グリフを 24×24 単位の座標系で描き、表示サイズに
 *       スケールする。これにより任意 DPI / 任意サイズで輪郭が破綻しない。</li>
 *   <li><b>テーマ対応</b>: 既定の描画色は {@link UIManager} の前景色から実行時に
 *       解決する。FlatLaf Light / Dark を切り替えても自動で追従する。</li>
 *   <li><b>アウトライン主体</b>: 2dp 相当の丸ストロークでアウトラインを描く
 *       Material Symbols の "outlined" スタイルに揃え、トーンを統一する。</li>
 * </ul>
 *
 * <p>従来の幾何形状ベタ塗りアイコンを置き換え、ツールバー・メニュー・ツリーの
 * アイコンを一貫した洗練トーンに統一する。</p>
 */
public final class MaterialIcons implements Icon {

    /** デザイングリッドの 1 辺 (dp 相当)。すべてのグリフはこの座標系で描く。 */
    private static final double GRID = 24.0;
    /** outlined スタイルの標準ストローク幅 (24 グリッド単位)。 */
    private static final float STROKE = 2.0f;

    /** 利用可能なグリフ。命名は Material Symbols のアイコン名に概ね対応する。 */
    public enum Glyph {
        // ── ファイル / プロジェクト操作 ──
        FOLDER_OPEN, ARCHIVE, SAVE, REFRESH, SEARCH, NOTE_ADD, CLOSE,
        // ── 表示 / ズーム / ナビゲーション ──
        ZOOM_IN, ZOOM_OUT, FIT_SCREEN, CENTER_FOCUS, SIDEBAR, CODE,
        TERMINAL, FILTER, ARROW_BACK, ARROW_FORWARD, CHEVRON_UP, CHEVRON_DOWN,
        // ── 設定 / スタイル ──
        SETTINGS, TUNE, PALETTE, DELETE_SWEEP, HELP, INFO,
        // ── 図種 / 構造 (ツリー & 図カテゴリ) ──
        SCHEMA, ACCOUNT_TREE, HUB, TIMELINE, FLOWCHART, CALL_SPLIT, CYCLE,
        // ── ツリーノード ──
        PACKAGE, MODULE, FUNCTION, MANIFEST, WIDGETS, SHIELD, STAR,
        BOLT, EXTENSION, LAYERS, GRID, ROUTE
    }

    private final Glyph glyph;
    private final int size;
    private final Color color; // null ならテーマ前景色を実行時解決

    private MaterialIcons(Glyph glyph, int size, Color color) {
        this.glyph = glyph;
        this.size = size;
        this.color = color;
    }

    /** テーマ前景色で塗る {@code size}px 角のアイコンを返す。 */
    public static Icon of(Glyph glyph, int size) {
        return new MaterialIcons(glyph, size, null);
    }

    /** 指定色で塗る {@code size}px 角のアイコンを返す。 */
    public static Icon of(Glyph glyph, int size, Color color) {
        return new MaterialIcons(glyph, size, color);
    }

    /** ツールバー用の標準サイズ (18px) アイコン。 */
    public static Icon toolbar(Glyph glyph) {
        return new MaterialIcons(glyph, 18, null);
    }

    /** メニュー項目用の標準サイズ (16px) アイコン。 */
    public static Icon menu(Glyph glyph) {
        return new MaterialIcons(glyph, 16, null);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        paint((Graphics2D) g, x, y);
    }

    @Override public int getIconWidth() {
        return size;
    }

    @Override public int getIconHeight() {
        return size;
    }

    /** テーマの前景色 (なければ妥当な既定) を解決する。 */
    private static Color themeForeground() {
        Color c = UIManager.getColor("Tree.foreground");
        if (c == null) {
            c = UIManager.getColor("Label.foreground");
        }
        // FlatLaf Dark でも見やすいよう、純粋な黒/白ではなくやや柔らかいトーンへ寄せる。
        return c != null ? c : new Color(0x3C3C3C);
    }

    private void paint(Graphics2D g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            double scale = size / GRID;
            g.translate(x, y);
            g.scale(scale, scale);
            g.setColor(color != null ? color : themeForeground());
            g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            drawGlyph(g, glyph);
        } finally {
            g.dispose();
        }
    }

    // ── グリフ描画 (24×24 座標系) ────────────────────────────────────────
    // 各グリフは Material Symbols outlined を Java2D で近似したもの。
    // stroke(): 輪郭線、fill(): ベタ塗り、dot(): 小円。

    private static void drawGlyph(Graphics2D g, Glyph glyph) {
        switch (glyph) {
            case FOLDER_OPEN: folderOpen(g); break;
            case ARCHIVE: archive(g); break;
            case SAVE: save(g); break;
            case REFRESH: refresh(g); break;
            case SEARCH: search(g); break;
            case NOTE_ADD: noteAdd(g); break;
            case CLOSE: close(g); break;
            case ZOOM_IN: zoom(g, true); break;
            case ZOOM_OUT: zoom(g, false); break;
            case FIT_SCREEN: fitScreen(g); break;
            case CENTER_FOCUS: centerFocus(g); break;
            case SIDEBAR: sidebar(g); break;
            case CODE: code(g); break;
            case TERMINAL: terminal(g); break;
            case FILTER: filter(g); break;
            case SETTINGS: settings(g); break;
            case TUNE: tune(g); break;
            case PALETTE: palette(g); break;
            case DELETE_SWEEP: trash(g); break;
            case HELP: help(g); break;
            case INFO: info(g); break;
            case SCHEMA: schema(g); break;
            case ACCOUNT_TREE: accountTree(g); break;
            case HUB: hub(g); break;
            case TIMELINE: timeline(g); break;
            case FLOWCHART: flowchart(g); break;
            case CALL_SPLIT: callSplit(g); break;
            case CYCLE: cycle(g); break;
            case PACKAGE: pkg(g); break;
            case MODULE: module(g); break;
            case FUNCTION: function(g); break;
            case MANIFEST: manifest(g); break;
            case WIDGETS: widgets(g); break;
            case SHIELD: shield(g); break;
            case STAR: star(g); break;
            case BOLT: bolt(g); break;
            case EXTENSION: extension(g); break;
            case LAYERS: layers(g); break;
            case GRID: grid(g); break;
            case ROUTE: route(g); break;
            case ARROW_BACK: arrowBack(g); break;
            case ARROW_FORWARD: arrowForward(g); break;
            case CHEVRON_UP: chevronUp(g); break;
            case CHEVRON_DOWN: chevronDown(g); break;
            default: break;
        }
    }

    // ── 描画ヘルパ ──
    private static void stroke(Graphics2D g, java.awt.Shape s) {
        g.draw(s);
    }

    private static void fill(Graphics2D g, java.awt.Shape s) {
        g.fill(s);
    }

    private static void dot(Graphics2D g, double cx, double cy, double r) {
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static GeneralPath path() {
        return new GeneralPath();
    }

    // ── 個別グリフ ─────────────────────────────────────────────────────

    private static void folderOpen(Graphics2D g) {
        // 開いたフォルダ: タブ付きの台形
        GeneralPath p = path();
        p.moveTo(3, 6.5);
        p.lineTo(9.5, 6.5);
        p.lineTo(11.5, 9);
        p.lineTo(21, 9);
        p.lineTo(21, 19);
        p.lineTo(3, 19);
        p.closePath();
        stroke(g, p);
        // 前面のフタ (奥行き感)
        line(g, 3, 19, 6, 12.5);
        line(g, 6, 12.5, 22.5, 12.5);
        line(g, 22.5, 12.5, 21, 19);
    }

    private static void archive(Graphics2D g) {
        // 箱: 上フタ + 本体 + 取っ手スリット
        stroke(g, new RoundRectangle2D.Double(3.5, 5, 17, 3.5, 1.5, 1.5));
        GeneralPath p = path();
        p.moveTo(5, 8.5);
        p.lineTo(5, 19);
        p.lineTo(19, 19);
        p.lineTo(19, 8.5);
        stroke(g, p);
        line(g, 10, 12, 14, 12);
    }

    private static void save(Graphics2D g) {
        // ダウンロード/保存: 下向き矢印 + トレイ
        line(g, 12, 4, 12, 14.5);
        GeneralPath arrow = path();
        arrow.moveTo(7.5, 10);
        arrow.lineTo(12, 14.5);
        arrow.lineTo(16.5, 10);
        stroke(g, arrow);
        GeneralPath tray = path();
        tray.moveTo(4.5, 16);
        tray.lineTo(4.5, 19.5);
        tray.lineTo(19.5, 19.5);
        tray.lineTo(19.5, 16);
        stroke(g, tray);
    }

    private static void refresh(Graphics2D g) {
        // 円形の回転矢印 (上に隙間 + 矢じり)
        java.awt.geom.Arc2D arc = new java.awt.geom.Arc2D.Double(
                4.5, 4.5, 15, 15, 70, 280, java.awt.geom.Arc2D.OPEN);
        stroke(g, arc);
        // 矢じり (右上)
        GeneralPath head = path();
        head.moveTo(17.2, 4.2);
        head.lineTo(17.6, 8.2);
        head.lineTo(13.8, 7.0);
        stroke(g, head);
    }

    private static void search(Graphics2D g) {
        stroke(g, new Ellipse2D.Double(5, 5, 10, 10));
        line(g, 14.5, 14.5, 20, 20);
    }

    private static void noteAdd(Graphics2D g) {
        // 付箋 (折れ角) + プラス
        GeneralPath p = path();
        p.moveTo(5, 4.5);
        p.lineTo(15, 4.5);
        p.lineTo(19.5, 9);
        p.lineTo(19.5, 19.5);
        p.lineTo(5, 19.5);
        p.closePath();
        stroke(g, p);
        GeneralPath fold = path();
        fold.moveTo(15, 4.5);
        fold.lineTo(15, 9);
        fold.lineTo(19.5, 9);
        stroke(g, fold);
        line(g, 12, 12, 12, 16.5);
        line(g, 9.75, 14.25, 14.25, 14.25);
    }

    private static void close(Graphics2D g) {
        line(g, 6.5, 6.5, 17.5, 17.5);
        line(g, 17.5, 6.5, 6.5, 17.5);
    }

    private static void zoom(Graphics2D g, boolean plus) {
        stroke(g, new Ellipse2D.Double(4.5, 4.5, 11, 11));
        line(g, 15, 15, 20, 20);
        line(g, 7, 10, 13, 10);
        if (plus) {
            line(g, 10, 7, 10, 13);
        }
    }

    private static void fitScreen(Graphics2D g) {
        // 画面枠 + 四隅の外向き矢印
        stroke(g, new RoundRectangle2D.Double(4, 6, 16, 12, 2, 2));
        // 左上
        GeneralPath tl = path();
        tl.moveTo(7, 10);
        tl.lineTo(7, 8.5);
        tl.lineTo(9.5, 8.5);
        stroke(g, tl);
        // 右下
        GeneralPath br = path();
        br.moveTo(17, 14);
        br.lineTo(17, 15.5);
        br.lineTo(14.5, 15.5);
        stroke(g, br);
    }

    private static void centerFocus(Graphics2D g) {
        stroke(g, new RoundRectangle2D.Double(4, 4, 16, 16, 3, 3));
        dot(g, 12, 12, 2.2);
        line(g, 12, 4, 12, 7);
        line(g, 12, 17, 12, 20);
        line(g, 4, 12, 7, 12);
        line(g, 17, 12, 20, 12);
    }

    private static void sidebar(Graphics2D g) {
        stroke(g, new RoundRectangle2D.Double(4, 5, 16, 14, 2, 2));
        line(g, 10, 5, 10, 19);
        // サイドバー領域を示す細線
        line(g, 6.3, 9, 8, 9);
        line(g, 6.3, 12, 8, 12);
        line(g, 6.3, 15, 8, 15);
    }

    private static void code(Graphics2D g) {
        // </>
        GeneralPath left = path();
        left.moveTo(9, 8);
        left.lineTo(4.5, 12);
        left.lineTo(9, 16);
        stroke(g, left);
        GeneralPath right = path();
        right.moveTo(15, 8);
        right.lineTo(19.5, 12);
        right.lineTo(15, 16);
        stroke(g, right);
        line(g, 13, 6.5, 11, 17.5);
    }

    private static void terminal(Graphics2D g) {
        stroke(g, new RoundRectangle2D.Double(4, 5, 16, 14, 2, 2));
        GeneralPath chevron = path();
        chevron.moveTo(7.5, 10);
        chevron.lineTo(10.5, 12.5);
        chevron.lineTo(7.5, 15);
        stroke(g, chevron);
        line(g, 12, 15, 16, 15);
    }

    private static void filter(Graphics2D g) {
        GeneralPath p = path();
        p.moveTo(4, 6);
        p.lineTo(20, 6);
        p.lineTo(14, 13);
        p.lineTo(14, 19);
        p.lineTo(10, 16.5);
        p.lineTo(10, 13);
        p.closePath();
        stroke(g, p);
    }

    private static void settings(Graphics2D g) {
        // 歯車: 歯付きの外周リング + 中央のハブ穴。
        double cx = 12;
        double cy = 12;
        int teeth = 8;
        double rOut = 9.2;   // 歯先
        double rIn = 7.0;    // 歯元 (リング外周)
        double half = Math.toRadians(11); // 歯の角半幅
        GeneralPath gear = path();
        for (int i = 0; i < teeth; i++) {
            double a = Math.toRadians(i * (360.0 / teeth));
            // 歯元 → 歯先 (立ち上がり) → 歯先 → 歯元 (立ち下がり)
            addArcPoint(gear, cx, cy, rIn, a - half - Math.toRadians(11), i == 0);
            addArcPoint(gear, cx, cy, rOut, a - half, false);
            addArcPoint(gear, cx, cy, rOut, a + half, false);
            addArcPoint(gear, cx, cy, rIn, a + half + Math.toRadians(11), false);
        }
        gear.closePath();
        stroke(g, gear);
        // 中央ハブ
        stroke(g, new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));
    }

    private static void addArcPoint(GeneralPath p, double cx, double cy,
                                    double r, double ang, boolean first) {
        double px = cx + r * Math.cos(ang);
        double py = cy + r * Math.sin(ang);
        if (first) {
            p.moveTo(px, py);
        } else {
            p.lineTo(px, py);
        }
    }

    private static void tune(Graphics2D g) {
        // スライダー 3 本 + つまみ
        line(g, 4, 7.5, 20, 7.5);
        line(g, 4, 12, 20, 12);
        line(g, 4, 16.5, 20, 16.5);
        dot(g, 9, 7.5, 2.2);
        dot(g, 15, 12, 2.2);
        dot(g, 8, 16.5, 2.2);
    }

    private static void palette(Graphics2D g) {
        // パレット (欠けた円) + 絵の具のドット
        java.awt.geom.Arc2D arc = new java.awt.geom.Arc2D.Double(
                4, 4, 16, 16, -25, 300, java.awt.geom.Arc2D.OPEN);
        stroke(g, arc);
        // 親指穴 (右下) を小さく
        stroke(g, new Ellipse2D.Double(14.5, 14, 4.5, 4));
        dot(g, 8, 9, 1.3);
        dot(g, 12, 7.5, 1.3);
        dot(g, 16, 9, 1.3);
        dot(g, 8.5, 13, 1.3);
    }

    private static void trash(Graphics2D g) {
        line(g, 5, 7, 19, 7);
        GeneralPath body = path();
        body.moveTo(6.5, 7);
        body.lineTo(7.3, 19.5);
        body.lineTo(16.7, 19.5);
        body.lineTo(17.5, 7);
        stroke(g, body);
        // フタの取っ手
        GeneralPath lid = path();
        lid.moveTo(9.5, 7);
        lid.lineTo(9.5, 4.7);
        lid.lineTo(14.5, 4.7);
        lid.lineTo(14.5, 7);
        stroke(g, lid);
        line(g, 10, 10, 10.4, 16.5);
        line(g, 14, 10, 13.6, 16.5);
    }

    private static void help(Graphics2D g) {
        stroke(g, new Ellipse2D.Double(4, 4, 16, 16));
        // 疑問符
        java.awt.geom.Arc2D q = new java.awt.geom.Arc2D.Double(
                8.5, 7, 7, 6, 200, -230, java.awt.geom.Arc2D.OPEN);
        stroke(g, q);
        line(g, 12, 13, 12, 15);
        dot(g, 12, 17, 1.1);
    }

    private static void info(Graphics2D g) {
        stroke(g, new Ellipse2D.Double(4, 4, 16, 16));
        dot(g, 12, 8, 1.2);
        line(g, 12, 11, 12, 16.5);
    }

    private static void schema(Graphics2D g) {
        // 3 ノードのツリー (上 1 → 下 2)
        stroke(g, new RoundRectangle2D.Double(9, 4, 6, 4, 1, 1));
        stroke(g, new RoundRectangle2D.Double(4, 16, 6, 4, 1, 1));
        stroke(g, new RoundRectangle2D.Double(14, 16, 6, 4, 1, 1));
        line(g, 12, 8, 12, 12);
        line(g, 7, 16, 7, 12);
        line(g, 17, 16, 17, 12);
        line(g, 7, 12, 17, 12);
    }

    private static void accountTree(Graphics2D g) {
        // 親 (左) → 子 2 (右)
        stroke(g, new RoundRectangle2D.Double(3.5, 9.5, 5, 5, 1, 1));
        stroke(g, new RoundRectangle2D.Double(15.5, 4.5, 5, 5, 1, 1));
        stroke(g, new RoundRectangle2D.Double(15.5, 14.5, 5, 5, 1, 1));
        line(g, 8.5, 12, 12, 12);
        line(g, 12, 7, 12, 17);
        line(g, 12, 7, 15.5, 7);
        line(g, 12, 17, 15.5, 17);
    }

    private static void hub(Graphics2D g) {
        // 中央ノード + 放射 4 ノード (コンポーネント/依存)
        dot(g, 12, 12, 2);
        double[][] pts = {{12, 4.5}, {12, 19.5}, {4.5, 12}, {19.5, 12}};
        for (double[] pt : pts) {
            line(g, 12, 12, pt[0], pt[1]);
            stroke(g, new Ellipse2D.Double(pt[0] - 2, pt[1] - 2, 4, 4));
        }
    }

    private static void timeline(Graphics2D g) {
        // ジグザグのライフライン上のノード (シーケンス図)
        double[][] pts = {{5, 16}, {10, 9}, {15, 15}, {20, 8}};
        for (int i = 0; i < pts.length - 1; i++) {
            line(g, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]);
        }
        for (double[] pt : pts) {
            g.setColor(g.getColor());
            stroke(g, new Ellipse2D.Double(pt[0] - 1.8, pt[1] - 1.8, 3.6, 3.6));
        }
    }

    private static void flowchart(Graphics2D g) {
        // 上の角丸 (開始) → 下のひし形 (分岐) : アクティビティ
        stroke(g, new RoundRectangle2D.Double(7.5, 3.5, 9, 4.5, 2.2, 2.2));
        line(g, 12, 8, 12, 11);
        GeneralPath diamond = path();
        diamond.moveTo(12, 11);
        diamond.lineTo(17.5, 15.5);
        diamond.lineTo(12, 20);
        diamond.lineTo(6.5, 15.5);
        diamond.closePath();
        stroke(g, diamond);
    }

    private static void callSplit(Graphics2D g) {
        // 呼び出しの分岐 (Call Graph)
        line(g, 12, 20, 12, 13);
        GeneralPath up = path();
        up.moveTo(12, 13);
        up.curveTo(12, 9, 8, 8, 6, 6);
        stroke(g, up);
        GeneralPath up2 = path();
        up2.moveTo(12, 13);
        up2.curveTo(12, 9, 16, 8, 18, 6);
        stroke(g, up2);
        // 矢じり
        GeneralPath h1 = path();
        h1.moveTo(4, 7.5);
        h1.lineTo(5.5, 5.5);
        h1.lineTo(7.8, 6.5);
        stroke(g, h1);
        GeneralPath h2 = path();
        h2.moveTo(20, 7.5);
        h2.lineTo(18.5, 5.5);
        h2.lineTo(16.2, 6.5);
        stroke(g, h2);
    }

    private static void grid(Graphics2D g) {
        // 画面レイアウト (ビュー階層): 外枠 + 内部を 4 分割した格子。
        stroke(g, new RoundRectangle2D.Double(4, 4, 16, 16, 2, 2));
        line(g, 12, 4, 12, 20);
        line(g, 4, 12, 20, 12);
    }

    private static void route(Graphics2D g) {
        // 画面遷移 (ナビゲーション): 起点ノード → 折れ線 → 終点ノード (矢じり)。
        stroke(g, new Ellipse2D.Double(3.5, 15.5, 4, 4));
        GeneralPath p = path();
        p.moveTo(5.5, 15.5);
        p.lineTo(5.5, 9);
        p.lineTo(15, 9);
        p.lineTo(15, 6);
        stroke(g, p);
        // 終点ノード (右上) と矢じり
        stroke(g, new Ellipse2D.Double(13, 4, 4, 4));
        line(g, 15, 8, 15, 12);
        GeneralPath head = path();
        head.moveTo(13, 10.5);
        head.lineTo(15, 12.5);
        head.lineTo(17, 10.5);
        stroke(g, head);
    }

    private static void cycle(Graphics2D g) {
        // 循環: 2 本の半円矢印が輪を成す (依存サイクルのメタファー)。refresh と差別化。
        java.awt.geom.Arc2D top = new java.awt.geom.Arc2D.Double(
                5, 5, 14, 14, 20, 140, java.awt.geom.Arc2D.OPEN);
        stroke(g, top);
        java.awt.geom.Arc2D bottom = new java.awt.geom.Arc2D.Double(
                5, 5, 14, 14, 200, 140, java.awt.geom.Arc2D.OPEN);
        stroke(g, bottom);
        // 上弧の終端 (左上) に下向き矢じり
        GeneralPath h1 = path();
        h1.moveTo(4.0, 9.0);
        h1.lineTo(6.0, 7.2);
        h1.lineTo(8.4, 8.4);
        stroke(g, h1);
        // 下弧の終端 (右下) に上向き矢じり
        GeneralPath h2 = path();
        h2.moveTo(20.0, 15.0);
        h2.lineTo(18.0, 16.8);
        h2.lineTo(15.6, 15.6);
        stroke(g, h2);
    }

    private static void pkg(Graphics2D g) {
        // 立体的な箱 (パッケージ)
        GeneralPath p = path();
        p.moveTo(12, 4);
        p.lineTo(20, 8);
        p.lineTo(20, 16);
        p.lineTo(12, 20);
        p.lineTo(4, 16);
        p.lineTo(4, 8);
        p.closePath();
        stroke(g, p);
        line(g, 4, 8, 12, 12);
        line(g, 20, 8, 12, 12);
        line(g, 12, 12, 12, 20);
    }

    private static void module(Graphics2D g) {
        // 4 マスのモジュール
        stroke(g, new RoundRectangle2D.Double(4, 4, 7, 7, 1.5, 1.5));
        stroke(g, new RoundRectangle2D.Double(13, 4, 7, 7, 1.5, 1.5));
        stroke(g, new RoundRectangle2D.Double(4, 13, 7, 7, 1.5, 1.5));
        stroke(g, new RoundRectangle2D.Double(13, 13, 7, 7, 1.5, 1.5));
    }

    private static void function(Graphics2D g) {
        // ƒ() を想起させる丸括弧 + 中央
        java.awt.geom.Arc2D l = new java.awt.geom.Arc2D.Double(
                5, 5, 8, 14, 110, 140, java.awt.geom.Arc2D.OPEN);
        stroke(g, l);
        java.awt.geom.Arc2D r = new java.awt.geom.Arc2D.Double(
                11, 5, 8, 14, -70, 140, java.awt.geom.Arc2D.OPEN);
        stroke(g, r);
        dot(g, 12, 12, 1.6);
    }

    private static void manifest(Graphics2D g) {
        // ドキュメント (折れ角) + 行
        GeneralPath p = path();
        p.moveTo(6, 3.5);
        p.lineTo(14.5, 3.5);
        p.lineTo(18.5, 7.5);
        p.lineTo(18.5, 20.5);
        p.lineTo(6, 20.5);
        p.closePath();
        stroke(g, p);
        GeneralPath fold = path();
        fold.moveTo(14.5, 3.5);
        fold.lineTo(14.5, 7.5);
        fold.lineTo(18.5, 7.5);
        stroke(g, fold);
        line(g, 8.5, 11, 16, 11);
        line(g, 8.5, 14, 16, 14);
        line(g, 8.5, 17, 13, 17);
    }

    private static void widgets(Graphics2D g) {
        // 1 つを回転させた 4 部品 (コンポーネント群)
        stroke(g, new RoundRectangle2D.Double(4, 11, 7, 7, 1.2, 1.2));
        stroke(g, new RoundRectangle2D.Double(13, 11, 7, 7, 1.2, 1.2));
        stroke(g, new RoundRectangle2D.Double(4, 2.5, 7, 7, 1.2, 1.2));
        GeneralPath dia = path();
        dia.moveTo(16.5, 2);
        dia.lineTo(21, 6.5);
        dia.lineTo(16.5, 11);
        dia.lineTo(12, 6.5);
        dia.closePath();
        stroke(g, dia);
    }

    private static void shield(Graphics2D g) {
        // 盾 (権限)
        GeneralPath p = path();
        p.moveTo(12, 3.5);
        p.lineTo(19, 6.5);
        p.lineTo(19, 12);
        p.curveTo(19, 17, 15.5, 19.5, 12, 20.8);
        p.curveTo(8.5, 19.5, 5, 17, 5, 12);
        p.lineTo(5, 6.5);
        p.closePath();
        stroke(g, p);
        // チェック
        GeneralPath check = path();
        check.moveTo(9, 11.8);
        check.lineTo(11.2, 14);
        check.lineTo(15, 9.5);
        stroke(g, check);
    }

    private static void star(Graphics2D g) {
        GeneralPath p = path();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + i * 36);
            double r = (i % 2 == 0) ? 8.5 : 3.6;
            double px = 12 + r * Math.cos(a);
            double py = 12 + r * Math.sin(a);
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        stroke(g, p);
    }

    private static void bolt(Graphics2D g) {
        // 稲妻 (Build/Soong/Ninja アクション)
        GeneralPath p = path();
        p.moveTo(13, 3);
        p.lineTo(6, 13);
        p.lineTo(11, 13);
        p.lineTo(10, 21);
        p.lineTo(18, 10);
        p.lineTo(12.5, 10);
        p.closePath();
        stroke(g, p);
    }

    private static void extension(Graphics2D g) {
        // パズルピース (拡張 / feature)
        GeneralPath p = path();
        p.moveTo(5, 8);
        p.lineTo(9, 8);
        p.curveTo(9, 5.5, 15, 5.5, 15, 8);
        p.lineTo(19, 8);
        p.lineTo(19, 12);
        p.curveTo(16.5, 12, 16.5, 16, 19, 16);
        p.lineTo(19, 19);
        p.lineTo(5, 19);
        p.closePath();
        stroke(g, p);
    }

    private static void layers(Graphics2D g) {
        // 重なり (intermediates / resources)
        GeneralPath top = path();
        top.moveTo(12, 4);
        top.lineTo(20, 8.5);
        top.lineTo(12, 13);
        top.lineTo(4, 8.5);
        top.closePath();
        stroke(g, top);
        line(g, 4, 12.5, 12, 17);
        line(g, 20, 12.5, 12, 17);
        line(g, 4, 16, 12, 20.5);
        line(g, 20, 16, 12, 20.5);
    }

    private static void arrowBack(Graphics2D g) {
        line(g, 5, 12, 19, 12);
        line(g, 5, 12, 11, 6);
        line(g, 5, 12, 11, 18);
    }

    private static void arrowForward(Graphics2D g) {
        line(g, 5, 12, 19, 12);
        line(g, 19, 12, 13, 6);
        line(g, 19, 12, 13, 18);
    }

    private static void chevronUp(Graphics2D g) {
        line(g, 6, 15, 12, 9);
        line(g, 12, 9, 18, 15);
    }

    private static void chevronDown(Graphics2D g) {
        line(g, 6, 9, 12, 15);
        line(g, 12, 15, 18, 9);
    }
}
