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
import java.util.List;

/**
 * コンポーネント図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>コンポーネントを UML コンポーネントアイコン付きの矩形、インターフェースを円で描き、
 * 関係を矢印/線で結ぶ。ドラッグ移動・ダブルクリック編集・右クリックメニュー・関係の
 * 2 クリック追加を受け付ける。モデル変更は {@link Listener#modelEdited()} で通知し、
 * テキスト再生成は呼び出し側 ({@link SketchPane}) が行う。</p>
 */
final class ComponentSketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        void modelEdited();

        void editNodeRequested(ComponentNode n);

        default void relationModeCancelled() { }

        default void editRelationRequested(ComponentRelation r) { }
    }

    private static final int PAD_X = 16;
    private static final int COMP_H = 46;
    private static final int COMP_MIN_W = 110;
    private static final int IFACE_D = 34;
    private static final int GRID = 8;

    private ComponentSketchModel model = new ComponentSketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;

    private ComponentNode selected;
    private ComponentRelation.Kind relationMode;
    private ComponentNode relationSource;
    private boolean snapToGrid = true;
    private Point dragOffset;
    private boolean draggedSinceMousePress;

    ComponentSketchCanvas(Listener listener) {
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
                if (e.getClickCount() != 2 || !editable || relationMode != null) {
                    return;
                }
                if (selected != null) {
                    listener.editNodeRequested(selected);
                    return;
                }
                ComponentRelation rel = relationAt(e.getPoint());
                if (rel != null) {
                    listener.editRelationRequested(rel);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE && editable && selected != null
                        && relationMode == null) {
                    model.removeNode(selected);
                    selected = null;
                    listener.modelEdited();
                    repaint();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && relationMode != null) {
                    setRelationMode(null);
                    listener.relationModeCancelled();
                } else if (editable && selected != null && relationMode == null) {
                    int[] d = SketchNudge.deltaFor(e.getKeyCode(), e.isShiftDown(), GRID);
                    if (d != null) {
                        nudgeSelected(d[0], d[1]);
                        e.consume();
                    }
                }
            }
        });
    }

    /** 選択要素を相対移動する (矢印キーの微調整。Shift でグリッド単位)。 */
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

    void setModel(ComponentSketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        this.relationSource = null;
        revalidate();
        repaint();
    }

    ComponentSketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    void setSnapToGrid(boolean on) {
        this.snapToGrid = on;
    }

    void setRelationMode(ComponentRelation.Kind kind) {
        this.relationMode = kind;
        this.relationSource = null;
        this.selected = null;
        repaint();
    }

    /** 新しい要素 (コンポーネント/インターフェース) を追加する。 */
    void addNode(ComponentNode.Kind kind, Point at) {
        if (!editable) {
            return;
        }
        int n = model.getNodes().size();
        int x = at != null ? at.x : 50 + (n % 6) * 30;
        int y = at != null ? at.y : 50 + (n % 6) * 28;
        String base = kind == ComponentNode.Kind.INTERFACE ? "Interface" : "Component";
        model.getNodes().add(new ComponentNode(kind, model.uniqueId(base), null, x, y));
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
        ComponentNode hit = nodeAt(e.getPoint());
        if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
            selected = hit;
            repaint();
            if (e.isPopupTrigger()) {
                showPopup(e, hit);
            }
            return;
        }
        if (relationMode != null) {
            handleRelationClick(hit);
            return;
        }
        selected = hit;
        draggedSinceMousePress = false;
        if (hit != null) {
            dragOffset = new Point(e.getX() - hit.getX(), e.getY() - hit.getY());
        }
        repaint();
    }

    private void handleRelationClick(ComponentNode hit) {
        if (hit == null) {
            relationSource = null;
            repaint();
            return;
        }
        if (relationSource == null) {
            relationSource = hit;
        } else if (relationSource != hit) {
            model.getRelations().add(new ComponentRelation(
                    relationSource.getId(), relationMode, hit.getId(), null));
            relationSource = null;
            listener.modelEdited();
        }
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        if (!editable || relationMode != null || selected == null || dragOffset == null) {
            return;
        }
        selected.moveTo(Math.max(0, e.getX() - dragOffset.x),
                Math.max(0, e.getY() - dragOffset.y));
        draggedSinceMousePress = true;
        revalidate();
        repaint();
    }

    private void handleRelease(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopup(e, nodeAt(e.getPoint()));
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

    private void showPopup(MouseEvent e, ComponentNode hit) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (hit != null) {
            addItem(menu, "sketch.menu.edit", () -> listener.editNodeRequested(hit));
            addItem(menu, "sketch.menu.delete", () -> {
                model.removeNode(hit);
                selected = null;
                listener.modelEdited();
                repaint();
            });
            addRelationDeleteMenu(menu, hit);
        } else {
            final Point at = e.getPoint();
            addItem(menu, "sketch.comp.menu.addComponentHere",
                    () -> addNode(ComponentNode.Kind.COMPONENT, at));
            addItem(menu, "sketch.comp.menu.addInterfaceHere",
                    () -> addNode(ComponentNode.Kind.INTERFACE, at));
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void addItem(JPopupMenu menu, String key, Runnable action) {
        JMenuItem item = new JMenuItem(Messages.get(key));
        item.addActionListener(a -> action.run());
        menu.add(item);
    }

    private void addRelationDeleteMenu(JPopupMenu menu, ComponentNode hit) {
        List<ComponentRelation> touching = model.getRelations().stream()
                .filter(r -> r.touches(hit.getId())).toList();
        if (touching.isEmpty()) {
            return;
        }
        JMenu sub = new JMenu(Messages.get("sketch.comp.menu.deleteRelation"));
        for (ComponentRelation r : touching) {
            JMenuItem item = new JMenuItem(
                    r.getFrom() + " " + r.getKind().arrow() + " " + r.getTo());
            item.addActionListener(a -> {
                model.getRelations().remove(r);
                listener.modelEdited();
                repaint();
            });
            sub.add(item);
        }
        menu.add(sub);
    }

    private ComponentNode nodeAt(Point p) {
        List<ComponentNode> ns = model.getNodes();
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

    Rectangle boundsOf(ComponentNode n) {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont()
                : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        if (n.getKind() == ComponentNode.Kind.INTERFACE) {
            int w = IFACE_D + 8 + fm.stringWidth(n.displayText()) + 6;
            return new Rectangle(n.getX(), n.getY(), w, Math.max(IFACE_D + 6, COMP_H - 8));
        }
        int w = Math.max(COMP_MIN_W, fm.stringWidth(n.displayText()) + 2 * PAD_X);
        return new Rectangle(n.getX(), n.getY(), w, COMP_H);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 300;
        for (ComponentNode n : model.getNodes()) {
            Rectangle r = boundsOf(n);
            w = Math.max(w, r.x + r.width + 80);
            h = Math.max(h, r.y + r.height + 80);
        }
        return new Dimension(w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            for (ComponentRelation r : model.getRelations()) {
                paintRelation(g2, r);
            }
            for (ComponentNode n : model.getNodes()) {
                paintNode(g2, n);
            }
            if (!editable) {
                SketchBanner.paint(g2, this, unsupported);
            } else if (relationMode != null) {
                g2.setColor(new Color(0x1565C0));
                g2.drawString(Messages.get(relationSource == null
                        ? "sketch.comp.hint.pickSource"
                        : "sketch.comp.hint.pickTarget"), 8, 14);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintNode(Graphics2D g2, ComponentNode n) {
        Rectangle r = boundsOf(n);
        boolean sel = n == selected || n == relationSource;
        Color line = sel ? new Color(0x1565C0) : new Color(0x555555);
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        FontMetrics fm = g2.getFontMetrics();
        if (n.getKind() == ComponentNode.Kind.INTERFACE) {
            g2.setColor(new Color(0xEAF7EA));
            g2.fillOval(r.x, r.y + (r.height - IFACE_D) / 2, IFACE_D, IFACE_D);
            g2.setColor(line);
            g2.drawOval(r.x, r.y + (r.height - IFACE_D) / 2, IFACE_D, IFACE_D);
            g2.setColor(new Color(0x1A1A1A));
            g2.drawString(n.displayText(), r.x + IFACE_D + 8,
                    r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
        } else {
            g2.setColor(new Color(0xEAF1FB));
            g2.fillRect(r.x, r.y, r.width, r.height);
            g2.setColor(line);
            g2.drawRect(r.x, r.y, r.width, r.height);
            // 左辺にまたがる 2 つの小矩形 (UML コンポーネントアイコン)。
            g2.fillRect(r.x - 4, r.y + 8, 8, 6);
            g2.drawRect(r.x - 4, r.y + 8, 8, 6);
            g2.fillRect(r.x - 4, r.y + 20, 8, 6);
            g2.drawRect(r.x - 4, r.y + 20, 8, 6);
            int tw = fm.stringWidth(n.displayText());
            g2.setColor(new Color(0x1A1A1A));
            g2.drawString(n.displayText(), r.x + (r.width - tw) / 2 + 4,
                    r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    private void paintRelation(Graphics2D g2, ComponentRelation rel) {
        ComponentNode from = model.findNode(rel.getFrom());
        ComponentNode to = model.findNode(rel.getTo());
        if (from == null || to == null || from == to) {
            return;
        }
        Point pf = edgePoint(boundsOf(from), center(boundsOf(to)));
        Point pt = edgePoint(boundsOf(to), center(boundsOf(from)));
        boolean dashed = rel.getKind() == ComponentRelation.Kind.DEPENDENCY;
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x37474F));
        g2.setStroke(dashed
                ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{6f, 5f}, 0f)
                : new BasicStroke(1.2f));
        g2.drawLine(pf.x, pf.y, pt.x, pt.y);
        g2.setStroke(old);
        if (rel.getKind() != ComponentRelation.Kind.LINK) {
            paintOpenArrow(g2, pt, pf);
        }
        if (rel.getLabel() != null && !rel.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(rel.getLabel(), (pf.x + pt.x) / 2 + 4, (pf.y + pt.y) / 2 - 4);
        }
    }

    // -------------------------------------------------------------------------
    // ヒットテスト / 幾何
    // -------------------------------------------------------------------------

    private ComponentRelation relationAt(Point p) {
        ComponentRelation best = null;
        double bestD = 7.0;
        for (ComponentRelation rel : model.getRelations()) {
            ComponentNode from = model.findNode(rel.getFrom());
            ComponentNode to = model.findNode(rel.getTo());
            if (from == null || to == null || from == to) {
                continue;
            }
            Point pf = edgePoint(boundsOf(from), center(boundsOf(to)));
            Point pt = edgePoint(boundsOf(to), center(boundsOf(from)));
            double d = pointToSegment(p.x, p.y, pf.x, pf.y, pt.x, pt.y);
            if (d < bestD) {
                bestD = d;
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

    ComponentNode selectedForTest() {
        return selected;
    }

    void setSelectedForTest(ComponentNode n) {
        this.selected = n;
    }
}
