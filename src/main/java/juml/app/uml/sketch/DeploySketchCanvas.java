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
import java.util.Map;

/**
 * 配置図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>ノードを種別ごとの形 (物理ノード=立方体箱・データベース=円筒・クラウド=雲・
 * 成果物=角折れ矩形など) で描き、種別が一目で分かるよう {@code «kind»}
 * ステレオタイプを併記する。リンクを矢印/線で結び、自己リンクはループとして描く。
 * ドラッグ移動・ダブルクリック編集・右クリックメニュー・リンクの 2 クリック追加を
 * 受け付ける。モデル変更は {@link Listener#modelEdited()} で通知し、テキスト再生成は
 * 呼び出し側 ({@link SketchPane}) が行う。</p>
 *
 * <p>入れ子コンテナ ({@link DeployNode#isContainer()}) は枠 (タイトル行 + 子ノード領域)
 * として描く。子ノードの座標は親の内側原点からの相対値で、絶対座標への変換・当たり判定・
 * 自動サイズ計算は {@link DeploySketchLayout} が担う。</p>
 *
 * <p>選択/移動モード ({@code linkMode == null}) では各リンクの端点に正方形ハンドルを描き、
 * ハンドルを掴んでドラッグし別ノード上で離すと端点を付け替える (ノード外で離せば取消)。
 * ジオメトリは {@link DeploySketchLinkHandles} が担う。</p>
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
    /** ドラッグ中ノードの座標系原点 (絶対座標。最上位なら (0,0)、入れ子なら親の内側原点)。 */
    private Point dragOrigin = new Point(0, 0);
    private boolean draggedSinceMousePress;
    /** 端点ドラッグ (リンクの付替え) の状態。クリック判定/no-op 判定は
     * {@link EndpointDragSession#finish} に委ねる。 */
    private final EndpointDragSession<DeployLink> endpointDrag = new EndpointDragSession<>();

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
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && endpointDrag.isActive()) {
            // 端点ドラッグ中の Esc は繋ぎ替えを行わず安全に中断する。
            cancelEndpointDrag();
            return;
        }
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
        // モデル差替え時に旧モデルのリンクを指した端点ドラッグが残ると、以後の release で
        // 孤立参照への reattach/modelEdited が起きうる。他 6 キャンバスと同様に必ず中断する
        // (bug-hunt round4 指摘 L)。
        this.endpointDrag.cancel();
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
        // モード切替で進行中の端点ドラッグも安全に中断する (他 6 キャンバスと同じ spec #6。
        // bug-hunt round4 で Deploy に欠けていたことが判明)。
        this.endpointDrag.cancel();
        repaint();
    }

    /** 新しいノードを最上位 (トップレベル) へ追加する。 */
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

    /**
     * 新しいノードを {@code parent} の子として追加する ({@code at} は盤面の絶対座標。
     * null なら親の内側原点付近の既定位置)。
     */
    void addChildNode(DeployNode.Kind kind, DeployNode parent, Point at) {
        if (!editable || parent == null) {
            return;
        }
        // 先に既定位置で子を追加し、親をコンテナ化させてから content 原点を確定する。
        // 葉ノードは computeContentOrigins に載らない (コンテナだけが原点を持つ) ため、
        // 追加「前」に逆算すると親が葉のとき原点が (0,0) になり、絶対クリック座標が
        // そのまま相対座標として入って初回の子が大きくずれる (bug-hunt round9 論点1/2:
        // round8 で葉ノードにも子追加を許した際の回帰)。
        DeployNode child = new DeployNode(kind, model.uniqueId(baseNameFor(kind)), null, 10, 10);
        model.addChild(parent, child);
        if (at != null) {
            // 親がコンテナになった後の論理 content 原点 (枠拡張の影響を受けない、子配置の
            // 唯一の基準。bug-hunt round5 論点1) を基準に相対座標へ変換する。
            Point origin = currentContentOrigins().getOrDefault(parent, new Point(0, 0));
            child.moveTo(Math.max(0, at.x - origin.x), Math.max(0, at.y - origin.y));
        }
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
        Map<DeployNode, Rectangle> layout = currentLayout();
        DeployNode hit = DeploySketchLayout.hitTest(model.getNodes(), layout, mp);
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
        // 選択/移動モードでは端点ハンドルを優先的に判定する (ノードの縁と重なりうるため)。
        DeploySketchLinkHandles.EndpointHit eh =
                DeploySketchLinkHandles.hitTest(model, layout, mp, view.zoom());
        if (eh != null) {
            endpointDrag.start(eh.link(), eh.startEnd(), mp);
            selected = null;
            repaint();
            return;
        }
        selected = hit;
        draggedSinceMousePress = false;
        if (hit != null) {
            Rectangle r = layout.get(hit);
            dragOffset = new Point(mp.x - r.x, mp.y - r.y);
            dragOrigin = DeploySketchLayout.contentOriginOf(hit, currentContentOrigins());
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
        if (!editable) {
            return;
        }
        if (endpointDrag.isActive()) {
            endpointDrag.updateCursor(view.toModel(e.getPoint()));
            repaint();
            return;
        }
        if (linkMode != null || selected == null || dragOffset == null) {
            return;
        }
        Point mp = view.toModel(e.getPoint());
        selected.moveTo(Math.max(0, mp.x - dragOffset.x - dragOrigin.x),
                Math.max(0, mp.y - dragOffset.y - dragOrigin.y));
        draggedSinceMousePress = true;
        revalidate();
        repaint();
    }

    private void handleRelease(MouseEvent e) {
        // 中ボタン (パン) のリリースでは確定しない (bug-hunt round5 論点3、全8キャンバス共通)。
        if (endpointDrag.isActive() && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
            finishEndpointDrag(view.toModel(e.getPoint()));
            return;
        }
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

    /** Esc/モード切替時に端点ドラッグを繋ぎ替えずに中断する。 */
    private void cancelEndpointDrag() {
        endpointDrag.cancel();
        repaint();
    }

    /** 端点ドラッグを終える。クリック相当 (移動なし) や自ノードへの落下は no-op として弾き、
     * 実際に別ノード上へドラッグされたときだけ付け替える。 */
    private void finishEndpointDrag(Point mp) {
        DeployLink link = endpointDrag.item();
        boolean startEnd = endpointDrag.leftEnd();
        DeployNode target = DeploySketchLayout.hitTest(model.getNodes(), currentLayout(), mp);
        String targetId = target == null ? null : target.getId();
        String current = startEnd ? link.getFrom() : link.getTo();
        if (endpointDrag.finish(mp, targetId, current, view.zoom())) {
            reattach(link, startEnd, target);
        } else {
            repaint();
        }
    }

    /** リンクの端点 ({@code startEnd} true = from 側) を {@code target} へ付け替える。 */
    private void reattach(DeployLink link, boolean startEnd, DeployNode target) {
        if (startEnd) {
            link.setFrom(target.getId());
        } else {
            link.setTo(target.getId());
        }
        listener.modelEdited();
        repaint();
    }

    /** テスト用: 実際のドラッグ操作を経ずにリンク端点付け替えを直接実行する。 */
    void reattachForTest(DeployLink link, boolean startEnd, DeployNode target) {
        reattach(link, startEnd, target);
    }

    private void showPopup(MouseEvent e, DeployNode hit) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (hit != null) {
            addItem(menu, "sketch.depl.menu.edit", () -> listener.editNodeRequested(hit));
            addItem(menu, "sketch.depl.menu.delete", () -> {
                // round10: 削除される部分木 (コンテナなら子孫も消える) に、リンク追加モードで
                // 確定済みの始点が含まれるなら pending source を無効化する。放置すると削除済み
                // ノードを指し続け、次クリックで宙吊りリンク/幽霊ノードを生む (8キャンバス横断)。
                for (DeployNode n = linkSource; n != null; n = n.getParent()) {
                    if (n == hit) {
                        linkSource = null;
                        break;
                    }
                }
                model.removeNode(hit);
                selected = null;
                listener.modelEdited();
                repaint();
            });
            addLinkDeleteMenu(menu, hit);
            // 葉ノードでも子を追加できるようにする (bug-hunt round8 #1: addChildNode は
            // model.addChild 経由で parent.setContainer(true) を踏むので、コンテナ限定に
            // していると GUI から第1階層のネストを新規作成する導線が一切無かった)。
            final Point at = view.toModel(e.getPoint());
            addItem(menu, "sketch.depl.menu.addChildHere",
                    () -> addChildNode(DeployNode.Kind.NODE, hit, at));
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
        return DeploySketchLayout.hitTest(model.getNodes(), currentLayout(), p);
    }

    /** 全ノード (入れ子含む) の絶対矩形を求める (呼び出しごとに再計算する軽量な純関数)。 */
    private Map<DeployNode, Rectangle> currentLayout() {
        return DeploySketchLayout.compute(model.getNodes(), this::titleSize);
    }

    /** 全コンテナの論理 content 原点 (枠拡張前の (ax+PAD, ay+title.height))。子ドラッグ/
     * 子追加の相対座標変換は必ずこちらを使う (bug-hunt round5 論点1)。 */
    private Map<DeployNode, Point> currentContentOrigins() {
        return DeploySketchLayout.computeContentOrigins(model.getNodes(), this::titleSize);
    }

    /** ノードのタイトル (ステレオタイプ + 表示名) を収める最小サイズ。葉ノードの全体サイズにも使う。 */
    private Dimension titleSize(DeployNode n) {
        FontMetrics fm = getFontMetrics(getFont() != null ? getFont()
                : new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int textW = Math.max(fm.stringWidth(n.displayText()),
                fm.stringWidth(stereotype(n.getKind())));
        int w = Math.max(NODE_MIN_W, textW + 2 * PAD_X);
        return new Dimension(w, NODE_H);
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    @Override
    public Dimension getPreferredSize() {
        int w = 400;
        int h = 300;
        for (Rectangle r : currentLayout().values()) {
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
            Map<DeployNode, Rectangle> layout = currentLayout();
            // ノード (入れ子コンテナの不透明な枠塗りを含む) を先に描き、リンクは後から重ねる。
            // 逆順だと、入れ子子ノードへ繋がるリンク線がコンテナ枠の不透明塗りに隠れてしまう
            // (paintContainerFrame の fillRect が全域を覆うため。bug-hunt round7 #3)。
            // edgePoint は矩形の縁までしか線を伸ばさないため、非入れ子ノード同士のリンクの
            // 見た目はこの並び替えでも変わらない。
            for (DeployNode n : model.getNodes()) {
                paintNodeTree(g2, n, layout);
            }
            for (DeployLink l : model.getLinks()) {
                paintLink(g2, l, layout);
            }
            if (editable && linkMode == null) {
                // 選択/移動モードのみ端点ハンドルを見せる (リンク作成モード中は邪魔になる)。
                paintEndpointHandles(g2, layout);
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
            } else if (linkMode != null) {
                overlay.setColor(new Color(0x1565C0));
                overlay.drawString(Messages.get(linkSource == null
                        ? "sketch.depl.hint.pickSource"
                        : "sketch.depl.hint.pickTarget"), 8, 14);
            }
        } finally {
            overlay.dispose();
        }
    }

    /** 各リンクの端点に正方形ハンドルを描き、端点ドラッグ中ならラバーバンド線も添える。 */
    private void paintEndpointHandles(Graphics2D g2, Map<DeployNode, Rectangle> layout) {
        g2.setColor(new Color(0x1565C0));
        for (DeployLink link : model.getLinks()) {
            Point[] eps = DeploySketchLinkHandles.endpointsOf(model, link, layout);
            if (eps == null) {
                continue;
            }
            drawHandle(g2, eps[0]);
            drawHandle(g2, eps[1]);
        }
        Point cursor = endpointDrag.cursor();
        if (endpointDrag.isActive() && cursor != null) {
            // 固定側 (掴んでいない端点) のノードの縁からカーソルへラバーバンドを引く。
            // 他 7 キャンバスと同様、固定端はカーソル方向へ都度再計算する。固定した相手
            // (元の接続先) 向きの静的端点を使うと、離れた位置へドラッグしたとき線が固定端の
            // ノード縁からずれて浮く (bug-hunt round9 論点4: Deploy だけ再計算が抜けていた)。
            DeployLink dragging = endpointDrag.item();
            DeployNode fixed = model.findNode(
                    endpointDrag.leftEnd() ? dragging.getTo() : dragging.getFrom());
            Rectangle fr = fixed != null ? layout.get(fixed) : null;
            if (fr != null) {
                Point anchor = DeploySketchLinkHandles.edgePoint(fr, cursor);
                g2.drawLine(anchor.x, anchor.y, cursor.x, cursor.y);
            }
        }
    }

    private void drawHandle(Graphics2D g2, Point p) {
        int s = EndpointHitThreshold.handleSizeModel(DeploySketchLinkHandles.HANDLE_SIZE, view.zoom());
        g2.fillRect(p.x - s / 2, p.y - s / 2, s, s);
    }

    private static String stereotype(DeployNode.Kind kind) {
        return "«" + kind.keyword() + "»";
    }

    /** ノード 1 個 (コンテナならその枠 + 子孫全体) を再帰的に描く。 */
    private void paintNodeTree(Graphics2D g2, DeployNode n, Map<DeployNode, Rectangle> layout) {
        Rectangle r = layout.get(n);
        if (n.isContainer()) {
            paintContainerFrame(g2, n, r);
            for (DeployNode c : n.getChildren()) {
                paintNodeTree(g2, c, layout);
            }
        } else {
            paintNode(g2, n, r);
        }
    }

    /** コンテナの枠 (タイトル行 + 子ノード領域) を描く。子は呼び出し側が別途描く。 */
    private void paintContainerFrame(Graphics2D g2, DeployNode n, Rectangle r) {
        boolean sel = n == selected || n == linkSource;
        Color line = sel ? new Color(0x1565C0) : new Color(0x555555);
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        // 種別ごとの外形でコンテナ枠を描く。database/cloud/artifact などが子を持つと
        // ただの矩形に潰れて種別 (円柱/雲/成果物) が消えていた (bug-hunt round9 論点5)。
        // 子ノードは呼び出し側がこの外形の内側に別途描く。
        paintShape(g2, n.getKind(), r, line);
        Dimension title = titleSize(n);
        g2.setColor(line);
        g2.drawLine(r.x, r.y + title.height, r.x + r.width, r.y + title.height);
        g2.setStroke(new BasicStroke(1f));
        drawTitleText(g2, n, r, title.height);
    }

    private void drawTitleText(Graphics2D g2, DeployNode n, Rectangle r, int titleH) {
        FontMetrics fm = g2.getFontMetrics();
        int cx = r.x + r.width / 2;
        g2.setColor(new Color(0x607D8B));
        String st = stereotype(n.getKind());
        g2.drawString(st, cx - fm.stringWidth(st) / 2, r.y + 18);
        g2.setColor(new Color(0x1A1A1A));
        String name = n.displayText();
        int nameY = Math.min(r.y + 36, r.y + titleH - 6);
        g2.drawString(name, cx - fm.stringWidth(name) / 2, nameY);
    }

    private void paintNode(Graphics2D g2, DeployNode n, Rectangle r) {
        boolean sel = n == selected || n == linkSource;
        Color line = sel ? new Color(0x1565C0) : new Color(0x555555);
        g2.setStroke(new BasicStroke(sel ? 2f : 1f));
        paintShape(g2, n.getKind(), r, line);
        drawTitleText(g2, n, r, NODE_H);
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

    private void paintLink(Graphics2D g2, DeployLink link, Map<DeployNode, Rectangle> layout) {
        DeployNode from = model.findNode(link.getFrom());
        DeployNode to = model.findNode(link.getTo());
        if (from == null || to == null) {
            return;
        }
        Rectangle fr = layout.get(from);
        Rectangle tr = layout.get(to);
        if (from == to) {
            paintSelfLink(g2, fr, link);
            return;
        }
        Point pf = DeploySketchLinkHandles.edgePoint(fr, DeploySketchLinkHandles.center(tr));
        Point pt = DeploySketchLinkHandles.edgePoint(tr, DeploySketchLinkHandles.center(fr));
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
        Point[] loop = DeploySketchLinkHandles.selfLoopPoints(r);
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
        Map<DeployNode, Rectangle> layout = currentLayout();
        DeployLink best = null;
        double bestD = 7.0;
        for (DeployLink link : model.getLinks()) {
            DeployNode from = model.findNode(link.getFrom());
            DeployNode to = model.findNode(link.getTo());
            if (from == null || to == null) {
                continue;
            }
            Rectangle fr = layout.get(from);
            Rectangle tr = layout.get(to);
            double d = from == to
                    ? selfLoopDistance(fr, p)
                    : segmentDistance(fr, tr, p);
            if (d < bestD) {
                bestD = d;
                best = link;
            }
        }
        return best;
    }

    private static double segmentDistance(Rectangle from, Rectangle to, Point p) {
        Point pf = DeploySketchLinkHandles.edgePoint(from, DeploySketchLinkHandles.center(to));
        Point pt = DeploySketchLinkHandles.edgePoint(to, DeploySketchLinkHandles.center(from));
        return pointToSegment(p.x, p.y, pf.x, pf.y, pt.x, pt.y);
    }

    private static double selfLoopDistance(Rectangle r, Point p) {
        Point[] loop = DeploySketchLinkHandles.selfLoopPoints(r);
        double best = Double.MAX_VALUE;
        for (int i = 0; i < loop.length - 1; i++) {
            best = Math.min(best, pointToSegment(p.x, p.y,
                    loop[i].x, loop[i].y, loop[i + 1].x, loop[i + 1].y));
        }
        return best;
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

    /** テスト用: 現在の絶対レイアウト矩形 (入れ子含む)。 */
    Map<DeployNode, Rectangle> layoutForTest() {
        return currentLayout();
    }

    /** テスト用: 現在端点ドラッグ中のリンク (無ければ null)。 */
    DeployLink endpointDragLinkForTest() {
        return endpointDrag.item();
    }

    /** テスト用: ズーム倍率を直接設定する (Ctrl+ホイール相当)。 */
    void setZoomForTest(double z) {
        view.setZoom(z);
    }
}
