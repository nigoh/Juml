// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * アクティビティ図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>ノード列を上から下へのフローチャートとして自動レイアウトする (座標は持たず
 * 並び順から決定的に計算する)。IF ノードはひし形 + 左右のブランチ + 合流点で描く。
 * クリック選択・ダブルクリック編集・右クリックメニュー・Delete 削除を受け付け、
 * 並べ替えはメニューの「上へ/下へ」で行う。モデル変更は
 * {@link Listener#modelEdited()} で通知し、テキストへの反映は呼び出し側が行う。</p>
 */
final class ActivitySketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        /** 追加・削除・並べ替えなどモデルが変わった (テキスト再生成が必要)。 */
        void modelEdited();

        /** ノードの編集 (ダブルクリック / メニュー) が要求された。 */
        void editRequested(ActivityNode node);
    }

    private static final int TOP_Y = 28;
    private static final int V_GAP = 26;
    private static final int TERMINAL_D = 20;
    private static final int ACTION_H = 28;
    private static final int IF_H = 40;
    private static final int JOIN_W = 20;
    private static final int JOIN_H = 14;

    private ActivitySketchModel model = new ActivitySketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;

    private ActivityNode selected;
    /** ズーム (Ctrl+ホイール) と中ボタンパン。マウス座標は toModel で逆変換して使う。 */
    private final SketchViewport view = new SketchViewport(this);

    // relayout() が再計算するレイアウト結果。
    private final Map<ActivityNode, Rectangle> bounds = new LinkedHashMap<>();
    /** 接続線 {x1, y1, x2, y2, 終端矢印フラグ(1/0)}。 */
    private final List<int[]> edges = new ArrayList<>();
    /** ブランチラベル {テキスト, x, y}。 */
    private final List<Object[]> branchLabels = new ArrayList<>();
    /** 合流点ひし形の中心 {x, y}。 */
    private final List<int[]> joins = new ArrayList<>();
    private int extentX;
    private int extentY;

    ActivitySketchCanvas(Listener listener) {
        this.listener = listener;
        setBackground(Color.WHITE);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handlePress(e);
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    selected = nodeAt(view.toModel(e.getPoint()));
                    repaint();
                    showPopup(e);
                }
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !editable || selected == null
                        || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (selected.getKind() == ActivityNode.Kind.ACTION
                        || selected.getKind() == ActivityNode.Kind.IF) {
                    listener.editRequested(selected);
                }
            }
        };
        addMouseListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE && editable && selected != null) {
                    model.remove(selected);
                    selected = null;
                    listener.modelEdited();
                    revalidate();
                    repaint();
                }
            }
        });
    }

    /** 表示・編集対象のモデルを差し替える。 */
    void setModel(ActivitySketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selected = null;
        revalidate();
        repaint();
    }

    ActivitySketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    /** テスト用: 現在の選択ノード。 */
    ActivityNode selectedForTest() {
        return selected;
    }

    /** テスト用: 選択ノードを直接設定する (マウス press の代替)。 */
    void setSelectedForTest(ActivityNode node) {
        this.selected = node;
    }

    /**
     * ノードを追加する: 選択があればその直後、無ければ最上位の末尾。
     * 追加後は新ノードを選択状態にする (連続追加しやすくするため)。
     */
    void addNode(ActivityNode node) {
        if (!editable) {
            return;
        }
        model.insertAfter(selected, node);
        selected = node;
        listener.modelEdited();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // マウス操作
    // -------------------------------------------------------------------------

    private void handlePress(MouseEvent e) {
        if (!editable) {
            return;
        }
        // 中ボタンはパン (SketchViewport) 専用。選択として扱わない。
        if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
            return;
        }
        selected = nodeAt(view.toModel(e.getPoint()));
        repaint();
        if (e.isPopupTrigger()) {
            showPopup(e);
        }
    }

    private ActivityNode nodeAt(Point p) {
        relayout();
        ActivityNode hit = null;
        for (Map.Entry<ActivityNode, Rectangle> entry : bounds.entrySet()) {
            if (entry.getValue().contains(p)) {
                hit = entry.getKey();
            }
        }
        return hit;
    }

    private void showPopup(MouseEvent e) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        ActivityNode hit = selected;
        if (hit != null) {
            if (hit.getKind() == ActivityNode.Kind.ACTION
                    || hit.getKind() == ActivityNode.Kind.IF) {
                JMenuItem edit = new JMenuItem(Messages.get("sketch.act.menu.edit"));
                edit.addActionListener(a -> listener.editRequested(hit));
                menu.add(edit);
            }
            JMenuItem del = new JMenuItem(Messages.get("sketch.act.menu.delete"));
            del.addActionListener(a -> {
                model.remove(hit);
                selected = null;
                fireEdited();
            });
            menu.add(del);
            menu.addSeparator();
            JMenuItem up = new JMenuItem(Messages.get("sketch.act.menu.moveUp"));
            up.addActionListener(a -> {
                model.move(hit, -1);
                fireEdited();
            });
            menu.add(up);
            JMenuItem down = new JMenuItem(Messages.get("sketch.act.menu.moveDown"));
            down.addActionListener(a -> {
                model.move(hit, 1);
                fireEdited();
            });
            menu.add(down);
            menu.addSeparator();
            JMenuItem addAfter = new JMenuItem(Messages.get("sketch.act.menu.addActionAfter"));
            addAfter.addActionListener(a -> {
                model.insertAfter(hit, ActivityNode.action("Action"));
                fireEdited();
            });
            menu.add(addAfter);
            if (hit.getKind() == ActivityNode.Kind.IF) {
                JMenuItem toThen = new JMenuItem(Messages.get("sketch.act.menu.addToThen"));
                toThen.addActionListener(a -> {
                    hit.getThenBranch().add(ActivityNode.action("Action"));
                    fireEdited();
                });
                menu.add(toThen);
                JMenuItem toElse = new JMenuItem(Messages.get("sketch.act.menu.addToElse"));
                toElse.addActionListener(a -> {
                    hit.ensureElseBranch().add(ActivityNode.action("Action"));
                    fireEdited();
                });
                menu.add(toElse);
            }
        } else {
            JMenuItem addAction = new JMenuItem(Messages.get("sketch.act.menu.addAction"));
            addAction.addActionListener(a -> addNode(ActivityNode.action("Action")));
            menu.add(addAction);
            JMenuItem addIf = new JMenuItem(Messages.get("sketch.act.menu.addIf"));
            addIf.addActionListener(a -> {
                ActivityNode n = ActivityNode.branch("condition?", "yes", "no");
                n.ensureElseBranch();
                addNode(n);
            });
            menu.add(addIf);
        }
        menu.show(this, e.getX(), e.getY());
    }

    private void fireEdited() {
        listener.modelEdited();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // レイアウト (並び順から決定的に計算)
    // -------------------------------------------------------------------------

    private FontMetrics metrics() {
        return getFontMetrics(getFont() != null ? getFont()
                : new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
    }

    private int nodeWidth(ActivityNode n) {
        FontMetrics fm = metrics();
        switch (n.getKind()) {
            case ACTION:
                return Math.max(80, fm.stringWidth(n.getText()) + 28);
            case IF:
                return Math.max(72, fm.stringWidth(n.getCondition()) + 36);
            default:
                return TERMINAL_D;
        }
    }

    /** ブランチ (ノード列) の占有幅 (IF の左右の広がりも含む)。 */
    private int blockWidth(List<ActivityNode> nodes) {
        int w = 60;
        for (ActivityNode n : nodes) {
            if (n.getKind() == ActivityNode.Kind.IF) {
                int spreadL = branchSpread(n.getThenBranch());
                int spreadR = branchSpread(n.getElseBranch());
                w = Math.max(w, 2 * Math.max(spreadL, spreadR)
                        + Math.max(blockWidth(n.getThenBranch()),
                                n.getElseBranch() != null ? blockWidth(n.getElseBranch()) : 0));
            } else {
                w = Math.max(w, nodeWidth(n));
            }
        }
        return w;
    }

    /** IF 中心からブランチ中心までの横距離。 */
    private int branchSpread(List<ActivityNode> branch) {
        int bw = branch == null || branch.isEmpty() ? 40 : blockWidth(branch);
        return bw / 2 + 36;
    }

    /** レイアウト結果 (bounds / edges / labels / joins) を再計算する。 */
    private void relayout() {
        bounds.clear();
        edges.clear();
        branchLabels.clear();
        joins.clear();
        extentX = 360;
        extentY = 240;
        int cx = Math.max(180, blockWidth(model.getNodes()) / 2 + 48);
        layoutBlock(model.getNodes(), cx, TOP_Y);
    }

    /**
     * ノード列を {@code cx} を中心軸として {@code y} から下へ配置する。
     * 連続するノードは縦の矢印で接続する。
     *
     * @return ブロック下端の Y 座標
     */
    private int layoutBlock(List<ActivityNode> nodes, int cx, int y) {
        int prevBottom = -1;
        for (ActivityNode n : nodes) {
            if (prevBottom >= 0) {
                edges.add(new int[]{cx, prevBottom, cx, y, 1});
            }
            prevBottom = placeNode(n, cx, y);
            y = prevBottom + V_GAP;
        }
        return prevBottom >= 0 ? prevBottom : y - V_GAP;
    }

    /** ノード 1 個を配置し、下端の Y 座標を返す。 */
    private int placeNode(ActivityNode n, int cx, int y) {
        int w = nodeWidth(n);
        switch (n.getKind()) {
            case START:
            case STOP:
            case END:
                setBounds(n, new Rectangle(cx - TERMINAL_D / 2, y, TERMINAL_D, TERMINAL_D));
                return y + TERMINAL_D;
            case ACTION:
                setBounds(n, new Rectangle(cx - w / 2, y, w, ACTION_H));
                return y + ACTION_H;
            default:
                return placeIf(n, cx, y, w);
        }
    }

    private void setBounds(ActivityNode n, Rectangle r) {
        bounds.put(n, r);
        extentX = Math.max(extentX, r.x + r.width + 60);
        extentY = Math.max(extentY, r.y + r.height + 60);
    }

    /** IF ノード: ひし形 + 左 (then) / 右 (else) ブランチ + 合流点。 */
    private int placeIf(ActivityNode n, int cx, int y, int w) {
        Rectangle diamond = new Rectangle(cx - w / 2, y, w, IF_H);
        setBounds(n, diamond);
        int midY = y + IF_H / 2;
        int thenCX = cx - branchSpread(n.getThenBranch());
        int elseCX = cx + branchSpread(n.getElseBranch());
        int branchTop = y + IF_H + V_GAP;
        FontMetrics fm = metrics();

        // then 側: ひし形左頂点 → 横 → 下ろしてブランチへ。
        edges.add(new int[]{cx - w / 2, midY, thenCX, midY, 0});
        edges.add(new int[]{thenCX, midY, thenCX, branchTop, 1});
        if (n.getThenLabel() != null) {
            branchLabels.add(new Object[]{n.getThenLabel(),
                    thenCX - fm.stringWidth(n.getThenLabel()) - 6, midY - 4});
        }
        int thenBottom = n.getThenBranch().isEmpty() ? branchTop
                : layoutBlock(n.getThenBranch(), thenCX, branchTop);

        // else 側: else 節が無くてもバイパス線は描く (合流までの素通り)。
        edges.add(new int[]{cx + w / 2, midY, elseCX, midY, 0});
        int elseBottom = branchTop;
        if (n.getElseBranch() != null && !n.getElseBranch().isEmpty()) {
            edges.add(new int[]{elseCX, midY, elseCX, branchTop, 1});
            elseBottom = layoutBlock(n.getElseBranch(), elseCX, branchTop);
        }
        if (n.getElseLabel() != null) {
            branchLabels.add(new Object[]{n.getElseLabel(), elseCX + 6, midY - 4});
        }

        int joinY = Math.max(thenBottom, elseBottom) + V_GAP;
        // 各ブランチ下端 → 下ろして合流点の高さ → 合流ひし形へ。
        edges.add(new int[]{thenCX, thenBottom, thenCX, joinY + JOIN_H / 2, 0});
        edges.add(new int[]{thenCX, joinY + JOIN_H / 2, cx - JOIN_W / 2, joinY + JOIN_H / 2, 1});
        int elseFrom = (n.getElseBranch() != null && !n.getElseBranch().isEmpty())
                ? elseBottom : midY;
        edges.add(new int[]{elseCX, elseFrom, elseCX, joinY + JOIN_H / 2, 0});
        edges.add(new int[]{elseCX, joinY + JOIN_H / 2, cx + JOIN_W / 2, joinY + JOIN_H / 2, 1});
        joins.add(new int[]{cx, joinY + JOIN_H / 2});
        extentY = Math.max(extentY, joinY + JOIN_H + 60);
        return joinY + JOIN_H;
    }

    @Override
    public Dimension getPreferredSize() {
        relayout();
        return view.scaled(new Dimension(extentX, extentY));
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        relayout();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            view.applyTransform(g2);
            g2.setColor(new Color(0x37474F));
            g2.setStroke(new BasicStroke(1.2f));
            for (int[] e : edges) {
                g2.drawLine(e[0], e[1], e[2], e[3]);
                if (e[4] == 1) {
                    paintArrowHead(g2, new Point(e[2], e[3]), new Point(e[0], e[1]));
                }
            }
            for (int[] j : joins) {
                paintDiamondShape(g2, j[0], j[1], JOIN_W, JOIN_H, new Color(0xFFFBE6), false);
            }
            g2.setColor(new Color(0x555555));
            for (Object[] l : branchLabels) {
                g2.drawString((String) l[0], (Integer) l[1], (Integer) l[2]);
            }
            for (Map.Entry<ActivityNode, Rectangle> entry : bounds.entrySet()) {
                paintNode(g2, entry.getKey(), entry.getValue());
            }
        } finally {
            g2.dispose();
        }
        // バナーはズームに依らず読める大きさで描く (スケール適用外)。
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

    private void paintNode(Graphics2D g2, ActivityNode n, Rectangle r) {
        boolean isSel = n == selected;
        Color border = isSel ? new Color(0x1565C0) : new Color(0x555555);
        g2.setStroke(new BasicStroke(isSel ? 2f : 1.2f));
        FontMetrics fm = g2.getFontMetrics();
        switch (n.getKind()) {
            case START:
                g2.setColor(isSel ? new Color(0x1565C0) : new Color(0x212121));
                g2.fillOval(r.x, r.y, r.width, r.height);
                break;
            case STOP:
            case END:
                g2.setColor(getBackground());
                g2.fillOval(r.x, r.y, r.width, r.height);
                g2.setColor(border);
                g2.drawOval(r.x, r.y, r.width, r.height);
                if (n.getKind() == ActivityNode.Kind.STOP) {
                    g2.setColor(isSel ? new Color(0x1565C0) : new Color(0x212121));
                    g2.fillOval(r.x + 4, r.y + 4, r.width - 8, r.height - 8);
                } else {
                    g2.drawLine(r.x + 5, r.y + 5, r.x + r.width - 5, r.y + r.height - 5);
                    g2.drawLine(r.x + r.width - 5, r.y + 5, r.x + 5, r.y + r.height - 5);
                }
                break;
            case ACTION:
                g2.setColor(new Color(0xFFFBE6));
                g2.fillRoundRect(r.x, r.y, r.width, r.height, 14, 14);
                g2.setColor(border);
                g2.drawRoundRect(r.x, r.y, r.width, r.height, 14, 14);
                g2.drawString(n.getText(),
                        r.x + (r.width - fm.stringWidth(n.getText())) / 2,
                        r.y + r.height / 2 + fm.getAscent() / 2 - 2);
                break;
            default:
                paintDiamondShape(g2, r.x + r.width / 2, r.y + r.height / 2,
                        r.width, r.height, new Color(0xFFFBE6), isSel);
                g2.setColor(border);
                g2.drawString(n.getCondition(),
                        r.x + (r.width - fm.stringWidth(n.getCondition())) / 2,
                        r.y + r.height / 2 + fm.getAscent() / 2 - 2);
                break;
        }
    }

    /** 中心 (cx, cy)・幅 w・高さ h のひし形。 */
    private void paintDiamondShape(Graphics2D g2, int cx, int cy, int w, int h,
                                   Color fill, boolean isSel) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx - w / 2.0, cy);
        p.lineTo(cx, cy - h / 2.0);
        p.lineTo(cx + w / 2.0, cy);
        p.lineTo(cx, cy + h / 2.0);
        p.closePath();
        g2.setColor(fill);
        g2.fill(p);
        g2.setColor(isSel ? new Color(0x1565C0) : new Color(0x555555));
        g2.setStroke(new BasicStroke(isSel ? 2f : 1.2f));
        g2.draw(p);
    }

    private void paintArrowHead(Graphics2D g2, Point at, Point from) {
        double dx = from.x - at.x;
        double dy = from.y - at.y;
        double d = Math.max(1e-6, Math.hypot(dx, dy));
        double ux = dx / d;
        double uy = dy / d;
        int len = 9;
        int wid = 5;
        Path2D p = new Path2D.Double();
        p.moveTo(at.x, at.y);
        p.lineTo(at.x + ux * len - uy * wid, at.y + uy * len + ux * wid);
        p.lineTo(at.x + ux * len + uy * wid, at.y + uy * len - ux * wid);
        p.closePath();
        g2.fill(p);
    }
}
