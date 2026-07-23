// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.SourceHighlighter.Span;
import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Rectangle2D;

/**
 * PlantUML テキストを表示・編集するコードペイン。既定はリードオンリー
 * (生成された図のソース参照用)。行番号ガター・シンタックスハイライト
 * ({@link PlantUmlHighlighter})・現在行の強調を備え、あらゆる図種のテキストを
 * 読みやすくする。
 *
 * <p>自由編集 PlantUML エディタタブでは {@link #setEditable(boolean)} で編集可能にし、
 * {@link #setOnTextChange(Runnable)} でユーザー編集をライブプレビューへ配線する。
 * 装飾は {@link StyleConstants#setForeground} のみを用い、段落属性は変えない
 * (行番号ガターを {@code modelToView2D} で整列させる前提を崩さないため)。</p>
 */
public class PumlSourcePanel extends JPanel {

    /** これを超える文字数のテキストはハイライトを省略してプレーン表示する (EDT 保護)。 */
    private static final int HIGHLIGHT_CHAR_LIMIT = 400_000;

    private final JTextPane textPane;
    private final LineNumberGutter gutter;
    private final JButton copyButton;
    /** 図種別スニペットを挿入するパレットボタン (編集モードのみ表示)。 */
    private final JButton snippetButton;
    /** ソース内検索/置換バー (Ctrl+F / Ctrl+H)。 */
    private final SourceFindBar findBar;
    /** 行ジャンプバー (Ctrl+G)。 */
    private final GotoLineBar gotoBar;
    /** 入力追従の補完ポップアップ (編集モードで生成)。 */
    private PumlCompletionPopup completionPopup;
    /** シンタックスハイライトの再計算をまとめる遅延タイマ (連続入力のたびに走らせない)。 */
    private final Timer highlightTimer;

    /** 現在行ハイライトのタグ (キャレット移動で貼り替える)。 */
    private Object currentLineTag;

    public PumlSourcePanel() {
        super(new BorderLayout());
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textPane.setForeground(EditorColors.text());
        textPane.setBackground(EditorColors.background());
        // Ctrl+Tab 等のタブ移動が外側へ届くよう、フォーカストラバーサルは無効化する。
        textPane.setFocusTraversalKeysEnabled(false);

        copyButton = new JButton(Messages.get("puml.copy"));
        copyButton.setToolTipText(Messages.get("puml.copy.tip"));
        copyButton.setEnabled(false);
        copyButton.addActionListener(e -> copyAllToClipboard());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        bar.add(copyButton);

        // 図種別スニペットのパレット。ボタン押下でグループ別のポップアップを開く。
        snippetButton = new JButton(Messages.get("puml.snippet.label"));
        snippetButton.setToolTipText(Messages.get("puml.snippet.tip"));
        final JPopupMenu palette = buildSnippetPalette();
        snippetButton.addActionListener(
                e -> palette.show(snippetButton, 0, snippetButton.getHeight()));
        // スニペット挿入は編集モードのときだけ有効。
        snippetButton.setVisible(false);
        bar.add(snippetButton);

        // 折り返し無効ラッパー経由で横スクロールさせる (コード編集は折り返さない)。
        JPanel noWrap = new JPanel(new BorderLayout());
        noWrap.add(textPane, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(noWrap);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        gutter = new LineNumberGutter(textPane, () -> true);
        scroll.setRowHeaderView(gutter);

        // ソース内検索/置換バー (既定は非表示)。テキストが真実源なので置換も可能にする。
        findBar = new SourceFindBar(textPane, () -> {
            revalidate();
            repaint();
        }, true);
        gotoBar = new GotoLineBar(this::jumpToLine, this::revalidate, textPane);

        add(bar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));
        south.add(findBar);
        south.add(gotoBar);
        add(south, BorderLayout.SOUTH);
        installFindKeys();

        highlightTimer = new Timer(120, e -> applyHighlight());
        highlightTimer.setRepeats(false);
        // 本文編集 (挿入/削除) のたびに、ハイライト再計算とガター更新をスケジュールする。
        textPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                onStructuralChange();
            }
            @Override public void removeUpdate(DocumentEvent e) {
                onStructuralChange();
            }
            @Override public void changedUpdate(DocumentEvent e) {
                // 属性変更 (ハイライト自身) は無視する (再ハイライトの無限ループを防ぐ)。
            }
        });
        textPane.addCaretListener(e -> {
            updateCurrentLineHighlight();
            updateBracketMatch();
        });
    }

    private void onStructuralChange() {
        highlightTimer.restart();
        gutter.refresh();
    }

    /**
     * スニペット文字列を現在のキャレット位置へ挿入する (編集不可なら無視)。
     * {@code ${caret}} マーカー ({@link PumlSnippets#CARET}) があれば取り除き、
     * その位置へキャレットを移す (続きをすぐ入力できるようにする)。
     */
    void insertSnippet(String text) {
        if (!textPane.isEditable() || text == null || text.isEmpty()) {
            return;
        }
        int marker = text.indexOf(PumlSnippets.CARET);
        String body = marker >= 0 ? text.replace(PumlSnippets.CARET, "") : text;
        StyledDocument doc = textPane.getStyledDocument();
        int pos = Math.max(0, Math.min(textPane.getCaretPosition(), doc.getLength()));
        try {
            doc.insertString(pos, body, null);
            // marker はマーカー除去前の位置。除去しても手前の文字数は変わらないので pos+marker。
            int caret = marker >= 0 ? pos + marker : pos + body.length();
            textPane.setCaretPosition(Math.min(caret, doc.getLength()));
        } catch (BadLocationException ignored) {
            return;
        }
        textPane.requestFocusInWindow();
    }

    /** 図種別グループのサブメニューを持つスニペット挿入パレットを構築する。 */
    private JPopupMenu buildSnippetPalette() {
        JPopupMenu menu = new JPopupMenu();
        for (PumlSnippets.Group g : PumlSnippets.Group.values()) {
            JMenu sub = new JMenu(g.displayName());
            for (PumlSnippets.Snippet snip : PumlSnippets.forGroup(g)) {
                JMenuItem item = new JMenuItem(snip.displayName());
                item.addActionListener(e -> insertSnippet(snip.body()));
                sub.add(item);
            }
            menu.add(sub);
        }
        return menu;
    }

    /** 表示中の PlantUML 全文をクリップボードへコピーする。 */
    private void copyAllToClipboard() {
        String text = getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    public void setText(String puml) {
        String text = puml == null ? "" : puml;
        // 全ハイライト (現在行・エラー行) を一旦消す。オフセットが旧内容基準で無効になるため。
        textPane.getHighlighter().removeAllHighlights();
        currentLineTag = null;
        errorHighlightTag = null;
        highlightedErrorLine = 0;
        bracketTags.clear();
        // 検索バーの一致 (hits[] オフセット・件数表示) も旧内容基準で無効になる。reset しないと
        // removeAllHighlights でハイライトだけ消え、次候補ジャンプが旧オフセットを新文書へ適用して
        // キャレット誤配置や BadLocationException を招く (JavaSourcePanel と同じ差し替え時の契約)。
        findBar.reset();
        replaceDocText(text);
        textPane.setCaretPosition(0);
        copyButton.setEnabled(!text.isEmpty());
        // プログラムによる全文差し替え (ファイル読込・Design キャンバス同期など) は
        // undo 単位として意味を成さないため履歴を破棄する。ユーザーのキー入力・
        // スニペット挿入だけが Ctrl+Z の対象になる。
        if (undoManager != null) {
            undoManager.discardAllEdits();
        }
        // 描画は通知外なので遅延実行で安全にハイライトする (ドキュメント変更通知中の再入回避)。
        SwingUtilities.invokeLater(this::applyHighlight);
        SwingUtilities.invokeLater(this::updateCurrentLineHighlight);
        gutter.refresh();
    }

    /** ドキュメント本文を丸ごと差し替える (基準色を付与)。 */
    private void replaceDocText(String text) {
        StyledDocument doc = textPane.getStyledDocument();
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, EditorColors.text());
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, text, base);
        } catch (BadLocationException ignored) {
            // 空ドキュメントへの操作は通常失敗しない。
        }
    }

    public String getText() {
        StyledDocument doc = textPane.getStyledDocument();
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // シンタックスハイライト
    // -------------------------------------------------------------------------

    /** 現在の本文を PlantUML トークンで再着色する (基準色→トークン色の順に適用)。 */
    private void applyHighlight() {
        StyledDocument doc = textPane.getStyledDocument();
        int len = doc.getLength();
        if (len == 0) {
            return;
        }
        String text = getText();
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, EditorColors.text());
        doc.setCharacterAttributes(0, len, base, true);
        if (len > HIGHLIGHT_CHAR_LIMIT) {
            return; // 巨大テキストはプレーン表示 (EDT 保護)
        }
        for (Span s : PlantUmlHighlighter.highlight(text)) {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setForeground(a, s.color);
            doc.setCharacterAttributes(s.start, s.length, a, false);
        }
    }

    // -------------------------------------------------------------------------
    // 現在行ハイライト
    // -------------------------------------------------------------------------

    /** キャレット行を薄く塗る現在行ハイライトを貼り替える (エラー行とは重ねない)。 */
    private void updateCurrentLineHighlight() {
        Highlighter h = textPane.getHighlighter();
        if (h == null) {
            return;
        }
        try {
            if (currentLineTag != null) {
                h.removeHighlight(currentLineTag);
                currentLineTag = null;
            }
            Element root = textPane.getDocument().getDefaultRootElement();
            int line0 = root.getElementIndex(textPane.getCaretPosition());
            // 赤いエラー帯を隠さないよう、エラー行と重なるときは現在行を塗らない。
            if (line0 + 1 == highlightedErrorLine) {
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

    // -------------------------------------------------------------------------
    // 対応括弧の強調
    // -------------------------------------------------------------------------

    private final java.util.List<Object> bracketTags = new java.util.ArrayList<>();

    /** キャレット隣の括弧とその対応括弧を枠で囲む (無ければ消すだけ)。ドキュメントは変更しない。 */
    private void updateBracketMatch() {
        Highlighter h = textPane.getHighlighter();
        if (h == null) {
            return;
        }
        for (Object t : bracketTags) {
            h.removeHighlight(t);
        }
        bracketTags.clear();
        int[] pair = BracketMatcher.matchingBrackets(getText(), textPane.getCaretPosition());
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

    /** 対応括弧を枠線で囲むペインター (PumlEditorPainters へ分離)。 */
    private static final Highlighter.HighlightPainter BRACKET_PAINTER =
            PumlEditorPainters.BRACKET;

    /** 行全体を塗る現在行ハイライトペインター (PumlEditorPainters へ分離)。 */
    private static final Highlighter.HighlightPainter CURRENT_LINE_PAINTER =
            PumlEditorPainters.CURRENT_LINE;

    // -------------------------------------------------------------------------
    // エラー行ハイライト
    // -------------------------------------------------------------------------

    private Object errorHighlightTag;
    /** 現在強調しているエラー行 (1 始まり)。無しは 0。テーマ切替時の再着色に使う。 */
    private int highlightedErrorLine;

    /**
     * Look&amp;Feel のライブ切替に追従して、焼き込まれた色のハイライトを現テーマで貼り直す。
     * ハイライトのペインター色は追加時に固定されるため、{@code updateComponentTreeUI} では
     * 更新されず旧テーマ色が残る。エラー行の強調・シンタックスハイライトを現テーマで再適用する。
     * super から呼ばれるためフィールド未初期化ガードを置く。
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (textPane == null) {
            return;
        }
        final int line = highlightedErrorLine;
        // ツリーの LaF 更新が済んでから貼り直す。
        SwingUtilities.invokeLater(() -> {
            applyHighlight();
            updateCurrentLineHighlight();
            updateBracketMatch();
            if (line > 0) {
                highlightErrorLine(line);
            }
        });
    }

    /** エラー行の強調色。テーマ (ライト/ダーク) に応じて描画時に解決する。 */
    private static Color errorHighlightColor() {
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
    public void highlightErrorLine(int line) {
        clearErrorHighlight();
        if (line <= 0) {
            return;
        }
        try {
            Element root = textPane.getDocument().getDefaultRootElement();
            int li = line - 1;
            if (li >= root.getElementCount()) {
                return;
            }
            Element el = root.getElement(li);
            int start = el.getStartOffset();
            int end = el.getEndOffset();
            errorHighlightTag = textPane.getHighlighter().addHighlight(start, end,
                    new DefaultHighlighter.DefaultHighlightPainter(errorHighlightColor()));
            highlightedErrorLine = line;
            // エラー行に現在行ハイライトが重なっていたら退かす (赤帯を隠さない)。
            updateCurrentLineHighlight();
            if (!textPane.hasFocus()) {
                Rectangle2D r = textPane.modelToView2D(start);
                if (r != null) {
                    textPane.scrollRectToVisible(r.getBounds());
                }
            }
        } catch (BadLocationException ignored) {
            // 行範囲がずれた場合は強調しない (致命的でない)。
        }
    }

    /** 描画失敗行の強調を消す。 */
    public void clearErrorHighlight() {
        highlightedErrorLine = 0;
        if (errorHighlightTag != null) {
            textPane.getHighlighter().removeHighlight(errorHighlightTag);
            errorHighlightTag = null;
        }
    }

    /** テスト用: シンタックスハイライトを同期適用する (タイマ待ちを避ける)。 */
    void applyHighlightForTest() {
        applyHighlight();
    }

    /** テスト用: 基準色と異なる着色 (キーワード等) の文字が 1 つでもあるか。 */
    boolean hasColoredRunForTest() {
        StyledDocument doc = textPane.getStyledDocument();
        Color base = EditorColors.text();
        for (int i = 0; i < doc.getLength(); i++) {
            Color fg = StyleConstants.getForeground(doc.getCharacterElement(i).getAttributes());
            if (fg != null && !fg.equals(base)) {
                return true;
            }
        }
        return false;
    }

    /** テスト用: 行番号ガターが認識している行数 (本文の行数と一致するはず)。 */
    int gutterLineCountForTest() {
        return textPane.getDocument().getDefaultRootElement().getElementCount();
    }

    /** テスト用: 現在のキャレット位置 ({@code ${caret}} 配置の検証に使う)。 */
    int caretForTest() {
        return textPane.getCaretPosition();
    }

    /** テスト用: 選択範囲を設定する (コメント切替/インデントの検証に使う)。 */
    void selectRangeForTest(int a, int b) {
        textPane.setSelectionStart(a);
        textPane.setSelectionEnd(b);
    }

    /** テスト用: 選択行の行コメントを切り替える。 */
    void toggleCommentForTest() {
        toggleComment();
    }

    /** テスト用: 選択行をインデント/アウトデントする。 */
    void indentSelectionForTest(boolean outdent) {
        indentSelection(outdent);
    }

    /** テスト用: 検索/置換バーで全置換する。 */
    void replaceAllForTest(String query, String with) {
        findBar.replaceAllForTest(query, with);
    }

    /** テスト用: 現在の対応括弧ハイライト数 (対応があれば 2、無ければ 0)。 */
    int bracketMatchCountForTest() {
        return bracketTags.size();
    }

    /** テスト用: 現在キャレット位置での補完候補件数。 */
    int completionCandidateCountForTest() {
        String text = getText();
        String prefix = PumlCompletion.wordPrefix(text, textPane.getCaretPosition());
        return PumlCompletion.candidates(prefix, text).size();
    }

    /** テスト用: 打ちかけ語の続きを補完挿入する (ポップアップ選択と同等)。 */
    void applyCompletionForTest(String candidate) {
        int at = textPane.getCaretPosition();
        String prefix = PumlCompletion.wordPrefix(getText(), at);
        insertCompletion(at, prefix, candidate);
    }

    /** テスト用: 名前付きエディタアクション (juml-newline 等) を実行する。 */
    void performEditorActionForTest(String actionKey) {
        javax.swing.Action a = textPane.getActionMap().get(actionKey);
        if (a != null) {
            a.actionPerformed(new java.awt.event.ActionEvent(textPane, 0, actionKey));
        }
    }

    /** テスト用: キャレット位置を設定する。 */
    void setCaretForTest(int pos) {
        textPane.setCaretPosition(Math.max(0, Math.min(pos, getText().length())));
    }

    /** テスト用: 入力追従補完ポップアップ (編集モード以外は null)。 */
    PumlCompletionPopup completionPopupForTest() {
        return completionPopup;
    }

    /** テスト用: 検索バーが表示 (アクティブ) 状態か。setText で reset されるかの検証に使う。 */
    boolean findBarActiveForTest() {
        return findBar.isVisible();
    }

    /** テスト用: 直近の編集を 1 手戻す (複合編集の一括 Undo を検証)。 */
    void undoForTest() {
        if (undoManager != null && undoManager.canUndo()) {
            undoManager.undo();
        }
    }

    /**
     * テスト用: エラー行ハイライトの件数。常時付く現在行ハイライトは数えない
     * (現在行はキャレット追従の装飾で、エラー行強調とは別責務のため)。
     */
    int highlightCountForTest() {
        int n = 0;
        for (Highlighter.Highlight h : textPane.getHighlighter().getHighlights()) {
            if (h.getPainter() != CURRENT_LINE_PAINTER && h.getPainter() != BRACKET_PAINTER) {
                n++;
            }
        }
        return n;
    }

    // -------------------------------------------------------------------------
    // 編集モード / Undo
    // -------------------------------------------------------------------------

    /** 編集モードで有効化する undo/redo マネージャ (リードオンリー表示では null)。 */
    private UndoManager undoManager;
    /**
     * 複数行のコメント切替・インデントを 1 回の Undo で戻すためのグルーピング。
     * null 以外の間、ドキュメント編集はこの複合編集へ束ねる。
     */
    private CompoundEdit activeCompound;

    /** インデント 1 段分 (スペース 2 つ)。 */
    private static final String INDENT = "  ";

    /** テキスト領域の編集可否を切り替える (自由編集エディタタブは true にする)。 */
    public void setEditable(boolean editable) {
        textPane.setEditable(editable);
        // 編集モードでは空テキストからでもコピーできるよう常時有効にする。
        if (editable) {
            copyButton.setEnabled(true);
            installUndoSupport();
        }
        // スニペット挿入 UI は編集モードのときだけ見せる。
        snippetButton.setVisible(editable);
    }

    /**
     * Ctrl(⌘)+Z / Ctrl(⌘)+Y / Ctrl(⌘)+Shift+Z の undo/redo を編集モードに配線する。
     * JTextPane は既定では undo を持たないため、エディタとして「まともに使える」
     * 最低限の取り消し操作をここで足す。シンタックスハイライトによる属性変更 (CHANGE) は
     * undo 対象から除外し、Ctrl+Z が文字の挿入/削除だけを巻き戻すようにする。
     * 多重呼び出しは無視 (再インストールしない)。
     */
    private void installUndoSupport() {
        if (undoManager != null) {
            return;
        }
        undoManager = new UndoManager();
        undoManager.setLimit(500);
        textPane.getDocument().addUndoableEditListener(e -> {
            UndoableEdit edit = e.getEdit();
            if (edit instanceof AbstractDocument.DefaultDocumentEvent
                    && ((AbstractDocument.DefaultDocumentEvent) edit).getType()
                        == DocumentEvent.EventType.CHANGE) {
                return; // 属性変更 (ハイライト) は取り消し対象にしない
            }
            // コメント切替・インデント中はまとめて 1 手にする。
            if (activeCompound != null) {
                activeCompound.addEdit(edit);
                return;
            }
            undoManager.addEdit(edit);
        });
        installEditorActions();
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im = textPane.getInputMap();
        javax.swing.ActionMap am = textPane.getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, menuMask),
                "juml-undo");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, menuMask),
                "juml-redo");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
                menuMask | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "juml-redo");
        am.put("juml-undo", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.canUndo()) {
                    undoManager.undo();
                }
            }
        });
        am.put("juml-redo", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (undoManager.canRedo()) {
                    undoManager.redo();
                }
            }
        });
    }

    /** Ctrl(⌘)+F で検索、Ctrl(⌘)+H で置換、Ctrl(⌘)+G で行ジャンプ (リードオンリーでも可)。 */
    private void installFindKeys() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im = textPane.getInputMap();
        javax.swing.ActionMap am = textPane.getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, menuMask),
                "juml-find");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, menuMask),
                "juml-replace");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, menuMask),
                "juml-goto");
        am.put("juml-find", action(findBar::activate));
        am.put("juml-replace", action(() -> {
            if (textPane.isEditable()) {
                findBar.activateWithReplace();
            } else {
                findBar.activate();
            }
        }));
        am.put("juml-goto", action(this::showGotoBar));
    }

    /** 行ジャンプバーを現在行・総行数つきで開く。 */
    private void showGotoBar() {
        javax.swing.text.Element root = textPane.getDocument().getDefaultRootElement();
        int current = root.getElementIndex(textPane.getCaretPosition()) + 1;
        gotoBar.activate(current, root.getElementCount());
    }

    /** 指定行 (1 始まり) の行頭へキャレットを移して可視化する。範囲外はクランプする。 */
    private void jumpToLine(int line) {
        javax.swing.text.Element root = textPane.getDocument().getDefaultRootElement();
        int li = Math.max(0, Math.min(line - 1, root.getElementCount() - 1));
        int offset = root.getElement(li).getStartOffset();
        textPane.setCaretPosition(offset);
        try {
            Rectangle2D r = textPane.modelToView2D(offset);
            if (r != null) {
                textPane.scrollRectToVisible(r.getBounds());
            }
        } catch (BadLocationException ignored) {
            // 行が見えないだけで致命的でない。
        }
    }

    /**
     * コード編集の最低限のショートカットを配線する: {@code Ctrl(⌘)+/} で行コメント切替、
     * {@code Tab}/{@code Shift+Tab} で選択行のインデント/アウトデント。多重呼び出しでも
     * 同じアクションを上書きするだけなので安全。
     */
    private void installEditorActions() {
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im = textPane.getInputMap();
        javax.swing.ActionMap am = textPane.getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SLASH, menuMask),
                "juml-comment");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0),
                "juml-indent");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB,
                java.awt.event.InputEvent.SHIFT_DOWN_MASK), "juml-outdent");
        am.put("juml-comment", action(this::toggleComment));
        am.put("juml-indent", action(() -> indentOrTab(false)));
        am.put("juml-outdent", action(() -> indentSelection(true)));
        // VS Code 相当の編集キー (Enter 自動インデント・自動閉じペア・行移動/複製/削除)。
        PumlEditorKeys.install(textPane, this::runAsCompound);
        // 入力追従補完 (Ctrl+Space の明示起動も内包)。Enter/Tab/Up/Down の委譲があるため
        // PumlEditorKeys の後にインストールする。
        completionPopup = new PumlCompletionPopup(textPane, (prefix, candidate) ->
                insertCompletion(textPane.getCaretPosition(), prefix, candidate));
    }

    /** 打ちかけの語 {@code prefix} (キャレット直前) を候補で置換する。 */
    private void insertCompletion(int at, String prefix, String candidate) {
        if (!textPane.isEditable()) {
            return;
        }
        // 候補生成 (PumlCompletion.matches) は大文字小文字を区別しないため、検証も
        // case-insensitive で行う ("CLA" → "class" の確定を黙殺しない)。対応しない
        // 確定 (陳腐化ポップアップ由来) は無視する。
        if (!candidate.toLowerCase(java.util.Locale.ROOT)
                .startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        StyledDocument doc = textPane.getStyledDocument();
        int caret = Math.min(at, doc.getLength());
        int start = Math.max(0, caret - prefix.length());
        // 語中で確定した場合はキャレット後方の語の残り (例: "cl|a" の a) も含めて
        // 置換する ("classa" のような残余崩れを防ぐ)。
        int end = PumlCompletion.wordEnd(getText(), caret);
        // remove + insert を 1 個の複合編集にまとめ、Ctrl+Z 1 回で確定前へ戻せるようにする
        // (分かれていると 1 回目の Undo で接頭辞ごと消える)。
        runAsCompound(() -> {
            try {
                // 接頭辞ごと候補で置換し、大文字小文字のゆらぎも候補どおりに揃える。
                doc.remove(start, end - start);
                doc.insertString(start, candidate, null);
            } catch (BadLocationException ignored) {
                // 競合編集で範囲がずれた場合は何もしない (致命的でない)。
            }
        });
        textPane.setCaretPosition(Math.min(start + candidate.length(), doc.getLength()));
        textPane.requestFocusInWindow();
    }

    private static javax.swing.AbstractAction action(Runnable r) {
        return new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        };
    }

    /** 複数行選択なら選択行をインデント、そうでなければキャレット位置へ 1 段分を挿入する。 */
    private void indentOrTab(boolean outdent) {
        if (!textPane.isEditable()) {
            return;
        }
        Element root = textPane.getDocument().getDefaultRootElement();
        int a = Math.min(textPane.getSelectionStart(), textPane.getSelectionEnd());
        int b = Math.max(textPane.getSelectionStart(), textPane.getSelectionEnd());
        if (root.getElementIndex(a) != root.getElementIndex(b)) {
            indentSelection(outdent);
        } else {
            insertSnippet(INDENT);
        }
    }

    /** 選択範囲にかかる各行の行頭へ 1 段分の字下げを挿入/除去する (1 手で戻せる)。 */
    private void indentSelection(boolean outdent) {
        if (!textPane.isEditable()) {
            return;
        }
        StyledDocument doc = textPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        int a = Math.min(textPane.getSelectionStart(), textPane.getSelectionEnd());
        int b = Math.max(textPane.getSelectionStart(), textPane.getSelectionEnd());
        int first = root.getElementIndex(a);
        int last = root.getElementIndex(b);
        // 選択が次行の行頭ちょうどで終わる場合、その行は対象に含めない。
        if (last > first && b == root.getElement(last).getStartOffset()) {
            last--;
        }
        final int firstLine = first;
        final int lastLine = last;
        runAsCompound(() -> {
            for (int ln = lastLine; ln >= firstLine; ln--) {
                int ls = root.getElement(ln).getStartOffset();
                String t = lineText(root, ln);
                try {
                    if (outdent) {
                        int remove = 0;
                        while (remove < INDENT.length() && remove < t.length()
                                && t.charAt(remove) == ' ') {
                            remove++;
                        }
                        if (remove > 0) {
                            doc.remove(ls, remove);
                        }
                    } else if (!t.isEmpty() && !t.equals("\n")) {
                        doc.insertString(ls, INDENT, null);
                    }
                } catch (BadLocationException ignored) {
                    // 範囲外は無視。
                }
            }
        });
    }

    /** 選択行 (無選択なら現在行) の行コメント ({@code '}) を一括で切り替える (1 手で戻せる)。 */
    private void toggleComment() {
        if (!textPane.isEditable()) {
            return;
        }
        StyledDocument doc = textPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        int a = Math.min(textPane.getSelectionStart(), textPane.getSelectionEnd());
        int b = Math.max(textPane.getSelectionStart(), textPane.getSelectionEnd());
        int first = root.getElementIndex(a);
        int last = root.getElementIndex(b);
        if (last > first && b == root.getElement(last).getStartOffset()) {
            last--;
        }
        // 対象の非空行がすべて既にコメントなら「解除」、そうでなければ「付与」。
        boolean allCommented = true;
        for (int ln = first; ln <= last; ln++) {
            String trimmed = lineText(root, ln).stripLeading();
            if (!trimmed.isEmpty() && !trimmed.startsWith("'")) {
                allCommented = false;
                break;
            }
        }
        final int firstLine = first;
        final int lastLine = last;
        final boolean uncomment = allCommented;
        runAsCompound(() -> {
            for (int ln = lastLine; ln >= firstLine; ln--) {
                int ls = root.getElement(ln).getStartOffset();
                String t = lineText(root, ln);
                int indent = t.length() - t.stripLeading().length();
                if (t.stripLeading().isEmpty()) {
                    continue; // 空行は触らない
                }
                try {
                    if (uncomment) {
                        int at = ls + indent; // ' の位置
                        int len = (indent + 1 < t.length() && t.charAt(indent + 1) == ' ') ? 2 : 1;
                        doc.remove(at, len);
                    } else {
                        doc.insertString(ls + indent, "' ", null);
                    }
                } catch (BadLocationException ignored) {
                    // 範囲外は無視。
                }
            }
        });
    }

    /** 指定行 (0 始まり) のテキスト (改行含む) を返す。取得失敗時は空文字。 */
    private String lineText(Element root, int lineIndex) {
        Element el = root.getElement(lineIndex);
        try {
            return textPane.getDocument().getText(el.getStartOffset(),
                    el.getEndOffset() - el.getStartOffset());
        } catch (BadLocationException ex) {
            return "";
        }
    }

    /** {@code mutation} 内のドキュメント編集を 1 個の複合編集にまとめ、Ctrl+Z で一括して戻せるようにする。 */
    private void runAsCompound(Runnable mutation) {
        if (undoManager == null) {
            mutation.run();
            return;
        }
        activeCompound = new CompoundEdit();
        try {
            mutation.run();
        } finally {
            CompoundEdit ce = activeCompound;
            activeCompound = null;
            ce.end();
            if (ce.canUndo()) {
                undoManager.addEdit(ce);
            }
        }
    }

    /** エディタのテキスト領域へ入力フォーカスを移す (タブを開いた直後に呼ぶ)。 */
    public void focusEditor() {
        textPane.requestFocusInWindow();
    }

    /**
     * このパネルが保持するネイティブリソースを解放する (タブのクローズ時に呼ぶ)。
     * 補完ポップアップの {@link javax.swing.JWindow} はコンポーネント階層の外にあるため、
     * 明示的に破棄しないとタブを閉じてもネイティブウィンドウが残る。
     */
    public void disposeEditorResources() {
        if (completionPopup != null) {
            completionPopup.dispose();
        }
    }

    /**
     * ユーザー編集 (挿入/削除) のたびに呼ぶリスナーを登録する。
     * デバウンスは呼び出し側の責務 (連続キー入力のたびの再描画を避けるため)。
     * シンタックスハイライトによる属性変更 (changedUpdate) では発火しない
     * (無変更の再描画・偽 dirty を防ぐ)。
     */
    public void setOnTextChange(Runnable onChange) {
        textPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                onChange.run();
            }
            @Override public void removeUpdate(DocumentEvent e) {
                onChange.run();
            }
            @Override public void changedUpdate(DocumentEvent e) {
                // 属性変更 (ハイライト) はユーザー編集ではないので通知しない。
            }
        });
    }
}
