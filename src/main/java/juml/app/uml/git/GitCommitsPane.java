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
import java.awt.FlowLayout;
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
    private final SideBySideDiffView splitDiff = new SideBySideDiffView();
    private final java.awt.CardLayout diffCards = new java.awt.CardLayout();
    private final JPanel diffHost = new JPanel(diffCards);
    /** diff 表示モード: {@code "unified"} または {@code "split"}。 */
    private String diffMode = "unified";
    /**
     * 現在の比較コンテキスト。1 コミット選択時は {@code cmpOldRev=null}（= その親と比較）、
     * 2 コミット以上選択時は古い方の SHA を {@code cmpOldRev} に置く（選択同士で比較）。
     */
    private String cmpOldRev;
    private String cmpNewRev;
    private String cmpNewLabel;
    /** モード切替時の再描画用に、現在 diff 表示中のファイルを保持する。 */
    private FileChange diffFile;

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

        // 2 コミット選択で「選択同士」の比較を可能にする (1 選択時は親と比較)。
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
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
                onSelectionChanged();
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
        installFilesContextMenu();
        diffArea.setText(Messages.get("git.diff.hint"));
        diffHost.add(new JScrollPane(diffArea), "unified");
        diffHost.add(splitDiff, "split");

        JPanel detailTop = new JPanel(new BorderLayout(0, 2));
        detailTop.add(header, BorderLayout.NORTH);
        JScrollPane msgScroll = new JScrollPane(messageArea);
        msgScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        detailTop.add(msgScroll, BorderLayout.CENTER);
        JPanel detail = new JPanel(new BorderLayout(0, 4));
        detail.add(detailTop, BorderLayout.NORTH);
        detail.add(new JScrollPane(filesList), BorderLayout.CENTER);

        JPanel diffPanel = new JPanel(new BorderLayout());
        diffPanel.add(buildDiffToolbar(), BorderLayout.NORTH);
        diffPanel.add(diffHost, BorderLayout.CENTER);
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detail, diffPanel);
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
        cmpOldRev = null;
        cmpNewRev = null;
        diffFile = null;
        diffArea.setText(Messages.get("git.diff.hint"));
        splitDiff.setEmptyText(Messages.get("git.diff.hint"));
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
            diffFile = f;
            filesList.setSelectedIndex(0);
            if ("split".equals(diffMode)) {
                splitDiff.setRows(computeSplitRows(svc, c, f));
                diffCards.show(diffHost, "split");
            } else {
                String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
                diffArea.setDiff(svc.diffOf(c.sha, path));
            }
        }
    }

    /**
     * テスト・スクリーンショット用に、2 コミット (newerRow 新 / olderRow 旧) を選択した
     * 「選択同士」比較を同期でロードする。EDT 上から呼ぶこと。本番コードからは使わない。
     */
    void loadCompareForTest(GitRepoService svc, String ref, int newerRow, int olderRow)
            throws Exception {
        List<CommitInfo> log = svc.log(ref, GitRepoService.DEFAULT_LOG_LIMIT);
        Map<String, List<RefLabel>> decorations = svc.refDecorations();
        applyResult(new LogResult(log, GitGraphLayout.compute(log), decorations));
        if (commits.size() <= Math.max(newerRow, olderRow)) {
            return;
        }
        CommitInfo newer = commits.get(newerRow);
        CommitInfo older = commits.get(olderRow);
        selectCommitRowsForTest(newerRow, olderRow);
        cmpNewRev = newer.sha;
        cmpNewLabel = newer.shortSha;
        cmpOldRev = older.sha;
        header.setCompare(older, newer);
        messageArea.setText(older.shortSha + " → " + newer.shortSha + "\n\n" + newer.fullMessage);
        messageArea.setCaretPosition(0);
        filesModel.clear();
        List<FileChange> changes = svc.changesBetween(older.sha, newer.sha);
        for (FileChange f : changes) {
            filesModel.addElement(f);
        }
        if (!changes.isEmpty()) {
            FileChange f = changes.get(0);
            diffFile = f;
            filesList.setSelectedIndex(0);
            if ("split".equals(diffMode)) {
                splitDiff.setRows(computeSplitRows(svc, older.sha, newer.sha, f));
                diffCards.show(diffHost, "split");
            } else {
                String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
                diffArea.setDiff(svc.diffOf(older.sha, newer.sha, path));
            }
        }
    }

    /** テスト用に diff 表示モードを切り替える。 */
    void setDiffModeForTest(String mode) {
        this.diffMode = mode;
        diffCards.show(diffHost, mode);
    }

    /**
     * テスト用: {@code diffCards} (CardLayout) のうち split カードが表示されているかを返す。
     * unified/split はこの 2 枚しか無いため、split の可視状態だけで判定できる。
     * 本番ロジック (renderDiff/setDiffMode) には触れない、可視性の窓のみのシーム。
     */
    boolean isSplitVisibleForTest() {
        return splitDiff.isVisible();
    }

    /** テスト用: 現在 {@link SideBySideDiffView} に入っている行数を返す。 */
    int splitRowCountForTest() {
        return splitDiff.rowCountForTest();
    }

    /**
     * テスト用: 変更ファイル一覧から指定パスの要素を選択する (見つかれば true)。
     * filesList は private フィールドなので、内部走査だけをここに閉じ込める。
     */
    boolean selectFileForTest(String path) {
        for (int i = 0; i < filesModel.size(); i++) {
            FileChange f = filesModel.get(i);
            String p = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
            if (path.equals(p)) {
                filesList.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    /**
     * テスト用: 右クリックメニューの「UML Diff」項目相当を、Robot によるポップアップ操作
     * を介さず直接呼び出す。ロジックは {@link #openUmlDiffForSelectedFile()} と同一。
     */
    void openUmlDiffForTest() {
        openUmlDiffForSelectedFile();
    }

    /** テスト用: 指定した複数行を選択する (2 コミット比較の検証用)。 */
    void selectCommitRowsForTest(int... rows) {
        table.clearSelection();
        for (int r : rows) {
            table.addRowSelectionInterval(r, r);
        }
    }

    /** テスト用: 現在の比較コンテキスト (1 選択時 old は null = 親)。 */
    String cmpOldRevForTest() {
        return cmpOldRev;
    }

    String cmpNewRevForTest() {
        return cmpNewRev;
    }

    /**
     * コミット選択の変化に応じて比較コンテキストを組み立て、詳細と変更ファイルを更新する。
     * 1 コミット選択 = そのコミット vs 親、2 コミット以上選択 = 選択の最古 vs 最新で比較する。
     */
    private void onSelectionChanged() {
        List<CommitInfo> sel = selectedCommits();
        if (sel.isEmpty()) {
            header.setCommit(null);
            messageArea.setText("");
            filesModel.clear();
            resetDiff(Messages.get("git.diff.hint"));
            cmpNewRev = null;
            return;
        }
        // 履歴は新しい順。sel は行昇順なので先頭が最新、末尾が最古。
        CommitInfo newer = sel.get(0);
        CommitInfo older = sel.size() >= 2 ? sel.get(sel.size() - 1) : null;
        cmpNewRev = newer.sha;
        cmpNewLabel = newer.shortSha;
        cmpOldRev = older != null ? older.sha : null; // null なら親と比較
        if (older != null) {
            header.setCompare(older, newer);
            messageArea.setText(older.shortSha + " → " + newer.shortSha + "\n\n"
                    + newer.fullMessage);
        } else {
            header.setCommit(newer);
            messageArea.setText(newer.fullMessage);
        }
        messageArea.setCaretPosition(0);
        filesModel.clear();
        diffFile = null;
        resetDiff(Messages.get("git.diff.fileHint"));
        loadChangedFiles();
    }

    /** 現在の比較コンテキストで変更ファイル一覧を背景読み込みする。 */
    private void loadChangedFiles() {
        final GitRepoService svc = ctx.service();
        if (svc == null || cmpNewRev == null) {
            return;
        }
        final String oldRev = cmpOldRev;
        final String newRev = cmpNewRev;
        final int gen = ++detailGen;
        new SwingWorker<List<FileChange>, Void>() {
            @Override protected List<FileChange> doInBackground() throws Exception {
                String base = oldRev != null ? oldRev : svc.parentOf(newRev);
                return svc.changesBetween(base, newRev);
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

    private void resetDiff(String hint) {
        diffArea.setText(hint);
        splitDiff.setEmptyText(hint);
    }

    /** 変更ファイル一覧の右クリックメニュー (UML 構造 Diff 起動) を組み込む。 */
    private void installFilesContextMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem umlItem =
                new javax.swing.JMenuItem(Messages.get("git.file.umlDiff"));
        umlItem.setToolTipText(Messages.get("git.file.umlDiffTip"));
        umlItem.addActionListener(e -> openUmlDiffForSelectedFile());
        menu.add(umlItem);
        javax.swing.JMenuItem cmpItem =
                new javax.swing.JMenuItem(Messages.get("git.file.diagCompare"));
        cmpItem.setToolTipText(Messages.get("git.file.diagCompareTip"));
        cmpItem.addActionListener(e -> openDiagramCompareForSelectedFile());
        menu.add(cmpItem);
        javax.swing.JMenuItem actItem =
                new javax.swing.JMenuItem(Messages.get("git.file.actCompare"));
        actItem.setToolTipText(Messages.get("git.file.actCompareTip"));
        actItem.addActionListener(e -> openActivityCompareForSelectedFile());
        menu.add(actItem);
        filesList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                maybeShow(e);
            }

            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                maybeShow(e);
            }

            private void maybeShow(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int idx = filesList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    filesList.setSelectedIndex(idx);
                }
                FileChange f = filesList.getSelectedValue();
                boolean java = f != null && isJavaChange(f);
                umlItem.setEnabled(java);
                cmpItem.setEnabled(java);
                actItem.setEnabled(java);
                menu.show(filesList, e.getX(), e.getY());
            }
        });
    }

    private static boolean isJavaChange(FileChange f) {
        String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
        return path != null && path.endsWith(".java");
    }

    /**
     * 選択中の変更ファイル (.java) をクラス構造 UML 差分表示する。比較の 2 時点は現在の
     * 比較コンテキスト（1 コミット選択なら vs 親、2 コミット選択なら選択同士）に従う。
     * グラフ (Commits) から UML 比較画面へ直接つなぐ統合ポイント。
     */
    private void openUmlDiffForSelectedFile() {
        FileChange f = filesList.getSelectedValue();
        final GitRepoService svc = ctx.service();
        if (f == null || svc == null || cmpNewRev == null) {
            return;
        }
        if (!isJavaChange(f)) {
            ctx.reportStatus(Messages.get("git.umldiff.javaOnly"));
            return;
        }
        String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
        GitUmlDiffDialog dialog = new GitUmlDiffDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this),
                svc, path, cmpOldRev, cmpNewRev, cmpNewLabel);
        dialog.setVisible(true);
    }

    /**
     * 選択中の変更ファイル (.java) の旧・新クラス図を左右に並べて比較する
     * (変更ノードを図中で色付き表示)。比較の 2 時点は現在の比較コンテキストに従う。
     */
    private void openDiagramCompareForSelectedFile() {
        FileChange f = filesList.getSelectedValue();
        final GitRepoService svc = ctx.service();
        if (f == null || svc == null || cmpNewRev == null) {
            return;
        }
        if (!isJavaChange(f)) {
            ctx.reportStatus(Messages.get("git.umldiff.javaOnly"));
            return;
        }
        String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
        GitDiagramCompareDialog dialog = new GitDiagramCompareDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this),
                svc, path, cmpOldRev, cmpNewRev, cmpNewLabel);
        dialog.setVisible(true);
    }

    /** テスト用: 図の左右比較ダイアログを直接開く (右クリックメニュー相当)。 */
    void openDiagramCompareForTest() {
        openDiagramCompareForSelectedFile();
    }

    /**
     * 選択中の変更ファイル (.java) のメソッドを、旧・新のアクティビティ図で左右比較する
     * (関数の中身＝制御フローの変化を図中で色付き表示)。
     */
    private void openActivityCompareForSelectedFile() {
        FileChange f = filesList.getSelectedValue();
        final GitRepoService svc = ctx.service();
        if (f == null || svc == null || cmpNewRev == null) {
            return;
        }
        if (!isJavaChange(f)) {
            ctx.reportStatus(Messages.get("git.umldiff.javaOnly"));
            return;
        }
        String path = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
        GitActivityCompareDialog dialog = new GitActivityCompareDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this),
                svc, path, cmpOldRev, cmpNewRev, cmpNewLabel);
        dialog.setVisible(true);
    }

    /** テスト用: アクティビティ図の左右比較ダイアログを直接開く。 */
    void openActivityCompareForTest() {
        openActivityCompareForSelectedFile();
    }

    /** Unified / Side-by-side を切り替えるトグルバーを作る。 */
    private JPanel buildDiffToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        javax.swing.JToggleButton unified =
                new javax.swing.JToggleButton(Messages.get("git.diff.unified"), true);
        javax.swing.JToggleButton split =
                new javax.swing.JToggleButton(Messages.get("git.diff.split"));
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        group.add(unified);
        group.add(split);
        unified.addActionListener(e -> setDiffMode("unified"));
        split.addActionListener(e -> setDiffMode("split"));
        bar.add(unified);
        bar.add(split);
        return bar;
    }

    /** diff モードを切り替え、現在のファイルを新モードで描き直す。 */
    private void setDiffMode(String mode) {
        if (mode.equals(diffMode)) {
            return;
        }
        diffMode = mode;
        diffCards.show(diffHost, mode);
        if (diffFile != null) {
            renderDiff(diffFile);
        }
    }

    /** 選択ファイルの diff を現在のモードで読み込む。 */
    private void showSelectedFileDiff() {
        FileChange f = filesList.getSelectedValue();
        if (f == null) {
            return;
        }
        renderDiff(f);
    }

    /** 選択ファイルの diff を、現在の比較コンテキスト・モード (unified/split) で背景描画する。 */
    private void renderDiff(FileChange f) {
        this.diffFile = f;
        final GitRepoService svc = ctx.service();
        if (svc == null || cmpNewRev == null) {
            return;
        }
        final String oldRev = cmpOldRev;
        final String newRev = cmpNewRev;
        final boolean split = "split".equals(diffMode);
        final int gen = ++diffGen;
        new SwingWorker<Object, Void>() {
            @Override protected Object doInBackground() throws Exception {
                String base = oldRev != null ? oldRev : svc.parentOf(newRev);
                return split ? computeSplitRows(svc, base, newRev, f)
                        : svc.diffOf(base, newRev,
                                "DELETE".equals(f.changeType) ? f.oldPath : f.path);
            }

            @Override @SuppressWarnings("unchecked") protected void done() {
                if (gen != diffGen) {
                    return;
                }
                try {
                    Object result = get();
                    if (split) {
                        splitDiff.setRows((List<LineDiff.Row>) result);
                    } else {
                        diffArea.setDiff((String) result);
                    }
                } catch (Exception ex) {
                    ctx.reportStatus(Messages.get("git.status.failed") + ex.getMessage());
                }
            }
        }.execute();
    }

    private CommitInfo selectedCommit() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        // モデル行へ変換してから境界検査する。ビュー行で検査してモデル行で参照すると、
        // 行ソータを有効にした瞬間に別コミットを返す/IndexOutOfBounds になる。
        int model = table.convertRowIndexToModel(row);
        if (model < 0 || model >= commits.size()) {
            return null;
        }
        return commits.get(model);
    }

    /** side-by-side 用に、1 コミット選択 (vs 親) のファイルの旧/新内容を整列する。 */
    static List<LineDiff.Row> computeSplitRows(
            GitRepoService svc, CommitInfo c, FileChange f) throws java.io.IOException {
        return computeSplitRows(svc, svc.parentOf(c.sha), c.sha, f);
    }

    /** side-by-side 用に、任意 2 リビジョン間でのファイルの旧/新内容を取得して整列する。 */
    static List<LineDiff.Row> computeSplitRows(GitRepoService svc, String oldRev,
            String newRev, FileChange f) throws java.io.IOException {
        String newPath = "DELETE".equals(f.changeType) ? f.oldPath : f.path;
        String oldPath = "RENAME".equals(f.changeType) || "COPY".equals(f.changeType)
                ? f.oldPath : newPath;
        String oldSrc = oldRev != null ? svc.fileContentAt(oldRev, oldPath) : null;
        String newSrc = svc.fileContentAt(newRev, newPath);
        return LineDiff.compute(oldSrc, newSrc);
    }

    /** 選択中のコミットを行昇順 (= 新しい順) で返す。 */
    private List<CommitInfo> selectedCommits() {
        List<CommitInfo> out = new java.util.ArrayList<>();
        for (int row : table.getSelectedRows()) {
            int m = table.convertRowIndexToModel(row);
            if (m >= 0 && m < commits.size()) {
                out.add(commits.get(m));
            }
        }
        return out;
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
                setText(GitTimes.relative(commits.get(modelRow).when));
                setToolTipText(value == null ? null : value.toString());
            }
            return this;
        }
    }

}
