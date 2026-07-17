// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Window;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import juml.core.formats.uml.UmlGenerator;
import juml.util.Messages;

/**
 * クラス図 / パッケージ図の表示範囲 ({@link DiagramScope}) を編集するダイアログ。
 *
 * <p>大規模プロジェクトで全クラスを描くと PlantUML が完走しないため、
 * パッケージ・モジュール・正規表現・最大クラス数・外部ライブラリ除外・関連線フィルタで
 * 表示を絞る。プリセット切替で密度を一括変更できる。</p>
 */
public final class DiagramScopeDialog extends JDialog {

    private final JList<String> packageList;
    private final JList<String> excludePackageList;
    private final JList<String> moduleList;
    private final JTextField regexField;
    private final JTextField excludeRegexField;
    private final JTextField includeAnnotationsField;
    private final JTextField excludeAnnotationsField;
    private final JSpinner maxClassesSpinner;
    private final JSpinner neighborHopsSpinner;
    private final JComboBox<DiagramPreset> presetCombo;
    private final JCheckBox inheritanceCheckbox;
    private final JCheckBox implementationCheckbox;
    private final JCheckBox usageCheckbox;
    private final JComboBox<VisibilityFilter> visibilityCombo;
    private final JCheckBox excludeExternalCheckbox;
    private final JRadioButton parseModeFull;
    private final JRadioButton parseModeHeaders;
    private DiagramScope result;

    public DiagramScopeDialog(Window owner, List<String> packages, List<String> modules,
                              DiagramScope initial) {
        super(owner, Messages.get("dlg.scope.title"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        packageList = new JList<>(packages.toArray(new String[0]));
        packageList.setVisibleRowCount(8);
        excludePackageList = new JList<>(packages.toArray(new String[0]));
        excludePackageList.setVisibleRowCount(8);
        moduleList = new JList<>(modules.toArray(new String[0]));
        moduleList.setVisibleRowCount(5);
        regexField = new JTextField(20);
        regexField.setToolTipText(Messages.get("dlg.scope.regexTip"));
        excludeRegexField = new JTextField(20);
        excludeRegexField.setToolTipText(Messages.get("dlg.scope.excludeRegexTip"));
        includeAnnotationsField = new JTextField(20);
        includeAnnotationsField.setToolTipText(Messages.get("dlg.scope.includeAnnotationsTip"));
        excludeAnnotationsField = new JTextField(20);
        excludeAnnotationsField.setToolTipText(Messages.get("dlg.scope.excludeAnnotationsTip"));
        maxClassesSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100_000, 50));
        maxClassesSpinner.setToolTipText(Messages.get("dlg.scope.maxClassesTip"));
        neighborHopsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        neighborHopsSpinner.setToolTipText(Messages.get("dlg.scope.neighborHopsTip"));
        presetCombo = new JComboBox<>(DiagramPreset.values());
        presetCombo.setSelectedItem(DiagramPreset.CUSTOM);
        inheritanceCheckbox = new JCheckBox(Messages.get("dlg.scope.inheritance"), true);
        implementationCheckbox = new JCheckBox(Messages.get("dlg.scope.implementation"), true);
        usageCheckbox = new JCheckBox(Messages.get("dlg.scope.usage"), true);
        visibilityCombo = new JComboBox<>(VisibilityFilter.values());
        visibilityCombo.setSelectedItem(VisibilityFilter.ALL);
        excludeExternalCheckbox = new JCheckBox(Messages.get("dlg.scope.excludeExternal"));
        parseModeFull = new JRadioButton(Messages.get("dlg.scope.parseFull"), true);
        parseModeFull.setToolTipText(Messages.get("dlg.scope.parseFullTip"));
        parseModeHeaders = new JRadioButton(Messages.get("dlg.scope.parseHeaders"));
        parseModeHeaders.setToolTipText(Messages.get("dlg.scope.parseHeadersTip"));
        ButtonGroup pmGroup = new ButtonGroup();
        pmGroup.add(parseModeFull);
        pmGroup.add(parseModeHeaders);

        if (initial != null) {
            selectAll(packageList, initial.getIncludedPackages());
            selectAll(excludePackageList, initial.getExcludedPackages());
            selectAll(moduleList, initial.getIncludedModules());
            if (initial.getClassNameRegex() != null) {
                regexField.setText(initial.getClassNameRegex().pattern());
            }
            if (initial.getExcludeClassNameRegex() != null) {
                excludeRegexField.setText(initial.getExcludeClassNameRegex().pattern());
            }
            includeAnnotationsField.setText(String.join(", ", initial.getIncludedAnnotations()));
            excludeAnnotationsField.setText(String.join(", ", initial.getExcludedAnnotations()));
            maxClassesSpinner.setValue(initial.getMaxClasses());
            neighborHopsSpinner.setValue(initial.getNeighborHops());
            EnumSet<RelationKind> kinds = initial.getRelationKinds();
            inheritanceCheckbox.setSelected(kinds.contains(RelationKind.INHERITANCE));
            implementationCheckbox.setSelected(kinds.contains(RelationKind.IMPLEMENTATION));
            usageCheckbox.setSelected(kinds.contains(RelationKind.USAGE));
            visibilityCombo.setSelectedItem(initial.getVisibilityFilter());
            excludeExternalCheckbox.setSelected(initial.isExcludeExternalLibraries());
            if (initial.getParseMode() == UmlGenerator.ParseMode.HEADERS_ONLY) {
                parseModeHeaders.setSelected(true);
            } else {
                parseModeFull.setSelected(true);
            }
            if (initial.getPreset() != null) {
                presetCombo.setSelectedItem(initial.getPreset());
            }
        }

        presetCombo.addActionListener(e -> applyPresetToWidgets());

        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        setPreferredSize(clampToScreen(new Dimension(640, 620)));
        pack();
        setLocationRelativeTo(owner);
    }

    public DiagramScope getResult() {
        return result;
    }

    /**
     * 希望サイズが画面 (タスクバー等を除いた利用可能領域) の 90% を超える場合に縮める。
     * 低解像度環境で OK/Cancel ボタンが画面外に出て操作不能になるのを防ぐ。
     */
    private static Dimension clampToScreen(Dimension preferred) {
        try {
            java.awt.Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getMaximumWindowBounds();
            int maxW = (int) (bounds.width * 0.9);
            int maxH = (int) (bounds.height * 0.9);
            return new Dimension(Math.min(preferred.width, maxW),
                    Math.min(preferred.height, maxH));
        } catch (HeadlessException ex) {
            return preferred;
        }
    }

    private JPanel buildForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;

        // Preset row
        c.gridx = 0; c.gridy = 0; c.weighty = 0;
        JPanel presetRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        presetRow.add(new JLabel(Messages.get("dlg.scope.preset")));
        presetRow.add(Box.createHorizontalStrut(4));
        presetRow.add(presetCombo);
        p.add(presetRow, c);

        c.gridy = 1; c.weighty = 0;
        p.add(new JLabel(Messages.get("dlg.scope.includePackages")), c);
        c.gridy = 2; c.weighty = 1;
        p.add(new JScrollPane(packageList), c);

        c.gridy = 3; c.weighty = 0;
        p.add(new JLabel(Messages.get("dlg.scope.excludePackages")), c);
        c.gridy = 4; c.weighty = 1;
        p.add(new JScrollPane(excludePackageList), c);

        c.gridy = 5; c.weighty = 0;
        p.add(new JLabel(Messages.get("dlg.scope.modules")), c);
        c.gridy = 6; c.weighty = 0.5;
        p.add(new JScrollPane(moduleList), c);

        c.gridy = 7; c.weighty = 0;
        p.add(new JLabel(Messages.get("dlg.scope.classRegex")), c);
        c.gridy = 8;
        p.add(regexField, c);

        c.gridy = 9; c.weighty = 0;
        p.add(new JLabel(Messages.get("dlg.scope.excludeClassRegex")), c);
        c.gridy = 10;
        p.add(excludeRegexField, c);

        c.gridy = 11;
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        row.add(new JLabel(Messages.get("dlg.scope.maxClasses")));
        row.add(Box.createHorizontalStrut(4));
        row.add(maxClassesSpinner);
        row.add(Box.createHorizontalStrut(12));
        row.add(new JLabel(Messages.get("dlg.scope.neighborHops")));
        row.add(Box.createHorizontalStrut(4));
        row.add(neighborHopsSpinner);
        p.add(row, c);

        c.gridy = 12;
        JPanel relations = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        relations.add(new JLabel(Messages.get("dlg.scope.relations")));
        relations.add(Box.createHorizontalStrut(4));
        relations.add(inheritanceCheckbox);
        relations.add(implementationCheckbox);
        relations.add(usageCheckbox);
        p.add(relations, c);

        c.gridy = 13;
        JPanel vis = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        vis.add(new JLabel(Messages.get("dlg.scope.visibility")));
        vis.add(Box.createHorizontalStrut(4));
        vis.add(visibilityCombo);
        vis.add(Box.createHorizontalStrut(12));
        vis.add(excludeExternalCheckbox);
        p.add(vis, c);

        c.gridy = 14;
        JPanel mode = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        mode.add(new JLabel(Messages.get("dlg.scope.parseMode")));
        mode.add(Box.createHorizontalStrut(4));
        mode.add(parseModeFull);
        mode.add(parseModeHeaders);
        p.add(mode, c);

        c.gridy = 15; c.weighty = 0;
        p.add(new JLabel(Messages.get("dlg.scope.includeAnnotations")), c);
        c.gridy = 16;
        p.add(includeAnnotationsField, c);
        c.gridy = 17;
        p.add(new JLabel(Messages.get("dlg.scope.excludeAnnotations")), c);
        c.gridy = 18;
        p.add(excludeAnnotationsField, c);

        return p;
    }

    private JPanel buildButtons() {
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton ok = new JButton(Messages.get("dlg.ok"));
        ok.addActionListener(e -> {
            // 不正な正規表現などで buildScope が null を返したら閉じずに修正を促す。
            DiagramScope scope = buildScope();
            if (scope != null) {
                result = scope;
                dispose();
            }
        });
        JButton cancel = new JButton(Messages.get("dlg.cancel"));
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        bar.add(ok);
        bar.add(cancel);
        DialogUtils.installEscapeAndDefault(this, ok);
        return bar;
    }

    /** プリセットセレクタが変わったとき、他ウィジェットに既定値を反映する。 */
    private void applyPresetToWidgets() {
        Object sel = presetCombo.getSelectedItem();
        if (!(sel instanceof DiagramPreset)) {
            return;
        }
        DiagramPreset p = (DiagramPreset) sel;
        if (p == DiagramPreset.CUSTOM) {
            return;
        }
        DiagramScope.Builder b = DiagramScope.builder();
        p.applyTo(b);
        DiagramScope tmp = b.build();
        EnumSet<RelationKind> kinds = tmp.getRelationKinds();
        inheritanceCheckbox.setSelected(kinds.contains(RelationKind.INHERITANCE));
        implementationCheckbox.setSelected(kinds.contains(RelationKind.IMPLEMENTATION));
        usageCheckbox.setSelected(kinds.contains(RelationKind.USAGE));
        visibilityCombo.setSelectedItem(tmp.getVisibilityFilter());
        excludeExternalCheckbox.setSelected(tmp.isExcludeExternalLibraries());
        if (tmp.getParseMode() == UmlGenerator.ParseMode.HEADERS_ONLY) {
            parseModeHeaders.setSelected(true);
        } else {
            parseModeFull.setSelected(true);
        }
        maxClassesSpinner.setValue(tmp.getMaxClasses());
    }

    /** カンマ区切りのアノテーション名を集合へ (空要素は無視)。@ 除去はビルダ側で行う。 */
    private static Set<String> parseCsvSet(String csv) {
        Set<String> set = new LinkedHashSet<>();
        if (csv != null) {
            for (String tok : csv.split(",")) {
                String t = tok.trim();
                if (!t.isEmpty()) {
                    set.add(t);
                }
            }
        }
        return set;
    }

    private DiagramScope buildScope() {
        DiagramScope.Builder b = DiagramScope.builder();
        Set<String> pkgs = new LinkedHashSet<>(packageList.getSelectedValuesList());
        b.includePackages(pkgs);
        Set<String> excluded = new LinkedHashSet<>(excludePackageList.getSelectedValuesList());
        b.excludePackages(excluded);
        Set<String> mods = new LinkedHashSet<>(moduleList.getSelectedValuesList());
        b.includeModules(mods);
        String regex = regexField.getText().trim();
        if (!regex.isEmpty()) {
            try {
                b.classNameRegex(regex);
            } catch (java.util.regex.PatternSyntaxException ex) {
                juml.util.AppLog.warn(juml.util.ErrorCode.DIAG_002, "DiagramScopeDialog",
                        "Invalid class-name regex in scope filter: " + regex, ex);
                javax.swing.JOptionPane.showMessageDialog(this,
                        java.text.MessageFormat.format(
                                Messages.get("dlg.scope.invalidRegex"), ex.getMessage()),
                        Messages.get("dlg.scope.invalidRegexTitle"),
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                regexField.requestFocusInWindow();
                return null; // 閉じずに修正させる
            }
        }
        String excludeRegex = excludeRegexField.getText().trim();
        if (!excludeRegex.isEmpty()) {
            try {
                b.excludeClassNameRegex(excludeRegex);
            } catch (java.util.regex.PatternSyntaxException ex) {
                juml.util.AppLog.warn(juml.util.ErrorCode.DIAG_002, "DiagramScopeDialog",
                        "Invalid exclude class-name regex in scope filter: " + excludeRegex, ex);
                javax.swing.JOptionPane.showMessageDialog(this,
                        java.text.MessageFormat.format(
                                Messages.get("dlg.scope.invalidRegex"), ex.getMessage()),
                        Messages.get("dlg.scope.invalidRegexTitle"),
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                excludeRegexField.requestFocusInWindow();
                return null; // 閉じずに修正させる
            }
        }
        b.includeAnnotations(parseCsvSet(includeAnnotationsField.getText()));
        b.excludeAnnotations(parseCsvSet(excludeAnnotationsField.getText()));
        b.maxClasses(((Number) maxClassesSpinner.getValue()).intValue());
        b.neighborHops(((Number) neighborHopsSpinner.getValue()).intValue());
        EnumSet<RelationKind> kinds = EnumSet.noneOf(RelationKind.class);
        if (inheritanceCheckbox.isSelected()) kinds.add(RelationKind.INHERITANCE);
        if (implementationCheckbox.isSelected()) kinds.add(RelationKind.IMPLEMENTATION);
        if (usageCheckbox.isSelected()) kinds.add(RelationKind.USAGE);
        if (kinds.isEmpty()) {
            // 全部 off は意味がないので all on に戻す
            kinds = EnumSet.allOf(RelationKind.class);
        }
        b.relationKinds(kinds);
        Object visSel = visibilityCombo.getSelectedItem();
        b.visibilityFilter(visSel instanceof VisibilityFilter
                ? (VisibilityFilter) visSel : VisibilityFilter.ALL);
        b.excludeExternalLibraries(excludeExternalCheckbox.isSelected());
        b.parseMode(parseModeHeaders.isSelected()
                ? UmlGenerator.ParseMode.HEADERS_ONLY
                : UmlGenerator.ParseMode.FULL);
        Object presetSel = presetCombo.getSelectedItem();
        b.preset(presetSel instanceof DiagramPreset
                ? (DiagramPreset) presetSel : DiagramPreset.CUSTOM);
        return b.build();
    }

    private static void selectAll(JList<String> list, Set<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        int[] idx = new int[values.size()];
        int n = 0;
        for (int i = 0; i < list.getModel().getSize(); i++) {
            if (values.contains(list.getModel().getElementAt(i))) {
                idx[n++] = i;
            }
        }
        int[] trimmed = new int[n];
        System.arraycopy(idx, 0, trimmed, 0, n);
        list.setSelectedIndices(trimmed);
    }
}
