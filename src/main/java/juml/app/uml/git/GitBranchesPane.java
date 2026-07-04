// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.RefRow;
import juml.util.Messages;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * git 閲覧タブの「Branches &amp; Tags」サブペイン。GitKraken 風に、ローカル / リモート /
 * タグをセクション分けした 1 本のツリーで表示する。各行は先頭コミットの作者アバター・
 * 相対日時・要約を添え、現在チェックアウト中のブランチを強調し、上流に対する ahead/behind
 * (↑/↓) を出す。ローカルブランチのダブルクリックで履歴表示の基準 ref を切り替える。
 */
final class GitBranchesPane extends JPanel {

    private final GitPanel.GitContext ctx;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model);
    /** 古い SwingWorker 結果で UI を上書きしない世代カウンタ。 */
    private int gen;

    /** セクション見出し (件数付き)。 */
    private static final class Section {
        final String title;
        final int count;

        Section(String title, int count) {
            this.title = title;
            this.count = count;
        }
    }

    GitBranchesPane(GitPanel.GitContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.setCellRenderer(new RowRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    RefRow row = selectedRow();
                    // ローカルブランチのみ基準 ref 切替に対応 (コンボの項目と一致するため)。
                    if (row != null && row.type == GitRepoService.RefLabel.Type.LOCAL) {
                        ctx.selectRef(row.name);
                    }
                }
            }
        });
        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    private RefRow selectedRow() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return obj instanceof RefRow ? (RefRow) obj : null;
    }

    /** ブランチ・タグ一覧を背景で読み込み直してツリーを組み直す。 */
    void reload() {
        final GitRepoService svc = ctx.service();
        if (svc == null) {
            return;
        }
        final int g = ++gen;
        new SwingWorker<List<List<RefRow>>, Void>() {
            @Override protected List<List<RefRow>> doInBackground() throws Exception {
                return List.of(svc.localBranchRows(), svc.remoteBranchRows(), svc.tagRows());
            }

            @Override protected void done() {
                if (g != gen) {
                    return;
                }
                try {
                    List<List<RefRow>> all = get();
                    root.removeAllChildren();
                    addSection(Messages.get("git.branches.local"), all.get(0));
                    addSection(Messages.get("git.branches.remote"), all.get(1));
                    addSection(Messages.get("git.branches.tags"), all.get(2));
                    model.reload();
                    expandAll();
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    /** テスト・スクリーンショット用に、SwingWorker を介さず同期でツリーを組む。 */
    void loadForTest(GitRepoService svc) throws Exception {
        root.removeAllChildren();
        addSection(Messages.get("git.branches.local"), svc.localBranchRows());
        addSection(Messages.get("git.branches.remote"), svc.remoteBranchRows());
        addSection(Messages.get("git.branches.tags"), svc.tagRows());
        model.reload();
        expandAll();
    }

    /** テスト用: 構築したツリーモデル (セクション → RefRow) を覗く。 */
    javax.swing.tree.TreeModel treeModelForTest() {
        return model;
    }

    private void addSection(String title, List<RefRow> rows) {
        DefaultMutableTreeNode section =
                new DefaultMutableTreeNode(new Section(title, rows.size()));
        for (RefRow r : rows) {
            section.add(new DefaultMutableTreeNode(r));
        }
        root.add(section);
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    /** セクション見出しと ref 行を GitKraken 風に描くツリーレンダラ。 */
    private static final class RowRenderer extends JPanel implements TreeCellRenderer {
        private Object value;
        private boolean selected;
        private Color fg = Color.BLACK;
        private Color muted = Color.GRAY;
        private Color selBg;

        RowRenderer() {
            setOpaque(true);
        }

        @Override public Component getTreeCellRendererComponent(
                JTree t, Object node, boolean sel, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            this.value = ((DefaultMutableTreeNode) node).getUserObject();
            this.selected = sel;
            this.fg = orDefault("Tree.textForeground", Color.BLACK);
            this.muted = orDefault("Label.disabledForeground", Color.GRAY);
            this.selBg = orDefault("Tree.selectionBackground", new Color(0x2E9BFF));
            setBackground(sel ? selBg : orDefault("Tree.textBackground", Color.WHITE));
            setFont(t.getFont());
            return this;
        }

        private static Color orDefault(String key, Color fallback) {
            Color c = javax.swing.UIManager.getColor(key);
            return c != null ? c : fallback;
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(320, 24);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int h = getHeight();
            if (value instanceof Section) {
                Section s = (Section) value;
                g2.setFont(getFont().deriveFont(Font.BOLD));
                g2.setColor(selected ? textOnSel() : muted);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(s.title + "  (" + s.count + ")", 4, baseline(h, fm));
            } else if (value instanceof RefRow) {
                paintRow(g2, (RefRow) value, h);
            }
            g2.dispose();
        }

        private void paintRow(Graphics2D g2, RefRow r, int h) {
            Color base = selected ? textOnSel() : fg;
            int x = 4;
            int cy = h / 2;
            GitAvatars.paint(g2, x + 8, cy, 8, r.tip != null ? r.tip.author : "", "");
            x += 20;

            Font nameFont = getFont().deriveFont(r.current ? Font.BOLD : Font.PLAIN);
            g2.setFont(nameFont);
            FontMetrics nfm = g2.getFontMetrics();
            g2.setColor(r.current ? currentColor() : base);
            int by = baseline(h, nfm);
            g2.drawString(r.name, x, by);
            x += nfm.stringWidth(r.name) + 8;

            Font small = getFont().deriveFont(getFont().getSize2D() - 1f);
            g2.setFont(small);
            FontMetrics sfm = g2.getFontMetrics();
            if (r.ahead > 0) {
                g2.setColor(selected ? textOnSel() : new Color(0x1A7F37));
                g2.drawString("↑" + r.ahead, x, by);
                x += sfm.stringWidth("↑" + r.ahead) + 4;
            }
            if (r.behind > 0) {
                g2.setColor(selected ? textOnSel() : new Color(0xCF222E));
                g2.drawString("↓" + r.behind, x, by);
                x += sfm.stringWidth("↓" + r.behind) + 4;
            }
            String suffix = summary(r);
            if (!suffix.isEmpty()) {
                g2.setColor(selected ? textOnSel() : muted);
                g2.drawString(suffix, x, by);
            }
        }

        private static String summary(RefRow r) {
            if (r.tip == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder("  ");
            if (r.tip.when != null) {
                sb.append(GitTimes.relative(r.tip.when)).append("  ·  ");
            }
            if (r.tip.message != null) {
                sb.append(r.tip.message);
            }
            return sb.toString();
        }

        private Color currentColor() {
            return selected ? textOnSel() : new Color(0x2E9BFF);
        }

        private Color textOnSel() {
            Color c = javax.swing.UIManager.getColor("Tree.selectionForeground");
            return c != null ? c : Color.WHITE;
        }

        private static int baseline(int h, FontMetrics fm) {
            return (h - fm.getHeight()) / 2 + fm.getAscent();
        }
    }
}
