// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.SourceHighlighter.Span;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link PumlSourcePanel} の「本文を変えない装飾」をまとめて受け持つ。
 *
 * <p>シンタックスハイライト・現在行の帯・対応括弧の枠・描画失敗行の赤帯は、
 * どれもドキュメントを変更せず {@link Highlighter} と文字属性だけを触る処理で、
 * かつ互いに順序と重なりの調整が要る (赤帯を現在行の帯で隠さない、など)。
 * これらを 1 か所に集めることで、パネル本体は編集とレイアウトの配線に専念できる。</p>
 */
final class PumlSourceDecorations {

    /** これを超える文字数のテキストはハイライトを省略してプレーン表示する (EDT 保護)。 */
    private static final int HIGHLIGHT_CHAR_LIMIT = 400_000;

    /** 対応括弧を枠線で囲むペインター。 */
    private static final Highlighter.HighlightPainter BRACKET_PAINTER =
            PumlEditorPainters.BRACKET;

    /** 行全体を塗る現在行ハイライトペインター。 */
    private static final Highlighter.HighlightPainter CURRENT_LINE_PAINTER =
            PumlEditorPainters.CURRENT_LINE;

    private final JTextPane pane;
    /** 行番号ガター (現在行/エラー行の表示を追従させるため再描画する)。 */
    private final LineNumberGutter gutter;

    /** 現在行ハイライトのタグ (キャレット移動で貼り替える)。 */
    private Object currentLineTag;
    /** 対応括弧のハイライトタグ (0 個または 2 個)。 */
    private final List<Object> bracketTags = new ArrayList<>();
    /** 描画失敗行のハイライトタグ。 */
    private Object errorTag;
    /** 現在強調しているエラー行 (1 始まり)。無しは 0。テーマ切替時の再着色に使う。 */
    private int errorLine;
    /** 構文チェックの波線タグ。 */
    private final List<Object> diagnosticTags = new ArrayList<>();
    /** 行番号 (1 始まり) → 指摘文。ツールチップで理由を出すために持つ。 */
    private final Map<Integer, String> diagnosticsByLine = new HashMap<>();

    PumlSourceDecorations(JTextPane pane, LineNumberGutter gutter) {
        this.pane = pane;
        this.gutter = gutter;
    }

    /** 現在強調しているエラー行 (1 始まり、無しは 0)。 */
    int errorLine() {
        return errorLine;
    }

    /**
     * 全ハイライトを捨てる (本文の全文差し替え時)。オフセットが旧内容基準で
     * 無効になるため、貼り直しではなく破棄する。
     */
    void reset() {
        pane.getHighlighter().removeAllHighlights();
        currentLineTag = null;
        errorTag = null;
        errorLine = 0;
        bracketTags.clear();
        diagnosticTags.clear();
        diagnosticsByLine.clear();
    }

    // -------------------------------------------------------------------------
    // シンタックスハイライト
    // -------------------------------------------------------------------------

    /** 現在の本文を PlantUML トークンで再着色する (基準色→トークン色の順に適用)。 */
    void applySyntax() {
        StyledDocument doc = pane.getStyledDocument();
        int len = doc.getLength();
        if (len == 0) {
            return;
        }
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, EditorColors.text());
        doc.setCharacterAttributes(0, len, base, true);
        if (len > HIGHLIGHT_CHAR_LIMIT) {
            return; // 巨大テキストはプレーン表示 (EDT 保護)
        }
        for (Span s : PlantUmlHighlighter.highlight(textOf())) {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setForeground(a, s.color);
            doc.setCharacterAttributes(s.start, s.length, a, false);
        }
    }

    // -------------------------------------------------------------------------
    // 現在行 / 対応括弧
    // -------------------------------------------------------------------------

    /** キャレット行を薄く塗る現在行ハイライトを貼り替える (エラー行とは重ねない)。 */
    void updateCurrentLine() {
        Highlighter h = pane.getHighlighter();
        if (h == null) {
            return;
        }
        try {
            if (currentLineTag != null) {
                h.removeHighlight(currentLineTag);
                currentLineTag = null;
            }
            Element root = pane.getDocument().getDefaultRootElement();
            int line0 = root.getElementIndex(pane.getCaretPosition());
            // 赤いエラー帯を隠さないよう、エラー行と重なるときは現在行を塗らない。
            if (line0 + 1 == errorLine) {
                gutter.repaint();
                return;
            }
            Element el = root.getElement(line0);
            currentLineTag = h.addHighlight(el.getStartOffset(), el.getEndOffset(),
                    CURRENT_LINE_PAINTER);
            gutter.repaint();
        } catch (BadLocationException ignored) {
            // 行範囲取得失敗時はハイライトを諦める。
        }
    }

    /** キャレット隣の括弧とその対応括弧を枠で囲む (無ければ消すだけ)。 */
    void updateBracketMatch() {
        Highlighter h = pane.getHighlighter();
        if (h == null) {
            return;
        }
        for (Object t : bracketTags) {
            h.removeHighlight(t);
        }
        bracketTags.clear();
        int[] pair = BracketMatcher.matchingBrackets(textOf(), pane.getCaretPosition());
        if (pair == null) {
            return;
        }
        try {
            bracketTags.add(h.addHighlight(pair[0], pair[0] + 1, BRACKET_PAINTER));
            bracketTags.add(h.addHighlight(pair[1], pair[1] + 1, BRACKET_PAINTER));
        } catch (BadLocationException ignored) {
            // 範囲外は無視。
        }
    }

    // -------------------------------------------------------------------------
    // エラー行
    // -------------------------------------------------------------------------

    /** エラー行の強調色。テーマ (ライト/ダーク) に応じて描画時に解決する。 */
    private static Color errorColor() {
        return EditorColors.isDark()
                ? new Color(0x5A, 0x1D, 0x1D)
                : new Color(0xFF, 0xCD, 0xD2);
    }

    /**
     * 描画失敗行 (1 始まり、エディタ行) を赤く強調する。
     * {@code line} が 0 以下・範囲外なら既存の強調を消すだけ。
     *
     * <p>キャレットは移動しない: ライブプレビューの描画失敗は入力ポーズのたびに
     * 非同期で届くため、キャレットを奪うと以降の入力が誤った行へ挿入される。
     * 入力中でない (フォーカスが無い) 場合のみ、エラー行が見えるようスクロールする。</p>
     */
    void highlightErrorLine(int line) {
        clearError();
        if (line <= 0) {
            return;
        }
        try {
            Element root = pane.getDocument().getDefaultRootElement();
            int li = line - 1;
            if (li >= root.getElementCount()) {
                return;
            }
            Element el = root.getElement(li);
            int start = el.getStartOffset();
            errorTag = pane.getHighlighter().addHighlight(start, el.getEndOffset(),
                    new DefaultHighlighter.DefaultHighlightPainter(errorColor()));
            errorLine = line;
            // エラー行に現在行ハイライトが重なっていたら退かす (赤帯を隠さない)。
            updateCurrentLine();
            if (!pane.hasFocus()) {
                Rectangle2D r = pane.modelToView2D(start);
                if (r != null) {
                    pane.scrollRectToVisible(r.getBounds());
                }
            }
        } catch (BadLocationException ignored) {
            // 行範囲がずれた場合は強調しない (致命的でない)。
        }
    }

    /** 描画失敗行の強調を消す。 */
    void clearError() {
        errorLine = 0;
        if (errorTag != null) {
            pane.getHighlighter().removeHighlight(errorTag);
            errorTag = null;
        }
    }

    // -------------------------------------------------------------------------
    // 構文チェックの波線
    // -------------------------------------------------------------------------

    /**
     * 構文チェックの結果を波線で示す。前回の波線は必ず消してから貼り直すので、
     * 直った指摘が残ることはない。
     */
    void applyDiagnostics(List<PumlDiagnostics.Diagnostic> diagnostics) {
        Highlighter h = pane.getHighlighter();
        if (h == null) {
            return;
        }
        for (Object t : diagnosticTags) {
            h.removeHighlight(t);
        }
        diagnosticTags.clear();
        diagnosticsByLine.clear();
        Element root = pane.getDocument().getDefaultRootElement();
        for (PumlDiagnostics.Diagnostic d : diagnostics) {
            int li = d.line() - 1;
            if (li < 0 || li >= root.getElementCount()) {
                continue;
            }
            // 同じ行に複数の指摘が乗ることは稀だが、最初のものを理由として見せる。
            diagnosticsByLine.putIfAbsent(d.line(), d.message());
            Element el = root.getElement(li);
            try {
                diagnosticTags.add(h.addHighlight(el.getStartOffset(),
                        Math.max(el.getStartOffset(), el.getEndOffset() - 1),
                        PumlEditorPainters.SQUIGGLE));
            } catch (BadLocationException ignored) {
                // 行範囲がずれた場合はその 1 件を諦める。
            }
        }
        pane.repaint();
    }

    /** 指定オフセットの行に付いている指摘 (無ければ null)。ツールチップに使う。 */
    String diagnosticAt(int offset) {
        if (diagnosticsByLine.isEmpty()) {
            return null;
        }
        Element root = pane.getDocument().getDefaultRootElement();
        return diagnosticsByLine.get(root.getElementIndex(offset) + 1);
    }

    /** 現在の指摘件数 (ステータス表示・テスト用)。 */
    int diagnosticCount() {
        return diagnosticsByLine.size();
    }

    /**
     * テーマ切替後に、焼き込まれた色のハイライトを現テーマで貼り直す。
     * ペインターの色は追加時に固定されるため、{@code updateComponentTreeUI} では
     * 更新されず旧テーマ色が残る。
     */
    void reapplyForTheme() {
        int line = errorLine;
        applySyntax();
        updateCurrentLine();
        updateBracketMatch();
        if (line > 0) {
            highlightErrorLine(line);
        }
    }

    // -------------------------------------------------------------------------
    // テスト用
    // -------------------------------------------------------------------------

    /** テスト用: 現在の対応括弧ハイライト数 (対応があれば 2、無ければ 0)。 */
    int bracketMatchCountForTest() {
        return bracketTags.size();
    }

    /** テスト用: 基準色と異なる着色 (キーワード等) の文字が 1 つでもあるか。 */
    boolean hasColoredRunForTest() {
        StyledDocument doc = pane.getStyledDocument();
        Color base = EditorColors.text();
        for (int i = 0; i < doc.getLength(); i++) {
            Color fg = StyleConstants.getForeground(doc.getCharacterElement(i).getAttributes());
            if (fg != null && !fg.equals(base)) {
                return true;
            }
        }
        return false;
    }

    /**
     * テスト用: エラー行ハイライトの件数。常時付く現在行ハイライトと対応括弧は
     * 数えない (それらはキャレット追従の装飾で、エラー行強調とは別責務のため)。
     */
    int errorHighlightCountForTest() {
        int n = 0;
        for (Highlighter.Highlight h : pane.getHighlighter().getHighlights()) {
            if (h.getPainter() != CURRENT_LINE_PAINTER && h.getPainter() != BRACKET_PAINTER) {
                n++;
            }
        }
        return n;
    }

    private String textOf() {
        try {
            return pane.getDocument().getText(0, pane.getDocument().getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }
}
