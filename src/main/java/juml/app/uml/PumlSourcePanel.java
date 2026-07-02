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

    private Object errorHighlightTag;
    private final javax.swing.text.Highlighter.HighlightPainter errorPainter =
            new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(
                    new java.awt.Color(0xFF, 0xCD, 0xD2));

    /**
     * 描画失敗行 (1 始まり、エディタ行) を赤く強調してキャレットを移動する。
     * {@code line} が 0 以下・範囲外なら既存の強調を消すだけ。
     */
    public void highlightErrorLine(int line) {
        clearErrorHighlight();
        if (line <= 0) {
            return;
        }
        try {
            int li = line - 1;
            if (li >= textArea.getLineCount()) {
                return;
            }
            int start = textArea.getLineStartOffset(li);
            int end = textArea.getLineEndOffset(li);
            errorHighlightTag = textArea.getHighlighter().addHighlight(start, end, errorPainter);
            textArea.setCaretPosition(start);
        } catch (javax.swing.text.BadLocationException ignored) {
            // 行範囲がずれた場合は強調しない (致命的でない)。
        }
    }

    /** 描画失敗行の強調を消す。 */
    public void clearErrorHighlight() {
        if (errorHighlightTag != null) {
            textArea.getHighlighter().removeHighlight(errorHighlightTag);
            errorHighlightTag = null;
        }
    }

    /**
     * PlantUML が報告した「生成ソースの行番号」を、スタイル prelude 挿入分
     * ({@code injectedLines}) を差し引いてエディタ上の行番号へ写像する (純関数)。
     * 挿入は {@code @startuml} 直後に入るため、行 1 (= @startuml) はそのまま。
     */
    public static int editorLineForError(int errorLine, int injectedLines) {
        if (errorLine <= 1 || injectedLines <= 0) {
            return errorLine;
        }
        return Math.max(1, errorLine - injectedLines);
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
