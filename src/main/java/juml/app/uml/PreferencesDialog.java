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
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
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
 *   <li>外観 (Look &amp; Feel): FlatLaf Light / Dark / System / Cross-Platform /
 *       Nimbus。FlatLaf 等は再起動なしで即時反映する。</li>
 *   <li>起動時に前回のプロジェクトを復元するか。</li>
 * </ul>
 */
public final class PreferencesDialog extends JDialog {

    /**
     * 選択可能な Look &amp; Feel。表示名と実際の L&amp;F クラス名解決を持つ。
     * 列挙子の {@code name()} が永続化キー ({@link juml.Setting#getLookAndFeel()}) になる。
     */
    public enum LookAndFeelOption {
        FLATLAF_LIGHT("FlatLaf Light (モダン・推奨)"),
        FLATLAF_DARK("FlatLaf Dark (ダークモード)"),
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

        /** ダークテーマ (FlatLaf Dark) か。図プレビュー背景の判定などに使う。 */
        public boolean isDark() {
            return this == FLATLAF_DARK;
        }

        /** この L&amp;F の実体クラス名。環境に依存するものは実行時に解決する。 */
        public String className() {
            switch (this) {
                case FLATLAF_LIGHT:
                    return "com.formdev.flatlaf.FlatLightLaf";
                case FLATLAF_DARK:
                    return "com.formdev.flatlaf.FlatDarkLaf";
                case CROSS_PLATFORM:
                    return UIManager.getCrossPlatformLookAndFeelClassName();
                case NIMBUS:
                    return "javax.swing.plaf.nimbus.NimbusLookAndFeel";
                case SYSTEM:
                    return UIManager.getSystemLookAndFeelClassName();
                default:
                    return "com.formdev.flatlaf.FlatLightLaf";
            }
        }

        /** 永続化キー (未知/空なら既定の FlatLaf Light)。 */
        public static LookAndFeelOption fromKey(String key) {
            if (key != null) {
                for (LookAndFeelOption o : values()) {
                    if (o.name().equalsIgnoreCase(key.trim())) {
                        return o;
                    }
                }
            }
            return FLATLAF_LIGHT;
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
        /** 選択された図プレビューの描画品質キー ("AUTO" / "LOW" / "HIGH" / "ULTRA")。 */
        public final String diagramRenderQuality;
        /** タブの最大数 (LRU 自動クローズ)。 */
        public final int maxDiagramTabs;
        /** 描画保持タブ数。 */
        public final int renderedTabs;
        /** 図の描画完了時に自動で全体表示 (Fit) するか。 */
        public final boolean autoFitOnRender;

        public Result(String lookAndFeel, boolean restoreLastProjectOnStartup,
                      String language, String diagramRenderQuality,
                      int maxDiagramTabs, int renderedTabs, boolean autoFitOnRender) {
            this.lookAndFeel = (lookAndFeel == null || lookAndFeel.isEmpty())
                    ? "SYSTEM" : lookAndFeel;
            this.restoreLastProjectOnStartup = restoreLastProjectOnStartup;
            this.language = LanguageOption.fromKey(language).key();
            this.diagramRenderQuality =
                    DiagramRenderQuality.fromKey(diagramRenderQuality).name();
            this.maxDiagramTabs = maxDiagramTabs;
            this.renderedTabs = renderedTabs;
            this.autoFitOnRender = autoFitOnRender;
        }
    }

    private final JComboBox<LookAndFeelOption> lafCombo =
            new JComboBox<>(LookAndFeelOption.values());
    private final JComboBox<LanguageOption> languageCombo =
            new JComboBox<>(LanguageOption.values());
    private final JComboBox<DiagramRenderQuality> renderQualityCombo =
            new JComboBox<>(DiagramRenderQuality.values());
    private final JCheckBox restoreLastProjectCheck =
            new JCheckBox(Messages.get("pref.restoreLastProject"));
    private final JSpinner maxTabsSpinner =
            new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
    private final JSpinner renderedTabsSpinner =
            new JSpinner(new SpinnerNumberModel(4, 1, 50, 1));
    private final JCheckBox autoFitCheck =
            new JCheckBox(Messages.get("pref.autoFitOnRender"));

    private Result result;

    private PreferencesDialog(Frame owner, String currentLaf,
                              boolean currentRestoreLastProject,
                              String currentLanguage,
                              String currentRenderQuality,
                              int currentMaxTabs, int currentRenderedTabs,
                              boolean currentAutoFit) {
        super(owner, Messages.get("dlg.preferences.title"), true);
        lafCombo.setSelectedItem(LookAndFeelOption.fromKey(currentLaf));
        languageCombo.setSelectedItem(LanguageOption.fromKey(currentLanguage));
        renderQualityCombo.setSelectedItem(
                DiagramRenderQuality.fromKey(currentRenderQuality));
        restoreLastProjectCheck.setSelected(currentRestoreLastProject);
        maxTabsSpinner.setValue(currentMaxTabs);
        renderedTabsSpinner.setValue(currentRenderedTabs);
        autoFitCheck.setSelected(currentAutoFit);
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
        form.add(sectionLabel(Messages.get("pref.section.appearance")), c);

        c.gridy = 1;
        c.gridx = 0;
        form.add(new JLabel(Messages.get("pref.laf.label")), c);
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
        form.add(hint(Messages.get("pref.laf.hint")), c);

        // --- 起動時の動作 ---
        c.gridy = 3;
        c.gridx = 0;
        c.insets = new Insets(14, 4, 4, 4);
        form.add(sectionLabel(Messages.get("pref.section.startup")), c);
        c.insets = new Insets(4, 4, 4, 4);

        c.gridy = 4;
        c.gridx = 0;
        c.gridwidth = 2;
        restoreLastProjectCheck.setToolTipText(Messages.get("pref.restoreLastProject.tip"));
        form.add(restoreLastProjectCheck, c);

        // --- 言語 (Language) ---
        c.gridwidth = 1;
        c.gridy = 5;
        c.gridx = 0;
        c.insets = new Insets(14, 4, 4, 4);
        form.add(sectionLabel(Messages.get("pref.section.language")), c);
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

        // --- 図の描画 (Rendering) ---
        c.gridy = 8;
        c.gridx = 0;
        c.insets = new Insets(14, 4, 4, 4);
        form.add(sectionLabel(Messages.get("pref.section.rendering")), c);
        c.insets = new Insets(4, 4, 4, 4);

        c.gridy = 9;
        c.gridx = 0;
        form.add(new JLabel(Messages.get("pref.renderQuality")), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        renderQualityCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel l = new JLabel(value != null
                    ? Messages.get("pref.renderQuality." + value.name()) : "");
            l.setOpaque(true);
            if (isSelected) {
                l.setBackground(list.getSelectionBackground());
                l.setForeground(list.getSelectionForeground());
            }
            l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            return l;
        });
        form.add(renderQualityCombo, c);

        c.gridy = 10;
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(hint(Messages.get("pref.renderQuality.hint")), c);

        c.gridy = 11;
        c.gridx = 0;
        c.gridwidth = 2;
        autoFitCheck.setToolTipText(Messages.get("pref.autoFitOnRender.tip"));
        form.add(autoFitCheck, c);
        c.gridwidth = 1;

        // --- タブ管理 (Tabs) ---
        c.gridy = 12;
        c.gridx = 0;
        c.insets = new Insets(14, 4, 4, 4);
        form.add(sectionLabel(Messages.get("pref.section.tabs")), c);
        c.insets = new Insets(4, 4, 4, 4);

        c.gridy = 13;
        c.gridx = 0;
        form.add(new JLabel(Messages.get("pref.maxTabs")), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        form.add(maxTabsSpinner, c);

        c.gridy = 14;
        c.gridx = 0;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(Messages.get("pref.renderedTabs")), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        form.add(renderedTabsSpinner, c);

        c.gridy = 15;
        c.gridx = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(hint(Messages.get("pref.tabs.hint")), c);

        return form;
    }

    private JComponent buildButtons() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton ok = new JButton(Messages.get("dlg.ok"));
        JButton cancel = new JButton(Messages.get("dlg.cancel"));
        ok.addActionListener(e -> {
            LookAndFeelOption sel = (LookAndFeelOption) lafCombo.getSelectedItem();
            LanguageOption lang = (LanguageOption) languageCombo.getSelectedItem();
            DiagramRenderQuality rq =
                    (DiagramRenderQuality) renderQualityCombo.getSelectedItem();
            result = new Result(sel != null ? sel.name() : "SYSTEM",
                    restoreLastProjectCheck.isSelected(),
                    lang != null ? lang.key() : "ja",
                    rq != null ? rq.name() : "AUTO",
                    (Integer) maxTabsSpinner.getValue(),
                    (Integer) renderedTabsSpinner.getValue(),
                    autoFitCheck.isSelected());
            dispose();
        });
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        // Esc でキャンセル、Enter で OK を統一ユーティリティ経由で設定する。
        DialogUtils.installEscapeAndDefault(this, ok);
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
                                    String currentLanguage,
                                    String currentRenderQuality,
                                    int currentMaxTabs, int currentRenderedTabs,
                                    boolean currentAutoFit) {
        PreferencesDialog dlg = new PreferencesDialog(owner, currentLaf,
                currentRestoreLastProject, currentLanguage, currentRenderQuality,
                currentMaxTabs, currentRenderedTabs, currentAutoFit);
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

    /**
     * Look &amp; Feel を実行中に切り替え、開いている全ウィンドウへ即時反映する
     * (再起動不要)。FlatLaf を含むほとんどの L&amp;F は
     * {@link javax.swing.SwingUtilities#updateComponentTreeUI} でライブ更新できる。
     *
     * @param key 適用する L&amp;F の永続化キー
     * @return ライブ反映に成功したら true (失敗時は再起動が必要)
     */
    public static boolean applyLookAndFeelLive(String key) {
        String current = UIManager.getLookAndFeel() != null
                ? UIManager.getLookAndFeel().getClass().getName() : null;
        String target = LookAndFeelOption.fromKey(key).className();
        if (target.equals(current)) {
            return true;
        }
        try {
            UIManager.setLookAndFeel(target);
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                javax.swing.SwingUtilities.updateComponentTreeUI(w);
            }
            return true;
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException
                 | InstantiationException | IllegalAccessException
                 | RuntimeException ex) {
            // ライブ切替に失敗 → 呼び出し側で再起動を促す
            return false;
        }
    }
}
