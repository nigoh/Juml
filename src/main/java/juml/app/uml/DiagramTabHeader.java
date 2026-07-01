// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * ダイアグラムタブのヘッダ (アイコン + ラベル + × ボタン) を組み立てるヘルパ。
 *
 * <p>操作は呼び出し側のコールバックへ委譲する: 閉じる ({@code onClose})、
 * コンテキストメニュー ({@code onPopup})、左クリックでの選択 ({@code onSelect})。
 * UI 構築のみを担い、タブの状態は持たない。</p>
 */
final class DiagramTabHeader {

    /** タイトル {@link JLabel} を後から差し替えるための識別名。 */
    private static final String TITLE_NAME = "juml.tabTitle";
    /** プレビュー表示前の元ツールチップを退避しておくクライアントプロパティキー。 */
    private static final String BASE_TOOLTIP = "juml.tabBaseTooltip";

    private DiagramTabHeader() {
    }

    /**
     * タブヘッダのタイトルテキストを差し替える (同名タブのパンくず付与/解除に使う)。
     * ヘッダ構造を知らなくても名前 {@link #TITLE_NAME} で対象ラベルを探す。
     */
    static void updateTitle(java.awt.Component header, String text) {
        JLabel label = findTitle(header);
        if (label != null) {
            label.setText(text);
            label.getParent().revalidate();
            label.getParent().repaint();
        }
    }

    static void setPreview(java.awt.Component header, boolean preview) {
        JLabel label = findTitle(header);
        if (label != null) {
            int style = preview ? Font.ITALIC : Font.PLAIN;
            label.setFont(label.getFont().deriveFont(style, 11f));
            label.getParent().repaint();
        }
        // プレビュータブは「ダブルクリックで固定」という操作を発見しにくいため、
        // ツールチップに案内を追記する (VS Code の Preview Tab 相当)。
        if (header instanceof javax.swing.JComponent) {
            applyPreviewTooltip((javax.swing.JComponent) header, preview);
        }
        if (label != null) {
            applyPreviewTooltip(label, preview);
        }
    }

    private static void applyPreviewTooltip(javax.swing.JComponent c, boolean preview) {
        String base = (String) c.getClientProperty(BASE_TOOLTIP);
        if (base == null) {
            base = c.getToolTipText();
            c.putClientProperty(BASE_TOOLTIP, base);
        }
        if (!preview || base == null) {
            c.setToolTipText(base);
            return;
        }
        c.setToolTipText(base + " — " + juml.util.Messages.get("tab.preview.pinHint"));
    }

    private static JLabel findTitle(java.awt.Component header) {
        if (!(header instanceof java.awt.Container)) {
            return null;
        }
        for (java.awt.Component c : ((java.awt.Container) header).getComponents()) {
            if (c instanceof JLabel && TITLE_NAME.equals(c.getName())) {
                return (JLabel) c;
            }
        }
        return null;
    }

    /** タブの×アイコンのアイドル色 (テーマ追従)。 */
    private static Color closeIdle() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : new Color(0x888888);
    }

    /** タブの×アイコンのホバー色 (テーマのエラー色 → 無ければ赤)。 */
    private static Color closeHover() {
        Color c = UIManager.getColor("Component.error.focusedBorderColor");
        if (c == null) {
            c = UIManager.getColor("Actions.Red");
        }
        return c != null ? c : new Color(0xE53935);
    }

    static JPanel build(String label, TreeNodeIcon icon, String tooltip,
                        Runnable onClose, Consumer<MouseEvent> onPopup, Runnable onSelect,
                        Runnable onDoubleClick) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        header.setOpaque(false);
        header.setToolTipText(tooltip);
        if (icon != null) {
            header.add(new JLabel(icon));
        }
        JLabel title = new JLabel(label);
        title.setName(TITLE_NAME);
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 11f));
        title.setToolTipText(tooltip);
        header.add(title);
        header.add(buildCloseButton(onClose));

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    onClose.run();
                } else if (e.isPopupTrigger()) {
                    onPopup.accept(e);
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    onSelect.run();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    onPopup.accept(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2
                        && onDoubleClick != null) {
                    onDoubleClick.run();
                }
            }
        };
        header.addMouseListener(ma);
        title.addMouseListener(ma);
        return header;
    }

    private static JButton buildCloseButton(Runnable onClose) {
        // フォント任せの "×" 文字ではなく、Material の close グリフをベクター描画する
        // (HiDPI でもサイズ・太さが一定で、テーマ色に追従する)。
        javax.swing.Icon idleIcon = MaterialIcons.of(MaterialIcons.Glyph.CLOSE, 12, closeIdle());
        javax.swing.Icon hoverIcon = MaterialIcons.of(MaterialIcons.Glyph.CLOSE, 12, closeHover());
        JButton close = new JButton(idleIcon);
        close.setMargin(new Insets(1, 2, 1, 2));
        close.setFocusable(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setToolTipText(juml.util.Messages.get("tab.closeTooltip"));
        close.getAccessibleContext().setAccessibleName(juml.util.Messages.get("tab.closeTooltip"));
        // ホバー時に赤くして「閉じる」操作であることを明示する。
        close.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                close.setIcon(hoverIcon);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                close.setIcon(idleIcon);
            }
        });
        close.addActionListener(e -> onClose.run());
        return close;
    }
}
