// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * アクションツールバー末尾に置く「図種ドロップダウン」。
 *
 * <p>以前はウィンドウ上部に図種切替専用のツールバー行 (3 段目) を設け、図種ごとに
 * トグルボタンを並べていた。メニューバー・アクション行と合わせて 3 段が積み重なり、
 * 縦の作業領域を圧迫していたため、その 1 行を「現在の図種を示すボタン 1 個 +
 * カテゴリ区切り付きポップアップ」へ畳んで上部を 2 段にした。</p>
 *
 * <p>ボタンのラベルは常に現在の図種を示すので、一覧を開かなくても状態を見失わない。
 * ポップアップと項目は生成時に一度だけ組み立てて使い回し、到達できない図種は
 * 項目を消さずに無効化する ({@link #setAvailableKinds})。</p>
 */
public final class DiagramKindChooser {

    /** ポップアップを開くツールバーボタン。ラベルは常に現在の図種を示す。 */
    private final JButton button;
    /** 図種一覧のポップアップ。生成時に一度だけ組み立て、以後は作り直さない。 */
    private final JPopupMenu popup;
    /** 図種 → メニュー項目。有効/無効の切り替え対象。 */
    private final EnumMap<DiagramKind, JMenuItem> items;
    /** いま選べる図種。プロジェクト未ロード時は空集合。 */
    private EnumSet<DiagramKind> allowed = EnumSet.allOf(DiagramKind.class);
    /** ボタンラベルが示している図種。null は図種を持たないタブ (自由編集エディタ)。 */
    private DiagramKind current;
    /** {@link #current} の初期化済みフラグ (null を初期値と区別するため)。 */
    private boolean kindInitialized;

    /**
     * {@link ToolBarBuilder} が組み立てたポップアップと項目を受け取って束ねる。
     * 項目の並び順・カテゴリ区切り・アイコン色は呼び出し側の責務。
     */
    DiagramKindChooser(DiagramKind initialKind, JPopupMenu popup,
                       EnumMap<DiagramKind, JMenuItem> items) {
        this.popup = popup;
        this.items = items;
        this.button = new JButton();
        this.button.addActionListener(e -> popup.show(button, 0, button.getHeight()));
        // 項目の有効/無効は allowed が唯一の真実。表示直前に再適用しておくと、
        // 項目生成と updateAvailableDiagrams の呼び出し順が将来入れ替わっても崩れない。
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                applyAllowed();
            }

            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        setCurrentKind(initialKind);
    }

    /** ツールバーへ載せるボタン。 */
    public JButton component() {
        return button;
    }

    /**
     * ボタンの表示を {@code kind} に合わせる (見た目のみ)。
     *
     * <p>ポップアップに項目を持たない図種 (メソッド系・レイアウトの画面/実寸) でも
     * ラベルには出す。それらはタブ上部の切替バーから選ぶが、現在の図種としては
     * 正しく示す必要があるため。{@code kind} が null (図種を持たない自由編集
     * エディタタブ) のときはプレースホルダを表示する。</p>
     */
    public void setCurrentKind(DiagramKind kind) {
        if (kindInitialized && kind == current) {
            return; // メニューラジオ経由の再入で無駄な再描画をしない
        }
        kindInitialized = true;
        current = kind;
        String label = kind != null ? ToolBarBuilder.toolbarLabel(kind)
                : Messages.get("toolbar.diagramKind.none");
        button.setText(MessageFormat.format(Messages.get("toolbar.diagramKind.format"),
                Messages.get("toolbar.diagramKind"), label));
        button.setIcon(kind != null ? ToolBarBuilder.kindIcon(kind, 16) : null);
        button.setToolTipText(MessageFormat.format(Messages.get("toolbar.diagramKind.tip"),
                kind != null ? kind.getDisplayName() : label));
        button.getAccessibleContext().setAccessibleName(button.getText());
    }

    /**
     * 選べる図種を差し替える。到達できない図種は項目を消さずに無効化し、
     * 1 つも選べない (プロジェクト未ロード) ならボタンごと無効化する。
     */
    public void setAvailableKinds(EnumSet<DiagramKind> allowed) {
        this.allowed = EnumSet.copyOf(allowed);
        applyAllowed();
    }

    private void applyAllowed() {
        for (Map.Entry<DiagramKind, JMenuItem> e : items.entrySet()) {
            e.getValue().setEnabled(allowed.contains(e.getKey()));
        }
        button.setEnabled(!allowed.isEmpty());
    }

    /** 図種に対応する一覧の項目。一覧に出ない図種 (メソッド系など) は null。 */
    public JMenuItem itemFor(DiagramKind kind) {
        return items.get(kind);
    }

    /** テスト用: 図種一覧のポップアップ。 */
    JPopupMenu popup() {
        return popup;
    }
}
