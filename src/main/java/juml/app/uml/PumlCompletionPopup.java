// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
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
 * <p>タイプ中の語 ({@link PumlCompletion#wordPrefix}) が {@value #MIN_PREFIX} 文字以上に
 * なると候補リストを自動表示し、入力の継続で絞り込む。{@code Up/Down} で選択、
 * {@code Enter}/{@code Tab} で確定、{@code Esc} で閉じる。{@code Ctrl+Space} での
 * 明示起動 (1 文字から) にも対応する。</p>
 *
 * <p>ポップアップはフォーカスを奪わない {@link JWindow} で、ヘッドレス環境でも
 * インストール自体は安全なよう遅延生成する。確定時の挿入は呼び出し側から渡される
 * コールバック (キャレット位置・接頭辞・候補) 経由で行う。</p>
 */
final class PumlCompletionPopup {

    /** 自動表示を始める接頭辞の最小文字数。 */
    static final int MIN_PREFIX = 2;

    /** 一度に見せる候補行数 (それ以上はスクロール)。 */
    private static final int VISIBLE_ROWS = 8;

    private final JTextComponent pane;
    /** 確定時の挿入先: (接頭辞, 候補) を受け取り本文へ反映する。 */
    private final BiConsumer<String, String> onAccept;

    private JWindow window;
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private JList<String> list;
    /** プログラム起因のドキュメント変更 (確定挿入など) 中は自動表示を抑止する。 */
    private boolean suppressAutoShow;

    PumlCompletionPopup(JTextComponent pane, BiConsumer<String, String> onAccept) {
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
        // クリック等でキャレットだけ動いたとき、古い位置の候補を出しっぱなしにしない。
        pane.addCaretListener(e -> {
            if (isVisible() && PumlCompletion.wordPrefix(text(), e.getDot()).isEmpty()) {
                hide();
            }
        });
        pane.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                hide();
            }
        });
        installKeys();
    }

    /** ドキュメント変更通知の中からは UI を触れないため、イベント後に更新する。 */
    private void scheduleAutoUpdate() {
        if (suppressAutoShow) {
            return;
        }
        SwingUtilities.invokeLater(() -> updateCandidates(false));
    }

    /** Ctrl+Space の明示起動 (1 文字から表示する)。 */
    void showNow() {
        updateCandidates(true);
    }

    /**
     * キャレット位置の接頭辞で候補を計算しポップアップを更新する。
     * {@code explicit} でなければ {@value #MIN_PREFIX} 文字未満では表示しない。
     */
    private void updateCandidates(boolean explicit) {
        if (!pane.isEditable() || (!explicit && !pane.hasFocus())) {
            hide();
            return;
        }
        String text = text();
        int caret = pane.getCaretPosition();
        String prefix = PumlCompletion.wordPrefix(text, caret);
        int min = explicit ? 1 : MIN_PREFIX;
        if (prefix.length() < min) {
            hide();
            return;
        }
        List<String> candidates = PumlCompletion.candidates(prefix, text);
        candidates = candidates.stream().filter(c -> !c.equals(prefix)).toList();
        if (candidates.isEmpty()) {
            hide();
            return;
        }
        model.clear();
        for (String c : candidates) {
            model.addElement(c);
        }
        ensureWindow();
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(VISIBLE_ROWS, model.size()));
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
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        list.setFocusable(false);
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
        }
    }

    /** 現在選択中の候補で確定する。 */
    private void accept() {
        if (!isVisible() || list.getSelectedValue() == null) {
            return;
        }
        String candidate = list.getSelectedValue();
        hide();
        String prefix = PumlCompletion.wordPrefix(text(), pane.getCaretPosition());
        suppressAutoShow = true;
        try {
            onAccept.accept(prefix, candidate);
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
    }

    /** テスト用: 現在表示中の候補数 (非表示なら 0)。 */
    int visibleCandidateCountForTest() {
        return isVisible() ? model.size() : 0;
    }

    /** テスト用: 自動表示の更新を同期実行する。 */
    void updateForTest(boolean explicit) {
        updateCandidates(explicit);
    }
}
