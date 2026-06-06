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

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
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
    private static final int MENU_MASK = computeMenuShortcutMask();

    private static int computeMenuShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException ex) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

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
    private final MethodListPanel methodListPanel = new MethodListPanel();
    private final MemberListPanel memberListPanel = new MemberListPanel();
    private final JLabel status = new JLabel(" ");
    private final JLabel zoomLabel = new JLabel("100%");
    private final JProgressBar loadProgress = new JProgressBar();
    /** プロジェクト解析中に全面表示する GIF ローディングオーバーレイ (glass pane)。 */
    private final LoadingGlassPane loadingOverlay = new LoadingGlassPane();
    private JMenuItem cancelLoadingItem;
    private ButtonGroup diagramGroup;
    private java.util.EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    /** ツールバー上の「図種切替」トグルボタン。メニュー側ラジオと選択状態を同期する。 */
    private java.util.EnumMap<DiagramKind, JToggleButton> diagramToggles;
    private ButtonGroup themeGroup;
    private java.util.Map<String, JRadioButtonMenuItem> themeItems;

    /** タブマネージャ (すべての図を対等なタブとして管理)。 */
    private DiagramTabPane tabPane;
    /** 右側のフラットタブバー (動的タブ / Manifest / Impact / References / Func Diff / Functions)。 */
    private JTabbedPane mainTabs;

    /** アクティブタブのミラー兼スクラッチ状態 (エクスポート・スコープ/フィルタダイアログが参照)。 */
    private final DiagramState state = new DiagramState();
    /** 図のエクスポート (保存ダイアログ・クリップボード) を担う補助。 */
    private final ExportController exportController = new ExportController(this, state, status);

    DiagramKind currentKind = DiagramKind.CLASS;
    /** 現在ロード中のプロジェクトルート。null なら未ロード。 */
    private File currentProjectRoot;
    /** 進行中のロード処理のキャンセル用 (null ならロード中ではない)。 */
    private CancelToken loadingCancelToken;

    public UmlMainFrame(File initialProject) {
        super(WINDOW_TITLE);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowState();
                dispose();
            }
        });

        wirePanelListeners();
        buildMenuBar();
        buildCenterTabs();
        buildToolBar();
        controller = createDiagramController();
        // タブにフォーカスが移ったら、ツリーハイライト・図種ミラー・ツールバー反映を連動させる。
        tabPane.setOnTabFocused(info -> controller.onTabFocused(info));
        add(buildStatusBar(), BorderLayout.SOUTH);
        setGlassPane(loadingOverlay); // 解析中オーバーレイ (初期は非表示)
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

        loadProgress.setStringPainted(true);
        loadProgress.setVisible(false);
        loadProgress.setPreferredSize(new Dimension(200, 16));
    }

    /** メニューバーを構築して各メニュー項目フィールドへ反映する。 */
    private void buildMenuBar() {
        MenuBarBuilder.Callbacks mcb = new MenuBarBuilder.Callbacks();
        mcb.chooseProject = this::chooseProject;
        mcb.chooseAndExport = this::chooseAndExport;
        mcb.exportClassDiagramsPerFolder = this::exportClassDiagramsPerFolder;
        mcb.exportFunctionList = this::exportFunctionList;
        mcb.exportMemberList = this::exportMemberList;
        mcb.refreshDiagram = this::refreshDiagram;
        mcb.cancelLoading = () -> {
            if (loadingCancelToken != null) {
                loadingCancelToken.cancel();
                status.setText("Cancelling...");
            }
        };
        mcb.exitApp = () -> { saveWindowState(); dispose(); };
        mcb.loadProject = this::loadProject;
        mcb.openEntitySearch = () -> controller.openEntitySearch();
        mcb.pickSequenceEntry = () -> controller.pickSequenceEntry();
        mcb.openParticipantFilterDialog = () -> controller.openParticipantFilterDialog();
        mcb.clearSequenceParticipants = () -> {
            if (!state.sequenceHiddenParticipants.isEmpty()) {
                state.sequenceHiddenParticipants.clear();
                status.setText("Cleared sequence participant filter.");
                refreshDiagram();
            }
        };
        mcb.pickActivityEntry = () -> controller.pickActivityEntry();
        mcb.pickLayoutFile = () -> controller.pickLayoutFile();
        mcb.pickNavigationGraph = () -> controller.pickNavigationGraph();
        mcb.applyPreset = this::applyPreset;
        mcb.openScopeDialog = () -> controller.openScopeDialog();
        mcb.clearScope = () -> { state.currentScope = null; refreshDiagram(); };
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
        MenuBarBuilder.Result menuResult =
                new MenuBarBuilder(DiagramKind.CLASS, MENU_MASK, mcb, this).build();
        cancelLoadingItem = menuResult.cancelLoadingItem;
        diagramItems = menuResult.diagramItems;
        diagramGroup = menuResult.diagramGroup;
        themeItems = menuResult.themeItems;
        themeGroup = menuResult.themeGroup;
        setJMenuBar(menuResult.menuBar);
    }

    /** 中央のツリー + タブ (動的ダイアグラムタブ + 末尾の固定ユーティリティタブ) を構築する。 */
    private void buildCenterTabs() {
        // 右側: VS Code 風のフラットタブバー
        // [動的ダイアグラムタブ…] [Manifest] [Impact] [References] [Func Diff] [Functions] [Members]
        // 特別扱いの「Home タブ」は持たない。
        mainTabs = new JTabbedPane(JTabbedPane.TOP);
        // タブ多数でも 1 段スクロール表示にし、多段折り返しで図領域が潰れるのを防ぐ。
        mainTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // ユーティリティタブ (固定・末尾 6 本)
        mainTabs.addTab("Manifest", manifestSummaryPanel);
        mainTabs.addTab("Impact", impactPanel);
        mainTabs.addTab("References", referencesPanel);
        mainTabs.addTab("Func Diff", funcDiffPanel);
        mainTabs.addTab("Functions", methodListPanel);
        mainTabs.addTab("Members", memberListPanel);

        // 動的タブマネージャ (fixedSuffix=6 で末尾ユーティリティタブの手前に挿入)
        tabPane = new DiagramTabPane(mainTabs, 6, cache, state,
                status::setText, this::updateZoomLabelFromValue);

        // Functions / Members タブ表示時は一覧を遅延生成
        mainTabs.addChangeListener(ev -> {
            if (mainTabs.getSelectedComponent() == methodListPanel) {
                updateFunctionList();
            } else if (mainTabs.getSelectedComponent() == memberListPanel) {
                updateMemberList();
            }
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                treePanel, mainTabs);
        split.setResizeWeight(0.22);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);
    }

    /** 上部ツールバーを構築する。 */
    private void buildToolBar() {
        ToolBarBuilder.Callbacks tcb = new ToolBarBuilder.Callbacks();
        tcb.chooseProject = this::chooseProject;
        tcb.chooseAndExport = this::chooseAndExport;
        tcb.refreshDiagram = this::refreshDiagram;
        tcb.openEntitySearch = () -> controller.openEntitySearch();
        tcb.selectDiagramKind = k -> controller.selectDiagramKind(k);
        ToolBarBuilder.Result toolBarResult =
                new ToolBarBuilder(DiagramKind.CLASS, tcb).build();
        diagramToggles = toolBarResult.diagramToggles;
        add(toolBarResult.toolBarPanel, BorderLayout.NORTH);
    }

    /** 保存済みウィンドウサイズ・位置を適用して pack する。 */
    private void applyInitialWindowSize() {
        Setting setting = Main.getSetting();
        int w = setting.getWindowWidth() > 0 ? setting.getWindowWidth() : 1200;
        int h = setting.getWindowHeight() > 0 ? setting.getWindowHeight() : 800;
        setPreferredSize(new Dimension(w, h));
        pack();
        restoreWindowLocation(setting);
    }

    /** プロジェクト設定の永続化担当とプロジェクトローダを生成する。 */
    private void initPersistorsAndLoader() {
        settingsPersistor = new ProjectSettingsPersistor(
                Main::getSetting,
                Main::saveSetting,
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
            persistAndRestoreProjectSettings(root);
            updateManifestSummary();
            // VS Code 風: 起動直後に既定タブ (Common 図) を 1 枚開く。
            controller.openDefaultDiagram();
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
        status.setText("Preset: " + p.getDisplayName());
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
                        setting.getClassDiagramCommentMaxLength(),
                        StyleSettingsDialog.ClassDiagramPrefs.parseCsv(
                                setting.getClassDiagramHiddenAnnotations()))
                : StyleSettingsDialog.ClassDiagramPrefs.defaults();
        int curCallGraphDepth = setting != null ? setting.getCallGraphMaxDepth() : 4;
        StyleSettingsDialog.Result edited = StyleSettingsDialog.showDialog(
                this, PlantUmlRenderer.getStyle(), curShow, curStyle,
                curPlacement, curQualify, curClass, curCallGraphDepth);
        if (edited != null) {
            applyStyleSettings(edited);
        }
    }

    /** Style ダイアログ結果 (Style + シーケンス図設定 + クラス図設定 + コールグラフ設定) を反映する。 */
    private void applyStyleSettings(StyleSettingsDialog.Result r) {
        try {
            juml.Setting setting = Main.getSetting();
            if (setting != null) {
                setting.setSequenceShowComments(r.sequenceShowComments);
                setting.setSequenceCommentStyle(r.sequenceCommentStyle.name());
                setting.setSequenceCommentPlacement(r.sequenceCommentPlacement.name());
                setting.setSequenceQualifyMethodNames(r.sequenceQualifyMethodNames);
                setting.setCallGraphMaxDepth(r.callGraphMaxDepth);
                if (r.classDiagram != null) {
                    StyleSettingsDialog.ClassDiagramPrefs cp = r.classDiagram;
                    setting.setClassDiagramShowFields(cp.showFields);
                    setting.setClassDiagramShowMethods(cp.showMethods);
                    setting.setClassDiagramShowAnnotations(cp.showAnnotations);
                    setting.setClassDiagramPublicOnly(cp.publicOnly);
                    setting.setClassDiagramExcludeExternal(cp.excludeExternal);
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
        PreferencesDialog.Result r =
                PreferencesDialog.showDialog(this, curLaf, curRestore, curLang);
        if (r == null) {
            return;
        }
        boolean lafChanged = !r.lookAndFeel.equalsIgnoreCase(curLaf);
        boolean langChanged = !r.language.equalsIgnoreCase(curLang);
        try {
            if (setting != null) {
                setting.setLookAndFeel(r.lookAndFeel);
                setting.setRestoreLastProjectOnStartup(r.restoreLastProjectOnStartup);
                setting.setLanguage(r.language);
                Main.saveSetting();
            }
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
        // 外観・言語のいずれも、既に生成済みの UI へは遡及しないため再起動で反映する。
        if (lafChanged || langChanged) {
            JOptionPane.showMessageDialog(this,
                    juml.util.Messages.get("pref.restartNotice"),
                    juml.util.Messages.get("menubar.settings.preferences"),
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * 現在のプロジェクトの解析キャッシュ (メモリ + ディスク) を破棄し、再解析する。
     * プロジェクト未読込なら何もせず通知のみ。
     */
    private void clearAnalysisCache() {
        File root = cache.getProjectRoot();
        if (root == null) {
            JOptionPane.showMessageDialog(this,
                    "クリアする解析キャッシュがありません (プロジェクト未読込)。",
                    "Clear Analysis Cache", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "現在のプロジェクトの解析キャッシュ (メモリ + ディスク) を破棄して\n"
                        + "再解析しますか?",
                "Clear Analysis Cache",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        cache.invalidate();
        status.setText("Cleared analysis cache. Reloading...");
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

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(status, BorderLayout.CENTER);
        JPanel east = new JPanel(new BorderLayout(8, 0));
        east.add(loadProgress, BorderLayout.WEST);
        east.add(zoomLabel, BorderLayout.EAST);
        bar.add(east, BorderLayout.EAST);
        return bar;
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
     * 現在ロード中のプロジェクトを再帰的に走査し、ソースファイルを含むフォルダごとに
     * 1 枚ずつ PlantUML クラス図 ({@code classes.puml} + {@code classes.svg}) を
     * 出力する。実処理は {@link PerFolderExporter} に委譲。
     */
    private void exportClassDiagramsPerFolder() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File projectRoot = cache.getProjectRoot();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "Loaded project root is unavailable.",
                    "No project", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PerFolderExporter.choose(this, projectRoot,
                cache.getClasses(), cache.getIndex(),
                loadProgress, status);
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

    /** 全クラスの関数使用マップ (署名・利用側・実行条件・リスナー) を指定形式で構築する。 */
    private String buildFunctionListReport(
            juml.core.formats.uml.MethodUsageReport.Format format) {
        java.util.List<juml.core.formats.android.actions.UiActionEntry> actions =
                java.util.Collections.emptyList();
        if (currentProjectRoot != null) {
            try {
                actions = new juml.core.formats.android.actions.UiActionScanner()
                        .analyzeProject(currentProjectRoot);
            } catch (java.io.IOException ex) {
                status.setText("UI action scan failed: " + ex.getMessage());
            }
        }
        return juml.core.formats.uml.MethodUsageReport.render(
                cache.getClasses(), refIndexCache.get(), actions, format);
    }

    /** 「Functions」タブの内容をプロジェクトの現状で更新する (タブ選択時に遅延生成)。 */
    private void updateFunctionList() {
        methodListPanel.setText(cache.isLoaded()
                ? buildFunctionListReport(
                        juml.core.formats.uml.MethodUsageReport.Format.TABLE)
                : "");
    }

    /** 「Members」タブに全クラスの純粋なメンバー一覧を表示する (タブ選択時に遅延生成)。 */
    private void updateMemberList() {
        memberListPanel.setText(cache.isLoaded()
                ? juml.core.formats.uml.ClassMemberReport.render(cache.getClasses())
                : "");
    }

    /** 関数使用マップをファイルに保存する (File メニュー、Markdown テーブル / CSV を拡張子で選択)。 */
    private void exportFunctionList() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this, "Open a project first.",
                    "Functions", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        exportController.exportFunctionList(
                buildFunctionListReport(
                        juml.core.formats.uml.MethodUsageReport.Format.TABLE),
                buildFunctionListReport(
                        juml.core.formats.uml.MethodUsageReport.Format.CSV),
                "Save function list (Markdown table or CSV)");
    }

    /** 全クラスのメンバー解析結果を Excel (.xlsx) ワークブックとして保存する (File メニュー)。 */
    private void exportMemberList() {
        if (!cache.isLoaded()) {
            JOptionPane.showMessageDialog(this, "Open a project first.",
                    "Members", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        exportController.exportMemberWorkbook(
                cache.getClasses(), "Save members workbook (Excel .xlsx)");
    }

    /** F5 / Refresh / フィルタ変更後にアクティブタブを再描画する。 */
    private void refreshDiagram() {
        if (controller != null) {
            controller.applyStateToActiveTab();
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
                    "Graphviz (dot) を検出しました。\n開いている図を再描画しました。",
                    "Graphviz", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Graphviz (dot) が見つかりませんでした。\n"
                        + "dot 実行ファイルの場所を指定しますか?\n\n"
                        + "未インストールの場合は https://graphviz.org/download/ から\n"
                        + "インストールしてください。",
                "Graphviz が見つかりません",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("dot 実行ファイルを選択");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dot = fc.getSelectedFile();
        if (GraphvizLocator.useDotBinary(dot)) {
            tabPane.rerenderAllTabs();
            JOptionPane.showMessageDialog(this,
                    "Graphviz (dot) を有効化しました。\n開いている図を再描画しました。",
                    "Graphviz", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "選択したファイルは実行可能な dot ではありません:\n"
                            + dot.getAbsolutePath(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseAndExport() {
        exportController.chooseAndExport();
    }

    // --- 状態管理 -------------------------------------------------------------

    /** アクティブタブのズーム率 (1.0 = 100%) をステータスバーのズームラベルへ反映する。 */
    private void updateZoomLabelFromValue(double zoom) {
        int pct = (int) Math.round(zoom * 100);
        zoomLabel.setText(pct + "%");
    }

    private void restoreWindowLocation(Setting setting) {
        if (setting.getWindowX() >= 0 && setting.getWindowY() >= 0) {
            setLocation(setting.getWindowX(), setting.getWindowY());
        } else {
            setLocationRelativeTo(null);
        }
    }

    private void saveWindowState() {
        try {
            Setting setting = Main.getSetting();
            setting.setWindowX(getX());
            setting.setWindowY(getY());
            setting.setWindowWidth(getWidth());
            setting.setWindowHeight(getHeight());
            Main.saveSetting();
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート
        }
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
