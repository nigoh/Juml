// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * AndroidManifest.xml の Markdown サマリーをリードオンリー表示するパネル。
 *
 * <p>{@link juml.core.formats.android.TextSummaryReport#toManifestMarkdown} の
 * 結果をそのまま貼り付ける。等幅フォント / 行ラップなしで表示し、ユーザーは
 * 必要に応じて選択コピーできる (CLI の {@code -m} に相当する出力の GUI 版)。</p>
 */
public class ManifestSummaryPanel extends JPanel {

    private final JTextArea textArea;

    public ManifestSummaryPanel() {
        super(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setTabSize(2);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    public void setText(String markdown) {
        textArea.setText(markdown == null ? "" : markdown);
        textArea.setCaretPosition(0);
    }

    public String getText() {
        return textArea.getText();
    }
}
