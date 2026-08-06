// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.text.MessageFormat;
import java.util.List;
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
    /** 未宣言の参加者をまとめて宣言する (実行件数を返す)。 */
    private final Supplier<Integer> onDeclareMissing;
    /** ユーザー定義スニペットの保管庫。 */
    private final PumlUserSnippets userSnippets;
    /** 選択範囲をスニペットとして登録する。 */
    private final Runnable onRegisterSelection;
    /** 管理ダイアログを開く。 */
    private final Runnable onManage;
    /** 選択があるか (囲む項目を出すかの判定)。 */
    private final Supplier<Boolean> hasSelection;

    PumlInsertPalette(Consumer<String> onInsert, Consumer<String> onSurround,
                      Supplier<String> textSupplier, Supplier<Boolean> hasSelection,
                      Supplier<Integer> onDeclareMissing, PumlUserSnippets userSnippets,
                      Runnable onRegisterSelection, Runnable onManage) {
        this.onInsert = onInsert;
        this.onSurround = onSurround;
        this.textSupplier = textSupplier;
        this.hasSelection = hasSelection;
        this.onDeclareMissing = onDeclareMissing;
        this.userSnippets = userSnippets;
        this.onRegisterSelection = onRegisterSelection;
        this.onManage = onManage;
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
        // 未宣言の参加者があるときだけ、その場で直せる項目を出す。
        List<String> missing = PumlSymbols.undeclaredParticipants(textSupplier.get());
        if (!missing.isEmpty()) {
            JMenuItem fix = new JMenuItem(MessageFormat.format(
                    Messages.get("puml.fix.declareMissing"), missing.size()));
            fix.addActionListener(e -> onDeclareMissing.get());
            menu.add(fix);
            menu.addSeparator();
        }
        menu.add(userSnippetMenu());
        menu.addSeparator();
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
     * 自分で登録したスニペットの節。登録済みがあればそれを並べ、末尾に
     * 「選択範囲を登録」と「管理」を置く。空でも項目自体は出す (登録の入口が
     * どこにも無いと、機能があること自体に気づけない)。
     */
    private JMenu userSnippetMenu() {
        JMenu sub = new JMenu(Messages.get("puml.userSnip.menu"));
        for (PumlUserSnippets.Entry e : userSnippets.load()) {
            JMenuItem item = new JMenuItem(e.label());
            item.addActionListener(ev -> onInsert.accept(e.body()));
            sub.add(item);
        }
        if (sub.getItemCount() > 0) {
            sub.addSeparator();
        }
        JMenuItem register = new JMenuItem(Messages.get("puml.userSnip.register"));
        register.setEnabled(Boolean.TRUE.equals(hasSelection.get()));
        register.addActionListener(ev -> onRegisterSelection.run());
        sub.add(register);
        JMenuItem manage = new JMenuItem(Messages.get("puml.userSnip.manage"));
        manage.addActionListener(ev -> onManage.run());
        sub.add(manage);
        return sub;
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
