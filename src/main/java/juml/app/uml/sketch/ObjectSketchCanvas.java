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
 * オブジェクト図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>オブジェクトをタイトル (名前・任意のステレオタイプ) + 属性行の矩形で描き、リンクを
 * 矢印/線で結ぶ。ドラッグ移動・ダブルクリック編集・右クリックメニュー・リンクの 2 クリック
 * 追加 (同一オブジェクトを 2 回で自己リンク) を受け付ける。モデル変更は
 * {@link Listener#modelEdited()} で通知し、テキスト再生成は呼び出し側 ({@link SketchPane})
 * が行う。</p>
 */
final class ObjectSketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        /** 移動・削除・リンク追加などモデルが変わった (テキスト再生成が必要)。 */
        void modelEdited();

        /** オブジェクトの編集 (ダブルクリック / メニュー) が要求された。 */
        void editObjectRequested(ObjectInstance o);

        /** 空白位置への「オブジェクトを追加」が要求された。 */
        void addObjectRequested(Point at);

        /** Esc 等でリンク追加モードが取り消された (ツールバーのモード表示を戻すため)。 */
        default void relationModeCancelled() { }

        /** リンク線のダブルクリック編集 (種別/ラベル) が要求された。 */
        default void editLinkRequested(ObjectLink link) { }
    }

    private static final int PAD_X = 10;
    private static final int TITLE_H = 26;
    private static final int LINE_H = 16;
    private static final int MIN_W = 100;
    private static final int GRID = 8;

    private ObjectSketchModel model = new ObjectSketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;
    /** ズーム (Ctrl+ホイール) と中ボタンパン。マウス座標は toModel で逆変換して使う。 */
    private final SketchViewport view = new SketchViewport(this);

    private ObjectInstance selected;
    /** リンク追加モードの矢印種別 (null = 選択/移動モード)。 */
    private ObjectLink.Kind relationMode;
    /** リンク追加モードで最初にクリックした始点オブジェクト。 */
    private ObjectInstance relationSource;
    /** グリッド吸着 (移動確定時に座標を格子へ丸める)。既定で有効。 */
    private boolean snapToGrid = true;
    private Point dragOffset;
    private boolean draggedSinceMousePress;

    ObjectSketchCanvas(Listener listener) {
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
                // リンク追加モード中はクリックで端点を置く操作なので、ダブルクリック編集は無効化する。
                if (e.getClickCount() != 2 || !editable || relationMode != null
                        || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (selected != null) {
                    listener.editObjectRequested(selected);
                    return;
                }
                ObjectLink link = linkAt(view.toModel(e.getPoint()));
                if (link != null) {
                    listener.editLinkRequested(link);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                // リンク追加モード中の Delete は無効 (旧 selected の破壊的削除を防ぐ。中断は Esc)。
                if (e.getKeyCode() == KeyEvent.VK_DELETE && editable && selected != null
                        && relationMode == null) {
                    model.removeObject(selected);
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

    /** 選択オブジェクトを相対移動する (矢印キーの微調整。Shift でグリッド単位)。 */
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

    /** 表示・編集対象のモデルを差し替える。 */
    void setModel(ObjectSketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        this.relationSource = null;
        revalidate();
        repaint();
    }

    ObjectSketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    /** グリッド吸着の有効/無効を切り替える。 */
    void setSnapToGrid(boolean on) {
        this.snapToGrid = on;
    }

    /** リンク追加モードを切り替える (null で選択/移動モードへ戻す)。 */
    void setRelationMode(ObjectLink.Kind kind) {
        this.relationMode = kind;
        this.relationSource = null;
        // モード切替時に旧選択をクリアする (ダブルクリック編集/Delete が旧オブジェクトへ漏れる防止)。
        this.selected = null;
        repaint();
    }

    /** 新しいオブジェクトを追加する。 */
    void addObject(Point at) {
        if (!editable) {
            return;
        }
        int n = model.getObjects().size();
        int x = at != null ? at.x : 40 + (n % 8) * 30;
        int y = at != null ? at.y : 40 + (n % 8) * 26;
        model.getObjects().add(new ObjectInstance(model.uniqueName("NewObject"), null, x, y));
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
        ObjectInstance hit = objectAt(mp);
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
            dragOffset = new Point(mp.x - hit.getX(), mp.y - hit.getY());
        }
        repaint();
    }

    /** テスト用: リンクモードのクリックを 1 回シミュレートする (自己リンク作成の検証に使う)。 */
    void relationClickForTest(ObjectInstance hit) {
        handleRelationClick(hit);
    }

    /** リンク追加モード: 1 クリック目で始点、2 クリック目で終点を確定する (同一で自己リンク)。 */
    private void handleRelationClick(ObjectInstance hit) {
        if (hit == null) {
            relationSource = null;
            repaint();
            return;
        }
        if (relationSource == null) {
            relationSource = hit;
        } else {
            // 2 回目のクリックでリンクを作る。同一オブジェクトを 2 回クリックした場合は
            // 自己リンク (A→A) を作る (描画は paintSelfLink が対応済み)。
            model.getLinks().add(new ObjectLink(
                    relationSource.getName(), relationMode, hit.getName(), null));
            relationSource = null;
            listener.modelEdited();
        }
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        if (!editable || relationMode != null || selected == null || dragOffset == null) {
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
            showPopup(e, objectAt(view.toModel(e.getPoint())));
            return;
        }
        if (draggedSinceMousePress) {
            draggedSinceMousePress = false;
            if (snapToGrid && selected != null) {
                selected.moveTo(snap(selected.getX()), snap(selected.getY()));
                revalidate();
                repaint();
            }
            listener.modelEdited(); // 移動確定 → '@pos を再生成
        }
        dragOffset = null;
    }

    private void showPopup(MouseEvent e, ObjectInstance hit) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (hit != null) {
            addItem(menu, "sketch.obj.menu.edit", () -> listener.editObjectRequested(hit));
            addItem(menu, "sketch.obj.menu.delete", () -> {
                model.removeObject(hit);
                selected = null;
                listener.modelEdited();
                repaint();
            });
            addLinkDeleteMenu(menu, hit);
        } else {
            // 追加位置はモデル座標で渡す (ズーム中でもクリックした場所に置く)。
            final Point at = view.toModel(e.getPoint());
            addItem(menu, "sketch.obj.menu.addObjectHere", () -> addObject(at));
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void addItem(JPopupMenu menu, String key, Runnable action) {
        JMenuItem item = new JMenuItem(Messages.get(key));
        item.addActionListener(a -> action.run());
        menu.add(item);
    }

    /** オブジェクトに接続しているリンクを個別に削除するサブメニュー。 */
    private void addLinkDeleteMenu(JPopupMenu menu, ObjectInstance hit) {
        List<ObjectLink> touching = model.getLinks().stream()
                .filter(l -> l.touches(hit.getName())).toList();
        if (touching.isEmpty()) {
            return;
        }
        JMenu sub = new JMenu(Messages.get("sketch.obj.menu.deleteLink"));
        for (ObjectLink l : touching) {
            JMenuItem item = new JMenuItem(
                    l.getLeft() + " " + l.getKind().arrow() + " " + l.getRight());
            item.addActionListener(a -> {
                model.getLinks().remove(l);
                listener.modelEdited();
                repaint();
            });
            sub.add(item);
        }
        menu.add(sub);
    }

    /** 指定点にあるオブジェクト (重なりは後に描いたもの優先)。 */
    private ObjectInstance objectAt(Point p) {
        List<ObjectInstance> os = model.getObjects();
        for (int i = os.size() - 1; i >= 0; i--) {
            if (boundsOf(os.get(i)).contains(p)) {
                return os.get(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    /** オブジェクトボックスの矩形 (フォントメトリクスから内容に合わせて算出)。 */
    Rectangle boundsOf(ObjectInstance o) {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont()
                : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int w = fm.stringWidth(o.getName()) + 2 * PAD_X + 20;
        if (o.getStereotype() != null && !o.getStereotype().isEmpty()) {
            w = Math.max(w, fm.stringWidth("<<" + o.getStereotype() + ">>") + 2 * PAD_X);
        }
        for (String s : o.getAttributes()) {
            w = Math.max(w, fm.stringWidth(s) + 2 * PAD_X);
        }
        w = Math.max(w, MIN_W);
        int h = TITLE_H + 4 + Math.max(1, o.getAttributes().size()) * LINE_H + 8;
        return new Rectangle(o.getX(), o.getY(), w, h);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 300;
        for (ObjectInstance o : model.getObjects()) {
            Rectangle r = boundsOf(o);
            w = Math.max(w, r.x + r.width + 60);
            h = Math.max(h, r.y + r.height + 60);
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
            for (ObjectLink l : model.getLinks()) {
                paintLink(g2, l);
            }
            for (ObjectInstance o : model.getObjects()) {
                paintObject(g2, o);
            }
        } finally {
            g2.dispose();
        }
        // バナー/ヒントはズームに依らず読める大きさで描く (スケール適用外)。
        Graphics2D overlay = (Graphics2D) g.create();
        try {
            overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            if (!editable) {
                SketchBanner.paint(overlay, this, unsupported);
            } else if (relationMode != null) {
                overlay.setColor(new Color(0x1565C0));
                overlay.drawString(Messages.get(relationSource == null
                        ? "sketch.obj.hint.pickSource" : "sketch.obj.hint.pickTarget"), 8, 14);
            }
        } finally {
            overlay.dispose();
        }
    }

    private void paintObject(Graphics2D g2, ObjectInstance o) {
        Rectangle r = boundsOf(o);
        g2.setColor(new Color(0xEAF3FF));
        g2.fillRect(r.x, r.y, r.width, r.height);
        boolean sel = o == selected || o == relationSource;
        g2.setColor(sel ? new Color(0x1565C0) : new Color(0x555555));
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        g2.drawRect(r.x, r.y, r.width, r.height);

        Font base = g2.getFont();
        int nameY = r.y + TITLE_H - 8;
        String stereo = o.getStereotype();
        if (stereo != null && !stereo.isEmpty()) {
            String txt = "<<" + stereo + ">>";
            g2.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() - 2f));
            int sw = g2.getFontMetrics().stringWidth(txt);
            g2.drawString(txt, r.x + (r.width - sw) / 2, r.y + 12);
            g2.setFont(base);
            nameY += 3;
        }
        // オブジェクト名は下線付きボールドで描く (インスタンスであることを示す UML 慣習)。
        g2.setFont(base.deriveFont(Font.BOLD));
        int nameW = g2.getFontMetrics().stringWidth(o.getName());
        int nameX = r.x + (r.width - nameW) / 2;
        g2.drawString(o.getName(), nameX, nameY);
        g2.drawLine(nameX, nameY + 2, nameX + nameW, nameY + 2);
        g2.setFont(base);
        g2.setStroke(new BasicStroke(1f));

        int sep = r.y + TITLE_H + 4;
        g2.drawLine(r.x, sep, r.x + r.width, sep);
        int line = sep + LINE_H - 4;
        for (String a : o.getAttributes()) {
            g2.drawString(a, r.x + PAD_X, line);
            line += LINE_H;
        }
    }

    private void paintLink(Graphics2D g2, ObjectLink link) {
        ObjectInstance left = model.findObject(link.getLeft());
        ObjectInstance right = model.findObject(link.getRight());
        if (left == null || right == null) {
            return;
        }
        boolean dashed = link.getKind() == ObjectLink.Kind.DEPENDENCY;
        boolean arrow = link.getKind() != ObjectLink.Kind.LINK;
        if (left == right) {
            // 自己リンク (A --> A) はボックス右上へ回り込むループとして専用描画する。
            paintSelfLink(g2, boundsOf(left), link, dashed, arrow);
            return;
        }
        Rectangle rl = boundsOf(left);
        Rectangle rr = boundsOf(right);
        Point pl = edgePoint(rl, center(rr));
        Point pr = edgePoint(rr, center(rl));
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x37474F));
        g2.setStroke(strokeFor(dashed));
        g2.drawLine(pl.x, pl.y, pr.x, pr.y);
        g2.setStroke(old);
        if (arrow) {
            paintOpenArrow(g2, pr, pl);
        }
        if (link.getLabel() != null && !link.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(link.getLabel(), (pl.x + pr.x) / 2 + 4, (pl.y + pr.y) / 2 - 4);
        }
    }

    /** 自己リンク (始点=終点) をボックス右上のループ線として描く。 */
    private void paintSelfLink(Graphics2D g2, Rectangle r, ObjectLink link,
                              boolean dashed, boolean arrow) {
        int exitX = r.x + r.width - 20;      // 上辺から出る点
        int topY = r.y - 18;                 // ループの上端
        int rightX = r.x + r.width + 18;     // ループの右端
        Point ret = new Point(r.x + r.width, r.y + 14); // 右辺へ戻る (矢印先)
        Point from = new Point(rightX, ret.y);
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x37474F));
        g2.setStroke(strokeFor(dashed));
        Path2D loop = new Path2D.Double();
        loop.moveTo(exitX, r.y);
        loop.lineTo(exitX, topY);
        loop.lineTo(rightX, topY);
        loop.lineTo(rightX, ret.y);
        loop.lineTo(ret.x, ret.y);
        g2.draw(loop);
        g2.setStroke(old);
        if (arrow) {
            paintOpenArrow(g2, ret, from);
        }
        if (link.getLabel() != null && !link.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(link.getLabel(), rightX + 4, topY + 4);
        }
    }

    private static Stroke strokeFor(boolean dashed) {
        return dashed
                ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{6f, 5f}, 0f)
                : new BasicStroke(1.2f);
    }

    // -------------------------------------------------------------------------
    // ヒットテスト / 幾何
    // -------------------------------------------------------------------------

    private static Point center(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** クリック点に近いリンク線を返す (しきい値内で最も近いもの)。無ければ null。 */
    private ObjectLink linkAt(Point p) {
        ObjectLink best = null;
        double bestD = 7.0; // ヒットしきい値 (px)
        for (ObjectLink link : model.getLinks()) {
            ObjectInstance left = model.findObject(link.getLeft());
            ObjectInstance right = model.findObject(link.getRight());
            if (left == null || right == null || left == right) {
                continue;
            }
            Point pl = edgePoint(boundsOf(left), center(boundsOf(right)));
            Point pr = edgePoint(boundsOf(right), center(boundsOf(left)));
            double d = pointToSegment(p.x, p.y, pl.x, pl.y, pr.x, pr.y);
            if (d < bestD) {
                bestD = d;
                best = link;
            }
        }
        return best;
    }

    private static double pointToSegment(double px, double py,
                                         double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    /** 矩形の中心から {@code toward} へ向かう線と矩形境界の交点。 */
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

    /** {@code at} に開き矢印 (関連/依存)。 */
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

    /** テスト用: 現在の選択オブジェクト。 */
    ObjectInstance selectedForTest() {
        return selected;
    }

    /** テスト用: 選択オブジェクトを直接設定する (マウス press の代替)。 */
    void setSelectedForTest(ObjectInstance o) {
        this.selected = o;
    }

    /** テスト用: 現在のズーム倍率。 */
    double zoomForTest() {
        return view.zoom();
    }

    /** テスト用: ズーム倍率を直接設定する (Ctrl+ホイール相当)。 */
    void setZoomForTest(double z) {
        view.setZoom(z);
    }
}
