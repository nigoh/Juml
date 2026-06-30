// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.doxygen.DoxGroup;
import juml.core.formats.doxygen.DoxModel;
import juml.core.formats.doxygen.DoxygenLocator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Doxygen の論理グループ階層 ({@code @defgroup} / {@code @ingroup}) をツリー表示する独立タブ (R4)。
 *
 * <p>{@link DoxygenResultCache} を共有し、Doxygen / TODO タブと同じ 1 回の解析結果を使う。
 * グループ → 下位グループ・所属型をツリーで辿れる。グループ注釈の無いプロジェクトでは空になる。</p>
 */
public final class DoxygenGroupsPanel extends JPanel {

    private final ProjectAnalysisCache projectCache;
    private final DoxygenResultCache resultCache;
    private final JButton runButton = new JButton("Run Doxygen");
    private final JButton locateButton = new JButton("Locate doxygen...");
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Groups");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);

    public DoxygenGroupsPanel(ProjectAnalysisCache projectCache, DoxygenResultCache resultCache) {
        super(new BorderLayout(0, 4));
        if (projectCache == null || resultCache == null) {
            throw new IllegalArgumentException("projectCache/resultCache");
        }
        this.projectCache = projectCache;
        this.resultCache = resultCache;
        resultCache.addListener(this::rebuild);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        input.add(runButton);
        input.add(locateButton);
        input.add(new JLabel("@defgroup / @ingroup hierarchy (from doxygen XML)"));
        add(input, BorderLayout.NORTH);

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);

        runButton.addActionListener(this::onRun);
        locateButton.addActionListener(this::onLocate);
        refreshLocateVisibility();
        rebuild();
    }

    private void refreshLocateVisibility() {
        locateButton.setVisible(!DoxygenLocator.isAvailable());
    }

    private void onLocate(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select doxygen executable");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (DoxygenLocator.useDoxygenBinary(fc.getSelectedFile())) {
            statusLabel.setText("doxygen set: " + DoxygenLocator.getDoxygenPath());
            refreshLocateVisibility();
        } else {
            statusLabel.setText("Not an executable file: " + fc.getSelectedFile());
        }
    }

    private void onRun(ActionEvent e) {
        if (!projectCache.isLoaded()) {
            statusLabel.setText("No project loaded. Open a project first.");
            return;
        }
        File root = projectCache.getProjectRoot();
        if (root == null || !root.isDirectory()) {
            statusLabel.setText("Doxygen needs a project directory (archives are not supported).");
            return;
        }
        if (!DoxygenLocator.isAvailable() && !DoxygenLocator.redetect()) {
            statusLabel.setText("doxygen not found. Click \"Locate doxygen...\" to select it.");
            refreshLocateVisibility();
            return;
        }
        resultCache.runAsync(root,
                () -> {
                    runButton.setEnabled(false);
                    locateButton.setEnabled(false);
                    statusLabel.setText("Running doxygen (this may take a while)...");
                },
                msg -> statusLabel.setText("Doxygen failed: " + msg),
                () -> {
                    runButton.setEnabled(true);
                    locateButton.setEnabled(true);
                    refreshLocateVisibility();
                });
    }

    /** 共有キャッシュの結果からグループツリーを作り直す。 */
    private void rebuild() {
        rootNode.removeAllChildren();
        DoxModel model = resultCache.getModel();
        if (model == null) {
            treeModel.reload();
            statusLabel.setText("Run Doxygen to see the @defgroup / @ingroup hierarchy.");
            return;
        }
        List<DoxGroup> groups = model.getGroups();
        Map<String, DoxGroup> byId = new LinkedHashMap<>();
        Set<String> nested = new HashSet<>();
        for (DoxGroup g : groups) {
            byId.put(g.getId(), g);
            nested.addAll(g.getInnerGroupIds());
        }
        // トップレベル = どのグループの下位にもなっていないグループ。
        int topLevel = 0;
        for (DoxGroup g : groups) {
            if (!nested.contains(g.getId())) {
                rootNode.add(buildGroupNode(g, byId, new HashSet<>()));
                topLevel++;
            }
        }
        treeModel.reload();
        if (groups.isEmpty()) {
            statusLabel.setText("No @defgroup groups found in this project.");
        } else {
            tree.expandPath(new TreePath(rootNode.getPath()));
            statusLabel.setText(groups.size() + " groups (" + topLevel + " top-level).");
        }
    }

    /** グループ 1 件のツリーノードを構築する。下位グループは再帰、循環は visited で防ぐ。 */
    private DefaultMutableTreeNode buildGroupNode(DoxGroup g, Map<String, DoxGroup> byId,
                                                  Set<String> visited) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode("[group] " + g.getTitle());
        if (!visited.add(g.getId())) {
            return node; // 循環参照ガード
        }
        for (String subId : g.getInnerGroupIds()) {
            DoxGroup sub = byId.get(subId);
            if (sub != null) {
                node.add(buildGroupNode(sub, byId, visited));
            } else {
                node.add(new DefaultMutableTreeNode("[group] " + subId));
            }
        }
        for (String cls : g.getInnerClassNames()) {
            node.add(new DefaultMutableTreeNode(cls));
        }
        visited.remove(g.getId());
        return node;
    }
}
