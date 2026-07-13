// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * ウィンドウ上部のツールバー（アクション行 + 図種切替行）を構築するクラス。
 *
 * <p>UI の構築のみ担当し、状態変更は行わない。
 * アクションはすべて {@link Callbacks} 経由で呼び出し側に委譲する。</p>
 */
public final class ToolBarBuilder {

    // ── ノード種別ごとに押下可能な図種セット ──────────────────────
    public static final EnumSet<DiagramKind> DIAGRAMS_ALL =
            EnumSet.allOf(DiagramKind.class);
    public static final EnumSet<DiagramKind> DIAGRAMS_MODULE = EnumSet.of(
            DiagramKind.CLASS, DiagramKind.PACKAGE, DiagramKind.MODULE,
            DiagramKind.DEPENDENCY, DiagramKind.COMPONENT, DiagramKind.COMMON,
            DiagramKind.CYCLES);
    public static final EnumSet<DiagramKind> DIAGRAMS_PACKAGE = EnumSet.of(
            DiagramKind.CLASS, DiagramKind.PACKAGE, DiagramKind.COMMON,
            DiagramKind.CYCLES);
    public static final EnumSet<DiagramKind> DIAGRAMS_JAVA_TYPE = EnumSet.of(
            DiagramKind.CLASS, DiagramKind.COMMON);
    public static final EnumSet<DiagramKind> DIAGRAMS_METHOD = EnumSet.of(
            DiagramKind.SEQUENCE, DiagramKind.ACTIVITY, DiagramKind.CALLGRAPH);
    /**
     * 同一レイアウトファイルを題材とする 3 図種。レイアウト図タブ上部の切替バー
     * ({@link DiagramTabPane} の layoutBar) で「レイアウト ⇄ 画面 ⇄ 実寸」を
     * その場で行き来する対象。{@link #LAYOUT} を単一の入口として残し、
     * 画面/実寸 ({@link #LAYOUT_VARIANT_HIDDEN}) はトップメニュー/ツールバーから外す。
     */
    public static final EnumSet<DiagramKind> DIAGRAMS_LAYOUT = EnumSet.of(
            DiagramKind.LAYOUT, DiagramKind.LAYOUT_SCREEN, DiagramKind.LAYOUT_RENDER);
    /**
     * レイアウト図タブ上部の切替バーへ一本化したため、トップメニュー/ツールバーからは
     * 外す図種 (画面/実寸)。入口は {@link #LAYOUT} 1 つに集約し、開いた後は切替バーで
     * 画面/実寸へ切り替える。
     */
    public static final EnumSet<DiagramKind> LAYOUT_VARIANT_HIDDEN = EnumSet.of(
            DiagramKind.LAYOUT_SCREEN, DiagramKind.LAYOUT_RENDER);
    public static final EnumSet<DiagramKind> DIAGRAMS_ANDROID = EnumSet.of(
            DiagramKind.MANIFEST, DiagramKind.COMPONENT, DiagramKind.SCREEN_FLOW,
            DiagramKind.SOONG, DiagramKind.BUILD_NINJA, DiagramKind.INTERMEDIATES);

    /** ツールバーアクションのコールバック群。 */
    public static final class Callbacks {
        public Runnable chooseProject;
        public Runnable chooseAndExport;
        public Runnable refreshDiagram;
        public Runnable openEntitySearch;
        /** 図に Markdown 付箋メモを追加する (null ならボタンを出さない)。 */
        public Runnable addNote;
        public Consumer<DiagramKind> selectDiagramKind;
    }

    /** {@link #build()} の戻り値。 */
    public static final class Result {
        public final JComponent toolBarPanel;
        public final EnumMap<DiagramKind, JToggleButton> diagramToggles;
        public final javax.swing.ButtonGroup diagramToolbarGroup;
        /** 「図に付箋を追加」ボタン (アクティブタブ無し時に無効化する用)。null 可。 */
        public final JButton addNoteButton;
        /** エクスポートボタン (プロジェクト未ロード時に無効化する用)。 */
        public final JButton saveButton;

        Result(JComponent toolBarPanel,
               EnumMap<DiagramKind, JToggleButton> diagramToggles,
               javax.swing.ButtonGroup diagramToolbarGroup,
               JButton addNoteButton,
               JButton saveButton) {
            this.toolBarPanel = toolBarPanel;
            this.diagramToggles = diagramToggles;
            this.diagramToolbarGroup = diagramToolbarGroup;
            this.addNoteButton = addNoteButton;
            this.saveButton = saveButton;
        }
    }

    private final DiagramKind initialKind;
    private final Callbacks cb;
    /** {@link #buildActionToolBar()} が生成する付箋ボタンの参照 (Result へ渡す)。 */
    private JButton addNoteButton;
    private JButton saveButton;

    public ToolBarBuilder(DiagramKind initialKind, Callbacks cb) {
        this.initialKind = initialKind;
        this.cb = cb;
    }

    /** ツールバーコンポーネントを構築して返す。EDT から呼ぶこと。 */
    public Result build() {
        JPanel container = new JPanel(new BorderLayout(0, 0));
        container.add(buildActionToolBar(), BorderLayout.NORTH);
        EnumMap<DiagramKind, JToggleButton> toggles = new EnumMap<>(DiagramKind.class);
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        container.add(buildDiagramKindToolBar(toggles, group), BorderLayout.SOUTH);
        return new Result(container, toggles, group, addNoteButton, saveButton);
    }

    private JToolBar buildActionToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.add(makeButton(Messages.get("toolbar.open"),
                Messages.get("toolbar.open.tip"),
                MaterialIcons.toolbar(MaterialIcons.Glyph.FOLDER_OPEN),
                e -> cb.chooseProject.run()));
        saveButton = makeButton(Messages.get("toolbar.save"),
                Messages.get("toolbar.save.tip"),
                MaterialIcons.toolbar(MaterialIcons.Glyph.SAVE),
                e -> cb.chooseAndExport.run());
        saveButton.setEnabled(false);
        bar.add(saveButton);
        bar.add(makeButton(Messages.get("toolbar.refresh"),
                Messages.get("toolbar.refresh.tip"),
                MaterialIcons.toolbar(MaterialIcons.Glyph.REFRESH),
                e -> cb.refreshDiagram.run()));
        bar.addSeparator();
        bar.add(makeButton(Messages.get("toolbar.search"),
                Messages.get("toolbar.search.tip"),
                MaterialIcons.toolbar(MaterialIcons.Glyph.SEARCH),
                e -> cb.openEntitySearch.run()));
        if (cb.addNote != null) {
            bar.addSeparator();
            addNoteButton = makeButton(Messages.get("menubar.view.addNote"),
                    Messages.get("toolbar.addNote.tip"),
                    MaterialIcons.toolbar(MaterialIcons.Glyph.NOTE_ADD),
                    e -> cb.addNote.run());
            bar.add(addNoteButton);
        }
        return bar;
    }

    /**
     * 図種をカテゴリ単位にまとめた順序。1 行に 20 個並ぶと視認性が悪いため、
     * 関連する図種をグループ化し、グループ間にセパレータを入れて走査しやすくする。
     * 各グループの色はトグルボタン左に付く小アイコンの色になる。
     */
    private static final DiagramCategory[] CATEGORIES = {
        new DiagramCategory(new java.awt.Color(0x1565C0), new DiagramKind[] {
            DiagramKind.CLASS, DiagramKind.PACKAGE, DiagramKind.MODULE,
            DiagramKind.INHERITANCE, DiagramKind.COMMON, DiagramKind.CYCLES }),
        new DiagramCategory(new java.awt.Color(0xE53935), new DiagramKind[] {
            DiagramKind.SEQUENCE, DiagramKind.ACTIVITY, DiagramKind.CALLGRAPH }),
        new DiagramCategory(new java.awt.Color(0x00695C), new DiagramKind[] {
            DiagramKind.COMPONENT, DiagramKind.DEPENDENCY }),
        new DiagramCategory(new java.awt.Color(0x2E7D32), new DiagramKind[] {
            DiagramKind.MANIFEST, DiagramKind.LAYOUT, DiagramKind.LAYOUT_SCREEN,
            DiagramKind.LAYOUT_RENDER, DiagramKind.RESOURCE_LINK, DiagramKind.NAVIGATION,
            DiagramKind.SCREEN_FLOW }),
        new DiagramCategory(new java.awt.Color(0x546E7A), new DiagramKind[] {
            DiagramKind.SOONG, DiagramKind.BUILD_NINJA, DiagramKind.INTERMEDIATES }),
    };

    /** 1 カテゴリ = アイコン色 + 所属図種。 */
    private static final class DiagramCategory {
        final java.awt.Color color;
        final DiagramKind[] kinds;

        DiagramCategory(java.awt.Color color, DiagramKind[] kinds) {
            this.color = color;
            this.kinds = kinds;
        }
    }

    /**
     * トップツールバーには出さない図種。メソッド系 (SEQUENCE/ACTIVITY/CALLGRAPH) は
     * メソッド図タブ上部の切替バー ({@link DiagramTabPane} の kindBar) へ一本化したため、
     * ツールバーからは除外する。{@link #CATEGORIES}（アイコン着色に使う）からは外さず、
     * 描画時にここでフィルタする。
     */
    private static final EnumSet<DiagramKind> TOOLBAR_HIDDEN_KINDS = EnumSet.of(
            DiagramKind.SEQUENCE, DiagramKind.ACTIVITY, DiagramKind.CALLGRAPH,
            DiagramKind.LAYOUT_SCREEN, DiagramKind.LAYOUT_RENDER);

    private JToolBar buildDiagramKindToolBar(EnumMap<DiagramKind, JToggleButton> toggles,
                                             javax.swing.ButtonGroup group) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.add(new JLabel(Messages.get("toolbar.diagramLabel")));
        boolean anyRendered = false;
        for (DiagramCategory cat : CATEGORIES) {
            boolean firstInCategory = true;
            for (DiagramKind k : cat.kinds) {
                if (TOOLBAR_HIDDEN_KINDS.contains(k)) {
                    continue;
                }
                // カテゴリの先頭ボタンを描く直前だけセパレータを入れる (空カテゴリでは入れない)。
                if (firstInCategory && anyRendered) {
                    bar.addSeparator();
                }
                firstInCategory = false;
                anyRendered = true;
                // 図種ごとの Material グリフをカテゴリ色で着色し、ひと目で図種を識別できるようにする。
                javax.swing.Icon icon = MaterialIcons.of(kindGlyph(k), 16, cat.color);
                JToggleButton b = new JToggleButton(toolbarLabel(k), icon);
                b.setToolTipText(k.getDisplayName() + tooltipExtra(k));
                b.getAccessibleContext().setAccessibleName(k.getDisplayName());
                b.setIconTextGap(4);
                if (k == initialKind) {
                    b.setSelected(true);
                }
                final DiagramKind kind = k;
                b.addActionListener(e -> cb.selectDiagramKind.accept(kind));
                group.add(b);
                toggles.put(k, b);
                bar.add(b);
            }
        }
        return bar;
    }

    /**
     * トグルボタン用の短いラベル。i18n リソース {@code diagram.kind.<NAME>.short} を
     * 引き、未定義なら英語の既定 (下の {@code switch}) へフォールバックする。
     */
    public static String toolbarLabel(DiagramKind k) {
        return DiagramKind.localized("diagram.kind." + k.name() + ".short",
                defaultToolbarLabel(k));
    }

    private static String defaultToolbarLabel(DiagramKind k) {
        switch (k) {
            case CLASS: return "Class";
            case PACKAGE: return "Package";
            case SEQUENCE: return "Sequence";
            case ACTIVITY: return "Activity";
            case CALLGRAPH: return "Call Graph";
            case COMMON: return "Common";
            case CYCLES: return "Cycles";
            case COMPONENT: return "Component";
            case DEPENDENCY: return "Dependency";
            case MANIFEST: return "Manifest";
            case LAYOUT: return "Layout";
            case LAYOUT_SCREEN: return "Screen";
            case LAYOUT_RENDER: return "Render";
            case RESOURCE_LINK: return "Resources";
            case NAVIGATION: return "Navigation";
            case MODULE: return "Module";
            case INHERITANCE: return "Inherit";
            case SOONG: return "Soong";
            case BUILD_NINJA: return "Ninja";
            case INTERMEDIATES: return "Intermed.";
            default: return k.getDisplayName();
        }
    }

    /**
     * ツールチップ末尾に付く補足説明。i18n リソース {@code diagram.kind.<NAME>.tip} を
     * 引き、未定義なら英語の既定 (下の {@code switch}) へフォールバックする。
     */
    public static String tooltipExtra(DiagramKind k) {
        return DiagramKind.localized("diagram.kind." + k.name() + ".tip",
                defaultTooltipExtra(k));
    }

    private static String defaultTooltipExtra(DiagramKind k) {
        switch (k) {
            case SEQUENCE:   return " (choose entry from Diagram menu)";
            case ACTIVITY:   return " (choose method from Diagram menu)";
            case CALLGRAPH:  return " — shows which functions are called from entry";
            case LAYOUT:     return " (choose layout file from Diagram menu)";
            case LAYOUT_SCREEN: return " — wireframe screen preview (choose layout file)";
            case LAYOUT_RENDER: return " — actual-size layout render from real layout_* values";
            case RESOURCE_LINK: return " — code ↔ layout/string resource links";
            case NAVIGATION: return " (choose navigation file from Diagram menu)";
            case COMMON:     return " — top-N most referenced classes (fan-in)";
            case CYCLES:     return " — package dependency cycles (red = cyclic)";
            case SOONG:      return " — Android.bp (Soong) module dependencies";
            case BUILD_NINJA: return " — build.ninja target dependencies & rule stats";
            case INTERMEDIATES: return " — .intermediates artifact inventory by module";
            default: return "";
        }
    }

    public static JButton makeButton(String text, String tooltip, ActionListener listener) {
        return makeButton(text, tooltip, null, listener);
    }

    /** アイコン付きツールバーボタンを作る。{@code icon} は null 可。 */
    public static JButton makeButton(String text, String tooltip, javax.swing.Icon icon,
                                     ActionListener listener) {
        JButton b = new JButton(text, icon);
        b.setToolTipText(tooltip);
        if (icon != null) {
            b.setIconTextGap(6);
        }
        b.addActionListener(listener);
        return b;
    }

    /**
     * 図種に割り当てた Material グリフ。図種固有のアイコンでツールバー/タブ/ツリーの
     * 視認性を上げる。カテゴリ色 ({@link #categoryColor(DiagramKind)}) と組み合わせて使う。
     */
    public static MaterialIcons.Glyph kindGlyph(DiagramKind k) {
        switch (k) {
            case CLASS:         return MaterialIcons.Glyph.SCHEMA;
            case PACKAGE:       return MaterialIcons.Glyph.PACKAGE;
            case MODULE:        return MaterialIcons.Glyph.MODULE;
            case INHERITANCE:   return MaterialIcons.Glyph.ACCOUNT_TREE;
            case COMMON:        return MaterialIcons.Glyph.STAR;
            case CYCLES:        return MaterialIcons.Glyph.CYCLE;
            case SEQUENCE:      return MaterialIcons.Glyph.TIMELINE;
            case ACTIVITY:      return MaterialIcons.Glyph.FLOWCHART;
            case CALLGRAPH:     return MaterialIcons.Glyph.CALL_SPLIT;
            case COMPONENT:     return MaterialIcons.Glyph.WIDGETS;
            case DEPENDENCY:    return MaterialIcons.Glyph.HUB;
            case MANIFEST:      return MaterialIcons.Glyph.MANIFEST;
            case LAYOUT:        return MaterialIcons.Glyph.GRID;
            case LAYOUT_SCREEN: return MaterialIcons.Glyph.CENTER_FOCUS;
            case LAYOUT_RENDER: return MaterialIcons.Glyph.FIT_SCREEN;
            case RESOURCE_LINK: return MaterialIcons.Glyph.LAYERS;
            case NAVIGATION:    return MaterialIcons.Glyph.ROUTE;
            case SCREEN_FLOW:   return MaterialIcons.Glyph.FLOWCHART;
            case SOONG:         return MaterialIcons.Glyph.BOLT;
            case BUILD_NINJA:   return MaterialIcons.Glyph.BOLT;
            case INTERMEDIATES: return MaterialIcons.Glyph.LAYERS;
            default:            return MaterialIcons.Glyph.SCHEMA;
        }
    }

    /** 図種が属するカテゴリの色。未分類は中立グレー。 */
    public static java.awt.Color categoryColor(DiagramKind k) {
        for (DiagramCategory cat : CATEGORIES) {
            for (DiagramKind kk : cat.kinds) {
                if (kk == k) {
                    return cat.color;
                }
            }
        }
        return new java.awt.Color(0x607D8B);
    }

    /** 図種アイコン (カテゴリ色で着色した Material グリフ) を指定サイズで返す。 */
    public static javax.swing.Icon kindIcon(DiagramKind k, int size) {
        return MaterialIcons.of(kindGlyph(k), size, categoryColor(k));
    }
}
