// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * ステータスバーの構築と「題材 · 図種」常時表示を担う小さなビュー。
 *
 * <p>左に題材/図種 (アクティブタブ連動)、中央に汎用メッセージラベル、
 * 右に進捗バーとズーム率を配置する。状態ラベル類は {@link UmlMainFrame} が保持し、
 * このビューはレイアウトと題材表示の更新だけを担う。</p>
 */
final class StatusBarView {

    private final JLabel subject = new JLabel(" ");
    private final JComponent component;

    StatusBarView(JComponent statusLabel, JComponent loadProgress, JComponent zoomLabel) {
        Color subtle = UIManager.getColor("Label.disabledForeground");
        subject.setForeground(subtle != null ? subtle : new Color(0x555555));
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        // 上辺に区切り線を入れて VS Code のステータスバーのように本文領域と分離する。
        Color sep = UIManager.getColor("Separator.foreground");
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        sep != null ? sep : new Color(0xBDBDBD)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        bar.add(subject, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.CENTER);
        JPanel east = new JPanel(new BorderLayout(8, 0));
        east.add(loadProgress, BorderLayout.WEST);
        east.add(zoomLabel, BorderLayout.EAST);
        bar.add(east, BorderLayout.EAST);
        this.component = bar;
    }

    /** ステータスバー本体 (SOUTH へ配置する)。 */
    JComponent getComponent() {
        return component;
    }

    /** アクティブタブのフォーカス情報から「題材 · 図種」を更新する。 */
    void setFocusedTab(DiagramTabPane.FocusedTab info) {
        if (info == null || info.kind == null) {
            subject.setText(" ");
            subject.setIcon(null);
            return;
        }
        String subj = info.treeSync != null ? info.treeSync.displayLabel() : "";
        String kind = ToolBarBuilder.toolbarLabel(info.kind);
        subject.setText(subj.isEmpty() ? kind : subj + "  ·  " + kind);
        // 図種アイコンを左に添えて、いま見ている図の種類をひと目で分かるようにする。
        subject.setIcon(ToolBarBuilder.kindIcon(info.kind, 14));
        subject.setIconTextGap(5);
    }
}
