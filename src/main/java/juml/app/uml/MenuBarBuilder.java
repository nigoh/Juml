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
    }

    /** {@link #build()} の戻り値。 */
    public static final class Result {
        public final JMenuBar menuBar;
        public final JMenuItem cancelLoadingItem;
        public final EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
        public final Map<String, JRadioButtonMenuItem> themeItems;
        public final ButtonGroup diagramGroup;
        public final ButtonGroup themeGroup;

        Result(JMenuBar menuBar,
               JMenuItem cancelLoadingItem,
               EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems,
               Map<String, JRadioButtonMenuItem> themeItems,
               ButtonGroup diagramGroup,
               ButtonGroup themeGroup) {
            this.menuBar = menuBar;
            this.cancelLoadingItem = cancelLoadingItem;
            this.diagramItems = diagramItems;
            this.themeItems = themeItems;
            this.diagramGroup = diagramGroup;
            this.themeGroup = themeGroup;
        }
    }

    private final DiagramKind initialKind;
    private final int menuMask;
    private final Callbacks cb;
    private final Supplier<java.util.List<juml.ProjectRecord>> recentProjects;
    private final JOptionPane parentForDialogs;
    private final java.awt.Frame parentFrame;

    public MenuBarBuilder(DiagramKind initialKind, int menuMask, Callbacks cb,
                          java.awt.Frame parentFrame) {
        this.initialKind = initialKind;
        this.menuMask = menuMask;
        this.cb = cb;
        this.parentFrame = parentFrame;
        this.recentProjects = null;
        this.parentForDialogs = null;
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
                diagramGroup, themeGroup);
    }

    private JMenu buildFileMenu(JMenuItem cancelLoadingItem) {
        JMenu m = new JMenu(Messages.get("menubar.file"));
        m.setMnemonic(KeyEvent.VK_F);
        JMenuItem open = new JMenuItem(Messages.get("menubar.file.open"));
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, menuMask));
        open.addActionListener(e -> cb.chooseProject.run());
        JMenu recent = new JMenu(Messages.get("menubar.file.openRecent"));
        m.addMenuListener(new javax.swing.event.MenuListener() {
            @Override public void menuSelected(javax.swing.event.MenuEvent e) {
                rebuildRecentMenu(recent);
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
        JMenuItem save = new JMenuItem(Messages.get("menubar.file.saveAs"));
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask));
        save.addActionListener(e -> cb.chooseAndExport.run());
        JMenuItem perFolder = new JMenuItem(Messages.get("menubar.file.exportPerFolder"));
        perFolder.addActionListener(e -> cb.exportClassDiagramsPerFolder.run());
        JMenuItem functionList = new JMenuItem(Messages.get("menubar.file.exportFunctionList"));
        functionList.addActionListener(e -> cb.exportFunctionList.run());
        JMenuItem memberList = new JMenuItem(Messages.get("menubar.file.exportMembers"));
        memberList.addActionListener(e -> cb.exportMemberList.run());
        JMenuItem refresh = new JMenuItem(Messages.get("menubar.file.refresh"));
        refresh.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refresh.addActionListener(e -> cb.refreshDiagram.run());
        JMenuItem exit = new JMenuItem(Messages.get("menubar.file.exit"));
        exit.addActionListener(e -> cb.exitApp.run());
        m.add(open);
        m.add(recent);
        m.add(save);
        m.add(perFolder);
        m.add(functionList);
        m.add(memberList);
        m.addSeparator();
        m.add(refresh);
        m.add(cancelLoadingItem);
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
        JMenuItem search = new JMenuItem(Messages.get("menubar.diagram.search"));
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
                mi.setAccelerator(KeyStroke.getKeyStroke(keyCode, menuMask));
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
        zoomIn.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, menuMask));
        zoomIn.addActionListener(e -> cb.zoomIn.run());
        JMenuItem zoomOut = new JMenuItem(Messages.get("menubar.view.zoomOut"));
        zoomOut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, menuMask));
        zoomOut.addActionListener(e -> cb.zoomOut.run());
        JMenuItem zoomReset = new JMenuItem(Messages.get("menubar.view.zoomReset"));
        zoomReset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, menuMask));
        zoomReset.addActionListener(e -> cb.zoomReset.run());
        JMenuItem zoomFit = new JMenuItem(Messages.get("menubar.view.zoomFit"));
        zoomFit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask));
        zoomFit.addActionListener(e -> cb.zoomToFit.run());
        m.add(zoomIn);
        m.add(zoomOut);
        m.add(zoomReset);
        m.add(zoomFit);
        return m;
    }

    private JMenu buildStyleMenu(Map<String, JRadioButtonMenuItem> themeItems,
                                 ButtonGroup themeGroup) {
        JMenu m = new JMenu(Messages.get("menubar.style"));
        m.setMnemonic(KeyEvent.VK_S);
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
        m.addSeparator();
        JMenuItem advanced = new JMenuItem(Messages.get("menubar.style.settings"));
        advanced.addActionListener(e -> cb.openStyleSettings.run());
        m.add(advanced);
        return m;
    }

    private JMenu buildSettingsMenu() {
        JMenu m = new JMenu(Messages.get("menubar.settings"));
        m.setMnemonic(KeyEvent.VK_T);
        JMenuItem preferences = new JMenuItem(Messages.get("menubar.settings.preferences"));
        preferences.setToolTipText(Messages.get("menubar.settings.preferences.tooltip"));
        preferences.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, menuMask));
        preferences.addActionListener(e -> cb.openPreferences.run());
        m.add(preferences);
        m.addSeparator();
        JMenuItem styleSettings = new JMenuItem(Messages.get("menubar.settings.styleSettings"));
        styleSettings.setToolTipText(Messages.get("menubar.settings.styleSettings.tooltip"));
        styleSettings.addActionListener(e -> cb.openStyleSettings.run());
        m.add(styleSettings);
        JMenuItem enableGraphviz = new JMenuItem(Messages.get("menubar.settings.enableGraphviz"));
        enableGraphviz.setToolTipText(Messages.get("menubar.settings.enableGraphviz.tooltip"));
        enableGraphviz.addActionListener(e -> cb.enableGraphviz.run());
        m.add(enableGraphviz);
        m.addSeparator();
        JMenuItem clearCache = new JMenuItem(Messages.get("menubar.settings.clearCache"));
        clearCache.setToolTipText(Messages.get("menubar.settings.clearCache.tooltip"));
        clearCache.addActionListener(e -> cb.clearAnalysisCache.run());
        m.add(clearCache);
        return m;
    }

    private JMenu buildHelpMenu() {
        JMenu m = new JMenu(Messages.get("menubar.help"));
        m.setMnemonic(KeyEvent.VK_H);
        JMenuItem usage = new JMenuItem(Messages.get("menubar.help.usage"));
        usage.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        usage.addActionListener(e -> showUsageDialog());
        JMenuItem about = new JMenuItem(Messages.get("menubar.help.about"));
        about.addActionListener(e -> JOptionPane.showMessageDialog(parentFrame,
                Messages.get("menubar.help.about.message"),
                Messages.get("menubar.help.about.title"),
                JOptionPane.INFORMATION_MESSAGE));
        m.add(usage);
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
                        + "■ 左ペインのツリー操作\n"
                        + "  - クラスやメソッドを選択すると、対応する図に絞り込み表示します。\n"
                        + "  - パッケージ / モジュール選択でスコープを切り替えられます。\n"
                        + "\n"
                        + "■ プレビュー (右ペイン) の操作\n"
                        + "  - 左ドラッグ / 中ボタンドラッグ: パン (画面移動)\n"
                        + "  - " + mod + " + マウスホイール: ポインタ位置を基点にズームイン/アウト\n"
                        + "  - マウスホイールのみ: 縦スクロール\n"
                        + "  - View > Zoom In / Out / 100% / Fit (" + mod + "+= / " + mod
                        + "+- / " + mod + "+0 / " + mod + "+F)\n"
                        + "\n"
                        + "■ ドリルダウン (図中のクリック可能要素)\n"
                        + "  - 図中のクラス名やメソッド名のうち、人差し指 (👆) アイコンが\n"
                        + "    表示される箇所はクリックで詳細表示に切り替わります。\n"
                        + "    ※ アイコンが出ない箇所はクリック対象ではありません。\n"
                        + "  - 右クリックでポップアップメニュー (関連図への遷移など)。\n"
                        + "\n"
                        + "■ 絞り込み / 検索\n"
                        + "  - Diagram > Search Entities... (" + mod + "+Shift+F): クラス/メソッドを検索。\n"
                        + "  - Diagram > Scope...: 表示範囲 (パッケージ等) を細かく指定。\n"
                        + "  - Diagram > Filter Sequence Participants...: シーケンス図の登場人物を隠す。\n"
                        + "  - Diagram > Preset > Minimal / Balanced / Detailed\n"
                        + "    (" + mod + "+1 / " + mod + "+2 / " + mod
                        + "+3): クラス図の表示密度を切替。\n"
                        + "\n"
                        + "■ 再描画 / キャンセル\n"
                        + "  - File > Refresh (F5): 現在の図を再生成。\n"
                        + "  - File > Cancel Loading: 重い解析を途中で中断。\n"
                        + "\n"
                        + "■ エクスポート (画像保存)\n"
                        + "  - File > Save Diagram As... (" + mod + "+S): PNG / SVG / PUML で保存。\n"
                        + "  - File > Export Class Diagrams Per Folder...: フォルダ単位で一括出力。\n"
                        + "\n"
                        + "■ スタイル (見た目)\n"
                        + "  - Style メニュー: テーマ切替・詳細スタイル設定。\n"
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
                "Usage — Juml UML", JOptionPane.INFORMATION_MESSAGE);
    }
}
