// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.DiagramTabInternals.RenderResult;
import juml.app.uml.PlantUmlSvgRenderer.LinkArea;
import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;

import static juml.app.uml.DiagramTabInternals.entryOf;
import static juml.app.uml.DiagramTabInternals.extractSimpleClass;
import static juml.app.uml.DiagramTabInternals.menuItem;
import static juml.app.uml.DiagramTabInternals.parseClassFqnFromHref;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.util.Messages;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * すべてのダイアグラムを対等な「タブ (= エディタ)」として管理する VS Code 風タブペイン。
 *
 * <p>外部から渡された {@link JTabbedPane} に動的タブを挿入する。
 * 末尾 {@code fixedSuffix} 本はユーティリティタブ (Manifest / Impact / References /
 * Func Diff / Insights / Doxygen / TODO / Groups / Functions / Members) で、
 * 動的タブはその手前に挿入される。
 * 特別扱いの「Home タブ」は存在しない。</p>
 *
 * <p>各タブには {@link SvgPreviewPanel} と {@link PumlSourcePanel} を
 * {@link JSplitPane} で上下に配置する (入れ子タブなし)。各タブは自身の
 * {@link DiagramRequest} を保持し、{@link DiagramService} で描画する。</p>
 *
 * <p>同じタブキーのタブが既にある場合は新規作成せずフォーカスのみ移す。</p>
 */
public final class DiagramTabPane {

    private final JTabbedPane tabs;
    private final int fixedSuffix;
    private final Map<String, DiagramTab> openTabs = new LinkedHashMap<>();
    /** 閉じたタブの再オープン用スタック (Ctrl+Shift+T)。各要素は軽量な再オープンクロージャ。 */
    private final Deque<Runnable> closedTabs = new ArrayDeque<>();
    /** 再オープン履歴の上限。古い履歴を捨ててメモリを抑える。 */
    private static final int MAX_CLOSED_HISTORY = 10;
    /** 図タブのメモリ抑制 (LRU クローズ / 描画解放) を担う協調オブジェクト。 */
    private final TabMemoryManager tabMemory = new TabMemoryManager();
    /** Alt+Left/Right による前後ナビゲーション (VS Code 風 Go Back / Go Forward)。 */
    private final NavigationHistory navHistory = new NavigationHistory();
    /** UML に重ねる付箋メモのロード/保存配線を担うヘルパ。 */
    private final DiagramNotesBinder notesBinder = new DiagramNotesBinder(this::reportStatus);
    /** {@link #tabMemory} 適用中フラグ。クローズ起因の選択変更による再入を防ぐ。 */
    private boolean applyingTabBudget;
    private final ProjectAnalysisCache cache;
    private final DiagramState state;
    private final Consumer<String> statusReporter;
    private final DoubleConsumer zoomReporter;
    /** 動的タブにフォーカスが移ったとき、その情報 (由来ノード + 図種) を通知する。 */
    private Consumer<FocusedTab> onTabFocused;
    /** タブ右クリック「Reveal in Explorer」でツリーの該当ノードを選択するコールバック。 */
    private Consumer<TreeNodeOpenRequest> revealInTree;
    /**
     * 直近でフォーカスした動的ダイアグラムタブの由来ノード。
     * ユーティリティタブ (Functions / Members 等) を選択中でも「いま見ていた図の題材」を
     * 復元できるよう保持する。{@link #focusedTabRequest()} は選択中が動的タブでないと null
     * を返すため、コンテキスト連動リストはこちらを参照する。
     */
    private TreeNodeOpenRequest lastDiagramRequest;

    /** フォーカスが移ったタブの情報 (タブ ↔ ツリー ↔ ツールバー連動用)。 */
    public static final class FocusedTab {
        /** タブの由来ノード (ツリーでハイライトする対象)。汎用タブでは null。 */
        public final TreeNodeOpenRequest treeSync;
        /** タブが表示している図種。 */
        public final DiagramKind kind;

        FocusedTab(TreeNodeOpenRequest treeSync, DiagramKind kind) {
            this.treeSync = treeSync;
            this.kind = kind;
        }
    }

    /**
     * @param tabs           ダイアグラムタブを追加する外部 JTabbedPane
     * @param fixedSuffix    末尾に固定されたユーティリティタブ数
     * @param cache          解析キャッシュ
     * @param state          アクティブタブの描画結果を反映する共有状態 (エクスポート等が参照)
     * @param statusReporter ステータスバー更新コールバック
     * @param zoomReporter   アクティブタブのズーム率通知コールバック
     */
    public DiagramTabPane(JTabbedPane tabs, int fixedSuffix,
                          ProjectAnalysisCache cache, DiagramState state,
                          Consumer<String> statusReporter, DoubleConsumer zoomReporter) {
        this.tabs = tabs;
        this.fixedSuffix = fixedSuffix;
        this.cache = cache;
        this.state = state;
        this.statusReporter = statusReporter;
        this.zoomReporter = zoomReporter;
        tabs.addChangeListener(e -> handleTabSelectionChanged());
        this.mru = new TabMruController(tabs, () -> tabs.getTabCount() - fixedSuffix);
        this.mru.install();
    }

    /** MRU (Ctrl+Tab) 巡回コントローラ。動的タブのアクティブ化/クローズを通知する。 */
    private final TabMruController mru;

    /**
     * 動的タブにフォーカスが移ったときに呼ぶコールバックを設定する。
     * 受け取り側は由来ノードのツリーハイライトと図種のツールバー反映に使う。
     */
    public void setOnTabFocused(Consumer<FocusedTab> listener) {
        this.onTabFocused = listener;
    }

    /** タブ右クリック「Reveal in Explorer」でツリーへ遷移するコールバックを設定する。 */
    public void setRevealInTree(Consumer<TreeNodeOpenRequest> listener) {
        this.revealInTree = listener;
    }

    /** いま選択中のタブが動的ダイアグラムタブか (ユーティリティタブなら false)。 */
    public boolean dynamicTabFocused() {
        return tabs.getSelectedComponent() instanceof DiagramTab;
    }

    /** ダイアグラムタブが 1 つ以上開かれていて、かつ選択中か。 */
    public boolean hasActiveTab() {
        return tabs.getSelectedComponent() instanceof DiagramTab;
    }

    /** フォーカス中の動的タブの由来ノード。動的タブでない / 汎用タブなら null。 */
    public TreeNodeOpenRequest focusedTabRequest() {
        DiagramTab t = activeTab();
        return t != null ? t.treeSync : null;
    }

    /**
     * 直近でフォーカスした動的タブの由来ノード。ユーティリティタブ選択中でも保持される。
     * 一度も図タブを開いていなければ null。
     */
    public TreeNodeOpenRequest lastFocusedDiagramRequest() {
        return lastDiagramRequest;
    }

    /** フォーカス中タブの図種。動的タブでなければ null。 */
    public DiagramKind activeTabKind() {
        DiagramTab t = activeTab();
        return t != null ? t.spec.getKind() : null;
    }

    /** フォーカス中タブの SVG プレビュー。動的タブでなければ null。 */
    public SvgPreviewPanel activePreviewPanel() {
        DiagramTab t = activeTab();
        return t != null ? t.previewPanel : null;
    }

    private DiagramTab activeTab() {
        java.awt.Component sel = tabs.getSelectedComponent();
        return (sel instanceof DiagramTab) ? (DiagramTab) sel : null;
    }

    private void handleTabSelectionChanged() {
        DiagramTab tab = activeTab();
        if (tab == null) {
            return;
        }
        applyTabBudget(tab.key);
        mru.onActivated(tab, tab.label);
        navHistory.push(tab.key);
        lastDiagramRequest = tab.treeSync;
        tab.reportFocusStatus();
        tab.mirrorToState();
        if (zoomReporter != null) {
            zoomReporter.accept(tab.previewPanel.getZoomLevel());
        }
        if (onTabFocused != null) {
            onTabFocused.accept(new FocusedTab(tab.treeSync, tab.spec.getKind()));
        }
    }

    // -------------------------------------------------------------------------
    // タブを開く
    // -------------------------------------------------------------------------

    /** ツリー由来のリクエストに対応するタブを開く。既存タブがあればフォーカスのみ移す。 */
    public void addOrFocusTab(TreeNodeOpenRequest req) {
        if (req == null) {
            return;
        }
        openDiagram(req.tabKey(), req.displayLabel(), DiagramTabSupport.iconFor(req),
                DiagramTabSupport.toDiagramRequest(req), req);
    }

    /**
     * ツリー由来のリクエストのタブを開く / フォーカスし、その下部 Source ビューを前面に出す。
     * 「Open source」操作の受け口 (図ではなく実ソースを見たいケース)。
     */
    public void openSourceForRequest(TreeNodeOpenRequest req) {
        if (req == null) {
            return;
        }
        addOrFocusTab(req);
        // activeTab() ではなくキーで明示取得し、タブ予算処理などで選択が揺れても確実に対象を掴む。
        DiagramTab t = openTabs.get(req.tabKey());
        if (t != null) {
            t.selectSourceView();
        }
    }

    /**
     * 任意の図種・スコープのダイアグラムをタブとして開く。
     * 既存タブ ({@code key} 一致) があればフォーカスのみ移す。
     *
     * @param key      タブ識別キー (同一なら既存タブにフォーカス)
     * @param label    タブヘッダのラベル
     * @param icon     タブヘッダのアイコン
     * @param spec     描画リクエスト
     * @param treeSync ツリーハイライト用の由来ノード (無ければ null)
     */
    public void openDiagram(String key, String label, TreeNodeIcon icon,
                            DiagramRequest spec, TreeNodeOpenRequest treeSync) {
        if (spec == null || !cache.isLoaded()) {
            return;
        }
        DiagramTab existing = openTabs.get(key);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return;
        }
        DiagramTab tab = new DiagramTab(key, label, icon, spec, treeSync);
        openTabs.put(key, tab);
        int insertAt = tabs.getTabCount() - fixedSuffix;
        if (insertAt < 0) {
            insertAt = 0;
        }
        String tip = DiagramTabSupport.tooltipFor(spec, treeSync);
        tabs.insertTab(label, null, tab, tip, insertAt);
        java.awt.Component header = DiagramTabHeader.build(label, icon, tip,
                () -> closeTab(tab, key), e -> showTabMenu(tab, key, e),
                () -> tabs.setSelectedComponent(tab));
        tabs.setTabComponentAt(insertAt, header);
        // VS Code 風: タブをドラッグして並び替え可能にする (固定タブ境界は越えない)。
        TabReorderHandler.install(tabs, header, () -> tabs.getTabCount() - fixedSuffix);
        tabs.setSelectedIndex(insertAt);
        refreshTabLabels();
        tab.startRender();
        // 開きすぎ防止: 上限超過タブを閉じ、古いタブの描画を解放してメモリを抑える
        applyTabBudget(key);
    }

    /**
     * 同名ラベルのタブが複数開いているとき、VS Code 同様にパッケージ等の補足を付けて
     * 区別できるようにする (重複が無いタブは基本ラベルへ戻す)。
     */
    private void refreshTabLabels() {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (DiagramTab t : openTabs.values()) {
            counts.merge(t.label, 1, Integer::sum);
        }
        int dyn = tabs.getTabCount() - fixedSuffix;
        for (int i = 0; i < dyn && i < tabs.getTabCount(); i++) {
            java.awt.Component c = tabs.getComponentAt(i);
            if (!(c instanceof DiagramTab)) {
                continue;
            }
            DiagramTab t = (DiagramTab) c;
            String display = t.label;
            if (counts.getOrDefault(t.label, 0) > 1 && t.treeSync != null) {
                String q = t.treeSync.disambiguator();
                if (!q.isEmpty()) {
                    display = t.label + "  ·  " + q;
                }
            }
            tabs.setTitleAt(i, display);
            DiagramTabHeader.updateTitle(tabs.getTabComponentAt(i), display);
        }
    }

    /** アクティブな図タブに付箋メモを追加する (メニュー / コマンドパレット用)。 */
    public void addNoteToActiveTab() {
        DiagramTab t = activeTab();
        if (t != null) {
            t.previewPanel.addNoteAtViewportCenter();
        } else {
            reportStatus(Messages.get("note.noActiveTab"));
        }
    }

    /** アクティブな図タブの付箋一覧サイドパネルを開閉する (メニュー / コマンドパレット用)。 */
    public void toggleActiveNotesPanel() {
        DiagramTab t = activeTab();
        if (t != null) {
            t.toggleNotesPanel();
        } else {
            reportStatus(Messages.get("note.noActiveTab"));
        }
    }

    /** アクティブな図タブの図内検索バー (Ctrl+F) を表示する (メニュー / コマンドパレット用)。 */
    public void activateFindInActiveTab() {
        DiagramTab t = activeTab();
        if (t != null) {
            t.activateFind();
        } else {
            reportStatus(Messages.get("diagram.find.noActiveTab"));
        }
    }

    /** アクティブな図タブの下部 Source ビューを前面に出す (メニュー / コマンドパレット用)。 */
    public void showSourceForActiveTab() {
        DiagramTab t = activeTab();
        if (t != null) {
            t.selectSourceView();
        } else {
            reportStatus(Messages.get("source.noActiveTab"));
        }
    }

    /** アクティブタブの描画リクエストを差し替えて再描画する (スコープ/プリセット適用など)。 */
    public void setActiveTabSpecAndRender(DiagramRequest spec) {
        DiagramTab t = activeTab();
        if (t != null && spec != null) {
            t.spec = spec;
            t.startRender();
        }
    }

    /** アクティブタブを現在のリクエストで再描画する (F5 / Refresh)。 */
    public void rerenderActiveTab() {
        DiagramTab t = activeTab();
        if (t != null) {
            t.startRender();
        }
    }

    /** 開いているすべてのダイアグラムタブを再描画する (スタイル変更時など)。 */
    public void rerenderAllTabs() {
        for (DiagramTab t : new ArrayList<>(openTabs.values())) {
            t.startRender();
        }
    }

    public void zoomInActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomIn();
        }
    }

    public void zoomOutActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomOut();
        }
    }

    public void zoomResetActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomReset();
        }
    }

    public void zoomToFitActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomToFit();
        }
    }

    private void showTabMenu(DiagramTab tab, String key, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem close = new JMenuItem(Messages.get("tab.menu.close"));
        close.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.CLOSE));
        close.addActionListener(a -> closeTab(tab, key));
        menu.add(close);
        JMenuItem others = new JMenuItem(Messages.get("tab.menu.closeOthers"));
        others.addActionListener(a -> closeOtherTabs(key));
        others.setEnabled(openTabs.size() > 1);
        menu.add(others);
        JMenuItem right = new JMenuItem(Messages.get("tab.menu.closeRight"));
        right.addActionListener(a -> closeTabsToRight(key));
        right.setEnabled(hasTabsToRight(key));
        menu.add(right);
        JMenuItem all = new JMenuItem(Messages.get("tab.menu.closeAll"));
        all.addActionListener(a -> closeAllTabs());
        all.setEnabled(!openTabs.isEmpty());
        menu.add(all);
        if (tab.treeSync != null && revealInTree != null) {
            menu.addSeparator();
            JMenuItem reveal = new JMenuItem(Messages.get("tab.menu.revealInExplorer"));
            reveal.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SIDEBAR));
            reveal.addActionListener(a -> revealInTree.accept(tab.treeSync));
            menu.add(reveal);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /** {@code keepKey} 以外のすべてのダイアグラムタブを閉じる。 */
    void closeOtherTabs(String keepKey) {
        for (Map.Entry<String, DiagramTab> en : new ArrayList<>(openTabs.entrySet())) {
            if (!en.getKey().equals(keepKey)) {
                closeTab(en.getValue(), en.getKey());
            }
        }
    }

    /** アクティブタブ以外のすべてのダイアグラムタブを閉じる。 */
    void closeOtherTabsExceptActive() {
        java.awt.Component sel = tabs.getSelectedComponent();
        String activeKey = null;
        for (Map.Entry<String, DiagramTab> en : openTabs.entrySet()) {
            if (en.getValue() == sel) {
                activeKey = en.getKey();
                break;
            }
        }
        closeOtherTabs(activeKey);
    }

    /** アクティブタブの右にある図タブをすべて閉じる (メニュー用)。 */
    void closeTabsToRightOfActive() {
        java.awt.Component sel = tabs.getSelectedComponent();
        String activeKey = null;
        for (Map.Entry<String, DiagramTab> en : openTabs.entrySet()) {
            if (en.getValue() == sel) {
                activeKey = en.getKey();
                break;
            }
        }
        if (activeKey != null) {
            closeTabsToRight(activeKey);
        }
    }

    /** すべてのダイアグラムタブを閉じる (ユーティリティタブは残す)。 */
    void closeAllTabs() {
        closeOtherTabs(null);
    }

    /** 指定タブより右にある図タブをすべて閉じる。 */
    void closeTabsToRight(String pivotKey) {
        java.util.List<String> keys = new ArrayList<>(openTabs.keySet());
        int idx = keys.indexOf(pivotKey);
        if (idx < 0) {
            return;
        }
        for (int i = keys.size() - 1; i > idx; i--) {
            String k = keys.get(i);
            DiagramTab t = openTabs.get(k);
            if (t != null) {
                closeTab(t, k);
            }
        }
    }

    /** 指定タブの右側に図タブがあるか。 */
    private boolean hasTabsToRight(String key) {
        java.util.List<String> keys = new ArrayList<>(openTabs.keySet());
        int idx = keys.indexOf(key);
        return idx >= 0 && idx < keys.size() - 1;
    }

    /** アクティブな動的タブを閉じる。Ctrl+W / File &gt; Close Tab 用 (汎用タブには無作用)。 */
    public void closeActiveTab() {
        DiagramTab t = activeTab();
        if (t != null) {
            closeTab(t, t.key);
        }
    }

    private void closeTab(DiagramTab tab, String key) {
        closeTab(tab, key, true);
    }

    /**
     * タブを閉じる。{@code recordForReopen} が true のときだけ再オープン履歴に積む。
     * メモリ上限による自動クローズ (LRU) は履歴を汚さないよう false で呼ぶ。
     */
    private void closeTab(DiagramTab tab, String key, boolean recordForReopen) {
        if (recordForReopen) {
            pushClosedTab(tab);
        }
        if (tab.activeWorker != null) {
            tab.activeWorker.cancel(true);
        }
        tab.previewPanel.notes().setOnChange(null);
        int index = tabs.indexOfComponent(tab);
        if (index >= 0) {
            tabs.remove(index);
        }
        openTabs.remove(key);
        mru.onClosed(tab);
        navHistory.remove(key);
        refreshTabLabels();
        tabMemory.onClose(key);
    }

    private void pushClosedTab(DiagramTab tab) {
        // 重い DiagramTab を捕捉しないよう、再オープンに必要な軽量フィールドだけを束ねる。
        final String key = tab.key;
        final String label = tab.label;
        final TreeNodeIcon icon = tab.icon;
        final DiagramRequest spec = tab.spec;
        final TreeNodeOpenRequest treeSync = tab.treeSync;
        closedTabs.push(() -> openDiagram(key, label, icon, spec, treeSync));
        while (closedTabs.size() > MAX_CLOSED_HISTORY) {
            closedTabs.removeLast();
        }
    }

    /**
     * 直近に閉じたタブを再オープンする (Ctrl+Shift+T)。既に同キーのタブが開いていれば
     * {@link #openDiagram} 側でフォーカスのみ移す。履歴が空なら案内ステータスを出す。
     */
    public void reopenLastClosedTab() {
        Runnable reopen = closedTabs.poll();
        if (reopen != null) {
            reopen.run();
        } else {
            reportStatus(Messages.get("status.noClosedTab"));
        }
    }

    /** Alt+Left: 直前にフォーカスしていたタブに戻る。 */
    public void navigateBack() {
        String key = navHistory.back();
        if (key != null) {
            focusTabByKey(key);
        }
    }

    /** Alt+Right: navigateBack で戻った先からひとつ進む。 */
    public void navigateForward() {
        String key = navHistory.forward();
        if (key != null) {
            focusTabByKey(key);
        }
    }

    private void focusTabByKey(String key) {
        DiagramTab t = openTabs.get(key);
        if (t == null) {
            return;
        }
        navHistory.setNavigating(true);
        try {
            tabs.setSelectedComponent(t);
        } finally {
            navHistory.setNavigating(false);
        }
    }

    /**
     * アクティブタブを起点に、タブ数上限のクローズと古いタブの描画解放を適用する。
     * クローズが選択変更を誘発して再入しないようフラグでガードする。
     */
    private void applyTabBudget(String activeKey) {
        if (applyingTabBudget) {
            return;
        }
        applyingTabBudget = true;
        try {
            tabMemory.onActivate(activeKey, openTabs.size(), tabBudgetActions);
        } finally {
            applyingTabBudget = false;
        }
    }

    /** {@link TabMemoryManager} からの実操作 (キー → タブ) を Swing 側で実行する。 */
    private final TabMemoryManager.Actions tabBudgetActions = new TabMemoryManager.Actions() {
        @Override
        public void closeTab(String key) {
            DiagramTab t = openTabs.get(key);
            if (t != null) {
                // 上限超過でタブが自動クローズされたことを通知し、無通知で消える驚きを防ぐ。
                reportStatus(Messages.get("status.tabAutoClosed") + t.label);
                DiagramTabPane.this.closeTab(t, key, false);
            }
        }

        @Override
        public void releaseRender(String key) {
            DiagramTab t = openTabs.get(key);
            if (t != null) {
                t.releaseRender();
            }
        }

        @Override
        public void ensureRendered(String key) {
            DiagramTab t = openTabs.get(key);
            if (t != null && t.needsRender()) {
                t.startRender();
            }
        }
    };

    private void reportStatus(String msg) {
        if (statusReporter != null) {
            statusReporter.accept(msg);
        }
    }

    /**
     * 1 タブ分の内容。SVG プレビューと PlantUML ソースを JSplitPane で上下に配置。
     * 自身の {@link DiagramRequest} を保持し、差し替え再描画にも対応する。
     */
    private final class DiagramTab extends JPanel {
        private final String key;
        private final String label;
        /** タブヘッダのアイコン (再オープン時に同じ見た目で復元するため保持)。 */
        private final TreeNodeIcon icon;
        /** ツリーハイライト用の由来ノード (汎用タブでは null)。 */
        private final TreeNodeOpenRequest treeSync;
        /** このタブが描画するリクエスト (差し替え可能)。 */
        private DiagramRequest spec;
        private final SvgPreviewPanel previewPanel = new SvgPreviewPanel();
        /** 表示中の図に対するインクリメンタル検索バー (Ctrl+F)。 */
        private final DiagramFindBar findBar = new DiagramFindBar(previewPanel, this::revalidate);
        private final PumlSourcePanel sourcePanel  = new PumlSourcePanel();
        /** 実際の Java/Kotlin ソースを表示するパネル (VS Code 風)。 */
        private final JavaSourcePanel javaSourcePanel = new JavaSourcePanel();
        /** 下部の「PlantUML / Source」切替タブ。 */
        private final JTabbedPane bottomTabs = new JTabbedPane();
        /** プレビュー / メッセージ(描画中・失敗・空) を切り替えるカード。 */
        private final java.awt.CardLayout cards = new java.awt.CardLayout();
        private final JPanel viewCards = new JPanel(cards);
        /** 付箋一覧サイドパネル (既定は非表示)。 */
        private NotesSidePanel notesPanel;
        /** プレビュー領域と付箋一覧の左右分割。 */
        private JSplitPane hsplit;
        private final JLabel messageLabel = new JLabel("", javax.swing.SwingConstants.CENTER);
        private final javax.swing.JProgressBar renderSpinner = new javax.swing.JProgressBar();
        private String renderedPuml;
        private String renderedSvgXml;
        private String lastStatus;
        /** 描画済み SVG をメモリ節約のため解放した状態か (再フォーカスで再描画する)。 */
        private boolean renderReleased;
        /** 進行中の描画ワーカー。再描画/クローズ時にキャンセルして競合・無駄を防ぐ。 */
        private SwingWorker<RenderResult, Void> activeWorker;

        DiagramTab(String key, String label, TreeNodeIcon icon,
                   DiagramRequest spec, TreeNodeOpenRequest treeSync) {
            super(new java.awt.BorderLayout());
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.spec = spec;
            this.treeSync = treeSync;

            // 図プレビュー (スクロール) + 下端の図内検索バーを 1 枚の "view" カードにまとめる。
            JPanel viewPanel = new JPanel(new java.awt.BorderLayout());
            viewPanel.add(new JScrollPane(previewPanel), java.awt.BorderLayout.CENTER);
            viewPanel.add(findBar, java.awt.BorderLayout.SOUTH);
            viewCards.add(viewPanel, "view");
            JPanel msgPanel = new JPanel(new java.awt.GridBagLayout());
            java.awt.Color msgBg = javax.swing.UIManager.getColor("Panel.background");
            msgPanel.setBackground(msgBg != null ? msgBg : java.awt.Color.WHITE);
            java.awt.Color msgFg = javax.swing.UIManager.getColor("Label.foreground");
            messageLabel.setForeground(msgFg != null ? msgFg : new Color(0x555555));
            renderSpinner.setIndeterminate(true);
            renderSpinner.setPreferredSize(new java.awt.Dimension(200, 6));
            renderSpinner.setVisible(false);
            JPanel msgInner = new JPanel(new java.awt.BorderLayout(0, 12));
            msgInner.setOpaque(false);
            msgInner.add(messageLabel, java.awt.BorderLayout.CENTER);
            msgInner.add(renderSpinner, java.awt.BorderLayout.SOUTH);
            msgPanel.add(msgInner);
            viewCards.add(msgPanel, "msg");

            // 下部は「PlantUML テキスト」と「実ソース (Java/Kotlin)」を切り替えられるタブ。
            javaSourcePanel.setStatusReporter(DiagramTabPane.this::reportStatus);
            bottomTabs.addTab(Messages.get("tab.plantuml"), sourcePanel);
            bottomTabs.addTab(Messages.get("tab.source"), javaSourcePanel);
            bottomTabs.addChangeListener(e -> {
                if (bottomTabs.getSelectedComponent() == javaSourcePanel) {
                    ensureSourceLoaded();
                }
            });

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, viewCards, bottomTabs);
            split.setResizeWeight(0.7);
            split.setDividerLocation(0.7);
            // プレビュー(+ソース) の右に付箋一覧パネルを置く左右分割 (既定は畳む)。
            notesPanel = new NotesSidePanel(previewPanel);
            notesPanel.setVisible(false);
            hsplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, split, notesPanel);
            hsplit.setResizeWeight(1.0);
            hsplit.setDividerSize(0);
            add(hsplit, java.awt.BorderLayout.CENTER);
            previewPanel.setOnLinkClick(this::handleLinkClick);
            previewPanel.setOnLinkPopup(this::handleLinkPopup);
            previewPanel.setCopyFeedbackListener(DiagramTabPane.this::reportStatus);
            previewPanel.setZoomChangeListener(() -> {
                if (isActive() && zoomReporter != null) {
                    zoomReporter.accept(previewPanel.getZoomLevel());
                }
            });
            // 付箋メモを .juml/notes.json からロードし、変更時に保存するよう配線
            notesBinder.bind(previewPanel, cache.getProjectRoot(), key);
        }

        private boolean isActive() {
            return tabs.getSelectedComponent() == this;
        }

        /** 付箋一覧サイドパネルの表示/非表示を切り替える。 */
        void toggleNotesPanel() {
            boolean show = !notesPanel.isVisible();
            notesPanel.setVisible(show);
            hsplit.setDividerSize(show ? 6 : 0);
            if (show) {
                notesPanel.refresh();
                int w = hsplit.getWidth();
                hsplit.setDividerLocation(Math.max(120, (w > 0 ? w : 800) - 260));
            }
            hsplit.revalidate();
            hsplit.repaint();
        }

        /** Source サブタブを前面に出してソースを表示する (ツリーの「Open source」用)。 */
        void selectSourceView() {
            bottomTabs.setSelectedComponent(javaSourcePanel);
            ensureSourceLoaded();
        }

        /**
         * このタブの題材クラスの実ソースを解決して {@link #javaSourcePanel} に読み込む。
         * 解決ロジックは {@link DiagramTabSupport#showSource} に委譲する。
         */
        private void ensureSourceLoaded() {
            DiagramTabSupport.showSource(javaSourcePanel, treeSync, cache);
        }

        /** プレビュー(SVG)カードを前面に出す。 */
        private void showPreviewCard() {
            cards.show(viewCards, "view");
        }

        /** 図内検索バー (Ctrl+F) を表示してフォーカスする。 */
        void activateFind() {
            showPreviewCard();
            findBar.activate();
        }

        private void showMessageCard(String html) {
            showMessageCard(html, false);
        }

        private void showMessageCard(String html, boolean showSpinner) {
            messageLabel.setText("<html><div style='text-align:center;width:460px'>"
                    + html + "</div></html>");
            renderSpinner.setVisible(showSpinner);
            cards.show(viewCards, "msg");
        }

        private void setStatus(String msg) {
            lastStatus = msg;
            reportStatus(msg);
        }

        void reportFocusStatus() {
            reportStatus(lastStatus != null ? lastStatus : label + " (tab)");
        }

        /** 描画結果と図のパラメータを共有 {@link DiagramState} に反映する (エクスポート/ダイアログ用)。 */
        void mirrorToState() {
            if (state == null) {
                return;
            }
            state.currentPuml = renderedPuml;
            state.currentSvgXml = renderedSvgXml;
            state.currentScope = spec.getScope();
            state.sequenceEntry = null;
            state.activityEntry = null;
            state.callGraphEntry = null;
            switch (spec.getKind()) {
                case SEQUENCE:
                    state.sequenceEntry = entryOf(spec);
                    state.sequenceHiddenParticipants.clear();
                    state.sequenceHiddenParticipants.addAll(spec.getSequenceHiddenParticipants());
                    break;
                case ACTIVITY:
                    state.activityEntry = entryOf(spec);
                    break;
                case CALLGRAPH:
                    state.callGraphEntry = entryOf(spec);
                    break;
                case LAYOUT:
                case LAYOUT_SCREEN:
                case LAYOUT_RENDER:
                    state.currentLayoutKey = spec.getLayoutKey();
                    break;
                case NAVIGATION:
                    state.currentNavigationKey = spec.getNavigationGraphKey();
                    break;
                default:
                    break;
            }
        }

        /** 描画解放後で再描画が必要か。 */
        boolean needsRender() {
            return renderReleased;
        }

        /**
         * 描画済み SVG (ベクタ木) を解放してメモリを返す。PlantUML テキストは軽量なので
         * 残し、再フォーカス時に {@link #startRender()} で再描画する。
         */
        void releaseRender() {
            if (renderReleased) {
                return;
            }
            previewPanel.setSvgGraphicsNode(null, 0, 0);
            previewPanel.setLinkAreas(null);
            previewPanel.setTextItems(java.util.Collections.emptyList());
            findBar.reset();
            renderedSvgXml = null;
            renderReleased = true;
            showMessageCard("<b>" + esc(label) + "</b><br><br>" + Messages.get("tab.released"));
        }

        void startRender() {
            renderReleased = false;
            // 旧図のヒットを引きずらないよう、再描画のたびに検索状態をリセットする。
            findBar.reset();
            setStatus(Messages.get("status.rendering") + " " + label + " ...");
            showMessageCard("<b>" + Messages.get("status.rendering") + " " + esc(label) + " …</b>", true);
            final DiagramRequest dreq = spec;
            if (activeWorker != null) {
                activeWorker.cancel(true); // 旧描画を破棄して競合・無駄な処理を防ぐ
            }
            SwingWorker<RenderResult, Void> worker = new SwingWorker<RenderResult, Void>() {
                private Throwable error;
                private String pumlOnError;

                @Override
                protected RenderResult doInBackground() {
                    try {
                        String puml = DiagramService.generatePuml(dreq, cache);
                        pumlOnError = puml;
                        RenderedSvg svg = PlantUmlSvgRenderer.render(puml);
                        return new RenderResult(puml, svg);
                    } catch (Throwable ex) {
                        error = ex;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    if (isCancelled() || this != activeWorker) {
                        return; // キャンセル済み、または新しい描画に置き換わったので破棄
                    }
                    if (error != null) {
                        if (pumlOnError != null) {
                            sourcePanel.setText(pumlOnError);
                        }
                        previewPanel.setSvgGraphicsNode(null, 0, 0);
                        renderedPuml = pumlOnError;
                        renderedSvgXml = null;
                        if (isActive()) {
                            mirrorToState();
                        }
                        showMessageCard(failureMessage(error));
                        setStatus(label + ": " + Messages.get("status.renderFailed") + " " + failureReason(error));
                        return;
                    }
                    try {
                        RenderResult r = get();
                        if (r == null || r.svg == null) {
                            sourcePanel.setText(r != null ? r.puml : "");
                            renderedPuml = r != null ? r.puml : null;
                            renderedSvgXml = null;
                            if (isActive()) {
                                mirrorToState();
                            }
                            showMessageCard("<b>" + Messages.get("tab.noDiagram.title") + "</b><br><br>" + Messages.get("tab.noDiagram.hint"));
                            setStatus(label + ": " + Messages.get("status.noDiagramShort"));
                            return;
                        }
                        previewPanel.setSvgGraphicsNode(r.svg.getRoot(),
                                r.svg.getWidth(), r.svg.getHeight());
                        previewPanel.setLinkAreas(r.svg.getLinkAreas());
                        previewPanel.setTextItems(r.svg.getTextItems());
                        sourcePanel.setText(r.puml);
                        renderedPuml = r.puml;
                        renderedSvgXml = r.svg.getSvgXml();
                        showPreviewCard();
                        if (isActive()) {
                            mirrorToState();
                        }
                        setStatus(label + " " + Messages.get("status.rendered") + " ("
                                + (int) Math.round(r.svg.getWidth()) + "x" + (int) Math.round(r.svg.getHeight()) + ", SVG)");
                    } catch (Exception ex) {
                        showMessageCard(failureMessage(ex));
                        setStatus(label + ": " + ex.getMessage());
                    }
                }
            };
            activeWorker = worker;
            worker.execute();
        }

        private String failureMessage(Throwable error) {
            return DiagramFailureMessage.forError(error);
        }

        private String failureReason(Throwable error) {
            return DiagramFailureMessage.reason(error);
        }

        private String esc(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private void handleLinkClick(LinkArea link, MouseEvent event) {
            if (link == null) {
                return;
            }
            String href = link.getHref();
            if (href == null) {
                return;
            }
            if (href.startsWith("juml://method/")) {
                showMethodMenuInTab(href, event);
                return;
            }
            String fqn = parseClassFqnFromHref(href);
            if (fqn == null) {
                return;
            }
            cache.getIndex().header(fqn).ifPresent(
                    ci -> addOrFocusTab(TreeNodeOpenRequest.classNode(ci)));
        }

        private void showMethodMenuInTab(String href, MouseEvent event) {
            String path = href.substring("juml://method/".length());
            int hash = path.lastIndexOf('#');
            if (hash < 0) {
                return;
            }
            String classFqn = path.substring(0, hash);
            String methodName = path.substring(hash + 1);
            if (classFqn.isEmpty() || methodName.isEmpty()) {
                return;
            }
            JavaClassInfo classInfo = cache.getIndex().header(classFqn).orElse(null);
            if (classInfo == null) {
                classInfo = new JavaClassInfo();
                classInfo.setSimpleName(extractSimpleClass(classFqn));
            }
            JavaMethodInfo methodInfo = new JavaMethodInfo();
            methodInfo.setName(methodName);
            final JavaClassInfo ci = classInfo;
            final JavaMethodInfo mi = methodInfo;
            JPopupMenu menu = new JPopupMenu();
            JMenuItem seqItem = new JMenuItem(DiagramKind.SEQUENCE.getDisplayName());
            seqItem.setIcon(ToolBarBuilder.kindIcon(DiagramKind.SEQUENCE, 14));
            seqItem.addActionListener(e -> addOrFocusTab(
                    TreeNodeOpenRequest.method(ci, mi, DiagramKind.SEQUENCE)));
            menu.add(seqItem);
            JMenuItem actItem = new JMenuItem(DiagramKind.ACTIVITY.getDisplayName());
            actItem.setIcon(ToolBarBuilder.kindIcon(DiagramKind.ACTIVITY, 14));
            actItem.addActionListener(e -> addOrFocusTab(
                    TreeNodeOpenRequest.method(ci, mi, DiagramKind.ACTIVITY)));
            menu.add(actItem);
            menu.show(event.getComponent(), event.getX(), event.getY());
        }

        private void handleLinkPopup(LinkArea link, MouseEvent event) {
            if (event == null) {
                return;
            }
            JPopupMenu popup = new JPopupMenu(Messages.get("export.title"));
            popup.add(menuItem(Messages.get("export.saveSvg"), () -> exportTabAs(UmlExporter.Format.SVG)));
            popup.add(menuItem(Messages.get("export.savePng"), () -> exportTabAs(UmlExporter.Format.PNG)));
            popup.add(menuItem(Messages.get("export.savePuml"), () -> exportTabAs(UmlExporter.Format.PUML)));
            popup.addSeparator();
            popup.add(menuItem(Messages.get("note.menu.addHere"),
                    () -> previewPanel.addNoteAtPanelPoint(event.getPoint())));
            // クラス要素上での右クリックなら、その要素に追従する付箋 (ELEMENT アンカー) を追加できる。
            String fqn = link == null ? null : parseClassFqnFromHref(link.getHref());
            if (fqn != null) {
                popup.add(menuItem(Messages.get("note.menu.addToElement"),
                        () -> previewPanel.addElementNote(fqn)));
            }
            if (!previewPanel.getTextItems().isEmpty()) {
                popup.addSeparator();
                popup.add(menuItem(Messages.get("context.copyAllText"),
                        previewPanel::copyAllText));
                JMenuItem hint = new JMenuItem(Messages.get("context.selectTextHint"));
                hint.setEnabled(false);
                popup.add(hint);
            }
            popup.show(event.getComponent(), event.getX(), event.getY());
        }

        private void exportTabAs(UmlExporter.Format fmt) {
            DiagramTabSupport.exportPuml(this, renderedPuml, previewPanel, fmt,
                    DiagramTabPane.this::reportStatus);
        }
    }

}
