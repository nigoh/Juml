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
 * 生成された PlantUML テキストをリードオンリーで表示するパネル。
 *
 * <p>デバッグ目的、もしくはユーザーが PlantUML テキストを別ツールに
 * コピーしたい場合の参照用。等幅フォントで表示し、上部に「Copy」ボタンを置く。</p>
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
}
