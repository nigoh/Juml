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
    private final CommitHeaderPanel header = new CommitHeaderPanel();
    private final JTextArea messageArea = new JTextArea(3, 40);
    private final DefaultListModel<FileChange> filesModel = new DefaultListModel<>();
    private final JList<FileChange> filesList = new JList<>(filesModel);
    private final GitDiffView diffArea = new GitDiffView();

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
        table.getColumnModel().getColumn(2).setCellRenderer(new AuthorCellRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new RelativeDateRenderer());
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
        filesList.setFixedCellHeight(22);
        filesList.setCellRenderer(new FileChangeCellRenderer());
        filesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedFileDiff();
            }
        });
        diffArea.setText(Messages.get("git.diff.hint"));

        JPanel detailTop = new JPanel(new BorderLayout(0, 2));
        detailTop.add(header, BorderLayout.NORTH);
        JScrollPane msgScroll = new JScrollPane(messageArea);
        msgScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        detailTop.add(msgScroll, BorderLayout.CENTER);
        JPanel detail = new JPanel(new BorderLayout(0, 4));
        detail.add(detailTop, BorderLayout.NORTH);
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
                    applyResult(get());
                    ctx.reportStatus(java.text.MessageFormat.format(
                            Messages.get("git.status.commitsLoaded"), commits.size(), ref));
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    /** 背景で読み込んだ履歴・レイアウト・ref をテーブルへ反映する (EDT で呼ぶ)。 */
    private void applyResult(LogResult result) {
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
        header.setCommit(null);
        messageArea.setText("");
        filesModel.clear();
        diffArea.setText(Messages.get("git.diff.hint"));
    }

    /**
     * テスト・スクリーンショット用に、SwingWorker を介さず同期でロードして先頭コミットを
     * 選択状態にする。EDT 上から呼ぶこと。本番コードからは使わない。
     */
    void loadForTest(GitRepoService svc, String ref) throws Exception {
        List<CommitInfo> log = svc.log(ref, GitRepoService.DEFAULT_LOG_LIMIT);
        Map<String, List<RefLabel>> decorations = svc.refDecorations();
        applyResult(new LogResult(log, GitGraphLayout.compute(log), decorations));
        if (commits.isEmpty()) {
            return;
        }
        CommitInfo c = commits.get(0);
        // 先に行選択して選択リスナー (detail 初期化) を走らせ、その後に中身を上書きする。
        table.setRowSelectionInterval(0, 0);
        header.setCommit(c);
        messageArea.setText(c.fullMessage);
        messageArea.setCaretPosition(0);
        filesModel.clear();
        List<FileChange> changes = svc.changesOf(c.sha);
        for (FileChange f : changes) {
            filesModel.addElement(f);
        }
        if (!changes.isEmpty()) {
            FileChange f = changes.get(0);
            String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
            diffArea.setDiff(svc.diffOf(c.sha, path));
        }
    }

    /** 選択コミットのメッセージと変更ファイル一覧を読み込む。 */
    private void showSelectedCommit() {
        CommitInfo c = selectedCommit();
        if (c == null) {
            return;
        }
        header.setCommit(c);
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
                    diffArea.setDiff(get());
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

    /** 「3 days ago」風の相対日時。近すぎる/未来は "just now"。 */
    private static String relativeTime(java.util.Date when) {
        if (when == null) {
            return "";
        }
        long secs = (System.currentTimeMillis() - when.getTime()) / 1000L;
        if (secs < 60) {
            return "just now";
        }
        long[] steps = {60, 3600, 86400, 604800, 2629746, 31556952};
        String[] unit = {"minute", "hour", "day", "week", "month", "year"};
        int i = 0;
        for (; i < steps.length - 1; i++) {
            if (secs < steps[i + 1]) {
                break;
            }
        }
        long n = secs / steps[i];
        return n + " " + unit[i] + (n == 1 ? "" : "s") + " ago";
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

    /** 作者列: 色付きイニシャル丸アバター + 名前 (GitKraken 風)。 */
    private final class AuthorCellRenderer extends DefaultTableCellRenderer {
        private final AvatarIcon icon = new AvatarIcon();

        @Override public Component getTableCellRendererComponent(
                JTable t, Object value, boolean isSelected, boolean hasFocus,
                int r, int column) {
            super.getTableCellRendererComponent(t, value, isSelected, hasFocus, r, column);
            int modelRow = t.convertRowIndexToModel(r);
            if (modelRow >= 0 && modelRow < commits.size()) {
                CommitInfo c = commits.get(modelRow);
                icon.name = c.author;
                icon.email = c.authorEmail;
                setIcon(icon);
                setIconTextGap(6);
            } else {
                setIcon(null);
            }
            return this;
        }
    }

    /** 丸アバターを描く Icon。行レンダラで使い回す (状態は描画直前に差し替え)。 */
    private static final class AvatarIcon implements javax.swing.Icon {
        private static final int SIZE = 16;
        private String name = "";
        private String email = "";

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            GitAvatars.paint((Graphics2D) g, x + SIZE / 2, y + SIZE / 2, SIZE / 2,
                    name, email);
        }

        @Override public int getIconWidth() {
            return SIZE;
        }

        @Override public int getIconHeight() {
            return SIZE;
        }
    }

    /** 日時列: 「3 days ago」風の相対表記 (絶対時刻はツールチップ)。 */
    private final class RelativeDateRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable t, Object value, boolean isSelected, boolean hasFocus,
                int r, int column) {
            super.getTableCellRendererComponent(t, value, isSelected, hasFocus, r, column);
            int modelRow = t.convertRowIndexToModel(r);
            if (modelRow >= 0 && modelRow < commits.size()) {
                setText(relativeTime(commits.get(modelRow).when));
                setToolTipText(value == null ? null : value.toString());
            }
            return this;
        }
    }

    /** 変更ファイル列: 状態レター (A/M/D/R/C) の色付きバッジ + パス (GitKraken 風)。 */
    private static final class FileChangeCellRenderer extends javax.swing.DefaultListCellRenderer {
        private final StatusBadgeIcon badge = new StatusBadgeIcon();

        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (!(value instanceof FileChange)) {
                return this;
            }
            FileChange fc = (FileChange) value;
            String path;
            if ("RENAME".equals(fc.changeType) || "COPY".equals(fc.changeType)) {
                path = fc.oldPath + "  →  " + fc.path;
            } else {
                path = "DELETE".equals(fc.changeType) ? fc.oldPath : fc.path;
            }
            setText(path);
            badge.letter = fc.changeType.isEmpty() ? "?" : fc.changeType.substring(0, 1);
            badge.color = statusColor(fc.changeType);
            setIcon(badge);
            setIconTextGap(8);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return this;
        }

        private static Color statusColor(String changeType) {
            switch (changeType) {
                case "ADD":    return new Color(0x00A36B);
                case "MODIFY": return new Color(0x2E9BFF);
                case "DELETE": return new Color(0xE0518A);
                case "RENAME": return new Color(0x9B59F0);
                case "COPY":   return new Color(0x18C0C4);
                default:       return Color.GRAY;
            }
        }
    }

    /** 変更種別レターの角丸バッジ。 */
    private static final class StatusBadgeIcon implements javax.swing.Icon {
        private static final int SIZE = 16;
        private String letter = "?";
        private Color color = Color.GRAY;

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillRoundRect(x, y, SIZE, SIZE, 5, 5);
            Font f = c.getFont().deriveFont(Font.BOLD, SIZE - 5f);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(letter);
            g2.setColor(Color.WHITE);
            g2.drawString(letter, x + (SIZE - tw) / 2,
                    y + (SIZE + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override public int getIconWidth() {
            return SIZE;
        }

        @Override public int getIconHeight() {
            return SIZE;
        }
    }

    /** 選択コミットのヘッダ: アバター + 作者、SHA・相対日時を強調表示する。 */
    private static final class CommitHeaderPanel extends JPanel {
        private CommitInfo commit;

        CommitHeaderPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(10, 46));
        }

        void setCommit(CommitInfo c) {
            this.commit = c;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (commit == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int h = getHeight();
            int r = 16;
            int cx = 6 + r;
            int cy = h / 2;
            GitAvatars.paint(g2, cx, cy, r, commit.author, commit.authorEmail);

            Color fg = javax.swing.UIManager.getColor("Label.foreground");
            Color muted = javax.swing.UIManager.getColor("Label.disabledForeground");
            if (fg == null) {
                fg = Color.DARK_GRAY;
            }
            if (muted == null) {
                muted = Color.GRAY;
            }
            int tx = cx + r + 10;
            Font bold = getFont().deriveFont(Font.BOLD);
            g2.setFont(bold);
            FontMetrics bfm = g2.getFontMetrics();
            g2.setColor(fg);
            g2.drawString(commit.author, tx, cy - 2);
            Font small = getFont().deriveFont(getFont().getSize2D() - 1f);
            g2.setFont(small);
            g2.setColor(muted);
            String line2 = commit.shortSha + "   ·   " + relativeTime(commit.when);
            g2.drawString(line2, tx, cy + bfm.getHeight() - 2);
            g2.dispose();
        }
    }
}
