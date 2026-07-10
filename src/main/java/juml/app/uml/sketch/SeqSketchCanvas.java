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
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * シーケンス図デザイナーの描画・マウス操作キャンバス。
 *
 * <p>参加者をライフライン (縦の破線) として横に並べ、メッセージを上から時系列順の
 * 水平矢印で描く。ドラッグでメッセージの並べ替え (縦) と参加者の並べ替え (横)、
 * ダブルクリック編集・右クリックメニュー・メッセージの 2 クリック追加を受け付ける。
 * モデル変更は {@link Listener#modelEdited()} で通知し、テキストへの反映
 * (PlantUML 再生成) は呼び出し側が行う。</p>
 */
final class SeqSketchCanvas extends JPanel {

    /** キャンバス操作の通知先。 */
    interface Listener {
        /** 並べ替え・削除・メッセージ追加などモデルが変わった (テキスト再生成が必要)。 */
        void modelEdited();

        /** メッセージの編集 (ダブルクリック / メニュー) が要求された。 */
        void editMessageRequested(SeqItem message);

        /** 参加者の編集 (ダブルクリック / メニュー) が要求された。 */
        void editParticipantRequested(SeqParticipant participant);

        /** Esc 等でメッセージ追加モードが取り消された (ツールバーのモード表示を戻すため)。 */
        default void messageModeCancelled() { }
    }

    private static final int MARGIN_X = 32;
    private static final int HEAD_TOP = 12;
    private static final int HEAD_H = 48;
    private static final int COL_MIN_W = 120;
    private static final int ROW_H = 38;
    private static final int FIRST_ROW_GAP = 30;
    private static final int BAR_W = 8;

    private SeqSketchModel model = new SeqSketchModel();
    private boolean editable;
    private List<String> unsupported = List.of();
    private final Listener listener;

    private SeqItem selectedItem;
    private SeqParticipant selectedParticipant;
    /** メッセージ追加モードの矢印種別 (null = 選択/移動モード)。 */
    private SeqItem.Arrow messageMode;
    /** メッセージ追加モードで最初にクリックした始点参加者。 */
    private SeqParticipant messageSource;
    /** ドラッグ中のマウス位置 (並べ替えのゴースト描画用。null = ドラッグ中でない)。 */
    private Point dragPoint;
    private boolean draggedSinceMousePress;

    SeqSketchCanvas(Listener listener) {
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
                if (e.getClickCount() != 2 || !editable || messageMode != null) {
                    return;
                }
                if (selectedItem != null) {
                    listener.editMessageRequested(selectedItem);
                } else if (selectedParticipant != null) {
                    listener.editParticipantRequested(selectedParticipant);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE && editable && messageMode == null) {
                    deleteSelection();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && messageMode != null) {
                    setMessageMode(null);
                    listener.messageModeCancelled();
                }
            }
        });
    }

    /** 表示・編集対象のモデルを差し替える。 */
    void setModel(SeqSketchModel model, boolean editable, List<String> unsupported) {
        this.model = model;
        this.editable = editable;
        this.unsupported = unsupported != null ? unsupported : List.of();
        this.selectedItem = null;
        this.selectedParticipant = null;
        this.messageSource = null;
        revalidate();
        repaint();
    }

    SeqSketchModel model() {
        return model;
    }

    boolean isModelEditable() {
        return editable;
    }

    /** メッセージ追加モードを切り替える (null で選択/移動モードへ戻す)。 */
    void setMessageMode(SeqItem.Arrow arrow) {
        this.messageMode = arrow;
        this.messageSource = null;
        // モード切替時に旧選択をクリアする (Delete / ダブルクリックの誤爆防止)。
        this.selectedItem = null;
        this.selectedParticipant = null;
        repaint();
    }

    /** 選択中のメッセージ or 参加者を削除する。 */
    private void deleteSelection() {
        if (selectedItem != null) {
            model.getItems().remove(selectedItem);
            selectedItem = null;
        } else if (selectedParticipant != null) {
            model.removeParticipant(selectedParticipant);
            selectedParticipant = null;
        } else {
            return;
        }
        listener.modelEdited();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // レイアウト計算 (並び順から決定的に算出。座標は保持しない)
    // -------------------------------------------------------------------------

    /** 参加者ごとの列幅 (名前幅から算出)。 */
    private int colWidth(SeqParticipant p) {
        FontMetrics fm = getFontMetrics(getFont());
        return Math.max(COL_MIN_W, fm.stringWidth(p.getName()) + 36);
    }

    /** 各参加者のライフライン中心 X 座標 (participants のリスト順)。 */
    private int[] centers() {
        List<SeqParticipant> ps = model.getParticipants();
        int[] xs = new int[ps.size()];
        int x = MARGIN_X;
        for (int i = 0; i < ps.size(); i++) {
            int w = colWidth(ps.get(i));
            xs[i] = x + w / 2;
            x += w;
        }
        return xs;
    }

    private int firstRowY() {
        return HEAD_TOP + HEAD_H + FIRST_ROW_GAP;
    }

    /** メッセージ項目の行 Y 座標 (時系列順の行番号から算出)。 */
    private int rowYOf(SeqItem target) {
        int row = 0;
        for (SeqItem m : model.getItems()) {
            if (m == target) {
                break;
            }
            if (m.getKind() == SeqItem.Kind.MESSAGE) {
                row++;
            }
        }
        return firstRowY() + row * ROW_H;
    }

    private int messageCount() {
        int n = 0;
        for (SeqItem m : model.getItems()) {
            if (m.getKind() == SeqItem.Kind.MESSAGE) {
                n++;
            }
        }
        return n;
    }

    private int bottomY() {
        return firstRowY() + Math.max(1, messageCount()) * ROW_H;
    }

    /** 参加者ヘッダーの矩形。 */
    private Rectangle headerBounds(int index) {
        int[] xs = centers();
        SeqParticipant p = model.getParticipants().get(index);
        int w = colWidth(p) - 12;
        return new Rectangle(xs[index] - w / 2, HEAD_TOP, w, HEAD_H);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = MARGIN_X * 2;
        for (SeqParticipant p : model.getParticipants()) {
            w += colWidth(p);
        }
        return new Dimension(Math.max(400, w), Math.max(300, bottomY() + 60));
    }

    // -------------------------------------------------------------------------
    // ヒットテスト
    // -------------------------------------------------------------------------

    private SeqParticipant participantAt(Point p) {
        for (int i = 0; i < model.getParticipants().size(); i++) {
            if (headerBounds(i).contains(p)) {
                return model.getParticipants().get(i);
            }
        }
        return null;
    }

    /** 指定 X に最も近いライフラインの参加者 (メッセージ追加・並べ替えの対象)。 */
    private SeqParticipant columnAt(int x) {
        int[] xs = centers();
        SeqParticipant best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            int d = Math.abs(xs[i] - x);
            if (d < bestD) {
                bestD = d;
                best = model.getParticipants().get(i);
            }
        }
        return best;
    }

    private SeqItem messageAt(Point p) {
        int[] xs = centers();
        for (SeqItem m : model.getItems()) {
            if (m.getKind() != SeqItem.Kind.MESSAGE) {
                continue;
            }
            int y = rowYOf(m);
            if (Math.abs(p.y - y) > ROW_H / 3) {
                continue;
            }
            int x1 = centerOf(xs, m.getFrom());
            int x2 = centerOf(xs, m.getTo());
            int lo = Math.min(x1, x2) - 10;
            int hi = m.getFrom().equals(m.getTo()) ? x1 + 60 : Math.max(x1, x2) + 10;
            if (p.x >= lo && p.x <= hi) {
                return m;
            }
        }
        return null;
    }

    private int centerOf(int[] xs, String name) {
        List<SeqParticipant> ps = model.getParticipants();
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).getName().equals(name)) {
                return xs[i];
            }
        }
        return MARGIN_X;
    }

    // -------------------------------------------------------------------------
    // マウス操作
    // -------------------------------------------------------------------------

    private void handlePress(MouseEvent e) {
        if (!editable) {
            return;
        }
        if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
            // 右クリックは選択のみ更新し、ポップアップはトリガー時点 (press/release) で 1 回出す。
            selectAt(e.getPoint());
            repaint();
            if (e.isPopupTrigger()) {
                showPopup(e);
            }
            return;
        }
        if (messageMode != null) {
            handleMessageClick(columnAt(e.getX()));
            return;
        }
        selectAt(e.getPoint());
        draggedSinceMousePress = false;
        dragPoint = null;
        repaint();
    }

    private void selectAt(Point p) {
        selectedItem = null;
        selectedParticipant = participantAt(p);
        if (selectedParticipant == null) {
            selectedItem = messageAt(p);
        }
    }

    /** メッセージ追加モード: 1 クリック目で始点、2 クリック目で終点を確定する。 */
    private void handleMessageClick(SeqParticipant hit) {
        if (hit == null) {
            messageSource = null;
            repaint();
            return;
        }
        if (messageSource == null) {
            messageSource = hit;
        } else {
            // 同じ参加者を 2 回クリックした場合は自己メッセージとして追加する。
            model.getItems().add(SeqItem.message(
                    messageSource.getName(), messageMode, hit.getName(), null));
            messageSource = null;
            listener.modelEdited();
            revalidate();
        }
        repaint();
    }

    private void handleDrag(MouseEvent e) {
        if (!editable || messageMode != null
                || (selectedItem == null && selectedParticipant == null)) {
            return;
        }
        dragPoint = e.getPoint();
        draggedSinceMousePress = true;
        repaint();
    }

    private void handleRelease(MouseEvent e) {
        if (e.isPopupTrigger()) {
            selectAt(e.getPoint());
            repaint();
            showPopup(e);
            return;
        }
        if (!draggedSinceMousePress || dragPoint == null) {
            dragPoint = null;
            return;
        }
        draggedSinceMousePress = false;
        boolean changed = false;
        if (selectedItem != null) {
            changed = dropMessageAt(e.getY());
        } else if (selectedParticipant != null) {
            changed = dropParticipantAt(e.getX());
        }
        dragPoint = null;
        if (changed) {
            listener.modelEdited();
        }
        revalidate();
        repaint();
    }

    /** ドラッグ先の Y からメッセージの新しい時系列位置を決めて並べ替える。 */
    private boolean dropMessageAt(int y) {
        int targetRow = Math.max(0, Math.round((y - firstRowY()) / (float) ROW_H));
        // targetRow 番目のメッセージの位置 (items 内 index) を探す。
        List<SeqItem> items = model.getItems();
        int oldIndex = items.indexOf(selectedItem);
        int row = 0;
        int newIndex = items.size();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getKind() != SeqItem.Kind.MESSAGE || items.get(i) == selectedItem) {
                continue;
            }
            if (row == targetRow) {
                newIndex = i;
                break;
            }
            row++;
        }
        if (newIndex > oldIndex) {
            newIndex--;
        }
        if (newIndex == oldIndex) {
            return false;
        }
        model.moveItem(selectedItem, newIndex);
        return true;
    }

    /** ドラッグ先の X から参加者の新しい並び位置を決めて並べ替える。 */
    private boolean dropParticipantAt(int x) {
        int[] xs = centers();
        int oldIndex = model.getParticipants().indexOf(selectedParticipant);
        int newIndex = 0;
        for (int i = 0; i < xs.length; i++) {
            if (i != oldIndex && xs[i] < x) {
                newIndex++;
            }
        }
        if (newIndex == oldIndex) {
            return false;
        }
        model.moveParticipant(selectedParticipant, newIndex);
        return true;
    }

    private void showPopup(MouseEvent e) {
        if (!editable) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (selectedParticipant != null) {
            SeqParticipant hit = selectedParticipant;
            JMenuItem edit = new JMenuItem(Messages.get("sketch.seq.menu.editParticipant"));
            edit.addActionListener(a -> listener.editParticipantRequested(hit));
            menu.add(edit);
            JMenuItem del = new JMenuItem(Messages.get("sketch.seq.menu.deleteParticipant"));
            del.addActionListener(a -> deleteSelection());
            menu.add(del);
        } else if (selectedItem != null) {
            SeqItem hit = selectedItem;
            JMenuItem edit = new JMenuItem(Messages.get("sketch.seq.menu.editMessage"));
            edit.addActionListener(a -> listener.editMessageRequested(hit));
            menu.add(edit);
            JMenuItem del = new JMenuItem(Messages.get("sketch.seq.menu.deleteMessage"));
            del.addActionListener(a -> deleteSelection());
            menu.add(del);
        } else {
            JMenuItem addP = new JMenuItem(Messages.get("sketch.seq.menu.addParticipantHere"));
            addP.addActionListener(a -> addParticipant(SeqParticipant.Kind.PARTICIPANT));
            menu.add(addP);
            JMenuItem addA = new JMenuItem(Messages.get("sketch.seq.menu.addActorHere"));
            addA.addActionListener(a -> addParticipant(SeqParticipant.Kind.ACTOR));
            menu.add(addA);
        }
        menu.show(this, e.getX(), e.getY());
    }

    /** 参加者を末尾に追加する (ツールバー / 右クリックメニューから)。 */
    void addParticipant(SeqParticipant.Kind kind) {
        if (!editable) {
            return;
        }
        String base = kind == SeqParticipant.Kind.ACTOR ? "NewActor" : "NewParticipant";
        SeqParticipant p = new SeqParticipant(model.uniqueName(base), kind, true);
        model.getParticipants().add(p);
        selectedParticipant = p;
        selectedItem = null;
        listener.modelEdited();
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xs = centers();
            paintLifelines(g2, xs);
            paintActivationBars(g2, xs);
            int row = 0;
            for (SeqItem m : model.getItems()) {
                if (m.getKind() == SeqItem.Kind.MESSAGE) {
                    paintMessage(g2, xs, m, firstRowY() + row * ROW_H);
                    row++;
                }
            }
            for (int i = 0; i < model.getParticipants().size(); i++) {
                paintHeader(g2, i);
            }
            paintDragGhost(g2);
            if (!editable) {
                SketchBanner.paint(g2, this, unsupported);
            } else if (messageMode != null) {
                g2.setColor(new Color(0x1565C0));
                g2.drawString(Messages.get(messageSource == null
                        ? "sketch.seq.hint.pickSource" : "sketch.seq.hint.pickTarget"), 8, 14);
            }
        } finally {
            g2.dispose();
        }
    }

    private void paintLifelines(Graphics2D g2, int[] xs) {
        Stroke old = g2.getStroke();
        g2.setColor(new Color(0x90A4AE));
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{5f, 5f}, 0f));
        for (int x : xs) {
            g2.drawLine(x, HEAD_TOP + HEAD_H, x, bottomY() + 20);
        }
        g2.setStroke(old);
    }

    private void paintHeader(Graphics2D g2, int index) {
        SeqParticipant p = model.getParticipants().get(index);
        Rectangle r = headerBounds(index);
        boolean isSel = p == selectedParticipant || p == messageSource;
        FontMetrics fm = g2.getFontMetrics();
        int nameW = fm.stringWidth(p.getName());
        if (p.getKind() == SeqParticipant.Kind.ACTOR) {
            // スティックフィギュア + 下に名前。
            int cx = r.x + r.width / 2;
            g2.setColor(getBackground());
            g2.fillRect(cx - nameW / 2 - 2, r.y, nameW + 4, r.height);
            g2.setColor(isSel ? new Color(0x1565C0) : new Color(0x37474F));
            g2.setStroke(new BasicStroke(isSel ? 2f : 1.2f));
            int hy = r.y + 2;
            g2.drawOval(cx - 5, hy, 10, 10);          // 頭
            g2.drawLine(cx, hy + 10, cx, hy + 24);    // 胴
            g2.drawLine(cx - 9, hy + 15, cx + 9, hy + 15); // 腕
            g2.drawLine(cx, hy + 24, cx - 8, hy + 33); // 脚
            g2.drawLine(cx, hy + 24, cx + 8, hy + 33);
            g2.drawString(p.getName(), cx - nameW / 2, r.y + r.height - 2);
        } else {
            int boxH = 30;
            int boxY = r.y + (r.height - boxH) / 2;
            g2.setColor(new Color(0xFFFBE6));
            g2.fillRect(r.x, boxY, r.width, boxH);
            g2.setColor(isSel ? new Color(0x1565C0) : new Color(0x555555));
            g2.setStroke(new BasicStroke(isSel ? 2f : 1f));
            g2.drawRect(r.x, boxY, r.width, boxH);
            g2.drawString(p.getName(), r.x + (r.width - nameW) / 2,
                    boxY + boxH / 2 + fm.getAscent() / 2 - 2);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    /** activate/deactivate 項目からライフライン上の活性区間バーを計算して描く。 */
    private void paintActivationBars(Graphics2D g2, int[] xs) {
        List<int[]> bars = new ArrayList<>(); // {x, y1, y2}
        java.util.Map<String, java.util.Deque<Integer>> open = new java.util.HashMap<>();
        int curY = HEAD_TOP + HEAD_H + 6;
        for (SeqItem m : model.getItems()) {
            if (m.getKind() == SeqItem.Kind.MESSAGE) {
                curY = rowYOf(m);
            } else if (m.getKind() == SeqItem.Kind.ACTIVATE) {
                open.computeIfAbsent(m.getTarget(), k -> new java.util.ArrayDeque<>())
                        .push(curY);
            } else {
                java.util.Deque<Integer> stack = open.get(m.getTarget());
                if (stack != null && !stack.isEmpty()) {
                    bars.add(new int[]{centerOf(xs, m.getTarget()), stack.pop(), curY + 6});
                }
            }
        }
        // 閉じられていない activate は図の下端まで伸ばす。
        for (var entry : open.entrySet()) {
            for (int y1 : entry.getValue()) {
                bars.add(new int[]{centerOf(xs, entry.getKey()), y1, bottomY() + 10});
            }
        }
        for (int[] b : bars) {
            g2.setColor(new Color(0xFFF3C4));
            g2.fillRect(b[0] - BAR_W / 2, b[1], BAR_W, Math.max(6, b[2] - b[1]));
            g2.setColor(new Color(0x8D6E63));
            g2.drawRect(b[0] - BAR_W / 2, b[1], BAR_W, Math.max(6, b[2] - b[1]));
        }
    }

    private void paintMessage(Graphics2D g2, int[] xs, SeqItem m, int y) {
        boolean isSel = m == selectedItem;
        Color color = isSel ? new Color(0x1565C0) : new Color(0x37474F);
        g2.setColor(color);
        Stroke old = g2.getStroke();
        g2.setStroke(m.getArrow().dashed()
                ? new BasicStroke(isSel ? 2f : 1.2f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, new float[]{6f, 5f}, 0f)
                : new BasicStroke(isSel ? 2f : 1.2f));
        int x1 = centerOf(xs, m.getFrom());
        int x2 = centerOf(xs, m.getTo());
        FontMetrics fm = g2.getFontMetrics();
        if (m.getFrom().equals(m.getTo())) {
            // 自己メッセージ: 右へ出て戻る小ループ。
            int loopW = 36;
            Path2D p = new Path2D.Double();
            p.moveTo(x1, y - 7);
            p.lineTo(x1 + loopW, y - 7);
            p.lineTo(x1 + loopW, y + 7);
            p.lineTo(x1 + 4, y + 7);
            g2.draw(p);
            g2.setStroke(old);
            paintArrowHead(g2, new Point(x1 + 4, y + 7), new Point(x1 + loopW, y + 7),
                    m.getArrow(), color);
            if (m.getLabel() != null && !m.getLabel().isEmpty()) {
                g2.drawString(m.getLabel(), x1 + loopW + 8, y - 2);
            }
            return;
        }
        g2.drawLine(x1, y, x2, y);
        g2.setStroke(old);
        paintArrowHead(g2, new Point(x2, y), new Point(x1, y), m.getArrow(), color);
        if (m.getLabel() != null && !m.getLabel().isEmpty()) {
            int lx = (x1 + x2) / 2 - fm.stringWidth(m.getLabel()) / 2;
            g2.drawString(m.getLabel(), lx, y - 5);
        }
    }

    /** 矢印先端: 同期呼び出しは塗り三角、応答・非同期は開き矢印。 */
    private void paintArrowHead(Graphics2D g2, Point at, Point from,
                                SeqItem.Arrow arrow, Color color) {
        double dx = from.x - at.x;
        double dy = from.y - at.y;
        double d = Math.max(1e-6, Math.hypot(dx, dy));
        double ux = dx / d;
        double uy = dy / d;
        int len = 11;
        int wid = 5;
        g2.setColor(color);
        if (arrow == SeqItem.Arrow.SYNC) {
            Path2D p = new Path2D.Double();
            p.moveTo(at.x, at.y);
            p.lineTo(at.x + ux * len - uy * wid, at.y + uy * len + ux * wid);
            p.lineTo(at.x + ux * len + uy * wid, at.y + uy * len - ux * wid);
            p.closePath();
            g2.fill(p);
        } else {
            g2.drawLine(at.x, at.y,
                    (int) (at.x + ux * len - uy * wid), (int) (at.y + uy * len + ux * wid));
            g2.drawLine(at.x, at.y,
                    (int) (at.x + ux * len + uy * wid), (int) (at.y + uy * len - ux * wid));
        }
    }

    /** ドラッグ並べ替え中の移動先ガイド線。 */
    private void paintDragGhost(Graphics2D g2) {
        if (dragPoint == null) {
            return;
        }
        g2.setColor(new Color(0x64, 0xB5, 0xF6, 160));
        if (selectedItem != null) {
            int row = Math.max(0, Math.round((dragPoint.y - firstRowY()) / (float) ROW_H));
            int y = firstRowY() + Math.min(row, Math.max(0, messageCount() - 1)) * ROW_H;
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(MARGIN_X / 2, y, getWidth() - MARGIN_X / 2, y);
        } else if (selectedParticipant != null) {
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(dragPoint.x, HEAD_TOP, dragPoint.x, bottomY() + 20);
        }
    }

    // -------------------------------------------------------------------------
    // テスト用シーム
    // -------------------------------------------------------------------------

    /** テスト用: 現在の選択メッセージ。 */
    SeqItem selectedItemForTest() {
        return selectedItem;
    }

    /** テスト用: 選択メッセージを直接設定する (マウス press の代替)。 */
    void setSelectedForTest(SeqItem item) {
        this.selectedItem = item;
        this.selectedParticipant = null;
    }
}
