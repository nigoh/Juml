// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

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
            DiagramKind.DEPENDENCY, DiagramKind.COMPONENT, DiagramKind.COMMON);
    public static final EnumSet<DiagramKind> DIAGRAMS_PACKAGE = EnumSet.of(
            DiagramKind.CLASS, DiagramKind.PACKAGE, DiagramKind.COMMON);
    public static final EnumSet<DiagramKind> DIAGRAMS_JAVA_TYPE = EnumSet.of(
            DiagramKind.CLASS, DiagramKind.COMMON);
    public static final EnumSet<DiagramKind> DIAGRAMS_METHOD = EnumSet.of(
            DiagramKind.SEQUENCE, DiagramKind.ACTIVITY, DiagramKind.CALLGRAPH);
    public static final EnumSet<DiagramKind> DIAGRAMS_ANDROID = EnumSet.of(
            DiagramKind.MANIFEST, DiagramKind.COMPONENT, DiagramKind.SCREEN_FLOW,
            DiagramKind.SOONG);

    /** ツールバーアクションのコールバック群。 */
    public static final class Callbacks {
        public Runnable chooseProject;
        public Runnable chooseAndExport;
        public Runnable refreshDiagram;
        public Runnable openEntitySearch;
        public Consumer<DiagramKind> selectDiagramKind;
    }

    /** {@link #build()} の戻り値。 */
    public static final class Result {
        public final JComponent toolBarPanel;
        public final EnumMap<DiagramKind, JToggleButton> diagramToggles;
        public final javax.swing.ButtonGroup diagramToolbarGroup;

        Result(JComponent toolBarPanel,
               EnumMap<DiagramKind, JToggleButton> diagramToggles,
               javax.swing.ButtonGroup diagramToolbarGroup) {
            this.toolBarPanel = toolBarPanel;
            this.diagramToggles = diagramToggles;
            this.diagramToolbarGroup = diagramToolbarGroup;
        }
    }

    private final DiagramKind initialKind;
    private final Callbacks cb;

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
        return new Result(container, toggles, group);
    }

    private JToolBar buildActionToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.add(makeButton("Open", "Open project (Ctrl+O)", e -> cb.chooseProject.run()));
        bar.add(makeButton("Save", "Save diagram (Ctrl+S)", e -> cb.chooseAndExport.run()));
        bar.add(makeButton("Refresh", "Refresh diagram (F5)", e -> cb.refreshDiagram.run()));
        bar.addSeparator();
        bar.add(makeButton("Search", "Search entities (Ctrl+Shift+F)",
                e -> cb.openEntitySearch.run()));
        return bar;
    }

    private JToolBar buildDiagramKindToolBar(EnumMap<DiagramKind, JToggleButton> toggles,
                                             javax.swing.ButtonGroup group) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.add(new JLabel(" Diagram: "));
        for (DiagramKind k : DiagramKind.values()) {
            JToggleButton b = new JToggleButton(toolbarLabel(k));
            b.setToolTipText(k.getDisplayName() + tooltipExtra(k));
            b.setFocusable(false);
            if (k == initialKind) {
                b.setSelected(true);
            }
            final DiagramKind kind = k;
            b.addActionListener(e -> cb.selectDiagramKind.accept(kind));
            group.add(b);
            toggles.put(k, b);
            bar.add(b);
        }
        return bar;
    }

    public static String toolbarLabel(DiagramKind k) {
        switch (k) {
            case CLASS: return "Class";
            case PACKAGE: return "Package";
            case SEQUENCE: return "Sequence";
            case ACTIVITY: return "Activity";
            case CALLGRAPH: return "Call Graph";
            case COMMON: return "Common";
            case COMPONENT: return "Component";
            case DEPENDENCY: return "Dependency";
            case MANIFEST: return "Manifest";
            case LAYOUT: return "Layout";
            case LAYOUT_SCREEN: return "Screen";
            case RESOURCE_LINK: return "Resources";
            case NAVIGATION: return "Navigation";
            case MODULE: return "Module";
            case INHERITANCE: return "Inherit";
            case SOONG: return "Soong";
            default: return k.getDisplayName();
        }
    }

    public static String tooltipExtra(DiagramKind k) {
        switch (k) {
            case SEQUENCE:   return " (choose entry from Diagram menu)";
            case ACTIVITY:   return " (choose method from Diagram menu)";
            case CALLGRAPH:  return " — shows which functions are called from entry";
            case LAYOUT:     return " (choose layout file from Diagram menu)";
            case LAYOUT_SCREEN: return " — wireframe screen preview (choose layout file)";
            case RESOURCE_LINK: return " — code ↔ layout/string resource links";
            case NAVIGATION: return " (choose navigation file from Diagram menu)";
            case COMMON:     return " — top-N most referenced classes (fan-in)";
            case SOONG:      return " — Android.bp (Soong) module dependencies";
            default: return "";
        }
    }

    public static JButton makeButton(String text, String tooltip, ActionListener listener) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.addActionListener(listener);
        return b;
    }
}
