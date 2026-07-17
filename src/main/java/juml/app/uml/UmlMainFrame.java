// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.Main;
import juml.Setting;
import juml.core.formats.android.TextSummaryReport;
import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.GraphvizLocator;
import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.CancelToken;
import juml.util.Messages;

import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * UML 専用のメインウィンドウ。
 *
 * <p>左ペインに {@link ProjectTreePanel} (サイドバー / ナビゲータ)、右ペインに
 * VS Code 風の {@link DiagramTabPane} (すべての図を対等なタブ = エディタとして管理) を
 * 配置する。特別扱いの「Home タブ」は持たない。メニュー・ツールバーはアクティブタブに
 * 対して作用する。</p>
 *
 * <p>図の生成と SVG (ベクター) レンダリングは各タブが {@code SwingWorker} で
 * バックグラウンド実行する。PNG ラスタ化は保存時のみ行う。</p>
 */
public class UmlMainFrame extends JFrame {

    private static final String WINDOW_TITLE = "Juml UML";
    private static final int MENU_MASK = MenuBarBuilder.menuShortcutMask();
    /** 末尾固定のユーティリティタブ数 (Manifest ... Members + Git)。 */
    private static final int FIXED_UTILITY_TABS = 11;

    private final ProjectAnalysisCache cache = new ProjectAnalysisCache();
    private final ReferenceIndexCache refIndexCache = new ReferenceIndexCache(cache);
    private final ProjectTreePanel treePanel = new ProjectTreePanel();
    private final ManifestSummaryPanel manifestSummaryPanel = new ManifestSummaryPanel();
    private final juml.app.uml.explore.ImpactExplorerPanel impactPanel
            = new juml.app.uml.explore.ImpactExplorerPanel(refIndexCache);
    private final juml.app.uml.explore.ReverseReferencePanel referencesPanel
            = new juml.app.uml.explore.ReverseReferencePanel(refIndexCache);
    private final juml.app.uml.explore.FuncDiffPanel funcDiffPanel
            = new juml.app.uml.explore.FuncDiffPanel();
    private final juml.app.uml.explore.InsightsPanel insightsPanel
            = new juml.app.uml.explore.InsightsPanel(cache, refIndexCache);
    /** Doxygen タブと TODO タブで 1 回の doxygen 解析結果を共有するキャッシュ。 */
    private final DoxygenResultCache doxygenResultCache = new DoxygenResultCache();
    private final DoxygenPanel doxygenPanel = new DoxygenPanel(cache, doxygenResultCache);
    private final DoxygenTodoPanel doxygenTodoPanel = new DoxygenTodoPanel(cache, doxygenResultCache);
    private final DoxygenGroupsPanel doxygenGroupsPanel
            = new DoxygenGroupsPanel(cache, doxygenResultCache);
    private final MethodListPanel methodListPanel = new MethodListPanel();
    private final MemberListPanel memberListPanel = new MemberListPanel();
    /** git リポジトリ閲覧タブ (読み取り専用。JGit ベース)。 */
    private final juml.app.uml.git.GitPanel gitPanel = new juml.app.uml.git.GitPanel();
    private final JLabel status = new JLabel(" ");
    private final JLabel zoomLabel = new JLabel("100%");
    /** ステータスバー (題材/図種の常時表示を含む)。 */
    private StatusBarView statusBar;
    private final JProgressBar loadProgress = new JProgressBar();
    /** プロジェクト解析中に全面表示する GIF ローディングオーバーレイ (glass pane)。 */
    private final LoadingGlassPane loadingOverlay = new LoadingGlassPane();
    private JMenuItem cancelLoadingItem;
    private ButtonGroup diagramGroup;
    private java.util.EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    /** ツールバー上の「図種切替」トグルボタン。メニュー側ラジオと選択状態を同期する。 */
    private java.util.EnumMap<DiagramKind, JToggleButton> diagramToggles;
    private javax.swing.JButton addNoteButton; // アクティブタブ無し時に無効化
    /** プロジェクト未ロード時に無効化するエクスポート系 UI 要素。 */
    private java.util.List<JMenuItem> exportMenuItems;
    private javax.swing.JButton exportToolbarButton;
    /** アクティブタブの図種に応じて有効/無効を切り替える文脈依存メニュー項目。 */
    private java.util.List<JMenuItem> sequenceOnlyMenuItems;
    private java.util.List<JMenuItem> activityOnlyMenuItems;
    private java.util.List<JMenuItem> layoutOnlyMenuItems;
    private java.util.List<JMenuItem> navigationOnlyMenuItems;
    private ButtonGroup themeGroup;
    private java.util.Map<String, JRadioButtonMenuItem> themeItems;

    /** タブマネージャ (すべての図を対等なタブとして管理)。 */
    private DiagramTabPane tabPane;
    /** 図タブを別ウィンドウへ切り出す仕組み (VS Code の Move into New Window 相当)。 */
    private DetachedDiagramWindows detachedWindows;
    /** 右側のフラットタブバー (動的タブ / Manifest / Impact / References / Func Diff / Insights / Functions)。 */
    private JTabbedPane mainTabs;
    /** 左右分割 (左: ツリーサイドバー / 右: タブ)。Ctrl+B で折りたたむ対象。 */
    private JSplitPane centerSplit;
    private CenterCardView centerCards; // Welcome 空状態 ↔ ワークスペース切替
    private ActivityBar activityBar;
    /** コマンドパレット (Ctrl+Shift+P) のコマンド一覧。メニューと同じコールバック由来。 */
    private java.util.List<CommandPalette.Command> paletteCommands;

    /** アクティブタブのミラー兼スクラッチ状態 (エクスポート・スコープ/フィルタダイアログが参照)。 */
    private final DiagramState state = new DiagramState();
    /** 図のエクスポート (保存ダイアログ・クリップボード) を担う補助。 */
    private final ExportController exportController = new ExportController(this, state, status,
            () -> tabPane != null ? tabPane.activePreviewPanel() : null);

    DiagramKind currentKind = DiagramKind.CLASS;
    /** 現在ロード中のプロジェクトルート。null なら未ロード。 */
    private File currentProjectRoot;
    /** 進行中のロード処理のキャンセル用 (null ならロード中ではない)。 */
    private CancelToken loadingCancelToken;

    public UmlMainFrame(File initialProject) {
        super(WINDOW_TITLE);
        // ブランドロゴをウィンドウ/タスクバーアイコンに使う (ベクター描画なので複数解像度を用意)。
        setIconImages(java.util.List.of(
                JumlLogo.renderMarkImage(16), JumlLogo.renderMarkImage(32),
                JumlLogo.renderMarkImage(64), JumlLogo.renderMarkImage(128)));
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });

        wirePanelListeners();
        buildMenuBar();
        buildCenterTabs();
        buildToolBar();
        controller = createDiagramController();
        controller.updateAvailableDiagrams(java.util.EnumSet.noneOf(DiagramKind.class));
        statusBar = new StatusBarView(status, loadProgress, zoomLabel);
        zoomLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        zoomLabel.setToolTipText(Messages.get("statusbar.zoom.tip"));
        zoomLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                tabPane.zoomResetActive();
            }
        });
        tabPane.setOnTabFocused(info -> {
            controller.onTabFocused(info);
            statusBar.setFocusedTab(info);
            if (addNoteButton != null) {
                addNoteButton.setEnabled(tabPane.hasActiveTab());
            }
        });
        tabPane.setRevealInTree(req -> controller.syncToFocusedTab(req));
        tabPane.setToastNotifier(msg -> ToastNotification.show(mainTabs, msg));
        // タブヘッダ右クリックの「Close All」もメニュー経路と同じ確認ロジックへ委譲する
        tabPane.setCloseAllRequestHandler(this::confirmAndCloseAllTabs);
        Setting splitSetting = Main.getSetting();
        if (splitSetting != null) {
            tabPane.setTabSplitRatio(splitSetting.getTabSplitRatio());
            tabPane.setAutoFitOnRender(splitSetting.isAutoFitOnRender());
        }
        // 図タブを「別ウィンドウ」へ切り出す仕組み (2 画面で確認しながら作業できるように)。
        // 解析キャッシュだけ共有し、各ウィンドウは独立した DiagramTabPane を持つ。
        detachedWindows = new DetachedDiagramWindows(cache, this, tabPane,
                () -> {
                    Setting s = Main.getSetting();
                    return s == null || s.isAutoFitOnRender();
                },
                splitSetting != null ? splitSetting.getMaxDiagramTabs() : 20,
                splitSetting != null ? splitSetting.getRenderedTabs() : 4,
                tabPane.notesBinder());
        tabPane.setOnMoveToNewWindow(detachedWindows::moveToNewWindow);
        // 同じ図をメインと別ウィンドウで二重に開かない (既存タブがあればそこへフォーカス)。
        tabPane.setCrossWindowFocus(key -> detachedWindows.focusExistingElsewhere(tabPane, key));
        add(statusBar.getComponent(), BorderLayout.SOUTH);
        setGlassPane(loadingOverlay);
        installDropTarget();
        applyInitialWindowSize();
        initPersistorsAndLoader();

        if (initialProject != null && initialProject.isDirectory()) {
            SwingUtilities.invokeLater(() -> loadProject(initialProject));
        }
    }

    /** treePanel / 進捗バー等のイベントリスナを配線する (図のプレビューは各タブが自前で持つ)。 */
    private void wirePanelListeners() {
        treePanel.setOnMethodSelected(sel -> controller.onTreeMethodSelected(sel));
        treePanel.setOnActivityMethodSelected(sel -> controller.onTreeActivityMethodSelected(sel));
        treePanel.setOnClassSelected(cls -> controller.onTreeClassSelected(cls));
        treePanel.setOnPackageSelected(pkg -> controller.onTreePackageSelected(pkg));
        treePanel.setOnModuleSelected(mod -> controller.onTreeModuleSelected(mod));
        treePanel.setOnManifestSelected(m -> controller.onTreeManifestSelected(m));
        treePanel.setOnComponentSelected(c -> controller.onTreeComponentSelected(c));
        treePanel.setOnOpenInNewTab(req -> controller.onTreeOpenInNewTab(req));
        treePanel.setOnPreviewInTab(req -> controller.onTreePreviewInTab(req));
        treePanel.setOnOpenSource(req -> controller.onTreeOpenSource(req));
        treePanel.setOnSoongSelected(() -> controller.openSoongDiagram());

        loadProgress.setStringPainted(true);
        loadProgress.setVisible(false);
        loadProgress.setPreferredSize(new Dimension(200, 16));
    }

    private void installDropTarget() {
        setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    @SuppressWarnings("unchecked")
                    java.util.List<File> files = (java.util.List<File>)
                            support.getTransferable().getTransferData(
                                    java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) {
                        return false;
                    }
                    File f = files.get(0);
                    if (f.isDirectory()) {
                        loadProject(f);
                    } else if (PumlEditorSupport.isPumlFile(f)) {
                        openPumlFileDirect(f); // .puml はエディタタブで開く
                    } else {
                        String name = f.getName().toLowerCase(java.util.Locale.ROOT);
                        if (name.endsWith(".jar") || name.endsWith(".aar")
                                || name.endsWith(".class")) {
                            projectLoader.startArchive(f);
                        } else {
                            loadProject(f);
                        }
                    }
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
    }

    /** メニューバーを構築して各メニュー項目フィールドへ反映する。 */
    private void buildMenuBar() {
        MenuBarBuilder.Callbacks mcb = new MenuBarBuilder.Callbacks();
        mcb.chooseProject = this::chooseProject;
        mcb.openArchive = this::openArchive;
        mcb.newUmlDiagram = t -> openPumlEditorTab(t.body(), null);
        mcb.openPumlFile = this::openPumlFile;
        mcb.editActiveAsPuml = this::editActiveAsPuml;
        mcb.savePumlTab = () -> tabPane.saveActivePumlEditor(false);
        mcb.savePumlTabAs = () -> tabPane.saveActivePumlEditor(true);
        mcb.diffPumlVsSaved = () -> tabPane.showDiffVsSavedForActiveEditor();
        mcb.chooseAndExport = this::chooseAndExport;
        mcb.exportClassDiagramsPerFolder = this::exportClassDiagramsPerFolder;
        mcb.exportFunctionList = this::exportFunctionList;
        mcb.exportMemberList = this::exportMemberList;
        mcb.refreshDiagram = this::refreshDiagram;
        mcb.cancelLoading = () -> {
            if (loadingCancelToken != null) {
                loadingCancelToken.cancel();
                status.setText(Messages.get("status.cancelling"));
            }
        };
        mcb.exitApp = this::exitApplication;
        mcb.loadProject = this::loadProject;
        mcb.openEntitySearch = () -> controller.openEntitySearch();
        mcb.findInDiagram = () -> tabPane.activateFindInActiveTab();
        mcb.pickSequenceEntry = () -> controller.pickSequenceEntry();
        mcb.openParticipantFilterDialog = () -> controller.openParticipantFilterDialog();
        mcb.clearSequenceParticipants = () -> {
            // 参加者フィルタはタブ固有。アクティブタブの spec から外し、下書きも空にする (#40)。
            DiagramRequest active = controller.activeTabSpec();
            boolean tabHasFilter = active != null
                    && !active.getSequenceHiddenParticipants().isEmpty();
            if (tabHasFilter || !state.sequenceHiddenParticipants.isEmpty()) {
                state.sequenceHiddenParticipants.clear();
                status.setText(Messages.get("status.clearedSeqFilter"));
                controller.applySpecToActiveTab(
                        active != null ? active.withSequenceHiddenParticipants(null) : null);
            }
        };
        mcb.pickActivityEntry = () -> controller.pickActivityEntry();
        mcb.pickLayoutFile = () -> controller.pickLayoutFile();
        mcb.pickNavigationGraph = () -> controller.pickNavigationGraph();
        mcb.applyPreset = this::applyPreset;
        mcb.openScopeDialog = () -> controller.openScopeDialog();
        mcb.clearScope = () -> {
            // スコープはタブ固有。アクティブタブの spec から外し、下書きも空にする (#40)。
            state.currentScope = null;
            DiagramRequest active = controller.activeTabSpec();
            controller.applySpecToActiveTab(active != null ? active.withScope(null) : null);
        };
        mcb.enableGraphviz = this::enableGraphviz;
        mcb.selectDiagramKindFromMenu = k -> controller.selectDiagramKind(k);
        mcb.syncDiagramToggle = k -> controller.syncDiagramToggle(k);
        mcb.applyTheme = this::applyTheme;
        mcb.openStyleSettings = this::openStyleSettings;
        mcb.openPreferences = this::openPreferences;
        mcb.clearAnalysisCache = this::clearAnalysisCache;
        mcb.zoomIn = () -> tabPane.zoomInActive();
        mcb.zoomOut = () -> tabPane.zoomOutActive();
        mcb.zoomReset = () -> tabPane.zoomResetActive();
        mcb.zoomToFit = () -> tabPane.zoomToFitActive();
        mcb.moveTabToNewWindow = () -> {
            // 空振り (エディタ/非図タブ/タブ無し/ロード中) では無反応にせずトーストで理由を示す。
            if (tabPane.canMoveActiveTab()) {
                tabPane.moveActiveTabToNewWindow();
            } else {
                ToastNotification.show(mainTabs, Messages.get("window.detached.cannotMove"));
            }
        };
        mcb.closeActiveTab = () -> tabPane.closeActiveTab();
        mcb.closeOtherTabs = () -> tabPane.closeOtherTabsExceptActive();
        mcb.closeTabsToRight = () -> tabPane.closeTabsToRightOfActive();
        mcb.closeAllTabs = this::confirmAndCloseAllTabs;
        mcb.reopenClosedTab = () -> tabPane.reopenLastClosedTab();
        // メニューが開く直前のタブ系項目の活性制御用 (buildCenterTabs より先に呼ばれるため
        // 遅延評価 + null ガードにする)。
        mcb.dynamicTabCount = () -> tabPane != null ? tabPane.dynamicTabCount() : 0;
        mcb.closedTabHistorySize = () -> tabPane != null ? tabPane.closedTabHistorySize() : 0;
        mcb.dynamicTabFocused = () -> tabPane != null && tabPane.dynamicTabFocused();
        mcb.hasTabsToRightOfActive = () -> tabPane != null && tabPane.hasTabsToRightOfActive();
        mcb.openCommandPalette = () -> CommandPalette.show(this, paletteCommands);
        mcb.toggleSidebar = () -> {
            AppShortcuts.toggleSidebar(centerSplit);
            activityBar.setSidebarActive(centerSplit.getDividerLocation() > 2);
        };
        mcb.openSourceForActiveTab = () -> tabPane.showSourceForActiveTab();
        mcb.addNoteToActiveTab = () -> tabPane.addNoteToActiveTab();
        mcb.toggleNotesPanel = () -> tabPane.toggleActiveNotesPanel();
        mcb.focusExplorer = () -> {
            if (centerSplit.getDividerLocation() <= 2) {
                AppShortcuts.toggleSidebar(centerSplit);
                activityBar.setSidebarActive(true);
            }
            treePanel.requestFocusInWindow();
        };
        mcb.navigateBack = () -> tabPane.navigateBack();
        mcb.navigateForward = () -> tabPane.navigateForward();
        mcb.openLogViewer = () -> LogViewerDialog.showFor(this);
        mcb.openErrorReference = () -> ErrorReferenceDialog.showFor(this, null);
        paletteCommands = AppCommands.from(mcb);
        MenuBarBuilder.Result menuResult =
                new MenuBarBuilder(DiagramKind.CLASS, MENU_MASK, mcb, this).build();
        cancelLoadingItem = menuResult.cancelLoadingItem;
        diagramItems = menuResult.diagramItems;
        diagramGroup = menuResult.diagramGroup;
        themeItems = menuResult.themeItems;
        themeGroup = menuResult.themeGroup;
        exportMenuItems = menuResult.exportItems;
        sequenceOnlyMenuItems = menuResult.contextualItems.sequenceOnlyItems;
        activityOnlyMenuItems = menuResult.contextualItems.activityOnlyItems;
        layoutOnlyMenuItems = menuResult.contextualItems.layoutOnlyItems;
        navigationOnlyMenuItems = menuResult.contextualItems.navigationOnlyItems;
        setJMenuBar(menuResult.menuBar);
        installQuickOpenShortcut();
    }

    /**
     * VS Code 流の Quick Open (Ctrl+P) を追加する。既存のエンティティ横断検索
     * ({@link DiagramController#openEntitySearch()}, Ctrl+Shift+F) を、より馴染みのある
     * ワンキーからも開けるようにするエイリアス。メニュー項目のアクセラレータ (Ctrl+Shift+F)
     * とは別に、ルートペインへ WHEN_IN_FOCUSED_WINDOW でバインドする。
     */
    private void installQuickOpenShortcut() {
        int menuMask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.KeyStroke ks = javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_P, menuMask);
        javax.swing.JRootPane rp = getRootPane();
        rp.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(ks, "quickOpen");
        rp.getActionMap().put("quickOpen", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                controller.openEntitySearch();
            }
        });
    }

    /** 中央のツリー + タブ (動的ダイアグラムタブ + 末尾の固定ユーティリティタブ) を構築する。 */
    private void buildCenterTabs() {
        // 右側: VS Code 風のフラットタブバー (動的タブ + 末尾の固定ユーティリティタブ)。Home タブは持たない。
        mainTabs = new JTabbedPane(JTabbedPane.TOP);
        // タブ多数でも 1 段スクロール表示にし、多段折り返しで図領域が潰れるのを防ぐ。
        mainTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // ユーティリティタブ (固定・末尾 11 本)。Material アイコン + tooltip で用途を示す。
        mainTabs.addTab("Manifest", MaterialIcons.menu(MaterialIcons.Glyph.MANIFEST),
                manifestSummaryPanel, "Components & permissions");
        mainTabs.addTab("Impact", MaterialIcons.menu(MaterialIcons.Glyph.BOLT),
                impactPanel, "Change impact analysis");
        mainTabs.addTab("References", MaterialIcons.menu(MaterialIcons.Glyph.CALL_SPLIT),
                referencesPanel, "Callers / users of a symbol");
        mainTabs.addTab("Func Diff", MaterialIcons.menu(MaterialIcons.Glyph.CODE),
                funcDiffPanel, "Compare two methods");
        mainTabs.addTab("Insights", MaterialIcons.menu(MaterialIcons.Glyph.STAR),
                insightsPanel, "Hot spots & metrics");
        mainTabs.addTab("Doxygen", MaterialIcons.menu(MaterialIcons.Glyph.INFO),
                doxygenPanel, "Doxygen XML documentation");
        mainTabs.addTab("TODO", MaterialIcons.menu(MaterialIcons.Glyph.NOTE_ADD),
                doxygenTodoPanel, "@todo / @bug / @deprecated (doxygen)");
        mainTabs.addTab("Groups", MaterialIcons.menu(MaterialIcons.Glyph.ACCOUNT_TREE),
                doxygenGroupsPanel, "@defgroup / @ingroup hierarchy (doxygen)");
        mainTabs.addTab("Functions", MaterialIcons.menu(MaterialIcons.Glyph.FUNCTION),
                methodListPanel, "Function usage table");
        mainTabs.addTab("Members", MaterialIcons.menu(MaterialIcons.Glyph.LAYERS),
                memberListPanel, "All class members");
        gitPanel.setStatusReporter(status::setText);
        mainTabs.addTab("Git", MaterialIcons.menu(MaterialIcons.Glyph.CALL_SPLIT),
                gitPanel, "Git history / branches / blame (read-only)");

        // 動的タブマネージャ (fixedSuffix=FIXED_UTILITY_TABS で末尾ユーティリティタブの手前に挿入)
        tabPane = new DiagramTabPane(mainTabs, FIXED_UTILITY_TABS, cache, state,
                status::setText, this::updateZoomLabelFromValue);
        // References (逆参照) の行ダブルクリック → 参照箇所のソースへジャンプ。
        referencesPanel.setOnOpenSite(site -> tabPane.openSourceSite(
                site.getCallerFqn(), site.getFile(), site.getLineHint()));
        // Ctrl+W / Ctrl+Shift+T / Ctrl+Tab 等。末尾の固定ユーティリティタブは
        // Ctrl+Tab 巡回から除外し、VS Code 同様に図 (動的タブ) だけを循環する。
        TabKeyBindings.install(mainTabs, FIXED_UTILITY_TABS,
                tabPane::closeActiveTab, tabPane::reopenLastClosedTab);

        // Functions / Members タブ表示時は一覧を遅延生成
        mainTabs.addChangeListener(ev -> {
            if (mainTabs.getSelectedComponent() == methodListPanel) {
                updateFunctionList();
            } else if (mainTabs.getSelectedComponent() == memberListPanel) {
                updateMemberList();
            }
        });
        methodListPanel.setOnScopeChanged(this::updateFunctionList); // 表示範囲切替で即再生成
        memberListPanel.setOnScopeChanged(this::updateMemberList);

        mainTabs.setMinimumSize(new Dimension(200, 100));
        centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, mainTabs);
        centerSplit.setResizeWeight(0.22);
        centerSplit.setDividerLocation(280);
        centerCards = new CenterCardView(centerSplit,
                new WelcomePanel(this::chooseProject, this::openArchive, this::loadProject,
                        () -> openPumlEditorTab(PumlTemplate.CLASS.body(), null)));
        add(centerCards, BorderLayout.CENTER);

        // VS Code 風の左端アクティビティバー (主要導線をアイコン縦列に集約)。
        ActivityBar.Actions acts = new ActivityBar.Actions();
        acts.openProject = this::chooseProject;
        acts.toggleSidebar = () -> {
            AppShortcuts.toggleSidebar(centerSplit);
            activityBar.setSidebarActive(centerSplit.getDividerLocation() > 2);
        };
        acts.search = () -> controller.openEntitySearch();
        acts.commandPalette = () -> CommandPalette.show(this, paletteCommands);
        acts.preferences = this::openPreferences;
        activityBar = new ActivityBar(acts);
        add(activityBar, BorderLayout.WEST);
    }

    /** 上部ツールバーを構築する。 */
    private void buildToolBar() {
        ToolBarBuilder.Callbacks tcb = new ToolBarBuilder.Callbacks();
        tcb.chooseProject = this::chooseProject;
        tcb.chooseAndExport = this::chooseAndExport;
        tcb.refreshDiagram = this::refreshDiagram;
        tcb.openEntitySearch = () -> controller.openEntitySearch();
        tcb.addNote = () -> tabPane.addNoteToActiveTab();
        tcb.selectDiagramKind = k -> controller.selectDiagramKind(k);
        ToolBarBuilder.Result toolBarResult =
                new ToolBarBuilder(DiagramKind.CLASS, tcb).build();
        diagramToggles = toolBarResult.diagramToggles;
        addNoteButton = toolBarResult.addNoteButton;
        exportToolbarButton = toolBarResult.saveButton;
        if (addNoteButton != null) {
            addNoteButton.setEnabled(false);
        }
        add(toolBarResult.toolBarPanel, BorderLayout.NORTH);
    }

    /** 保存済みウィンドウサイズ・位置を適用して pack する。 */
    private void applyInitialWindowSize() {
        Setting setting = Main.getSetting();
        int w = setting.getWindowWidth() > 0 ? setting.getWindowWidth() : 1200;
        int h = setting.getWindowHeight() > 0 ? setting.getWindowHeight() : 800;
        setPreferredSize(new Dimension(w, h));
        pack();
        WindowStateManager.restoreLocationAndDivider(this, centerSplit, setting);
    }

    /** プロジェクト設定の永続化担当とプロジェクトローダを生成する。 */
    private void initPersistorsAndLoader() {
        settingsPersistor = new ProjectSettingsPersistor(
                Main::getSetting,
                () -> syncThemeMenuSelection(PlantUmlRenderer.getStyle()));

        ProjectLoaderDeps loaderDeps = new ProjectLoaderDeps();
        loaderDeps.cache = cache;
        loaderDeps.refIndexCache = refIndexCache;
        loaderDeps.state = state;
        loaderDeps.treePanel = treePanel;
        loaderDeps.manifestSummaryPanel = manifestSummaryPanel;
        loaderDeps.loadProgress = loadProgress;
        loaderDeps.loadingOverlay = loadingOverlay;
        loaderDeps.cancelLoadingItem = cancelLoadingItem;
        loaderDeps.statusLabel = status;
        loaderDeps.parentFrame = this;
        loaderDeps.cancelTokenSetter = token -> loadingCancelToken = token;
        loaderDeps.projectRootSetter = root -> currentProjectRoot = root;
        loaderDeps.onLoadSuccess = root -> {
            // 旧プロジェクトの図タブ・再オープン履歴・ナビ履歴を破棄する。残すと
            // 再描画 (F5/スタイル変更/LRU 復帰) が新プロジェクトの解析結果に対して走り、
            // 旧ラベルのまま空図・別クラスの図が表示される。
            tabPane.onProjectSwitched();
            // 別ウィンドウも旧プロジェクトの図を保持しており、共有 cache が差し替わると
            // stale 図の再描画や crossWindowFocus の誤ヒットを起こすため、まとめて閉じる。
            if (detachedWindows != null) {
                detachedWindows.closeAll();
            }
            // Doxygen/TODO/Groups タブの前プロジェクト結果も破棄する。
            doxygenResultCache.clear();
            // Functions / Members は遅延生成のため、表示中でなければ次回選択時に
            // 新プロジェクトで再生成される。表示中なら即再生成して旧一覧を残さない。
            if (mainTabs.getSelectedComponent() == methodListPanel) {
                updateFunctionList();
            } else if (mainTabs.getSelectedComponent() == memberListPanel) {
                updateMemberList();
            }
            persistAndRestoreProjectSettings(root);
            updateManifestSummary();
            gitPanel.setRepositoryRoot(root); // git リポジトリなら Git タブを有効化
            centerCards.showWorkspace();
            controller.updateAvailableDiagrams(java.util.EnumSet.allOf(DiagramKind.class));
            controller.openDefaultDiagram();
            if (exportMenuItems != null) {
                exportMenuItems.forEach(item -> item.setEnabled(true));
            }
            if (exportToolbarButton != null) {
                exportToolbarButton.setEnabled(true);
            }
        };
        projectLoader = new ProjectLoader(loaderDeps);
    }

    private ProjectLoader projectLoader;
    private ProjectSettingsPersistor settingsPersistor;
    private DiagramController controller;

    /** 図制御コントローラを必要な状態・UI 参照・コールバックを束ねて生成する。 */
    private DiagramController createDiagramController() {
        DiagramControllerDeps deps = new DiagramControllerDeps();
        deps.state = state;
        deps.cacheSupplier = () -> cache;
        deps.diagramItems = diagramItems;
        deps.diagramToggles = diagramToggles;
        deps.treePanel = treePanel;
        deps.mainTabs = mainTabs;
        deps.tabPane = tabPane;
        deps.statusLabel = status;
        deps.parentFrame = this;
        deps.refreshDiagram = this::refreshDiagram;
        deps.onKindChanged = kind -> this.currentKind = kind;
        deps.sequenceOnlyMenuItems = sequenceOnlyMenuItems;
        deps.activityOnlyMenuItems = activityOnlyMenuItems;
        deps.layoutOnlyMenuItems = layoutOnlyMenuItems;
        deps.navigationOnlyMenuItems = navigationOnlyMenuItems;
        return new DiagramController(deps);
    }

    // --- スタイル / プリセット ------------------------------------------------

    /**
     * 指定された {@link DiagramPreset} を現在のスコープに適用して再描画する。
     * 既存スコープのフィルタ設定 (パッケージ・seed 等) は維持し、表示密度関連の
     * 項目だけプリセットで書き換える。
     */
    private void applyPreset(DiagramPreset p) {
        DiagramScope.Builder b = state.currentScope != null
                ? state.currentScope.toBuilder()
                : DiagramScope.builder();
        p.applyTo(b);
        state.currentScope = b.build();
        status.setText(juml.util.Messages.get("status.presetPrefix") + p.getDisplayName());
        controller.applyStateToActiveTab();
    }

    private void applyTheme(String theme) {
        DiagramStyle next = PlantUmlRenderer.getStyle().copy();
        next.setTheme(theme);
        applyStyle(next);
    }

    private void openStyleSettings() {
        juml.Setting setting = Main.getSetting();
        boolean curShow = setting != null && setting.isSequenceShowComments();
        juml.core.formats.uml.PlantUmlClassDiagram.CommentStyle curStyle =
                setting != null && "NOTE".equalsIgnoreCase(setting.getSequenceCommentStyle())
                        ? juml.core.formats.uml.PlantUmlClassDiagram.CommentStyle.NOTE
                        : juml.core.formats.uml.PlantUmlClassDiagram.CommentStyle.INLINE;
        juml.core.formats.uml.PlantUmlSequenceDiagram.CommentPlacement curPlacement =
                setting != null && "PARTICIPANT_TOP".equalsIgnoreCase(
                        setting.getSequenceCommentPlacement())
                        ? juml.core.formats.uml.PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP
                        : juml.core.formats.uml.PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
        boolean curQualify = setting == null || setting.isSequenceQualifyMethodNames();
        StyleSettingsDialog.ClassDiagramPrefs curClass = setting != null
                ? new StyleSettingsDialog.ClassDiagramPrefs(
                        setting.isClassDiagramShowFields(),
                        setting.isClassDiagramShowMethods(),
                        setting.isClassDiagramShowAnnotations(),
                        setting.isClassDiagramPublicOnly(),
                        setting.isClassDiagramExcludeExternal(),
                        setting.isClassDiagramMarkExternalSupertypes(),
                        setting.isClassDiagramColorCodeRelations(),
                        setting.getClassDiagramCommentMaxLength(),
                        StyleSettingsDialog.ClassDiagramPrefs.parseCsv(
                                setting.getClassDiagramHiddenAnnotations()),
                        setting.isClassDiagramHideEmptyMembers(),
                        setting.isClassDiagramHideUnlinked())
                : StyleSettingsDialog.ClassDiagramPrefs.defaults();
        int curSeqMaxDepth = setting != null ? setting.getSequenceMaxDepth() : 5;
        boolean curSeqShowArgs = setting != null && setting.isSequenceShowCallArguments();
        ActivityDiagramPrefs curActivity = setting != null
                ? new ActivityDiagramPrefs(
                        setting.isActivityExpandInlineCallbacks(),
                        setting.isActivityShowLocalVars(),
                        setting.isActivityShowAssignments(),
                        setting.isActivityShowCallArguments(),
                        setting.isActivityShowInlineComments())
                : ActivityDiagramPrefs.defaults();
        int curCallGraphDepth = setting != null ? setting.getCallGraphMaxDepth() : 4;
        StyleSettingsDialog.Result edited = StyleSettingsDialog.showDialog(
                this, PlantUmlRenderer.getStyle(), curShow, curStyle,
                curPlacement, curQualify, curSeqMaxDepth, curSeqShowArgs,
                curActivity, curClass, curCallGraphDepth);
        if (edited != null) {
            applyStyleSettings(edited);
        }
    }

    /** Style ダイアログ結果 (Style + シーケンス図/アクティビティ図/クラス図/コールグラフ設定) を反映する。 */
    private void applyStyleSettings(StyleSettingsDialog.Result r) {
        try {
            juml.Setting setting = Main.getSetting();
            if (setting != null) {
                setting.setSequenceShowComments(r.sequenceShowComments);
                setting.setSequenceCommentStyle(r.sequenceCommentStyle.name());
                setting.setSequenceCommentPlacement(r.sequenceCommentPlacement.name());
                setting.setSequenceQualifyMethodNames(r.sequenceQualifyMethodNames);
                setting.setSequenceMaxDepth(r.sequenceMaxDepth);
                setting.setSequenceShowCallArguments(r.sequenceShowCallArguments);
                if (r.activityDiagram != null) {
                    setting.setActivityExpandInlineCallbacks(
                            r.activityDiagram.expandInlineCallbacks);
                    setting.setActivityShowLocalVars(r.activityDiagram.showLocalVars);
                    setting.setActivityShowAssignments(r.activityDiagram.showAssignments);
                    setting.setActivityShowCallArguments(
                            r.activityDiagram.showCallArguments);
                    setting.setActivityShowInlineComments(
                            r.activityDiagram.showInlineComments);
                }
                setting.setCallGraphMaxDepth(r.callGraphMaxDepth);
                if (r.classDiagram != null) {
                    StyleSettingsDialog.ClassDiagramPrefs cp = r.classDiagram;
                    setting.setClassDiagramShowFields(cp.showFields);
                    setting.setClassDiagramShowMethods(cp.showMethods);
                    setting.setClassDiagramShowAnnotations(cp.showAnnotations);
                    setting.setClassDiagramPublicOnly(cp.publicOnly);
                    setting.setClassDiagramExcludeExternal(cp.excludeExternal);
                    setting.setClassDiagramMarkExternalSupertypes(cp.markExternalSupertypes);
                    setting.setClassDiagramColorCodeRelations(cp.colorCodeRelations);
                    setting.setClassDiagramHideEmptyMembers(cp.hideEmptyMembers);
                    setting.setClassDiagramHideUnlinked(cp.hideUnlinked);
                    setting.setClassDiagramCommentMaxLength(cp.commentMaxLength);
                    setting.setClassDiagramHiddenAnnotations(cp.hiddenAnnotationsCsv());
                }
            }
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
        saveCurrentProjectSettings();
        applyStyle(r.style);
    }

    /** アプリ全体設定 (Preferences) ダイアログを開き、結果を永続化する。 */
    private void openPreferences() {
        Setting setting = Main.getSetting();
        String curLaf = setting != null ? setting.getLookAndFeel() : "SYSTEM";
        boolean curRestore = setting != null && setting.isRestoreLastProjectOnStartup();
        String curLang = setting != null ? setting.getLanguage() : "ja";
        String curQuality = setting != null ? setting.getDiagramRenderQuality() : "AUTO";
        int curMaxTabs = setting != null ? setting.getMaxDiagramTabs() : 20;
        int curRenderedTabs = setting != null ? setting.getRenderedTabs() : 4;
        boolean curAutoFit = setting == null || setting.isAutoFitOnRender();
        PreferencesDialog.Result r =
                PreferencesDialog.showDialog(this, curLaf, curRestore, curLang, curQuality,
                        curMaxTabs, curRenderedTabs, curAutoFit);
        if (r == null) {
            return;
        }
        boolean lafChanged = !r.lookAndFeel.equalsIgnoreCase(curLaf);
        boolean langChanged = !r.language.equalsIgnoreCase(curLang);
        boolean qualityChanged = !r.diagramRenderQuality.equalsIgnoreCase(curQuality);
        boolean tabLimitsChanged = r.maxDiagramTabs != curMaxTabs
                || r.renderedTabs != curRenderedTabs;
        try {
            if (setting != null) {
                setting.setLookAndFeel(r.lookAndFeel);
                setting.setRestoreLastProjectOnStartup(r.restoreLastProjectOnStartup);
                setting.setLanguage(r.language);
                setting.setDiagramRenderQuality(r.diagramRenderQuality);
                setting.setMaxDiagramTabs(r.maxDiagramTabs);
                setting.setRenderedTabs(r.renderedTabs);
                setting.setAutoFitOnRender(r.autoFitOnRender);
                Main.saveSetting();
            }
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
        // 描画品質は再起動不要で即時反映する (全ウィンドウを再描画してバッファを作り直す)。
        if (qualityChanged) {
            DiagramRenderQuality.setCurrent(
                    DiagramRenderQuality.fromKey(r.diagramRenderQuality));
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                w.repaint();
            }
        }
        // タブ上限/描画保持数も再起動不要で即時反映する (別ウィンドウ群にも伝播)。
        if (tabLimitsChanged && tabPane != null) {
            tabPane.setTabBudget(r.maxDiagramTabs, r.renderedTabs);
            if (detachedWindows != null) {
                detachedWindows.setTabBudget(r.maxDiagramTabs, r.renderedTabs);
            }
        }
        // 「描画時に自動フィット」は再起動不要で即時反映する (別ウィンドウは supplier 経由で追従)。
        if (tabPane != null) {
            tabPane.setAutoFitOnRender(r.autoFitOnRender);
        }
        // 外観 (L&F) は FlatLaf 等なら再起動なしで即時反映する。失敗した L&F や
        // 言語変更は生成済み UI へ遡及できないため、その時だけ再起動を促す。
        boolean lafAppliedLive = !lafChanged
                || PreferencesDialog.applyLookAndFeelLive(r.lookAndFeel);
        if (lafAppliedLive && lafChanged) {
            // ライブ適用後はメインフレームのレイアウトを整え直す。
            SwingUtilities.updateComponentTreeUI(this);
            revalidate();
            repaint();
        }
        if ((lafChanged && !lafAppliedLive) || langChanged) {
            // 再起動が必要な変更: 「今すぐ終了」(保存を通る既存の終了経路) か「後で」を選べる。
            String exitNow = juml.util.Messages.get("pref.exitNow");
            String later = juml.util.Messages.get("pref.restartLater");
            Object[] options = {exitNow, later};
            int sel = JOptionPane.showOptionDialog(this,
                    juml.util.Messages.get("pref.restartNotice"),
                    juml.util.Messages.get("menubar.settings.preferences"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, options, later);
            if (sel == 0) {
                exitApplication();
            }
        }
    }

    /**
     * 現在のプロジェクトの解析キャッシュ (メモリ + ディスク) を破棄し、再解析する。
     * プロジェクト未読込なら何もせず通知のみ。
     */
    private void clearAnalysisCache() {
        File root = cache.getProjectRoot();
        String title = Messages.get("menubar.settings.clearCache");
        if (root == null) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("dlg.clearCache.none"),
                    title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!DialogUtils.confirmDestructive(this,
                Messages.get("dlg.clearCache.confirm"), title)) {
            return;
        }
        cache.invalidate();
        status.setText(Messages.get("status.cacheCleared"));
        loadProject(root);
    }

    /** スタイル変更を全方位 (レンダラ / 永続化 / メニュー UI / 再描画) に反映する。 */
    private void applyStyle(DiagramStyle style) {
        PlantUmlRenderer.setStyle(style);
        try {
            Main.getSetting().setStyle(style);
            Main.saveSetting();
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
        saveCurrentProjectSettings();
        syncThemeMenuSelection(style);
        // スタイルは全体共通設定なので、開いているすべてのタブを再描画する。
        tabPane.rerenderAllTabs();
    }

    private void syncThemeMenuSelection(DiagramStyle style) {
        String theme = style.getTheme() == null ? "" : style.getTheme();
        JRadioButtonMenuItem item = themeItems.get(theme);
        if (item != null) {
            item.setSelected(true);
        } else {
            // カスタムテーマ名: ラジオ選択を外す
            themeGroup.clearSelection();
        }
    }

    // --- イベント処理 ---------------------------------------------------------

    private void chooseProject() {
        java.util.List<juml.ProjectRecord> records;
        try {
            records = juml.ProjectRepository.getInstance().listRecent(10);
        } catch (RuntimeException ex) {
            records = java.util.Collections.emptyList();
        }
        File chosen = OpenProjectDialog.show(this, records, rec -> {
            try {
                juml.ProjectRepository.getInstance().deleteById(rec.getId());
            } catch (RuntimeException ignored) {
                // 削除はベストエフォート (リポジトリ未初期化など)
            }
        });
        if (chosen != null) {
            loadProject(chosen);
        }
    }

    private void loadProject(File root) {
        projectLoader.start(root);
    }

    /**
     * .jar/.aar/.class (コンパイル済みバイトコード) を解析対象として開く。
     * {@link ProjectLoader#startArchive(File)} に委譲し、ロード後はプロジェクトと同じく
     * ツリー・既定タブを更新する。
     */
    private void openArchive() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle(Messages.get("menubar.file.openArchive"));
        chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "JAR/AAR/Class (.jar, .aar, .class)", "jar", "aar", "class"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        if (chosen != null) {
            projectLoader.startArchive(chosen);
        }
    }

    /**
     * 現在ロード中のプロジェクトを再帰的に走査し、ソースファイルを含むフォルダごとに
     * 1 枚ずつ PlantUML クラス図 ({@code classes.puml} + {@code classes.svg}) を
     * 出力する。実処理は {@link PerFolderExporter} に委譲。
     */
    private void exportClassDiagramsPerFolder() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("dlg.openProjectFirst"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File projectRoot = cache.getProjectRoot();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    Messages.get("dlg.projectRootUnavailable"),
                    Messages.get("dlg.noProject.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        LazyDetail.withDetailedClasses(cache, this, detailed ->
                PerFolderExporter.choose(this, projectRoot,
                        detailed, cache.getIndex(),
                        loadProgress, status));
    }

    /** Manifest Summary タブのテキストを最新の解析結果で更新する。 */
    private void updateManifestSummary() {
        if (cache.isLoaded() && cache.getAnalysis() != null) {
            manifestSummaryPanel.setText(
                    TextSummaryReport.toManifestMarkdown(cache.getAnalysis()));
        } else {
            manifestSummaryPanel.setText("");
        }
    }

    /** 指定クラス集合の関数使用マップ (署名・利用側・実行条件・リスナー) を指定形式で構築する。 */
    private String buildFunctionListReport(
            java.util.List<juml.core.formats.uml.JavaClassInfo> classes,
            juml.core.formats.uml.MethodUsageReport.Format format) {
        return FunctionListReport.build(classes, format, currentProjectRoot,
                refIndexCache, status::setText);
    }

    // Functions / Members 一覧の世代カウンタ。古い SwingWorker 結果でパネルを上書きしない。
    private int functionListGen;
    private int memberListGen;

    /**
     * 「Functions」タブの内容をプロジェクトの現状で更新する (タブ選択時に遅延生成)。
     * レポート生成は UiActionScanner の IO と全クラス走査を含み重いため、EDT を
     * ブロックしないよう {@link javax.swing.SwingWorker} で背景実行する。
     */
    private void updateFunctionList() {
        if (!cache.isLoaded()) {
            methodListPanel.setText("");
            methodListPanel.setContextInfo("");
            return;
        }
        final int gen = ++functionListGen;
        final FilteredListPanel.Scope scope = methodListPanel.getScope();
        final TreeNodeOpenRequest subject = tabPane.lastFocusedDiagramRequest();
        methodListPanel.setText(Messages.get("status.generatingList"));
        new javax.swing.SwingWorker<ListScope.Result, Void>() {
            @Override
            protected ListScope.Result doInBackground() {
                java.util.List<juml.core.formats.uml.JavaClassInfo> classes =
                        ListScope.filter(cache.getDetailedClasses(), scope, subject);
                String text = buildFunctionListReport(classes,
                        juml.core.formats.uml.MethodUsageReport.Format.TABLE);
                return new ListScope.Result(text, ListScope.context(scope, subject, classes.size()));
            }

            @Override
            protected void done() {
                if (gen != functionListGen) {
                    return; // より新しい更新が走っているので破棄
                }
                try {
                    ListScope.Result r = get();
                    methodListPanel.setText(r.text);
                    methodListPanel.setContextInfo(r.context);
                } catch (java.lang.InterruptedException | java.util.concurrent.ExecutionException ex) {
                    methodListPanel.setText("");
                }
            }
        }.execute();
    }

    /**
     * 「Members」タブに全クラスの純粋なメンバー一覧を表示する (タブ選択時に遅延生成)。
     * {@code getDetailedClasses()} は Stage B 昇格 (再パース) を伴いうるため、
     * EDT をブロックしないよう {@link javax.swing.SwingWorker} で背景実行する。
     */
    private void updateMemberList() {
        if (!cache.isLoaded()) {
            memberListPanel.setText("");
            memberListPanel.setContextInfo("");
            return;
        }
        final int gen = ++memberListGen;
        final FilteredListPanel.Scope scope = memberListPanel.getScope();
        final TreeNodeOpenRequest subject = tabPane.lastFocusedDiagramRequest();
        memberListPanel.setText(Messages.get("status.generatingList"));
        new javax.swing.SwingWorker<ListScope.Result, Void>() {
            @Override
            protected ListScope.Result doInBackground() {
                java.util.List<juml.core.formats.uml.JavaClassInfo> classes =
                        ListScope.filter(cache.getDetailedClasses(), scope, subject);
                String text = juml.core.formats.uml.ClassMemberReport.render(classes);
                return new ListScope.Result(text, ListScope.context(scope, subject, classes.size()));
            }

            @Override
            protected void done() {
                if (gen != memberListGen) {
                    return; // より新しい更新が走っているので破棄
                }
                try {
                    ListScope.Result r = get();
                    memberListPanel.setText(r.text);
                    memberListPanel.setContextInfo(r.context);
                } catch (java.lang.InterruptedException | java.util.concurrent.ExecutionException ex) {
                    memberListPanel.setText("");
                }
            }
        }.execute();
    }

    /** 関数使用マップをファイルに保存する (File メニュー、Markdown テーブル / CSV を拡張子で選択)。 */
    private void exportFunctionList() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this, Messages.get("dlg.openProjectFirst"),
                    Messages.get("dlg.functions.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LazyDetail.withDetailedClasses(cache, this, detailed ->
                exportController.exportFunctionList(
                        buildFunctionListReport(detailed,
                                juml.core.formats.uml.MethodUsageReport.Format.TABLE),
                        buildFunctionListReport(detailed,
                                juml.core.formats.uml.MethodUsageReport.Format.CSV),
                        Messages.get("dlg.saveFunctionList.title")));
    }

    /** 全クラスのメンバー解析結果を Excel (.xlsx) ワークブックとして保存する (File メニュー)。 */
    private void exportMemberList() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this, Messages.get("dlg.openProjectFirst"),
                    Messages.get("dlg.members.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LazyDetail.withDetailedClasses(cache, this, detailed ->
                exportController.exportMemberWorkbook(
                        detailed, Messages.get("dlg.saveMembers.title")));
    }

    /**
     * F5 / Refresh: アクティブタブを「今の内容そのまま」で再描画する。
     *
     * <p>図タブはタブ自身の spec (seed/focus/includePackage 等の題材) を種に再レンダリングし、
     * エディタタブはテキストを種に再描画する ({@code startRender} が両方を扱う)。
     * 以前はグローバル {@link DiagramState} から spec を作り直していたため、クラス/パッケージ/
     * モジュールタブで F5 を押すと題材を失ってプロジェクト全体図に化けていた。スコープや
     * 参加者フィルタの変更でグローバル状態から作り直したいケースは、各呼び出し側が
     * {@code controller.applyStateToActiveTab()} を直接呼ぶ。</p>
     */
    private void refreshDiagram() {
        if (tabPane != null) {
            tabPane.rerenderActiveTab();
        }
    }

    /**
     * Graphviz dot を検出 / 指定して有効化する。検出できれば即再描画し、見つからなければ
     * ユーザーに dot 実行ファイルの場所を尋ねる。大きな図で Smetana レイアウトが破綻する
     * ケースを、より堅牢な dot レイアウトで描画できるようにするための導線。
     */
    private void enableGraphviz() {
        if (GraphvizLocator.redetect()) {
            tabPane.rerenderAllTabs();
            JOptionPane.showMessageDialog(this,
                    Messages.get("dlg.graphviz.detected"),
                    "Graphviz", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                Messages.get("dlg.graphviz.notFoundConfirm"),
                Messages.get("dlg.graphviz.notFoundTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(Messages.get("dlg.graphviz.chooseDot"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dot = fc.getSelectedFile();
        if (GraphvizLocator.useDotBinary(dot)) {
            tabPane.rerenderAllTabs();
            JOptionPane.showMessageDialog(this,
                    Messages.get("dlg.graphviz.enabled"),
                    "Graphviz", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    Messages.get("dlg.graphviz.notExecutable") + "\n"
                            + dot.getAbsolutePath(),
                    Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseAndExport() {
        exportController.chooseAndExport();
    }

    // --- 自由編集 PlantUML エディタ --------------------------------------------

    /**
     * 自由編集 PlantUML エディタタブを開く。プロジェクト未ロード時は Welcome 表示の
     * ままだとタブが見えないため、先にワークスペース表示へ切り替える。
     */
    private void openPumlEditorTab(String text, File file) {
        centerCards.showWorkspace();
        tabPane.openPumlEditor(text, file);
    }

    /**
     * アクティブな生成図の PlantUML を、自由編集できる新規 Untitled タブへ複製する
     * (File &gt; Edit as PlantUML)。生成図が無い / 未描画 / 既にエディタタブの場合は
     * ステータスで案内するだけで何もしない。
     */
    private void editActiveAsPuml() {
        centerCards.showWorkspace();
        if (!tabPane.editActiveAsPuml()) {
            status.setText(Messages.get("editAsPuml.unavailable"));
        }
    }

    /** 既存の .puml ファイルを選択して自由編集エディタタブで開く (File メニュー / Welcome)。 */
    private void openPumlFile() {
        File chosen = PumlEditorSupport.choosePumlToOpen(this);
        if (chosen == null) {
            return;
        }
        openPumlFileDirect(chosen);
    }

    /**
     * 指定の .puml ファイルをエディタタブで開く (ダイアログなし。ドロップ用)。
     * ファイル読み込みは {@link SwingWorker} で行い、低速/ハングしたネットワーク共有上の
     * .puml を開くときに EDT (UI) がフリーズしないようにする。読み込み結果の反映と
     * エラーダイアログは {@code done()} (EDT) で行う。
     */
    private void openPumlFileDirect(File file) {
        new SwingWorker<String, Void>() {
            private java.io.IOException failure;

            @Override
            protected String doInBackground() {
                try {
                    return PumlEditorSupport.read(file);
                } catch (java.io.IOException ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                if (failure != null) {
                    juml.util.AppLog.error(juml.util.ErrorCode.UML_E004, "UmlMainFrame",
                            "puml open failed: " + file.getAbsolutePath(), failure);
                    JOptionPane.showMessageDialog(UmlMainFrame.this,
                            Messages.get("puml.editor.openFailed") + failure.getMessage(),
                            Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String text;
                try {
                    text = get();
                } catch (java.util.concurrent.ExecutionException
                        | InterruptedException ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                openPumlEditorTab(text, file);
            }
        }.execute();
    }

    // --- 状態管理 -------------------------------------------------------------

    /** アクティブタブのズーム率 (1.0 = 100%) をステータスバーのズームラベルへ反映する。 */
    private void updateZoomLabelFromValue(double zoom) {
        int pct = (int) Math.round(zoom * 100);
        zoomLabel.setText(pct + "%");
    }

    private void saveWindowState() {
        Setting s = Main.getSetting();
        if (s != null && tabPane != null) {
            s.setTabSplitRatio(tabPane.getTabSplitRatio());
        }
        WindowStateManager.save(this, centerSplit, s, Main::saveSetting);
    }

    /**
     * アプリの終了経路を一本化する (ウィンドウの閉じるボタン / File &gt; Exit /
     * Preferences の「今すぐ終了」)。ウィンドウ状態を保存し、付箋メモの保存 IO を
     * flush してから dispose する (デーモンスレッドの保存タスクドロップ防止)。
     */
    private void exitApplication() {
        // 未保存のエディタタブがあれば保存/破棄/中止を確認する。中止なら終了しない
        // (DO_NOTHING_ON_CLOSE のためウィンドウはそのまま残る)。
        if (tabPane != null && !tabPane.confirmDiscardAllEdits()) {
            return;
        }
        saveWindowState();
        if (detachedWindows != null) {
            detachedWindows.closeAll(); // 別ウィンドウの付箋 IO を止めて破棄
        }
        if (tabPane != null) {
            tabPane.shutdown();
        }
        dispose();
    }

    /**
     * File &gt; Close All Tabs: 動的タブが 2 枚以上のときだけ確認ダイアログを挟む
     * (再オープン履歴は上限があり、まとめて閉じると復元できない場合があるため)。
     * 確認ダイアログの表示は呼び出し元 (フレーム) 側の責務とし、
     * {@link DiagramTabPane} はロジックのみを担う。
     */
    private void confirmAndCloseAllTabs() {
        runCloseAllWithConfirm(tabPane.dynamicTabCount(),
                this::showCloseAllTabsConfirm, tabPane::closeAllTabs);
    }

    /**
     * Close All Tabs の確認分岐 (テスト可能なシーム)。動的タブが 2 枚以上のときだけ
     * {@code confirm} を評価し、承認された場合のみ {@code closeAll} を実行する。
     * 1 枚以下なら確認なしで {@code closeAll} を実行する。
     *
     * @param dynamicTabCount 開いている動的タブ数
     * @param confirm         枚数を受け取り、閉じてよければ true を返す確認関数
     * @param closeAll        実際の全タブクローズ操作
     */
    static void runCloseAllWithConfirm(int dynamicTabCount,
            java.util.function.IntPredicate confirm, Runnable closeAll) {
        if (dynamicTabCount >= 2 && !confirm.test(dynamicTabCount)) {
            return;
        }
        closeAll.run();
    }

    /** Close All Tabs の確認ダイアログを表示し、YES が選ばれたら true。 */
    private boolean showCloseAllTabsConfirm(int count) {
        // 破壊的操作なので既定ボタンは「No」側 (誤って Enter で全タブを閉じない)。
        return DialogUtils.confirmDestructive(this,
                java.text.MessageFormat.format(
                        Messages.get("tab.closeAllConfirm"), count),
                Messages.get("menubar.file.closeAllTabs"));
    }

    /**
     * プロジェクトロード成功時: リポジトリに登録し、保存済み設定があれば復元する。
     * 初回ロードは設定がないため何も変わらない。
     */
    private void persistAndRestoreProjectSettings(File root) {
        settingsPersistor.restoreAndPersist(root);
    }

    private void saveCurrentProjectSettings() {
        settingsPersistor.saveCurrentProjectSettings(currentProjectRoot);
    }
}
