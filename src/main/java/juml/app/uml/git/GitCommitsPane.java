// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.CommitInfo;
import juml.app.uml.git.GitRepoService.FileChange;
import juml.util.Messages;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;

/**
 * git 閲覧タブの「Commits」サブペイン: コミット履歴一覧 + 選択コミットの
 * メッセージ / 変更ファイル / unified diff を表示する (読み取り専用)。
 */
final class GitCommitsPane extends JPanel {

    private final GitPanel.GitContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{
                    Messages.get("git.col.sha"), Messages.get("git.col.message"),
                    Messages.get("git.col.author"), Messages.get("git.col.date")}, 0) {
        @Override public boolean isCellEditable(int row, int column) {
            return false; // 閲覧のみ
        }
    };
    private final JTable table = new JTable(model);
    private final JTextArea messageArea = new JTextArea(4, 40);
    private final DefaultListModel<FileChange> filesModel = new DefaultListModel<>();
    private final JList<FileChange> filesList = new JList<>(filesModel);
    private final JTextArea diffArea = new JTextArea();

    /** 表示中の履歴 (テーブル行 → コミット対応)。 */
    private List<CommitInfo> commits = List.of();
    /** 古い SwingWorker 結果で UI を上書きしない世代カウンタ。 */
    private int logGen;
    private int detailGen;
    private int diffGen;

    GitCommitsPane(GitPanel.GitContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(420);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedCommit();
            }
        });

        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        filesList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object text = value instanceof FileChange
                        ? ((FileChange) value).display() : value;
                return super.getListCellRendererComponent(
                        list, text, index, isSelected, cellHasFocus);
            }
        });
        filesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedFileDiff();
            }
        });
        diffArea.setEditable(false);
        diffArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        diffArea.setText(Messages.get("git.diff.hint"));

        JPanel detail = new JPanel(new BorderLayout(0, 4));
        detail.add(new JScrollPane(messageArea), BorderLayout.NORTH);
        detail.add(new JScrollPane(filesList), BorderLayout.CENTER);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                detail, new JScrollPane(diffArea));
        right.setResizeWeight(0.4);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(table), right);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);
    }

    /** 現在の ref のコミット履歴を背景で読み込み直す。 */
    void reload() {
        final GitRepoService svc = ctx.service();
        if (svc == null) {
            return;
        }
        final String ref = ctx.selectedRef();
        final int gen = ++logGen;
        ctx.reportStatus(Messages.get("git.status.loading"));
        new SwingWorker<List<CommitInfo>, Void>() {
            @Override protected List<CommitInfo> doInBackground() throws Exception {
                return svc.log(ref, GitRepoService.DEFAULT_LOG_LIMIT);
            }

            @Override protected void done() {
                if (gen != logGen) {
                    return;
                }
                try {
                    commits = get();
                    model.setRowCount(0);
                    for (CommitInfo c : commits) {
                        model.addRow(new Object[]{c.shortSha, c.shortMessage,
                                c.author, GitPanel.formatDate(c.when)});
                    }
                    messageArea.setText("");
                    filesModel.clear();
                    diffArea.setText(Messages.get("git.diff.hint"));
                    ctx.reportStatus(java.text.MessageFormat.format(
                            Messages.get("git.status.commitsLoaded"), commits.size(), ref));
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    /** 選択コミットのメッセージと変更ファイル一覧を読み込む。 */
    private void showSelectedCommit() {
        CommitInfo c = selectedCommit();
        if (c == null) {
            return;
        }
        messageArea.setText(c.fullMessage);
        messageArea.setCaretPosition(0);
        filesModel.clear();
        diffArea.setText(Messages.get("git.diff.fileHint"));
        final GitRepoService svc = ctx.service();
        if (svc == null) {
            return;
        }
        final int gen = ++detailGen;
        new SwingWorker<List<FileChange>, Void>() {
            @Override protected List<FileChange> doInBackground() throws Exception {
                return svc.changesOf(c.sha);
            }

            @Override protected void done() {
                if (gen != detailGen) {
                    return;
                }
                try {
                    for (FileChange f : get()) {
                        filesModel.addElement(f);
                    }
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    /** 選択ファイルの unified diff を読み込む。 */
    private void showSelectedFileDiff() {
        CommitInfo c = selectedCommit();
        FileChange f = filesList.getSelectedValue();
        final GitRepoService svc = ctx.service();
        if (c == null || f == null || svc == null) {
            return;
        }
        final String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
        final int gen = ++diffGen;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return svc.diffOf(c.sha, path);
            }

            @Override protected void done() {
                if (gen != diffGen) {
                    return;
                }
                try {
                    diffArea.setText(get());
                    diffArea.setCaretPosition(0);
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    private CommitInfo selectedCommit() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= commits.size()) {
            return null;
        }
        return commits.get(table.convertRowIndexToModel(row));
    }
}
