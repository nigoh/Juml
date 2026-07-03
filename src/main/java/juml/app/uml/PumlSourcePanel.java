// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
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

    /** スニペット: {labelKey, 挿入テキスト}。編集モードでキャレット位置へ挿入する。 */
    private static final String[][] SNIPPETS = {
        {"puml.snippet.class", "class NewClass {\n  +field: Type\n  +method(): Type\n}\n"},
        {"puml.snippet.interface", "interface NewInterface {\n  +method(): Type\n}\n"},
        {"puml.snippet.abstract", "abstract class NewClass {\n  +method(): Type\n}\n"},
        {"puml.snippet.enum", "enum NewEnum {\n  VALUE_A\n  VALUE_B\n}\n"},
        {"puml.snippet.inheritance", "Parent <|-- Child\n"},
        {"puml.snippet.association", "ClassA --> ClassB\n"},
        {"puml.snippet.dependency", "ClassA ..> ClassB\n"},
        {"puml.snippet.note", "note right of ClassName: text\n"},
        {"puml.snippet.package", "package \"name\" {\n}\n"},
        {"puml.snippet.title", "title My Diagram\n"},
    };

    private final JTextArea textArea;
    private final JButton copyButton;
    private final JLabel snippetLabel;
    private final JComboBox<String> snippetCombo;

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

        snippetLabel = new JLabel(Messages.get("puml.snippet.label"));
        snippetCombo = new JComboBox<>();
        snippetCombo.setToolTipText(Messages.get("puml.snippet.tip"));
        snippetCombo.addItem(Messages.get("puml.snippet.prompt"));
        for (String[] s : SNIPPETS) {
            snippetCombo.addItem(Messages.get(s[0]));
        }
        snippetCombo.addActionListener(e -> {
            int i = snippetCombo.getSelectedIndex();
            if (i >= 1 && i <= SNIPPETS.length) {
                insertSnippet(SNIPPETS[i - 1][1]);
                snippetCombo.setSelectedIndex(0); // プロンプトへ戻す
            }
        });
        // スニペット挿入は編集モードのときだけ有効。
        snippetLabel.setVisible(false);
        snippetCombo.setVisible(false);
        bar.add(snippetLabel);
        bar.add(snippetCombo);

        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    /** スニペット文字列を現在のキャレット位置へ挿入する (編集不可なら無視)。 */
    void insertSnippet(String text) {
        if (!textArea.isEditable() || text == null || text.isEmpty()) {
            return;
        }
        int pos = Math.max(0, Math.min(textArea.getCaretPosition(), textArea.getText().length()));
        textArea.insert(text, pos);
        textArea.setCaretPosition(pos + text.length());
        textArea.requestFocusInWindow();
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

    /** エラー行の強調色。テーマ (ライト/ダーク) に応じて描画時に解決する。 */
    private static java.awt.Color errorHighlightColor() {
        return EditorColors.isDark()
                ? new java.awt.Color(0x5A, 0x1D, 0x1D)
                : new java.awt.Color(0xFF, 0xCD, 0xD2);
    }

    /**
     * 描画失敗行 (1 始まり、エディタ行) を赤く強調する。
     * {@code line} が 0 以下・範囲外なら既存の強調を消すだけ。
     *
     * <p>キャレットは移動しない: ライブプレビューの描画失敗は入力ポーズのたびに
     * 非同期で届くため、キャレットを奪うと以降の入力が誤った行へ挿入される。
     * 入力中でない (フォーカスが無い) 場合のみ、エラー行が見えるようスクロールする。</p>
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
            errorHighlightTag = textArea.getHighlighter().addHighlight(start, end,
                    new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(
                            errorHighlightColor()));
            if (!textArea.hasFocus()) {
                java.awt.geom.Rectangle2D r = textArea.modelToView2D(start);
                if (r != null) {
                    textArea.scrollRectToVisible(r.getBounds());
                }
            }
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

    /** テスト用: 現在のハイライト件数。 */
    int highlightCountForTest() {
        return textArea.getHighlighter().getHighlights().length;
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
        // スニペット挿入 UI は編集モードのときだけ見せる。
        snippetLabel.setVisible(editable);
        snippetCombo.setVisible(editable);
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
