// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
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
import java.util.Map;

/**
 * マインドマップデザイナーの描画・マウス操作キャンバス。
 *
 * <p>ルートを中心に左右へ枝を伸ばす木として自動レイアウトする (座標は持たず並び順・
 * 左右指定から決定的に計算する。{@link MindmapSketchLayout})。クリック選択・
 * ダブルクリック編集・右クリックメニュー・Delete 削除に加え、<b>ノードをドラッグして
 * 別ノードの子へ付け替える (reparent)</b> 操作を受け付ける。付け替えは
 * {@link EndpointDragSession} (名前ベースの関係端点用) とは型が合わないため流用せず、
 * 軽量な drag 状態 ({@link #dragging} / {@link #cursor}) をこのクラス内に持つ。クリック
 * 相当 (実質移動なし) の判定だけは {@link EndpointDragSession#CLICK_THRESHOLD_PX} と同じ
 * 定数値を参照する (クラスは複製しない)。</p>
 *
 * <p>キャンバスの AUTO の左右バランスは製品側の見せ方であり、PlantUML の実描画
 * (AUTO は実際には全て右へ縦積み) とは意図的に乖離させている (他 8 キャンバスも簡易表現)。
 * この乖離は仕様であり、後日「バグ」として直さないこと。</p>
 */
final class MindmapSketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        /** 追加・削除・並べ替え・付け替えなどモデルが変わった (テキスト再生成が必要)。 */
        void modelEdited();

        /** ノードの編集 (ダブルクリック / メニュー) が要求された。 */
        void editRequested(MindmapNode node);

        /** 空図でルートの新規追加 (ダブルクリック / メニュー) が要求された。 */
        default void addRootRequested(Point at) {
        }
    }

    private static final String DEFAULT_TEXT = "Idea";
    private static final int NODE_H = 26;
    private static final int PAD_X = 12;
    private static final int MIN_W = 64;

    private MindmapSketchModel model = new MindmapSketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;

    private MindmapNode selected;
    /** ズーム (Ctrl+ホイール) と中ボタンパン。マウス座標は toModel で逆変換して使う。 */
    private final SketchViewport view = new SketchViewport(this);

    /** reparent ドラッグ中に掴んでいるノード (非ドラッグ時 null。ルートは掴まない)。 */
    private MindmapNode dragging;
    /** ドラッグ開始位置とラバーバンド先端 (いずれもモデル座標)。 */
    private Point pressPoint;
    private Point cursor;

    private int extentX = 360;
    private int extentY = 240;

    MindmapSketchCanvas(Listener listener) {
        this.listener = listener;
        setBackground(Color.WHITE);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (!editable || SwingUtilities.isMiddleMouseButton(e)) {
                    return;
                }
                Point mp = view.toModel(e.getPoint());
                if (e.isPopupTrigger()) {
                    selected = nodeAt(mp);
                    repaint();
                    showPopup(e.getPoint(), mp);
                    return;
                }
                onPress(mp);
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (editable && dragging != null) {
                    onDrag(view.toModel(e.getPoint()));
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (!editable) {
                    return;
                }
                Point mp = view.toModel(e.getPoint());
                if (e.isPopupTrigger()) {
                    selected = nodeAt(mp);
                    cancelDrag();
                    repaint();
                    showPopup(e.getPoint(), mp);
                    return;
                }
                if (dragging != null) {
                    onRelease(mp);
                }
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !editable
                        || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                Point mp = view.toModel(e.getPoint());
                MindmapNode hit = nodeAt(mp);
                if (hit != null) {
                    listener.editRequested(hit);
                } else if (model.getRoot() == null) {
                    listener.addRootRequested(mp);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (!editable) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && dragging != null) {
                    cancelDrag();
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE && selected != null) {
                    deleteSelected();
                }
            }
        });
    }

    /** 表示・編集対象のモデルを差し替える。 */
    void setModel(MindmapSketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        cancelDrag();
        revalidate();
        repaint();
    }

    MindmapSketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    // -------------------------------------------------------------------------
    // 編集操作
    // -------------------------------------------------------------------------

    /**
     * 子ノードを追加する。{@code parent==null} かつ空図ならルートを新規作成し、ルートが
     * あれば ({@code parent==null} でも) ルート直下へ追加する。追加後は新ノードを選択する。
     */
    void addChild(MindmapNode parent) {
        if (!editable) {
            return;
        }
        MindmapNode child = new MindmapNode(DEFAULT_TEXT);
        if (parent == null) {
            if (model.getRoot() == null) {
                model.setRoot(child);
            } else {
                model.addChild(model.getRoot(), child);
            }
        } else {
            model.addChild(parent, child);
        }
        selected = child;
        fireEdited();
    }

    /** {@code after} の直後へ兄弟ノードを追加する ({@code after} がルートなら何もしない)。 */
    void addSibling(MindmapNode after) {
        if (!editable || after == null) {
            return;
        }
        MindmapNode parent = after.getParent();
        if (parent == null) {
            return;
        }
        MindmapNode sib = new MindmapNode(DEFAULT_TEXT);
        model.addChild(parent, sib);
        List<MindmapNode> siblings = parent.getChildren();
        siblings.remove(sib);
        siblings.add(siblings.indexOf(after) + 1, sib);
        selected = sib;
        fireEdited();
    }

    /** 選択ノードを削除する (ルートなら空図にする)。 */
    void deleteSelected() {
        if (!editable || selected == null) {
            return;
        }
        if (selected == model.getRoot()) {
            model.setRoot(null);
        } else {
            model.remove(selected);
        }
        selected = null;
        fireEdited();
    }

    /** 選択ノードの左右指定を変更する。 */
    void setSideOfSelected(MindmapNode.Side side) {
        if (!editable || selected == null) {
            return;
        }
        selected.setOwnSide(side);
        fireEdited();
    }

    private void fireEdited() {
        listener.modelEdited();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // reparent ドラッグ (モデル座標で駆動。テストシームも同じ経路を通す)
    // -------------------------------------------------------------------------

    private void onPress(Point p) {
        MindmapNode hit = nodeAt(p);
        selected = hit;
        pressPoint = p;
        cursor = p;
        // ルートは付け替え不可 (選択のみ)。それ以外は掴んで reparent 候補にする。
        dragging = (hit != null && hit != model.getRoot()) ? hit : null;
        repaint();
    }

    private void onDrag(Point p) {
        cursor = p;
        repaint();
    }

    /**
     * リリース: press からの移動がしきい値未満ならクリック相当で付け替えない。移動があり
     * ドロップ先が有効 (自分でも子孫でもない) なら {@code reparent} して通知する。
     *
     * @return 実際に付け替えたら true
     */
    private boolean onRelease(Point p) {
        boolean moved = pressPoint != null
                && p.distance(pressPoint) >= EndpointDragSession.CLICK_THRESHOLD_PX;
        MindmapNode target = moved ? nodeAt(p) : null;
        boolean reattached = false;
        if (moved && target != null && target != dragging
                && model.reparent(dragging, target, -1)) {
            selected = dragging;
            reattached = true;
        }
        dragging = null;
        pressPoint = null;
        cursor = null;
        if (reattached) {
            fireEdited();
        } else {
            repaint();
        }
        return reattached;
    }

    private void cancelDrag() {
        dragging = null;
        pressPoint = null;
        cursor = null;
        repaint();
    }

    private void showPopup(Point screenAt, Point modelAt) {
        JPopupMenu menu = new JPopupMenu();
        if (model.getRoot() == null) {
            JMenuItem addRoot = new JMenuItem(Messages.get("sketch.mm.menu.addRootHere"));
            addRoot.addActionListener(a -> listener.addRootRequested(modelAt));
            menu.add(addRoot);
        } else if (selected != null) {
            MindmapNode hit = selected;
            JMenuItem edit = new JMenuItem(Messages.get("sketch.mm.menu.edit"));
            edit.addActionListener(a -> listener.editRequested(hit));
            menu.add(edit);
            JMenuItem addChild = new JMenuItem(Messages.get("sketch.mm.menu.addChild"));
            addChild.addActionListener(a -> addChild(hit));
            menu.add(addChild);
            if (hit != model.getRoot()) {
                JMenuItem addSibling = new JMenuItem(Messages.get("sketch.mm.menu.addSibling"));
                addSibling.addActionListener(a -> addSibling(hit));
                menu.add(addSibling);
            }
            menu.addSeparator();
            JMenuItem del = new JMenuItem(Messages.get("sketch.mm.menu.delete"));
            del.addActionListener(a -> deleteSelected());
            menu.add(del);
        }
        if (menu.getComponentCount() > 0) {
            menu.show(this, screenAt.x, screenAt.y);
        }
    }

    // -------------------------------------------------------------------------
    // レイアウト
    // -------------------------------------------------------------------------

    private FontMetrics metrics() {
        return getFontMetrics(getFont() != null ? getFont()
                : new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
    }

    private Dimension sizeOf(MindmapNode n) {
        int w = Math.max(MIN_W, metrics().stringWidth(n.getText()) + PAD_X * 2);
        return new Dimension(w, NODE_H);
    }

    private MindmapSketchLayout.Result computeLayout() {
        MindmapSketchLayout.Result r = MindmapSketchLayout.compute(model.getRoot(), this::sizeOf);
        extentX = 360;
        extentY = 240;
        for (Rectangle rect : r.bounds.values()) {
            extentX = Math.max(extentX, rect.x + rect.width + 60);
            extentY = Math.max(extentY, rect.y + rect.height + 60);
        }
        return r;
    }

    private MindmapNode nodeAt(Point p) {
        return MindmapSketchLayout.hitTest(computeLayout(), p);
    }

    @Override
    public Dimension getPreferredSize() {
        computeLayout();
        return view.scaled(new Dimension(extentX, extentY));
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        MindmapSketchLayout.Result r = computeLayout();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            view.applyTransform(g2);
            paintEdges(g2, r);
            for (Map.Entry<MindmapNode, Rectangle> e : r.bounds.entrySet()) {
                paintNode(g2, e.getKey(), e.getValue());
            }
            paintDragOverlay(g2, r);
        } finally {
            g2.dispose();
        }
        if (!editable) {
            Graphics2D overlay = (Graphics2D) g.create();
            try {
                overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                SketchBanner.paint(overlay, this, unsupported);
            } finally {
                overlay.dispose();
            }
        }
    }

    private void paintEdges(Graphics2D g2, MindmapSketchLayout.Result r) {
        g2.setColor(new Color(0x90A4AE));
        g2.setStroke(new BasicStroke(1.4f));
        for (Map.Entry<MindmapNode, Rectangle> e : r.bounds.entrySet()) {
            MindmapNode n = e.getKey();
            MindmapNode parent = n.getParent();
            if (parent == null) {
                continue;
            }
            Rectangle pr = r.bounds.get(parent);
            Rectangle cr = e.getValue();
            if (pr == null) {
                continue;
            }
            boolean right = Boolean.TRUE.equals(r.onRight.get(n));
            int px = right ? pr.x + pr.width : pr.x;
            int cx = right ? cr.x : cr.x + cr.width;
            g2.drawLine(px, pr.y + pr.height / 2, cx, cr.y + cr.height / 2);
        }
    }

    private void paintNode(Graphics2D g2, MindmapNode n, Rectangle r) {
        boolean isSel = n == selected;
        boolean isRoot = n == model.getRoot();
        g2.setColor(isRoot ? new Color(0xE3F2FD) : new Color(0xFFFBE6));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        g2.setColor(isSel ? new Color(0x1565C0) : new Color(0x555555));
        g2.setStroke(new BasicStroke(isSel ? 2f : 1.2f));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(new Color(0x212121));
        g2.drawString(n.getText(), r.x + (r.width - fm.stringWidth(n.getText())) / 2,
                r.y + r.height / 2 + fm.getAscent() / 2 - 2);
    }

    /** ドラッグ中: 掴んだノードからカーソルへラバーバンド線 + ドロップ先ハイライト。 */
    private void paintDragOverlay(Graphics2D g2, MindmapSketchLayout.Result r) {
        if (dragging == null || cursor == null) {
            return;
        }
        Rectangle from = r.bounds.get(dragging);
        if (from == null) {
            return;
        }
        g2.setColor(new Color(0x1565C0));
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawLine(from.x + from.width / 2, from.y + from.height / 2, cursor.x, cursor.y);
        MindmapNode drop = MindmapSketchLayout.hitTest(r, cursor);
        if (drop != null && drop != dragging && !isAncestorOrSame(dragging, drop)) {
            Rectangle dr = r.bounds.get(drop);
            if (dr != null) {
                g2.setColor(new Color(0x1565C0));
                g2.setStroke(new BasicStroke(2.4f));
                g2.drawRoundRect(dr.x - 3, dr.y - 3, dr.width + 6, dr.height + 6, 16, 16);
            }
        }
    }

    private static boolean isAncestorOrSame(MindmapNode maybeAncestor, MindmapNode node) {
        for (MindmapNode cur = node; cur != null; cur = cur.getParent()) {
            if (cur == maybeAncestor) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // テストシーム (他キャンバス慣習に合わせた命名)
    // -------------------------------------------------------------------------

    /** テスト用: 現在の選択ノード。 */
    MindmapNode selectedForTest() {
        return selected;
    }

    /** テスト用: 選択ノードを直接設定する (マウス press の代替)。 */
    void setSelectedForTest(MindmapNode node) {
        this.selected = node;
    }

    /** テスト用: 現在ズーム。 */
    double zoomForTest() {
        return view.zoom();
    }

    /** テスト用: ズームを設定する。 */
    void setZoomForTest(double z) {
        view.setZoom(z);
    }

    /** テスト用: 現在のレイアウト結果 (矩形は press/reparent の座標特定に使う)。 */
    MindmapSketchLayout.Result layoutForTest() {
        return computeLayout();
    }

    /** テスト用: reparent ドラッグ中に掴んでいるノード (非ドラッグ時 null)。 */
    MindmapNode draggingForTest() {
        return dragging;
    }

    /** テスト用: モデル座標での press。 */
    void pressForTest(Point p) {
        onPress(p);
    }

    /** テスト用: モデル座標での drag 移動。 */
    void dragForTest(Point p) {
        onDrag(p);
    }

    /** テスト用: モデル座標での release (付け替えたら true)。 */
    boolean releaseForTest(Point p) {
        return onRelease(p);
    }

    /** テスト用: Esc 相当のドラッグ中断。 */
    void cancelDragForTest() {
        cancelDrag();
    }
}
