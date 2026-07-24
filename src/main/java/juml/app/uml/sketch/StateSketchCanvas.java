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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * 状態遷移図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>状態を角丸ボックスで描き、遷移を矢印で結ぶ。初期/終了の擬似状態 {@code [*]} は
 * 接続先の状態から導出した位置に小円 (初期) / 二重丸 (終了) として描く。ドラッグ移動・
 * ダブルクリック編集・右クリックメニュー・遷移の 2 クリック追加を受け付ける。モデル変更は
 * {@link Listener#modelEdited()} で通知し、テキスト再生成は呼び出し側 ({@link SketchPane})
 * が行う。</p>
 */
final class StateSketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        void modelEdited();

        void editStateRequested(StateNode s);

        default void transitionModeCancelled() { }

        default void editTransitionRequested(StateTransition t) { }
    }

    private static final int PAD_X = 14;
    private static final int STATE_H = 36;
    private static final int MIN_W = 90;
    private static final int ARC = 16;
    private static final int GRID = 8;
    /** 擬似状態 {@code [*]} の半径と、接続状態からの距離。 */
    private static final int PSEUDO_R = 9;
    private static final int PSEUDO_GAP = 30;

    private StateSketchModel model = new StateSketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;

    private StateNode selected;
    /** ズーム (Ctrl+ホイール) と中ボタンパン。マウス座標は toModel で逆変換して使う。 */
    private final SketchViewport view = new SketchViewport(this);
    /** 遷移追加モードか (true = 2 クリックで from→to を結ぶ)。 */
    private boolean transitionMode;
    private StateNode transitionSource;
    private boolean snapToGrid = true;
    private Point dragOffset;
    private boolean draggedSinceMousePress;
    /** 端点ドラッグ (遷移の付替え) のヒットしきい値 (画面上 px。{@link EndpointHitThreshold}
     * でズームに応じてモデル座標半径へ変換してから使う)。 */
    private static final double ENDPOINT_HIT_RADIUS = 8.0;
    /** 端点ハンドル (発見可能性のための小さな正方形) の一辺 (モデル座標, px)。 */
    private static final int HANDLE_SIZE = 6;
    /** 端点ドラッグ (遷移の付替え) の状態。クリック判定/no-op 判定は
     * {@link EndpointDragSession#finish} に委ねる。 */
    private final EndpointDragSession<StateTransition> endpointDrag = new EndpointDragSession<>();

    StateSketchCanvas(Listener listener) {
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
                if (e.getClickCount() != 2 || !editable || transitionMode
                        || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (selected != null) {
                    listener.editStateRequested(selected);
                    return;
                }
                StateTransition t = transitionAt(view.toModel(e.getPoint()));
                if (t != null) {
                    listener.editTransitionRequested(t);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && endpointDrag.isActive()) {
                    // 端点ドラッグ中の Esc は繋ぎ替えを行わず安全に中断する。
                    cancelEndpointDrag();
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_DELETE && editable && selected != null
                        && !transitionMode) {
                    model.removeState(selected);
                    selected = null;
                    listener.modelEdited();
                    repaint();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && transitionMode) {
                    setTransitionMode(false);
                    listener.transitionModeCancelled();
                } else if (editable && selected != null && !transitionMode) {
                    int[] d = SketchNudge.deltaFor(e.getKeyCode(), e.isShiftDown(), GRID);
                    if (d != null) {
                        nudgeSelected(d[0], d[1]);
                        e.consume();
                    }
                }
            }
        });
    }

    /** 選択状態を相対移動する (矢印キーの微調整。Shift でグリッド単位)。 */
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

    void setModel(StateSketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        this.transitionSource = null;
        this.endpointDrag.cancel();
        revalidate();
        repaint();
    }

    StateSketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    void setSnapToGrid(boolean on) {
        this.snapToGrid = on;
    }

    /** 遷移追加モードを切り替える。 */
    void setTransitionMode(boolean on) {
        this.transitionMode = on;
        this.transitionSource = null;
        this.selected = null;
        // モード切替で進行中の端点ドラッグも安全に中断する (spec #6)。
        this.endpointDrag.cancel();
        repaint();
    }

    /** 新しい状態を追加する (右クリック位置、無ければ既存数に応じてずらす)。 */
    void addState(Point at) {
        if (!editable) {
            return;
        }
        int n = model.getStates().size();
        int x = at != null ? at.x : 50 + (n % 6) * 30;
        int y = at != null ? at.y : 50 + (n % 6) * 28;
        model.getStates().add(new StateNode(model.uniqueName("State"), x, y));
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
        StateNode hit = stateAt(mp);
        if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
            selected = hit;
            repaint();
            if (e.isPopupTrigger()) {
                showPopup(e, hit);
            }
            return;
        }
        if (transitionMode) {
            handleTransitionClick(hit);
            return;
        }
        EndpointHit endpointHit = endpointHandleAt(mp);
        if (endpointHit != null) {
            // 端点ハンドルの掴みはノードドラッグより優先する。
            endpointDrag.start(endpointHit.transition(), endpointHit.fromEnd(), mp);
            selected = null;
            repaint();
            return;
        }
        selected = hit;
        draggedSinceMousePress = false;
        if (hit != null) {
            dragOffset = new Point(mp.x - hit.getX(), mp.y - hit.getY());
        }
        repaint();
    }

    private void handleTransitionClick(StateNode hit) {
        if (hit == null) {
            transitionSource = null;
            repaint();
            return;
        }
        if (transitionSource == null) {
            transitionSource = hit;
        } else {
            model.getTransitions().add(new StateTransition(
                    transitionSource.getName(), hit.getName(), null));
            transitionSource = null;
            listener.modelEdited();
        }
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        if (!editable) {
            return;
        }
        if (endpointDrag.isActive()) {
            // ノードは動かさず、固定側端点→カーソルのラバーバンド線だけを更新する。
            endpointDrag.updateCursor(view.toModel(e.getPoint()));
            repaint();
            return;
        }
        if (transitionMode || selected == null || dragOffset == null) {
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
        if (endpointDrag.isActive()) {
            finishEndpointDrag(view.toModel(e.getPoint()));
            return;
        }
        if (e.isPopupTrigger()) {
            showPopup(e, stateAt(view.toModel(e.getPoint())));
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

    private void showPopup(MouseEvent e, StateNode hit) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (hit != null) {
            addItem(menu, "sketch.menu.edit", () -> listener.editStateRequested(hit));
            addItem(menu, "sketch.state.menu.markInitial", () -> {
                model.getTransitions().add(new StateTransition(
                        StateTransition.PSEUDO, hit.getName(), null));
                listener.modelEdited();
                repaint();
            });
            addItem(menu, "sketch.state.menu.markFinal", () -> {
                model.getTransitions().add(new StateTransition(
                        hit.getName(), StateTransition.PSEUDO, null));
                listener.modelEdited();
                repaint();
            });
            addItem(menu, "sketch.menu.delete", () -> {
                model.removeState(hit);
                selected = null;
                listener.modelEdited();
                repaint();
            });
            addTransitionDeleteMenu(menu, hit);
        } else {
            // 追加位置はモデル座標で渡す (ズーム中でもクリックした場所に置く)。
            final Point at = view.toModel(e.getPoint());
            addItem(menu, "sketch.state.menu.addStateHere", () -> addState(at));
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void addItem(JPopupMenu menu, String key, Runnable action) {
        JMenuItem item = new JMenuItem(Messages.get(key));
        item.addActionListener(a -> action.run());
        menu.add(item);
    }

    private void addTransitionDeleteMenu(JPopupMenu menu, StateNode hit) {
        List<StateTransition> touching = model.getTransitions().stream()
                .filter(t -> t.touches(hit.getName())).toList();
        if (touching.isEmpty()) {
            return;
        }
        JMenu sub = new JMenu(Messages.get("sketch.state.menu.deleteTransition"));
        for (StateTransition t : touching) {
            JMenuItem item = new JMenuItem(t.getFrom() + " --> " + t.getTo());
            item.addActionListener(a -> {
                model.getTransitions().remove(t);
                listener.modelEdited();
                repaint();
            });
            sub.add(item);
        }
        menu.add(sub);
    }

    private StateNode stateAt(Point p) {
        List<StateNode> ss = model.getStates();
        for (int i = ss.size() - 1; i >= 0; i--) {
            if (boundsOf(ss.get(i)).contains(p)) {
                return ss.get(i);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 端点ドラッグ (遷移の付替え)
    // -------------------------------------------------------------------------

    /** 端点ハンドルのヒット結果 (遷移 + どちら側 (from/to) を掴んだか)。 */
    private record EndpointHit(StateTransition transition, boolean fromEnd) { }

    /** press 位置に最も近い端点ハンドルを返す (しきい値外や擬似状態端点/自己遷移は対象外)。 */
    private EndpointHit endpointHandleAt(Point p) {
        StateTransition bestT = null;
        boolean bestFrom = true;
        double bestD = EndpointHitThreshold.modelRadius(ENDPOINT_HIT_RADIUS, view.zoom());
        for (StateTransition t : model.getTransitions()) {
            Point[] anchors = realEndpointAnchors(t);
            if (anchors == null) {
                continue;
            }
            double df = p.distance(anchors[0]);
            if (df < bestD) {
                bestD = df;
                bestT = t;
                bestFrom = true;
            }
            double dt = p.distance(anchors[1]);
            if (dt < bestD) {
                bestD = dt;
                bestT = t;
                bestFrom = false;
            }
        }
        return bestT == null ? null : new EndpointHit(bestT, bestFrom);
    }

    /**
     * {@code t} の from/to アンカー (線が状態境界に接する点。描画と同じ {@link #segmentFor} を
     * 再利用)。初期/終了の擬似状態 {@code [*]} 端点や未解決端点は端点ドラッグの対象外として
     * null を返す (擬似状態はドラッグして繋ぎ替えるノードを持たないため)。自己遷移は
     * {@link #selfTransitionAnchors} のループ上アンカーを返す (掴み直せるようハンドルを消さない)。
     */
    private Point[] realEndpointAnchors(StateTransition t) {
        if (StateTransition.PSEUDO.equals(t.getFrom()) || StateTransition.PSEUDO.equals(t.getTo())) {
            return null;
        }
        StateNode fs = model.findState(t.getFrom());
        StateNode ts = model.findState(t.getTo());
        if (fs == null || ts == null) {
            return null;
        }
        if (fs == ts) {
            // 自己遷移も掴み直せるようループ上アンカーを返す (ハンドルを消さない)。
            return selfTransitionAnchors(boundsOf(fs));
        }
        double[] seg = segmentFor(t, fs, ts, false, false);
        return new Point[]{new Point((int) seg[0], (int) seg[1]), new Point((int) seg[2], (int) seg[3])};
    }

    /** 自己遷移ループのアンカー ({@code [from, to]})。{@link #paintSelfTransition} と同じ幾何。 */
    private static Point[] selfTransitionAnchors(Rectangle r) {
        Point ret = new Point(r.x + r.width, r.y + 14);
        return new Point[]{new Point(r.x + r.width + 20, ret.y), ret};
    }

    /**
     * テスト用/純関数: 点 {@code p} が 2 つの候補アンカー {@code a} (0側) / {@code b} (1側)
     * のどちらに近いか。しきい値 {@code radius} 以内で、より近い側の番号 (0 or 1) を返す。
     * どちらもしきい値外なら -1。
     */
    static int nearestEndpointForTest(Point p, Point a, Point b, double radius) {
        double da = p.distance(a);
        double db = p.distance(b);
        if (da > radius && db > radius) {
            return -1;
        }
        return da <= db ? 0 : 1;
    }

    /** 端点ドラッグのリリース処理: クリック相当 (移動なし) や自ノードへの落下は no-op として
     * 弾き、実際に別状態上へドラッグされたときだけ繋ぎ替える。 */
    private void finishEndpointDrag(Point releasePoint) {
        StateTransition t = endpointDrag.item();
        boolean fromEnd = endpointDrag.leftEnd();
        StateNode target = stateAt(releasePoint);
        String targetName = target == null ? null : target.getName();
        String current = fromEnd ? t.getFrom() : t.getTo();
        if (endpointDrag.finish(releasePoint, targetName, current)) {
            reattachEndpoint(t, fromEnd, targetName);
        }
        repaint();
    }

    /** Esc/モード切替時に端点ドラッグを繋ぎ替えずに中断する。 */
    private void cancelEndpointDrag() {
        endpointDrag.cancel();
        repaint();
    }

    /** 遷移の端点を実際に付け替える経路 (production と reattachForTest の共通経路)。 */
    private void reattachEndpoint(StateTransition t, boolean fromEnd, String targetName) {
        if (fromEnd) {
            t.setFrom(targetName);
        } else {
            t.setTo(targetName);
        }
        listener.modelEdited();
    }

    /** テスト用: 実際の繋ぎ替え経路 (reattachEndpoint) でモデル端点を更新する。 */
    void reattachForTest(StateTransition t, boolean fromEnd, String targetName) {
        reattachEndpoint(t, fromEnd, targetName);
        repaint();
    }

    /** テスト用: 現在ドラッグ中の遷移 (端点ドラッグ中でなければ null)。 */
    StateTransition dragTransitionForTest() {
        return endpointDrag.item();
    }

    /** テスト用: 遷移の端点アンカー ({from, to})。擬似状態端点/未解決なら null (自己遷移はループ上アンカー)。 */
    Point[] endpointAnchorsForTest(StateTransition t) {
        return realEndpointAnchors(t);
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    Rectangle boundsOf(StateNode s) {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont()
                : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int w = Math.max(MIN_W, fm.stringWidth(s.getName()) + 2 * PAD_X);
        return new Rectangle(s.getX(), s.getY(), w, STATE_H);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 300;
        for (StateNode s : model.getStates()) {
            Rectangle r = boundsOf(s);
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
            for (StateTransition t : model.getTransitions()) {
                paintTransition(g2, t);
            }
            for (StateNode s : model.getStates()) {
                paintState(g2, s);
            }
            if (editable && !transitionMode) {
                // 発見可能性: 選択/移動モードでのみ端点ハンドルを見せる。
                paintEndpointHandles(g2);
            }
            paintEndpointDragOverlay(g2);
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
            } else if (transitionMode) {
                overlay.setColor(new Color(0x1565C0));
                overlay.drawString(Messages.get(transitionSource == null
                        ? "sketch.state.hint.pickSource" : "sketch.state.hint.pickTarget"), 8, 14);
            }
        } finally {
            overlay.dispose();
        }
    }

    private void paintState(Graphics2D g2, StateNode s) {
        Rectangle r = boundsOf(s);
        g2.setColor(new Color(0xE8F0FE));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, ARC, ARC);
        boolean sel = s == selected || s == transitionSource;
        g2.setColor(sel ? new Color(0x1565C0) : new Color(0x555555));
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, ARC, ARC);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(s.getName());
        g2.setColor(new Color(0x1A1A1A));
        g2.drawString(s.getName(), r.x + (r.width - tw) / 2,
                r.y + (STATE_H + fm.getAscent() - fm.getDescent()) / 2);
        g2.setStroke(new BasicStroke(1f));
    }

    private void paintTransition(Graphics2D g2, StateTransition t) {
        boolean fromP = StateTransition.PSEUDO.equals(t.getFrom());
        boolean toP = StateTransition.PSEUDO.equals(t.getTo());
        StateNode fs = fromP ? null : model.findState(t.getFrom());
        StateNode ts = toP ? null : model.findState(t.getTo());
        if ((!fromP && fs == null) || (!toP && ts == null) || (fromP && toP)) {
            return;
        }
        g2.setColor(new Color(0x37474F));
        if (!fromP && !toP && fs == ts) {
            paintSelfTransition(g2, boundsOf(fs), t);
            return;
        }
        double[] seg = segmentFor(t, fs, ts, fromP, toP);
        Point p1 = new Point((int) seg[0], (int) seg[1]);
        Point p2 = new Point((int) seg[2], (int) seg[3]);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawLine(p1.x, p1.y, p2.x, p2.y);
        paintOpenArrow(g2, p2, p1);
        if (fromP) {
            // 初期擬似状態: 塗りつぶし小円。
            g2.fillOval(p1.x - PSEUDO_R, p1.y - PSEUDO_R, 2 * PSEUDO_R, 2 * PSEUDO_R);
        } else if (toP) {
            // 終了擬似状態: 二重丸。
            g2.drawOval(p2.x - PSEUDO_R, p2.y - PSEUDO_R, 2 * PSEUDO_R, 2 * PSEUDO_R);
            g2.fillOval(p2.x - PSEUDO_R + 3, p2.y - PSEUDO_R + 3,
                    2 * (PSEUDO_R - 3), 2 * (PSEUDO_R - 3));
        }
        paintLabel(g2, t, (p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
    }

    /** from→to の線分端点 {@code [x1,y1,x2,y2]} を求める (自己遷移を除く)。 */
    private double[] segmentFor(StateTransition t, StateNode fs, StateNode ts,
                                boolean fromP, boolean toP) {
        if (!fromP && !toP) {
            Rectangle rf = boundsOf(fs);
            Rectangle rt = boundsOf(ts);
            Point p1 = edgePoint(rf, center(rt));
            Point p2 = edgePoint(rt, center(rf));
            return new double[]{p1.x, p1.y, p2.x, p2.y};
        }
        if (fromP) {
            Rectangle rt = boundsOf(ts);
            int cx = rt.x + rt.width / 2;
            int cy = rt.y - PSEUDO_GAP;
            Point p2 = edgePoint(rt, new Point(cx, cy));
            return new double[]{cx, cy, p2.x, p2.y};
        }
        Rectangle rf = boundsOf(fs);
        int cx = rf.x + rf.width / 2;
        int cy = rf.y + rf.height + PSEUDO_GAP;
        Point p1 = edgePoint(rf, new Point(cx, cy));
        return new double[]{p1.x, p1.y, cx, cy};
    }

    private void paintSelfTransition(Graphics2D g2, Rectangle r, StateTransition t) {
        int exitX = r.x + r.width - 24;
        int topY = r.y - 20;
        Point[] anchors = selfTransitionAnchors(r);
        Point from = anchors[0];
        Point ret = anchors[1];
        g2.setStroke(new BasicStroke(1.2f));
        java.awt.geom.Path2D loop = new java.awt.geom.Path2D.Double();
        loop.moveTo(exitX, r.y);
        loop.lineTo(exitX, topY);
        loop.lineTo(from.x, topY);
        loop.lineTo(from.x, ret.y);
        loop.lineTo(ret.x, ret.y);
        g2.draw(loop);
        paintOpenArrow(g2, ret, from);
        paintLabel(g2, t, from.x + 4, topY + 4);
    }

    private void paintLabel(Graphics2D g2, StateTransition t, int x, int y) {
        if (t.getLabel() != null && !t.getLabel().isEmpty()) {
            g2.setColor(new Color(0x455A64));
            g2.drawString(t.getLabel(), x + 4, y - 4);
            g2.setColor(new Color(0x37474F));
        }
    }

    /** 発見可能性: 各遷移の from/to アンカーに小さな正方形ハンドルを描く。 */
    private void paintEndpointHandles(Graphics2D g2) {
        int half = HANDLE_SIZE / 2;
        g2.setColor(new Color(0x1565C0));
        for (StateTransition t : model.getTransitions()) {
            Point[] anchors = realEndpointAnchors(t);
            if (anchors == null) {
                continue;
            }
            g2.fillRect(anchors[0].x - half, anchors[0].y - half, HANDLE_SIZE, HANDLE_SIZE);
            g2.fillRect(anchors[1].x - half, anchors[1].y - half, HANDLE_SIZE, HANDLE_SIZE);
        }
    }

    /** 端点ドラッグ中: 固定側端点→カーソルのラバーバンド線を描く。 */
    private void paintEndpointDragOverlay(Graphics2D g2) {
        Point cursor = endpointDrag.cursor();
        if (!endpointDrag.isActive() || cursor == null) {
            return;
        }
        StateTransition t = endpointDrag.item();
        String fixedName = endpointDrag.leftEnd() ? t.getTo() : t.getFrom();
        StateNode fixed = StateTransition.PSEUDO.equals(fixedName) ? null : model.findState(fixedName);
        if (fixed == null) {
            return;
        }
        Point start = edgePoint(boundsOf(fixed), cursor);
        g2.setColor(new Color(0x1565C0));
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{5f, 4f}, 0f));
        g2.drawLine(start.x, start.y, cursor.x, cursor.y);
        g2.setStroke(new BasicStroke(1f));
    }

    // -------------------------------------------------------------------------
    // ヒットテスト / 幾何
    // -------------------------------------------------------------------------

    private StateTransition transitionAt(Point p) {
        StateTransition best = null;
        double bestD = 7.0;
        for (StateTransition t : model.getTransitions()) {
            boolean fromP = StateTransition.PSEUDO.equals(t.getFrom());
            boolean toP = StateTransition.PSEUDO.equals(t.getTo());
            StateNode fs = fromP ? null : model.findState(t.getFrom());
            StateNode ts = toP ? null : model.findState(t.getTo());
            if ((!fromP && fs == null) || (!toP && ts == null) || (fromP && toP)) {
                continue;
            }
            if (!fromP && !toP && fs == ts) {
                continue; // 自己遷移はラベル編集をダブルクリックで受けない (簡略化)
            }
            double[] s = segmentFor(t, fs, ts, fromP, toP);
            double d = pointToSegment(p.x, p.y, s[0], s[1], s[2], s[3]);
            if (d < bestD) {
                bestD = d;
                best = t;
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
        double cx = x1 + u * dx;
        double cy = y1 + u * dy;
        return Math.hypot(px - cx, py - cy);
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
        double tt = Math.min(scaleX, scaleY);
        return new Point((int) Math.round(c.x + dx * tt), (int) Math.round(c.y + dy * tt));
    }

    private void paintOpenArrow(Graphics2D g2, Point at, Point from) {
        double dx = from.x - at.x;
        double dy = from.y - at.y;
        double d = Math.max(1e-6, Math.hypot(dx, dy));
        double ux = dx / d;
        double uy = dy / d;
        int len = 11;
        int wid = 6;
        g2.drawLine(at.x, at.y,
                (int) (at.x + ux * len - uy * wid), (int) (at.y + uy * len + ux * wid));
        g2.drawLine(at.x, at.y,
                (int) (at.x + ux * len + uy * wid), (int) (at.y + uy * len - ux * wid));
    }

    // -------------------------------------------------------------------------
    // テスト用シーム
    // -------------------------------------------------------------------------

    StateNode selectedForTest() {
        return selected;
    }

    void setSelectedForTest(StateNode s) {
        this.selected = s;
    }
}
