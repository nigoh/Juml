// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
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
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 配置図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>ノードを種別ごとの形 (物理ノード=立方体箱・データベース=円筒・クラウド=雲・
 * 成果物=角折れ矩形など) で描き、種別が一目で分かるよう {@code «kind»}
 * ステレオタイプを併記する。リンクを矢印/線で結び、自己リンクはループとして描く。
 * ドラッグ移動・ダブルクリック編集・右クリックメニュー・リンクの 2 クリック追加を
 * 受け付ける。モデル変更は {@link Listener#modelEdited()} で通知し、テキスト再生成は
 * 呼び出し側 ({@link SketchPane}) が行う。</p>
 */
final class DeploySketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        void modelEdited();

        void editNodeRequested(DeployNode n);

        default void linkModeCancelled() { }

        default void editLinkRequested(DeployLink l) { }
    }

    private static final int PAD_X = 16;
    private static final int NODE_H = 52;
    private static final int NODE_MIN_W = 120;
    private static final int GRID = 8;
    /** 立方体/角折れ等の装飾に使う奥行きオフセット。 */
    private static final int DEPTH = 8;

    private DeploySketchModel model = new DeploySketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;

    private DeployNode selected;
    /** ズーム (Ctrl+ホイール) と中ボタンパン。マウス座標は toModel で逆変換して使う。 */
    private final SketchViewport view = new SketchViewport(this);
    private DeployLink.Kind linkMode;
    private DeployNode linkSource;
    private boolean snapToGrid = true;
    private Point dragOffset;
    private boolean draggedSinceMousePress;

    DeploySketchCanvas(Listener listener) {
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
                if (e.getClickCount() != 2 || !editable || linkMode != null
                        || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (selected != null) {
                    listener.editNodeRequested(selected);
                    return;
                }
                DeployLink link = linkAt(view.toModel(e.getPoint()));
                if (link != null) {
                    listener.editLinkRequested(link);
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
                && linkMode == null) {
            model.removeNode(selected);
            selected = null;
            listener.modelEdited();
            repaint();
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && linkMode != null) {
            setLinkMode(null);
            listener.linkModeCancelled();
        } else if (editable && selected != null && linkMode == null) {
            int[] d = SketchNudge.deltaFor(e.getKeyCode(), e.isShiftDown(), GRID);
            if (d != null) {
                nudgeSelected(d[0], d[1]);
                e.consume();
            }
        }
    }

    /** 選択ノードを相対移動する (矢印キーの微調整。Shift でグリッド単位)。 */
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

    void setModel(DeploySketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        this.linkSource = null;
        revalidate();
        repaint();
    }

    DeploySketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    void setSnapToGrid(boolean on) {
        this.snapToGrid = on;
    }

    void setLinkMode(DeployLink.Kind kind) {
        this.linkMode = kind;
        this.linkSource = null;
        this.selected = null;
        repaint();
    }

    /** 新しいノードを追加する。 */
    void addNode(DeployNode.Kind kind, Point at) {
        if (!editable) {
            return;
        }
        int n = model.getNodes().size();
        int x = at != null ? at.x : 50 + (n % 6) * 30;
        int y = at != null ? at.y : 50 + (n % 6) * 28;
        model.getNodes().add(new DeployNode(kind, model.uniqueId(baseNameFor(kind)), null, x, y));
        listener.modelEdited();
        revalidate();
        repaint();
    }

    private static String baseNameFor(DeployNode.Kind kind) {
        String kw = kind.keyword();
        return Character.toUpperCase(kw.charAt(0)) + kw.substring(1);
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
        DeployNode hit = nodeAt(mp);
        if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
            selected = hit;
            repaint();
            if (e.isPopupTrigger()) {
                showPopup(e, hit);
            }
            return;
        }
        if (linkMode != null) {
            handleLinkClick(hit);
            return;
        }
        selected = hit;
        draggedSinceMousePress = false;
        if (hit != null) {
            dragOffset = new Point(mp.x - hit.getX(), mp.y - hit.getY());
        }
        repaint();
    }

    /** テスト用: 実際のリンク追加経路 (2 クリック) を 1 クリック分呼ぶ。 */
    void linkClickForTest(DeployNode hit) {
        handleLinkClick(hit);
    }

    private void handleLinkClick(DeployNode hit) {
        if (hit == null) {
            linkSource = null;
            repaint();
            return;
        }
        if (linkSource == null) {
            linkSource = hit;
        } else {
            // 2 回目のクリックでリンクを作る。同一ノードを 2 回クリックすると自己リンク
            // (A→A) を作る (描画は paintSelfLink が対応済み)。
            model.getLinks().add(new DeployLink(
                    linkSource.getId(), linkMode, hit.getId(), null));
            linkSource = null;
            listener.modelEdited();
        }
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        if (!editable || linkMode != null || selected == null || dragOffset == null) {
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
            showPopup(e, nodeAt(view.toModel(e.getPoint())));
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

    private void showPopup(MouseEvent e, DeployNode hit) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (hit != null) {
            addItem(menu, "sketch.depl.menu.edit", () -> listener.editNodeRequested(hit));
            addItem(menu, "sketch.depl.menu.delete", () -> {
                model.removeNode(hit);
                selected = null;
                listener.modelEdited();
                repaint();
            });
            addLinkDeleteMenu(menu, hit);
        } else {
            // 追加位置はモデル座標で渡す (ズーム中でもクリックした場所に置く)。
            final Point at = view.toModel(e.getPoint());
            addItem(menu, "sketch.depl.menu.addNodeHere",
                    () -> addNode(DeployNode.Kind.NODE, at));
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void addItem(JPopupMenu menu, String key, Runnable action) {
        JMenuItem item = new JMenuItem(Messages.get(key));
        item.addActionListener(a -> action.run());
        menu.add(item);
    }

    private void addLinkDeleteMenu(JPopupMenu menu, DeployNode hit) {
        List<DeployLink> touching = model.getLinks().stream()
                .filter(l -> l.touches(hit.getId())).toList();
        if (touching.isEmpty()) {
            return;
        }
        JMenu sub = new JMenu(Messages.get("sketch.depl.menu.deleteLink"));
        for (DeployLink l : touching) {
            JMenuItem item = new JMenuItem(
                    l.getFrom() + " " + l.getKind().arrow() + " " + l.getTo());
            item.addActionListener(a -> {
                model.getLinks().remove(l);
                listener.modelEdited();
                repaint();
            });
            sub.add(item);
        }
        menu.add(sub);
    }

    private DeployNode nodeAt(Point p) {
        List<DeployNode> ns = model.getNodes();
        for (int i = ns.size() - 1; i >= 0; i--) {
            if (boundsOf(ns.get(i)).contains(p)) {
                return ns.get(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    Rectangle boundsOf(DeployNode n) {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont()
                : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int textW = Math.max(fm.stringWidth(n.displayText()),
                fm.stringWidth(stereotype(n.getKind())));
        int w = Math.max(NODE_MIN_W, textW + 2 * PAD_X);
        return new Rectangle(n.getX(), n.getY(), w, NODE_H);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 300;
        for (DeployNode n : model.getNodes()) {
            Rectangle r = boundsOf(n);
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
            for (DeployLink l : model.getLinks()) {
                paintLink(g2, l);
            }
            for (DeployNode n : model.getNodes()) {
                paintNode(g2, n);
            }
            if (!editable) {
                SketchBanner.paint(g2, this, unsupported);
            } else if (linkMode != null) {
                g2.setColor(new Color(0x1565C0));
                g2.drawString(Messages.get(linkSource == null
                        ? "sketch.depl.hint.pickSource"
                        : "sketch.depl.hint.pickTarget"), 8, 14);
            }
        } finally {
            g2.dispose();
        }
    }

    private static String stereotype(DeployNode.Kind kind) {
        return "«" + kind.keyword() + "»";
    }

    private void paintNode(Graphics2D g2, DeployNode n) {
        Rectangle r = boundsOf(n);
        boolean sel = n == selected || n == linkSource;
        Color line = sel ? new Color(0x1565C0) : new Color(0x555555);
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        paintShape(g2, n.getKind(), r, line);
        FontMetrics fm = g2.getFontMetrics();
        int cx = r.x + r.width / 2;
        g2.setColor(new Color(0x607D8B));
        String st = stereotype(n.getKind());
        g2.drawString(st, cx - fm.stringWidth(st) / 2, r.y + 18);
        g2.setColor(new Color(0x1A1A1A));
        String name = n.displayText();
        g2.drawString(name, cx - fm.stringWidth(name) / 2, r.y + 36);
        g2.setStroke(new BasicStroke(1f));
    }

    /** 種別ごとの外形を塗り + 枠線で描く (ステレオタイプ/表示名は呼び出し側が重ねる)。 */
    private void paintShape(Graphics2D g2, DeployNode.Kind kind, Rectangle r, Color line) {
        Color fill = new Color(0xEFF3F8);
        switch (kind) {
            case NODE:      paintBox3d(g2, r, fill, line); break;
            case DATABASE:  paintDatabase(g2, r, fill, line); break;
            case CLOUD:     paintCloud(g2, r, fill, line); break;
            case ARTIFACT:  paintArtifact(g2, r, fill, line); break;
            case COMPONENT: paintComponentShape(g2, r, fill, line); break;
            case FOLDER:    paintFolder(g2, r, fill, line); break;
            case FRAME:     paintFrame(g2, r, fill, line); break;
            case RECTANGLE:
            default:        paintPlain(g2, r, fill, line); break;
        }
    }

    private void paintPlain(Graphics2D g2, Rectangle r, Color fill, Color line) {
        g2.setColor(fill);
        g2.fillRect(r.x, r.y, r.width, r.height);
        g2.setColor(line);
        g2.drawRect(r.x, r.y, r.width, r.height);
    }

    private void paintBox3d(Graphics2D g2, Rectangle r, Color fill, Color line) {
        paintPlain(g2, r, fill, line);
        int d = DEPTH;
        g2.setColor(line);
        // 上面と右面のパースを 2 本の平行四辺形で示す。
        g2.drawLine(r.x, r.y, r.x + d, r.y - d);
        g2.drawLine(r.x + d, r.y - d, r.x + r.width + d, r.y - d);
        g2.drawLine(r.x + r.width + d, r.y - d, r.x + r.width, r.y);
        g2.drawLine(r.x + r.width + d, r.y - d, r.x + r.width + d, r.y + r.height - d);
        g2.drawLine(r.x + r.width + d, r.y + r.height - d, r.x + r.width, r.y + r.height);
    }

    private void paintDatabase(Graphics2D g2, Rectangle r, Color fill, Color line) {
        int eh = 12;
        g2.setColor(fill);
        g2.fillRect(r.x, r.y + eh / 2, r.width, r.height - eh);
        g2.fillOval(r.x, r.y, r.width, eh);
        g2.fillOval(r.x, r.y + r.height - eh, r.width, eh);
        g2.setColor(line);
        g2.drawLine(r.x, r.y + eh / 2, r.x, r.y + r.height - eh / 2);
        g2.drawLine(r.x + r.width, r.y + eh / 2, r.x + r.width, r.y + r.height - eh / 2);
        g2.drawArc(r.x, r.y + r.height - eh, r.width, eh, 180, 180);
        g2.drawOval(r.x, r.y, r.width, eh);
    }

    private void paintCloud(Graphics2D g2, Rectangle r, Color fill, Color line) {
        g2.setColor(fill);
        g2.fillRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
        g2.setColor(line);
        g2.drawRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
    }

    private void paintArtifact(Graphics2D g2, Rectangle r, Color fill, Color line) {
        int fold = 12;
        Polygon body = new Polygon();
        body.addPoint(r.x, r.y);
        body.addPoint(r.x + r.width - fold, r.y);
        body.addPoint(r.x + r.width, r.y + fold);
        body.addPoint(r.x + r.width, r.y + r.height);
        body.addPoint(r.x, r.y + r.height);
        g2.setColor(fill);
        g2.fillPolygon(body);
        g2.setColor(line);
        g2.drawPolygon(body);
        // 折り返した角。
        g2.drawLine(r.x + r.width - fold, r.y, r.x + r.width - fold, r.y + fold);
        g2.drawLine(r.x + r.width - fold, r.y + fold, r.x + r.width, r.y + fold);
    }

    private void paintComponentShape(Graphics2D g2, Rectangle r, Color fill, Color line) {
        paintPlain(g2, r, fill, line);
        // 左辺にまたがる 2 つの小矩形 (UML コンポーネントアイコン)。
        g2.setColor(fill);
        g2.fillRect(r.x - 4, r.y + 10, 8, 6);
        g2.fillRect(r.x - 4, r.y + 24, 8, 6);
        g2.setColor(line);
        g2.drawRect(r.x - 4, r.y + 10, 8, 6);
        g2.drawRect(r.x - 4, r.y + 24, 8, 6);
    }

    private void paintFolder(Graphics2D g2, Rectangle r, Color fill, Color line) {
        int tabW = Math.min(40, r.width / 3);
        int tabH = 8;
        g2.setColor(fill);
        g2.fillRect(r.x, r.y, tabW, tabH);
        g2.fillRect(r.x, r.y + tabH, r.width, r.height - tabH);
        g2.setColor(line);
        g2.drawLine(r.x, r.y, r.x + tabW, r.y);
        g2.drawLine(r.x + tabW, r.y, r.x + tabW, r.y + tabH);
        g2.drawRect(r.x, r.y + tabH, r.width, r.height - tabH);
    }

    private void paintFrame(Graphics2D g2, Rectangle r, Color fill, Color line) {
        paintPlain(g2, r, fill, line);
        int tabW = Math.min(30, r.width / 3);
        int tabH = 12;
        g2.setColor(line);
        g2.drawLine(r.x + tabW, r.y, r.x + tabW, r.y + tabH);
        g2.drawLine(r.x, r.y + tabH, r.x + tabW - 5, r.y + tabH);
        g2.drawLine(r.x + tabW - 5, r.y + tabH, r.x + tabW, r.y + tabH - 5);
    }

    private void paintLink(Graphics2D g2, DeployLink link) {
        DeployNode from = model.findNode(link.getFrom());
        DeployNode to = model.findNode(link.getTo());
        if (from == null || to == null) {
            return;
        }
        if (from == to) {
            paintSelfLink(g2, boundsOf(from), link);
            return;
        }
        Point pf = edgePoint(boundsOf(from), center(boundsOf(to)));
        Point pt = edgePoint(boundsOf(to), center(boundsOf(from)));
        boolean dashed = link.getKind() == DeployLink.Kind.DEPENDENCY;
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x37474F));
        g2.setStroke(linkStroke(dashed));
        g2.drawLine(pf.x, pf.y, pt.x, pt.y);
        g2.setStroke(old);
        if (link.getKind() != DeployLink.Kind.LINK) {
            paintOpenArrow(g2, pt, pf);
        }
        if (link.getLabel() != null && !link.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(link.getLabel(), (pf.x + pt.x) / 2 + 4, (pf.y + pt.y) / 2 - 4);
        }
    }

    /** 自己リンク (始点=終点) をボックス右上のループ線として描く。 */
    private void paintSelfLink(Graphics2D g2, Rectangle r, DeployLink link) {
        Point[] loop = selfLoopPoints(r);
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x37474F));
        g2.setStroke(linkStroke(link.getKind() == DeployLink.Kind.DEPENDENCY));
        for (int i = 0; i < loop.length - 1; i++) {
            g2.drawLine(loop[i].x, loop[i].y, loop[i + 1].x, loop[i + 1].y);
        }
        g2.setStroke(old);
        if (link.getKind() != DeployLink.Kind.LINK) {
            paintOpenArrow(g2, loop[loop.length - 1],
                    new Point(loop[loop.length - 2].x, loop[loop.length - 1].y));
        }
        if (link.getLabel() != null && !link.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(link.getLabel(), r.x + r.width + 4, r.y - 6);
        }
    }

    /** 自己ループの折れ線頂点列 (上辺→上→右→右辺へ戻る)。 */
    private static Point[] selfLoopPoints(Rectangle r) {
        int exitX = r.x + r.width - 20;
        int topY = r.y - 18;
        int rightX = r.x + r.width + 18;
        int retY = r.y + 14;
        return new Point[]{
                new Point(exitX, r.y),
                new Point(exitX, topY),
                new Point(rightX, topY),
                new Point(rightX, retY),
                new Point(r.x + r.width, retY),
        };
    }

    private static Stroke linkStroke(boolean dashed) {
        return dashed
                ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{6f, 5f}, 0f)
                : new BasicStroke(1.2f);
    }

    // -------------------------------------------------------------------------
    // ヒットテスト / 幾何
    // -------------------------------------------------------------------------

    private DeployLink linkAt(Point p) {
        DeployLink best = null;
        double bestD = 7.0;
        for (DeployLink link : model.getLinks()) {
            DeployNode from = model.findNode(link.getFrom());
            DeployNode to = model.findNode(link.getTo());
            if (from == null || to == null) {
                continue;
            }
            double d = from == to
                    ? selfLoopDistance(boundsOf(from), p)
                    : segmentDistance(boundsOf(from), boundsOf(to), p);
            if (d < bestD) {
                bestD = d;
                best = link;
            }
        }
        return best;
    }

    private static double segmentDistance(Rectangle from, Rectangle to, Point p) {
        Point pf = edgePoint(from, center(to));
        Point pt = edgePoint(to, center(from));
        return pointToSegment(p.x, p.y, pf.x, pf.y, pt.x, pt.y);
    }

    private static double selfLoopDistance(Rectangle r, Point p) {
        Point[] loop = selfLoopPoints(r);
        double best = Double.MAX_VALUE;
        for (int i = 0; i < loop.length - 1; i++) {
            best = Math.min(best, pointToSegment(p.x, p.y,
                    loop[i].x, loop[i].y, loop[i + 1].x, loop[i + 1].y));
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

    private void paintOpenArrow(Graphics2D g2, Point at, Point from) {
        double dx = from.x - at.x;
        double dy = from.y - at.y;
        double d = Math.max(1e-6, Math.hypot(dx, dy));
        double ux = dx / d;
        double uy = dy / d;
        int len = 11;
        int wid = 6;
        g2.setColor(new Color(0x37474F));
        g2.drawLine(at.x, at.y,
                (int) (at.x + ux * len - uy * wid), (int) (at.y + uy * len + ux * wid));
        g2.drawLine(at.x, at.y,
                (int) (at.x + ux * len + uy * wid), (int) (at.y + uy * len - ux * wid));
    }

    // -------------------------------------------------------------------------
    // テスト用シーム
    // -------------------------------------------------------------------------

    DeployNode selectedForTest() {
        return selected;
    }

    void setSelectedForTest(DeployNode n) {
        this.selected = n;
    }
}
