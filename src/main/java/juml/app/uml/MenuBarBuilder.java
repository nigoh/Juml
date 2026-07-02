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
        /** File &gt; New UML Diagram: テンプレートから自由編集エディタタブを開く。 */
        public Consumer<PumlTemplate> newUmlDiagram;
        /** File &gt; Open .puml File...: 既存の PlantUML テキストをエディタタブで開く。 */
        public Runnable openPumlFile;
        /** Ctrl+S / File &gt; Save .puml: アクティブなエディタタブを保存する。 */
        public Runnable savePumlTab;
        /** File &gt; Save .puml As...: アクティブなエディタタブを別名保存する。 */
        public Runnable savePumlTabAs;
        /** File &gt; Diff vs Saved: 編集中テキストと保存ファイルの差分を表示する。 */
        public Runnable diffPumlVsSaved;
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
        public Runnable closeOtherTabs;
        public Runnable closeTabsToRight;
        public Runnable closeAllTabs;
        /** 動的タブ枚数 (タブ系メニューの活性制御用)。null なら常時活性のまま。 */
        public java.util.function.IntSupplier dynamicTabCount;
        /** 閉じタブ履歴の件数 (Reopen Closed Tab の活性制御用)。null なら常時活性のまま。 */
        public java.util.function.IntSupplier closedTabHistorySize;
        /** いま動的タブが選択中か (Close Other Tabs の活性制御用)。null なら枚数のみで判定。 */
        public java.util.function.BooleanSupplier dynamicTabFocused;
        /** アクティブタブの右側に図タブがあるか (Close Tabs to the Right の活性制御用)。 */
        public java.util.function.BooleanSupplier hasTabsToRightOfActive;
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
        /** Alt+Left / View &gt; Navigate Back: 直前のタブに戻る。 */
        public Runnable navigateBack;
        /** Alt+Right / View &gt; Navigate Forward: 戻った先からひとつ進む。 */
        public Runnable navigateForward;
        /** Help &gt; Error Log: アプリのエラーログビューアを開く。 */
        public Runnable openLogViewer;
    }

    /**
     * 図種に依存する Diagram メニュー項目 (図種が合っていないと空振りするため、
     * アクティブな図種に応じて有効/無効を切り替える対象)。
     */
    public static final class ContextualMenuItems {
        /** SEQUENCE 図のときだけ意味を持つメニュー項目 (起点選択・参加者フィルタ)。 */
        public final java.util.List<JMenuItem> sequenceOnlyItems;
        /** ACTIVITY 図のときだけ意味を持つメニュー項目 (起点選択)。 */
        public final java.util.List<JMenuItem> activityOnlyItems;
        /** LAYOUT 系図のときだけ意味を持つメニュー項目 (レイアウトファイル選択)。 */
        public final java.util.List<JMenuItem> layoutOnlyItems;
        /** NAVIGATION 図のときだけ意味を持つメニュー項目 (ナビゲーショングラフ選択)。 */
        public final java.util.List<JMenuItem> navigationOnlyItems;

        ContextualMenuItems(java.util.List<JMenuItem> sequenceOnlyItems,
                             java.util.List<JMenuItem> activityOnlyItems,
                             java.util.List<JMenuItem> layoutOnlyItems,
                             java.util.List<JMenuItem> navigationOnlyItems) {
            this.sequenceOnlyItems = sequenceOnlyItems;
            this.activityOnlyItems = activityOnlyItems;
            this.layoutOnlyItems = layoutOnlyItems;
            this.navigationOnlyItems = navigationOnlyItems;
        }
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
        public final ContextualMenuItems contextualItems;

        Result(JMenuBar menuBar,
               JMenuItem cancelLoadingItem,
               EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems,
               Map<String, JRadioButtonMenuItem> themeItems,
               ButtonGroup diagramGroup,
               ButtonGroup themeGroup,
               java.util.List<JMenuItem> exportItems,
               ContextualMenuItems contextualItems) {
            this.menuBar = menuBar;
            this.cancelLoadingItem = cancelLoadingItem;
            this.diagramItems = diagramItems;
            this.themeItems = themeItems;
            this.diagramGroup = diagramGroup;
            this.themeGroup = themeGroup;
            this.exportItems = exportItems;
            this.contextualItems = contextualItems;
        }
    }

    private final DiagramKind initialKind;
    private final int menuMask;
    private final Callbacks cb;
    private final Supplier<java.util.List<juml.ProjectRecord>> recentProjects;
    private final JOptionPane parentForDialogs;
    private final java.awt.Frame parentFrame;
    private final java.util.List<JMenuItem> exportItems = new java.util.ArrayList<>();
    private final java.util.List<JMenuItem> sequenceOnlyItems = new java.util.ArrayList<>();
    private final java.util.List<JMenuItem> activityOnlyItems = new java.util.ArrayList<>();
    private final java.util.List<JMenuItem> layoutOnlyItems = new java.util.ArrayList<>();
    private final java.util.List<JMenuItem> navigationOnlyItems = new java.util.ArrayList<>();

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

        ContextualMenuItems contextualItems = new ContextualMenuItems(
                java.util.Collections.unmodifiableList(sequenceOnlyItems),
                java.util.Collections.unmodifiableList(activityOnlyItems),
                java.util.Collections.unmodifiableList(layoutOnlyItems),
                java.util.Collections.unmodifiableList(navigationOnlyItems));
        return new Result(bar, cancelLoadingItem, diagramItems, themeItems,
                diagramGroup, themeGroup,
                java.util.Collections.unmodifiableList(exportItems),
                contextualItems);
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
        // 自由編集 PlantUML エディタ (新規/開く/保存)。プロジェクト未ロードでも使える。
        JMenu newUml = null;
        if (cb.newUmlDiagram != null) {
            newUml = new JMenu(Messages.get("menubar.file.newUml"));
            newUml.setMnemonic(KeyEvent.VK_N);
            newUml.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.NOTE_ADD));
            for (PumlTemplate t : PumlTemplate.values()) {
                JMenuItem item = new JMenuItem(t.displayName());
                if (t == PumlTemplate.CLASS) {
                    item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, menuMask));
                }
                final PumlTemplate template = t;
                item.addActionListener(e -> cb.newUmlDiagram.accept(template));
                newUml.add(item);
            }
        }
        JMenuItem openPuml = null;
        if (cb.openPumlFile != null) {
            openPuml = new JMenuItem(Messages.get("menubar.file.openPuml"));
            openPuml.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.CODE));
            openPuml.addActionListener(e -> cb.openPumlFile.run());
        }
        JMenuItem savePuml = null;
        if (cb.savePumlTab != null) {
            savePuml = new JMenuItem(Messages.get("menubar.file.savePuml"));
            savePuml.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.SAVE));
            savePuml.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
            savePuml.addActionListener(e -> cb.savePumlTab.run());
        }
        JMenuItem savePumlAs = null;
        if (cb.savePumlTabAs != null) {
            savePumlAs = new JMenuItem(Messages.get("menubar.file.savePumlAs"));
            savePumlAs.addActionListener(e -> cb.savePumlTabAs.run());
        }
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
        if (newUml != null) {
            m.add(newUml);
        }
        m.add(open);
        m.add(openArchive);
        if (openPuml != null) {
            m.add(openPuml);
        }
        m.add(recent);
        m.addSeparator();
        if (savePuml != null) {
            m.add(savePuml);
        }
        if (savePumlAs != null) {
            m.add(savePumlAs);
        }
        if (cb.diffPumlVsSaved != null) {
            JMenuItem diff = new JMenuItem(Messages.get("menubar.file.diffSaved"));
            diff.addActionListener(e -> cb.diffPumlVsSaved.run());
            m.add(diff);
        }
        m.add(save);
        m.add(perFolder);
        m.add(functionList);
        m.add(memberList);
        m.addSeparator();
        m.add(refresh);
        m.add(cancelLoadingItem);
        m.addSeparator();
        m.add(closeTab);
        JMenuItem closeOthers = null;
        if (cb.closeOtherTabs != null) {
            closeOthers = new JMenuItem(Messages.get("menubar.file.closeOthers"));
            closeOthers.addActionListener(e -> cb.closeOtherTabs.run());
            m.add(closeOthers);
        }
        JMenuItem closeRight = null;
        if (cb.closeTabsToRight != null) {
            closeRight = new JMenuItem(Messages.get("menubar.file.closeRight"));
            closeRight.addActionListener(e -> cb.closeTabsToRight.run());
            m.add(closeRight);
        }
        JMenuItem closeAll = null;
        if (cb.closeAllTabs != null) {
            closeAll = new JMenuItem(Messages.get("menubar.file.closeAllTabs"));
            closeAll.addActionListener(e -> cb.closeAllTabs.run());
            m.add(closeAll);
        }
        m.add(reopenTab);
        installTabMenuEnablement(m, closeOthers, closeRight, closeAll, reopenTab);
        m.addSeparator();
        m.add(exit);
        return m;
    }

    /**
     * File メニューが開く直前に、タブ系メニュー項目 (Close Others / Close Right /
     * Close All / Reopen Closed Tab) の有効/無効を現在のタブ状態から評価する。
     * 空振りする操作をグレーアウトし「押しても何も起きない」状態を避ける。
     */
    private void installTabMenuEnablement(JMenu m, JMenuItem closeOthers,
                                          JMenuItem closeRight, JMenuItem closeAll,
                                          JMenuItem reopenTab) {
        m.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                if (cb.dynamicTabCount != null) {
                    int dyn = cb.dynamicTabCount.getAsInt();
                    boolean focused = cb.dynamicTabFocused == null
                            || cb.dynamicTabFocused.getAsBoolean();
                    if (closeOthers != null) {
                        // 動的タブ選択中は「他のタブ」が存在するとき、ユーティリティタブ
                        // 選択中は動的タブ全部が「他のタブ」になるため 1 枚以上で有効。
                        closeOthers.setEnabled(focused ? dyn >= 2 : dyn >= 1);
                    }
                    if (closeRight != null) {
                        closeRight.setEnabled(cb.hasTabsToRightOfActive != null
                                ? cb.hasTabsToRightOfActive.getAsBoolean()
                                : dyn >= 1);
                    }
                    if (closeAll != null) {
                        closeAll.setEnabled(dyn >= 1);
                    }
                }
                if (cb.closedTabHistorySize != null) {
                    reopenTab.setEnabled(cb.closedTabHistorySize.getAsInt() > 0);
                }
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
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
        java.util.Set<Integer> usedMnemonics = new java.util.HashSet<>();
        for (DiagramKind k : DiagramKind.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(k.getDisplayName());
            if (k == initialKind) {
                item.setSelected(true);
            }
            int mnemonic = firstFreeMnemonic(k.getDisplayName(), usedMnemonics);
            if (mnemonic != KeyEvent.VK_UNDEFINED) {
                item.setMnemonic(mnemonic);
                usedMnemonics.add(mnemonic);
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
        pickEntry.setEnabled(false);
        m.add(pickEntry);
        JMenuItem filterParticipants =
                new JMenuItem(Messages.get("menubar.diagram.filterParticipants"));
        filterParticipants.addActionListener(e -> cb.openParticipantFilterDialog.run());
        filterParticipants.setEnabled(false);
        m.add(filterParticipants);
        JMenuItem clearParticipantFilter =
                new JMenuItem(Messages.get("menubar.diagram.clearParticipantFilter"));
        clearParticipantFilter.addActionListener(e -> cb.clearSequenceParticipants.run());
        clearParticipantFilter.setEnabled(false);
        m.add(clearParticipantFilter);
        sequenceOnlyItems.addAll(java.util.List.of(pickEntry, filterParticipants,
                clearParticipantFilter));
        JMenuItem pickActivity = new JMenuItem(Messages.get("menubar.diagram.chooseActivity"));
        pickActivity.addActionListener(e -> cb.pickActivityEntry.run());
        pickActivity.setEnabled(false);
        m.add(pickActivity);
        activityOnlyItems.add(pickActivity);
        JMenuItem pickLayout = new JMenuItem(Messages.get("menubar.diagram.chooseLayout"));
        pickLayout.addActionListener(e -> cb.pickLayoutFile.run());
        pickLayout.setEnabled(false);
        m.add(pickLayout);
        layoutOnlyItems.add(pickLayout);
        JMenuItem pickNavigation = new JMenuItem(Messages.get("menubar.diagram.chooseNavigation"));
        pickNavigation.addActionListener(e -> cb.pickNavigationGraph.run());
        pickNavigation.setEnabled(false);
        m.add(pickNavigation);
        navigationOnlyItems.add(pickNavigation);
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

    /**
     * {@code label} 内の文字から、まだ {@code used} に含まれない先頭の英字を
     * ニーモニックキーコードとして返す (Diagram メニュー内でのみ一意であればよい)。
     * 割り当て可能な文字が無ければ {@code KeyEvent.VK_UNDEFINED}。
     */
    private static int firstFreeMnemonic(String label, java.util.Set<Integer> used) {
        for (int i = 0; i < label.length(); i++) {
            char c = Character.toUpperCase(label.charAt(i));
            if (c < 'A' || c > 'Z') {
                continue;
            }
            int keyCode = KeyEvent.VK_A + (c - 'A');
            if (!used.contains(keyCode)) {
                return keyCode;
            }
        }
        return KeyEvent.VK_UNDEFINED;
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
        if (cb.navigateBack != null || cb.navigateForward != null) {
            m.addSeparator();
            if (cb.navigateBack != null) {
                JMenuItem back = new JMenuItem(Messages.get("menubar.view.navigateBack"));
                back.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.ARROW_BACK));
                back.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,
                        InputEvent.ALT_DOWN_MASK));
                back.addActionListener(e -> cb.navigateBack.run());
                m.add(back);
            }
            if (cb.navigateForward != null) {
                JMenuItem fwd = new JMenuItem(Messages.get("menubar.view.navigateForward"));
                fwd.setIcon(MaterialIcons.menu(MaterialIcons.Glyph.ARROW_FORWARD));
                fwd.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,
                        InputEvent.ALT_DOWN_MASK));
                fwd.addActionListener(e -> cb.navigateForward.run());
                m.add(fwd);
            }
        }
        return m;
    }

    private JMenu buildStyleMenu(Map<String, JRadioButtonMenuItem> themeItems,
                                 ButtonGroup themeGroup) {
        JMenu m = new JMenu(Messages.get("menubar.style"));
        m.setMnemonic(KeyEvent.VK_T);
        DiagramStyle current = PlantUmlRenderer.getStyle();
        java.util.Set<Integer> usedMnemonics = new java.util.HashSet<>();
        for (String theme : StyleSettingsDialog.THEMES) {
            String label = theme.isEmpty() ? Messages.get("menubar.style.none") : theme;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label);
            if (theme.equals(current.getTheme() == null ? "" : current.getTheme())) {
                item.setSelected(true);
            }
            int mnemonic = firstFreeMnemonic(label, usedMnemonics);
            if (mnemonic != KeyEvent.VK_UNDEFINED) {
                item.setMnemonic(mnemonic);
                usedMnemonics.add(mnemonic);
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
        about.addActionListener(e -> showAboutDialog());
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

    private void showAboutDialog() {
        String appVer = MenuBarBuilder.class.getPackage().getImplementationVersion();
        if (appVer == null) {
            appVer = "dev";
        }
        String javaVer = System.getProperty("java.version", "?");
        String body = Messages.get("menubar.help.about.message")
                + "\n\n" + java.text.MessageFormat.format(
                        Messages.get("about.version"), appVer)
                + "\n" + java.text.MessageFormat.format(
                        Messages.get("about.java"), javaVer);
        JOptionPane.showMessageDialog(parentFrame, body,
                Messages.get("menubar.help.about.title"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void showUsageDialog() {
        String mod = menuMask == InputEvent.META_DOWN_MASK ? "Cmd" : "Ctrl";
        String text = java.text.MessageFormat.format(Messages.get("dlg.usage.body"), mod);

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
