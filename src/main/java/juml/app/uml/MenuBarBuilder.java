// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.Messages;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ウィンドウのメニューバーを構築するクラス。
 *
 * <p>UI の構築のみ担当し、状態変更は行わない。
 * アクションはすべて {@link Callbacks} 経由で呼び出し側に委譲する。</p>
 */
public final class MenuBarBuilder {

    /** メニューアクションのコールバック群。 */
    public static final class Callbacks {
        public Runnable chooseProject;
        /** .jar/.aar/.class (コンパイル済みバイトコード) を解析対象として開く。 */
        public Runnable openArchive;
        public Runnable chooseAndExport;
        public Runnable exportClassDiagramsPerFolder;
        public Runnable exportFunctionList;
        public Runnable exportMemberList;
        public Runnable refreshDiagram;
        /** Cancel Loading アイテムのアクション。loadingCancelToken がある場合に cancel を呼ぶ。 */
        public Runnable cancelLoading;
        public Runnable exitApp;
        public Consumer<File> loadProject;
        public Runnable openEntitySearch;
        /** Ctrl+F / Diagram &gt; Find in Diagram: 表示中の図内をインクリメンタル検索する。 */
        public Runnable findInDiagram;
        public Runnable pickSequenceEntry;
        public Runnable openParticipantFilterDialog;
        public Runnable clearSequenceParticipants;
        public Runnable pickActivityEntry;
        public Runnable pickLayoutFile;
        public Runnable pickNavigationGraph;
        public Consumer<DiagramPreset> applyPreset;
        public Runnable openScopeDialog;
        public Runnable clearScope;
        /** Graphviz dot を検出/指定して有効化し、図を再描画するアクション。 */
        public Runnable enableGraphviz;
        /** Diagram メニューのラジオ選択時のアクション (currentKind 更新 + refresh)。 */
        public Consumer<DiagramKind> selectDiagramKindFromMenu;
        /** ツールバートグルと Diagram メニューを同期するコールバック。 */
        public Consumer<DiagramKind> syncDiagramToggle;
        public Consumer<String> applyTheme;
        public Runnable openStyleSettings;
        /** Settings &gt; Preferences... のアクション (アプリ全体設定ダイアログ)。 */
        public Runnable openPreferences;
        /** Settings &gt; Clear Analysis Cache のアクション。 */
        public Runnable clearAnalysisCache;
        /** ズーム操作コールバック。 */
        public Runnable zoomIn;
        public Runnable zoomOut;
        public Runnable zoomReset;
        public Runnable zoomToFit;
        /** Ctrl+W / File &gt; Close Tab: アクティブな動的タブを閉じる。 */
        public Runnable closeActiveTab;
        /** Ctrl+Shift+T / File &gt; Reopen Closed Tab: 直近に閉じたタブを再オープン。 */
        public Runnable reopenClosedTab;
        /** Ctrl+Shift+P / View &gt; Command Palette: コマンドパレットを開く。 */
        public Runnable openCommandPalette;
        /** Ctrl+B / View &gt; Toggle Sidebar: 左ツリーペインの折りたたみ。 */
        public Runnable toggleSidebar;
        /** アクティブタブの下部 Source ビューを前面に出す。 */
        public Runnable openSourceForActiveTab;
        /** アクティブタブの図に Markdown 付箋メモを追加する。 */
        public Runnable addNoteToActiveTab;
        /** アクティブタブの付箋一覧サイドパネルを開閉する。 */
        public Runnable toggleNotesPanel;
        /** Ctrl+Shift+E / View &gt; Focus Explorer: ツリーペインへフォーカスを移す。 */
        public Runnable focusExplorer;
        /** Help &gt; Error Log: アプリのエラーログビューアを開く。 */
        public Runnable openLogViewer;
    }

    /** {@link #build()} の戻り値。 */
    public static final class Result {
        public final JMenuBar menuBar;
        public final JMenuItem cancelLoadingItem;
        public final EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
        public final Map<String, JRadioButtonMenuItem> themeItems;
        public final ButtonGroup diagramGroup;
        public final ButtonGroup themeGroup;
        /** プロジェクト未ロード時に無効化するエクスポート系メニュー項目。 */
        public final java.util.List<JMenuItem> exportItems;

        Result(JMenuBar menuBar,
               JMenuItem cancelLoadingItem,
               EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems,
               Map<String, JRadioButtonMenuItem> themeItems,
               ButtonGroup diagramGroup,
               ButtonGroup themeGroup,
               java.util.List<JMenuItem> exportItems) {
            this.menuBar = menuBar;
            this.cancelLoadingItem = cancelLoadingItem;
            this.diagramItems = diagramItems;
            this.themeItems = themeItems;
            this.diagramGroup = diagramGroup;
            this.themeGroup = themeGroup;
            this.exportItems = exportItems;
        }
    }

    private final DiagramKind initialKind;
    private final int menuMask;
    private final Callbacks cb;
    private final Supplier<java.util.List<juml.ProjectRecord>> recentProjects;
    private final JOptionPane parentForDialogs;
    private final java.awt.Frame parentFrame;
    private final java.util.List<JMenuItem> exportItems = new java.util.ArrayList<>();

    public MenuBarBuilder(DiagramKind initialKind, int menuMask, Callbacks cb,
                          java.awt.Frame parentFrame) {
        this.initialKind = initialKind;
        this.menuMask = menuMask;
        this.cb = cb;
        this.parentFrame = parentFrame;
        this.recentProjects = null;
        this.parentForDialogs = null;
    }

    /** プラットフォーム標準のメニューショートカット修飾キー (ヘッドレス時は Ctrl)。 */
    public static int menuShortcutMask() {
        try {
            return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException ex) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    /** メニューバーを構築して {@link Result} を返す。EDT から呼ぶこと。 */
    public Result build() {
        JMenuItem cancelLoadingItem = new JMenuItem(Messages.get("menubar.file.cancelLoading"));
        cancelLoadingItem.addActionListener(e -> cb.cancelLoading.run());
        cancelLoadingItem.setEnabled(false);

        EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems =
                new EnumMap<>(DiagramKind.class);
        ButtonGroup diagramGroup = new ButtonGroup();
        Map<String, JRadioButtonMenuItem> themeItems = new LinkedHashMap<>();
        ButtonGroup themeGroup = new ButtonGroup();

        JMenuBar bar = new JMenuBar();
        bar.add(buildFileMenu(cancelLoadingItem));
        bar.add(buildDiagramMenu(diagramItems, diagramGroup));
        bar.add(buildViewMenu());
        bar.add(buildStyleMenu(themeItems, themeGroup));
        bar.add(buildSettingsMenu());
        bar.add(buildHelpMenu());

        return new Result(bar, cancelLoadingItem, diagramItems, themeItems,
                diagramGroup, themeGroup,
                java.util.Collections.unmodifiableList(exportItems));
    }

    private JMenu buildFileMenu(JMenuItem cancelLoadingItem) {
        JMenu m = new JMenu(Messages.get("menubar.file"));
        m.setMnemonic(KeyEvent.VK_F);
        JMenuItem open = new JMenuItem(Messages.get("menubar.file.open"));
        open.setMnemonic(KeyEvent.VK_O);
        open.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.FOLDER_OPEN));
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
        open.addActionListener(e -> cb.chooseProject.run());
        JMenuItem openArchive = new JMenuItem(Messages.get("menubar.file.openArchive"));
        openArchive.setMnemonic(KeyEvent.VK_J);
        openArchive.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.ARCHIVE));
        openArchive.addActionListener(e -> cb.openArchive.run());
        JMenu recent = new JMenu(Messages.get("menubar.file.openRecent"));
        m.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                rebuildRecentMenu(recent);
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
        JMenuItem save = new JMenuItem(Messages.get("menubar.file.saveAs"));
        save.setMnemonic(KeyEvent.VK_S);
        save.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SAVE));
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                menuMask | InputEvent.SHIFT_DOWN_MASK));
        save.addActionListener(e -> cb.chooseAndExport.run());
        save.setEnabled(false);
        JMenuItem perFolder = new JMenuItem(Messages.get("menubar.file.exportPerFolder"));
        perFolder.setMnemonic(KeyEvent.VK_P);
        perFolder.addActionListener(e -> cb.exportClassDiagramsPerFolder.run());
        perFolder.setEnabled(false);
        JMenuItem functionList = new JMenuItem(Messages.get("menubar.file.exportFunctionList"));
        functionList.setMnemonic(KeyEvent.VK_F);
        functionList.addActionListener(e -> cb.exportFunctionList.run());
        functionList.setEnabled(false);
        JMenuItem memberList = new JMenuItem(Messages.get("menubar.file.exportMembers"));
        memberList.setMnemonic(KeyEvent.VK_M);
        memberList.addActionListener(e -> cb.exportMemberList.run());
        memberList.setEnabled(false);
        exportItems.addAll(java.util.List.of(save, perFolder, functionList, memberList));
        JMenuItem refresh = new JMenuItem(Messages.get("menubar.file.refresh"));
        refresh.setMnemonic(KeyEvent.VK_R);
        refresh.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.REFRESH));
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refresh.addActionListener(e -> cb.refreshDiagram.run());
        JMenuItem closeTab = new JMenuItem(Messages.get("menubar.file.closeTab"));
        closeTab.setMnemonic(KeyEvent.VK_C);
        closeTab.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.CLOSE));
        closeTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask));
        closeTab.addActionListener(e -> cb.closeActiveTab.run());
        JMenuItem reopenTab = new JMenuItem(Messages.get("menubar.file.reopenTab"));
        reopenTab.setMnemonic(KeyEvent.VK_T);
        reopenTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T,
                menuMask | InputEvent.SHIFT_DOWN_MASK));
        reopenTab.addActionListener(e -> cb.reopenClosedTab.run());
        JMenuItem exit = new JMenuItem(Messages.get("menubar.file.exit"));
        exit.setMnemonic(KeyEvent.VK_X);
        exit.addActionListener(e -> cb.exitApp.run());
        m.add(open);
        m.add(openArchive);
        m.add(recent);
        m.addSeparator();
        m.add(save);
        m.add(perFolder);
        m.add(functionList);
        m.add(memberList);
        m.addSeparator();
        m.add(refresh);
        m.add(cancelLoadingItem);
        m.addSeparator();
        m.add(closeTab);
        m.add(reopenTab);
        m.addSeparator();
        m.add(exit);
        return m;
    }

    private void rebuildRecentMenu(JMenu recent) {
        recent.removeAll();
        java.util.List<juml.ProjectRecord> records;
        try {
            records = juml.ProjectRepository.getInstance().listRecent(10);
        } catch (RuntimeException ex) {
            records = java.util.Collections.emptyList();
        }
        if (records.isEmpty()) {
            JMenuItem none = new JMenuItem(Messages.get("menubar.file.recentNone"));
            none.setEnabled(false);
            recent.add(none);
            return;
        }
        for (juml.ProjectRecord rec : records) {
            File root = rec.root();
            JMenuItem item = new JMenuItem(rec.getName() + "  — " + rec.getPath());
            item.setEnabled(root.isDirectory());
            item.addActionListener(e -> cb.loadProject.accept(root));
            recent.add(item);
        }
    }

    private JMenu buildDiagramMenu(EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems,
                                   ButtonGroup diagramGroup) {
        JMenu m = new JMenu(Messages.get("menubar.diagram"));
        m.setMnemonic(KeyEvent.VK_D);
        for (DiagramKind k : DiagramKind.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(k.getDisplayName());
            if (k == initialKind) {
                item.setSelected(true);
            }
            // 最頻使の図種にクイック切替アクセラレータを付与 (Preset の Ctrl+1..3 と衝突しない
            // Ctrl+Shift+1..4 を使う)。Sequence/Activity は従来どおり起点メソッド選択へ誘導する。
            int quickKey = quickSwitchKey(k);
            if (quickKey != KeyEvent.VK_UNDEFINED) {
                item.setAccelerator(KeyStroke.getKeyStroke(quickKey,
                        menuMask | InputEvent.SHIFT_DOWN_MASK));
            }
            item.addActionListener(e -> cb.selectDiagramKindFromMenu.accept(k));
            final DiagramKind kind = k;
            item.addItemListener(ev -> {
                if (((AbstractButton) ev.getSource()).isSelected()) {
                    cb.syncDiagramToggle.accept(kind);
                }
            });
            diagramGroup.add(item);
            diagramItems.put(k, item);
            m.add(item);
        }
        m.addSeparator();
        // Ctrl+F = 表示中の図内をインクリメンタル検索 (ブラウザ風)。
        JMenuItem findInDiagram = new JMenuItem(Messages.get("menubar.diagram.findInDiagram"));
        findInDiagram.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SEARCH));
        findInDiagram.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask));
        findInDiagram.addActionListener(e -> {
            if (cb.findInDiagram != null) {
                cb.findInDiagram.run();
            }
        });
        m.add(findInDiagram);
        // プロジェクト全体のエンティティ横断検索は Ctrl+Shift+F へ。
        JMenuItem search = new JMenuItem(Messages.get("menubar.diagram.search"));
        search.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SEARCH));
        search.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                menuMask | InputEvent.SHIFT_DOWN_MASK));
        search.addActionListener(e -> cb.openEntitySearch.run());
        m.add(search);
        JMenuItem pickEntry = new JMenuItem(Messages.get("menubar.diagram.chooseSequenceEntry"));
        pickEntry.addActionListener(e -> cb.pickSequenceEntry.run());
        m.add(pickEntry);
        JMenuItem filterParticipants =
                new JMenuItem(Messages.get("menubar.diagram.filterParticipants"));
        filterParticipants.addActionListener(e -> cb.openParticipantFilterDialog.run());
        m.add(filterParticipants);
        JMenuItem clearParticipantFilter =
                new JMenuItem(Messages.get("menubar.diagram.clearParticipantFilter"));
        clearParticipantFilter.addActionListener(e -> cb.clearSequenceParticipants.run());
        m.add(clearParticipantFilter);
        JMenuItem pickActivity = new JMenuItem(Messages.get("menubar.diagram.chooseActivity"));
        pickActivity.addActionListener(e -> cb.pickActivityEntry.run());
        m.add(pickActivity);
        JMenuItem pickLayout = new JMenuItem(Messages.get("menubar.diagram.chooseLayout"));
        pickLayout.addActionListener(e -> cb.pickLayoutFile.run());
        m.add(pickLayout);
        JMenuItem pickNavigation = new JMenuItem(Messages.get("menubar.diagram.chooseNavigation"));
        pickNavigation.addActionListener(e -> cb.pickNavigationGraph.run());
        m.add(pickNavigation);
        m.addSeparator();
        m.add(buildPresetSubMenu());
        JMenuItem scope = new JMenuItem(Messages.get("menubar.diagram.scope"));
        scope.addActionListener(e -> cb.openScopeDialog.run());
        m.add(scope);
        JMenuItem clearScope = new JMenuItem(Messages.get("menubar.diagram.clearScope"));
        clearScope.addActionListener(e -> cb.clearScope.run());
        m.add(clearScope);
        return m;
    }

    /** 図種クイック切替アクセラレータのキーコード (未割当ては VK_UNDEFINED)。 */
    private static int quickSwitchKey(DiagramKind k) {
        switch (k) {
            case CLASS:    return KeyEvent.VK_1;
            case PACKAGE:  return KeyEvent.VK_2;
            case SEQUENCE: return KeyEvent.VK_3;
            case ACTIVITY: return KeyEvent.VK_4;
            default:       return KeyEvent.VK_UNDEFINED;
        }
    }

    private JMenu buildPresetSubMenu() {
        JMenu sub = new JMenu(Messages.get("menubar.diagram.preset"));
        sub.setToolTipText(Messages.get("menubar.diagram.preset.tooltip"));
        int seq = 1;
        for (DiagramPreset p : DiagramPreset.values()) {
            if (p == DiagramPreset.CUSTOM) {
                continue;
            }
            JMenuItem mi = new JMenuItem(p.getDisplayName());
            int keyCode;
            switch (seq) {
                case 1: keyCode = KeyEvent.VK_1; break;
                case 2: keyCode = KeyEvent.VK_2; break;
                case 3: keyCode = KeyEvent.VK_3; break;
                default: keyCode = KeyEvent.VK_UNDEFINED;
            }
            if (keyCode != KeyEvent.VK_UNDEFINED) {
                mi.setAccelerator(KeyStroke.getKeyStroke(keyCode,
                        InputEvent.ALT_DOWN_MASK));
            }
            seq++;
            final DiagramPreset preset = p;
            mi.addActionListener(e -> cb.applyPreset.accept(preset));
            sub.add(mi);
        }
        return sub;
    }

    private JMenu buildViewMenu() {
        JMenu m = new JMenu(Messages.get("menubar.view"));
        m.setMnemonic(KeyEvent.VK_V);
        JMenuItem zoomIn = new JMenuItem(Messages.get("menubar.view.zoomIn"));
        zoomIn.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.ZOOM_IN));
        zoomIn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuMask));
        zoomIn.addActionListener(e -> cb.zoomIn.run());
        JMenuItem zoomOut = new JMenuItem(Messages.get("menubar.view.zoomOut"));
        zoomOut.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.ZOOM_OUT));
        zoomOut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuMask));
        zoomOut.addActionListener(e -> cb.zoomOut.run());
        JMenuItem zoomReset = new JMenuItem(Messages.get("menubar.view.zoomReset"));
        zoomReset.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.CENTER_FOCUS));
        zoomReset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, menuMask));
        zoomReset.addActionListener(e -> cb.zoomReset.run());
        JMenuItem zoomFit = new JMenuItem(Messages.get("menubar.view.zoomFit"));
        zoomFit.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.FIT_SCREEN));
        zoomFit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,
                menuMask | InputEvent.SHIFT_DOWN_MASK));
        zoomFit.addActionListener(e -> cb.zoomToFit.run());
        m.add(zoomIn);
        m.add(zoomOut);
        m.add(zoomReset);
        m.add(zoomFit);
        m.addSeparator();
        JMenuItem palette = new JMenuItem(Messages.get("menubar.view.commandPalette"));
        palette.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.TERMINAL));
        palette.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                menuMask | InputEvent.SHIFT_DOWN_MASK));
        palette.addActionListener(e -> cb.openCommandPalette.run());
        m.add(palette);
        JMenuItem toggleSidebar = new JMenuItem(Messages.get("menubar.view.toggleSidebar"));
        toggleSidebar.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SIDEBAR));
        toggleSidebar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, menuMask));
        toggleSidebar.addActionListener(e -> cb.toggleSidebar.run());
        m.add(toggleSidebar);
        if (cb.openSourceForActiveTab != null || cb.addNoteToActiveTab != null) {
            m.addSeparator();
        }
        if (cb.openSourceForActiveTab != null) {
            JMenuItem openSource = new JMenuItem(Messages.get("menubar.view.openSource"));
            openSource.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.CODE));
            openSource.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, menuMask));
            openSource.addActionListener(e -> cb.openSourceForActiveTab.run());
            m.add(openSource);
        }
        if (cb.addNoteToActiveTab != null) {
            JMenuItem addNote = new JMenuItem(Messages.get("menubar.view.addNote"));
            addNote.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.NOTE_ADD));
            addNote.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                    menuMask | InputEvent.SHIFT_DOWN_MASK));
            addNote.addActionListener(e -> cb.addNoteToActiveTab.run());
            m.add(addNote);
        }
        if (cb.toggleNotesPanel != null) {
            JMenuItem notesPanel = new JMenuItem(Messages.get("menubar.view.notesPanel"));
            notesPanel.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.NOTE_ADD));
            notesPanel.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J,
                    menuMask | InputEvent.SHIFT_DOWN_MASK));
            notesPanel.addActionListener(e -> cb.toggleNotesPanel.run());
            m.add(notesPanel);
        }
        if (cb.focusExplorer != null) {
            m.addSeparator();
            JMenuItem explorer = new JMenuItem(Messages.get("menubar.view.focusExplorer"));
            explorer.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SIDEBAR));
            explorer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E,
                    menuMask | InputEvent.SHIFT_DOWN_MASK));
            explorer.addActionListener(e -> cb.focusExplorer.run());
            m.add(explorer);
        }
        return m;
    }

    private JMenu buildStyleMenu(Map<String, JRadioButtonMenuItem> themeItems,
                                 ButtonGroup themeGroup) {
        JMenu m = new JMenu(Messages.get("menubar.style"));
        m.setMnemonic(KeyEvent.VK_T);
        DiagramStyle current = PlantUmlRenderer.getStyle();
        for (String theme : StyleSettingsDialog.THEMES) {
            String label = theme.isEmpty() ? Messages.get("menubar.style.none") : theme;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label);
            if (theme.equals(current.getTheme() == null ? "" : current.getTheme())) {
                item.setSelected(true);
            }
            item.addActionListener(e -> cb.applyTheme.accept(theme));
            themeGroup.add(item);
            themeItems.put(theme, item);
            m.add(item);
        }
        // "Style Settings..." は Settings メニューに一本化したため、ここからは除去する。
        return m;
    }

    private JMenu buildSettingsMenu() {
        JMenu m = new JMenu(Messages.get("menubar.settings"));
        m.setMnemonic(KeyEvent.VK_S);
        JMenuItem preferences = new JMenuItem(Messages.get("menubar.settings.preferences"));
        preferences.setToolTipText(Messages.get("menubar.settings.preferences.tooltip"));
        preferences.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SETTINGS));
        preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask));
        preferences.addActionListener(e -> cb.openPreferences.run());
        m.add(preferences);
        m.addSeparator();
        JMenuItem styleSettings = new JMenuItem(Messages.get("menubar.settings.styleSettings"));
        styleSettings.setToolTipText(Messages.get("menubar.settings.styleSettings.tooltip"));
        styleSettings.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.TUNE));
        styleSettings.addActionListener(e -> cb.openStyleSettings.run());
        m.add(styleSettings);
        JMenuItem enableGraphviz = new JMenuItem(Messages.get("menubar.settings.enableGraphviz"));
        enableGraphviz.setToolTipText(Messages.get("menubar.settings.enableGraphviz.tooltip"));
        enableGraphviz.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.HUB));
        enableGraphviz.addActionListener(e -> cb.enableGraphviz.run());
        m.add(enableGraphviz);
        m.addSeparator();
        JMenuItem clearCache = new JMenuItem(Messages.get("menubar.settings.clearCache"));
        clearCache.setToolTipText(Messages.get("menubar.settings.clearCache.tooltip"));
        clearCache.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.DELETE_SWEEP));
        clearCache.addActionListener(e -> cb.clearAnalysisCache.run());
        m.add(clearCache);
        return m;
    }

    private JMenu buildHelpMenu() {
        JMenu m = new JMenu(Messages.get("menubar.help"));
        m.setMnemonic(KeyEvent.VK_H);
        JMenuItem usage = new JMenuItem(Messages.get("menubar.help.usage"));
        usage.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.HELP));
        usage.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        usage.addActionListener(e -> showUsageDialog());
        JMenuItem about = new JMenuItem(Messages.get("menubar.help.about"));
        about.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.INFO));
        about.addActionListener(e -> JOptionPane.showMessageDialog(parentFrame,
                Messages.get("menubar.help.about.message"),
                Messages.get("menubar.help.about.title"),
                JOptionPane.INFORMATION_MESSAGE));
        m.add(usage);
        if (cb.openLogViewer != null) {
            JMenuItem logViewer = new JMenuItem(Messages.get("menubar.help.logViewer"));
            logViewer.setToolTipText(Messages.get("menubar.help.logViewer.tooltip"));
            logViewer.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.TERMINAL));
            logViewer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L,
                    menuMask | InputEvent.SHIFT_DOWN_MASK));
            logViewer.addActionListener(e -> cb.openLogViewer.run());
            m.add(logViewer);
        }
        m.addSeparator();
        m.add(about);
        return m;
    }

    private void showUsageDialog() {
        String mod = menuMask == InputEvent.META_DOWN_MASK ? "Cmd" : "Ctrl";
        String text =
                "Juml UML — 使い方 (Usage)\n"
                        + "\n"
                        + "■ プロジェクトを開く (Open Project)\n"
                        + "  File > Open Project... (" + mod + "+O)\n"
                        + "    Gradle / Java プロジェクトのルートディレクトリを指定すると、\n"
                        + "    左ペインのツリーにモジュール・パッケージ・クラスが表示されます。\n"
                        + "\n"
                        + "■ 図種を切り替える (Diagram)\n"
                        + "  Diagram メニューから Class / Sequence / Activity / Common / Layout などを選択。\n"
                        + "  ウィンドウ上部のツールバーのトグルボタンでも同じ切替ができます。\n"
                        + "  Common (共通クラス図) は他クラスから参照される回数 (fan-in) が多い\n"
                        + "  「使い回されているクラス」上位 N 件をハイライト表示します。\n"
                        + "  シーケンス図やアクティビティ図は起点メソッドの指定が必要です\n"
                        + "  (Diagram > Choose Sequence Entry... / Choose Activity Method...)。\n"
                        + "\n"
                        + "■ どの図を見ればいい? (図種ガイド)\n"
                        + "  - アプリ全体をまず把握: Component (画面・サービス等の部品全体像)\n"
                        + "                          / Manifest (アプリ構成の一覧)\n"
                        + "  - 画面の流れ (遷移) を知る: Screen Flow・Navigation (画面遷移図)\n"
                        + "  - クラスの構造を見る: Class (構造) / Inheritance (継承) / Package (フォルダ依存)\n"
                        + "  - 処理の流れを追う: Sequence (呼び出し順) / Activity (処理フロー) / Call Graph\n"
                        + "  - 画面の見た目を確認: Layout (部品の入れ子) / Layout Screen (ワイヤーフレーム)\n"
                        + "                        / Layout Render (実際の layout_* 値で実寸描画)\n"
                        + "  - URL から画面を開く設定: Deep Link\n"
                        + "  - 重要・要注意クラスを探す: Common (よく使われるクラス)\n"
                        + "                            / Cycles (循環依存=赤で警告)\n"
                        + "  ※ 各図の用途はツールバーのボタンにマウスを乗せると表示されます。\n"
                        + "\n"
                        + "■ 左ペインのツリー操作\n"
                        + "  - クラスやメソッドを選択すると、対応する図に絞り込み表示します。\n"
                        + "  - パッケージ / モジュール選択でスコープを切り替えられます。\n"
                        + "\n"
                        + "■ プレビュー (右ペイン) の操作\n"
                        + "  - 左ドラッグ / 中ボタンドラッグ: パン (画面移動)\n"
                        + "  - " + mod + " + マウスホイール: ポインタ位置を基点にズームイン/アウト\n"
                        + "  - マウスホイールのみ: 縦スクロール\n"
                        + "  - View > Zoom In / Out / 100% / Fit (" + mod + "+= / " + mod
                        + "+- / " + mod + "+0 / " + mod + "+Shift+0)\n"
                        + "\n"
                        + "■ 付箋メモ (Notes / Markdown)\n"
                        + "  - 図に Markdown の付箋を貼れます: View > 図に付箋メモを追加 ("
                        + mod + "+Shift+N)、\n"
                        + "    または図上で右クリック >「ここに付箋を追加」。\n"
                        + "  - ダブルクリックで編集、ドラッグで移動、右下角でリサイズ、Delete で削除。\n"
                        + "  - 付箋はズームに追従し、.juml/notes.json に保存されます\n"
                        + "    (commit すればチームで共有可)。SVG/PNG エクスポートにも含まれます。\n"
                        + "\n"
                        + "■ ドリルダウン (図中のクリック可能要素)\n"
                        + "  - 図中のクラス名やメソッド名のうち、人差し指 (👆) アイコンが\n"
                        + "    表示される箇所はクリックで詳細表示に切り替わります。\n"
                        + "    ※ アイコンが出ない箇所はクリック対象ではありません。\n"
                        + "  - 右クリックでポップアップメニュー (関連図への遷移など)。\n"
                        + "\n"
                        + "■ 絞り込み / 検索\n"
                        + "  - Diagram > Search Entities... (" + mod + "+F): クラス/メソッドを検索。\n"
                        + "  - Diagram > Scope...: 表示範囲 (パッケージ等) を細かく指定。\n"
                        + "  - Diagram > Filter Sequence Participants...: シーケンス図の登場人物を隠す。\n"
                        + "  - Diagram > Preset > Minimal / Balanced / Detailed\n"
                        + "    (Alt+1 / Alt+2 / Alt+3): クラス図の表示密度を切替。\n"
                        + "\n"
                        + "■ 再描画 / キャンセル\n"
                        + "  - File > Refresh (F5): 現在の図を再生成。\n"
                        + "  - File > Cancel Loading: 重い解析を途中で中断。\n"
                        + "\n"
                        + "■ タブ操作 (Tabs)\n"
                        + "  - File > Close Tab (" + mod + "+W): アクティブなタブを閉じる。\n"
                        + "  - Ctrl+Tab / Ctrl+Shift+Tab・" + mod
                        + "+PageDown / PageUp: タブを巡回。\n"
                        + "\n"
                        + "■ エクスポート (画像保存)\n"
                        + "  - File > Save Diagram As... (" + mod + "+Shift+S): PNG / SVG / PUML で保存。\n"
                        + "  - File > Export Class Diagrams Per Folder...: フォルダ単位で一括出力。\n"
                        + "\n"
                        + "■ スタイル (見た目)\n"
                        + "  - Style メニュー: テーマ切替・詳細スタイル設定。\n"
                        + "\n"
                        + "■ エラーログ (Error Log)\n"
                        + "  - Help > エラーログ... (" + mod + "+Shift+L): アプリのエラー・警告を一覧表示。\n"
                        + "    解析やエクスポートが失敗したときの原因 (スタックトレース) を確認できます。\n"
                        + "    内容は logs/juml.log にも保存され、再起動後も参照できます。\n"
                        + "\n"
                        + "■ ヒント\n"
                        + "  - 右側タブの \"PlantUML Source\" で生成された .puml を確認できます。\n"
                        + "  - Android プロジェクトでは \"Manifest Summary\" タブで概要を確認可能。";

        javax.swing.JTextArea area = new javax.swing.JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, area.getFont().getSize()));
        area.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new java.awt.Dimension(640, 520));
        JOptionPane.showMessageDialog(parentFrame, sp,
                Messages.get("dlg.usage.title"), JOptionPane.INFORMATION_MESSAGE);
    }
}
