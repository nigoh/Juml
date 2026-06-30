// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * 付箋メモの Markdown とタグを編集するモーダルダイアログ。
 *
 * <p>左に Markdown ソース ({@link JTextArea})、右にライブプレビュー
 * ({@link JEditorPane} で {@link MarkdownRenderer} 出力を表示) を並べ、下部にタグ
 * 入力欄を置く。OK で確定した {@link Result} を返し、Cancel なら {@code null} を返す。</p>
 */
final class NoteEditDialog {

    private NoteEditDialog() {
    }

    /** 編集結果 (本文 + タグ)。 */
    static final class Result {
        final String text;
        final java.util.List<String> tags;

        Result(String text, java.util.List<String> tags) {
            this.text = text;
            this.tags = tags;
        }
    }

    /** カンマ / 空白区切りのタグ文字列を分解する。 */
    static java.util.List<String> parseTags(String raw) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw != null) {
            for (String part : raw.split("[,\\s]+")) {
                String v = part.trim();
                if (!v.isEmpty() && !out.contains(v)) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    /**
     * 編集ダイアログを開く。
     *
     * @return 確定した本文 + タグ。キャンセル時は {@code null}。
     */
    static Result edit(Component parent, String initial, java.util.List<String> initialTags) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog = new JDialog(owner, Messages.get("note.edit.title"),
                JDialog.ModalityType.APPLICATION_MODAL);

        JTextArea editor = new JTextArea(initial == null ? "" : initial);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);

        JEditorPane preview = new JEditorPane();
        preview.setEditable(false);
        preview.setContentType("text/html");

        Runnable refresh = () -> preview.setText(wrap(MarkdownRenderer.toHtml(editor.getText())));
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh.run(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh.run(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh.run(); }
        });
        refresh.run();

        JPanel editorPane = titled(Messages.get("note.edit.markdown"),
                new JScrollPane(editor));
        JPanel previewPane = titled(Messages.get("note.edit.preview"),
                new JScrollPane(preview));
        JPanel split = new JPanel(new GridLayout(1, 2, 6, 0));
        split.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        split.add(editorPane);
        split.add(previewPane);

        // タグ入力欄 (カンマ / 空白区切り)。
        javax.swing.JTextField tagsField = new javax.swing.JTextField(
                initialTags == null ? "" : String.join(", ", initialTags));
        JPanel tagsRow = new JPanel(new BorderLayout(6, 0));
        tagsRow.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        tagsRow.add(new JLabel(Messages.get("note.edit.tags")), BorderLayout.WEST);
        tagsRow.add(tagsField, BorderLayout.CENTER);

        final Result[] result = {null};
        JButton ok = new JButton(Messages.get("note.edit.ok"));
        ok.addActionListener(e -> {
            result[0] = new Result(editor.getText(), parseTags(tagsField.getText()));
            dialog.dispose();
        });
        JButton cancel = new JButton(Messages.get("note.edit.cancel"));
        cancel.addActionListener(e -> dialog.dispose());
        JLabel hint = new JLabel(Messages.get("note.edit.hint"));
        java.awt.Color hintFg = javax.swing.UIManager.getColor("Label.disabledForeground");
        hint.setForeground(hintFg != null ? hintFg : new java.awt.Color(0x595959));
        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.add(hint, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        buttons.add(cancel);
        buttons.add(ok);
        buttonRow.add(buttons, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.add(tagsRow, BorderLayout.NORTH);
        south.add(buttonRow, BorderLayout.SOUTH);

        dialog.getContentPane().add(split, BorderLayout.CENTER);
        dialog.getContentPane().add(south, BorderLayout.SOUTH);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        // Esc=キャンセル / Enter=既定(OK) を共通ユーティリティで統一。
        DialogUtils.installEscapeAndDefault(dialog, ok);
        // Ctrl+Enter で確定 (JTextArea 内では素の Enter が改行になるため)。
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "ok");
        dialog.getRootPane().getActionMap().put("ok", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ok.doClick();
            }
        });
        dialog.setMinimumSize(new Dimension(480, 300));
        dialog.setPreferredSize(new Dimension(640, 380));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        SwingUtilities.invokeLater(editor::requestFocusInWindow);
        dialog.setVisible(true); // モーダル: ここでブロック
        return result[0];
    }

    private static JPanel titled(String title, Component body) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private static String wrap(String bodyHtml) {
        return MarkdownRenderer.wrapDocument(bodyHtml, 6, 12);
    }
}
