// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 図タブを「別ウィンドウ」へ切り出して表示する仕組み (VS Code の "Move into New Window" 相当)。
 *
 * <p>各ウィンドウは独立した {@link DiagramTabPane} を持ち、解析キャッシュ
 * ({@link ProjectAnalysisCache}) だけをメインウィンドウと共有する。タブの複製ではなく
 * <em>移動</em>なので、同じ図が同時に 2 か所へ束縛されることはなく、付箋メモの競合も起きない
 * (付箋はキー単位で {@code .juml/notes.json} に永続化され、移動先で同じキーで開き直すと復元される)。</p>
 *
 * <p>2 画面 (デュアルモニタ) 環境では、可能なら新ウィンドウをサブスクリーンへ配置し、
 * メインウィンドウと並べて確認しながら作業できるようにする。</p>
 */
public final class DetachedDiagramWindows {

    private final ProjectAnalysisCache cache;
    private final BooleanSupplier autoFitOnRender;
    private final java.awt.Window owner;
    private final DiagramTabPane mainPane;
    private int tabBudget;
    private int renderedTabs;
    private final DiagramNotesBinder sharedNotes;
    /** 開いている別ウィンドウ → その図ペイン (ウィンドウ横断の重複タブフォーカス用)。 */
    private final Map<JFrame, DiagramTabPane> windows = new LinkedHashMap<>();

    /**
     * @param cache           メインと共有する解析キャッシュ (読み取り専用の解析結果)
     * @param owner           位置決めの基準にするメインウィンドウ (null 可)
     * @param mainPane        メインウィンドウの図ペイン (ウィンドウ横断の重複タブ照合用)
     * @param autoFitOnRender 新ウィンドウの図に自動フィットを適用するか (Preferences 連動)
     * @param tabBudget       ウィンドウあたりのタブ上限 (メインと同じ設定)
     * @param renderedTabs    描画保持数 (メインと同じ設定)
     * @param sharedNotes     メインと共有する付箋バインダ (単一ファイル二重書き込みによる
     *                        付箋消失を防ぐ)。IO スレッドの停止は所有者 (メイン) が行うため
     *                        別ウィンドウの shutdown では止めない。
     */
    public DetachedDiagramWindows(ProjectAnalysisCache cache,
                                  java.awt.Window owner, DiagramTabPane mainPane,
                                  BooleanSupplier autoFitOnRender,
                                  int tabBudget, int renderedTabs,
                                  DiagramNotesBinder sharedNotes) {
        this.cache = cache;
        this.owner = owner;
        this.mainPane = mainPane;
        this.autoFitOnRender = autoFitOnRender;
        this.tabBudget = tabBudget;
        this.renderedTabs = renderedTabs;
        this.sharedNotes = sharedNotes;
    }

    /**
     * {@code key} の図タブが「別のウィンドウ」(呼び出し元ペイン以外) で既に開かれていれば、
     * そのウィンドウを前面に出しタブを選択して true を返す。ウィンドウ横断の重複タブ防止に使う。
     */
    boolean focusExistingElsewhere(DiagramTabPane caller, String key) {
        if (mainPane != null && mainPane != caller && mainPane.selectTabByKey(key)) {
            if (owner != null) {
                owner.toFront();
            }
            return true;
        }
        for (Map.Entry<JFrame, DiagramTabPane> e : windows.entrySet()) {
            if (e.getValue() != caller && e.getValue().selectTabByKey(key)) {
                e.getKey().toFront();
                return true;
            }
        }
        return false;
    }

    /**
     * 図タブを新しいウィンドウへ移す。移動元ペインが既にタブを剥がしているので、
     * ここでは受け取った {@link DiagramTabPane.TabHandle} を新ウィンドウで開き直すだけ。
     * EDT から呼ぶこと。
     */
    public void moveToNewWindow(DiagramTabPane.TabHandle handle) {
        if (handle == null) {
            return;
        }
        JFrame frame = new JFrame(windowTitle(handle.label));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        JLabel statusLine = new JLabel(" ");
        statusLine.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 8, 2, 8));
        // 各ウィンドウは "独立した" DiagramState を持つ。メインと共有すると、別ウィンドウの
        // 描画完了/フォーカス/クローズが共有 state を上書き・クリアし、メインの Export/Copy が
        // 別ウィンドウの図を出したり「図なし」になる (クロスウィンドウ state 汚染) ため。
        DiagramTabPane pane = new DiagramTabPane(tabs, 0, cache, new DiagramState(),
                msg -> statusLine.setText(msg == null || msg.isEmpty() ? " " : msg),
                zoom -> { });
        // 付箋ストアはメインと "共有" する。ウィンドウごとに別ストアだと、それぞれが
        // .juml/notes.json をメモリ像から丸ごと上書きし、last-writer-wins で他ウィンドウの
        // 付箋を無言で消す (単一ファイル二重書き込み)。共有すれば単一の in-memory 像になる。
        if (sharedNotes != null) {
            pane.useSharedNotesBinder(sharedNotes);
        }
        // 自動フィットは "サプライヤ" で毎回問い合わせる。生成時の値を焼き付けると、開いた後の
        // Preferences 変更 (auto-fit ON/OFF) がこのウィンドウへ反映されないため。
        if (autoFitOnRender != null) {
            pane.setAutoFitSupplier(autoFitOnRender);
        }
        if (tabBudget > 0) {
            pane.setTabBudget(tabBudget, renderedTabs);
        }
        // このウィンドウのタブも、さらに別ウィンドウへ移せるようにする (ウィンドウ間ホップ)。
        pane.setOnMoveToNewWindow(this::moveToNewWindow);
        // 同じ図をメイン/別ウィンドウで二重に開かないよう、既存タブがあればそこへフォーカス委譲。
        pane.setCrossWindowFocus(key -> focusExistingElsewhere(pane, key));
        // タブが 0 枚になったらウィンドウごと閉じる (空ウィンドウを残さない)。
        pane.setOnBecameEmpty(frame::dispose);
        // タイトルはタブ選択変更 (ChangeListener) と、タブ内トグルによる図種/レイアウト切替
        // (onTabFocused 経由) の両方で追従させる。切替はラベルだけ変えて選択を動かさないため、
        // ChangeListener だけだとタイトルが旧図種のまま残る。
        Runnable syncTitle = () -> frame.setTitle(windowTitle(selectedTitle(tabs)));
        tabs.addChangeListener(e -> syncTitle.run());
        pane.setOnTabFocused(ft -> syncTitle.run());

        JToolBar toolBar = buildZoomToolBar(pane);
        JPanel content = new JPanel(new BorderLayout());
        content.add(toolBar, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        content.add(statusLine, BorderLayout.SOUTH);
        frame.setContentPane(content);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                pane.shutdown(); // 付箋保存 IO スレッドを止める (共有バインダは所有者が止める)
                windows.remove(frame);
            }
        });

        placeWindow(frame);
        windows.put(frame, pane);
        pane.openFromHandle(handle);
        frame.setVisible(true);
        frame.toFront();
    }

    /** ズーム操作用の小さなツールバー (アクティブタブに作用)。 */
    private static JToolBar buildZoomToolBar(DiagramTabPane pane) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(new JLabel(Messages.get("toolbar.zoomLabel")));
        bar.add(iconButton(MaterialIcons.Glyph.ZOOM_IN, "menubar.view.zoomIn", pane::zoomInActive));
        bar.add(iconButton(MaterialIcons.Glyph.ZOOM_OUT, "menubar.view.zoomOut",
                pane::zoomOutActive));
        bar.add(iconButton(MaterialIcons.Glyph.CENTER_FOCUS, "menubar.view.zoomReset",
                pane::zoomResetActive));
        bar.add(iconButton(MaterialIcons.Glyph.FIT_SCREEN, "menubar.view.zoomFit",
                pane::zoomToFitActive));
        return bar;
    }

    private static javax.swing.JButton iconButton(MaterialIcons.Glyph glyph, String tipKey,
                                                  Runnable action) {
        javax.swing.JButton b = new javax.swing.JButton(MaterialIcons.toolbar(glyph));
        b.setToolTipText(Messages.get(tipKey));
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static String selectedTitle(JTabbedPane tabs) {
        int i = tabs.getSelectedIndex();
        return i >= 0 ? tabs.getTitleAt(i) : null;
    }

    private static String windowTitle(String label) {
        String base = Messages.get("window.detached.title");
        return label == null || label.isEmpty() ? base : base + " — " + label;
    }

    /**
     * 新ウィンドウを配置する。サブモニタがあればそこへ (2 画面で並べて確認できるように)、
     * 無ければオーナーのスクリーンの右半分へ置き、メイン (通常は左寄せ) を覆い隠さないようにする。
     * 複数開くときは台数ぶんカスケードしてウィンドウ同士も重なりすぎないようにする。
     */
    private void placeWindow(JFrame frame) {
        int cascade = windows.size() * 28;
        GraphicsDevice primary = null;
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] screens = ge.getScreenDevices();
            if (owner != null && owner.getGraphicsConfiguration() != null) {
                primary = owner.getGraphicsConfiguration().getDevice();
            }
            // サブモニタがあればそこへ (中央寄せ + 台数ぶんカスケード)。
            for (GraphicsDevice gd : screens) {
                if (gd != primary) {
                    Rectangle b = gd.getDefaultConfiguration().getBounds();
                    frame.setSize(Math.min(1000, b.width - 80), Math.min(760, b.height - 80));
                    int x = b.x + Math.max(0, (b.width - frame.getWidth()) / 2) + cascade;
                    int y = b.y + Math.max(0, (b.height - frame.getHeight()) / 2) + cascade;
                    frame.setLocation(x, y);
                    return;
                }
            }
            // 単一モニタ: オーナーのスクリーンの右半分に配置してメイン (左半分想定) を隠さない。
            Rectangle sb = (primary != null ? primary : screens[0])
                    .getDefaultConfiguration().getBounds();
            int w = Math.min(1000, Math.max(560, sb.width / 2 - 24));
            int h = Math.min(900, Math.max(480, sb.height - 96));
            frame.setSize(w, h);
            int x = sb.x + sb.width - w - 16 - cascade;
            int y = sb.y + 40 + cascade;
            frame.setLocation(Math.max(sb.x, x), Math.max(sb.y, y));
            return;
        } catch (RuntimeException ignore) {
            // ヘッドレス/情報取得失敗時は下のフォールバックへ。
        }
        // 最終フォールバック (スクリーン情報が取れないとき)。
        frame.setSize(1000, 760);
        if (owner != null) {
            frame.setLocation(owner.getX() + 40 + cascade, owner.getY() + 40 + cascade);
        } else {
            frame.setLocationByPlatform(true);
        }
    }

    /** 開いている別ウィンドウがあるか。 */
    public boolean hasOpenWindows() {
        return !windows.isEmpty();
    }

    /**
     * タブ上限/描画保持数の Preferences 変更を、以後の新規ウィンドウと
     * 既に開いている全ウィンドウへ反映する (メイン pane と挙動を揃える)。
     */
    public void setTabBudget(int maxTabs, int keepRendered) {
        this.tabBudget = maxTabs;
        this.renderedTabs = keepRendered;
        if (maxTabs > 0) {
            for (DiagramTabPane pane : windows.values()) {
                pane.setTabBudget(maxTabs, keepRendered);
            }
        }
    }

    /**
     * アプリ終了時に全ての別ウィンドウを閉じる。各ペインの付箋保存 IO スレッドを止めてから破棄する。
     * (別ウィンドウには生成系の図タブしか入らないため未保存確認は不要。)
     */
    public void closeAll() {
        for (JFrame f : new ArrayList<>(windows.keySet())) {
            f.dispose(); // windowClosed 経由で pane.shutdown() + マップ除去
        }
        windows.clear();
    }
}
