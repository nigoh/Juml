// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.CommitInfo;
import juml.app.uml.git.GitRepoService.FileChange;
import juml.app.uml.git.GitRepoService.RefLabel;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * git 閲覧タブの「Commits」サブペイン: コミット履歴を GitKraken 風のレーングラフで描き、
 * 選択コミットのメッセージ / 変更ファイル / unified diff を表示する (読み取り専用)。
 *
 * <p>左側の履歴テーブルは {@link GitGraphLayout} が計算した DAG レイアウトを自作レンダラ
 * で描画する (カラフルな曲線レーン + コミットノード + ブランチ/タグの ref ピル)。右側の
 * 詳細・diff ペインは従来通り。</p>
 */
final class GitCommitsPane extends JPanel {

    /** レーン配色 (GitKraken 風。明暗どちらのテーマでも視認できる鮮やかさ)。 */
    private static final Color[] LANE_COLORS = {
            new Color(0x2E9BFF), new Color(0x00C389), new Color(0xF5A623),
            new Color(0xE0518A), new Color(0x9B59F0), new Color(0x18C0C4),
            new Color(0xF2564B), new Color(0x8BC34A),
    };
    /** レーン 1 本分の横幅 (px)。 */
    private static final int LANE_W = 16;
    /** グラフ列の左パディング (px)。 */
    private static final int GRAPH_PAD = 8;
    /** コミットノードの半径 (px)。 */
    private static final int NODE_R = 4;
    /** 行の高さ (px)。ノードとピルが収まる程度に高めにとる。 */
    private static final int ROW_H = 24;

    private final GitPanel.GitContext ctx;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{
                    "", Messages.get("git.col.message"),
                    Messages.get("git.col.author"), Messages.get("git.col.date"),
                    Messages.get("git.col.sha")}, 0) {
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
    /** 履歴に対応するグラフレイアウト (行数は commits と一致)。 */
    private List<GitGraphLayout.Row> graphRows = List.of();
    /** 完全 SHA → ref ラベル。 */
    private Map<String, List<RefLabel>> refs = Map.of();
    /** 古い SwingWorker 結果で UI を上書きしない世代カウンタ。 */
    private int logGen;
    private int detailGen;
    private int diffGen;

    GitCommitsPane(GitPanel.GitContext ctx) {
        super(new BorderLayout());
        this.ctx = ctx;

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(ROW_H);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(LANE_W * 3 + GRAPH_PAD * 2);
        table.getColumnModel().getColumn(1).setPreferredWidth(440);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(0).setCellRenderer(new GraphCellRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new MessageCellRenderer());
        DefaultTableCellRenderer sha = new DefaultTableCellRenderer();
        sha.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getColumnModel().getColumn(4).setCellRenderer(sha);
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
        new SwingWorker<LogResult, Void>() {
            @Override protected LogResult doInBackground() throws Exception {
                List<CommitInfo> log = svc.log(ref, GitRepoService.DEFAULT_LOG_LIMIT);
                Map<String, List<RefLabel>> decorations = svc.refDecorations();
                GitGraphLayout layout = GitGraphLayout.compute(log);
                return new LogResult(log, layout, decorations);
            }

            @Override protected void done() {
                if (gen != logGen) {
                    return;
                }
                try {
                    LogResult result = get();
                    commits = result.commits;
                    graphRows = result.layout.rows();
                    refs = result.refs;
                    int graphW = result.layout.laneCount() * LANE_W + GRAPH_PAD * 2;
                    table.getColumnModel().getColumn(0).setPreferredWidth(graphW);
                    table.getColumnModel().getColumn(0).setMinWidth(graphW);
                    model.setRowCount(0);
                    for (CommitInfo c : commits) {
                        model.addRow(new Object[]{"", c.shortMessage,
                                c.author, GitPanel.formatDate(c.when), c.shortSha});
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

    private static Color laneColor(int colorIndex) {
        return LANE_COLORS[Math.floorMod(colorIndex, LANE_COLORS.length)];
    }

    /** 履歴・レイアウト・ref をまとめて EDT へ渡す背景処理の戻り値。 */
    private static final class LogResult {
        final List<CommitInfo> commits;
        final GitGraphLayout layout;
        final Map<String, List<RefLabel>> refs;

        LogResult(List<CommitInfo> commits, GitGraphLayout layout,
                  Map<String, List<RefLabel>> refs) {
            this.commits = commits;
            this.layout = layout;
            this.refs = refs;
        }
    }

    /** グラフ列 (レーン + ノード) を自作描画するレンダラ。 */
    private final class GraphCellRenderer extends JPanel implements TableCellRenderer {
        private int row;
        private boolean selected;

        GraphCellRenderer() {
            setOpaque(true);
        }

        @Override public Component getTableCellRendererComponent(
                JTable t, Object value, boolean isSelected, boolean hasFocus,
                int r, int column) {
            this.row = r;
            this.selected = isSelected;
            setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (row < 0 || row >= graphRows.size()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            GitGraphLayout.Row gr = graphRows.get(row);
            int nodeX = colX(gr.nodeColumn);
            int midY = h / 2;
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (GitGraphLayout.Edge e : gr.edges) {
                int x1 = e.topColumn < 0 ? nodeX : colX(e.topColumn);
                int y1 = e.topColumn < 0 ? midY : 0;
                int x2 = e.bottomColumn < 0 ? nodeX : colX(e.bottomColumn);
                int y2 = e.bottomColumn < 0 ? midY : h;
                g2.setColor(laneColor(e.colorIndex));
                drawEdge(g2, x1, y1, x2, y2);
            }
            // ノード (コミットの丸)。選択時は少し大きく縁取る。
            Color nodeColor = laneColor(gr.colorIndex);
            int r = selected ? NODE_R + 1 : NODE_R;
            g2.setColor(nodeColor);
            g2.fillOval(nodeX - r, midY - r, r * 2, r * 2);
            g2.setColor(getBackground());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(nodeX - r, midY - r, r * 2, r * 2);
            g2.dispose();
        }

        private void drawEdge(Graphics2D g2, int x1, int y1, int x2, int y2) {
            if (x1 == x2) {
                g2.drawLine(x1, y1, x2, y2);
            } else {
                double cy = (y1 + y2) / 2.0;
                g2.draw(new CubicCurve2D.Double(x1, y1, x1, cy, x2, cy, x2, y2));
            }
        }

        private int colX(int col) {
            return GRAPH_PAD + col * LANE_W + LANE_W / 2;
        }
    }

    /** メッセージ列 (ref ピル + コミットメッセージ) を自作描画するレンダラ。 */
    private final class MessageCellRenderer extends JPanel implements TableCellRenderer {
        private String message = "";
        private List<RefLabel> rowRefs = Collections.emptyList();
        private Color fg = Color.BLACK;

        MessageCellRenderer() {
            setOpaque(true);
        }

        @Override public Component getTableCellRendererComponent(
                JTable t, Object value, boolean isSelected, boolean hasFocus,
                int r, int column) {
            this.message = value == null ? "" : value.toString();
            this.fg = isSelected ? t.getSelectionForeground() : t.getForeground();
            setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
            setFont(t.getFont());
            int modelRow = r < commits.size() ? r : -1;
            rowRefs = modelRow >= 0
                    ? refs.getOrDefault(commits.get(modelRow).sha, Collections.emptyList())
                    : Collections.emptyList();
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int h = getHeight();
            int x = 4;
            Font pillFont = getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 1f);
            FontMetrics pfm = g2.getFontMetrics(pillFont);
            for (RefLabel ref : rowRefs) {
                x = paintPill(g2, ref, x, h, pillFont, pfm) + 4;
            }
            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(message, x, baseline);
            g2.dispose();
        }

        /** ref ピルを 1 個描いて次の x 座標を返す。 */
        private int paintPill(Graphics2D g2, RefLabel ref, int x, int h,
                              Font pillFont, FontMetrics pfm) {
            g2.setFont(pillFont);
            int textW = pfm.stringWidth(ref.name);
            int padX = 6;
            int pillH = h - 8;
            int pillW = textW + padX * 2;
            int y = (h - pillH) / 2;
            Color base = pillColor(ref.type);
            boolean filled = ref.type == RefLabel.Type.HEAD;
            if (filled) {
                g2.setColor(base);
                g2.fillRoundRect(x, y, pillW, pillH, 10, 10);
                g2.setColor(Color.WHITE);
            } else {
                g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 40));
                g2.fillRoundRect(x, y, pillW, pillH, 10, 10);
                g2.setColor(base);
                g2.drawRoundRect(x, y, pillW, pillH, 10, 10);
            }
            int baseline = y + (pillH - pfm.getHeight()) / 2 + pfm.getAscent();
            g2.drawString(ref.name, x + padX, baseline);
            return x + pillW;
        }

        private Color pillColor(RefLabel.Type type) {
            switch (type) {
                case HEAD:   return new Color(0x2E9BFF);
                case LOCAL:  return new Color(0x00A36B);
                case REMOTE: return new Color(0x9B59F0);
                case TAG:    return new Color(0xE08A00);
                default:     return Color.GRAY;
            }
        }
    }
}
