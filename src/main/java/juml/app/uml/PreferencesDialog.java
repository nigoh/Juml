// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * アプリ全体の設定 (Preferences) を編集するモーダルダイアログ。
 *
 * <p>図種・スタイル個別の設定は {@link StyleSettingsDialog} が担う。こちらは
 * 図の中身に依らない「アプリの振る舞い・外観」をまとめる器で、今後項目を
 * 足していく前提で作っている。現時点では以下を扱う:</p>
 *
 * <ul>
 *   <li>外観 (Look &amp; Feel): System / Cross-Platform / Nimbus。再起動後に反映。</li>
 *   <li>起動時に前回のプロジェクトを復元するか。</li>
 * </ul>
 */
public final class PreferencesDialog extends JDialog {

    /**
     * 選択可能な Look &amp; Feel。表示名と実際の L&amp;F クラス名解決を持つ。
     * 列挙子の {@code name()} が永続化キー ({@link juml.Setting#getLookAndFeel()}) になる。
     */
    public enum LookAndFeelOption {
        SYSTEM("System (OS ネイティブ)"),
        CROSS_PLATFORM("Cross-Platform (Metal)"),
        NIMBUS("Nimbus");

        private final String displayName;

        LookAndFeelOption(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** この L&amp;F の実体クラス名。環境に依存するものは実行時に解決する。 */
        public String className() {
            switch (this) {
                case CROSS_PLATFORM:
                    return UIManager.getCrossPlatformLookAndFeelClassName();
                case NIMBUS:
                    return "javax.swing.plaf.nimbus.NimbusLookAndFeel";
                case SYSTEM:
                default:
                    return UIManager.getSystemLookAndFeelClassName();
            }
        }

        /** 永続化キー (未知/空なら SYSTEM)。 */
        public static LookAndFeelOption fromKey(String key) {
            if (key != null) {
                for (LookAndFeelOption o : values()) {
                    if (o.name().equalsIgnoreCase(key.trim())) {
                        return o;
                    }
                }
            }
            return SYSTEM;
        }
    }

    /**
     * 選択可能な UI 表示言語。{@code key()} が永続化キー ({@link juml.Setting#getLanguage()})
     * になる。
     */
    public enum LanguageOption {
        JA("ja", "日本語"),
        EN("en", "English");

        private final String key;
        private final String displayName;

        LanguageOption(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String key() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** 永続化キー (未知/空/null は日本語)。 */
        public static LanguageOption fromKey(String key) {
            return (key != null && "en".equalsIgnoreCase(key.trim())) ? EN : JA;
        }
    }

    /** ダイアログの編集結果 (OK 押下時のみ生成)。 */
    public static final class Result {
        /** 選択された Look &amp; Feel の永続化キー。 */
        public final String lookAndFeel;
        /** 起動時に前回プロジェクトを復元するか。 */
        public final boolean restoreLastProjectOnStartup;
        /** 選択された UI 表示言語キー ("ja" / "en")。 */
        public final String language;

        public Result(String lookAndFeel, boolean restoreLastProjectOnStartup,
                      String language) {
            this.lookAndFeel = (lookAndFeel == null || lookAndFeel.isEmpty())
                    ? "SYSTEM" : lookAndFeel;
            this.restoreLastProjectOnStartup = restoreLastProjectOnStartup;
            this.language = LanguageOption.fromKey(language).key();
        }
    }

    private final JComboBox<LookAndFeelOption> lafCombo =
            new JComboBox<>(LookAndFeelOption.values());
    private final JComboBox<LanguageOption> languageCombo =
            new JComboBox<>(LanguageOption.values());
    private final JCheckBox restoreLastProjectCheck =
            new JCheckBox("前回開いたプロジェクトを起動時に復元する");

    private Result result;

    private PreferencesDialog(Frame owner, String currentLaf,
                              boolean currentRestoreLastProject,
                              String currentLanguage) {
        super(owner, Messages.get("dlg.preferences.title"), true);
        lafCombo.setSelectedItem(LookAndFeelOption.fromKey(currentLaf));
        languageCombo.setSelectedItem(LanguageOption.fromKey(currentLanguage));
        restoreLastProjectCheck.setSelected(currentRestoreLastProject);
        setContentPane(buildContent());
        pack();
        setMinimumSize(new Dimension(420, getPreferredSize().height));
        setLocationRelativeTo(owner);
    }

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        root.add(buildForm(), BorderLayout.CENTER);
        root.add(buildButtons(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        // --- 外観 (Look & Feel) ---
        c.gridx = 0;
        c.gridy = 0;
        form.add(sectionLabel("外観 (Appearance)"), c);

        c.gridy = 1;
        c.gridx = 0;
        form.add(new JLabel("Look & Feel:"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        lafCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel l = new JLabel(value != null ? value.getDisplayName() : "");
            l.setOpaque(true);
            if (isSelected) {
                l.setBackground(list.getSelectionBackground());
                l.setForeground(list.getSelectionForeground());
            }
            l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return l;
        });
        form.add(lafCombo, c);

        c.gridy = 2;
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(hint("変更はアプリの再起動後に反映されます。"), c);

        // --- 起動時の動作 ---
        c.gridy = 3;
        c.gridx = 0;
        c.insets = new Insets(14, 4, 4, 4);
        form.add(sectionLabel("起動 (Startup)"), c);
        c.insets = new Insets(4, 4, 4, 4);

        c.gridy = 4;
        c.gridx = 0;
        c.gridwidth = 2;
        restoreLastProjectCheck.setToolTipText(
                "引数でプロジェクトを指定せずに起動したとき、最後に開いたプロジェクトを自動で開きます");
        form.add(restoreLastProjectCheck, c);

        // --- 言語 (Language) ---
        c.gridwidth = 1;
        c.gridy = 5;
        c.gridx = 0;
        c.insets = new Insets(14, 4, 4, 4);
        form.add(sectionLabel("言語 (Language)"), c);
        c.insets = new Insets(4, 4, 4, 4);

        c.gridy = 6;
        c.gridx = 0;
        form.add(new JLabel(Messages.get("pref.language")), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        languageCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel l = new JLabel(value != null ? value.getDisplayName() : "");
            l.setOpaque(true);
            if (isSelected) {
                l.setBackground(list.getSelectionBackground());
                l.setForeground(list.getSelectionForeground());
            }
            l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return l;
        });
        form.add(languageCombo, c);

        c.gridy = 7;
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(hint(Messages.get("pref.language.hint")), c);

        return form;
    }

    private JComponent buildButtons() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> {
            LookAndFeelOption sel = (LookAndFeelOption) lafCombo.getSelectedItem();
            LanguageOption lang = (LanguageOption) languageCombo.getSelectedItem();
            result = new Result(sel != null ? sel.name() : "SYSTEM",
                    restoreLastProjectCheck.isSelected(),
                    lang != null ? lang.key() : "ja");
            dispose();
        });
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        getRootPane().setDefaultButton(ok);
        bar.add(ok);
        bar.add(cancel);
        return bar;
    }

    private static JComponent sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private static JComponent hint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, l.getFont().getSize() - 1f));
        java.awt.Color disabled = UIManager.getColor("Label.disabledForeground");
        l.setForeground(disabled != null ? disabled : java.awt.Color.GRAY);
        return l;
    }

    /**
     * モーダルでダイアログを表示する。OK なら {@link Result}、キャンセル/クローズなら null。
     */
    public static Result showDialog(Frame owner, String currentLaf,
                                    boolean currentRestoreLastProject,
                                    String currentLanguage) {
        PreferencesDialog dlg = new PreferencesDialog(owner, currentLaf,
                currentRestoreLastProject, currentLanguage);
        dlg.setVisible(true);
        return dlg.result;
    }

    /**
     * 永続化キーに対応する Look &amp; Feel を {@link UIManager} に適用する。
     * 失敗しても例外は投げず、既定 L&amp;F のまま続行する。起動時 ({@link UmlApp})
     * から呼ばれる。
     */
    public static void applyLookAndFeel(String key) {
        try {
            UIManager.setLookAndFeel(LookAndFeelOption.fromKey(key).className());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException
                 | InstantiationException | IllegalAccessException ex) {
            // 指定 L&F が使えない環境では既定のまま続行
        }
    }
}
