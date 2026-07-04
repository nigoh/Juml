// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import juml.app.uml.ReferenceIndexCache;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;
import juml.util.Messages;

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
            Messages.get("explore.ref.col.callerClass"),
            Messages.get("explore.ref.col.method"),
            Messages.get("explore.ref.col.kind"),
            Messages.get("explore.ref.col.file")
    };

    private final ReferenceIndexCache refCache;
    private final JTextField targetField;
    private final JButton findButton;
    private final AnalysisRunControls runControls = new AnalysisRunControls();
    private final JTable resultTable;
    private final DefaultTableModel tableModel;
    private final JLabel statusLabel;
    /** 表示中の検索結果 (テーブル行のモデル添字と対応)。 */
    private List<ReferenceSite> currentSites = java.util.Collections.emptyList();
    /** 検索の世代。再入時に古い worker の結果/UI 更新を破棄するために使う。 */
    private int findGen;
    /** 行ダブルクリックで参照箇所のソースを開くコールバック (UmlMainFrame が配線)。 */
    private java.util.function.Consumer<ReferenceSite> onOpenSite;

    public ReverseReferencePanel(ReferenceIndexCache refCache) {
        super(new BorderLayout());
        if (refCache == null) {
            throw new IllegalArgumentException("refCache");
        }
        this.refCache = refCache;

        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        input.add(new JLabel(Messages.get("explore.ref.symbolLabel")));
        targetField = new JTextField(40);
        targetField.setToolTipText(Messages.get("explore.ref.symbolTip"));
        input.add(targetField);
        findButton = new JButton(Messages.get("explore.ref.btn.find"));
        input.add(findButton);
        input.add(runControls.cancelButton());
        input.add(runControls.progressBar());
        add(input, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        resultTable = new JTable(tableModel);
        resultTable.setAutoCreateRowSorter(true);
        JScrollPane scroll = new JScrollPane(resultTable);
        scroll.setPreferredSize(new Dimension(400, 300));
        add(scroll, BorderLayout.CENTER);

        statusLabel = new JLabel(Messages.get("explore.ref.hint"));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        add(statusLabel, BorderLayout.SOUTH);

        findButton.addActionListener(this::onFind);
        targetField.addActionListener(this::onFind);
        resultTable.setToolTipText(Messages.get("explore.ref.rowTip"));
        resultTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedSite();
                }
            }
        });
        // Enter キーでも選択行のソースへ飛べるようにする (キーボード操作)。
        resultTable.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_ENTER, 0), "openSite");
        resultTable.getActionMap().put("openSite", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSelectedSite();
            }
        });
    }

    /** 行ダブルクリック / Enter で参照箇所のソースを開くコールバックを設定する。 */
    public void setOnOpenSite(java.util.function.Consumer<ReferenceSite> onOpenSite) {
        this.onOpenSite = onOpenSite;
    }

    /** 選択行の {@link ReferenceSite} をコールバックへ渡す (ソート済みビュー添字を変換)。 */
    private void openSelectedSite() {
        if (onOpenSite == null) {
            return;
        }
        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= currentSites.size()) {
            return;
        }
        onOpenSite.accept(currentSites.get(modelRow));
    }

    /** 外部から呼んでシンボル検索を実行する。 */
    public void findReferencesTo(String symbol) {
        targetField.setText(symbol == null ? "" : symbol);
        onFind(null);
    }

    private void onFind(ActionEvent e) {
        final String target = targetField.getText().trim();
        if (target.isEmpty()) {
            statusLabel.setText(Messages.get("explore.ref.enterSymbol"));
            return;
        }
        findButton.setEnabled(false);
        statusLabel.setText(Messages.get("explore.ref.building"));
        final int myGen = ++findGen;
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
                // 新しい検索に置き換わっていれば、この古い worker の結果は捨てる
                // (別シンボルの結果を表示したり、実行中の新 worker の Cancel/進捗を
                //  無効化したりしないため)。
                if (myGen != findGen) {
                    return;
                }
                findButton.setEnabled(true);
                runControls.finished();
                try {
                    List<ReferenceSite> sites = get();
                    if (sites == null) {
                        statusLabel.setText(Messages.get("explore.ref.noProject"));
                        currentSites = java.util.Collections.emptyList();
                        tableModel.setRowCount(0);
                        return;
                    }
                    populateTable(sites);
                    statusLabel.setText(java.text.MessageFormat.format(
                            Messages.get("explore.ref.countFormat"), sites.size()));
                } catch (java.util.concurrent.CancellationException ce) {
                    statusLabel.setText(Messages.get("analysis.cancelled"));
                } catch (Exception ex) {
                    juml.util.AppLog.error(juml.util.ErrorCode.ANA_001, "ReverseReferencePanel",
                            "Reference search failed", ex);
                    statusLabel.setText(Messages.get("explore.ref.failed")
                            + " " + ex.getMessage());
                }
            }
        };
        runControls.started(worker);
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
        currentSites = sites;
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
