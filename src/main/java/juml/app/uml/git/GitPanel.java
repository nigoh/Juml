// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * git リポジトリ閲覧タブ (読み取り専用)。
 *
 * <p>プロジェクトロード時に {@link #setRepositoryRoot(File)} でリポジトリを検出し、
 * 「Commits (履歴 + diff) / Branches &amp; Tags / File History (履歴 + blame)」の
 * サブタブを提供する。プロジェクトが git 管理でない場合や未ロード時は、案内と
 * 「リポジトリを選択」ボタンだけの空状態を表示する。</p>
 *
 * <p>JGit 呼び出しはすべて {@link SwingWorker} で背景実行し EDT を止めない。
 * 書き込み系の git 操作は一切行わない。</p>
 */
public final class GitPanel extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_CONTENT = "content";

    /** サブペインが共有する読み取りコンテキスト。 */
    interface GitContext {
        /** 現在開いているリポジトリ (未検出なら null)。 */
        GitRepoService service();

        /** 履歴表示の基準 ref (ブランチ名)。 */
        String selectedRef();

        /** ステータスバーへの通知。 */
        void reportStatus(String msg);

        /** ブランチ一覧ダブルクリックなどで基準 ref を切り替える。 */
        void selectRef(String ref);
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JLabel emptyLabel = new JLabel("", javax.swing.SwingConstants.CENTER);
    private final JLabel repoLabel = new JLabel(" ");
    private final JComboBox<String> branchCombo = new JComboBox<>();
    private final GitCommitsPane commitsPane;
    private final GitBranchesPane branchesPane;
    private final GitFileHistoryPane filePane;
    /** ステータスバー通知 (null 可)。 */
    private java.util.function.Consumer<String> statusReporter;

    private GitRepoService service;
    /** 古い open 結果で UI を上書きしない世代カウンタ。 */
    private int openGen;
    /** ブランチコンボ更新中はリスナー起点の再読込を抑止する。 */
    private boolean updatingBranches;

    public GitPanel() {
        super(new BorderLayout());
        GitContext ctx = new GitContext() {
            @Override public GitRepoService service() {
                return service;
            }
            @Override public String selectedRef() {
                Object sel = branchCombo.getSelectedItem();
                return sel != null ? sel.toString() : "HEAD";
            }
            @Override public void reportStatus(String msg) {
                if (statusReporter != null) {
                    statusReporter.accept(msg);
                }
            }
            @Override public void selectRef(String ref) {
                branchCombo.setSelectedItem(ref);
            }
        };
        commitsPane = new GitCommitsPane(ctx);
        branchesPane = new GitBranchesPane(ctx);
        filePane = new GitFileHistoryPane(ctx);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.add(new JLabel(Messages.get("git.branch")));
        branchCombo.setPrototypeDisplayValue("feature/some-long-branch-name");
        branchCombo.addActionListener(e -> {
            if (!updatingBranches && service != null) {
                reloadAll();
            }
        });
        bar.add(branchCombo);
        JButton refresh = new JButton(Messages.get("git.refresh"));
        refresh.addActionListener(e -> refresh());
        bar.add(refresh);
        bar.add(repoLabel);

        JTabbedPane subTabs = new JTabbedPane();
        subTabs.addTab(Messages.get("git.tab.commits"), commitsPane);
        subTabs.addTab(Messages.get("git.tab.branches"), branchesPane);
        subTabs.addTab(Messages.get("git.tab.fileHistory"), filePane);

        JPanel content = new JPanel(new BorderLayout());
        content.add(bar, BorderLayout.NORTH);
        content.add(subTabs, BorderLayout.CENTER);

        JPanel empty = new JPanel(new java.awt.GridBagLayout());
        JPanel emptyInner = new JPanel(new BorderLayout(0, 12));
        emptyInner.setOpaque(false);
        emptyLabel.setText(Messages.get("git.repo.none"));
        emptyInner.add(emptyLabel, BorderLayout.CENTER);
        JButton choose = new JButton(Messages.get("git.repo.choose"));
        choose.addActionListener(e -> chooseRepository());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnRow.setOpaque(false);
        btnRow.add(choose);
        emptyInner.add(btnRow, BorderLayout.SOUTH);
        empty.add(emptyInner);

        cardHost.add(empty, CARD_EMPTY);
        cardHost.add(content, CARD_CONTENT);
        add(cardHost, BorderLayout.CENTER);
        cards.show(cardHost, CARD_EMPTY);
    }

    /** ステータスバー通知コールバックを設定する。 */
    public void setStatusReporter(java.util.function.Consumer<String> reporter) {
        this.statusReporter = reporter;
    }

    /**
     * プロジェクトルートから git リポジトリを検出して開く (ロード成功時に呼ぶ)。
     * 検出・オープンは背景実行し、見つからなければ空状態を表示する。
     */
    public void setRepositoryRoot(File root) {
        final int gen = ++openGen;
        new SwingWorker<GitRepoService, Void>() {
            @Override protected GitRepoService doInBackground() {
                return GitRepoService.open(root);
            }

            @Override protected void done() {
                if (gen != openGen) {
                    return; // より新しいオープンが走っている
                }
                GitRepoService opened = null;
                try {
                    opened = get();
                } catch (Exception ignored) {
                    // 検出失敗は「リポジトリなし」として扱う
                }
                installService(opened);
            }
        }.execute();
    }

    /** 任意のディレクトリを選んでリポジトリとして開く (プロジェクト未ロードでも使える)。 */
    private void chooseRepository() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(Messages.get("git.repo.choose"));
        fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        setRepositoryRoot(fc.getSelectedFile());
    }

    /** 新しいサービスに差し替え、ブランチ一覧と各サブペインを再読込する。 */
    private void installService(GitRepoService opened) {
        if (service != null) {
            service.close();
        }
        service = opened;
        if (service == null) {
            repoLabel.setText(" ");
            cards.show(cardHost, CARD_EMPTY);
            return;
        }
        File tree = service.workTree();
        repoLabel.setText(tree != null ? tree.getAbsolutePath() : "");
        cards.show(cardHost, CARD_CONTENT);
        refresh();
    }

    /** ブランチコンボを現在のリポジトリ内容で再構築してから全ペインを再読込する。 */
    private void refresh() {
        if (service == null) {
            return;
        }
        final GitRepoService svc = service;
        new SwingWorker<List<String>, Void>() {
            private String current;

            @Override protected List<String> doInBackground() throws Exception {
                current = svc.currentBranch();
                return svc.localBranches();
            }

            @Override protected void done() {
                if (svc != service) {
                    return;
                }
                try {
                    List<String> branches = get();
                    updatingBranches = true;
                    try {
                        branchCombo.removeAllItems();
                        for (String b : branches) {
                            branchCombo.addItem(b);
                        }
                        if (branches.isEmpty() && current != null) {
                            branchCombo.addItem(current); // detached HEAD など
                        }
                        branchCombo.setSelectedItem(current);
                    } finally {
                        updatingBranches = false;
                    }
                    reloadAll();
                } catch (Exception ex) {
                    reportStatusSafe(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    private void reloadAll() {
        commitsPane.reload();
        branchesPane.reload();
        filePane.onRepositoryChanged();
    }

    private void reportStatusSafe(String msg) {
        if (statusReporter != null) {
            statusReporter.accept(msg);
        }
    }

    /** 一覧・blame 共通の日時表記。 */
    static String formatDate(Date when) {
        if (when == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(when);
    }
}
