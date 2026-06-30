// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import juml.util.SystemFonts;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.Collections;
import java.util.List;

/**
 * Style 設定ダイアログのフォント選択コントロール。
 *
 * <p>システムにインストールされたフォントファミリから選べる編集可能コンボボックスと、
 * 選択中フォントでサンプル文字列を描画するプレビューラベルをまとめて提供する。
 * 一覧に無い任意のフォント名も入力でき、「自動検出」({@link #fontName()} が空文字) を
 * 先頭に置く。日本語対応フォントは上位にまとめて表示する。</p>
 */
final class FontPickerField {

    /** フォント未指定（自動検出）を表す表示用ラベル。実値は空文字。 */
    private static final String AUTO = "(Auto / 自動検出)";

    private final JComboBox<String> combo = new JComboBox<>();
    private final JLabel preview = new JLabel();

    /** コンボとプレビューを構築し、初期フォント名を反映する。 */
    FontPickerField(String initialFont) {
        combo.setEditable(true);
        combo.addItem(AUTO);
        List<String> families;
        try {
            families = SystemFonts.familiesJapaneseFirst();
        } catch (RuntimeException ex) {
            families = Collections.emptyList();
        }
        for (String fam : families) {
            combo.addItem(fam);
        }
        String init = initialFont == null ? "" : initialFont.trim();
        if (init.isEmpty()) {
            combo.setSelectedItem(AUTO);
        } else {
            combo.setSelectedItem(init);
            // 一覧に無い任意フォント名でも編集フィールドに反映させる
            if (!init.equals(fontName())) {
                combo.getEditor().setItem(init);
            }
        }
        combo.setToolTipText(Messages.get("fontPicker.comboTip"));
        // 選択・入力が変わるたびにプレビューを更新
        combo.addActionListener(e -> updatePreview());
        Component editor = combo.getEditor().getEditorComponent();
        if (editor instanceof JTextField) {
            ((JTextField) editor).getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updatePreview(); }
                @Override public void removeUpdate(DocumentEvent e) { updatePreview(); }
                @Override public void changedUpdate(DocumentEvent e) { updatePreview(); }
            });
        }
        updatePreview();
    }

    /** フォント選択コンボボックス。 */
    JComboBox<String> getComboBox() {
        return combo;
    }

    /** フォントプレビューラベル。 */
    JLabel getPreview() {
        return preview;
    }

    /** 「自動検出」に戻す。 */
    void reset() {
        combo.setSelectedItem(AUTO);
        combo.getEditor().setItem(AUTO);
        updatePreview();
    }

    /** 現在値を実値 (自動検出は空文字) で返す。 */
    String fontName() {
        Object sel = combo.isEditable()
                ? combo.getEditor().getItem() : combo.getSelectedItem();
        String s = sel == null ? "" : sel.toString().trim();
        return (s.isEmpty() || AUTO.equals(s)) ? "" : s;
    }

    /** 選択中フォントでサンプル文字列を描画してプレビューを更新する。 */
    private void updatePreview() {
        String fam = fontName();
        preview.setText("<html>Aa Bb Cc  あいう 漢字 カタカナ  0123</html>");
        int size = 14;
        if (fam.isEmpty()) {
            preview.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
            preview.setToolTipText(Messages.get("fontPicker.autoTip"));
        } else {
            preview.setFont(new Font(fam, Font.PLAIN, size));
            preview.setToolTipText(SystemFonts.canDisplayJapanese(fam)
                    ? Messages.get("fontPicker.jaOk")
                    : Messages.get("fontPicker.jaWarn"));
        }
    }

    /** プレビュー用の枠線色（ダイアログ側から参照）。テーマ追従。 */
    static Color previewBorderColor() {
        Color sep = javax.swing.UIManager.getColor("Separator.foreground");
        return sep != null ? sep : Color.LIGHT_GRAY;
    }
}
