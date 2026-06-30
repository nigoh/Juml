// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import juml.app.uml.ReferenceIndexCache;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * シンボルへの参照箇所を表で表示する Swing パネル。
 *
 * <p>{@code --ref-find} CLI と同等の機能を GUI で提供する。</p>
 */
public final class ReverseReferencePanel extends JPanel {

    private static final String[] COLUMNS = {
            "Caller class", "Method", "Kind", "File"
    };

    private final ReferenceIndexCache refCache;
    private final JTextField targetField;
    private final JButton findButton;
    private final JTable resultTable;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;

    public ReverseReferencePanel(ReferenceIndexCache refCache) {
        super(new BorderLayout());
        if (refCache == null) {
            throw new IllegalArgumentException("refCache");
        }
        this.refCache = refCache;

        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        input.add(new JLabel("Symbol:"));
        targetField = new JTextField(40);
        targetField.setToolTipText("FQN (com.foo.Bar) or FQN.member (com.foo.Bar.doIt)");
        input.add(targetField);
        findButton = new JButton("Find references");
        input.add(findButton);
        add(input, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        resultTable = new JTable(tableModel);
        resultTable.setAutoCreateRowSorter(true);
        JScrollPane scroll = new JScrollPane(resultTable);
        scroll.setPreferredSize(new Dimension(400, 300));
        add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel("Open a project, then enter a symbol to find its references.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(statusLabel, BorderLayout.SOUTH);

        findButton.addActionListener(this::onFind);
        targetField.addActionListener(this::onFind);
    }

    /** 外部から呼んでシンボル検索を実行する。 */
    public void findReferencesTo(String symbol) {
        targetField.setText(symbol == null ? "" : symbol);
        onFind(null);
    }

    private void onFind(ActionEvent e) {
        final String target = targetField.getText().trim();
        if (target.isEmpty()) {
            statusLabel.setText("Enter a symbol to search.");
            return;
        }
        findButton.setEnabled(false);
        statusLabel.setText("Building reference index...");
        SwingWorker<List<ReferenceSite>, Void> worker =
                new SwingWorker<List<ReferenceSite>, Void>() {
            @Override
            protected List<ReferenceSite> doInBackground() {
                ReferenceIndex idx = refCache.get();
                if (idx == null) {
                    return null;
                }
                return queryReferences(idx, target);
            }

            @Override
            protected void done() {
                findButton.setEnabled(true);
                try {
                    List<ReferenceSite> sites = get();
                    if (sites == null) {
                        statusLabel.setText("No project loaded. Open a project first.");
                        tableModel.setRowCount(0);
                        return;
                    }
                    populateTable(sites);
                    statusLabel.setText(sites.size() + " reference(s) found.");
                } catch (Exception ex) {
                    juml.util.AppLog.error("ReverseReferencePanel",
                            "Reference search failed", ex);
                    statusLabel.setText("Search failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    /** target の形式によりクラス/メソッド検索を切り替える。 */
    private static List<ReferenceSite> queryReferences(ReferenceIndex idx, String target) {
        int lastDot = target.lastIndexOf('.');
        if (lastDot > 0 && lastDot < target.length() - 1) {
            String maybeOwner = target.substring(0, lastDot);
            String maybeMember = target.substring(lastDot + 1);
            if (!maybeMember.isEmpty()
                    && Character.isLowerCase(maybeMember.charAt(0))) {
                return idx.sitesByMember(
                        ReferenceKey.Kind.METHOD, maybeOwner, maybeMember);
            }
        }
        return idx.sitesForClass(target);
    }

    private void populateTable(List<ReferenceSite> sites) {
        tableModel.setRowCount(0);
        for (ReferenceSite s : sites) {
            String fileCol = s.getFile();
            if (s.getLineHint() > 0 && !fileCol.isEmpty()) {
                fileCol = fileCol + ":" + s.getLineHint();
            }
            tableModel.addRow(new Object[]{
                    s.getCallerFqn(),
                    s.getCallerMethod(),
                    s.getKind().name(),
                    fileCol
            });
        }
    }
}
