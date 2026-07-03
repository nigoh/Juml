// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.ErrorCode;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * エラーコード一覧 (アプリ内リファレンス) のモードレスダイアログ。
 *
 * <p>クローズド環境 (外部ドキュメント参照不可) でも、画面に表示されたエラー ID から
 * 原因の要約と対処法をアプリ内だけで引けるようにする。ID・領域・要約・対処法の
 * 全文検索と、選択項目の詳細表示・クリップボードコピーに対応する。</p>
 *
 * <p>入口はヘルプメニュー / エラーカードの ID リンク / ログビューア /
 * コマンドパレットの 4 箇所。{@link #showFor(Window, String)} に ID を渡すと
 * 該当項目を選択した状態で開く。単一インスタンス方針。</p>
 */
final class ErrorReferenceDialog extends JDialog {

    /** 既に開いているダイアログ (なければ null)。多重起動を避ける。 */
    private static ErrorReferenceDialog current;

    private final JTextField search = new JTextField();
    private final CatalogTableModel model = new CatalogTableModel();
    private final JTable table = new JTable(model);
    private final JTextArea detail = new JTextArea();

    private ErrorReferenceDialog(Window owner) {
        super(owner, Messages.get("errref.title"), ModalityType.MODELESS);
        buildUi();
        reload();
        setSize(860, 540);
        setLocationRelativeTo(owner);
    }

    /**
     * リファレンスを開く (既に開いていれば前面化)。EDT から呼ぶこと。
     *
     * @param owner    親ウィンドウ (null 可)
     * @param presetId 開いた時点で選択するエラー ID (null 可)
     */
    static void showFor(Window owner, String presetId) {
        if (current == null || !current.isDisplayable()) {
            current = new ErrorReferenceDialog(owner);
        }
        current.setVisible(true);
        current.toFront();
        current.requestFocus();
        if (presetId != null && !presetId.isEmpty()) {
            current.selectId(presetId);
        }
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        bar.add(new JLabel(Messages.get("errref.search") + " "));
        search.setToolTipText(Messages.get("errref.search.tip"));
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                reload();
            }
        });
        bar.add(search);
        JButton copy = new JButton(Messages.get("errref.copy"));
        copy.setToolTipText(Messages.get("errref.copy.tip"));
        copy.addActionListener(e -> copyDetail());
        bar.add(copy);
        add(bar, BorderLayout.NORTH);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(Math.max(table.getRowHeight(), 20));
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(520);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedDetail();
            }
        });

        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, detail.getFont().getSize()));
        detail.setText(Messages.get("errref.detail.empty"));
        detail.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /** 検索テキストでフィルタして一覧を組み直す。 */
    private void reload() {
        String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
        List<ErrorCode> rows = new ArrayList<>();
        for (ErrorCode c : ErrorCode.values()) {
            if (!c.hasId()) {
                continue;
            }
            if (q.isEmpty() || matches(c, q)) {
                rows.add(c);
            }
        }
        model.setRows(rows);
        if (!rows.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        } else {
            detail.setText(Messages.get("errref.detail.empty"));
        }
    }

    private static boolean matches(ErrorCode c, String q) {
        return c.getId().toLowerCase(Locale.ROOT).contains(q)
                || c.getArea().displayName().toLowerCase(Locale.ROOT).contains(q)
                || c.summary().toLowerCase(Locale.ROOT).contains(q)
                || c.remedy().toLowerCase(Locale.ROOT).contains(q);
    }

    /** 指定 ID の行を選択して詳細を表示する (見つからなければ何もしない)。 */
    private void selectId(String id) {
        // 絞り込みで隠れていると選択できないため、まず検索をクリアする
        if (!search.getText().isEmpty()) {
            search.setText("");
        }
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getCode(i).getId().equalsIgnoreCase(id.trim())) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private void showSelectedDetail() {
        int row = table.getSelectedRow();
        if (row < 0) {
            detail.setText(Messages.get("errref.detail.empty"));
            return;
        }
        detail.setText(detailText(model.getCode(row)));
        detail.setCaretPosition(0);
    }

    /** 詳細ペイン / コピー用の整形テキスト。 */
    static String detailText(ErrorCode c) {
        return c.getId() + " (" + c.getArea().displayName() + ")\n\n"
                + Messages.get("errref.summaryLabel") + "\n  " + c.summary() + "\n\n"
                + Messages.get("errref.remedyLabel") + "\n  " + c.remedy();
    }

    private void copyDetail() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        java.awt.datatransfer.StringSelection sel =
                new java.awt.datatransfer.StringSelection(detailText(model.getCode(row)));
        getToolkit().getSystemClipboard().setContents(sel, sel);
    }

    @Override
    public void dispose() {
        if (current == this) {
            current = null;
        }
        super.dispose();
    }

    /** ヘッドレステスト用: 内部テーブル。 */
    JTable getTableForTest() {
        return table;
    }

    /** テスト用: 現在開いているインスタンス (なければ null)。 */
    static ErrorReferenceDialog currentForTest() {
        return current;
    }

    // ── テーブルモデル (ID / 領域 / 要約) ──────────────────────────────

    private static final class CatalogTableModel extends AbstractTableModel {
        private final String[] columns = {
            Messages.get("errref.col.id"),
            Messages.get("errref.col.area"),
            Messages.get("errref.col.summary"),
        };
        private List<ErrorCode> rows = new ArrayList<>();

        void setRows(List<ErrorCode> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        ErrorCode getCode(int row) {
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
            ErrorCode c = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return c.getId();
                case 1: return c.getArea().displayName();
                case 2: return c.summary();
                default: return "";
            }
        }
    }
}
