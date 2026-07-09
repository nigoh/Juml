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
 * GUI デザイナーの描画・マウス操作キャンバス。
 *
 * <p>クラスを UML 風の 3 段ボックス (名前 / フィールド / メソッド) で描き、
 * ドラッグ移動・ダブルクリック編集・右クリックメニュー・関係の 2 クリック追加を
 * 受け付ける。モデル変更は {@link Listener#modelEdited()} で通知し、テキストへの
 * 反映 (PlantUML 再生成) は呼び出し側 ({@link SketchPane}) が行う。</p>
 */
final class SketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        /** 移動・削除・関係追加などモデルが変わった (テキスト再生成が必要)。 */
        void modelEdited();

        /** クラスの編集 (ダブルクリック / メニュー) が要求された。 */
        void editRequested(SketchClass c);

        /** 空白位置への「クラスを追加」が要求された。 */
        void addClassRequested(Point at);

        /** Esc 等で関係追加モードが取り消された (ツールバーのモード表示を戻すため)。 */
        default void relationModeCancelled() { }

        /** 関係線のダブルクリック編集 (ラベル/種別) が要求された。 */
        default void editRelationRequested(SketchRelation relation) { }
    }

    private static final int PAD_X = 10;
    private static final int TITLE_H = 24;
    private static final int LINE_H = 16;
    private static final int MIN_W = 120;

    private SketchModel model = new SketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;

    private SketchClass selected;
    /** 関係追加モードの矢印種別 (null = 選択/移動モード)。 */
    private SketchRelation.Kind relationMode;
    /** 関係追加モードで最初にクリックした始点クラス。 */
    private SketchClass relationSource;
    /** グリッド吸着 (移動確定時に座標を格子へ丸める)。既定で有効。 */
    private boolean snapToGrid = true;
    private static final int GRID = 8;
    /** ドラッグ中の掴み位置オフセット。 */
    private Point dragOffset;
    private boolean draggedSinceMousePress;

    SketchCanvas(Listener listener) {
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
                // 関係追加モード中はクリックで端点を置く操作なので、ダブルクリック編集は無効化する
                // (旧 selected の編集ダイアログが不意に開いて関係描画が中断するのを防ぐ)。
                if (e.getClickCount() != 2 || !editable || relationMode != null) {
                    return;
                }
                if (selected != null) {
                    listener.editRequested(selected);
                    return;
                }
                // クラス外のダブルクリック: 近くの関係線があればその場で編集する。
                SketchRelation rel = relationAt(e.getPoint());
                if (rel != null) {
                    listener.editRelationRequested(rel);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                // 関係追加モード中の Delete は無効 (旧 selected クラスの破壊的削除を防ぐ。中断は Esc)。
                if (e.getKeyCode() == KeyEvent.VK_DELETE && editable && selected != null
                        && relationMode == null) {
                    model.removeClass(selected);
                    selected = null;
                    listener.modelEdited();
                    repaint();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && relationMode != null) {
                    // 関係追加モードを中断して選択/移動モードへ戻す。
                    // ツールバーのモード表示も戻すためリスナーへ通知する。
                    setRelationMode(null);
                    listener.relationModeCancelled();
                }
            }
        });
    }

    /** 表示・編集対象のモデルを差し替える。 */
    void setModel(SketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        this.relationSource = null;
        revalidate();
        repaint();
    }

    SketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    /** グリッド吸着の有効/無効を切り替える。 */
    void setSnapToGrid(boolean on) {
        this.snapToGrid = on;
    }

    private static int snap(int v) {
        return Math.round(v / (float) GRID) * GRID;
    }

    /** テスト用: グリッド吸着の丸め結果 (純関数)。 */
    static int snapForTest(int v) {
        return snap(v);
    }

    /** テスト用: 現在の選択クラス。 */
    SketchClass selectedForTest() {
        return selected;
    }

    /** テスト用: 選択クラスを直接設定する (マウス press の代替)。 */
    void setSelectedForTest(SketchClass c) {
        this.selected = c;
    }

    /** 関係追加モードを切り替える (null で選択/移動モードへ戻す)。 */
    void setRelationMode(SketchRelation.Kind kind) {
        this.relationMode = kind;
        this.relationSource = null;
        // モード切替時に旧選択をクリアする。関係モード中は press で selected を更新しないため、
        // 残すとダブルクリック編集/Delete が旧クラスへ漏れる (上のガードと二重の保険)。
        this.selected = null;
        repaint();
    }

    // -------------------------------------------------------------------------
    // マウス操作
    // -------------------------------------------------------------------------

    private void handlePress(MouseEvent e) {
        if (!editable) {
            return;
        }
        SketchClass hit = classAt(e.getPoint());
        // 右ボタン押下は選択/関係クリックとして扱わず消費する。ただしポップアップの表示は
        // プラットフォームごとのトリガー時点で 1 回だけ行う (Linux は press, Windows/Mac は
        // release)。press で isRightMouseButton も見て showPopup すると、release の
        // isPopupTrigger と二重表示になるため、ここでは isPopupTrigger のときだけ表示する。
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

    /** 関係追加モード: 1 クリック目で始点、2 クリック目で終点を確定する。 */
    private void handleRelationClick(SketchClass hit) {
        if (hit == null) {
            relationSource = null;
            repaint();
            return;
        }
        if (relationSource == null) {
            relationSource = hit;
        } else if (relationSource != hit) {
            model.getRelations().add(new SketchRelation(
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
        selected.moveTo(Math.max(0, e.getX() - dragOffset.x),
                Math.max(0, e.getY() - dragOffset.y));
        draggedSinceMousePress = true;
        revalidate();
        repaint();
    }

    private void handleRelease(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showPopup(e, classAt(e.getPoint()));
            return;
        }
        if (draggedSinceMousePress) {
            draggedSinceMousePress = false;
            if (snapToGrid && selected != null) {
                // 移動確定時に座標を格子へ吸着させ、ボックスの整列を容易にする。
                selected.moveTo(snap(selected.getX()), snap(selected.getY()));
                revalidate();
                repaint();
            }
            listener.modelEdited(); // 移動確定 → '@pos を再生成
        }
        dragOffset = null;
    }

    private void showPopup(MouseEvent e, SketchClass hit) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (hit != null) {
            JMenuItem edit = new JMenuItem(Messages.get("sketch.menu.edit"));
            edit.addActionListener(a -> listener.editRequested(hit));
            menu.add(edit);
            JMenuItem del = new JMenuItem(Messages.get("sketch.menu.delete"));
            del.addActionListener(a -> {
                model.removeClass(hit);
                selected = null;
                listener.modelEdited();
                repaint();
            });
            menu.add(del);
            addRelationDeleteMenu(menu, hit);
        } else {
            JMenuItem add = new JMenuItem(Messages.get("sketch.menu.addClassHere"));
            final Point at = e.getPoint();
            add.addActionListener(a -> listener.addClassRequested(at));
            menu.add(add);
        }
        menu.show(this, e.getX(), e.getY());
    }

    /** クラスに接続している関係を個別に削除するサブメニュー。 */
    private void addRelationDeleteMenu(JPopupMenu menu, SketchClass hit) {
        List<SketchRelation> touching = model.getRelations().stream()
                .filter(r -> r.touches(hit.getName())).toList();
        if (touching.isEmpty()) {
            return;
        }
        JMenu sub = new JMenu(Messages.get("sketch.menu.deleteRelation"));
        for (SketchRelation r : touching) {
            JMenuItem item = new JMenuItem(
                    r.getLeft() + " " + r.getKind().arrow() + " " + r.getRight());
            item.addActionListener(a -> {
                model.getRelations().remove(r);
                listener.modelEdited();
                repaint();
            });
            sub.add(item);
        }
        menu.add(sub);
    }

    /** 指定点にあるクラス (重なりは後に描いたもの優先)。 */
    private SketchClass classAt(Point p) {
        List<SketchClass> cs = model.getClasses();
        for (int i = cs.size() - 1; i >= 0; i--) {
            if (boundsOf(cs.get(i)).contains(p)) {
                return cs.get(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    /** クラスボックスの矩形 (フォントメトリクスから内容に合わせて算出)。 */
    Rectangle boundsOf(SketchClass c) {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont()
                : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int w = fm.stringWidth(c.getName()) + 2 * PAD_X + 20;
        for (String s : c.getFields()) {
            w = Math.max(w, fm.stringWidth(s) + 2 * PAD_X);
        }
        for (String s : c.getMethods()) {
            w = Math.max(w, fm.stringWidth(s) + 2 * PAD_X);
        }
        w = Math.max(w, MIN_W);
        int h = TITLE_H + 4
                + Math.max(1, c.getFields().size()) * LINE_H
                + Math.max(1, c.getMethods().size()) * LINE_H + 8;
        return new Rectangle(c.getX(), c.getY(), w, h);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 300;
        for (SketchClass c : model.getClasses()) {
            Rectangle r = boundsOf(c);
            w = Math.max(w, r.x + r.width + 60);
            h = Math.max(h, r.y + r.height + 60);
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
            for (SketchRelation r : model.getRelations()) {
                paintRelation(g2, r);
            }
            for (SketchClass c : model.getClasses()) {
                paintClass(g2, c);
            }
            if (!editable) {
                paintDisabledBanner(g2);
            } else if (relationMode != null) {
                g2.setColor(new Color(0x1565C0));
                g2.drawString(Messages.get(relationSource == null
                        ? "sketch.hint.pickSource" : "sketch.hint.pickTarget"), 8, 14);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintClass(Graphics2D g2, SketchClass c) {
        Rectangle r = boundsOf(c);
        g2.setColor(new Color(0xFFFBE6));
        g2.fillRect(r.x, r.y, r.width, r.height);
        boolean isSel = c == selected || c == relationSource;
        g2.setColor(isSel ? new Color(0x1565C0) : new Color(0x555555));
        g2.setStroke(new BasicStroke(isSel ? 2f : 1f));
        g2.drawRect(r.x, r.y, r.width, r.height);

        FontMetrics fm = g2.getFontMetrics();
        int y = r.y + TITLE_H - 8;
        String stereo = stereotypeOf(c);
        Font base = g2.getFont();
        if (!stereo.isEmpty()) {
            g2.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() - 2f));
            // 中央寄せ幅は縮小後フォントのメトリクスで測る。base の fm で測ると
            // 実際の描画幅より広く見積もられ、ステレオタイプが左へずれる。
            int stereoW = g2.getFontMetrics().stringWidth(stereo);
            g2.drawString(stereo, r.x + (r.width - stereoW) / 2, r.y + 11);
            g2.setFont(base);
            y += 4;
        }
        Font nameFont = c.getKind() == SketchClass.Kind.ABSTRACT
                ? base.deriveFont(Font.BOLD | Font.ITALIC) : base.deriveFont(Font.BOLD);
        g2.setFont(nameFont);
        g2.drawString(c.getName(),
                r.x + (r.width - g2.getFontMetrics().stringWidth(c.getName())) / 2, y);
        g2.setFont(base);
        g2.setStroke(new BasicStroke(1f));

        int sep1 = r.y + TITLE_H + 4;
        g2.drawLine(r.x, sep1, r.x + r.width, sep1);
        int line = sep1 + LINE_H - 4;
        for (String s : c.getFields()) {
            g2.drawString(s, r.x + PAD_X, line);
            line += LINE_H;
        }
        int sep2 = sep1 + Math.max(1, c.getFields().size()) * LINE_H + 2;
        g2.drawLine(r.x, sep2, r.x + r.width, sep2);
        line = sep2 + LINE_H - 4;
        for (String s : c.getMethods()) {
            g2.drawString(s, r.x + PAD_X, line);
            line += LINE_H;
        }
    }

    private static String stereotypeOf(SketchClass c) {
        switch (c.getKind()) {
            case INTERFACE: return "«interface»";
            case ENUM:      return "«enum»";
            default:        return "";
        }
    }

    private void paintRelation(Graphics2D g2, SketchRelation rel) {
        SketchClass left = model.findClass(rel.getLeft());
        SketchClass right = model.findClass(rel.getRight());
        if (left == null || right == null) {
            return;
        }
        Rectangle rl = boundsOf(left);
        Rectangle rr = boundsOf(right);
        if (left == right) {
            // 自己関連 (A --> A) は始点=終点となり通常の線分では点に潰れる。
            // ボックス右上から外へ回り込むループとして専用描画する。
            paintSelfRelation(g2, rl, rel);
            return;
        }
        Point pl = edgePoint(rl, center(rr));
        Point pr = edgePoint(rr, center(rl));
        // PlantUML 表記の意味に合わせ、線は right(子/利用側) → left(親/対象) へ向かう。
        boolean dashed = rel.getKind() == SketchRelation.Kind.IMPLEMENTS
                || rel.getKind() == SketchRelation.Kind.DEPENDENCY;
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x37474F));
        g2.setStroke(dashed
                ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{6f, 5f}, 0f)
                : new BasicStroke(1.2f));
        g2.drawLine(pl.x, pl.y, pr.x, pr.y);
        g2.setStroke(old);
        switch (rel.getKind()) {
            case EXTENDS:
            case IMPLEMENTS:
                paintTriangle(g2, pl, pr, false);
                break;
            case AGGREGATION:
                paintDiamond(g2, pl, pr, false);
                break;
            case COMPOSITION:
                paintDiamond(g2, pl, pr, true);
                break;
            case ASSOCIATION:
            case DEPENDENCY:
                paintOpenArrow(g2, pr, pl);
                break;
            default:
                break;
        }
        if (rel.getLabel() != null && !rel.getLabel().isEmpty()) {
            g2.setColor(new Color(0x555555));
            g2.drawString(rel.getLabel(), (pl.x + pr.x) / 2 + 4, (pl.y + pr.y) / 2 - 4);
        }
    }

    /** 自己関連 (始点=終点) をボックス右上のループ線として描く。 */
    private void paintSelfRelation(Graphics2D g2, Rectangle r, SketchRelation rel) {
        int exitX = r.x + r.width - 20;      // 上辺から出る点
        int topY = r.y - 18;                 // ループの上端
        int rightX = r.x + r.width + 18;     // ループの右端
        Point ret = new Point(r.x + r.width, r.y + 14); // 右辺へ戻る (矢印先)
        Point from = new Point(rightX, ret.y);
        boolean dashed = rel.getKind() == SketchRelation.Kind.IMPLEMENTS
                || rel.getKind() == SketchRelation.Kind.DEPENDENCY;
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x37474F));
        g2.setStroke(dashed
                ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{6f, 5f}, 0f)
                : new BasicStroke(1.2f));
        Path2D loop = new Path2D.Double();
        loop.moveTo(exitX, r.y);
        loop.lineTo(exitX, topY);
        loop.lineTo(rightX, topY);
        loop.lineTo(rightX, ret.y);
        loop.lineTo(ret.x, ret.y);
        g2.draw(loop);
        g2.setStroke(old);
        switch (rel.getKind()) {
            case EXTENDS:
            case IMPLEMENTS:
                paintTriangle(g2, ret, from, false);
                break;
            case AGGREGATION:
                paintDiamond(g2, ret, from, false);
                break;
            case COMPOSITION:
                paintDiamond(g2, ret, from, true);
                break;
            case ASSOCIATION:
            case DEPENDENCY:
                paintOpenArrow(g2, ret, from);
                break;
            default:
                break;
        }
        if (rel.getLabel() != null && !rel.getLabel().isEmpty()) {
            g2.setColor(new Color(0x555555));
            g2.drawString(rel.getLabel(), rightX + 4, topY + 4);
        }
    }

    private static Point center(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** クリック点に近い関係線を返す (しきい値内で最も近いもの)。無ければ null。 */
    private SketchRelation relationAt(Point p) {
        SketchRelation best = null;
        double bestD = 7.0; // ヒットしきい値 (px)
        for (SketchRelation rel : model.getRelations()) {
            SketchClass left = model.findClass(rel.getLeft());
            SketchClass right = model.findClass(rel.getRight());
            if (left == null || right == null) {
                continue;
            }
            Point pl = edgePoint(boundsOf(left), center(boundsOf(right)));
            Point pr = edgePoint(boundsOf(right), center(boundsOf(left)));
            double d = pointToSegment(p.x, p.y, pl.x, pl.y, pr.x, pr.y);
            if (d < bestD) {
                bestD = d;
                best = rel;
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
        double cx = x1 + t * dx;
        double cy = y1 + t * dy;
        return Math.hypot(px - cx, py - cy);
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

    /** {@code at} に、{@code from} から向かってくる線に対する白三角 (継承/実現)。 */
    private void paintTriangle(Graphics2D g2, Point at, Point from, boolean filled) {
        Path2D p = arrowHead(at, from, 14, 8);
        g2.setColor(filled ? new Color(0x37474F) : getBackground());
        g2.fill(p);
        g2.setColor(new Color(0x37474F));
        g2.draw(p);
    }

    /** {@code at} にひし形 (集約=白 / コンポジション=黒)。 */
    private void paintDiamond(Graphics2D g2, Point at, Point from, boolean filled) {
        double ux = unitX(at, from);
        double uy = unitY(at, from);
        int len = 9;
        int wid = 6;
        Path2D p = new Path2D.Double();
        p.moveTo(at.x, at.y);
        p.lineTo(at.x + ux * len - uy * wid, at.y + uy * len + ux * wid);
        p.lineTo(at.x + ux * 2 * len, at.y + uy * 2 * len);
        p.lineTo(at.x + ux * len + uy * wid, at.y + uy * len - ux * wid);
        p.closePath();
        g2.setColor(filled ? new Color(0x37474F) : getBackground());
        g2.fill(p);
        g2.setColor(new Color(0x37474F));
        g2.draw(p);
    }

    /** {@code at} に開き矢印 (関連/依存)。 */
    private void paintOpenArrow(Graphics2D g2, Point at, Point from) {
        double ux = unitX(at, from);
        double uy = unitY(at, from);
        int len = 11;
        int wid = 6;
        g2.setColor(new Color(0x37474F));
        g2.drawLine(at.x, at.y,
                (int) (at.x + ux * len - uy * wid), (int) (at.y + uy * len + ux * wid));
        g2.drawLine(at.x, at.y,
                (int) (at.x + ux * len + uy * wid), (int) (at.y + uy * len - ux * wid));
    }

    private static Path2D arrowHead(Point at, Point from, int len, int wid) {
        double ux = unitX(at, from);
        double uy = unitY(at, from);
        Path2D p = new Path2D.Double();
        p.moveTo(at.x, at.y);
        p.lineTo(at.x + ux * len - uy * wid, at.y + uy * len + ux * wid);
        p.lineTo(at.x + ux * len + uy * wid, at.y + uy * len - ux * wid);
        p.closePath();
        return p;
    }

    private static double unitX(Point at, Point from) {
        double dx = from.x - at.x;
        double dy = from.y - at.y;
        double d = Math.max(1e-6, Math.hypot(dx, dy));
        return dx / d;
    }

    private static double unitY(Point at, Point from) {
        double dx = from.x - at.x;
        double dy = from.y - at.y;
        double d = Math.max(1e-6, Math.hypot(dx, dy));
        return dy / d;
    }

    /** バナーに列挙する未対応行の最大数 (残りは「... 他 N 行」でまとめる)。 */
    private static final int MAX_BANNER_LINES = 6;

    /**
     * 未対応構文で GUI 編集を無効化しているときの警告バナー。
     * 単なる件数だけでなく、実際に未対応だった行 (先頭数件) を列挙して、
     * ユーザーがテキストのどこを直せば編集可能になるか分かるようにする。
     */
    private void paintDisabledBanner(Graphics2D g2) {
        String header = java.text.MessageFormat.format(
                Messages.get("sketch.disabled.header"), unsupported.size());
        int shown = Math.min(unsupported.size(), MAX_BANNER_LINES);
        int lineH = 15;
        int pad = 5;
        int rows = 1 + shown + (unsupported.size() > shown ? 1 : 0);
        int bannerH = pad * 2 + rows * lineH;
        g2.setColor(new Color(0xB71C1C));
        g2.fillRect(0, 0, getWidth(), bannerH);
        g2.setColor(Color.WHITE);
        int y = pad + lineH - 3;
        g2.setFont(getFont().deriveFont(java.awt.Font.BOLD));
        g2.drawString(header, 8, y);
        g2.setFont(getFont().deriveFont(java.awt.Font.PLAIN));
        for (int i = 0; i < shown; i++) {
            y += lineH;
            g2.drawString("• " + truncate(unsupported.get(i), 90), 16, y);
        }
        if (unsupported.size() > shown) {
            y += lineH;
            g2.drawString(java.text.MessageFormat.format(
                    Messages.get("sketch.disabled.more"), unsupported.size() - shown), 16, y);
        }
    }

    private static String truncate(String s, int max) {
        String t = s == null ? "" : s;
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
