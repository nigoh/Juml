// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@link DiagramController} が必要とする状態・UI 参照・コールバックを 1 つにまとめた
 * 受け渡し用ホルダ。コンストラクタ引数の肥大化 (ParameterNumber) を避けるため、
 * 各依存をフィールド代入で設定してから {@link DiagramController} に渡す。
 */
public final class DiagramControllerDeps {
    public DiagramState state;
    public Supplier<ProjectAnalysisCache> cacheSupplier;
    public EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    public EnumMap<DiagramKind, JToggleButton> diagramToggles;
    public ProjectTreePanel treePanel;
    public JTabbedPane mainTabs;
    public DiagramTabPane tabPane;
    public javax.swing.JLabel statusLabel;
    public java.awt.Frame parentFrame;
    public Runnable refreshDiagram;
    public Consumer<DiagramKind> onKindChanged;
}
