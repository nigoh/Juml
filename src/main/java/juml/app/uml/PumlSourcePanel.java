// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * PlantUML テキストを表示するパネル。既定はリードオンリー (生成された図のソース参照用)。
 *
 * <p>デバッグ目的、もしくはユーザーが PlantUML テキストを別ツールに
 * コピーしたい場合の参照用。等幅フォントで表示し、上部に「Copy」ボタンを置く。</p>
 *
 * <p>自由編集 PlantUML エディタタブでは {@link #setEditable(boolean)} で編集可能にし、
 * {@link #setOnTextChange(Runnable)} でユーザー編集をライブプレビューへ配線する。</p>
 */
public class PumlSourcePanel extends JPanel {

    private final JTextArea textArea;
    private final JButton copyButton;

    public PumlSourcePanel() {
        super(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setTabSize(2);

        copyButton = new JButton(Messages.get("puml.copy"));
        copyButton.setToolTipText(Messages.get("puml.copy.tip"));
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyAllToClipboard());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(copyButton);

        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    /** 表示中の PlantUML 全文をクリップボードへコピーする。 */
    private void copyAllToClipboard() {
        String text = textArea.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    public void setText(String puml) {
        textArea.setText(puml == null ? "" : puml);
        textArea.setCaretPosition(0);
        copyButton.setEnabled(puml != null && !puml.isEmpty());
    }

    public String getText() {
        return textArea.getText();
    }

    /** テキスト領域の編集可否を切り替える (自由編集エディタタブは true にする)。 */
    public void setEditable(boolean editable) {
        textArea.setEditable(editable);
        // 編集モードでは空テキストからでもコピーできるよう常時有効にする。
        if (editable) {
            copyButton.setEnabled(true);
        }
    }

    /**
     * ユーザー編集 (挿入/削除) のたびに呼ぶリスナーを登録する。
     * デバウンスは呼び出し側の責務 (連続キー入力のたびの再描画を避けるため)。
     */
    public void setOnTextChange(Runnable onChange) {
        textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onChange.run();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onChange.run();
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onChange.run();
            }
        });
    }
}
