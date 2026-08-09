// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link SvgPreviewPanel} 上に Markdown 付箋メモを重ねて操作するレイヤ。
 *
 * <p>付箋の位置・サイズは図 (SVG) 座標で保持し、{@code zoom} 倍して扱う (図に貼り付いて
 * 一緒に拡大縮小する)。描画は {@link NoteRenderer} に委譲する。マウス操作 (移動・リサイズ・
 * 追加・編集・削除) は {@link SvgPreviewPanel} のイベントハンドラから委譲され、消費した
 * かどうかを boolean で返す。状態変更時は {@link #setOnChange} で永続化側へ通知する。</p>
 *
 * <p>編集系操作はすべて {@link #commit} を通して {@link NoteHistory} に積み、
 * Ctrl+Z / Ctrl+Y で Undo/Redo できる。選択は複数対応で、Shift/Ctrl クリックでトグルして
 * 一括適用する。</p>
 */
final class DiagramNotesLayer {

    /** リサイズハンドルの一辺 (パネル px)。ヒットテスト用。 */
    private static final int HANDLE = 12;
    /** Undo/Redo 履歴の上限 (古いものから捨てる)。 */
    private static final int UNDO_LIMIT = 100;
    /** 複製・ペースト時に重ならないようずらす量 (図座標)。 */
    private static final double PASTE_OFFSET = 16;

    /** 複製/ペースト用のクリップボード。タブ間で共有できるよう static。 */
    private static List<DiagramNote> clipboard = new ArrayList<>();

    /** プロジェクト切替時にクリップボードをクリアし、旧プロジェクトの付箋混入を防ぐ。 */
    static void clearClipboard() {
        clipboard = new ArrayList<>();
    }

    private enum Mode { NONE, MOVE, RESIZE }

    private final JComponent owner;
    private final NoteRenderer renderer = new NoteRenderer();
    private final List<DiagramNote> notes = new ArrayList<>();
    /** 付箋間コネクタ。 */
    private final List<DiagramConnector> connectors = new ArrayList<>();
    /** 選択中の付箋 id (挿入順)。複数選択対応。 */
    private final Set<String> selectedIds = new LinkedHashSet<>();
    /** コネクタ作成モードの始点付箋 id。null = 非モード。 */
    private String connectFromId;

    private Runnable onChange;
    /** 付箋一覧が変わったときに UI (一覧パネル等) を更新するための通知。null 可。 */
    private Runnable onModelChanged;
    private Mode mode = Mode.NONE;
    private DiagramNote active;
    /** ELEMENT アンカー時に、対象 FQN の要素矩形 {@code [x,y,w,h]} (SVG 座標) を解決する。null 可。 */
    private java.util.function.Function<String, double[]> elementResolver;
    /** MOVE 開始点 (図座標)。グループ移動の差分計算に使う。 */
    private double dragStartX;
    private double dragStartY;
    /** MOVE 対象 (id → 開始時の原点 [x,y])。ロック中の付箋は含めない。 */
    private final Map<String, double[]> dragOrigin = new LinkedHashMap<>();
    /** ドラッグ開始前のスナップショット (確定時に Undo へ積む)。 */
    private NotesSnapshot dragUndoSnapshot;
    /** 今回のドラッグで実際に変化があったか。 */
    private boolean dragChanged;

    /** Undo/Redo 履歴 (スナップショットの取得/適用は本クラスが担う)。 */
    private final NoteHistory history = new NoteHistory(UNDO_LIMIT);

    DiagramNotesLayer(JComponent owner) {
        this.owner = owner;
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    /** 付箋一覧の変化 (追加/削除/編集/ロード) を受け取るビュー更新フックを設定する。 */
    void setOnModelChanged(Runnable r) {
        this.onModelChanged = r;
    }

    /** ELEMENT アンカーの追従先 (FQN → 要素矩形) を解決する関数を設定する。 */
    void setElementResolver(java.util.function.Function<String, double[]> resolver) {
        this.elementResolver = resolver;
        renderer.setElementResolver(resolver);
    }

    /** ELEMENT アンカー付箋の追従先要素の矩形。FREE / 未解決 (孤児) は null。 */
    private double[] anchorRect(DiagramNote n) {
        if (n.getAnchor() == DiagramNote.Anchor.ELEMENT && elementResolver != null) {
            return elementResolver.apply(n.getTargetRef());
        }
        return null;
    }

    /** 付箋原点の図座標。ELEMENT は要素左上 + オフセット、FREE はそのまま。 */
    private double effX(DiagramNote n) {
        double[] r = anchorRect(n);
        return (r == null ? 0 : r[0]) + n.getX();
    }

    private double effY(DiagramNote n) {
        double[] r = anchorRect(n);
        return (r == null ? 0 : r[1]) + n.getY();
    }

    /** 付箋一覧を差し替える (図のロード時など)。Undo 履歴もリセットする。 */
    void setNotes(List<DiagramNote> list) {
        notes.clear();
        if (list != null) {
            notes.addAll(list);
        }
        selectedIds.clear();
        history.clear();
        mode = Mode.NONE;
        active = null;
        connectFromId = null; // 中身が総入れ替えなので作成中コネクタの始点も無効
        if (onModelChanged != null) {
            onModelChanged.run();
        }
        refreshOwner();
    }

    /** 現在の付箋一覧 (コピー)。 */
    List<DiagramNote> getNotes() {
        return new ArrayList<>(notes);
    }

    /** 指定 id の付箋だけを選択する (一覧パネルからのジャンプ用)。 */
    void selectOnly(String id) {
        selectedIds.clear();
        if (byId(id) != null) {
            selectedIds.add(id);
        }
        refreshOwner();
    }

    /**
     * エクスポート用: アンカーを解決して絶対図座標へ変換した付箋コピーを返す。
     * ELEMENT アンカー付箋の x/y は「対象要素からのオフセット」であり、そのまま
     * SVG に書くと原点付近へ飛んでしまうため、必ずこちらを使うこと。
     *
     * <p>ここで<b>内容矩形へ寄せてはいけない</b>。以前は寄せていたが、要素アンカーの
     * 既定配置 (要素の右隣) は図の外に出るのが普通なので、下端に並ぶ付箋が全部同じ矩形へ
     * 潰れて<b>互いを隠し、注釈対象のクラス箱まで覆って</b>いた。しかも画面側には同じ
     * クランプが無いので、画面と書き出しで位置が違った。付箋がはみ出すなら寄せるのではなく
     * <b>書き出しキャンバスを広げる</b> ({@link #exportBounds})。</p>
     */
    List<DiagramNote> notesForExportResolved() {
        List<DiagramNote> out = new ArrayList<>(notes.size());
        for (DiagramNote n : notes) {
            DiagramNote c = n.copyDeep();
            c.setAnchor(DiagramNote.Anchor.FREE);
            c.setX(effX(n));
            c.setY(effY(n));
            out.add(c);
        }
        return out;
    }

    /**
     * 書き出しオーバーレイの幾何 — 付箋・付箋間コネクタ・要素リーダー線。
     *
     * <p>ラスタ経路 ({@link #paintForExport}) はレイヤをそのまま描けるので全部出るが、
     * SVG 注入は付箋一覧しか受け取っておらず、<b>矩形と本文しか書いていなかった</b>。
     * そのため同じ右クリックメニューの Save SVG と Save PNG / 画像コピーで内容が違い、
     * 利用者が明示的に引いたコネクタが SVG のときだけ消えていた。端点の計算は
     * {@link NoteRenderer#borderPoint} を共有する (書き出し側で計算し直すと、また
     * 「同じ規則を 2 か所に書く」形になる)。</p>
     */
    static final class ExportOverlay {

        /** アンカー解決済みの付箋 (図座標)。 */
        final List<DiagramNote> notes;
        /** 付箋間コネクタの線分 {@code [x1,y1,x2,y2]} (矢じりは終点側)。 */
        final List<double[]> connectorLines;
        /** ELEMENT 付箋のリーダー線 {@code [x1,y1,x2,y2]} (終点側に丸を打つ)。 */
        final List<double[]> leaderLines;

        ExportOverlay(List<DiagramNote> notes, List<double[]> connectorLines,
                      List<double[]> leaderLines) {
            this.notes = notes;
            this.connectorLines = connectorLines;
            this.leaderLines = leaderLines;
        }

        /** 付箋だけのオーバーレイ (コネクタ・リーダー線なし)。 */
        static ExportOverlay ofNotes(List<DiagramNote> notes) {
            return new ExportOverlay(notes, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());
        }
    }

    /** 書き出し用のオーバーレイ幾何をまとめて作る。 */
    ExportOverlay exportOverlay() {
        List<double[]> lines = new ArrayList<>();
        Map<String, DiagramNote> byId = new LinkedHashMap<>();
        for (DiagramNote n : notes) {
            byId.put(n.getId(), n);
        }
        for (DiagramConnector c : connectors) {
            DiagramNote a = byId.get(c.getFromId());
            DiagramNote b = byId.get(c.getToId());
            if (a == null || b == null) {
                continue;
            }
            double ax = effX(a);
            double ay = effY(a);
            double bx = effX(b);
            double by = effY(b);
            double[] p1 = NoteRenderer.borderPoint(ax, ay, a.getWidth(), a.getHeight(),
                    bx + b.getWidth() / 2, by + b.getHeight() / 2);
            double[] p2 = NoteRenderer.borderPoint(bx, by, b.getWidth(), b.getHeight(),
                    ax + a.getWidth() / 2, ay + a.getHeight() / 2);
            lines.add(new double[] {p1[0], p1[1], p2[0], p2[1]});
        }
        List<double[]> leaders = new ArrayList<>();
        for (DiagramNote n : notes) {
            double[] er = anchorRect(n);
            if (n.getAnchor() != DiagramNote.Anchor.ELEMENT || er == null) {
                continue;
            }
            double nx = effX(n);
            double ny = effY(n);
            double[] from = NoteRenderer.borderPoint(nx, ny, n.getWidth(), n.getHeight(),
                    er[0] + er[2] / 2, er[1] + er[3] / 2);
            double[] to = NoteRenderer.borderPoint(er[0], er[1], er[2], er[3],
                    nx + n.getWidth() / 2, ny + n.getHeight() / 2);
            leaders.add(new double[] {from[0], from[1], to[0], to[1]});
        }
        return new ExportOverlay(notesForExportResolved(), lines, leaders);
    }

    /**
     * 図と全付箋が収まる書き出しキャンバスの大きさ {@code [w, h]}。
     *
     * <p>付箋は図の内容矩形からはみ出しうる (要素アンカーの既定配置、図が縮んだあと)。
     * はみ出しを座標のクランプで解くとモデルが壊れるか付箋どうしが重なるので、
     * <b>入れ物の側を広げる</b>。SVG・PNG・画像コピーの 3 経路が同じ答えを使うことで、
     * 「SVG には出るのに PNG では切れる」という食い違いも同時に無くなる。</p>
     */
    double[] exportBounds(double diagramWidth, double diagramHeight) {
        double w = Math.max(0, diagramWidth);
        double h = Math.max(0, diagramHeight);
        for (DiagramNote n : notes) {
            w = Math.max(w, effX(n) + n.getWidth());
            h = Math.max(h, effY(n) + n.getHeight());
        }
        return new double[] {w, h};
    }

    /** 指定 id の付箋の実効矩形 {@code [x,y,w,h]} (図座標)。なければ null。 */
    double[] noteRect(String id) {
        DiagramNote n = byId(id);
        return n == null ? null
                : new double[] {effX(n), effY(n), n.getWidth(), n.getHeight()};
    }

    boolean isDragging() {
        return mode != Mode.NONE;
    }

    /**
     * 付箋が変わったので入れ物を測り直して描き直す。
     *
     * <p>{@code repaint} だけでは足りない。入れ物の大きさは付箋を含めて決まるので
     * ({@link SvgPreviewPanel#getPreferredSize})、測り直さないと図の外へ動かした付箋に
     * スクロールで<b>到達できない</b>ままになる。</p>
     */
    private void refreshOwner() {
        owner.revalidate();
        owner.repaint();
    }

    /** 付箋が 1 件以上あるか (エクスポートに含めるか判定用)。 */
    boolean hasNotes() {
        return !notes.isEmpty();
    }

    /** エクスポート用に、選択枠やハンドルを描かずに付箋だけを描画する。 */
    void paintForExport(Graphics2D g2, double scale) {
        Set<String> saved = new LinkedHashSet<>(selectedIds);
        selectedIds.clear(); // 選択装飾を出さない
        try {
            paint(g2, scale);
        } finally {
            selectedIds.clear();
            selectedIds.addAll(saved);
        }
    }

    private void fireChange() {
        if (onChange != null) {
            onChange.run();
        }
        if (onModelChanged != null) {
            onModelChanged.run();
        }
    }

    // ── Undo / Redo ──

    /** 現在の付箋 + コネクタをディープコピーしたスナップショットを返す。 */
    private NotesSnapshot snapshot() {
        List<DiagramNote> ns = new ArrayList<>(notes.size());
        for (DiagramNote n : notes) {
            ns.add(n.copyDeep());
        }
        List<DiagramConnector> cs = new ArrayList<>(connectors.size());
        for (DiagramConnector c : connectors) {
            cs.add(c.copy());
        }
        return new NotesSnapshot(ns, cs);
    }

    /** スナップショットから付箋 + コネクタを復元する (選択は存在するものだけ残す)。 */
    private void restore(NotesSnapshot snap) {
        notes.clear();
        for (DiagramNote n : snap.notes) {
            notes.add(n.copyDeep());
        }
        connectors.clear();
        for (DiagramConnector c : snap.connectors) {
            connectors.add(c.copy());
        }
        Set<String> ids = new LinkedHashSet<>();
        for (DiagramNote n : notes) {
            ids.add(n.getId());
        }
        selectedIds.retainAll(ids);
        // 始点が履歴で消えたら作成モードも畳む (残すと次のクリックが食われる)。
        connectFromId = ids.contains(connectFromId) ? connectFromId : null;
        mode = Mode.NONE;
        active = null;
    }

    /**
     * 変更前の状態を Undo に積んでから {@code change} を実行し、変更通知・再描画する。
     * {@code coalesce} が直前と同じなら 1 操作に束ねる (矢印連続移動など)。null は独立操作。
     */
    private void commit(String coalesce, Runnable change) {
        if (history.beginEdit(coalesce)) {
            history.push(snapshot());
        }
        change.run();
        fireChange();
        refreshOwner();
    }

    /** 直前の編集を取り消す。取り消したら true。 */
    boolean undo() {
        NotesSnapshot snap = history.undo(snapshot());
        if (snap == null) {
            return false;
        }
        restore(snap);
        fireChange();
        refreshOwner();
        return true;
    }

    /** 取り消した編集をやり直す。やり直したら true。 */
    boolean redo() {
        NotesSnapshot snap = history.redo(snapshot());
        if (snap == null) {
            return false;
        }
        restore(snap);
        fireChange();
        refreshOwner();
        return true;
    }

    /** すべての付箋とコネクタを {@link NoteRenderer} で描く。{@code g2} はパネル座標前提。 */
    void paint(Graphics2D g2, double zoom) {
        renderer.paint(g2, zoom, notes, selectedIds, connectors);
    }

    /** 現在のコネクタ一覧 (コピー)。 */
    List<DiagramConnector> getConnectors() {
        return new ArrayList<>(connectors);
    }

    /**
     * 付箋とコネクタをまとめて差し替える (図のロード時)。端点が欠けたコネクタは捨て、
     * Undo 履歴はリセットする。
     */
    void setData(List<DiagramNote> noteList, List<DiagramConnector> connectorList) {
        setNotes(noteList); // 選択 / 履歴のリセットを含む
        connectors.clear();
        if (connectorList != null) {
            connectors.addAll(connectorList);
        }
        pruneConnectors();
        if (onModelChanged != null) {
            onModelChanged.run();
        }
        refreshOwner();
    }

    /** 端点の付箋が存在しないコネクタを除去する。 */
    private void pruneConnectors() {
        Set<String> ids = new LinkedHashSet<>();
        for (DiagramNote n : notes) {
            ids.add(n.getId());
        }
        connectors.removeIf(c -> !ids.contains(c.getFromId()) || !ids.contains(c.getToId()));
    }

    /** リサイズハンドルの一辺 (パネル px)。小さな付箋では本文を侵食しないよう縮める。 */
    private static int handleSize(int pw, int ph) {
        return Math.max(7, Math.min(HANDLE, Math.min(pw, ph) / 4));
    }

    // ── ヒットテスト / 選択 ──

    /** パネル座標 {@code p} の最前面の付箋 (後ろ=新しいものを優先)。なければ null。 */
    private DiagramNote hitTest(Point p, double zoom) {
        for (int i = notes.size() - 1; i >= 0; i--) {
            if (rectOf(notes.get(i), zoom).contains(p)) {
                return notes.get(i);
            }
        }
        return null;
    }

    private Rectangle rectOf(DiagramNote n, double zoom) {
        return new Rectangle((int) Math.round(effX(n) * zoom), (int) Math.round(effY(n) * zoom),
                (int) Math.round(n.getWidth() * zoom), (int) Math.round(n.getHeight() * zoom));
    }

    private boolean onHandle(DiagramNote n, Point p, double zoom) {
        Rectangle r = rectOf(n, zoom);
        int handle = handleSize(r.width, r.height);
        Rectangle h = new Rectangle(r.x + r.width - handle, r.y + r.height - handle, handle, handle);
        return h.contains(p);
    }

    /** 選択中の付箋を z 順 (奥→手前) で返す。 */
    List<DiagramNote> selectedNotes() {
        List<DiagramNote> out = new ArrayList<>();
        for (DiagramNote n : notes) {
            if (selectedIds.contains(n.getId())) {
                out.add(n);
            }
        }
        return out;
    }

    /** 選択中の最前面 (操作の主対象) 付箋。なければ null。 */
    private DiagramNote primarySelected() {
        DiagramNote primary = null;
        for (DiagramNote n : notes) {
            if (selectedIds.contains(n.getId())) {
                primary = n; // 後勝ち = 最前面
            }
        }
        return primary;
    }

    private DiagramNote byId(String id) {
        for (DiagramNote n : notes) {
            if (n.getId().equals(id)) {
                return n;
            }
        }
        return null;
    }

    private boolean anySelectedMovable() {
        for (DiagramNote n : selectedNotes()) {
            if (!n.isLocked()) {
                return true;
            }
        }
        return false;
    }

    /** マウス位置に応じたカーソル。付箋外なら null (パネル側に委ねる)。 */
    Cursor cursorFor(Point p, double zoom) {
        if (connectFromId != null) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
        DiagramNote n = hitTest(p, zoom);
        if (n == null) {
            return null;
        }
        if (n.isLocked()) {
            return Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
        }
        if (onHandle(n, p, zoom)) {
            return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
        }
        return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    }

    // ── マウス操作 (SvgPreviewPanel から委譲) ──

    /** 左ボタン押下。付箋を掴んだら true (パネルのパン/選択を抑止)。 */
    boolean pressed(MouseEvent e, double zoom) {
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return false;
        }
        Point p = e.getPoint();
        DiagramNote n = hitTest(p, zoom);
        // コネクタ作成モード: 次にクリックした付箋を終点にして 1 本引く。空クリックで中断。
        if (connectFromId != null) {
            String from = connectFromId;
            connectFromId = null;
            if (n != null && !n.getId().equals(from)) {
                createConnector(from, n.getId());
            }
            refreshOwner();
            return true;
        }
        boolean additive = e.isShiftDown() || e.isControlDown();
        if (n == null) {
            // 付箋外: 追加修飾なしなら選択解除し、パネルのパンに委ねる。
            if (!additive && !selectedIds.isEmpty()) {
                selectedIds.clear();
                refreshOwner();
            }
            return false;
        }
        owner.requestFocusInWindow(); // Delete キー等がプレビューに届くように
        if (additive) {
            // Shift / Ctrl クリックで選択トグル (移動は開始しない)。
            if (!selectedIds.remove(n.getId())) {
                selectedIds.add(n.getId());
            }
            mode = Mode.NONE;
            active = null;
            refreshOwner();
            return true;
        }
        // 未選択の付箋を掴んだら単独選択に切り替える (既に選択集合内ならグループを維持)。
        if (!selectedIds.contains(n.getId())) {
            selectedIds.clear();
            selectedIds.add(n.getId());
        }
        if (onHandle(n, p, zoom) && selectedIds.size() == 1 && !n.isLocked()) {
            mode = Mode.RESIZE;
            active = n;
            beginGesture();
        } else if (anySelectedMovable()) {
            mode = Mode.MOVE;
            active = n;
            dragStartX = p.x / zoom;
            dragStartY = p.y / zoom;
            dragOrigin.clear();
            for (DiagramNote s : selectedNotes()) {
                if (!s.isLocked()) {
                    dragOrigin.put(s.getId(), new double[] {s.getX(), s.getY()});
                }
            }
            beginGesture();
        } else {
            // 選択はすべてロック済み: 選択だけ更新してドラッグはしない。
            mode = Mode.NONE;
            active = null;
        }
        refreshOwner();
        return true;
    }

    private void beginGesture() {
        dragUndoSnapshot = snapshot();
        dragChanged = false;
    }

    boolean dragged(MouseEvent e, double zoom) {
        if (mode == Mode.NONE || active == null) {
            return false;
        }
        Point p = e.getPoint();
        if (mode == Mode.MOVE) {
            double dx = p.x / zoom - dragStartX;
            double dy = p.y / zoom - dragStartY;
            if (dx != 0 || dy != 0) {
                dragChanged = true;
            }
            for (Map.Entry<String, double[]> en : dragOrigin.entrySet()) {
                DiagramNote s = byId(en.getKey());
                if (s == null) {
                    continue;
                }
                double nx = en.getValue()[0] + dx;
                double ny = en.getValue()[1] + dy;
                // FREE は図の外 (負) へ出ないよう 0 でクランプ。ELEMENT は要素相対なので負も許容。
                if (s.getAnchor() == DiagramNote.Anchor.ELEMENT) {
                    s.setX(nx);
                    s.setY(ny);
                } else {
                    placeFree(s, nx, ny);
                }
            }
        } else if (!active.isLocked()) {
            resizeFree(active, p.x / zoom - effX(active), p.y / zoom - effY(active));
            dragChanged = true;
        }
        refreshOwner();
        return true;
    }

    boolean released() {
        if (mode == Mode.NONE) {
            return false;
        }
        mode = Mode.NONE;
        active = null;
        // 実際に動かした/リサイズした場合だけ履歴に積んで保存 (クリックだけなら何もしない)。
        if (dragChanged && dragUndoSnapshot != null) {
            history.push(dragUndoSnapshot);
            history.resetCoalesce();
            fireChange();
        }
        dragUndoSnapshot = null;
        dragChanged = false;
        refreshOwner();
        return true;
    }

    /** ダブルクリックで編集。消費したら true。 */
    boolean doubleClicked(MouseEvent e, double zoom) {
        DiagramNote n = hitTest(e.getPoint(), zoom);
        if (n == null) {
            return false;
        }
        editNote(n);
        return true;
    }

    /** 右クリック。付箋上なら文脈メニューを ({@link NoteContextMenu}) 出して true。 */
    boolean popup(MouseEvent e, double zoom) {
        DiagramNote n = hitTest(e.getPoint(), zoom);
        if (n == null) {
            return false;
        }
        // 選択外の付箋を右クリックしたら単独選択に切り替える (選択内ならグループを維持)。
        if (!selectedIds.contains(n.getId())) {
            selectedIds.clear();
            selectedIds.add(n.getId());
        }
        refreshOwner();
        NoteContextMenu.show(this, n, e, zoom);
        return true;
    }

    /** 選択がすべてロック済みか (メニューの Lock/Unlock ラベル判定用)。 */
    boolean isAllSelectedLocked() {
        return allLocked(selectedNotes());
    }

    private static boolean allLocked(List<DiagramNote> sel) {
        for (DiagramNote n : sel) {
            if (!n.isLocked()) {
                return false;
            }
        }
        return !sel.isEmpty();
    }

    void setColorSelected(String hex) {
        if (selectedIds.isEmpty()) {
            return;
        }
        commit(null, () -> {
            for (DiagramNote n : selectedNotes()) {
                n.setColor(hex);
            }
        });
    }

    /** JColorChooser で任意色を選び、選択中の付箋すべてに適用する。 */
    void pickCustomColor() {
        if (selectedIds.isEmpty()) {
            return;
        }
        DiagramNote primary = primarySelected();
        Color initial = NoteRenderer.parseColor(
                primary != null ? primary.getColor() : DiagramNote.DEFAULT_COLOR);
        Color chosen = javax.swing.JColorChooser.showDialog(
                owner, Messages.get("note.color.custom"), initial);
        if (chosen != null) {
            setColorSelected(String.format("#%02X%02X%02X",
                    chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
        }
    }

    /**
     * 選択中の付箋の高さを本文量に合わせる。
     *
     * <p>幅は変えない。図の下端寄りで本文が入らないときは<b>付箋を上へ寄せて</b>
     * 高さを確保する — 切り詰めて「合わせられませんでした」と黙るより、
     * このコマンドの目的 (本文が収まる高さにする) を果たす。</p>
     */
    void fitHeightSelected() {
        List<DiagramNote> sel = selectedNotes();
        if (sel.isEmpty()) {
            return;
        }
        commit(null, () -> {
            for (DiagramNote n : sel) {
                sizeFree(n, n.getWidth(), renderer.contentHeight(n));
            }
        });
    }

    /**
     * パネル座標 {@code p} の位置に新しい付箋を配置して選択状態にする。慣習に合わせ
     * 「まず配置 → ダブルクリック / Enter で編集」とし、即モーダルは開かない。
     */
    void addNoteAt(Point p, double zoom) {
        double w = 280 / zoom;
        double h = 160 / zoom;
        DiagramNote n = new DiagramNote(0, 0, w, h, "");
        fitFree(n, p.x / zoom, p.y / zoom, w, h);
        commit(null, () -> {
            notes.add(n);
            selectedIds.clear();
            selectedIds.add(n.getId());
        });
        owner.requestFocusInWindow(); // Enter/Delete/矢印キーが届くように
    }

    /**
     * FREE 付箋の<b>矩形全体</b>を書き出される図の範囲内へ収める。位置も大きさもここを通る。
     *
     * <p>言明は 1 つ:<b>付箋が占める矩形は図の内容矩形の中にある</b>。図がウィンドウより
     * 小さいと、画面には見えるのに ({@code JViewport} がビューを引き伸ばす) PNG も SVG も
     * 図の内容矩形で寸法が決まるため、外へ出た分は<b>書き出しから黙って消える</b>。</p>
     *
     * <p>以前はこれを「原点を寄せる」と「大きさを切り詰める」の 2 つの規則に分けて持って
     * いた。分けた結果、大きさの規則がリサイズにしか入らず生成・複製・貼り付けが素通しに
     * なり、逆に高さ合わせでは大きさを切り詰めるだけで<b>本文が入らない高さ</b>を返すように
     * なった。1 つの言明にすれば、切り詰めるか動かすかは<b>結果として</b>決まる —
     * まず箱に入る大きさへ、次に矩形が収まる位置へ。</p>
     */
    private void fitFree(DiagramNote n, double x, double y, double w, double h) {
        if (n.getAnchor() == DiagramNote.Anchor.ELEMENT) {
            // 要素相対なので図の内容矩形とは無関係 (負の位置も正当)。
            n.setX(x);
            n.setY(y);
            n.setWidth(w);
            n.setHeight(h);
            return;
        }
        double cw = contentLimit(true);
        double ch = contentLimit(false);
        double fw = cw <= 0 ? w : Math.min(w, cw);
        double fh = ch <= 0 ? h : Math.min(h, ch);
        n.setWidth(fw);
        n.setHeight(fh);
        n.setX(cw <= 0 ? Math.max(0, x) : Math.max(0, Math.min(x, cw - fw)));
        n.setY(ch <= 0 ? Math.max(0, y) : Math.max(0, Math.min(y, ch - fh)));
    }

    /** FREE 付箋を移動する (大きさは変えない)。 */
    private void placeFree(DiagramNote n, double x, double y) {
        fitFree(n, x, y, n.getWidth(), n.getHeight());
    }

    /** FREE 付箋の大きさを変える (位置は保つ — 収まらなければ矩形ごと寄る)。 */
    private void sizeFree(DiagramNote n, double w, double h) {
        fitFree(n, n.getX(), n.getY(), w, h);
    }

    /**
     * ドラッグによるリサイズ。{@link #sizeFree} と違い<b>原点を動かさない</b>。
     *
     * <p>この 2 つは契約が違う。「本文に合わせて高さ調整」は<b>本文が収まる高さ</b>が
     * 目的なので、収まらなければ付箋を上へ寄せてよい ({@link #sizeFree})。
     * ドラッグのリサイズは<b>掴んだ角だけを動かす</b>のが目的なので、寄せてはいけない。
     * 同じ経路にまとめたところ、幅が箱幅まで育った瞬間に原点が押し出されて左辺が
     * 図の左端まで走り、マウスを戻しても復元しなくなった。リサイズで許される上限は
     * 「箱の幅」ではなく<b>原点から端までの残り</b>である。</p>
     */
    private void resizeFree(DiagramNote n, double w, double h) {
        if (n.getAnchor() == DiagramNote.Anchor.ELEMENT) {
            n.setWidth(w);
            n.setHeight(h);
            return;
        }
        n.setWidth(roomFrom(n.getX(), w, true));
        n.setHeight(roomFrom(n.getY(), h, false));
    }

    /** 原点 {@code origin} から書き出し範囲の端までに収まる大きさ。 */
    private double roomFrom(double origin, double size, boolean horizontal) {
        double limit = contentLimit(horizontal);
        return limit <= 0 ? size : Math.max(0, Math.min(size, limit - Math.max(0, origin)));
    }

    /** 図の内容矩形の幅/高さ。所有者が答えられなければ 0 (= 制限なし)。 */
    private double contentLimit(boolean horizontal) {
        return owner instanceof SvgPreviewPanel p
                ? (horizontal ? p.contentWidth() : p.contentHeight()) : 0;
    }

    /** 指定要素 (FQN) に追従する ELEMENT 付箋を要素右上に追加して選択する ({@code rect} は要素矩形, null 可)。 */
    void addElementNote(String fqn, double[] rect) {
        double offX = rect != null ? rect[2] + 16 : 24; // 要素の右隣
        DiagramNote n = new DiagramNote(offX, 0, 240, 150, "");
        n.setAnchor(DiagramNote.Anchor.ELEMENT);
        n.setTargetRef(fqn);
        commit(null, () -> {
            notes.add(n);
            selectedIds.clear();
            selectedIds.add(n.getId());
        });
        owner.requestFocusInWindow();
    }

    /** 選択中の付箋をすべて削除する (Delete / Backspace / メニュー)。削除したら true。 */
    boolean deleteSelected() {
        if (selectedIds.isEmpty()) {
            return false;
        }
        // 始点を消した恐れがあるので作成モードも畳む (残すと次のクリックで宙ぶらりん)。
        connectFromId = null;
        commit(null, () -> {
            connectors.removeIf(c -> selectedIds.contains(c.getFromId())
                    || selectedIds.contains(c.getToId()));
            notes.removeIf(n -> selectedIds.contains(n.getId()));
            selectedIds.clear();
        });
        return true;
    }

    /** {@code from} を始点にコネクタ作成モードへ入る (次にクリックした付箋が終点)。 */
    void startConnectorFrom(String fromId) {
        connectFromId = fromId;
        owner.requestFocusInWindow();
        refreshOwner();
    }

    /** コネクタ作成モードを中断する (Esc)。中断したら true。 */
    boolean cancelConnect() {
        if (connectFromId == null) {
            return false;
        }
        connectFromId = null;
        refreshOwner();
        return true;
    }

    /** 2 付箋を結ぶコネクタを 1 本追加する (自己ループ・重複・消えた端点は無視)。 */
    private void createConnector(String fromId, String toId) {
        if (fromId.equals(toId) || byId(fromId) == null || byId(toId) == null) {
            return;
        }
        DiagramConnector added = new DiagramConnector(fromId, toId);
        for (DiagramConnector c : connectors) {
            if (c.sameEndpoints(added)) {
                return;
            }
        }
        commit(null, () -> connectors.add(added));
    }

    /** 選択中の付箋に接続するコネクタがあるか (メニュー有効化用)。 */
    boolean hasConnectorsTouchingSelection() {
        for (DiagramConnector c : connectors) {
            if (selectedIds.contains(c.getFromId()) || selectedIds.contains(c.getToId())) {
                return true;
            }
        }
        return false;
    }

    /** 選択中の付箋に接続するコネクタをすべて削除する。 */
    void removeConnectorsTouchingSelection() {
        if (!hasConnectorsTouchingSelection()) {
            return;
        }
        commit(null, () -> connectors.removeIf(c -> selectedIds.contains(c.getFromId())
                || selectedIds.contains(c.getToId())));
    }

    /** 選択中の付箋の編集を開く (Enter / 右クリック編集)。選択が無ければ何もしない。 */
    void editSelected() {
        DiagramNote n = primarySelected();
        if (n != null) {
            editNote(n);
        }
    }

    /**
     * 選択中の付箋を図座標で平行移動する (矢印キー)。ロック中は動かさない。
     * 連続した矢印移動は 1 回の Undo にまとめる。
     */
    void moveSelected(double dx, double dy) {
        List<DiagramNote> movable = new ArrayList<>();
        for (DiagramNote n : selectedNotes()) {
            if (!n.isLocked()) {
                movable.add(n);
            }
        }
        if (movable.isEmpty()) {
            return;
        }
        commit("move", () -> {
            for (DiagramNote n : movable) {
                double nx = n.getX() + dx;
                double ny = n.getY() + dy;
                if (n.getAnchor() == DiagramNote.Anchor.ELEMENT) {
                    n.setX(nx);
                    n.setY(ny);
                } else {
                    placeFree(n, nx, ny);
                }
            }
        });
    }

    /** 選択中の付箋を複製し、少しずらして配置して複製側を選択する (Ctrl+D)。 */
    void duplicateSelected() {
        List<DiagramNote> sel = selectedNotes();
        if (sel.isEmpty()) {
            return;
        }
        commit(null, () -> {
            selectedIds.clear();
            for (DiagramNote s : sel) {
                DiagramNote d = s.duplicate();
                fitFree(d, s.getX() + PASTE_OFFSET, s.getY() + PASTE_OFFSET,
                        d.getWidth(), d.getHeight());
                notes.add(d);
                selectedIds.add(d.getId());
            }
        });
    }

    /** 選択中の付箋をクリップボードへコピーする (Ctrl+C)。図は変更しない。 */
    void copySelected() {
        List<DiagramNote> sel = selectedNotes();
        if (sel.isEmpty()) {
            return;
        }
        List<DiagramNote> copy = new ArrayList<>();
        for (DiagramNote s : sel) {
            copy.add(s.copyDeep());
        }
        clipboard = copy;
    }

    /** クリップボードの付箋を貼り付けて選択する (Ctrl+V)。貼り付けたら true。 */
    boolean pasteClipboard() {
        if (clipboard.isEmpty()) {
            return false;
        }
        commit(null, () -> {
            selectedIds.clear();
            for (DiagramNote c : clipboard) {
                DiagramNote d = c.duplicate();
                fitFree(d, c.getX() + PASTE_OFFSET, c.getY() + PASTE_OFFSET,
                        d.getWidth(), d.getHeight());
                notes.add(d);
                selectedIds.add(d.getId());
            }
        });
        owner.requestFocusInWindow();
        return true;
    }

    /** 選択中の付箋のロック状態をまとめて切り替える。全ロック時のみ解除、それ以外はロック。 */
    void toggleLockSelected() {
        List<DiagramNote> sel = selectedNotes();
        if (sel.isEmpty()) {
            return;
        }
        final boolean lock = !allLocked(sel);
        commit(null, () -> {
            for (DiagramNote n : sel) {
                n.setLocked(lock);
            }
        });
    }

    /** 選択中の付箋を最前面へ (相対順は維持)。 */
    void bringToFront() {
        List<DiagramNote> sel = selectedNotes();
        if (sel.isEmpty()) {
            return;
        }
        commit(null, () -> {
            notes.removeAll(sel);
            notes.addAll(sel);
        });
    }

    /** 選択中の付箋を最背面へ (相対順は維持)。 */
    void sendToBack() {
        List<DiagramNote> sel = selectedNotes();
        if (sel.isEmpty()) {
            return;
        }
        commit(null, () -> {
            notes.removeAll(sel);
            notes.addAll(0, sel);
        });
    }

    /**
     * 付箋操作のキーバインドを {@code target} (= 描画先パネル) に登録する。Delete/Backspace=
     * 削除、Enter=編集、矢印=移動 (Shift で 10)、Ctrl+Z=取り消し / Ctrl+Y・Ctrl+Shift+Z=
     * やり直し、Ctrl+D=複製、Ctrl+C/V=コピー&ペースト、Ctrl+] / Ctrl+[=最前面 / 最背面。
     * いずれも選択が無ければ no-op で、パン等を阻害しない。
     */
    void installKeyBindings(JComponent target) {
        target.setFocusable(true);
        bindKey(target, KeyEvent.VK_DELETE, 0, "note.del", this::deleteSelected);
        bindKey(target, KeyEvent.VK_BACK_SPACE, 0, "note.del2", this::deleteSelected);
        bindKey(target, KeyEvent.VK_ENTER, 0, "note.edit", this::editSelected);
        bindKey(target, KeyEvent.VK_UP, 0, "note.up", () -> moveSelected(0, -1));
        bindKey(target, KeyEvent.VK_DOWN, 0, "note.down", () -> moveSelected(0, 1));
        bindKey(target, KeyEvent.VK_LEFT, 0, "note.left", () -> moveSelected(-1, 0));
        bindKey(target, KeyEvent.VK_RIGHT, 0, "note.right", () -> moveSelected(1, 0));
        int sh = InputEvent.SHIFT_DOWN_MASK;
        bindKey(target, KeyEvent.VK_UP, sh, "note.up10", () -> moveSelected(0, -10));
        bindKey(target, KeyEvent.VK_DOWN, sh, "note.down10", () -> moveSelected(0, 10));
        bindKey(target, KeyEvent.VK_LEFT, sh, "note.left10", () -> moveSelected(-10, 0));
        bindKey(target, KeyEvent.VK_RIGHT, sh, "note.right10", () -> moveSelected(10, 0));
        int c = InputEvent.CTRL_DOWN_MASK;
        bindKey(target, KeyEvent.VK_Z, c, "note.undo", this::undo);
        bindKey(target, KeyEvent.VK_Y, c, "note.redo", this::redo);
        bindKey(target, KeyEvent.VK_Z, c | sh, "note.redo2", this::redo);
        bindKey(target, KeyEvent.VK_D, c, "note.dup", this::duplicateSelected);
        bindKey(target, KeyEvent.VK_C, c, "note.copy", this::copySelected);
        bindKey(target, KeyEvent.VK_V, c, "note.paste", this::pasteClipboard);
        bindKey(target, KeyEvent.VK_CLOSE_BRACKET, c, "note.front", this::bringToFront);
        bindKey(target, KeyEvent.VK_OPEN_BRACKET, c, "note.back", this::sendToBack);
        bindKey(target, KeyEvent.VK_ESCAPE, 0, "note.connectCancel", this::cancelConnect);
    }

    private static void bindKey(JComponent c, int code, int mod, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(code, mod), name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    void editNote(DiagramNote n) {
        String beforeText = n.getText();
        List<String> beforeTags = n.getTags();
        NoteEditDialog.Result r = NoteEditDialog.edit(owner, beforeText, beforeTags);
        if (r != null) {
            if (!r.text.equals(beforeText) || !r.tags.equals(beforeTags)) {
                commit(null, () -> {
                    n.setText(r.text);
                    n.setTags(r.tags);
                });
            }
        } else if (n.getText().trim().isEmpty() && n.getTags().isEmpty() && notes.contains(n)) {
            // 空 (本文・タグなし) のままキャンセル → 付箋を残さない。commit 経由で
            // Undo 履歴に積む: 以前は履歴を通さず直接削除していたため、既存の空付箋
            // (色付きマーカー用途) を誤って Esc で消すと Ctrl+Z でも戻せなかった。
            commit(null, () -> {
                notes.remove(n);
                selectedIds.remove(n.getId());
            });
        }
    }
}
