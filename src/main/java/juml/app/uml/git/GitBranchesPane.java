// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * git 閲覧タブの「Branches &amp; Tags」サブペイン: ローカル/リモートブランチと
 * タグの一覧を表示する (読み取り専用)。ローカルブランチのダブルクリックで
 * 履歴表示の基準 ref を切り替える。
 */
final class GitBranchesPane extends JPanel {

    private final GitPanel.GitContext ctx;
    private final DefaultListModel<String> localModel = new DefaultListModel<>();
    private final DefaultListModel<String> remoteModel = new DefaultListModel<>();
    private final DefaultListModel<String> tagModel = new DefaultListModel<>();
    /** 古い SwingWorker 結果で UI を上書きしない世代カウンタ。 */
    private int gen;

    GitBranchesPane(GitPanel.GitContext ctx) {
        super(new GridLayout(1, 3, 8, 0));
        this.ctx = ctx;
        JList<String> localList = new JList<>(localModel);
        // ダブルクリックしたローカルブランチを Commits 表示の基準 ref にする。
        localList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String sel = localList.getSelectedValue();
                    if (sel != null) {
                        ctx.selectRef(sel);
                    }
                }
            }
        });
        add(titled(localList, Messages.get("git.branches.local")));
        add(titled(new JList<>(remoteModel), Messages.get("git.branches.remote")));
        add(titled(new JList<>(tagModel), Messages.get("git.branches.tags")));
    }

    private static JScrollPane titled(JList<String> list, String title) {
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createTitledBorder(title));
        return sp;
    }

    /** ブランチ・タグ一覧を背景で読み込み直す。 */
    void reload() {
        final GitRepoService svc = ctx.service();
        if (svc == null) {
            return;
        }
        final int g = ++gen;
        new SwingWorker<List<List<String>>, Void>() {
            @Override protected List<List<String>> doInBackground() throws Exception {
                return List.of(svc.localBranches(), svc.remoteBranches(), svc.tags());
            }

            @Override protected void done() {
                if (g != gen) {
                    return;
                }
                try {
                    List<List<String>> all = get();
                    fill(localModel, all.get(0));
                    fill(remoteModel, all.get(1));
                    fill(tagModel, all.get(2));
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    private static void fill(DefaultListModel<String> model, List<String> values) {
        model.clear();
        for (String v : values) {
            model.addElement(v);
        }
    }
}
