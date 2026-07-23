// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * ER 図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>エンティティを表 (タイトル + 主キー列を区切り線上部に {@code *} 付きで + 一般列を下部に)
 * として描き、リレーションを crow's-foot (IE) 記法の端点で結ぶ。ドラッグ移動・
 * ダブルクリック編集・右クリックメニュー・リレーションの 2 クリック追加 (自己関連含む) を
 * 受け付ける。モデル変更は {@link Listener#modelEdited()} で通知し、テキスト再生成は
 * 呼び出し側 ({@link SketchPane}) が行う。</p>
 */
final class ErSketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        void modelEdited();

        void editEntityRequested(ErSketchModel.Entity e);

        default void relationModeCancelled() { }

        default void editRelationRequested(ErSketchModel.Relation r) { }
    }

    private static final int PAD_X = 12;
    private static final int TITLE_H = 22;
    private static final int LINE_H = 16;
    private static final int DIV_H = 6;
    private static final int MIN_W = 120;
    private static final int GRID = 8;

    private static final Color FILL = new Color(0xFDF6E3);
    private static final Color TITLE_FILL = new Color(0xF3E4B3);
    private static final Color LINE = new Color(0x555555);
    private static final Color EDGE = new Color(0x37474F);
    private static final Color SELECTED = new Color(0x1565C0);

    private ErSketchModel model = new ErSketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;
    /** ズーム (Ctrl+ホイール) と中ボタンパン。マウス座標は toModel で逆変換して使う。 */
    private final SketchViewport view = new SketchViewport(this);

    private ErSketchModel.Entity selected;
    /** リレーション追加モード (true = 追加待ち, false = 選択/移動)。 */
    private boolean relationMode;
    private ErSketchModel.Entity relationSource;
    private boolean snapToGrid = true;
    private Point dragOffset;
    private boolean draggedSinceMousePress;

    ErSketchCanvas(Listener listener) {
        this.listener = listener;
        setBackground(Color.WHITE);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handlePress(e);
            }

            @Override public void mouseDragged(MouseEvent e) {
                handleDrag(e);
            }

            @Override public void mouseReleased(MouseEvent e) {
                handleRelease(e);
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !editable || relationMode
                        || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (selected != null) {
                    listener.editEntityRequested(selected);
                    return;
                }
                ErSketchModel.Relation rel = relationAt(view.toModel(e.getPoint()));
                if (rel != null) {
                    listener.editRelationRequested(rel);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                handleKey(e);
            }
        });
    }

    private void handleKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE && editable && selected != null
                && !relationMode) {
            model.removeEntity(selected);
            selected = null;
            listener.modelEdited();
            repaint();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && relationMode) {
            setRelationMode(false);
            listener.relationModeCancelled();
        } else if (editable && selected != null && !relationMode) {
            int[] d = SketchNudge.deltaFor(e.getKeyCode(), e.isShiftDown(), GRID);
            if (d != null) {
                nudgeSelected(d[0], d[1]);
                e.consume();
            }
        }
    }

    /** 選択エンティティを相対移動する (矢印キーの微調整。Shift でグリッド単位)。 */
    void nudgeSelected(int dx, int dy) {
        if (!editable || selected == null) {
            return;
        }
        selected.moveTo(Math.max(0, selected.getX() + dx),
                Math.max(0, selected.getY() + dy));
        listener.modelEdited();
        revalidate();
        repaint();
    }

    void setModel(ErSketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        this.relationSource = null;
        revalidate();
        repaint();
    }

    ErSketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    void setSnapToGrid(boolean on) {
        this.snapToGrid = on;
    }

    /** リレーション追加モードを切り替える (false で選択/移動モードへ戻す)。 */
    void setRelationMode(boolean on) {
        this.relationMode = on;
        this.relationSource = null;
        this.selected = null;
        repaint();
    }

    /** 新しいエンティティを追加する。 */
    void addEntity(Point at) {
        if (!editable) {
            return;
        }
        int n = model.getEntities().size();
        int x = at != null ? at.x : 40 + (n % 6) * 30;
        int y = at != null ? at.y : 40 + (n % 6) * 28;
        ErSketchModel.Entity e = new ErSketchModel.Entity(
                model.uniqueAlias("Entity"), null, x, y);
        e.getColumns().add(new ErSketchModel.Column(true, "id", "int"));
        model.getEntities().add(e);
        listener.modelEdited();
        revalidate();
        repaint();
    }

    private static int snap(int v) {
        return Math.round(v / (float) GRID) * GRID;
    }

    // -------------------------------------------------------------------------
    // マウス操作
    // -------------------------------------------------------------------------

    private void handlePress(MouseEvent e) {
        if (!editable) {
            return;
        }
        // 中ボタンはパン (SketchViewport) 専用。選択/ドラッグとして扱わない。
        if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
            return;
        }
        Point mp = view.toModel(e.getPoint());
        ErSketchModel.Entity hit = entityAt(mp);
        if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
            selected = hit;
            repaint();
            if (e.isPopupTrigger()) {
                showPopup(e, hit);
            }
            return;
        }
        if (relationMode) {
            handleRelationClick(hit);
            return;
        }
        selected = hit;
        draggedSinceMousePress = false;
        if (hit != null) {
            dragOffset = new Point(mp.x - hit.getX(), mp.y - hit.getY());
        }
        repaint();
    }

    /** リレーション追加モード: 1 クリック目で始点、2 クリック目で終点を確定する (自己関連可)。 */
    private void handleRelationClick(ErSketchModel.Entity hit) {
        if (hit == null) {
            relationSource = null;
            repaint();
            return;
        }
        if (relationSource == null) {
            relationSource = hit;
        } else {
            // 既定は「1 対 多」(||--o{)。カーディナリティ/ラベルはダブルクリック編集で調整する。
            model.getRelations().add(new ErSketchModel.Relation(
                    relationSource.getAlias(), ErSketchModel.Cardinality.EXACTLY_ONE,
                    ErSketchModel.Cardinality.ZERO_OR_MANY, hit.getAlias(), null));
            relationSource = null;
            listener.modelEdited();
        }
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        if (!editable || relationMode || selected == null || dragOffset == null) {
            return;
        }
        Point mp = view.toModel(e.getPoint());
        selected.moveTo(Math.max(0, mp.x - dragOffset.x),
                Math.max(0, mp.y - dragOffset.y));
        draggedSinceMousePress = true;
        revalidate();
        repaint();
    }

    private void handleRelease(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopup(e, entityAt(view.toModel(e.getPoint())));
            return;
        }
        if (draggedSinceMousePress) {
            draggedSinceMousePress = false;
            if (snapToGrid && selected != null) {
                selected.moveTo(snap(selected.getX()), snap(selected.getY()));
                revalidate();
                repaint();
            }
            listener.modelEdited();
        }
        dragOffset = null;
    }

    private void showPopup(MouseEvent e, ErSketchModel.Entity hit) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (hit != null) {
            addItem(menu, "sketch.er.menu.edit", () -> listener.editEntityRequested(hit));
            addItem(menu, "sketch.er.menu.delete", () -> {
                model.removeEntity(hit);
                selected = null;
                listener.modelEdited();
                repaint();
            });
            addRelationDeleteMenu(menu, hit);
        } else {
            final Point at = view.toModel(e.getPoint());
            addItem(menu, "sketch.er.menu.addEntityHere", () -> addEntity(at));
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void addItem(JPopupMenu menu, String key, Runnable action) {
        JMenuItem item = new JMenuItem(Messages.get(key));
        item.addActionListener(a -> action.run());
        menu.add(item);
    }

    private void addRelationDeleteMenu(JPopupMenu menu, ErSketchModel.Entity hit) {
        List<ErSketchModel.Relation> touching = model.getRelations().stream()
                .filter(r -> r.touches(hit.getAlias())).toList();
        if (touching.isEmpty()) {
            return;
        }
        JMenu sub = new JMenu(Messages.get("sketch.er.menu.deleteRelation"));
        for (ErSketchModel.Relation r : touching) {
            JMenuItem item = new JMenuItem(
                    r.getLeft() + " " + r.arrow() + " " + r.getRight());
            item.addActionListener(a -> {
                model.getRelations().remove(r);
                listener.modelEdited();
                repaint();
            });
            sub.add(item);
        }
        menu.add(sub);
    }

    private ErSketchModel.Entity entityAt(Point p) {
        List<ErSketchModel.Entity> es = model.getEntities();
        for (int i = es.size() - 1; i >= 0; i--) {
            if (boundsOf(es.get(i)).contains(p)) {
                return es.get(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    /** 列 1 行分の表示テキスト (主キーは先頭に {@code *})。 */
    private static String columnText(ErSketchModel.Column c) {
        StringBuilder sb = new StringBuilder();
        if (c.isPrimaryKey()) {
            sb.append("* ");
        }
        sb.append(c.getName());
        if (!c.getType().isEmpty()) {
            sb.append(" : ").append(c.getType());
        }
        return sb.toString();
    }

    private static int pkCount(ErSketchModel.Entity e) {
        int n = 0;
        for (ErSketchModel.Column c : e.getColumns()) {
            if (c.isPrimaryKey()) {
                n++;
            }
        }
        return n;
    }

    /** エンティティ表の矩形 (フォントメトリクスから内容に合わせて算出)。 */
    Rectangle boundsOf(ErSketchModel.Entity e) {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont()
                : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int w = fm.stringWidth(e.displayText()) + 2 * PAD_X + 16;
        for (ErSketchModel.Column c : e.getColumns()) {
            w = Math.max(w, fm.stringWidth(columnText(c)) + 2 * PAD_X);
        }
        w = Math.max(w, MIN_W);
        int pk = pkCount(e);
        int other = e.getColumns().size() - pk;
        boolean divider = pk > 0 && other > 0;
        int rows = Math.max(1, e.getColumns().size());
        int h = TITLE_H + 4 + rows * LINE_H + (divider ? DIV_H : 0) + 6;
        return new Rectangle(e.getX(), e.getY(), w, h);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 300;
        for (ErSketchModel.Entity e : model.getEntities()) {
            Rectangle r = boundsOf(e);
            w = Math.max(w, r.x + r.width + 80);
            h = Math.max(h, r.y + r.height + 80);
        }
        return view.scaled(new Dimension(w, h));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            view.applyTransform(g2);
            for (ErSketchModel.Relation r : model.getRelations()) {
                paintRelation(g2, r);
            }
            for (ErSketchModel.Entity e : model.getEntities()) {
                paintEntity(g2, e);
            }
        } finally {
            g2.dispose();
        }
        Graphics2D overlay = (Graphics2D) g.create();
        try {
            overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (!editable) {
                SketchBanner.paint(overlay, this, unsupported);
            } else if (relationMode) {
                overlay.setColor(SELECTED);
                overlay.drawString(Messages.get(relationSource == null
                        ? "sketch.er.hint.pickSource" : "sketch.er.hint.pickTarget"), 8, 14);
            }
        } finally {
            overlay.dispose();
        }
    }

    private void paintEntity(Graphics2D g2, ErSketchModel.Entity e) {
        Rectangle r = boundsOf(e);
        boolean sel = e == selected || e == relationSource;
        g2.setColor(FILL);
        g2.fillRect(r.x, r.y, r.width, r.height);
        g2.setColor(TITLE_FILL);
        g2.fillRect(r.x, r.y, r.width, TITLE_H);
        g2.setColor(sel ? SELECTED : LINE);
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        g2.drawRect(r.x, r.y, r.width, r.height);
        g2.setStroke(new BasicStroke(1f));

        Font base = g2.getFont();
        g2.setFont(base.deriveFont(Font.BOLD));
        int tw = g2.getFontMetrics().stringWidth(e.displayText());
        g2.setColor(new Color(0x1A1A1A));
        g2.drawString(e.displayText(), r.x + (r.width - tw) / 2, r.y + TITLE_H - 7);
        g2.setFont(base);

        int sep = r.y + TITLE_H;
        g2.setColor(LINE);
        g2.drawLine(r.x, sep, r.x + r.width, sep);
        int line = sep + LINE_H;
        int pk = pkCount(e);
        int other = e.getColumns().size() - pk;
        boolean divider = pk > 0 && other > 0;
        for (ErSketchModel.Column c : e.getColumns()) {
            if (c.isPrimaryKey()) {
                g2.drawString(columnText(c), r.x + PAD_X, line);
                line += LINE_H;
            }
        }
        if (divider) {
            g2.drawLine(r.x, line - LINE_H + DIV_H, r.x + r.width, line - LINE_H + DIV_H);
            line += DIV_H;
        }
        for (ErSketchModel.Column c : e.getColumns()) {
            if (!c.isPrimaryKey()) {
                g2.drawString(columnText(c), r.x + PAD_X, line);
                line += LINE_H;
            }
        }
    }

    private void paintRelation(Graphics2D g2, ErSketchModel.Relation rel) {
        ErSketchModel.Entity left = model.findEntity(rel.getLeft());
        ErSketchModel.Entity right = model.findEntity(rel.getRight());
        if (left == null || right == null) {
            return;
        }
        if (left == right) {
            paintSelfRelation(g2, boundsOf(left), rel);
            return;
        }
        Rectangle rl = boundsOf(left);
        Rectangle rr = boundsOf(right);
        Point pl = edgePoint(rl, center(rr));
        Point pr = edgePoint(rr, center(rl));
        Stroke old = g2.getStroke();
        g2.setColor(EDGE);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(pl.x, pl.y, pr.x, pr.y);
        paintEnd(g2, pl, pr, rel.getLeftCard());
        paintEnd(g2, pr, pl, rel.getRightCard());
        g2.setStroke(old);
        if (rel.getLabel() != null && !rel.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(rel.getLabel(), (pl.x + pr.x) / 2 + 4, (pl.y + pr.y) / 2 - 4);
        }
    }

    /** 自己関連 (始点=終点) をエンティティ右上のループ線として描く。 */
    private void paintSelfRelation(Graphics2D g2, Rectangle r, ErSketchModel.Relation rel) {
        int exitX = r.x + r.width - 24;
        int topY = r.y - 20;
        int rightX = r.x + r.width + 20;
        Point start = new Point(exitX, r.y);
        Point ret = new Point(r.x + r.width, r.y + 16);
        Stroke old = g2.getStroke();
        g2.setColor(EDGE);
        g2.setStroke(new BasicStroke(1.2f));
        Path2D loop = new Path2D.Double();
        loop.moveTo(start.x, start.y);
        loop.lineTo(exitX, topY);
        loop.lineTo(rightX, topY);
        loop.lineTo(rightX, ret.y);
        loop.lineTo(ret.x, ret.y);
        g2.draw(loop);
        paintEnd(g2, start, new Point(exitX, topY), rel.getLeftCard());
        paintEnd(g2, ret, new Point(rightX, ret.y), rel.getRightCard());
        g2.setStroke(old);
        if (rel.getLabel() != null && !rel.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(rel.getLabel(), rightX + 4, topY + 4);
        }
    }

    // -------------------------------------------------------------------------
    // crow's-foot 端点
    // -------------------------------------------------------------------------

    /**
     * エンティティ端 {@code tip} に、線上の相手方向 {@code other} を基準として
     * カーディナリティ記号を描く。棒 = 「1」、丸 = 「0 可」、三又 (crow's foot) = 「多」。
     */
    private void paintEnd(Graphics2D g2, Point tip, Point other,
                          ErSketchModel.Cardinality card) {
        double dx = other.x - tip.x;
        double dy = other.y - tip.y;
        double d = Math.max(1e-6, Math.hypot(dx, dy));
        double ux = dx / d;
        double uy = dy / d;
        switch (card) {
            case EXACTLY_ONE:
                tick(g2, tip, ux, uy, 8, 5);
                tick(g2, tip, ux, uy, 13, 5);
                break;
            case ZERO_OR_ONE:
                circleGlyph(g2, tip, ux, uy, 8, 4);
                tick(g2, tip, ux, uy, 15, 5);
                break;
            case ZERO_OR_MANY:
                foot(g2, tip, ux, uy, 13, 7);
                circleGlyph(g2, tip, ux, uy, 20, 4);
                break;
            case ONE_OR_MANY:
                foot(g2, tip, ux, uy, 13, 7);
                tick(g2, tip, ux, uy, 18, 5);
                break;
            default:
                break;
        }
    }

    /** 線に垂直な短い棒を {@code tip} から距離 {@code dist} の位置に描く (「1」)。 */
    private static void tick(Graphics2D g2, Point tip, double ux, double uy,
                             double dist, double half) {
        double cx = tip.x + ux * dist;
        double cy = tip.y + uy * dist;
        double px = -uy;
        double py = ux;
        g2.drawLine((int) Math.round(cx + px * half), (int) Math.round(cy + py * half),
                (int) Math.round(cx - px * half), (int) Math.round(cy - py * half));
    }

    /** 三又の crow's foot を描く (「多」)。頂点は線上、三本の足がエンティティ端 {@code tip} へ。 */
    private static void foot(Graphics2D g2, Point tip, double ux, double uy,
                             double len, double half) {
        double ax = tip.x + ux * len;
        double ay = tip.y + uy * len;
        double px = -uy;
        double py = ux;
        int apexX = (int) Math.round(ax);
        int apexY = (int) Math.round(ay);
        g2.drawLine(apexX, apexY, tip.x, tip.y);
        g2.drawLine(apexX, apexY, (int) Math.round(tip.x + px * half),
                (int) Math.round(tip.y + py * half));
        g2.drawLine(apexX, apexY, (int) Math.round(tip.x - px * half),
                (int) Math.round(tip.y - py * half));
    }

    /** 小さな丸を {@code tip} から距離 {@code dist} の位置に描く (「0 可」)。 */
    private static void circleGlyph(Graphics2D g2, Point tip, double ux, double uy,
                                    double dist, int radius) {
        int cx = (int) Math.round(tip.x + ux * dist);
        int cy = (int) Math.round(tip.y + uy * dist);
        g2.drawOval(cx - radius, cy - radius, 2 * radius, 2 * radius);
    }

    // -------------------------------------------------------------------------
    // ヒットテスト / 幾何
    // -------------------------------------------------------------------------

    private ErSketchModel.Relation relationAt(Point p) {
        ErSketchModel.Relation best = null;
        double bestD = 7.0;
        for (ErSketchModel.Relation rel : model.getRelations()) {
            ErSketchModel.Entity left = model.findEntity(rel.getLeft());
            ErSketchModel.Entity right = model.findEntity(rel.getRight());
            if (left == null || right == null || left == right) {
                continue;
            }
            Point pl = edgePoint(boundsOf(left), center(boundsOf(right)));
            Point pr = edgePoint(boundsOf(right), center(boundsOf(left)));
            double dd = pointToSegment(p.x, p.y, pl.x, pl.y, pr.x, pr.y);
            if (dd < bestD) {
                bestD = dd;
                best = rel;
            }
        }
        return best;
    }

    private static Point center(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private static double pointToSegment(double px, double py,
                                         double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        double u = len2 == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / len2;
        u = Math.max(0, Math.min(1, u));
        return Math.hypot(px - (x1 + u * dx), py - (y1 + u * dy));
    }

    private static Point edgePoint(Rectangle r, Point toward) {
        Point c = center(r);
        double dx = toward.x - c.x;
        double dy = toward.y - c.y;
        if (dx == 0 && dy == 0) {
            return c;
        }
        double scaleX = dx != 0 ? (r.width / 2.0) / Math.abs(dx) : Double.MAX_VALUE;
        double scaleY = dy != 0 ? (r.height / 2.0) / Math.abs(dy) : Double.MAX_VALUE;
        double t = Math.min(scaleX, scaleY);
        return new Point((int) Math.round(c.x + dx * t), (int) Math.round(c.y + dy * t));
    }

    // -------------------------------------------------------------------------
    // テスト用シーム
    // -------------------------------------------------------------------------

    ErSketchModel.Entity selectedForTest() {
        return selected;
    }

    void setSelectedForTest(ErSketchModel.Entity e) {
        this.selected = e;
    }
}
