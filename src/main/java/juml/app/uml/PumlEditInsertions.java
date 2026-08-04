// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * エディタへの雛形挿入と、挿入後の「穴埋め移動」(タブストップ) を担う。
 *
 * <p>補完ポップアップからの確定と、挿入パレットからの挿入の双方がここを通る。
 * どちらも {@link PumlSnippetTemplate} でテンプレートを展開するため、パレット経由でも
 * {@code Tab} での穴埋め移動が効く。</p>
 *
 * <p>タブストップは {@link Position} で保持する。挿入した雛形の穴を先頭から埋めていく
 * 間、手前の穴の長さが変わると後ろの穴の位置がずれるが、{@link Position} は文書の編集に
 * 追従するのでオフセットを持ち回るより壊れにくい。</p>
 */
final class PumlEditInsertions {

    private final JTextComponent pane;
    /** 編集を 1 個の複合編集 (Undo 1 回で戻せる単位) にまとめるための委譲。 */
    private final Consumer<Runnable> compound;

    /** 巡回中のタブストップ ({@code {開始, 終了}} の {@link Position} 対)。 */
    private final List<Position[]> stops = new ArrayList<>();
    /** 次に訪れるタブストップの添字。 */
    private int stopIndex;
    /** 雛形全体の範囲 (キャレットがここを出たらタブストップ巡回を終える)。 */
    private Position regionStart;
    private Position regionEnd;
    /** プログラムによるキャレット移動中は「範囲外へ出た」判定をしない。 */
    private boolean moving;

    PumlEditInsertions(JTextComponent pane, Consumer<Runnable> compound) {
        this.pane = pane;
        this.compound = compound;
        pane.addCaretListener(e -> {
            if (!moving && isActive() && outsideRegion(e.getDot())) {
                // 雛形の外を触りだしたら穴埋めモードは終わり。Tab を奪い続けると
                // 通常のインデントが効かなくなる。
                cancel();
            }
        });
    }

    /**
     * {@code Tab} / {@code Shift+Tab} / {@code Esc} を配線する。タブストップ巡回中だけ
     * 動作を奪い、そうでなければこの配線より前に登録されていた動作 (インデント等) へ委譲する。
     * 補完ポップアップはこの後にインストールし、ポップアップ表示中はそちらを優先させる。
     */
    void install(InputMap im, ActionMap am) {
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "juml-tabstop-next",
                this::next);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                "juml-tabstop-prev", this::previous);
        bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "juml-tabstop-cancel",
                this::cancelIfActive);
    }

    private void bind(InputMap im, ActionMap am, KeyStroke ks, String name,
                      java.util.function.BooleanSupplier whenActive) {
        Object prevKey = im.get(ks);
        javax.swing.Action prev = prevKey != null ? am.get(prevKey) : null;
        im.put(ks, name);
        am.put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (whenActive.getAsBoolean()) {
                    return;
                }
                if (prev != null) {
                    prev.actionPerformed(e);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // 挿入
    // -------------------------------------------------------------------------

    /** キャレット位置へテンプレートを挿入する (挿入パレット用)。 */
    void insertTemplate(String template) {
        if (!pane.isEditable() || template == null || template.isEmpty()) {
            return;
        }
        int at = clamp(pane.getCaretPosition());
        replaceWithTemplate(at, at, template);
    }

    /**
     * 打ちかけの語 (キャレット直前の {@code prefix}) を補完候補で置き換える。
     * 語中で確定した場合はキャレット後方の語の残りも含めて置換し、
     * {@code classa} のような残余崩れを防ぐ。
     */
    void insertCompletion(int at, String prefix, PumlCompletionItem item) {
        if (!pane.isEditable() || item == null) {
            return;
        }
        // 陳腐化したポップアップからの誤挿入ガード: いまキャレット直前にある打ちかけから
        // その候補が出てくる余地が無いなら、無関係な語を潰しにいかない。前方一致だけでなく
        // あいまい一致も候補生成に使うため、判定は生成側と同じ照合 (matchScore) で行う。
        if (PumlCompletion.matchScore(item.label(), prefix) < 0) {
            return;
        }
        String text = textOf();
        int caret = clamp(at);
        int start = Math.max(0, caret - prefix.length());
        int end = item.kind() == PumlCompletionItem.Kind.ARROW
                ? PumlCompletion.arrowEnd(text, caret) : PumlCompletion.wordEnd(text, caret);
        replaceWithTemplate(start, Math.max(start, end), item.insert());
    }

    /**
     * {@code [start, end)} をテンプレートの展開結果で置き換え、タブストップがあれば
     * 最初の穴を選択状態にする。無ければ挿入末尾へキャレットを置く。
     */
    private void replaceWithTemplate(int start, int end, String template) {
        cancel();
        Document doc = pane.getDocument();
        String indent = indentOfLineAt(textOf(), start);
        PumlSnippetTemplate.Expansion ex = PumlSnippetTemplate.expand(template, indent);
        String body = ex.text();
        // remove + insert を 1 個の複合編集にまとめ、Ctrl+Z 1 回で確定前へ戻せるようにする
        // (分かれていると 1 回目の Undo で接頭辞ごと消える)。
        boolean[] ok = {false};
        compound.accept(() -> {
            try {
                if (end > start) {
                    doc.remove(start, end - start);
                }
                doc.insertString(start, body, null);
                ok[0] = true;
            } catch (BadLocationException ignored) {
                // 競合編集で範囲がずれた場合は何もしない (致命的でない)。
            }
        });
        if (!ok[0]) {
            return;
        }
        moving = true;
        try {
            if (ex.stops().isEmpty()) {
                pane.setCaretPosition(clamp(start + body.length()));
            } else {
                arm(doc, start, body.length(), ex.stops());
            }
        } finally {
            moving = false;
        }
        pane.requestFocusInWindow();
    }

    /** タブストップを {@link Position} で記録し、最初の穴を選択する。 */
    private void arm(Document doc, int base, int length, List<int[]> relative) {
        try {
            regionStart = doc.createPosition(base);
            regionEnd = doc.createPosition(clamp(base + length));
            for (int[] r : relative) {
                stops.add(new Position[] {
                        doc.createPosition(clamp(base + r[0])),
                        doc.createPosition(clamp(base + r[1]))});
            }
        } catch (BadLocationException ex) {
            cancel();
            return;
        }
        stopIndex = -1;
        next();
    }

    // -------------------------------------------------------------------------
    // タブストップ巡回
    // -------------------------------------------------------------------------

    /** 巡回中か。 */
    boolean isActive() {
        return !stops.isEmpty();
    }

    /** 次の穴へ。最後の穴を過ぎたら巡回を終える。巡回していなければ false。 */
    boolean next() {
        return step(1);
    }

    /** 前の穴へ。先頭より前へは戻らない。巡回していなければ false。 */
    boolean previous() {
        return step(-1);
    }

    private boolean step(int delta) {
        if (!isActive()) {
            return false;
        }
        int target = stopIndex + delta;
        if (target < 0) {
            target = 0;
        }
        if (target >= stops.size()) {
            // 最後の穴を埋め終わった。キャレットはそのままに巡回だけ終える。
            cancel();
            return true;
        }
        stopIndex = target;
        Position[] p = stops.get(stopIndex);
        moving = true;
        try {
            pane.setCaretPosition(clamp(p[0].getOffset()));
            pane.moveCaretPosition(clamp(p[1].getOffset()));
        } finally {
            moving = false;
        }
        return true;
    }

    /** 巡回中なら終える (Esc)。巡回していなければ false を返し、既定動作へ譲る。 */
    private boolean cancelIfActive() {
        if (!isActive()) {
            return false;
        }
        cancel();
        return true;
    }

    /** 巡回状態を捨てる。 */
    void cancel() {
        stops.clear();
        stopIndex = -1;
        regionStart = null;
        regionEnd = null;
    }

    /** テスト用: 残っているタブストップ数 (巡回していなければ 0)。 */
    int remainingStopsForTest() {
        return isActive() ? stops.size() - Math.max(0, stopIndex) : 0;
    }

    // -------------------------------------------------------------------------
    // 補助
    // -------------------------------------------------------------------------

    private boolean outsideRegion(int dot) {
        if (regionStart == null || regionEnd == null) {
            return true;
        }
        return dot < regionStart.getOffset() || dot > regionEnd.getOffset();
    }

    /** {@code at} を含む行の字下げ (挿入する雛形を現在行に揃えるのに使う)。 */
    private static String indentOfLineAt(String text, int at) {
        int pos = Math.max(0, Math.min(at, text.length()));
        int lineStart = text.lastIndexOf('\n', Math.max(0, pos - 1)) + 1;
        int i = lineStart;
        while (i < pos && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(lineStart, i);
    }

    private int clamp(int offset) {
        return Math.max(0, Math.min(offset, pane.getDocument().getLength()));
    }

    private String textOf() {
        try {
            return pane.getDocument().getText(0, pane.getDocument().getLength());
        } catch (BadLocationException ex) {
            return "";
        }
    }
}
