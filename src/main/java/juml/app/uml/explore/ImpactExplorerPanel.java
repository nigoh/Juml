// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import juml.app.uml.ReferenceIndexCache;
import juml.core.impact.ImpactAnalyzer;
import juml.core.impact.ImpactGraph;
import juml.core.refs.ReferenceIndex;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Impact Analysis を対話的に実行する Swing パネル。
 *
 * <p>入力欄に対象シンボル (FQN クラス、もしくは {@code FQN.method}) を入れて
 * 「Analyze」ボタンを押すと、{@link ImpactAnalyzer} の結果を JTree で表示する。
 * 層 (layer) 別にグループ化され、各ノードに breakage リスクとスコアを併記。</p>
 *
 * <p>{@link ReferenceIndexCache} 経由で構築済みインデックスを再利用する。
 * 初回構築は重いので {@link SwingWorker} でバックグラウンド実行する。</p>
 */
public final class ImpactExplorerPanel extends JPanel {

    private final ReferenceIndexCache refCache;
    private final JTextField targetField;
    private final JSpinner depthSpinner;
    private final JButton runButton;
    private final JTree resultTree;
    private final DefaultTreeModel treeModel;
    private final JLabel statusLabel;

    public ImpactExplorerPanel(ReferenceIndexCache refCache) {
        super(new BorderLayout());
        if (refCache == null) {
            throw new IllegalArgumentException("refCache");
        }
        this.refCache = refCache;

        // 上部: 入力エリア
        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        input.add(new JLabel("Symbol:"));
        targetField = new JTextField(40);
        targetField.setToolTipText("FQN (com.foo.Bar) or FQN.method (com.foo.Bar.doIt)");
        input.add(targetField);
        input.add(new JLabel("Depth:"));
        depthSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
        depthSpinner.setToolTipText(
                "Number of reference layers to expand (1 = direct callers/users)");
        input.add(depthSpinner);
        runButton = new JButton("Analyze");
        input.add(runButton);
        add(input, BorderLayout.NORTH);

        // 中央: 結果ツリー
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("(no analysis yet)");
        treeModel = new DefaultTreeModel(root);
        resultTree = new JTree(treeModel);
        resultTree.setRootVisible(true);
        resultTree.setShowsRootHandles(true);
        JScrollPane scroll = new JScrollPane(resultTree);
        scroll.setPreferredSize(new Dimension(400, 300));
        add(scroll, BorderLayout.CENTER);

        // 下部: ステータス (初期は使い方を案内する)
        statusLabel = new JLabel("Open a project, then enter a symbol to analyze impact.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(statusLabel, BorderLayout.SOUTH);

        runButton.addActionListener(this::onRun);
        targetField.addActionListener(this::onRun);
    }

    /** 外部から対象シンボルをセットして実行する (将来 SymbolNavigator 等から呼ぶ用)。 */
    public void analyze(String symbol) {
        targetField.setText(symbol == null ? "" : symbol);
        onRun(null);
    }

    private void onRun(ActionEvent e) {
        final String target = targetField.getText().trim();
        if (target.isEmpty()) {
            statusLabel.setText("Enter a symbol to analyze.");
            return;
        }
        final int depth = (Integer) depthSpinner.getValue();
        runButton.setEnabled(false);
        statusLabel.setText("Building reference index...");

        SwingWorker<ImpactGraph, Void> worker = new SwingWorker<ImpactGraph, Void>() {
            @Override
            protected ImpactGraph doInBackground() {
                ReferenceIndex idx = refCache.get();
                if (idx == null) {
                    return null;
                }
                ImpactAnalyzer analyzer = new ImpactAnalyzer(idx);
                return splitTargetAndAnalyze(target, depth, analyzer);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    ImpactGraph graph = get();
                    if (graph == null) {
                        statusLabel.setText("No project loaded. Open a project first.");
                        return;
                    }
                    populateTree(graph);
                    statusLabel.setText("Direct callers: " + graph.directCallerCount()
                            + ", Transitive callers: " + graph.transitiveCallerCount());
                } catch (Exception ex) {
                    juml.util.AppLog.error("ImpactExplorerPanel", "Impact analysis failed", ex);
                    statusLabel.setText("Analysis failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    /** 対象シンボルを {@code FQN.method} 形式か {@code FQN} 形式かで判別して解析。 */
    private static ImpactGraph splitTargetAndAnalyze(String target, int depth,
                                                       ImpactAnalyzer analyzer) {
        int lastDot = target.lastIndexOf('.');
        if (lastDot > 0 && lastDot < target.length() - 1) {
            String maybeOwner = target.substring(0, lastDot);
            String maybeMember = target.substring(lastDot + 1);
            if (!maybeMember.isEmpty()
                    && Character.isLowerCase(maybeMember.charAt(0))) {
                return analyzer.analyzeMethod(maybeOwner, maybeMember, depth);
            }
        }
        return analyzer.analyzeClass(target, depth);
    }

    /** {@link ImpactGraph} を Layer ごとに整理してツリーに展開。 */
    private void populateTree(ImpactGraph graph) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(
                "Target: " + graph.getTarget());

        // 層別バケット
        Map<Integer, Set<ImpactGraph.Node>> byLayer = new LinkedHashMap<>();
        for (ImpactGraph.Node n : graph.nodes()) {
            byLayer.computeIfAbsent(n.getLayer(), k -> new LinkedHashSet<>()).add(n);
        }

        for (Map.Entry<Integer, Set<ImpactGraph.Node>> e : byLayer.entrySet()) {
            int layer = e.getKey();
            String label;
            if (layer == 0) label = "Layer 0 — Target";
            else if (layer == 1) label = "Layer 1 — Direct callers ("
                    + e.getValue().size() + ")";
            else label = "Layer " + layer + " — Transitive callers ("
                    + e.getValue().size() + ")";
            DefaultMutableTreeNode layerNode = new DefaultMutableTreeNode(label);
            for (ImpactGraph.Node n : e.getValue()) {
                String nodeLabel = n.getId()
                        + "  [" + n.getBreakageRisk()
                        + " " + String.format("%.2f", n.getScore())
                        + " " + n.getReason() + "]";
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(nodeLabel);
                // 関連エッジを children として追加 (この node が caller のエッジ)
                List<ImpactGraph.Edge> edges = graph.edges();
                for (ImpactGraph.Edge ed : edges) {
                    if (ed.getFrom().equals(n.getId())) {
                        String edgeLabel = "→ " + ed.getTo()
                                + " (" + ed.getKind()
                                + (ed.getCallerMethod().isEmpty() ? ""
                                        : " @ " + ed.getCallerMethod())
                                + ")";
                        child.add(new DefaultMutableTreeNode(edgeLabel));
                    }
                }
                layerNode.add(child);
            }
            root.add(layerNode);
        }

        treeModel.setRoot(root);
        // ルートと layer 1 まで展開
        resultTree.expandPath(new TreePath(root.getPath()));
        if (root.getChildCount() > 0) {
            DefaultMutableTreeNode firstLayer = (DefaultMutableTreeNode) root.getChildAt(0);
            resultTree.expandPath(new TreePath(firstLayer.getPath()));
            if (root.getChildCount() > 1) {
                DefaultMutableTreeNode secondLayer = (DefaultMutableTreeNode) root.getChildAt(1);
                resultTree.expandPath(new TreePath(secondLayer.getPath()));
            }
        }
    }
}
