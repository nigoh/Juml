// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.BlameLine;
import juml.app.uml.git.GitRepoService.CommitInfo;
import juml.util.Messages;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * git 閲覧タブの「File History」サブペイン: 指定ファイルの変更履歴一覧と、
 * 選択コミット時点の diff / HEAD の blame (行単位の最終変更者) を表示する
 * (読み取り専用)。
 */
final class GitFileHistoryPane extends JPanel {

    private final GitPanel.GitContext ctx;
    private final JTextField pathField = new JTextField(32);
    private final DefaultListModel<CommitInfo> historyModel = new DefaultListModel<>();
    private final JList<CommitInfo> historyList = new JList<>(historyModel);
    private final JTextArea textArea = new JTextArea();
    /** 古い SwingWorker 結果で UI を上書きしない世代カウンタ。 */
    private int gen;

    GitFileHistoryPane(GitPanel.GitContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        pathField.setToolTipText(Messages.get("git.file.pathTip"));
        bar.add(pathField);
        JButton choose = new JButton(Messages.get("git.file.choose"));
        choose.addActionListener(e -> chooseFile());
        bar.add(choose);
        JButton history = new JButton(Messages.get("git.file.history"));
        history.addActionListener(e -> loadHistory());
        bar.add(history);
        JButton blame = new JButton(Messages.get("git.file.blame"));
        blame.addActionListener(e -> loadBlame());
        bar.add(blame);
        add(bar, BorderLayout.NORTH);

        historyList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object text = value;
                if (value instanceof CommitInfo) {
                    CommitInfo c = (CommitInfo) value;
                    text = c.shortSha + "  " + GitPanel.formatDate(c.when)
                            + "  " + c.author + "  —  " + c.shortMessage;
                }
                return super.getListCellRendererComponent(
                        list, text, index, isSelected, cellHasFocus);
            }
        });
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedCommitDiff();
            }
        });
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setText(Messages.get("git.file.hint"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(historyList), new JScrollPane(textArea));
        split.setResizeWeight(0.35);
        add(split, BorderLayout.CENTER);
    }

    /** リポジトリが切り替わったら入力・結果をリセットする。 */
    void onRepositoryChanged() {
        pathField.setText("");
        historyModel.clear();
        textArea.setText(Messages.get("git.file.hint"));
    }

    /** 作業ツリー内のファイルを選び、リポジトリ相対パスへ変換して入力欄に入れる。 */
    private void chooseFile() {
        GitRepoService svc = ctx.service();
        if (svc == null) {
            return;
        }
        JFileChooser fc = new JFileChooser(svc.workTree());
        fc.setDialogTitle(Messages.get("git.file.choose"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String rel = svc.relativize(fc.getSelectedFile());
        if (rel == null) {
            ctx.reportStatus(Messages.get("git.file.outsideRepo"));
            return;
        }
        pathField.setText(rel);
        loadHistory();
    }

    /** 入力欄のファイルに触れたコミット履歴を背景で読み込む。 */
    private void loadHistory() {
        final GitRepoService svc = ctx.service();
        final String path = pathField.getText().trim();
        if (svc == null || path.isEmpty()) {
            return;
        }
        final String ref = ctx.selectedRef();
        final int g = ++gen;
        ctx.reportStatus(Messages.get("git.status.loading"));
        new SwingWorker<List<CommitInfo>, Void>() {
            @Override protected List<CommitInfo> doInBackground() throws Exception {
                return svc.fileLog(ref, path, GitRepoService.DEFAULT_LOG_LIMIT);
            }

            @Override protected void done() {
                if (g != gen) {
                    return;
                }
                try {
                    List<CommitInfo> commits = get();
                    historyModel.clear();
                    for (CommitInfo c : commits) {
                        historyModel.addElement(c);
                    }
                    ctx.reportStatus(java.text.MessageFormat.format(
                            Messages.get("git.status.fileHistoryLoaded"),
                            commits.size(), path));
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    /** 履歴で選択したコミットにおける、このファイルの diff を表示する。 */
    private void showSelectedCommitDiff() {
        final GitRepoService svc = ctx.service();
        final CommitInfo c = historyList.getSelectedValue();
        final String path = pathField.getText().trim();
        if (svc == null || c == null || path.isEmpty()) {
            return;
        }
        final int g = ++gen;
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return svc.diffOf(c.sha, path);
            }

            @Override protected void done() {
                if (g != gen) {
                    return;
                }
                try {
                    textArea.setText(get());
                    textArea.setCaretPosition(0);
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    /** 入力欄のファイルの blame (行単位の最終変更コミット) を表示する。 */
    private void loadBlame() {
        final GitRepoService svc = ctx.service();
        final String path = pathField.getText().trim();
        if (svc == null || path.isEmpty()) {
            return;
        }
        final String ref = ctx.selectedRef();
        final int g = ++gen;
        ctx.reportStatus(Messages.get("git.status.loading"));
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                return formatBlame(svc.blame(ref, path));
            }

            @Override protected void done() {
                if (g != gen) {
                    return;
                }
                try {
                    String text = get();
                    textArea.setText(text.isEmpty()
                            ? Messages.get("git.file.blameEmpty") : text);
                    textArea.setCaretPosition(0);
                    ctx.reportStatus(java.text.MessageFormat.format(
                            Messages.get("git.status.blameLoaded"), path));
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    /** blame 結果を「SHA 作者 日時 | 行内容」の等幅テキストに整形する。 */
    private static String formatBlame(List<BlameLine> lines) {
        int authorWidth = 0;
        for (BlameLine l : lines) {
            authorWidth = Math.max(authorWidth, l.author.length());
        }
        StringBuilder sb = new StringBuilder();
        int lineNo = 1;
        for (BlameLine l : lines) {
            sb.append(l.shortSha)
                    .append(' ')
                    .append(String.format("%-" + Math.max(1, authorWidth) + "s", l.author))
                    .append(' ')
                    .append(String.format("%-16s", GitPanel.formatDate(l.when)))
                    .append(String.format(" %5d | ", lineNo++))
                    .append(l.content)
                    .append('\n');
        }
        return sb.toString();
    }
}
