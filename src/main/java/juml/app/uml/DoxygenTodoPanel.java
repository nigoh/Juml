// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.doxygen.DoxModel;
import juml.core.formats.doxygen.DoxXrefItem;
import juml.core.formats.doxygen.DoxygenLocator;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

/**
 * Doxygen の xref 項目 ({@code @todo} / {@code @bug} / {@code @deprecated}) をプロジェクト横断で
 * 集約表示する独立の解析タブ (R3)。
 *
 * <p>{@link DoxygenPanel} と {@link DoxygenResultCache} を共有し、どちらのタブで doxygen を
 * 実行しても同じ 1 回の結果を使い回す (二重起動しない)。種別フィルタ付きのテーブルに
 * 「種別 / 箇所 / 内容」を一覧する。</p>
 */
public final class DoxygenTodoPanel extends JPanel {

    private static final String[] COLUMNS = {"Type", "Location", "Description"};

    private final ProjectAnalysisCache projectCache;
    private final DoxygenResultCache resultCache;
    private final JButton runButton = new JButton("Run Doxygen");
    private final JButton locateButton = new JButton("Locate doxygen...");
    private final JComboBox<String> filter =
            new JComboBox<>(new String[]{"All", "Todo", "Bug", "Deprecated"});
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);

    public DoxygenTodoPanel(ProjectAnalysisCache projectCache, DoxygenResultCache resultCache) {
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
        input.add(new JLabel("Show:"));
        input.add(filter);
        input.add(new JLabel("@todo / @bug / @deprecated across the project"));
        add(input, BorderLayout.NORTH);

        add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);

        runButton.addActionListener(this::onRun);
        locateButton.addActionListener(this::onLocate);
        filter.addActionListener(e -> rebuild());
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

    /** 共有キャッシュの結果からテーブルを作り直す (フィルタ適用)。 */
    private void rebuild() {
        tableModel.setRowCount(0);
        DoxModel model = resultCache.getModel();
        if (model == null) {
            statusLabel.setText("Run Doxygen to collect @todo / @bug / @deprecated items.");
            return;
        }
        List<DoxXrefItem> items = model.getXrefItems();
        String selected = (String) filter.getSelectedItem();
        int shown = 0;
        for (DoxXrefItem item : items) {
            String type = label(item.getKind());
            if (!"All".equals(selected) && !type.equals(selected)) {
                continue;
            }
            tableModel.addRow(new Object[]{type, item.getLocation(), item.getDescription()});
            shown++;
        }
        if (items.isEmpty()) {
            statusLabel.setText("No @todo / @bug / @deprecated items found.");
        } else {
            statusLabel.setText(shown + " of " + items.size() + " items shown.");
        }
    }

    private static String label(DoxXrefItem.Kind kind) {
        switch (kind) {
            case TODO: return "Todo";
            case BUG: return "Bug";
            case DEPRECATED: return "Deprecated";
            default: return "Other";
        }
    }
}
