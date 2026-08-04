// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * PlantUML エディタの入力追従補完ポップアップ (as-you-type completion)。
 *
 * <p>タイプ中の語が {@value #MIN_PREFIX} 文字以上になると候補リストを自動表示し、
 * 入力の継続で絞り込む。{@code Up/Down} で選択、{@code Enter}/{@code Tab} で確定、
 * {@code Esc} で閉じる。{@code Ctrl+Space} での明示起動 (0 文字から) にも対応する。</p>
 *
 * <p>候補は語だけでなく、矢印記法 ({@code -} や {@code .} を打った時点で起動) と、
 * ブロックを丸ごと展開するスニペットも含む。1 行に「見出し + 補足」を並べて出すのは、
 * {@code alt} のように「語としての alt」と「alt/else/end のブロック」が同名で並ぶため、
 * 補足なしでは選べないから。</p>
 *
 * <p>ポップアップはフォーカスを奪わない {@link JWindow} で、ヘッドレス環境でも
 * インストール自体は安全なよう遅延生成する。確定時の挿入は呼び出し側から渡される
 * コールバック (接頭辞・候補) 経由で行う。</p>
 */
final class PumlCompletionPopup {

    /** 自動表示を始める接頭辞の最小文字数。 */
    static final int MIN_PREFIX = 2;

    /** 一度に見せる候補行数 (それ以上はスクロール)。 */
    private static final int VISIBLE_ROWS = 8;

    /** 行幅に足す余白 (フォント代替で描画幅が計測値を超えても切れないように)。 */
    private static final int ROW_WIDTH_SLACK = 16;

    private final JTextComponent pane;
    /** 確定時の挿入先: (接頭辞, 候補) を受け取り本文へ反映する。 */
    private final BiConsumer<String, PumlCompletionItem> onAccept;

    private JWindow window;
    private final DefaultListModel<PumlCompletionItem> model = new DefaultListModel<>();
    private JList<PumlCompletionItem> list;
    /** プログラム起因のドキュメント変更 (確定挿入など) 中は自動表示を抑止する。 */
    private boolean suppressAutoShow;
    /** テストからの同期更新中だけ、フォーカス要件を満たしたものとみなす。 */
    private boolean assumeFocused;
    /**
     * 現在の候補リストを生成した語の開始オフセット。キャレットが別の語へ移った
     * (= 語頭が変わった) ことの検出に使う。接頭辞文字列の比較にしないのは、同じ語内の
     * タイプ継続でも接頭辞は毎キー変わり、hide → 再 show のちらつきになるため。
     */
    private int shownWordStart = -1;

    PumlCompletionPopup(JTextComponent pane, BiConsumer<String, PumlCompletionItem> onAccept) {
        this.pane = pane;
        this.onAccept = onAccept;
        pane.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                scheduleAutoUpdate();
            }
            @Override public void removeUpdate(DocumentEvent e) {
                scheduleAutoUpdate();
            }
            @Override public void changedUpdate(DocumentEvent e) {
                // 属性変更 (シンタックスハイライト) では反応しない。
            }
        });
        // クリック等でキャレットが別の語へ動いたとき、古い位置の候補を出しっぱなしにしない
        // (残ったまま確定すると無関係な語への誤挿入になる)。同じ語内のタイプ継続では
        // 語頭が変わらないため隠さず、DocumentListener 経由の更新が絞り込みを行う。
        pane.addCaretListener(e -> {
            if (!isVisible()) {
                return;
            }
            if (e.getDot() - typedPrefix(text(), e.getDot()).length() != shownWordStart) {
                hide();
            }
        });
        pane.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                hide();
            }
        });
        // スクロールやウィンドウ移動で位置がずれたポップアップを出しっぱなしにしない
        // (JWindow はエディタに追従しないため、古いスクリーン座標に浮き続ける)。
        pane.addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override public void ancestorMoved(javax.swing.event.AncestorEvent event) {
                hide();
            }
            @Override public void ancestorAdded(javax.swing.event.AncestorEvent event) {
            }
            @Override public void ancestorRemoved(javax.swing.event.AncestorEvent event) {
                hide();
            }
        });
        installKeys();
    }

    /**
     * キャレット直前の「打ちかけ」。語が無ければ矢印記法の打ちかけを見る
     * ({@code A -} まで打った時点で矢印候補を出せるようにする)。
     */
    private static String typedPrefix(String text, int caret) {
        String word = PumlCompletion.wordPrefix(text, caret);
        return word.isEmpty() ? PumlCompletion.arrowPrefix(text, caret) : word;
    }

    /** ポップアップウィンドウを破棄する (エディタタブのクローズ時に呼ぶ)。 */
    void dispose() {
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    /** ドキュメント変更通知の中からは UI を触れないため、イベント後に更新する。 */
    private void scheduleAutoUpdate() {
        if (suppressAutoShow) {
            return;
        }
        SwingUtilities.invokeLater(() -> updateCandidates(false));
    }

    /** Ctrl+Space の明示起動 (接頭辞ゼロでも表示する)。 */
    void showNow() {
        updateCandidates(true);
    }

    /**
     * キャレット位置の接頭辞で候補を計算しポップアップを更新する。
     * {@code explicit} でなければ {@value #MIN_PREFIX} 文字未満では表示しない。
     */
    private void updateCandidates(boolean explicit) {
        if (!pane.isEditable() || (!explicit && !pane.hasFocus() && !assumeFocused)) {
            hide();
            return;
        }
        String text = text();
        int caret = pane.getCaretPosition();
        String prefix = typedPrefix(text, caret);
        // 明示起動 (Ctrl+Space) は接頭辞ゼロでも「その文脈の全候補」を出す (VS Code 相当)。
        // 入力追従の暗黙起動だけ最低文字数 (MIN_PREFIX) を要求してノイズを抑える。
        if (!explicit && prefix.length() < MIN_PREFIX) {
            hide();
            return;
        }
        List<PumlCompletionItem> candidates = PumlCompletion.items(text, caret, explicit);
        if (candidates.isEmpty()) {
            hide();
            return;
        }
        model.clear();
        for (PumlCompletionItem c : candidates) {
            model.addElement(c);
        }
        shownWordStart = caret - prefix.length();
        ensureWindow();
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(VISIBLE_ROWS, model.size()));
        list.setFixedCellWidth(widestRow());
        // スクリーンリーダー向けに、候補件数と先頭候補をエディタの accessible name へ
        // 反映する (フォーカスを奪わない JWindow の候補リストは単独では読み上げられない
        // ため、フォーカスのあるエディタ側で件数・選択候補をアナウンスできるようにする)。
        announceSelection();
        window.pack();
        Point at = popupLocation(caret);
        if (at == null) {
            hide();
            return;
        }
        window.setLocation(at);
        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    /**
     * 全候補を実際に描いてみたときの最大幅 (+ 余白)。
     *
     * <p>{@link JList} 任せの幅計算だと、日本語の補足がフォント代替で描かれる際に
     * 実際の描画幅が計測値を上回り、行末が切れることがある。候補は多くても
     * {@value PumlCompletion#MAX_CANDIDATES} 件なので、全行を測って明示的に決める。</p>
     */
    private int widestRow() {
        ListCellRenderer<? super PumlCompletionItem> renderer = list.getCellRenderer();
        int width = 0;
        for (int i = 0; i < model.size(); i++) {
            Component c = renderer.getListCellRendererComponent(
                    list, model.get(i), i, false, false);
            width = Math.max(width, c.getPreferredSize().width);
        }
        return width + ROW_WIDTH_SLACK;
    }

    /** キャレット行の直下のスクリーン座標。解決できなければ null。 */
    private Point popupLocation(int caret) {
        try {
            java.awt.geom.Rectangle2D r = pane.modelToView2D(caret);
            if (r == null || !pane.isShowing()) {
                return null;
            }
            Point p = new Point((int) r.getX(), (int) (r.getY() + r.getHeight()));
            SwingUtilities.convertPointToScreen(p, pane);
            return p;
        } catch (BadLocationException ex) {
            return null;
        }
    }

    private void ensureWindow() {
        if (window != null) {
            return;
        }
        list = new JList<>(model);
        list.setFocusable(false);
        list.setCellRenderer(new ItemRenderer());
        // クリックでも確定できるようにする (キーボードが主動線だがマウスも拒まない)。
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                accept();
            }
        });
        java.awt.Window owner = SwingUtilities.getWindowAncestor(pane);
        window = new JWindow(owner);
        window.setFocusableWindowState(false);
        window.add(new JScrollPane(list));
        window.setAlwaysOnTop(true);
    }

    boolean isVisible() {
        return window != null && window.isVisible();
    }

    void hide() {
        if (window != null && window.isVisible()) {
            window.setVisible(false);
            clearAnnouncement();
        }
    }

    /** 現在選択中の候補で確定する。 */
    private void accept() {
        if (!isVisible() || list.getSelectedValue() == null) {
            return;
        }
        PumlCompletionItem item = list.getSelectedValue();
        hide();
        // 接頭辞は確定の瞬間に取り直す (表示時点のものは打鍵で陳腐化している)。
        String prefix = item.kind() == PumlCompletionItem.Kind.ARROW
                ? PumlCompletion.arrowPrefix(text(), pane.getCaretPosition())
                : PumlCompletion.wordPrefix(text(), pane.getCaretPosition());
        suppressAutoShow = true;
        try {
            onAccept.accept(prefix, item);
        } finally {
            suppressAutoShow = false;
        }
    }

    private String text() {
        try {
            return pane.getDocument().getText(0, pane.getDocument().getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // 描画
    // -------------------------------------------------------------------------

    /** 「見出し + 補足」の 2 段組で 1 候補を描く。 */
    private static final class ItemRenderer extends JPanel
            implements ListCellRenderer<PumlCompletionItem> {

        private final JLabel label = new JLabel();
        private final JLabel detail = new JLabel();

        ItemRenderer() {
            super(new BorderLayout(12, 0));
            setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            label.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            detail.setFont(detail.getFont().deriveFont(Font.PLAIN, 11f));
            add(label, BorderLayout.WEST);
            add(detail, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PumlCompletionItem> list,
                                                      PumlCompletionItem value, int index,
                                                      boolean selected, boolean focused) {
            Color bg = selected ? list.getSelectionBackground() : list.getBackground();
            Color fg = selected ? list.getSelectionForeground() : list.getForeground();
            setBackground(bg);
            setOpaque(true);
            // 展開ものは太字にして「これは丸ごと入る候補」と一目で分かるようにする。
            label.setFont(label.getFont().deriveFont(
                    value.isTemplate() ? Font.BOLD : Font.PLAIN));
            label.setText(value.label());
            label.setForeground(fg);
            detail.setText(kindTag(value) + value.detail());
            // 補足は主役ではないので、選択時も本文より一段落とした色にする。
            detail.setForeground(selected ? fg : dim(fg, list.getBackground()));
            return this;
        }

        /** 候補種別の短い見出し (スニペットと語を混ぜて出すため識別子が要る)。 */
        private static String kindTag(PumlCompletionItem item) {
            switch (item.kind()) {
                case SNIPPET:    return Messages.get("puml.completion.kind.snippet") + "  ";
                case ARROW:      return Messages.get("puml.completion.kind.arrow") + "  ";
                case IDENTIFIER: return Messages.get("puml.completion.kind.identifier") + "  ";
                // 引数値の補足は「!theme のテーマ名」のように何の値かまで言うので、
                // 種別見出しを重ねても幅が増えるだけになる。
                case VALUE:
                case KEYWORD:
                default:         return "";
            }
        }

        /** 前景色を背景側へ寄せた控えめな色。 */
        private static Color dim(Color fg, Color bg) {
            return new Color((fg.getRed() + bg.getRed()) / 2,
                    (fg.getGreen() + bg.getGreen()) / 2,
                    (fg.getBlue() + bg.getBlue()) / 2);
        }
    }

    // -------------------------------------------------------------------------
    // キー配線: ポップアップ表示中だけ挙動を差し替え、非表示時は元のアクションへ委譲する。
    // -------------------------------------------------------------------------

    private void installKeys() {
        InputMap im = pane.getInputMap();
        ActionMap am = pane.getActionMap();
        delegate(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "juml-comp-up",
                () -> move(-1));
        delegate(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "juml-comp-down",
                () -> move(1));
        delegate(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "juml-comp-enter",
                this::accept);
        delegate(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "juml-comp-tab",
                this::accept);
        delegate(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "juml-comp-esc",
                this::hide);
        // Ctrl+Space の明示起動。
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK),
                "juml-complete");
        am.put("juml-complete", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                showNow();
            }
        });
    }

    /**
     * キーを {@code name} のアクションへ束縛する。ポップアップ表示中は {@code whenVisible} を
     * 実行し、非表示時はこの束縛前に登録されていた既存アクション (既定のキャレット移動・
     * 改行・インデント等) をそのまま実行する。
     */
    private void delegate(InputMap im, ActionMap am, KeyStroke ks, String name,
                          Runnable whenVisible) {
        Object prevKey = im.get(ks);
        javax.swing.Action prev = prevKey != null ? am.get(prevKey) : null;
        // InputMap に無い場合でも JTextComponent の Keymap 既定動作 (文字入力・改行) が
        // あるため、既定動作へ委譲するには「削除して再送」はできない。既定挙動が
        // アクションとして取れないキー (Esc など) では prev=null → 何もしない。
        javax.swing.Action fallback = prev != null ? prev
                : pane.getActionMap().get(defaultActionFor(ks));
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (isVisible()) {
                    whenVisible.run();
                } else if (fallback != null) {
                    fallback.actionPerformed(e);
                }
            }
        });
    }

    /** 既定アクション名 (DefaultEditorKit) へのフォールバック解決。 */
    private static String defaultActionFor(KeyStroke ks) {
        switch (ks.getKeyCode()) {
            case KeyEvent.VK_UP:    return javax.swing.text.DefaultEditorKit.upAction;
            case KeyEvent.VK_DOWN:  return javax.swing.text.DefaultEditorKit.downAction;
            case KeyEvent.VK_ENTER: return javax.swing.text.DefaultEditorKit.insertBreakAction;
            case KeyEvent.VK_TAB:   return javax.swing.text.DefaultEditorKit.insertTabAction;
            default:                return "";
        }
    }

    private void move(int delta) {
        int size = model.size();
        if (size == 0) {
            return;
        }
        int idx = Math.max(0, Math.min(size - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(idx);
        list.ensureIndexIsVisible(idx);
        announceSelection();
    }

    /**
     * 現在の選択候補と件数をエディタの accessible name へ反映する
     * (フォーカスを持つエディタ経由でスクリーンリーダーへ選択変化を伝える)。
     */
    private void announceSelection() {
        if (list == null || model.isEmpty()) {
            return;
        }
        PumlCompletionItem sel = list.getSelectedValue();
        String announce = java.text.MessageFormat.format(
                Messages.get("puml.completion.a11y"),
                sel != null ? sel.label() : "", list.getSelectedIndex() + 1, model.size());
        pane.getAccessibleContext().setAccessibleDescription(announce);
    }

    /** 補完ポップアップを閉じたら、エディタの補完アナウンスもクリアする。 */
    private void clearAnnouncement() {
        pane.getAccessibleContext().setAccessibleDescription(null);
    }

    /** テスト用: 現在表示中の候補数 (非表示なら 0)。 */
    int visibleCandidateCountForTest() {
        return isVisible() ? model.size() : 0;
    }

    /** テスト用: 現在表示中の候補見出し (非表示なら空)。 */
    List<String> visibleLabelsForTest() {
        List<String> out = new java.util.ArrayList<>();
        if (isVisible()) {
            for (int i = 0; i < model.size(); i++) {
                out.add(model.get(i).label());
            }
        }
        return out;
    }

    /**
     * テスト用: 自動表示の更新を同期実行する。
     *
     * <p>入力追従の表示はペインがフォーカスを持つことを前提にするが、ウィンドウ
     * マネージャの無い環境 (Xvfb) では実フォーカスを得られない。フォーカスは
     * 「いつ呼ばれるか」の前提条件であって候補生成の一部ではないので、ここでだけ
     * 満たされたものとみなし、絞り込みと表示の中身を検証できるようにする。</p>
     */
    void updateForTest(boolean explicit) {
        assumeFocused = true;
        try {
            updateCandidates(explicit);
        } finally {
            assumeFocused = false;
        }
    }
}
