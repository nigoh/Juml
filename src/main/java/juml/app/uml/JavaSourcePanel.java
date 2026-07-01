// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.SourceHighlighter.Span;
import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JTextPane;

/**
 * 実際の Java / Kotlin ソースコードを VS Code のエディタ風に表示する読み取り専用パネル。
 *
 * <p>左に行番号ガター、本文に等幅フォント + 簡易シンタックスハイライト
 * ({@link SourceHighlighter}) を備える。さらに現在行の強調、{@code Ctrl+F} の
 * インクリメンタル検索 ({@link SourceFindBar})、{@code Ctrl+G} の行ジャンプ、折り返し
 * トグルといった VS Code 風の最小限のエディタ操作を提供する。</p>
 *
 * <p>大きなファイルでも EDT を固めないよう、ファイル読み込みとハイライト計算は
 * {@link SwingWorker} で background 実行する。本文の装飾は
 * {@link StyleConstants#setForeground} のみを用い、段落の {@code spaceAbove} 等は
 * 変更しない (行番号ガターを {@code modelToView2D} で整列させる前提を崩さないため)。</p>
 */
public final class JavaSourcePanel extends JPanel {

    /** これを超える文字数のファイルはハイライトを省略してプレーン表示する (EDT 保護)。 */
    private static final int HIGHLIGHT_CHAR_LIMIT = 400_000;
    /** これを超えるバイト数のファイルは読み込まず警告する (巨大生成ファイル等の暴発防止)。 */
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    // 配色はテーマ追従 (EditorColors)。FlatLaf Light / Dark を切り替えても
    // ソースビューアが白く浮かないよう、描画時に動的解決する。

    /** メソッド宣言行らしさを示す修飾子。{@code findMethodDefinition} の精度向上に使う。 */
    private static final Set<String> DECL_MODIFIERS = new HashSet<>(Arrays.asList(
            "public", "protected", "private", "static", "final", "abstract",
            "synchronized", "native", "default", "override", "open", "suspend",
            "internal", "fun", "void"));

    private final JTextPane textPane;
    private final LineNumberGutter gutter;
    private final JScrollPane scroll;
    private final JPanel noWrap;
    private final JLabel pathLabel;
    private final JButton copyButton;
    private final JButton openButton;
    private final JToggleButton wrapToggle;
    private final SourceFindBar findBar;
    /** Ctrl+G: インラインの行移動バー (VS Code 風)。 */
    private final GotoLineBar gotoBar;

    /** 現在行ハイライトのタグ (キャレット移動で貼り替える)。 */
    private Object currentLineTag;
    /** ステータスバー通知 (任意)。外部起動失敗などを伝える。 */
    private Consumer<String> statusReporter;

    /** いま表示しているファイル (なければ null)。外部アプリ起動用。 */
    private File currentFile;
    /** 進行中のロード。差し替え時にキャンセルして競合を防ぐ。 */
    private SwingWorker<LoadResult, Void> activeLoad;
    /** 表示完了後にスクロールしたいメソッド名 (なければ null)。 */
    private String pendingScrollMethod;
    /** 折り返し表示中か。 */
    private boolean wrapping;

    public JavaSourcePanel() {
        super(new BorderLayout());

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textPane.setForeground(EditorColors.text());
        textPane.setBackground(EditorColors.background());
        // Source 内にフォーカスがあっても Ctrl+Tab 等のタブ移動が外側に届くようにする。
        textPane.setFocusTraversalKeysEnabled(false);
        // JTextPane は折り返すため、折り返し無効ラッパー経由で横スクロールさせる (既定: 折り返し無効)。
        noWrap = new JPanel(new BorderLayout());
        noWrap.add(textPane, BorderLayout.CENTER);

        scroll = new JScrollPane(noWrap);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        gutter = new LineNumberGutter();
        scroll.setRowHeaderView(gutter);

        pathLabel = new JLabel(" ");
        Color pathFg = javax.swing.UIManager.getColor("Label.disabledForeground");
        pathLabel.setForeground(pathFg != null ? pathFg : new Color(0x555555));
        copyButton = makeButton("source.copy", "source.copy.tip", e -> copyAllToClipboard());
        copyButton.setEnabled(false);
        openButton = makeButton("source.openExternal", "source.openExternal.tip",
                e -> openExternally());
        openButton.setEnabled(false);
        wrapToggle = new JToggleButton(Messages.get("source.wrap"));
        wrapToggle.setToolTipText(Messages.get("source.wrap.tip"));
        wrapToggle.addActionListener(e -> setWrapping(wrapToggle.isSelected()));
        JButton findButton = makeButton("source.find", "source.find.tip", e -> showSearchBar());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        left.add(pathLabel);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        right.add(findButton);
        right.add(wrapToggle);
        right.add(copyButton);
        right.add(openButton);
        JPanel bar = new JPanel(new BorderLayout());
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        findBar = new SourceFindBar(textPane, this::revalidate);
        gotoBar = new GotoLineBar(this::jumpToLine, this::revalidate, textPane);

        JPanel north = new JPanel(new BorderLayout());
        north.add(bar, BorderLayout.NORTH);
        JPanel barsPanel = new JPanel(new javax.swing.BoxLayout(
                new JPanel(), javax.swing.BoxLayout.Y_AXIS));
        barsPanel.setLayout(new javax.swing.BoxLayout(barsPanel, javax.swing.BoxLayout.Y_AXIS));
        barsPanel.add(findBar);
        barsPanel.add(gotoBar);
        north.add(barsPanel, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        installKeyBindings();
        textPane.addCaretListener(e -> updateCurrentLineHighlight());

        showMessage(Messages.get("source.initial"));
    }

    private JButton makeButton(String labelKey, String tipKey,
                               java.awt.event.ActionListener action) {
        JButton b = new JButton(Messages.get(labelKey));
        b.setToolTipText(Messages.get(tipKey));
        b.addActionListener(action);
        return b;
    }

    /** ステータスバー通知コールバックを設定する (外部起動失敗などを伝える)。 */
    public void setStatusReporter(Consumer<String> reporter) {
        this.statusReporter = reporter;
    }

    /** 中央に案内メッセージを出す (ソース未解決 / 読み込み中など)。 */
    public void showMessage(String msg) {
        currentFile = null;
        copyButton.setEnabled(false);
        openButton.setEnabled(false);
        pathLabel.setText(" ");
        pathLabel.setToolTipText(null);
        findBar.reset();
        setPlainText(msg == null ? "" : msg, EditorColors.gutterForeground());
        gutter.refresh();
    }

    /**
     * 指定ファイルを表示する。{@code scrollToMethod} が非 null なら、表示後に
     * その名前のメソッド定義行へスクロール・強調する。
     */
    public void showFile(File file, String scrollToMethod) {
        if (file == null) {
            showMessage(Messages.get("source.notFound"));
            return;
        }
        // 同じファイルを再要求された場合は再読み込みしない (メソッドだけ移動)。
        if (file.equals(currentFile) && (activeLoad == null || activeLoad.isDone())) {
            if (scrollToMethod != null) {
                scrollToMethod(scrollToMethod);
            }
            return;
        }
        if (activeLoad != null) {
            activeLoad.cancel(true);
        }
        pendingScrollMethod = scrollToMethod;
        pathLabel.setText(file.getName());
        pathLabel.setToolTipText(file.getAbsolutePath());
        findBar.reset();
        setPlainText(Messages.get("source.loading"), EditorColors.gutterForeground());
        gutter.refresh();
        startLoad(file);
    }

    private void startLoad(final File target) {
        SwingWorker<LoadResult, Void> worker = new SwingWorker<LoadResult, Void>() {
            @Override
            protected LoadResult doInBackground() throws Exception {
                if (!target.isFile()) {
                    return LoadResult.error(Messages.get("source.notFound"));
                }
                if (target.length() > MAX_FILE_BYTES) {
                    return LoadResult.error(Messages.get("source.tooLarge"));
                }
                String text = decode(Files.readAllBytes(target.toPath()));
                boolean omit = text.length() > HIGHLIGHT_CHAR_LIMIT;
                List<Span> spans = omit ? null
                        : SourceHighlighter.highlight(text, isKotlin(target.getName()));
                return LoadResult.ok(text, spans, omit);
            }

            @Override
            protected void done() {
                if (isCancelled() || this != activeLoad) {
                    return;
                }
                applyLoadResult(target, this);
            }
        };
        activeLoad = worker;
        worker.execute();
    }

    private void applyLoadResult(File target, SwingWorker<LoadResult, Void> worker) {
        try {
            LoadResult r = worker.get();
            if (r.error != null) {
                currentFile = null;
                copyButton.setEnabled(false);
                openButton.setEnabled(false);
                setPlainText(r.error, EditorColors.gutterForeground());
                gutter.refresh();
                return;
            }
            currentFile = target;
            applyHighlighted(r.text, r.spans);
            copyButton.setEnabled(true);
            openButton.setEnabled(true);
            if (r.highlightOmitted) {
                pathLabel.setToolTipText(target.getAbsolutePath() + "  —  "
                        + Messages.get("source.highlightOmitted"));
                report(target.getName() + ": " + Messages.get("source.highlightOmitted"));
            }
            if (pendingScrollMethod != null) {
                final String m = pendingScrollMethod;
                pendingScrollMethod = null;
                SwingUtilities.invokeLater(() -> scrollToMethod(m));
            } else {
                textPane.setCaretPosition(0);
            }
        } catch (Exception ex) {
            setPlainText(Messages.get("source.readFailed") + ex.getMessage(), EditorColors.gutterForeground());
            gutter.refresh();
        }
    }

    /** 表示中ソース全文をクリップボードへコピーする。 */
    private void copyAllToClipboard() {
        StyledDocument doc = textPane.getStyledDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return;
        }
        if (text.isEmpty()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        report(Messages.get("source.copied"));
    }

    /** OS 既定アプリ (エディタ等) で現在のファイルを開く。 */
    private void openExternally() {
        if (currentFile == null || !currentFile.isFile()) {
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(
                            java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(currentFile);
            } else {
                report(Messages.get("source.openUnsupported"));
            }
        } catch (IOException | UnsupportedOperationException ex) {
            juml.util.AppLog.warn("JavaSourcePanel",
                    "Failed to open file in external app: " + currentFile.getAbsolutePath(), ex);
            report(Messages.get("source.openFailed") + ex.getMessage());
        }
    }

    private void report(String msg) {
        if (statusReporter != null && msg != null) {
            statusReporter.accept(msg);
        }
    }

    /** バイト列を UTF-8 系として復号 (BOM があれば尊重)。それ以外は UTF-8 とみなす。 */
    private static String decode(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB
                && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // 折り返しトグル
    // -------------------------------------------------------------------------

    private void setWrapping(boolean on) {
        if (on == wrapping) {
            return;
        }
        wrapping = on;
        wrapToggle.setSelected(on);
        // 折り返し ON: JTextPane を直接ビューポートへ (既定で折り返す)。
        // 折り返し OFF: 折り返し無効ラッパー経由で横スクロールさせる。
        noWrap.remove(textPane);
        if (on) {
            scroll.setViewportView(textPane);
        } else {
            noWrap.add(textPane, BorderLayout.CENTER);
            scroll.setViewportView(noWrap);
        }
        scroll.setRowHeaderView(gutter);
        scroll.revalidate();
        scroll.repaint();
        gutter.refresh();
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    private void setPlainText(String text, Color color) {
        textPane.setBackground(EditorColors.background());
        textPane.getHighlighter().removeAllHighlights();
        currentLineTag = null;
        textPane.setText("");
        StyledDocument doc = textPane.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        try {
            doc.insertString(0, text, a);
        } catch (BadLocationException ignored) {
            // 空ドキュメントへの 0 挿入は通常失敗しない。
        }
        textPane.setCaretPosition(0);
    }

    private void applyHighlighted(String text, List<Span> spans) {
        textPane.setBackground(EditorColors.background());
        textPane.getHighlighter().removeAllHighlights();
        currentLineTag = null;
        textPane.setText("");
        StyledDocument doc = textPane.getStyledDocument();
        SimpleAttributeSet base = new SimpleAttributeSet();
        StyleConstants.setForeground(base, EditorColors.text());
        try {
            doc.insertString(0, text, base);
        } catch (BadLocationException ignored) {
            return;
        }
        if (spans != null) {
            for (Span s : spans) {
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, s.color);
                doc.setCharacterAttributes(s.start, s.length, a, false);
            }
        }
        gutter.refresh();
        textPane.setCaretPosition(0);
    }

    /** キャレット行を薄く塗る現在行ハイライトを貼り替える。 */
    private void updateCurrentLineHighlight() {
        Highlighter h = textPane.getHighlighter();
        if (h == null || currentFile == null) {
            return;
        }
        try {
            if (currentLineTag != null) {
                h.removeHighlight(currentLineTag);
                currentLineTag = null;
            }
            Element root = textPane.getDocument().getDefaultRootElement();
            Element el = root.getElement(root.getElementIndex(textPane.getCaretPosition()));
            currentLineTag = h.addHighlight(el.getStartOffset(), el.getEndOffset(),
                    CURRENT_LINE_PAINTER);
            gutter.repaint();
        } catch (BadLocationException ignored) {
            // 行範囲取得失敗時はハイライトを諦める。
        }
    }

    /** 行全体 (ビュー幅いっぱい) を塗る現在行ハイライトペインター。 */
    private static final Highlighter.HighlightPainter CURRENT_LINE_PAINTER =
            (g, p0, p1, bounds, c) -> {
                try {
                    Rectangle2D r = c.modelToView2D(p0);
                    if (r == null) {
                        return;
                    }
                    g.setColor(EditorColors.currentLine());
                    g.fillRect(0, (int) r.getY(), c.getWidth(), (int) Math.ceil(r.getHeight()));
                } catch (BadLocationException ignored) {
                    // 無視 (致命的でない)。
                }
            };

    // -------------------------------------------------------------------------
    // メソッドへスクロール
    // -------------------------------------------------------------------------

    /** メソッド名の定義らしき行を探してスクロールし、その行を一時的に選択強調する。 */
    private void scrollToMethod(String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return;
        }
        StyledDocument doc = textPane.getStyledDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return;
        }
        int idx = findMethodDefinition(text, methodName);
        if (idx < 0) {
            return;
        }
        try {
            Rectangle2D r2 = textPane.modelToView2D(idx);
            if (r2 != null) {
                Rectangle r = r2.getBounds();
                textPane.scrollRectToVisible(new Rectangle(r.x, Math.max(0, r.y - 60),
                        r.width, r.height + 160));
            }
            int lineEnd = text.indexOf('\n', idx);
            textPane.setCaretPosition(idx);
            textPane.moveCaretPosition(lineEnd < 0 ? text.length() : lineEnd);
            textPane.getCaret().setSelectionVisible(true);
        } catch (BadLocationException ignored) {
            // モデル→ビュー変換失敗時はスクロールを諦める (致命的でない)。
        }
    }

    /**
     * メソッド名の定義位置を推定する。{@code name(} の各出現について、同じ行に
     * アクセス修飾子 / 戻り値型らしき語があり、かつ直前が {@code .} でない (=呼び出しでない)
     * ものを宣言とみなして優先する。該当なしなら「直前が {@code .} でない最初の出現」、
     * それも無ければ最初の出現の行頭を返す。見つからなければ -1。
     */
    static int findMethodDefinition(String text, String name) {
        String needle = name + "(";
        int from = 0;
        int firstAny = -1;
        int firstNonCall = -1;
        while (true) {
            int p = text.indexOf(needle, from);
            if (p < 0) {
                break;
            }
            from = p + needle.length();
            if (firstAny < 0) {
                firstAny = p;
            }
            int q = p - 1;
            while (q >= 0 && (text.charAt(q) == ' ' || text.charAt(q) == '\t')) {
                q--;
            }
            if (q >= 0 && text.charAt(q) == '.') {
                continue; // 直前が '.' = 呼び出しの可能性が高い
            }
            int ls = text.lastIndexOf('\n', p) + 1;
            if (firstNonCall < 0) {
                firstNonCall = ls;
            }
            if (looksLikeDeclaration(text.substring(ls, p))) {
                return ls;
            }
        }
        if (firstNonCall >= 0) {
            return firstNonCall;
        }
        return firstAny >= 0 ? text.lastIndexOf('\n', firstAny) + 1 : -1;
    }

    /** 行頭からメソッド名直前までの文字列が宣言らしいか (修飾子/型/総称型を含むか)。 */
    private static boolean looksLikeDeclaration(String prefix) {
        String s = prefix.trim();
        if (s.isEmpty()) {
            return false; // メソッド名が行頭 = 呼び出しの可能性が高い
        }
        for (String tok : s.split("[\\s<>,\\[\\].]+")) {
            if (DECL_MODIFIERS.contains(tok)) {
                return true;
            }
        }
        // 修飾子なしでも「型 メソッド名(」のように語があれば宣言の可能性が高い。式断片は弾く。
        return !s.contains("=") && !s.contains("->") && !s.endsWith("return")
                && !s.contains("return ") && s.matches("[A-Za-z_$][\\w$<>,\\[\\].\\s]*");
    }

    private static boolean isKotlin(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".kt") || n.endsWith(".kts");
    }

    // -------------------------------------------------------------------------
    // キーバインド (Ctrl+F / Ctrl+G)
    // -------------------------------------------------------------------------

    private void installKeyBindings() {
        registerPanelKey(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "find", this::showSearchBar);
        registerPanelKey(KeyStroke.getKeyStroke(KeyEvent.VK_G,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "goto", this::showGotoBar);
    }

    private void registerPanelKey(KeyStroke ks, String name, Runnable action) {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, name);
        getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void showSearchBar() {
        if (currentFile != null) {
            findBar.activate();
        }
    }

    private void showGotoBar() {
        if (currentFile == null) {
            return;
        }
        Element root = textPane.getDocument().getDefaultRootElement();
        int max = root.getElementCount();
        int current = root.getElementIndex(textPane.getCaretPosition()) + 1;
        gotoBar.activate(current, max);
    }

    private void jumpToLine(int line) {
        Element root = textPane.getDocument().getDefaultRootElement();
        int max = root.getElementCount();
        int clamped = Math.max(1, Math.min(max, line));
        try {
            int offset = root.getElement(clamped - 1).getStartOffset();
            Rectangle2D r = textPane.modelToView2D(offset);
            if (r != null) {
                textPane.scrollRectToVisible(new Rectangle(0, Math.max(0, (int) r.getY() - 80),
                        10, (int) r.getHeight() + 160));
            }
            textPane.setCaretPosition(offset);
            textPane.requestFocusInWindow();
        } catch (BadLocationException ignored) {
            // 範囲外は無視。
        }
    }

    // -------------------------------------------------------------------------
    // ロード結果 / 行番号ガター
    // -------------------------------------------------------------------------

    private static final class LoadResult {
        final String text;
        final List<Span> spans;
        final boolean highlightOmitted;
        final String error;

        private LoadResult(String text, List<Span> spans, boolean omitted, String error) {
            this.text = text;
            this.spans = spans;
            this.highlightOmitted = omitted;
            this.error = error;
        }

        static LoadResult ok(String text, List<Span> spans, boolean omitted) {
            return new LoadResult(text, spans, omitted, null);
        }

        static LoadResult error(String error) {
            return new LoadResult(null, null, false, error);
        }
    }

    /**
     * {@link #textPane} の行に合わせて行番号を描画する row header。
     * 各行の y 座標は {@code modelToView2D} で取得するため、フォントや行高の差異が
     * あっても本文と整列する。
     */
    private final class LineNumberGutter extends JComponent {

        void refresh() {
            revalidate();
            repaint();
        }

        private int lineCount() {
            return textPane.getDocument().getDefaultRootElement().getElementCount();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(textPane.getFont());
            int digits = Math.max(3, String.valueOf(Math.max(lineCount(), 1)).length());
            int w = fm.charWidth('0') * digits + 14;
            int h = Math.max(textPane.getHeight(), textPane.getPreferredSize().height);
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            Rectangle clip = g2.getClipBounds();
            g2.setColor(EditorColors.gutterBackground());
            g2.fillRect(clip.x, clip.y, clip.width, clip.height);

            Element root = textPane.getDocument().getDefaultRootElement();
            int lines = root.getElementCount();
            if (lines <= 0) {
                return;
            }
            Font f = textPane.getFont();
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics(f);
            int w = getWidth();
            int caretLine = currentFile != null
                    ? root.getElementIndex(textPane.getCaretPosition()) : -1;

            int startLine = 0;
            try {
                int off = textPane.viewToModel2D(new Point2D.Double(0, clip.y));
                startLine = Math.max(0, root.getElementIndex(off));
            } catch (RuntimeException ignored) {
                startLine = 0;
            }
            for (int line = startLine; line < lines; line++) {
                Element el = root.getElement(line);
                Rectangle2D r;
                try {
                    r = textPane.modelToView2D(el.getStartOffset());
                } catch (BadLocationException ex) {
                    break;
                }
                if (r == null) {
                    break;
                }
                int top = (int) r.getY();
                if (top > clip.y + clip.height) {
                    break;
                }
                String num = String.valueOf(line + 1);
                int sw = fm.stringWidth(num);
                g2.setColor(line == caretLine ? EditorColors.text() : EditorColors.gutterForeground());
                g2.drawString(num, w - sw - 8, top + fm.getAscent());
            }
        }
    }
}
