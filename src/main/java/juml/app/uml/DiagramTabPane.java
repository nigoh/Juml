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
 * Func Diff / Insights / Doxygen / TODO / Groups / Functions / Members / Git) で、
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
    /** トースト通知 (LRU 自動クローズなど) のコールバック。 */
    private Consumer<String> toastNotifier;
    /** タブ右クリック「Close All」の委譲先 (確認付き)。未設定なら確認なしで閉じる。 */
    private Runnable closeAllRequestHandler;
    /** タブ内上下分割の既定比率 (Setting から取得)。 */
    private double tabSplitRatio = 0.7;
    /** VS Code 風プレビュータブのキー (null = プレビューなし)。 */
    private String previewTabKey;
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
        // 巡回確定時、最終的に選ばれたタブだけを本活性化する (巡回中は履歴/LRU を抑止)。
        this.mru.setOnCommit(this::handleTabSelectionChanged);
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

    /** トースト通知コールバックを設定する (LRU 自動クローズなど)。 */
    public void setToastNotifier(Consumer<String> notifier) {
        this.toastNotifier = notifier;
    }

    /**
     * タブヘッダ右クリックの「Close All」を処理するハンドラを設定する。
     * 確認ダイアログの表示はフレーム側の責務のため、メニュー経路
     * (File &gt; Close All Tabs) と同じ確認ロジックへ委譲するのに使う。
     * 未設定の場合は従来どおり確認なしで {@link #closeAllTabs()} を呼ぶ。
     */
    public void setCloseAllRequestHandler(Runnable handler) {
        this.closeAllRequestHandler = handler;
    }

    /** タブ内上下分割の既定比率を設定する (新規タブに適用)。 */
    public void setTabSplitRatio(double ratio) {
        this.tabSplitRatio = Math.max(0.1, Math.min(0.9, ratio));
    }

    /** 直近のタブ内上下分割比率を返す (永続化用)。 */
    public double getTabSplitRatio() {
        return tabSplitRatio;
    }

    /**
     * Preferences のタブ上限/描画保持数の変更を再起動なしで反映する。
     * 既に開いているタブ数が新しい上限を超えていても、ここでは閉じない
     * (次にタブをアクティブ化したタイミングで {@link TabMemoryManager} が調整する)。
     */
    public void setTabBudget(int maxTabs, int keepRendered) {
        tabMemory.configure(maxTabs, keepRendered);
    }

    /** いま選択中のタブが動的ダイアグラムタブか (ユーティリティタブなら false)。 */
    public boolean dynamicTabFocused() {
        return tabs.getSelectedComponent() instanceof DiagramTab;
    }

    /** 開いている動的ダイアグラムタブの枚数 (ユーティリティタブは含まない)。 */
    public int dynamicTabCount() {
        return openTabs.size();
    }

    /** 再オープン (Ctrl+Shift+T) できる閉じタブ履歴の件数。 */
    public int closedTabHistorySize() {
        return closedTabs.size();
    }

    /**
     * アプリ終了前に呼ぶ後始末。付箋メモの保存 IO スレッドを停止し、
     * キュー内の保存タスクが完了するまで短時間待つ (データロス防止)。
     */
    public void shutdown() {
        notesBinder.shutdown();
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

    /** フォーカス中タブの図種。動的タブでない / 自由編集エディタタブなら null。 */
    public DiagramKind activeTabKind() {
        DiagramTab t = activeTab();
        return t != null && t.spec != null ? t.spec.getKind() : null;
    }

    /** フォーカス中のタブが自由編集 PlantUML エディタタブか。 */
    public boolean activeTabIsPumlEditor() {
        DiagramTab t = activeTab();
        return t != null && t.isEditor();
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
        // ドラッグ並び替えの remove→insert 過渡で発火する一時的な選択変更は無視する
        // (並び替え完了時に必要なら TabReorderHandler が 1 回だけ再通知する)。
        if (Boolean.TRUE.equals(tabs.getClientProperty(TabReorderHandler.CLIENT_PROP_REORDERING))) {
            return;
        }
        // Ctrl+Tab の MRU 巡回中は、通過しただけの中間タブでナビ履歴/LRU 予算を汚さない。
        // 確定時に TabMruController の onCommit がこのメソッドを 1 回だけ呼び直す。
        if (Boolean.TRUE.equals(tabs.getClientProperty(TabMruController.CLIENT_PROP_TRAVERSING))) {
            return;
        }
        DiagramTab tab = activeTab();
        if (tab == null) {
            // ユーティリティタブ (Functions/Members/Manifest 等) 選択時は図タブが無いことを
            // 通知し、ステータスバーの題材表示クリアと "Add Note" ボタンの無効化を促す。
            // (通知しないと直前の図タブの状態が残り、見た目と挙動が食い違う。)
            // 共有 state の描画結果も消す。残すと閉じたタブの図が Export/Copy SVG で
            // 「現在の図」として出力されてしまう。
            if (state != null) {
                state.currentPuml = null;
                state.currentSvgXml = null;
                state.sequenceEntry = null;
                state.activityEntry = null;
                state.callGraphEntry = null;
                state.currentLayoutKey = null;
                state.currentNavigationKey = null;
                state.sequenceHiddenParticipants.clear();
            }
            if (onTabFocused != null) {
                onTabFocused.accept(null);
            }
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
            // 自由編集エディタタブは spec を持たないため図種は null で通知する
            // (受け取り側はツールバー/メニューの図種反映をスキップする)。
            onTabFocused.accept(new FocusedTab(tab.treeSync,
                    tab.spec != null ? tab.spec.getKind() : null));
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
        pinPreviewTab();
        openDiagram(req.tabKey(), req.displayLabel(), DiagramTabSupport.iconFor(req),
                DiagramTabSupport.toDiagramRequest(req), req, false);
    }

    /**
     * VS Code 風プレビュータブとして開く。既存タブがあればフォーカスのみ移す。
     * 既にプレビュー中の別タブがあれば置き換える。
     */
    public void addOrFocusPreviewTab(TreeNodeOpenRequest req) {
        if (req == null) {
            return;
        }
        String key = req.tabKey();
        DiagramTab existing = openTabs.get(key);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return;
        }
        if (previewTabKey != null && !previewTabKey.equals(key)) {
            DiagramTab old = openTabs.get(previewTabKey);
            if (old != null) {
                // プレビュータブの自動置換はユーザーの「閉じる」操作ではないため、
                // Ctrl+Shift+T の再オープン履歴には積まない (明示的に閉じたタブが
                // プレビューの置換ラッシュで履歴から押し出されるのを防ぐ)。
                closeTab(old, previewTabKey, false);
            }
            previewTabKey = null;
        }
        openDiagram(key, req.displayLabel(), DiagramTabSupport.iconFor(req),
                DiagramTabSupport.toDiagramRequest(req), req, true);
    }

    /** プレビュータブを確定 (ピン留め) して通常タブにする。 */
    public void pinPreviewTab() {
        if (previewTabKey == null) {
            return;
        }
        DiagramTab tab = openTabs.get(previewTabKey);
        if (tab != null) {
            int idx = tabs.indexOfComponent(tab);
            if (idx >= 0) {
                DiagramTabHeader.setPreview(tabs.getTabComponentAt(idx), false);
            }
        }
        previewTabKey = null;
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

    public void openDiagram(String key, String label, TreeNodeIcon icon,
                            DiagramRequest spec, TreeNodeOpenRequest treeSync) {
        openDiagram(key, label, icon, spec, treeSync, false);
    }

    private void openDiagram(String key, String label, TreeNodeIcon icon,
                             DiagramRequest spec, TreeNodeOpenRequest treeSync,
                             boolean preview) {
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
        java.awt.Component header = buildTabHeader(tab);
        tabs.setTabComponentAt(insertAt, header);
        if (preview) {
            previewTabKey = key;
            DiagramTabHeader.setPreview(header, true);
        }
        // VS Code 風: タブをドラッグして並び替え可能にする (固定タブ境界は越えない)。
        TabReorderHandler.install(tabs, header, () -> tabs.getTabCount() - fixedSuffix);
        tabs.setSelectedIndex(insertAt);
        refreshTabLabels();
        tab.startRender();
        // 開きすぎ防止: 上限超過タブを閉じ、古いタブの描画を解放してメモリを抑える
        applyTabBudget(key);
    }

    /**
     * タブヘッダ (アイコン + ラベル + × ボタン) を組み立てる。閉じる/メニューの
     * クローズャは {@code tab.key} を実行時に読むため、図種切替でキーが変わっても
     * 常に最新のキーで動作する。
     */
    private java.awt.Component buildTabHeader(DiagramTab tab) {
        String tip = tab.isEditor()
                ? (tab.editorFile != null ? tab.editorFile.getAbsolutePath()
                        : Messages.get("puml.editor.tooltip"))
                : DiagramTabSupport.tooltipFor(tab.spec, tab.treeSync);
        return DiagramTabHeader.build(tab.label, tab.icon, tip,
                () -> closeTab(tab, tab.key), e -> showTabMenu(tab, tab.key, e),
                () -> tabs.setSelectedComponent(tab),
                this::pinPreviewTab);
    }

    // -------------------------------------------------------------------------
    // 自由編集 PlantUML エディタタブ
    // -------------------------------------------------------------------------

    /** 未保存 (Untitled) エディタタブの連番。 */
    private int untitledCounter;

    /**
     * 自由編集 PlantUML エディタタブを開く。プロジェクト未ロードでも動作する
     * (描画は解析キャッシュに依存しない {@link PlantUmlSvgRenderer} を直接使う)。
     *
     * @param initialText エディタの初期 PlantUML テキスト (null は空)
     * @param file        編集対象の .puml ファイル (null なら Untitled の新規図)。
     *                    同じファイルのタブが既に開いていればフォーカスのみ移す。
     */
    public void openPumlEditor(String initialText, java.io.File file) {
        openPumlEditor(initialText, file, false);
    }

    /**
     * {@code markDirty} 版。閉じたタブの再オープン時に、未保存 (●) 状態も復元して
     * 2 回目のクローズで無警告消失しないようにするために使う。
     */
    void openPumlEditor(String initialText, java.io.File file, boolean markDirty) {
        String key;
        String label;
        if (file != null) {
            key = "PUML:" + file.getAbsolutePath();
            label = file.getName();
        } else {
            untitledCounter++;
            key = "PUML:untitled-" + untitledCounter;
            label = Messages.get("puml.editor.untitled") + "-" + untitledCounter + ".puml";
        }
        DiagramTab existing = openTabs.get(key);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return;
        }
        pinPreviewTab();
        DiagramTab tab = new DiagramTab(key, label, TreeNodeIcon.PUML, null, null);
        tab.enableEditor(initialText != null ? initialText : "", file);
        openTabs.put(key, tab);
        int insertAt = tabs.getTabCount() - fixedSuffix;
        if (insertAt < 0) {
            insertAt = 0;
        }
        String tip = file != null ? file.getAbsolutePath()
                : Messages.get("puml.editor.tooltip");
        tabs.insertTab(label, null, tab, tip, insertAt);
        java.awt.Component header = buildTabHeader(tab);
        tabs.setTabComponentAt(insertAt, header);
        TabReorderHandler.install(tabs, header, () -> tabs.getTabCount() - fixedSuffix);
        tabs.setSelectedIndex(insertAt);
        if (markDirty) {
            tab.dirty = true; // 復元した未保存内容は dirty のまま扱う
        }
        refreshTabLabels();
        tab.startRender();
        applyTabBudget(key);
    }

    /**
     * アクティブな自由編集エディタタブの内容を .puml に保存する。
     * 保存先が未定 ({@code Untitled}) または {@code saveAs} 指定時はダイアログで選ばせる。
     *
     * @return 実際に保存できたら true (キャンセル・非エディタタブ・IO 失敗は false)
     */
    public boolean saveActivePumlEditor(boolean saveAs) {
        DiagramTab t = activeTab();
        if (t == null || !t.isEditor()) {
            reportStatus(Messages.get("puml.editor.noEditorTab"));
            return false;
        }
        return savePumlEditor(t, saveAs);
    }

    /**
     * アクティブなエディタタブの「編集中テキスト」と「保存済みファイル」の行差分を表示する。
     * ファイル未保存 (Untitled) や差分なしのときは案内だけ出す。
     */
    public void showDiffVsSavedForActiveEditor() {
        DiagramTab t = activeTab();
        if (t == null || !t.isEditor()) {
            reportStatus(Messages.get("puml.editor.noEditorTab"));
            return;
        }
        if (t.editorFile == null || !t.editorFile.isFile()) {
            javax.swing.JOptionPane.showMessageDialog(tabs,
                    Messages.get("puml.diff.noSaved"), Messages.get("puml.diff.title"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String saved;
        try {
            saved = PumlEditorSupport.read(t.editorFile);
        } catch (java.io.IOException ex) {
            reportStatus(Messages.get("puml.editor.openFailed") + ex.getMessage());
            return;
        }
        String current = t.sourcePanel.getText();
        if (!PumlDiff.hasChanges(saved, current)) {
            javax.swing.JOptionPane.showMessageDialog(tabs,
                    Messages.get("puml.diff.none"), Messages.get("puml.diff.title"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (PumlDiff.tooLargeToDiff(saved, current)) {
            // 巨大 .puml で EDT が固まらないよう、行差分は諦めて案内だけ出す。
            javax.swing.JOptionPane.showMessageDialog(tabs,
                    Messages.get("puml.diff.tooLarge"), Messages.get("puml.diff.title"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        javax.swing.JTextArea area = new javax.swing.JTextArea(
                PumlDiff.unified(saved, current), 24, 72);
        area.setEditable(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        area.setCaretPosition(0);
        javax.swing.JOptionPane.showMessageDialog(tabs,
                new javax.swing.JScrollPane(area),
                Messages.get("puml.diff.title") + " — " + t.label,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * テスト用: 保存先を明示指定して Save As 相当を実行する ({@code JFileChooser} を回避)。
     * Save As のキー移行・dedup 一貫性を統合検証するためのシーム。
     */
    boolean saveActiveEditorToForTest(java.io.File target) {
        DiagramTab t = activeTab();
        if (t == null || !t.isEditor() || target == null) {
            return false;
        }
        return writeEditorTo(t, target);
    }

    private boolean savePumlEditor(DiagramTab tab, boolean saveAs) {
        java.io.File target = tab.editorFile;
        if (saveAs || target == null) {
            target = PumlEditorSupport.choosePumlSaveTarget(tabs, tab.label);
            if (target == null) {
                return false;
            }
            // 保存先が別タブで開かれている場合は拒否する。書いてしまうと「同じファイルを
            // 指す 2 つのタブが別内容・別 dirty 状態で並ぶ」状態になり、もう一方のタブの
            // Ctrl+S が今回の保存を黙って巻き戻す。
            DiagramTab other = openTabs.get("PUML:" + target.getAbsolutePath());
            if (other != null && other != tab) {
                javax.swing.JOptionPane.showMessageDialog(tabs,
                        java.text.MessageFormat.format(
                                Messages.get("puml.editor.saveTargetOpen"), target.getName()),
                        Messages.get("dlg.error.title"),
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        return writeEditorTo(tab, target);
    }

    private boolean writeEditorTo(DiagramTab tab, java.io.File target) {
        try {
            PumlEditorSupport.write(target, tab.sourcePanel.getText());
        } catch (java.io.IOException ex) {
            juml.util.AppLog.error(juml.util.ErrorCode.UML_E003, "DiagramTabPane",
                    "puml save failed: " + target.getAbsolutePath(), ex);
            javax.swing.JOptionPane.showMessageDialog(tabs,
                    Messages.get("puml.editor.saveFailed") + ex.getMessage(),
                    Messages.get("dlg.error.title"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return false;
        }
        tab.editorFile = target;
        tab.dirty = false;
        // Save As で保存先が変わったらタブキーもファイルパス基準へ移行する。
        // これをしないと、保存後に同じ .puml を File > Open した際にキー不一致で
        // タブが重複生成される (dedup の一貫性が崩れる)。
        migrateEditorTabKey(tab, "PUML:" + target.getAbsolutePath());
        // Save As で名前が付いたらタブラベルもファイル名に合わせる。
        tab.label = target.getName();
        int idx = tabs.indexOfComponent(tab);
        if (idx >= 0) {
            tabs.setToolTipTextAt(idx, target.getAbsolutePath());
        }
        refreshTabLabels();
        reportStatus(Messages.get("status.saved") + target.getAbsolutePath());
        return true;
    }

    /**
     * エディタタブのキーを新パス基準へ移行する (Save As 後の dedup 一貫性のため)。
     * openTabs / previewTabKey / 付箋保存キー / ナビ履歴の旧キーを更新する。
     * 移行先キーが既に別タブで使われている稀なケースでは触らない (クロブ回避)。
     */
    private void migrateEditorTabKey(DiagramTab tab, String newKey) {
        String oldKey = tab.key;
        if (newKey.equals(oldKey) || openTabs.containsKey(newKey)) {
            return;
        }
        openTabs.remove(oldKey);
        tab.key = newKey;
        openTabs.put(newKey, tab);
        if (oldKey.equals(previewTabKey)) {
            previewTabKey = newKey;
        }
        // 付箋の保存先を新キーへ。ストア上のエントリも移す (移さないと旧キーに取り残され、
        // ファイルを開き直しても付箋がロードされない)。
        notesBinder.renameKey(cache.getProjectRoot(), oldKey, newKey);
        notesBinder.bind(tab.previewPanel, cache.getProjectRoot(), newKey);
        navHistory.replaceKey(oldKey, newKey);
        // メモリ管理の MRU も追従させる (旧キーの幽霊が退避枠を浪費しないように)。
        tabMemory.rename(oldKey, newKey);
    }

    /** アクティブなエディタタブの PlantUML テキスト (非エディタタブなら null)。 */
    String activeEditorText() {
        DiagramTab t = activeTab();
        return t != null && t.isEditor() ? t.sourcePanel.getText() : null;
    }

    /** アクティブなエディタタブのテキストを差し替える (編集扱いで再描画が走る)。 */
    void setActiveEditorText(String text) {
        DiagramTab t = activeTab();
        if (t != null && t.isEditor()) {
            t.sourcePanel.setText(text);
        }
    }

    /**
     * メソッド図タブ (SEQUENCE ⇄ ACTIVITY ⇄ CALLGRAPH) を、同じ {@code Class.method} のまま
     * 別図種へ「その場で」切り替える。タブを複製せず同じ位置のタブを描き替えるため、
     * 関数を選択したまま図種を行き来してもタブが増えない。
     *
     * <p>既に対象図種のタブが別に開いている場合は、重複を避けてそのタブへフォーカスする。
     * METHOD 以外のタブ、メソッド系 (DIAGRAMS_METHOD) 以外の図種、同一図種への切替は無視する。</p>
     */
    /**
     * アクティブなメソッド図タブを、同じ関数のまま {@code next} (SEQUENCE/ACTIVITY/CALLGRAPH) へ
     * その場で切り替える。アクティブタブがメソッド図でなければ何もしない。タブは複製されない。
     */
    public void switchActiveMethodKind(DiagramKind next) {
        switchMethodTabKind(activeTab(), next);
    }

    void switchMethodTabKind(DiagramTab tab, DiagramKind next) {
        if (tab == null || tab.treeSync == null
                || tab.treeSync.target != TreeNodeOpenRequest.Target.METHOD
                || next == null || next == tab.spec.getKind()
                || !ToolBarBuilder.DIAGRAMS_METHOD.contains(next)) {
            return;
        }
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                tab.treeSync.classInfo, tab.treeSync.methodInfo, next);
        String newKey = req.tabKey();
        DiagramTab existing = openTabs.get(newKey);
        if (existing != null && existing != tab) {
            // 既に対象図種のタブがある → 複製せずそこへフォーカスするだけ。
            // ただしユーザーのクリックで元タブのトグル選択は既に変わっているため、
            // 元タブの実際の図種へ戻して見た目と内容のデシンクを防ぐ。
            tab.updateKindToggle();
            tabs.setSelectedComponent(existing);
            return;
        }
        int index = tabs.indexOfComponent(tab);
        if (index < 0) {
            return;
        }
        String oldKey = tab.key;
        openTabs.remove(oldKey);
        if (oldKey.equals(previewTabKey)) {
            previewTabKey = newKey;
        }
        // タブの題材 (由来ノード) を新図種へ差し替え、描画リクエスト/ラベル/アイコンを再計算。
        tab.key = newKey;
        tab.treeSync = req;
        tab.spec = DiagramTabSupport.toDiagramRequest(req);
        tab.icon = DiagramTabSupport.iconFor(req);
        tab.label = req.displayLabel();
        openTabs.put(newKey, tab);
        // ナビ履歴の旧キーを新キーへ置換 (Alt+Left/Right が消えた旧キーで no-op にならないよう)。
        navHistory.replaceKey(oldKey, newKey);
        // メモリ管理の MRU も追従させる (旧キーの幽霊が退避枠を浪費しないように)。
        tabMemory.rename(oldKey, newKey);
        // 付箋メモは図ごとに別管理。別図種の付箋が残らないよう一旦クリアして新キーへ再バインド。
        tab.previewPanel.notes().setData(
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        notesBinder.bind(tab.previewPanel, cache.getProjectRoot(), newKey);
        // ヘッダ (アイコン/ラベル/ツールチップ/クローズ時のキー参照) を作り直して差し替える。
        java.awt.Component header = buildTabHeader(tab);
        tabs.setTabComponentAt(index, header);
        if (newKey.equals(previewTabKey)) {
            DiagramTabHeader.setPreview(header, true);
        }
        TabReorderHandler.install(tabs, header, () -> tabs.getTabCount() - fixedSuffix);
        tabs.setToolTipTextAt(index,
                DiagramTabSupport.tooltipFor(tab.spec, tab.treeSync));
        tab.updateKindToggle();
        refreshTabLabels();
        // 図種切替では選択変更が起きず handleTabSelectionChanged を通らないため、
        // MRU オーバーレイのラベルが旧図種のまま残らないよう明示的に更新する。
        mru.onActivated(tab, tab.label);
        tab.startRender();
        // アクティブタブならツールバー/ツリー/ステータスの図種連動を更新する。
        if (activeTab() == tab) {
            lastDiagramRequest = tab.treeSync;
            if (onTabFocused != null) {
                onTabFocused.accept(new FocusedTab(tab.treeSync, tab.spec.getKind()));
            }
        }
        applyTabBudget(newKey);
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
            if (t.dirty) {
                display = "● " + display; // 未保存の変更を VS Code 風の ● で示す
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

    /**
     * 指定クラス (と任意のメソッド) のソース定義へジャンプする。クラスのタブを
     * 開き (既にあればフォーカスし)、Source ビューを前面に出して宣言行へスクロールする。
     * 図上リンクの Ctrl+クリック / 右クリック「ソースを開く」/ メソッドポップアップから使う。
     */
    public void openSourceFor(JavaClassInfo classInfo, JavaMethodInfo methodInfo) {
        if (classInfo == null) {
            return;
        }
        addOrFocusTab(TreeNodeOpenRequest.classNode(classInfo));
        DiagramTab t = activeTab();
        if (t != null) {
            t.selectSourceView(methodInfo);
        }
    }

    /**
     * 参照サイト (呼び出し元 FQN + file:line) のソースへジャンプする。
     * References (逆参照) パネルの行ダブルクリックから使う。呼び出し元クラスの
     * タブを開いて Source ビューを指定行へスクロールする。クラスがインデックスに
     * 無い場合はファイルパスだけで開けないためステータスに通知する。
     */
    public void openSourceSite(String callerFqn, String file, int line) {
        JavaClassInfo ci = callerFqn == null
                ? null : cache.getIndex().header(callerFqn).orElse(null);
        if (ci == null) {
            reportStatus(Messages.get("source.notFound")
                    + (file != null && !file.isEmpty() ? " (" + file + ")" : ""));
            return;
        }
        addOrFocusTab(TreeNodeOpenRequest.classNode(ci));
        DiagramTab t = activeTab();
        if (t != null) {
            t.selectSourceViewAtLine(line);
        }
    }

    /** アクティブタブの描画リクエストを差し替えて再描画する (スコープ/プリセット適用など)。 */
    public void setActiveTabSpecAndRender(DiagramRequest spec) {
        DiagramTab t = activeTab();
        // 自由編集エディタタブは spec を持たない (テキストが真実源) ため差し替え対象外。
        if (t != null && spec != null && !t.isEditor()) {
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
        all.addActionListener(a -> requestCloseAll());
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
                // 未保存タブの確認でキャンセルされたら一括クローズ全体を中止する。
                // (残りを閉じ続けると「キャンセル」が部分クローズになってしまう。)
                if (!closeTab(en.getValue(), en.getKey())) {
                    return;
                }
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

    /**
     * プロジェクト切替時の後始末。旧プロジェクトの解析キャッシュに依存する図タブを
     * すべて閉じ (残すと再描画時に新プロジェクトのデータで空図・別図が出る)、
     * 再オープン履歴・ナビ履歴も破棄する (どちらも旧プロジェクトのキー/クロージャを
     * 参照しているため)。自由編集の PlantUML エディタタブはプロジェクト非依存なので
     * 残し、付箋の保存先だけ新プロジェクトへ再バインドする。
     */
    public void onProjectSwitched() {
        java.util.List<DiagramTab> toClose = new ArrayList<>();
        for (DiagramTab t : openTabs.values()) {
            if (!t.isEditor()) {
                toClose.add(t);
            }
        }
        for (DiagramTab t : toClose) {
            closeTab(t, t.key, false);
        }
        closedTabs.clear();
        navHistory.clear();
        for (DiagramTab t : openTabs.values()) {
            notesBinder.bind(t.previewPanel, cache.getProjectRoot(), t.key);
            navHistory.push(t.key);
        }
    }

    /**
     * タブ右クリック「Close All」の要求。ハンドラが設定されていればそちらへ委譲し
     * (フレーム側で確認ダイアログを挟む)、未設定なら従来どおり確認なしで閉じる。
     * {@link #closeAllTabs()} 自体は生のクローズ操作のまま維持する。
     */
    void requestCloseAll() {
        if (closeAllRequestHandler != null) {
            closeAllRequestHandler.run();
        } else {
            closeAllTabs();
        }
    }

    /**
     * 指定タブより右にある図タブをすべて閉じる。「右」は JTabbedPane 上の視覚的な並びで
     * 判定する。openTabs (挿入順) はドラッグ並び替えや図種のその場切替で見た目の順序と
     * 食い違うため、順序の根拠に使わない。
     */
    void closeTabsToRight(String pivotKey) {
        DiagramTab pivot = openTabs.get(pivotKey);
        int pivotIdx = pivot != null ? tabs.indexOfComponent(pivot) : -1;
        if (pivotIdx < 0) {
            return;
        }
        java.util.List<DiagramTab> victims = new ArrayList<>();
        for (int i = pivotIdx + 1; i < tabs.getTabCount(); i++) {
            java.awt.Component c = tabs.getComponentAt(i);
            if (c instanceof DiagramTab) {
                victims.add((DiagramTab) c);
            }
        }
        for (int i = victims.size() - 1; i >= 0; i--) {
            DiagramTab t = victims.get(i);
            // 未保存タブの確認でキャンセルされたら残りのクローズも中止する。
            if (!closeTab(t, t.key)) {
                return;
            }
        }
    }

    /** 指定タブの右側 (視覚順) に図タブがあるか。 */
    private boolean hasTabsToRight(String key) {
        DiagramTab pivot = openTabs.get(key);
        int idx = pivot != null ? tabs.indexOfComponent(pivot) : -1;
        if (idx < 0) {
            return false;
        }
        for (int i = idx + 1; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof DiagramTab) {
                return true;
            }
        }
        return false;
    }

    /** アクティブタブの右側に図タブがあるか (メニュー活性制御用)。動的タブ未選択なら false。 */
    public boolean hasTabsToRightOfActive() {
        DiagramTab t = activeTab();
        return t != null && hasTabsToRight(t.key);
    }

    /**
     * アクティブな動的タブを閉じる。Ctrl+W / File &gt; Close Tab 用。
     * ユーティリティタブ選択中は閉じられないため、silent no-op にせず
     * ビープ + ステータス通知でフィードバックする。
     */
    public void closeActiveTab() {
        DiagramTab t = activeTab();
        if (t != null) {
            closeTab(t, t.key);
        } else {
            java.awt.Toolkit.getDefaultToolkit().beep();
            reportStatus(Messages.get("tab.closeUtilityDenied"));
        }
    }

    private boolean closeTab(DiagramTab tab, String key) {
        return closeTab(tab, key, true);
    }

    /**
     * タブを閉じる。{@code recordForReopen} が true のときだけ再オープン履歴に積む。
     * メモリ上限による自動クローズ (LRU) は履歴を汚さないよう false で呼ぶ。
     *
     * @return 実際に閉じたら true、未保存確認でキャンセルされ閉じなかったら false
     */
    private boolean closeTab(DiagramTab tab, String key, boolean recordForReopen) {
        // ユーザー操作で未保存のエディタタブを閉じるときは保存/破棄/キャンセルを確認する。
        if (recordForReopen && !confirmDiscardEdits(tab)) {
            return false;
        }
        if (recordForReopen) {
            pushClosedTab(tab);
        }
        if (tab.activeWorker != null) {
            tab.activeWorker.cancel(true);
        }
        if (tab.renderDebounce != null) {
            tab.renderDebounce.stop(); // クローズ後のデバウンス発火 (無駄な再描画) を止める
        }
        tab.previewPanel.notes().setOnChange(null);
        int index = tabs.indexOfComponent(tab);
        if (index >= 0) {
            tabs.remove(index);
        }
        openTabs.remove(key);
        if (key.equals(previewTabKey)) {
            previewTabKey = null;
        }
        mru.onClosed(tab);
        navHistory.remove(key);
        refreshTabLabels();
        tabMemory.onClose(key);
        return true;
    }

    /**
     * 未保存の変更があるエディタタブを閉じてよいかユーザーへ確認する。
     * Yes = 保存してから閉じる / No = 破棄して閉じる / Cancel = 閉じない。
     *
     * @return タブを閉じてよければ true
     */
    /**
     * 終了前に、未保存のエディタタブそれぞれについて保存/破棄/中止を確認する。
     * いずれかで Cancel されたら false を返し、呼び出し側は終了を中止する。
     * (ウィンドウの×/File &gt; Exit からの無警告データ消失を防ぐ。)
     */
    public boolean confirmDiscardAllEdits() {
        return confirmDiscardAllEdits(this::askDiscardChoice);
    }

    /**
     * 確認の「聞き方」を注入できる版 (テスト用)。{@code ask} はタブラベルを受け取り
     * {@link javax.swing.JOptionPane} の YES/NO/CANCEL 相当の値を返す。
     */
    boolean confirmDiscardAllEdits(java.util.function.ToIntFunction<String> ask) {
        for (DiagramTab t : new ArrayList<>(openTabs.values())) {
            if (t.isEditor() && t.dirty) {
                tabs.setSelectedComponent(t); // どのタブの確認かユーザーに見せる
                if (!confirmDiscardEdits(t, ask)) {
                    return false;
                }
            }
        }
        return true;
    }

    private int askDiscardChoice(String label) {
        return javax.swing.JOptionPane.showConfirmDialog(tabs,
                java.text.MessageFormat.format(
                        Messages.get("puml.editor.confirmClose"), label),
                Messages.get("puml.editor.confirmClose.title"),
                javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
    }

    private boolean confirmDiscardEdits(DiagramTab tab) {
        return confirmDiscardEdits(tab, this::askDiscardChoice);
    }

    private boolean confirmDiscardEdits(DiagramTab tab,
                                        java.util.function.ToIntFunction<String> ask) {
        if (!tab.isEditor() || !tab.dirty) {
            return true;
        }
        int choice = ask.applyAsInt(tab.label);
        if (choice == javax.swing.JOptionPane.YES_OPTION) {
            return savePumlEditor(tab, false); // 保存キャンセル時は閉じない
        }
        return choice == javax.swing.JOptionPane.NO_OPTION;
    }

    private void pushClosedTab(DiagramTab tab) {
        // 重い DiagramTab を捕捉しないよう、再オープンに必要な軽量フィールドだけを束ねる。
        if (tab.isEditor()) {
            final String text = tab.sourcePanel.getText();
            final java.io.File file = tab.editorFile;
            final boolean wasDirty = tab.dirty;
            closedTabs.push(() -> openPumlEditor(text, file, wasDirty));
        } else {
            final String key = tab.key;
            final String label = tab.label;
            final TreeNodeIcon icon = tab.icon;
            final DiagramRequest spec = tab.spec;
            final TreeNodeOpenRequest treeSync = tab.treeSync;
            closedTabs.push(() -> openDiagram(key, label, icon, spec, treeSync));
        }
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
        public boolean closeTab(String key) {
            DiagramTab t = openTabs.get(key);
            if (t == null) {
                return true; // 既に存在しない幽霊キー → 帳簿から外してよい
            }
            if (t.isEditor() && t.dirty) {
                return false; // 未保存編集を LRU 自動クローズで失わせない
            }
            String msg = Messages.get("status.tabAutoClosed") + t.label;
            reportStatus(msg);
            if (toastNotifier != null) {
                toastNotifier.accept(msg);
            }
            DiagramTabPane.this.closeTab(t, key, false);
            return true;
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
        /** タブ識別キー。図種切替でメソッド図の図種が変わると更新される。 */
        private String key;
        /** タブヘッダのラベル。図種切替で更新される。 */
        private String label;
        /** タブヘッダのアイコン (再オープン時に同じ見た目で復元するため保持)。 */
        private TreeNodeIcon icon;
        /** ツリーハイライト用の由来ノード (汎用タブでは null)。図種切替で更新される。 */
        private TreeNodeOpenRequest treeSync;
        /** このタブが描画するリクエスト (差し替え可能)。null = 自由編集エディタタブ。 */
        private DiagramRequest spec;
        /** 自由編集エディタ: 保存先の .puml (null = 未保存の Untitled)。 */
        private java.io.File editorFile;
        /** 自由編集エディタ: 未保存の変更があるか。 */
        private boolean dirty;
        /** 自由編集エディタ: 編集が落ち着いてから再描画するデバウンスタイマ。 */
        private javax.swing.Timer renderDebounce;
        /** メソッド図の SEQUENCE ⇄ ACTIVITY ⇄ CALLGRAPH 切替バー (メソッド図以外では非表示)。 */
        private final JPanel kindBar;
        private final javax.swing.JToggleButton seqToggle;
        private final javax.swing.JToggleButton activityToggle;
        private final javax.swing.JToggleButton callgraphToggle;
        /** トグルのプログラム的な選択更新中にアクションが発火しても切替しないためのガード。 */
        private boolean syncingKindToggle;
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
        /**
         * メッセージカード本体。JLabel ではなく JEditorPane にすることで、
         * エラー内容をマウス選択してコピーできる (クローズド環境での転記支援)。
         * juml-errcode: / juml-copy: リンクのクリックも受け付ける。
         */
        private final javax.swing.JEditorPane messageLabel = new javax.swing.JEditorPane();
        private final javax.swing.JProgressBar renderSpinner = new javax.swing.JProgressBar();
        private String renderedPuml;
        private String renderedSvgXml;
        private String lastStatus;
        /** 直近の描画失敗の転記用テキスト (juml-copy: リンクでコピーされる)。 */
        private String copyableFailureText;
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
            messageLabel.setContentType("text/html");
            messageLabel.setEditable(false);
            messageLabel.setOpaque(false);
            // JLabel と同じ UI フォント / 前景色で HTML を描画させる
            messageLabel.putClientProperty(
                    javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            messageLabel.setFont(javax.swing.UIManager.getFont("Label.font"));
            messageLabel.setForeground(msgFg != null ? msgFg : new Color(0x555555));
            messageLabel.addHyperlinkListener(this::handleMessageCardLink);
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
            split.setResizeWeight(tabSplitRatio);
            split.setDividerLocation(tabSplitRatio);
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
                int h = split.getHeight();
                if (h > 0) {
                    tabSplitRatio = (double) split.getDividerLocation() / h;
                }
            });
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

            // メソッド図の上部に「シーケンス ⇄ アクティビティ ⇄ コールグラフ」切替バーを置く。
            // 関数を選択したまま図種を行き来できるので、図種ごとにタブを開かずに済む。
            // トップツールバーからメソッド系の図種ボタンは廃し、切替はこのバーへ一本化した。
            seqToggle = new javax.swing.JToggleButton(Messages.get("diagram.kind.SEQUENCE.short"));
            activityToggle = new javax.swing.JToggleButton(Messages.get("diagram.kind.ACTIVITY.short"));
            callgraphToggle = new javax.swing.JToggleButton(Messages.get("diagram.kind.CALLGRAPH.short"));
            seqToggle.setFocusable(false);
            activityToggle.setFocusable(false);
            callgraphToggle.setFocusable(false);
            seqToggle.setToolTipText(Messages.get("diagram.toggle.tip"));
            activityToggle.setToolTipText(Messages.get("diagram.toggle.tip"));
            callgraphToggle.setToolTipText(Messages.get("diagram.toggle.tip"));
            javax.swing.ButtonGroup kindGroup = new javax.swing.ButtonGroup();
            kindGroup.add(seqToggle);
            kindGroup.add(activityToggle);
            kindGroup.add(callgraphToggle);
            seqToggle.addActionListener(e -> {
                if (!syncingKindToggle) {
                    switchMethodTabKind(this, DiagramKind.SEQUENCE);
                }
            });
            activityToggle.addActionListener(e -> {
                if (!syncingKindToggle) {
                    switchMethodTabKind(this, DiagramKind.ACTIVITY);
                }
            });
            callgraphToggle.addActionListener(e -> {
                if (!syncingKindToggle) {
                    switchMethodTabKind(this, DiagramKind.CALLGRAPH);
                }
            });
            kindBar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2));
            kindBar.add(new JLabel(Messages.get("diagram.toggle.label")));
            kindBar.add(seqToggle);
            kindBar.add(activityToggle);
            kindBar.add(callgraphToggle);
            add(kindBar, java.awt.BorderLayout.NORTH);
            updateKindToggle();
        }

        private boolean isActive() {
            return tabs.getSelectedComponent() == this;
        }

        /** 自由編集 PlantUML エディタタブか (spec を持たずテキストが真実源)。 */
        boolean isEditor() {
            return spec == null;
        }

        /**
         * このタブを自由編集エディタモードに切り替える (生成直後・挿入前に呼ぶ)。
         * PlantUML テキスト欄を編集可能にし、編集停止から少し置いてライブプレビューを
         * 再描画するよう配線する。実 Java ソースのサブタブはエディタでは無意味なので外す。
         */
        void enableEditor(String initialText, java.io.File file) {
            this.editorFile = file;
            bottomTabs.remove(javaSourcePanel);
            sourcePanel.setText(initialText);
            sourcePanel.setEditable(true);
            // デバウンス: 連続キー入力のたびに PlantUML 描画が走らないよう 600ms 待つ。
            renderDebounce = new javax.swing.Timer(600, e -> startRender());
            renderDebounce.setRepeats(false);
            // リスナー登録は初期 setText の後 (初期化を dirty 扱いにしない)。
            sourcePanel.setOnTextChange(() -> {
                markEditorDirty();
                renderDebounce.restart();
            });
            // GUI 図形操作デザイナー (Design サブタブ)。テキストとの双方向同期:
            // Design 選択時にテキストを解析して復元し、キャンバス操作でテキストを再生成する。
            // 同時に見えるのは片方だけ (JTabbedPane) なので同期ループは起きない。
            juml.app.uml.sketch.SketchPane sketchPane = new juml.app.uml.sketch.SketchPane();
            sketchPane.setOnPumlChange(sourcePanel::setText);
            bottomTabs.addTab(Messages.get("tab.design"), sketchPane);
            bottomTabs.addChangeListener(e -> {
                if (bottomTabs.getSelectedComponent() == sketchPane) {
                    sketchPane.loadFrom(sourcePanel.getText());
                }
            });
        }

        /** 編集発生を記録し、タブヘッダに未保存マーク (●) を付ける。 */
        private void markEditorDirty() {
            if (!dirty) {
                dirty = true;
                refreshTabLabels();
            }
        }

        /**
         * 図種切替バーの表示/選択状態を現在の図種に合わせる。メソッド図
         * (SEQUENCE/ACTIVITY/CALLGRAPH) のときだけ表示し、それ以外の図種では隠す。
         */
        void updateKindToggle() {
            DiagramKind k = spec != null ? spec.getKind() : null;
            boolean method = k != null && ToolBarBuilder.DIAGRAMS_METHOD.contains(k);
            kindBar.setVisible(method);
            syncingKindToggle = true;
            try {
                seqToggle.setSelected(k == DiagramKind.SEQUENCE);
                activityToggle.setSelected(k == DiagramKind.ACTIVITY);
                callgraphToggle.setSelected(k == DiagramKind.CALLGRAPH);
            } finally {
                syncingKindToggle = false;
            }
            kindBar.revalidate();
            kindBar.repaint();
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
         * Source サブタブを前面に出し、タブの題材クラスのソースを指定メソッドの
         * 宣言行付きで表示する (図上のメソッドリンク「ソースを開く」用)。
         */
        void selectSourceView(JavaMethodInfo methodOverride) {
            bottomTabs.setSelectedComponent(javaSourcePanel);
            DiagramTabSupport.showSource(javaSourcePanel, treeSync, cache, methodOverride);
        }

        /** Source サブタブを前面に出し、題材クラスのソースを指定行で表示する (逆参照ジャンプ用)。 */
        void selectSourceViewAtLine(int line) {
            bottomTabs.setSelectedComponent(javaSourcePanel);
            String fqn = (treeSync != null && treeSync.classInfo != null)
                    ? treeSync.classInfo.getQualifiedName() : null;
            java.io.File src = fqn == null
                    ? null : cache.getIndex().source(fqn).orElse(null);
            if (src == null) {
                javaSourcePanel.showMessage(Messages.get("source.notFound"));
                return;
            }
            if (line > 0) {
                javaSourcePanel.showFileAtLine(src, line);
            } else {
                DiagramTabSupport.showSource(javaSourcePanel, treeSync, cache);
            }
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
            messageLabel.setCaretPosition(0);
            renderSpinner.setVisible(showSpinner);
            cards.show(viewCards, "msg");
        }

        /**
         * メッセージカード内リンクの処理。{@code juml-errcode:<ID>} はアプリ内
         * エラーリファレンスの該当項目を開き、{@code juml-copy:} は直近の失敗詳細を
         * クリップボードへコピーする。
         */
        private void handleMessageCardLink(javax.swing.event.HyperlinkEvent e) {
            if (e.getEventType() != javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) {
                return;
            }
            String href = e.getDescription();
            if (href == null) {
                return;
            }
            if (href.startsWith("juml-errcode:")) {
                ErrorReferenceDialog.showFor(
                        javax.swing.SwingUtilities.getWindowAncestor(this),
                        href.substring("juml-errcode:".length()));
            } else if (href.startsWith("juml-copy:") && copyableFailureText != null) {
                java.awt.datatransfer.StringSelection sel =
                        new java.awt.datatransfer.StringSelection(copyableFailureText);
                getToolkit().getSystemClipboard().setContents(sel, sel);
                reportStatus(Messages.get("diag.fail.copied"));
            }
        }

        /** 転記・報告用の失敗詳細テキストを組み立てる。 */
        private String buildFailureText(juml.util.ErrorCode code, Throwable error,
                java.io.File dumped) {
            StringBuilder sb = new StringBuilder();
            sb.append(code.getId()).append(' ').append(code.summary()).append('\n');
            sb.append(label).append('\n');
            sb.append(DiagramFailureMessage.fullReason(error)).append('\n');
            if (dumped != null) {
                sb.append(Messages.get("diag.fail.savedTo")).append(' ')
                  .append(dumped.getAbsolutePath()).append('\n');
            }
            sb.append(Messages.get("errref.remedyLabel")).append(' ')
              .append(code.remedy());
            return sb.toString();
        }

        private void setStatus(String msg) {
            lastStatus = msg;
            // 共有ステータスバーへの反映はアクティブタブのときだけ行う。非アクティブタブの
            // 背景描画完了が、別タブに切替済みのステータスバーを上書きするのを防ぐ。
            // (lastStatus は常に保持するので、後でこのタブへ戻れば reportFocusStatus で出る。)
            if (isActive()) {
                reportStatus(msg);
            }
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
            // 図種依存の起点/対象キーは毎回リセットし、アクティブタブの分だけを下で設定する。
            // (リセットしないと前タブの layout/navigation キーが残り、別図種へ切替後に
            //  古いキーで図が再構築される。seq/activity/callGraph は元からリセット済み。)
            state.sequenceEntry = null;
            state.activityEntry = null;
            state.callGraphEntry = null;
            state.currentLayoutKey = null;
            state.currentNavigationKey = null;
            // 隠しシーケンス参加者もリセットする。クリアしないと、あるシーケンス図で
            // 隠した参加者が、別の題材で新規生成したシーケンス図にも漏れて適用される。
            state.sequenceHiddenParticipants.clear();
            if (spec == null) {
                // 自由編集エディタタブ: 図種依存のパラメータは持たない。
                state.currentScope = null;
                return;
            }
            state.currentScope = spec.getScope();
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
            // エディタタブは描画のたびにエラー行の強調を一旦消す (成功なら消えたまま、
            // 失敗なら done() で該当行を再強調する)。
            if (isEditor()) {
                sourcePanel.clearErrorHighlight();
            }
            setStatus(Messages.get("status.rendering") + " " + label + " ...");
            showMessageCard("<b>" + Messages.get("status.rendering") + " " + esc(label) + " …</b>", true);
            final DiagramRequest dreq = spec;
            // エディタタブはテキストが真実源: EDT 上でスナップショットしてから背景描画する。
            final String editorPuml = isEditor() ? sourcePanel.getText() : null;
            if (activeWorker != null) {
                activeWorker.cancel(true); // 旧描画を破棄して競合・無駄な処理を防ぐ
            }
            SwingWorker<RenderResult, Void> worker = new SwingWorker<RenderResult, Void>() {
                private Throwable error;
                private String pumlOnError;

                @Override
                protected RenderResult doInBackground() {
                    try {
                        String puml = dreq != null
                                ? DiagramService.generatePuml(dreq, cache)
                                : editorPuml;
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
                        // エディタタブでは編集中テキストを描画結果で上書きしない。
                        if (pumlOnError != null && !isEditor()) {
                            sourcePanel.setText(pumlOnError);
                        }
                        // エディタタブ: PlantUML が報告した失敗行を (prelude 挿入分を
                        // 補正して) エディタ上で赤く強調し、原因箇所へ誘導する。
                        if (isEditor() && editorPuml != null
                                && error instanceof juml.core.formats.uml.PlantUmlRenderFailedException) {
                            int genLine = ((juml.core.formats.uml.PlantUmlRenderFailedException) error)
                                    .getErrorLine();
                            if (genLine > 0) {
                                // 生成テキストとエディタテキストを直接付き合わせ、prelude 挿入・
                                // direction 行除去に関わらず正確な行へ写像する (#42)。
                                String generated =
                                        juml.core.formats.uml.PlantUmlRenderer.injectLayout(editorPuml);
                                sourcePanel.highlightErrorLine(
                                        PumlSourcePanel.editorLineForError(
                                                editorPuml, generated, genLine));
                            }
                        }
                        previewPanel.setSvgGraphicsNode(null, 0, 0);
                        renderedPuml = pumlOnError;
                        renderedSvgXml = null;
                        if (isActive()) {
                            mirrorToState();
                        }
                        // 失敗した PlantUML を logs/ へ保存し、例外を AppLog へ記録する
                        // (ユーザがそのまま報告できるようにする)。
                        juml.util.ErrorCode code = RenderFailureLog.classify(error, isEditor());
                        java.io.File dumped = RenderFailureLog.dump(
                                label, pumlOnError, error, isEditor());
                        copyableFailureText = buildFailureText(code, error, dumped);
                        showMessageCard(DiagramFailureMessage.forError(error, dumped, code));
                        setStatus(label + ": " + code.tag() + " "
                                + Messages.get("status.renderFailed") + " " + failureReason(error));
                        return;
                    }
                    try {
                        RenderResult r = get();
                        if (r == null || r.svg == null) {
                            if (!isEditor()) {
                                sourcePanel.setText(r != null ? r.puml : "");
                            }
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
                        if (!isEditor()) {
                            sourcePanel.setText(r.puml);
                        }
                        renderedPuml = r.puml;
                        renderedSvgXml = r.svg.getSvgXml();
                        showPreviewCard();
                        if (isActive()) {
                            mirrorToState();
                        }
                        setStatus(label + " " + Messages.get("status.rendered") + " ("
                                + (int) Math.round(r.svg.getWidth()) + "x" + (int) Math.round(r.svg.getHeight()) + ", SVG)");
                    } catch (Exception ex) {
                        juml.util.AppLog.error(juml.util.ErrorCode.UML_R004, "DiagramTab",
                                "render result handling failed: " + label, ex);
                        copyableFailureText = buildFailureText(
                                juml.util.ErrorCode.UML_R004, ex, null);
                        showMessageCard(DiagramFailureMessage.forError(
                                ex, null, juml.util.ErrorCode.UML_R004));
                        setStatus(label + ": " + juml.util.ErrorCode.UML_R004.tag()
                                + " " + ex.getMessage());
                    }
                }
            };
            activeWorker = worker;
            worker.execute();
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
            JavaClassInfo ci = cache.getIndex().header(fqn).orElse(null);
            if (ci == null) {
                return;
            }
            // Ctrl(⌘)+クリック: IDE の Go to Definition と同様にソース定義へ直接ジャンプ。
            if (event != null && (event.isControlDown() || event.isMetaDown())) {
                openSourceFor(ci, null);
                return;
            }
            addOrFocusTab(TreeNodeOpenRequest.classNode(ci));
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
                // インデックスに無いクラスはスタブで代替する。tabKey() や図生成が
                // FQN を参照するため、単純名だけでなくパッケージも復元しておく
                // (未設定だと別クラスとタブキーが衝突し空図が開くバグの元)。
                classInfo = new JavaClassInfo();
                int lastDot = classFqn.lastIndexOf('.');
                if (lastDot > 0) {
                    classInfo.setPackageName(classFqn.substring(0, lastDot));
                }
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
            menu.addSeparator();
            JMenuItem srcItem = new JMenuItem(Messages.get("source.openSource"));
            srcItem.addActionListener(e -> openSourceFor(ci, mi));
            menu.add(srcItem);
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
                // 図上のクラス → ソース定義へジャンプ (Ctrl+クリックと同じ)。
                cache.getIndex().header(fqn).ifPresent(ci -> {
                    popup.addSeparator();
                    popup.add(menuItem(Messages.get("source.openSource"),
                            () -> openSourceFor(ci, null)));
                });
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
