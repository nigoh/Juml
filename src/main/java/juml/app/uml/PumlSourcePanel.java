// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
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
    /** 雛形挿入とタブストップ巡回 (補完確定・挿入パレットの共通経路)。 */
    private PumlEditInsertions insertions;
    /** 「挿入」ボタンのパレット (スニペット挿入 + 選択範囲の囲み)。 */
    private final PumlInsertPalette palette;
    /** シンタックスハイライトの再計算をまとめる遅延タイマ (連続入力のたびに走らせない)。 */
    private final Timer highlightTimer;
    /** 本文を変えない装飾 (ハイライト・現在行・対応括弧・エラー行)。 */
    private final PumlSourceDecorations decorations;

    public PumlSourcePanel() {
        super(new BorderLayout());
        textPane = new JTextPane() {
            @Override public String getToolTipText(java.awt.event.MouseEvent e) {
                // 波線の理由をその場で読めるようにする。指摘が無ければ既定の
                // ツールチップ (読み取り専用ヒント) に譲る。
                if (decorations != null) {
                    String hint = decorations.diagnosticAt(viewToModel2D(e.getPoint()));
                    if (hint != null) {
                        return hint;
                    }
                }
                return super.getToolTipText(e);
            }
        };
        javax.swing.ToolTipManager.sharedInstance().registerComponent(textPane);
        undoSupport = new PumlUndoSupport(textPane);
        textPane.setEditable(false);
        // 既定は読み取り専用 (生成図のプレビュー)。クリックしても入力が効かない理由と
        // 編集への導線をツールチップで示す (無表示だと「なぜ打てない?」が分からない)。
        textPane.setToolTipText(Messages.get("puml.readOnly.tip"));
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
        // パレットは押すたびに組み直す (囲みの並びは図種で、出し分けは選択の有無で変わる)。
        palette = new PumlInsertPalette(this::insertSnippet, this::surroundSelection,
                this::getText, () -> textPane.getSelectedText() != null);
        snippetButton.addActionListener(
                e -> palette.build().show(snippetButton, 0, snippetButton.getHeight()));
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
        decorations = new PumlSourceDecorations(textPane, gutter);

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

        highlightTimer = new Timer(120, e -> {
            decorations.applySyntax();
            refreshDiagnostics();
        });
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
            decorations.updateCurrentLine();
            decorations.updateBracketMatch();
        });
    }

    private void onStructuralChange() {
        highlightTimer.restart();
        gutter.refresh();
    }

    /**
     * スニペット文字列を現在のキャレット位置へ挿入する (編集不可なら無視)。
     * {@link PumlSnippetTemplate} のプレースホルダを展開し、最初の穴を選択状態にする
     * ({@code Tab} で次の穴へ送れる)。編集モードで無ければ何もしない。
     */
    void insertSnippet(String text) {
        if (!textPane.isEditable() || text == null || text.isEmpty()) {
            return;
        }
        insertions().insertTemplate(text);
    }

    /**
     * 雛形挿入とタブストップ巡回の実体。読み取り専用ペインでも
     * {@link #insertSnippet(String)} 経由の呼び出しに備えて遅延生成する。
     */
    private PumlEditInsertions insertions() {
        if (insertions == null) {
            insertions = new PumlEditInsertions(textPane, this::runAsCompound);
        }
        return insertions;
    }

    /**
     * 選択している行を雛形のブロックで囲む (編集不可なら無視)。選択が無ければ
     * キャレット行が対象になる。
     */
    void surroundSelection(String template) {
        if (!textPane.isEditable()) {
            return;
        }
        insertions().surroundSelection(template);
    }

    /** 囲みブロックの一覧をキャレット位置に出す (Ctrl+Alt+T)。 */
    private void showSurroundMenu() {
        if (!textPane.isEditable()) {
            return;
        }
        try {
            Rectangle2D r = textPane.modelToView2D(textPane.getCaretPosition());
            palette.buildSurroundOnly().show(textPane, (int) r.getX(),
                    (int) (r.getY() + r.getHeight()));
        } catch (BadLocationException ignored) {
            // キャレット位置を解決できないときはメニューを出さないだけでよい。
        }
    }

    /**
     * 構文チェックを走らせて波線を貼り直す。編集モードのときだけ行う
     * (生成された図のソースを眺めているだけの人に指摘を出しても手がない)。
     */
    private void refreshDiagnostics() {
        if (!textPane.isEditable()) {
            return;
        }
        decorations.applyDiagnostics(PumlDiagnostics.analyze(getText()));
    }

    /** テスト用: 構文チェックを同期実行し、指摘件数を返す。 */
    int diagnosticCountForTest() {
        refreshDiagnostics();
        return decorations.diagnosticCount();
    }

    /** テスト用: 指定オフセット行に付いている指摘 (無ければ null)。 */
    String diagnosticAtForTest(int offset) {
        return decorations.diagnosticAt(offset);
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
        decorations.reset();
        // 検索バーの一致 (hits[] オフセット・件数表示) も旧内容基準で無効になる。reset しないと
        // removeAllHighlights でハイライトだけ消え、次候補ジャンプが旧オフセットを新文書へ適用して
        // キャレット誤配置や BadLocationException を招く (JavaSourcePanel と同じ差し替え時の契約)。
        findBar.reset();
        // 巡回中のタブストップも旧内容基準。全文差し替え後は指し先が意味を失うので捨てる
        // (残すと Tab が本来のインデントへ戻らないまま無関係な位置を選びにいく)。
        if (insertions != null) {
            insertions.cancel();
        }
        replaceDocText(text);
        textPane.setCaretPosition(0);
        copyButton.setEnabled(!text.isEmpty());
        // プログラムによる全文差し替え (ファイル読込・Design キャンバス同期など) は
        // undo 単位として意味を成さないため履歴を破棄する。ユーザーのキー入力・
        // スニペット挿入だけが Ctrl+Z の対象になる。
        undoSupport.discardAll(); // 全文差し替えで旧編集は破棄。進行中のタイプ塊も無効化する。
        // 描画は通知外なので遅延実行で安全にハイライトする (ドキュメント変更通知中の再入回避)。
        SwingUtilities.invokeLater(decorations::applySyntax);
        SwingUtilities.invokeLater(decorations::updateCurrentLine);
        SwingUtilities.invokeLater(this::refreshDiagnostics);
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

    // 装飾 (ハイライト・現在行・対応括弧・エラー行) — PumlSourceDecorations へ委譲
    // -------------------------------------------------------------------------

    /**
     * Look&amp;Feel のライブ切替に追従して、焼き込まれた色のハイライトを現テーマで貼り直す。
     * super から呼ばれるためフィールド未初期化ガードを置く。
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (textPane == null || decorations == null) {
            return;
        }
        // ツリーの LaF 更新が済んでから貼り直す。
        SwingUtilities.invokeLater(decorations::reapplyForTheme);
    }

    /** 描画失敗行 (1 始まり、エディタ行) を赤く強調する。 */
    public void highlightErrorLine(int line) {
        decorations.highlightErrorLine(line);
    }

    /** 描画失敗行の強調を消す。 */
    public void clearErrorHighlight() {
        decorations.clearError();
    }

    /** テスト用: シンタックスハイライトを同期適用する (タイマ待ちを避ける)。 */
    void applyHighlightForTest() {
        decorations.applySyntax();
    }

    /** テスト用: 基準色と異なる着色 (キーワード等) の文字が 1 つでもあるか。 */
    boolean hasColoredRunForTest() {
        return decorations.hasColoredRunForTest();
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
        return decorations.bracketMatchCountForTest();
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
        insertions().insertCompletion(at, prefix, PumlCompletionItem
                .word(PumlCompletionItem.Kind.KEYWORD, candidate, ""));
    }

    /** テスト用: 補完候補 (文脈込み) を確定する。 */
    void applyCompletionItemForTest(PumlCompletionItem item) {
        int at = textPane.getCaretPosition();
        String prefix = item.kind() == PumlCompletionItem.Kind.ARROW
                ? PumlCompletion.arrowPrefix(getText(), at)
                : PumlCompletion.wordPrefix(getText(), at);
        insertions().insertCompletion(at, prefix, item);
    }

    /** テスト用: 残っているタブストップ数 (巡回していなければ 0)。 */
    int tabStopsRemainingForTest() {
        return insertions().remainingStopsForTest();
    }

    /** テスト用: 現在の選択文字列 (タブストップが選択されているかの確認に使う)。 */
    String selectedTextForTest() {
        String sel = textPane.getSelectedText();
        return sel == null ? "" : sel;
    }

    /** テスト用: 名前付きエディタアクション (juml-newline 等) を実行する。 */
    void performEditorActionForTest(String actionKey) {
        javax.swing.Action a = textPane.getActionMap().get(actionKey);
        if (a != null) {
            a.actionPerformed(new java.awt.event.ActionEvent(textPane, 0, actionKey));
        }
    }

    /** テスト用: 1 文字ずつ順に挿入して連続タイプを再現する (Undo 束ねの検証)。 */
    void typeForTest(String s) {
        try {
            for (int i = 0; i < s.length(); i++) {
                int pos = textPane.getCaretPosition();
                textPane.getDocument().insertString(pos, String.valueOf(s.charAt(i)), null);
                textPane.setCaretPosition(pos + 1);
            }
        } catch (BadLocationException ex) {
            throw new IllegalStateException(ex);
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

    /** テスト用: 直近の編集を 1 手戻す (複合編集・連続タイプの一括 Undo を検証)。 */
    void undoForTest() {
        undoSupport.undo();
    }

    /**
     * テスト用: エラー行ハイライトの件数。常時付く現在行ハイライトは数えない
     * (現在行はキャレット追従の装飾で、エラー行強調とは別責務のため)。
     */
    int highlightCountForTest() {
        return decorations.errorHighlightCountForTest();
    }

    // -------------------------------------------------------------------------
    // 編集モード / Undo
    // -------------------------------------------------------------------------

    /**
     * 編集モードで有効化する Undo/Redo サブシステム (連続タイプの束ね・複合編集を含む)。
     * リードオンリー表示では未装備 ({@link PumlUndoSupport#isInstalled()} が false)。
     */
    private final PumlUndoSupport undoSupport;

    /** インデント 1 段分 (スペース 2 つ)。 */
    private static final String INDENT = "  ";

    /** テキスト領域の編集可否を切り替える (自由編集エディタタブは true にする)。 */
    public void setEditable(boolean editable) {
        textPane.setEditable(editable);
        // 読み取り専用ヒントは編集モードでは外す (編集できるのに「読み取り専用」と誤解させない)。
        textPane.setToolTipText(editable ? null : Messages.get("puml.readOnly.tip"));
        // 編集モードでは空テキストからでもコピーできるよう常時有効にする。
        if (editable) {
            copyButton.setEnabled(true);
            if (!undoSupport.isInstalled()) {
                undoSupport.install();
                installEditorActions();
            }
        }
        // スニペット挿入 UI は編集モードのときだけ見せる。
        snippetButton.setVisible(editable);
        if (editable) {
            SwingUtilities.invokeLater(this::refreshDiagnostics);
        }
    }

    /**
     * Ctrl(⌘)+Z / Ctrl(⌘)+Y / Ctrl(⌘)+Shift+Z の undo/redo を編集モードに配線する。
     * JTextPane は既定では undo を持たないため、エディタとして「まともに使える」
     * 最低限の取り消し操作をここで足す。シンタックスハイライトによる属性変更 (CHANGE) は
     * undo 対象から除外し、Ctrl+Z が文字の挿入/削除だけを巻き戻すようにする。
     * 多重呼び出しは無視 (再インストールしない)。
     */
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
        // 選択行を囲む (IntelliJ の Surround With と同じ Ctrl+Alt+T。Ctrl+Shift+T は
        // 「閉じたタブを開き直す」に取られている)。
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T,
                menuMask | java.awt.event.InputEvent.ALT_DOWN_MASK), "juml-surround");
        am.put("juml-comment", action(this::toggleComment));
        am.put("juml-indent", action(() -> indentOrTab(false)));
        am.put("juml-outdent", action(() -> indentSelection(true)));
        am.put("juml-surround", action(this::showSurroundMenu));
        // VS Code 相当の編集キー (Enter 自動インデント・自動閉じペア・行移動/複製/削除)。
        PumlEditorKeys.install(textPane, this::runAsCompound);
        // 雛形の穴を Tab で巡る配線。素の Tab インデントより優先し、補完ポップアップには
        // 譲る必要があるため、PumlEditorKeys の後・補完ポップアップの前に入れる。
        insertions().install(im, am);
        // 入力追従補完 (Ctrl+Space の明示起動も内包)。Enter/Tab/Up/Down の委譲があるため
        // 最後にインストールする。
        completionPopup = new PumlCompletionPopup(textPane, (prefix, item) ->
                insertions().insertCompletion(textPane.getCaretPosition(), prefix, item));
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
        int a = Math.min(textPane.getSelectionStart(), textPane.getSelectionEnd());
        int b = Math.max(textPane.getSelectionStart(), textPane.getSelectionEnd());
        if (a != b) {
            // 選択があれば (単一行でも複数行でも) その行を字下げする (VS Code 相当)。以前は
            // 単一行選択で insertSnippet(INDENT) を呼び、選択を残したままキャレット位置へ空白が
            // 割り込む中途半端な挙動だった。
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

    /**
     * {@link #removeCommentLines()} が削除する行 (トリム済み) を、上から順に返す。
     * 削除は元に戻せる保証が限定的な破壊的操作なので、実行前の確認ダイアログで
     * 「何が消えるか」を提示するために使う。レイアウトコメント ({@code '@pos}) は消さない。
     */
    public java.util.List<String> commentLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        Element root = textPane.getStyledDocument().getDefaultRootElement();
        for (int ln = 0; ln < root.getElementCount(); ln++) {
            String t = lineText(root, ln);
            if (juml.app.uml.sketch.SketchDiagramType.isRemovableComment(t)) {
                out.add(t.strip());
            }
        }
        return out;
    }

    /**
     * トリム後 {@code '} で始まる行 (PlantUML の行コメント) を削除する (1 手で戻せる)。
     * ビジュアルデザイナーで「コメント行だけが原因の編集ロック」を解除するために使う。
     * {@link #setText} を使わず {@code doc.remove} で消すため通常の Ctrl+Z で復元できる。
     */
    public void removeCommentLines() {
        if (!textPane.isEditable()) {
            return;
        }
        StyledDocument doc = textPane.getStyledDocument();
        Element root = doc.getDefaultRootElement();
        // 下から上へ削除してオフセットのずれを避ける。行末の改行も含めて 1 行分消す。
        runAsCompound(() -> {
            for (int ln = root.getElementCount() - 1; ln >= 0; ln--) {
                if (!juml.app.uml.sketch.SketchDiagramType.isRemovableComment(lineText(root, ln))) {
                    continue;
                }
                Element el = root.getElement(ln);
                int start = el.getStartOffset();
                int len = Math.min(el.getEndOffset(), doc.getLength()) - start;
                if (len <= 0) {
                    continue;
                }
                try {
                    doc.remove(start, len);
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
    /** コメント切替・インデント等の複数行操作を 1 回の Undo で戻せるよう束ねる。 */
    private void runAsCompound(Runnable mutation) {
        undoSupport.runAsCompound(mutation);
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
