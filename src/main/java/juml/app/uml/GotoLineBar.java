// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;

/**
 * ソースパネル内のインライン行移動バー (VS Code の {@code Ctrl+G} 相当)。
 *
 * <p>{@code Ctrl+G} で表示し、行番号を入力して {@code Enter} で移動、
 * {@code Esc} で閉じる。{@code JOptionPane} のモーダルダイアログではなく
 * エディタ内に埋め込まれたインラインウィジェットとして動作する。</p>
 */
final class GotoLineBar extends JPanel {

    private final IntConsumer onJump;
    private final Runnable onLayoutChange;
    private final JTextField field;
    private final JLabel info;

    GotoLineBar(IntConsumer onJump, Runnable onLayoutChange) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));
        this.onJump = onJump;
        this.onLayoutChange = onLayoutChange;
        Color sep = javax.swing.UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                sep != null ? sep : new Color(0xCCCCCC)));

        JLabel lbl = new JLabel(Messages.get("source.goto.label") + ":");
        field = new JTextField(10);
        info = new JLabel(" ");
        Color infoFg = javax.swing.UIManager.getColor("Label.disabledForeground");
        info.setForeground(infoFg != null ? infoFg : new Color(0x777777));

        field.registerKeyboardAction(e -> commit(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
        field.registerKeyboardAction(e -> close(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

        add(lbl);
        add(field);
        add(info);
        setVisible(false);
    }

    void activate(int currentLine, int maxLine) {
        info.setText("(1-" + maxLine + ")");
        field.setText(String.valueOf(currentLine));
        field.putClientProperty("JTextField.placeholderText",
                Messages.get("source.goto.placeholder"));
        setVisible(true);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
        field.requestFocusInWindow();
        field.selectAll();
    }

    private void commit() {
        String text = field.getText().trim();
        try {
            int line = Integer.parseInt(text);
            onJump.accept(line);
        } catch (NumberFormatException ignored) {
            // 数値以外は無視
        }
        close();
    }

    private void close() {
        setVisible(false);
        if (onLayoutChange != null) {
            onLayoutChange.run();
        }
    }
}
