// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.AppLog;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * アプリのエラーログ (AppLog) を閲覧するモードレスダイアログ。
 *
 * <p>{@link AppLog} のリングバッファを一覧表示し、開いている間は新着ログをライブ追記する。
 * レベルでの絞り込み、行選択でのスタックトレース表示、クリップボードコピー、
 * テキストファイル保存に対応する。図の操作を妨げないようモードレスで開く。</p>
 *
 * <p>同時に複数開かない単一インスタンス方針。{@link #showFor(Frame)} で開く / 前面化する。</p>
 */
final class LogViewerDialog extends JDialog {

    /** 既に開いているビューア (なければ null)。多重起動を避ける。 */
    private static LogViewerDialog current;

    private final LogTableModel model = new LogTableModel();
    private final JTable table = new JTable(model);
    private final JTextArea detail = new JTextArea();
    private final JCheckBox autoScroll = new JCheckBox(Messages.get("log.autoscroll"), true);
    private final JComboBox<LevelFilter> levelFilter = new JComboBox<>(LevelFilter.values());
    private final AppLog.Listener listener = this::onLogLive;

    /**
     * {@link #reload()} 時点で取り込み済みの最大連番。これ以下の連番のライブ通知は
     * 既に snapshot で表示済みなので無視する (生成直後の二重表示を防ぐ)。
     */
    private long reloadHighWater = -1;

    private LogViewerDialog(Frame owner) {
        super(owner, Messages.get("log.title"), false);
        buildUi();
        AppLog.addListener(listener);
        reload();
        setSize(820, 520);
        setLocationRelativeTo(owner);
    }

    /** ログビューアを開く (既に開いていれば前面化する)。EDT から呼ぶこと。 */
    static void showFor(Frame owner) {
        if (current != null && current.isDisplayable()) {
            current.toFront();
            current.requestFocus();
            return;
        }
        current = new LogViewerDialog(owner);
        current.setVisible(true);
    }

    private void buildUi() {
        setLayout(new BorderLayout());
        add(buildToolBar(), BorderLayout.NORTH);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(table.getRowHeight(), 20));
        table.setDefaultRenderer(Object.class, new LevelRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedDetail();
            }
        });
        configureColumns();

        detail.setEditable(false);
        detail.setLineWrap(false);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, detail.getFont().getSize()));
        detail.setText(Messages.get("log.detail.empty"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.72);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        bar.add(new JLabel(Messages.get("log.filter") + " "));
        levelFilter.addActionListener(e -> reload());
        levelFilter.setMaximumSize(levelFilter.getPreferredSize());
        bar.add(levelFilter);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(autoScroll);
        bar.add(Box.createHorizontalGlue());

        bar.add(makeButton("log.refresh", this::reload));
        bar.add(makeButton("log.copy", this::copyAll));
        bar.add(makeButton("log.save", this::saveToFile));
        bar.add(makeButton("log.openFile", this::openLogFile));
        bar.add(makeButton("log.clear", this::clearLog));
        return bar;
    }

    private JButton makeButton(String key, Runnable action) {
        JButton b = new JButton(Messages.get(key));
        String tip = Messages.getOrNull(key + ".tip");
        if (tip != null) {
            b.setToolTipText(tip);
        }
        b.addActionListener(e -> action.run());
        return b;
    }

    private void configureColumns() {
        int[] widths = {110, 64, 130, 480};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setPreferredWidth(widths[i]);
        }
    }

    /** リングバッファを取り直してフィルタを適用し、テーブルを再構築する。 */
    private void reload() {
        LevelFilter filter = (LevelFilter) levelFilter.getSelectedItem();
        List<AppLog.Entry> filtered = new ArrayList<>();
        long highWater = reloadHighWater;
        for (AppLog.Entry e : AppLog.snapshot()) {
            highWater = Math.max(highWater, e.getSeq());
            if (filter == null || filter.accepts(e.getLevel())) {
                filtered.add(e);
            }
        }
        reloadHighWater = highWater;
        model.setRows(filtered);
        scrollToBottomIfNeeded();
    }

    /** 新着ログを EDT で 1 件追記する (フィルタ通過 + reload 未取り込みのみ)。 */
    private void onLogLive(AppLog.Entry entry) {
        SwingUtilities.invokeLater(() -> {
            // reload の snapshot で既に表示済みの連番はライブ追記しない (二重表示防止)。
            if (entry.getSeq() <= reloadHighWater) {
                return;
            }
            LevelFilter filter = (LevelFilter) levelFilter.getSelectedItem();
            if (filter != null && !filter.accepts(entry.getLevel())) {
                return;
            }
            model.addRow(entry);
            scrollToBottomIfNeeded();
        });
    }

    private void scrollToBottomIfNeeded() {
        if (!autoScroll.isSelected() || model.getRowCount() == 0) {
            return;
        }
        int last = model.getRowCount() - 1;
        table.scrollRectToVisible(table.getCellRect(last, 0, true));
    }

    private void showSelectedDetail() {
        int row = table.getSelectedRow();
        if (row < 0) {
            detail.setText(Messages.get("log.detail.empty"));
            return;
        }
        AppLog.Entry e = model.getEntry(row);
        StringBuilder sb = new StringBuilder();
        sb.append(e.formatLine());
        if (e.getDetail() != null) {
            sb.append('\n').append(e.getDetail());
        }
        detail.setText(sb.toString());
        detail.setCaretPosition(0);
    }

    private void copyAll() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.getRowCount(); i++) {
            AppLog.Entry e = model.getEntry(i);
            sb.append(e.formatLine()).append('\n');
            if (e.getDetail() != null) {
                sb.append(e.getDetail());
            }
        }
        java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(sb.toString());
        getToolkit().getSystemClipboard().setContents(sel, sel);
    }

    private void clearLog() {
        // 誤クリックでログが即消えて取り返せないため、実行前に確認する。
        int answer = javax.swing.JOptionPane.showConfirmDialog(
                this,
                Messages.get("log.clear.confirm"),
                Messages.get("log.clear.confirmTitle"),
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        AppLog.clearBuffer();
        model.setRows(new ArrayList<>());
        detail.setText(Messages.get("log.detail.empty"));
    }

    private void saveToFile() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setSelectedFile(new File("juml-log.txt"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = fc.getSelectedFile();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.getRowCount(); i++) {
            AppLog.Entry e = model.getEntry(i);
            sb.append(e.formatLine()).append('\n');
            if (e.getDetail() != null) {
                sb.append(e.getDetail());
            }
        }
        try {
            Files.write(target.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            javax.swing.JOptionPane.showMessageDialog(this,
                    Messages.get("log.saved") + "\n" + target.getAbsolutePath(),
                    Messages.get("log.title"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    Messages.get("log.saveFailed") + "\n" + ex.getMessage(),
                    Messages.get("log.title"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /** OS の関連付けでログファイルを開く (Desktop 非対応時はパスを通知する)。 */
    private void openLogFile() {
        File f = AppLog.getLogFile();
        if (f == null || !f.exists()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    Messages.get("log.noFile"), Messages.get("log.title"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(
                            java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(f);
                return;
            }
        } catch (IOException | RuntimeException ex) {
            AppLog.warn(juml.util.ErrorCode.SYS_003, "LogViewerDialog",
                    "Desktop.open() for log file failed; showing path instead", ex);
        }
        javax.swing.JOptionPane.showMessageDialog(this,
                f.getAbsolutePath(), Messages.get("log.title"),
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void dispose() {
        AppLog.removeListener(listener);
        if (current == this) {
            current = null;
        }
        super.dispose();
    }

    /** ヘッドレステスト等から内部テーブルへアクセスするためのフック。 */
    JTable getTableForTest() {
        return table;
    }

    /** テスト用: 現在開いているインスタンス (なければ null)。 */
    static LogViewerDialog currentForTest() {
        return current;
    }

    // ── レベルフィルタ ─────────────────────────────────────────────────

    /** レベルしきい値での絞り込み。{@code accepts} はそのレベル以上を通す。 */
    private enum LevelFilter {
        ALL(null),
        INFO(AppLog.Level.INFO),
        WARN(AppLog.Level.WARN),
        ERROR(AppLog.Level.ERROR);

        private final AppLog.Level threshold;

        LevelFilter(AppLog.Level threshold) {
            this.threshold = threshold;
        }

        boolean accepts(AppLog.Level level) {
            return threshold == null || level.ordinal() >= threshold.ordinal();
        }

        @Override
        public String toString() {
            return this == ALL ? Messages.get("log.filter.all") : name();
        }
    }

    // ── テーブルモデル ─────────────────────────────────────────────────

    /** AppLog エントリのテーブルモデル (時刻 / レベル / スレッド / メッセージ)。 */
    private static final class LogTableModel extends AbstractTableModel {
        private final String[] columns = {
            Messages.get("log.col.time"),
            Messages.get("log.col.level"),
            Messages.get("log.col.thread"),
            Messages.get("log.col.message"),
        };
        private List<AppLog.Entry> rows = new ArrayList<>();

        void setRows(List<AppLog.Entry> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        void addRow(AppLog.Entry e) {
            rows.add(e);
            int idx = rows.size() - 1;
            fireTableRowsInserted(idx, idx);
        }

        AppLog.Entry getEntry(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            AppLog.Entry e = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return e.formatTime();
                case 1: return e.getLevel().name();
                case 2: return e.getThread();
                case 3: return e.getMessage();
                default: return "";
            }
        }
    }

    // ── 行レンダラ (レベルで色分け) ────────────────────────────────────

    private static final class LevelRenderer extends DefaultTableCellRenderer {
        private static final Color ERROR_FG = new Color(0xD32F2F);
        private static final Color WARN_FG = new Color(0xB8860B);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    t, value, isSelected, hasFocus, row, column);
            if (isSelected) {
                return c;
            }
            LogTableModel m = (LogTableModel) t.getModel();
            int modelRow = t.convertRowIndexToModel(row);
            if (modelRow < 0 || modelRow >= m.getRowCount()) {
                return c;
            }
            switch (m.getEntry(modelRow).getLevel()) {
                case ERROR: c.setForeground(ERROR_FG); break;
                case WARN:  c.setForeground(WARN_FG); break;
                default:    c.setForeground(t.getForeground()); break;
            }
            return c;
        }
    }
}
