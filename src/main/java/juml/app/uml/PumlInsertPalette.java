// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 自由編集エディタの「挿入」パレット (ツールバーボタンから開くポップアップ)。
 *
 * <p>2 段構えで、上に <b>選択範囲を囲む</b> ({@link PumlSurrounds})、下に
 * 図種別の<b>スニペット挿入</b> ({@link PumlSnippets}) を並べる。囲む側を上に置くのは、
 * 選択があるときはたいてい「この行を括りたい」からで、無いときは項目自体を伏せる。</p>
 *
 * <p>メニューは開くたびに組み直す。囲みの並びは編集中の図種で変わり、選択の有無でも
 * 出し分けるため、構築時に固定してしまうと実態と合わなくなる。</p>
 */
final class PumlInsertPalette {

    /** 雛形を現在のキャレット位置へ挿入する。 */
    private final Consumer<String> onInsert;
    /** 選択している行を雛形で囲む。 */
    private final Consumer<String> onSurround;
    /** いま編集している本文 (図種の判定に使う)。 */
    private final Supplier<String> textSupplier;
    /** 選択があるか (囲む項目を出すかの判定)。 */
    private final Supplier<Boolean> hasSelection;

    PumlInsertPalette(Consumer<String> onInsert, Consumer<String> onSurround,
                      Supplier<String> textSupplier, Supplier<Boolean> hasSelection) {
        this.onInsert = onInsert;
        this.onSurround = onSurround;
        this.textSupplier = textSupplier;
        this.hasSelection = hasSelection;
    }

    /** いまの状態に合わせてポップアップを組み立てる。 */
    JPopupMenu build() {
        JPopupMenu menu = new JPopupMenu();
        if (Boolean.TRUE.equals(hasSelection.get())) {
            JMenu surround = new JMenu(Messages.get("puml.surround.menu"));
            for (PumlSurrounds.Surround s : PumlSurrounds.forFlavor(currentFlavor())) {
                JMenuItem item = new JMenuItem(s.displayName());
                item.addActionListener(e -> onSurround.accept(s.body()));
                surround.add(item);
            }
            menu.add(surround);
            menu.addSeparator();
        }
        for (PumlSnippets.Group g : PumlSnippets.Group.values()) {
            JMenu sub = new JMenu(g.displayName());
            for (PumlSnippets.Snippet snip : PumlSnippets.forGroup(g)) {
                JMenuItem item = new JMenuItem(snip.displayName());
                item.addActionListener(e -> onInsert.accept(snip.body()));
                sub.add(item);
            }
            menu.add(sub);
        }
        return menu;
    }

    /**
     * 選択が無いときでも使える「囲む」だけのポップアップ (ショートカット起動用)。
     * 選択が無ければキャレット行が対象になる。
     */
    JPopupMenu buildSurroundOnly() {
        JPopupMenu menu = new JPopupMenu();
        for (PumlSurrounds.Surround s : PumlSurrounds.forFlavor(currentFlavor())) {
            JMenuItem item = new JMenuItem(s.displayName());
            item.addActionListener(e -> onSurround.accept(s.body()));
            menu.add(item);
        }
        return menu;
    }

    /** 編集中の本文から推定した図種 (囲みの並び替えに使う)。 */
    private PumlSnippets.Group currentFlavor() {
        String text = textSupplier.get();
        if (text == null) {
            return PumlSnippets.Group.COMMON;
        }
        return PumlCompletionContext.at(text, text.length()).flavor();
    }
}
