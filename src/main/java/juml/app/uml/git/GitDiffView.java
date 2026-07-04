// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;

/**
 * unified diff を GitKraken 風に色分け表示する読み取り専用ペイン。
 *
 * <p>追加行 (+) は緑、削除行 (-) は赤、hunk ヘッダ (@@) はアクセント色、ファイルヘッダ
 * (diff --git / index / +++ / ---) は淡色で描く。配色は現在のテーマ (背景の明暗) に
 * 合わせて切り替える。</p>
 */
final class GitDiffView extends JTextPane {

    private final SimpleAttributeSet added = new SimpleAttributeSet();
    private final SimpleAttributeSet removed = new SimpleAttributeSet();
    private final SimpleAttributeSet hunk = new SimpleAttributeSet();
    private final SimpleAttributeSet header = new SimpleAttributeSet();
    private final SimpleAttributeSet context = new SimpleAttributeSet();

    GitDiffView() {
        setEditable(false);
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        refreshTheme();
    }

    @Override public void updateUI() {
        super.updateUI();
        // テーマ切替 (FlatLaf Light/Dark) 後も色を追従させる。
        refreshTheme();
    }

    /** 背景の明暗を見て、diff 各種行の前景色・強調背景を決める。 */
    private void refreshTheme() {
        if (added == null) {
            return; // super コンストラクタからの updateUI(); フィールド未初期化
        }
        Color bg = getBackground();
        boolean dark = bg != null && luminance(bg) < 0.5;
        Color green = dark ? new Color(0x4EC9A0) : new Color(0x1A7F37);
        Color red = dark ? new Color(0xF07178) : new Color(0xCF222E);
        Color accent = dark ? new Color(0x9B84EE) : new Color(0x8250DF);
        Color muted = dark ? new Color(0x8A94A6) : new Color(0x8A94A6);
        Color fg = getForeground() != null ? getForeground() : Color.GRAY;

        StyleConstants.setForeground(added, green);
        StyleConstants.setBackground(added, tint(green, dark ? 0.12f : 0.10f, bg));
        StyleConstants.setForeground(removed, red);
        StyleConstants.setBackground(removed, tint(red, dark ? 0.12f : 0.10f, bg));
        StyleConstants.setForeground(hunk, accent);
        StyleConstants.setForeground(header, muted);
        StyleConstants.setForeground(context, fg);
    }

    /** color を bg に対して alpha 分だけ薄く混ぜた不透明色を返す。 */
    private static Color tint(Color color, float alpha, Color bg) {
        Color b = bg != null ? bg : Color.WHITE;
        int r = Math.round(color.getRed() * alpha + b.getRed() * (1 - alpha));
        int g = Math.round(color.getGreen() * alpha + b.getGreen() * (1 - alpha));
        int bl = Math.round(color.getBlue() * alpha + b.getBlue() * (1 - alpha));
        return new Color(r, g, bl);
    }

    private static double luminance(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }

    /** diff テキスト全体を色分けして表示する。プレーン文言 (ヒント等) はそのまま。 */
    void setDiff(String diff) {
        refreshTheme();
        setText("");
        StyledDocument doc = getStyledDocument();
        if (diff == null || diff.isEmpty()) {
            return;
        }
        String[] lines = diff.split("\n", -1);
        try {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String text = i < lines.length - 1 ? line + "\n" : line;
                doc.insertString(doc.getLength(), text, styleFor(line));
            }
        } catch (BadLocationException ignored) {
            setText(diff); // 失敗時は素のテキストにフォールバック
        }
        setCaretPosition(0);
    }

    private SimpleAttributeSet styleFor(String line) {
        if (line.startsWith("+++") || line.startsWith("---")
                || line.startsWith("diff ") || line.startsWith("index ")
                || line.startsWith("new file") || line.startsWith("deleted file")
                || line.startsWith("rename ") || line.startsWith("similarity ")) {
            return header;
        }
        if (line.startsWith("@@")) {
            return hunk;
        }
        if (line.startsWith("+")) {
            return added;
        }
        if (line.startsWith("-")) {
            return removed;
        }
        return context;
    }
}
