// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.CellRendererPane;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 付箋 ({@link DiagramNote}) のパネル描画を担うレンダラ。{@link DiagramNotesLayer} から
 * 描画責務を切り出したもの。
 *
 * <p>位置・サイズは図 (SVG) 座標で保持され、{@code zoom} 倍して描く。本文は
 * {@link MarkdownRenderer} で HTML 化し、再利用する {@link JEditorPane} を {@code zoom}
 * スケール下で描画してベクタ品質で出す。ELEMENT アンカーの追従先矩形は
 * {@link #setElementResolver} で解決する。</p>
 */
final class NoteRenderer {

    /** リサイズハンドルの一辺 (パネル px)。 */
    private static final int HANDLE = 12;
    /** これ未満 (パネル px) では本文 HTML を描かず色付き矩形だけ出す (極小ズーム対策)。 */
    private static final int MIN_BODY_W = 48;
    private static final int MIN_BODY_H = 30;
    private static final Color BORDER = new Color(0xC9A227);
    private static final Color SELECTED_BORDER = new Color(0x1E78E6);
    private static final Color ORPHAN_BORDER = new Color(0xD9534F);
    private static final Color LOCK_GLYPH = new Color(0x5A5A5A);
    private static final Color SHADOW = new Color(0, 0, 0, 40);
    /** ELEMENT 付箋→対象要素を結ぶリーダー線の色。 */
    private static final Color LEADER = new Color(0xC9A227);
    /** 付箋間コネクタの色。 */
    private static final Color CONNECTOR = new Color(0x6B7280);
    private static final BasicStroke CONNECTOR_STROKE = new BasicStroke(1.4f);
    private static final BasicStroke BORDER_STROKE = new BasicStroke(1f);
    private static final BasicStroke SELECTED_STROKE = new BasicStroke(2f);
    private static final BasicStroke LOCK_STROKE = new BasicStroke(1.4f);
    private static final BasicStroke LEADER_STROKE = new BasicStroke(1.2f);
    /** タグ表示 (付箋下端のストリップ)。 */
    private static final Font TAG_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static Color tagBg() {
        return EditorColors.isDark()
                ? new Color(0, 0, 0, 160) : new Color(255, 255, 255, 190);
    }

    private static Color tagFg() {
        return EditorColors.isDark()
                ? new Color(0x6EA5D4) : new Color(0x3A6EA5);
    }
    /** 孤児 (追従先が消えた ELEMENT 付箋) を示す破線枠。 */
    private static final BasicStroke ORPHAN_STROKE = new BasicStroke(2f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f);

    private final CellRendererPane rendererPane = new CellRendererPane();
    private final JEditorPane htmlView = new JEditorPane();
    private java.util.function.Function<String, double[]> elementResolver;

    /**
     * Markdown→HTML 変換結果のキャッシュ。本文文字列をキーに変換済み HTML を保持し、
     * 再描画 (パン/ズーム/操作) のたびの再パースを避ける。上限付き LRU (アクセス順)。
     */
    private final java.util.Map<String, String> htmlCache =
            new java.util.LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String> e) {
                    return size() > 256;
                }
            };

    NoteRenderer() {
        htmlView.setEditable(false);
        htmlView.setContentType("text/html");
        htmlView.setOpaque(false);
        htmlView.setBorder(null);
    }

    void setElementResolver(java.util.function.Function<String, double[]> resolver) {
        this.elementResolver = resolver;
    }

    private double[] anchorRect(DiagramNote n) {
        if (n.getAnchor() == DiagramNote.Anchor.ELEMENT && elementResolver != null) {
            return elementResolver.apply(n.getTargetRef());
        }
        return null;
    }

    private double effX(DiagramNote n) {
        double[] r = anchorRect(n);
        return (r == null ? 0 : r[0]) + n.getX();
    }

    private double effY(DiagramNote n) {
        double[] r = anchorRect(n);
        return (r == null ? 0 : r[1]) + n.getY();
    }

    /** すべての付箋・コネクタ・リーダー線を描く。{@code g2} はパネル座標前提。 */
    void paint(Graphics2D g2, double zoom, List<DiagramNote> notes, Set<String> selected,
               List<DiagramConnector> connectors) {
        if (notes.isEmpty() || zoom <= 0) {
            return;
        }
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 先にリーダー線・コネクタを描いて付箋の下に潜らせる。
        for (DiagramNote n : notes) {
            if (n.getAnchor() == DiagramNote.Anchor.ELEMENT) {
                paintLeader(g2, zoom, n);
            }
        }
        paintConnectors(g2, zoom, notes, connectors);
        for (DiagramNote n : notes) {
            paintNote(g2, n, zoom, selected);
        }
    }

    /** 付箋間コネクタを矢印付き線で描く。端点は各付箋の枠で切り詰める。 */
    private void paintConnectors(Graphics2D g2, double zoom, List<DiagramNote> notes,
                                 List<DiagramConnector> connectors) {
        if (connectors == null || connectors.isEmpty()) {
            return;
        }
        Map<String, DiagramNote> byId = new HashMap<>();
        for (DiagramNote n : notes) {
            byId.put(n.getId(), n);
        }
        g2.setColor(CONNECTOR);
        g2.setStroke(CONNECTOR_STROKE);
        for (DiagramConnector c : connectors) {
            DiagramNote a = byId.get(c.getFromId());
            DiagramNote b = byId.get(c.getToId());
            if (a == null || b == null) {
                continue;
            }
            double ax = effX(a);
            double ay = effY(a);
            double bx = effX(b);
            double by = effY(b);
            double[] p1 = borderPoint(ax, ay, a.getWidth(), a.getHeight(),
                    bx + b.getWidth() / 2, by + b.getHeight() / 2);
            double[] p2 = borderPoint(bx, by, b.getWidth(), b.getHeight(),
                    ax + a.getWidth() / 2, ay + a.getHeight() / 2);
            int x1 = (int) Math.round(p1[0] * zoom);
            int y1 = (int) Math.round(p1[1] * zoom);
            int x2 = (int) Math.round(p2[0] * zoom);
            int y2 = (int) Math.round(p2[1] * zoom);
            g2.drawLine(x1, y1, x2, y2);
            drawArrowHead(g2, x1, y1, x2, y2);
        }
    }

    /** {@code (x2,y2)} に {@code (x1,y1)} から向かう矢じりを塗る。 */
    private static void drawArrowHead(Graphics2D g2, int x1, int y1, int x2, int y2) {
        double ang = Math.atan2(y2 - y1, x2 - x1);
        double spread = Math.toRadians(22);
        int len = 9;
        int ax = (int) (x2 - len * Math.cos(ang - spread));
        int ay = (int) (y2 - len * Math.sin(ang - spread));
        int bx = (int) (x2 - len * Math.cos(ang + spread));
        int by = (int) (y2 - len * Math.sin(ang + spread));
        g2.fill(new Polygon(new int[] {x2, ax, bx}, new int[] {y2, ay, by}, 3));
    }

    /**
     * ELEMENT 付箋とその対象要素を結ぶリーダー線を描く。両矩形の中心を結んだ線分を
     * それぞれの枠で切り詰め、対象側の端に小さな丸を打つ。孤児 (追従先なし) は描かない。
     */
    private void paintLeader(Graphics2D g2, double zoom, DiagramNote n) {
        double[] er = anchorRect(n);
        if (er == null) {
            return;
        }
        double nx = effX(n);
        double ny = effY(n);
        double[] from = borderPoint(nx, ny, n.getWidth(), n.getHeight(),
                er[0] + er[2] / 2, er[1] + er[3] / 2);
        double[] to = borderPoint(er[0], er[1], er[2], er[3],
                nx + n.getWidth() / 2, ny + n.getHeight() / 2);
        int x1 = (int) Math.round(from[0] * zoom);
        int y1 = (int) Math.round(from[1] * zoom);
        int x2 = (int) Math.round(to[0] * zoom);
        int y2 = (int) Math.round(to[1] * zoom);
        g2.setColor(LEADER);
        g2.setStroke(LEADER_STROKE);
        g2.drawLine(x1, y1, x2, y2);
        g2.fillOval(x2 - 3, y2 - 3, 6, 6); // 対象要素側の端点
    }

    /** 矩形中心から {@code (tx,ty)} に向かう半直線が矩形枠と交わる点。 */
    private static double[] borderPoint(double x, double y, double w, double h,
                                        double tx, double ty) {
        double cx = x + w / 2;
        double cy = y + h / 2;
        double dx = tx - cx;
        double dy = ty - cy;
        if (dx == 0 && dy == 0) {
            return new double[] {cx, cy};
        }
        double sx = dx != 0 ? (w / 2) / Math.abs(dx) : Double.MAX_VALUE;
        double sy = dy != 0 ? (h / 2) / Math.abs(dy) : Double.MAX_VALUE;
        double s = Math.min(sx, sy);
        return new double[] {cx + dx * s, cy + dy * s};
    }

    /**
     * 付箋本文を現在の幅で表示するのに必要な高さ (図座標)。高さオートフィット用。
     * 上下パディングを含む。空本文では {@link DiagramNote#getHeight} を返す。
     */
    double contentHeight(DiagramNote n) {
        int pad = 6;
        int innerW = (int) (n.getWidth() - pad * 2);
        if (innerW <= 0 || n.getText() == null || n.getText().trim().isEmpty()) {
            return n.getHeight();
        }
        htmlView.setText(html(n.getText()));
        htmlView.setSize(innerW, Short.MAX_VALUE);
        return htmlView.getPreferredSize().height + pad * 2;
    }

    private void paintNote(Graphics2D g2, DiagramNote n, double zoom, Set<String> selectedIds) {
        int px = (int) Math.round(effX(n) * zoom);
        int py = (int) Math.round(effY(n) * zoom);
        int pw = (int) Math.round(n.getWidth() * zoom);
        int ph = (int) Math.round(n.getHeight() * zoom);
        if (pw < 2 || ph < 2) {
            return;
        }
        // 影 + 付箋本体 (パネル座標で角丸矩形)
        g2.setColor(SHADOW);
        g2.fillRoundRect(px + 3, py + 3, pw, ph, 10, 10);
        g2.setColor(parseColor(n.getColor()));
        g2.fillRoundRect(px, py, pw, ph, 10, 10);
        boolean selected = selectedIds.contains(n.getId());
        // ELEMENT アンカーだが対象要素が見つからない (孤児) 付箋は破線枠で知らせる。
        boolean orphan = n.getAnchor() == DiagramNote.Anchor.ELEMENT && anchorRect(n) == null;
        g2.setColor(orphan ? ORPHAN_BORDER : (selected ? SELECTED_BORDER : BORDER));
        g2.setStroke(orphan ? ORPHAN_STROKE : (selected ? SELECTED_STROKE : BORDER_STROKE));
        g2.drawRoundRect(px, py, pw, ph, 10, 10);

        // 極小 (ズームアウト時) では本文を省き、付箋の存在だけ色で示す
        if (pw < MIN_BODY_W || ph < MIN_BODY_H) {
            return;
        }

        // 本文 (HTML) を zoom スケール下で描画
        boolean overflow = false;
        Graphics2D ng = (Graphics2D) g2.create();
        try {
            ng.translate(px, py);
            ng.scale(zoom, zoom);
            int pad = 6;
            int innerW = (int) (n.getWidth() - pad * 2);
            int innerH = (int) (n.getHeight() - pad * 2);
            if (innerW > 0 && innerH > 0) {
                htmlView.setText(html(n.getText()));
                // あふれ検出: 与えた幅での必要高さが本文領域を超えるか。
                htmlView.setSize(innerW, Short.MAX_VALUE);
                overflow = htmlView.getPreferredSize().height > innerH;
                SwingUtilities.paintComponent(ng, htmlView, rendererPane, pad, pad, innerW, innerH);
            }
        } finally {
            ng.dispose();
        }
        // あふれているときは下端にフェードを描き「続きがある」ことを示す。
        if (overflow) {
            int fadeH = Math.min(ph / 4, 14);
            Color base = parseColor(n.getColor());
            g2.setPaint(new GradientPaint(
                    0, py + ph - fadeH, new Color(base.getRed(), base.getGreen(), base.getBlue(), 0),
                    0, py + ph - 2, base));
            g2.fillRoundRect(px + 1, py + ph - fadeH, pw - 2, fadeH - 1, 8, 8);
        }
        // タグがあれば下端にストリップで表示する。
        paintTags(g2, n, px, py, pw, ph);
        // ロック中は右上に錠前アイコンを描く (移動・リサイズ不可の目印)。
        if (n.isLocked()) {
            drawLock(g2, px + pw - 15, py + 4);
        }
        // リサイズハンドルは「単一選択かつ非ロック」のときだけ出す。
        if (selected && selectedIds.size() == 1 && !n.isLocked()) {
            int handle = Math.max(7, Math.min(HANDLE, Math.min(pw, ph) / 4));
            g2.setColor(SELECTED_BORDER);
            g2.fillRect(px + pw - handle, py + ph - handle, handle, handle);
        }
    }

    /** 付箋下端にタグを {@code #tag} 形式のストリップで描く。タグが無ければ何もしない。 */
    private void paintTags(Graphics2D g2, DiagramNote n, int px, int py, int pw, int ph) {
        List<String> tags = n.getTags();
        if (tags.isEmpty()) {
            return;
        }
        g2.setFont(TAG_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int stripH = fm.getHeight();
        if (stripH + 4 > ph) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append('#').append(t);
        }
        int sy = py + ph - stripH;
        g2.setColor(tagBg());
        g2.fillRect(px + 1, sy, pw - 2, stripH);
        Shape clip = g2.getClip();
        g2.clipRect(px + 4, sy, pw - 8, stripH);
        g2.setColor(tagFg());
        g2.drawString(sb.toString(), px + 4, py + ph - fm.getDescent() - 1);
        g2.setClip(clip);
    }

    /** 右上に描く小さな錠前アイコン (ロック中の目印)。{@code x,y} は左上。 */
    private static void drawLock(Graphics2D g2, int x, int y) {
        g2.setColor(LOCK_GLYPH);
        g2.setStroke(LOCK_STROKE);
        g2.drawArc(x + 2, y, 6, 7, 0, 180); // シャックル (上の弧)
        g2.fillRoundRect(x, y + 4, 10, 7, 2, 2); // 本体
    }

    private String html(String md) {
        // プレースホルダ (空本文) は locale 依存で安価なためキャッシュしない。
        if (md == null || md.trim().isEmpty()) {
            String phColor = EditorColors.isDark() ? "#AAA" : "#666";
            return MarkdownRenderer.wrapDocument(
                    "<span style=\"color:" + phColor + ";\">"
                            + Messages.get("note.placeholder") + "</span>", 0, 11);
        }
        String cached = htmlCache.get(md);
        if (cached != null) {
            return cached;
        }
        String full = MarkdownRenderer.wrapDocument(MarkdownRenderer.toHtml(md), 0, 11);
        htmlCache.put(md, full);
        return full;
    }

    static Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException ex) {
            return Color.decode(DiagramNote.DEFAULT_COLOR);
        }
    }

    /**
     * 付箋色を常に {@code #RRGGBB} 形式へ正規化する。画面描画は {@link Color#decode} を使う
     * のに対し SVG エクスポートは色文字列を CSS として解釈するため、{@code #FFF} や 10 進値
     * などで画面 (例: {@code Color.decode("#FFF")} → (0,15,255)) と SVG ({@code fill="#FFF"}
     * → 白) が食い違っていた。両者を {@link #parseColor} の解釈へ揃えることで一致させる。
     */
    static String normalizeColorHex(String hex) {
        return String.format("#%06X", parseColor(hex).getRGB() & 0xFFFFFF);
    }
}
