// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.PlantUmlSequenceDiagram;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

/**
 * UML 描画スタイル詳細設定ダイアログ。
 *
 * <p>{@link DiagramStyle} の各フィールドを編集する Swing モーダルダイアログ。
 * {@link #showDialog(Component, DiagramStyle)} を呼ぶと OK 押下時は編集後の
 * {@code DiagramStyle} を、キャンセル時は {@code null} を返す。</p>
 */
public final class StyleSettingsDialog extends JDialog {

    /** ダイアログ・メニュー双方で共有する組み込みテーマ一覧 (先頭 "" は未指定)。 */
    public static final String[] THEMES = new String[] {
            "", "plain", "cerulean", "sketchy", "mono", "vibrant",
            "materia", "hacker", "cyborg", "mars", "amiga", "spacelab"
    };

    private final JComboBox<String> themeCombo = new JComboBox<>(THEMES);
    private final JButton bgColorButton = new JButton();
    private final JTextField bgColorField = new JTextField(10);
    /** フォント選択 (コンボ + プレビュー)。{@link #buildForm} で初期化する。 */
    private FontPickerField fontPicker;
    private final JSpinner fontSizeSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 48, 1));
    private final JRadioButton dirDefault = new JRadioButton(Messages.get("style.dir.default"));
    private final JRadioButton dirLeftRight = new JRadioButton(Messages.get("style.dir.leftRight"));
    private final JRadioButton dirTopBottom = new JRadioButton(Messages.get("style.dir.topBottom"));
    private final JComboBox<String> lineTypeCombo =
            new JComboBox<>(new String[] { "Default", "Polyline", "Ortho", "Spline" });
    private final JComboBox<String> shadowingCombo =
            new JComboBox<>(new String[] { "Default", "On", "Off" });
    private final JSpinner nodeSepSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 200, 5));
    private final JSpinner rankSepSpinner =
            new JSpinner(new SpinnerNumberModel(0, 0, 200, 5));
    private final JTextArea customSkinparamArea = new JTextArea(6, 32);
    private final JCheckBox sequenceShowCommentsCheckbox =
            new JCheckBox(Messages.get("style.seq.showComments"));
    private final JComboBox<String> sequenceCommentStyleCombo =
            new JComboBox<>(new String[] { "INLINE", "NOTE" });
    private final JComboBox<String> sequenceCommentPlacementCombo =
            new JComboBox<>(new String[] { "AT_CALL_SITE", "PARTICIPANT_TOP" });
    private final JCheckBox sequenceQualifyMethodsCheckbox =
            new JCheckBox(Messages.get("style.seq.qualify"));
    private final JCheckBox sequenceShowArgsCheckbox =
            new JCheckBox(Messages.get("style.seq.showArgs"));
    private final JSpinner sequenceMaxDepthSpinner =
            new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));

    private final JCheckBox activityExpandCallbacksCheckbox =
            new JCheckBox(Messages.get("style.act.expandCallbacks"));
    private final JCheckBox activityShowLocalVarsCheckbox =
            new JCheckBox(Messages.get("style.act.showLocalVars"));
    private final JCheckBox activityShowAssignmentsCheckbox =
            new JCheckBox(Messages.get("style.act.showAssignments"));
    private final JCheckBox activityShowCallArgsCheckbox =
            new JCheckBox(Messages.get("style.act.showCallArgs"));
    private final JCheckBox activityShowInlineCommentsCheckbox =
            new JCheckBox(Messages.get("style.act.showInlineComments"));

    private final JCheckBox classShowFieldsCheckbox =
            new JCheckBox(Messages.get("style.class.showFields"));
    private final JCheckBox classShowMethodsCheckbox =
            new JCheckBox(Messages.get("style.class.showMethods"));
    private final JCheckBox classShowAnnotationsCheckbox =
            new JCheckBox(Messages.get("style.class.showAnnotations"));
    private final JCheckBox classPublicOnlyCheckbox =
            new JCheckBox(Messages.get("style.class.publicOnly"));
    private final JCheckBox classExcludeExternalCheckbox =
            new JCheckBox(Messages.get("style.class.excludeExternal"));
    private final JCheckBox classMarkExternalSupertypesCheckbox =
            new JCheckBox(Messages.get("style.class.markExternalSupertypes"));
    private final JCheckBox classColorCodeRelationsCheckbox =
            new JCheckBox(Messages.get("style.class.colorCodeRelations"));
    private final JCheckBox classHideEmptyMembersCheckbox =
            new JCheckBox(Messages.get("style.class.hideEmptyMembers"));
    private final JCheckBox classHideUnlinkedCheckbox =
            new JCheckBox(Messages.get("style.class.hideUnlinked"));
    private final JCheckBox classColorCodeStereotypesCheckbox =
            new JCheckBox(Messages.get("style.class.colorCodeStereotypes"));
    private final JSpinner classCommentMaxLengthSpinner =
            new JSpinner(new SpinnerNumberModel(80, 0, 500, 10));
    private final JTextField classHiddenAnnotationsField = new JTextField(24);

    private final JSpinner callGraphMaxDepthSpinner =
            new JSpinner(new SpinnerNumberModel(4, 1, 10, 1));

    private Result result;

    /** ダイアログの戻り値 (Style + シーケンス図 + アクティビティ図 + クラス図設定)。 */
    public static final class Result {
        public final DiagramStyle style;
        public final boolean sequenceShowComments;
        public final PlantUmlClassDiagram.CommentStyle sequenceCommentStyle;
        public final PlantUmlSequenceDiagram.CommentPlacement sequenceCommentPlacement;
        public final boolean sequenceQualifyMethodNames;
        /** シーケンス図の再帰展開の最大深さ (0 = 無制限)。 */
        public final int sequenceMaxDepth;
        /** シーケンス図の呼び出しラベルに引数を表示するか。 */
        public final boolean sequenceShowCallArguments;
        public final ActivityDiagramPrefs activityDiagram;
        public final ClassDiagramPrefs classDiagram;
        public final int callGraphMaxDepth;

        public Result(DiagramStyle style, boolean sequenceShowComments,
                      PlantUmlClassDiagram.CommentStyle sequenceCommentStyle,
                      PlantUmlSequenceDiagram.CommentPlacement sequenceCommentPlacement,
                      boolean sequenceQualifyMethodNames,
                      int sequenceMaxDepth,
                      boolean sequenceShowCallArguments,
                      ActivityDiagramPrefs activityDiagram,
                      ClassDiagramPrefs classDiagram,
                      int callGraphMaxDepth) {
            this.style = style;
            this.sequenceShowComments = sequenceShowComments;
            this.sequenceCommentStyle = sequenceCommentStyle;
            this.sequenceCommentPlacement = sequenceCommentPlacement;
            this.sequenceQualifyMethodNames = sequenceQualifyMethodNames;
            this.sequenceMaxDepth = Math.max(0, Math.min(10, sequenceMaxDepth));
            this.sequenceShowCallArguments = sequenceShowCallArguments;
            this.activityDiagram = activityDiagram != null
                    ? activityDiagram : ActivityDiagramPrefs.defaults();
            this.classDiagram = classDiagram != null
                    ? classDiagram : ClassDiagramPrefs.defaults();
            this.callGraphMaxDepth = callGraphMaxDepth > 0 ? callGraphMaxDepth : 4;
        }
    }

    private StyleSettingsDialog(Window owner, DiagramStyle initial,
                                 boolean initialSeqShowComments,
                                 PlantUmlClassDiagram.CommentStyle initialSeqCommentStyle,
                                 PlantUmlSequenceDiagram.CommentPlacement initialSeqPlacement,
                                 boolean initialSeqQualify,
                                 int initialSeqMaxDepth,
                                 boolean initialSeqShowArgs,
                                 ActivityDiagramPrefs initialActivityPrefs,
                                 ClassDiagramPrefs initialClassPrefs,
                                 int initialCallGraphMaxDepth) {
        super(owner, Messages.get("style.title"), Dialog.ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(buildForm(initial, initialSeqShowComments,
                initialSeqCommentStyle, initialSeqPlacement, initialSeqQualify,
                initialSeqMaxDepth, initialSeqShowArgs, initialActivityPrefs,
                initialClassPrefs, initialCallGraphMaxDepth));
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);
        pack();
        // フォーム高がスクリーンを超えると pack でクリップされるので明示的に上限を入れる
        java.awt.Dimension pref = getPreferredSize();
        int maxH = (int) (java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds().height * 0.9);
        if (pref.height > maxH) {
            setSize(pref.width + 30, maxH);
        }
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm(DiagramStyle initial,
                              boolean initialSeqShowComments,
                              PlantUmlClassDiagram.CommentStyle initialSeqCommentStyle,
                              PlantUmlSequenceDiagram.CommentPlacement initialSeqPlacement,
                              boolean initialSeqQualify,
                              int initialSeqMaxDepth,
                              boolean initialSeqShowArgs,
                              ActivityDiagramPrefs initialActivityPrefs,
                              ClassDiagramPrefs initialClassPrefs,
                              int initialCallGraphMaxDepth) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // テーマ
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get("style.label.theme")), c);
        themeCombo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String s = (value == null || value.toString().isEmpty())
                        ? Messages.get("menubar.style.none") : value.toString();
                return super.getListCellRendererComponent(list, s, index, isSelected, cellHasFocus);
            }
        });
        themeCombo.setSelectedItem(initial.getTheme());
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(themeCombo, c);
        c.gridwidth = 1;
        row++;

        // 背景色
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get("style.label.background")), c);
        bgColorField.setText(initial.getBackgroundColor());
        bgColorField.setToolTipText(Messages.get("style.tip.bgColor"));
        c.gridx = 1; c.gridy = row; c.weightx = 1;
        form.add(bgColorField, c);
        bgColorButton.setText(Messages.get("style.btn.pick"));
        bgColorButton.addActionListener(e -> pickBackgroundColor());
        c.gridx = 2; c.gridy = row; c.weightx = 0;
        form.add(bgColorButton, c);
        row++;

        // フォント名 (システムにインストールされたフォントから選択。任意入力も可)
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get("style.label.fontName")), c);
        fontPicker = new FontPickerField(initial.getFontName());
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(fontPicker.getComboBox(), c);
        c.gridwidth = 1;
        row++;

        // フォントプレビュー
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        fontPicker.getPreview().setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FontPickerField.previewBorderColor()),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        form.add(fontPicker.getPreview(), c);
        c.gridwidth = 1;
        row++;

        // フォントサイズ
        fontSizeSpinner.setValue(initial.getFontSize());
        ((JSpinner.DefaultEditor) fontSizeSpinner.getEditor()).getTextField()
                .setToolTipText(Messages.get("style.tip.fontSize"));
        row = addLabeledRow(form, c, row, "style.label.fontSize", fontSizeSpinner);

        // 方向
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get("style.label.direction")), c);
        ButtonGroup dirGroup = new ButtonGroup();
        dirGroup.add(dirDefault);
        dirGroup.add(dirLeftRight);
        dirGroup.add(dirTopBottom);
        switch (initial.getDirection()) {
            case LEFT_TO_RIGHT: dirLeftRight.setSelected(true); break;
            case TOP_TO_BOTTOM: dirTopBottom.setSelected(true); break;
            default: dirDefault.setSelected(true); break;
        }
        JPanel dirPanel = new JPanel();
        dirPanel.setLayout(new BoxLayout(dirPanel, BoxLayout.Y_AXIS));
        dirPanel.add(dirDefault);
        dirPanel.add(dirLeftRight);
        dirPanel.add(dirTopBottom);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(dirPanel, c);
        c.gridwidth = 1;
        row++;

        // ─── Readability (可読性) ──────────────────────────────────────────
        // 図が見づらい場合に効く設定群。線種・影・要素間隔を調整できる。
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(10, 4, 4, 4);
        form.add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        JLabel readHeading = new JLabel(Messages.get("style.section.readability"));
        readHeading.setFont(readHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(readHeading, c);
        c.gridwidth = 1;
        row++;

        // 線種 (linetype)
        lineTypeCombo.setSelectedIndex(lineTypeIndex(initial.getLineType()));
        lineTypeCombo.setToolTipText(Messages.get("style.tip.lineType"));
        row = addLabeledRow(form, c, row, "style.label.lineType", lineTypeCombo);

        // 影 (shadowing)
        shadowingCombo.setSelectedIndex(shadowingIndex(initial.getShadowing()));
        shadowingCombo.setToolTipText(Messages.get("style.tip.shadowing"));
        row = addLabeledRow(form, c, row, "style.label.shadowing", shadowingCombo);

        // 要素間隔 (nodesep / ranksep)
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get("style.label.spacing")), c);
        nodeSepSpinner.setValue(initial.getNodeSep());
        rankSepSpinner.setValue(initial.getRankSep());
        ((JSpinner.DefaultEditor) nodeSepSpinner.getEditor()).getTextField()
                .setToolTipText(Messages.get("style.tip.nodeSep"));
        ((JSpinner.DefaultEditor) rankSepSpinner.getEditor()).getTextField()
                .setToolTipText(Messages.get("style.tip.rankSep"));
        JPanel spacingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        spacingPanel.add(new JLabel(Messages.get("style.label.node")));
        spacingPanel.add(nodeSepSpinner);
        spacingPanel.add(Box.createHorizontalStrut(8));
        spacingPanel.add(new JLabel(Messages.get("style.label.rank")));
        spacingPanel.add(rankSepSpinner);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(spacingPanel, c);
        c.gridwidth = 1;
        row++;

        // カスタム skinparam
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel(Messages.get("style.label.customSkinparam")), c);
        customSkinparamArea.setText(initial.getCustomSkinparam());
        customSkinparamArea.setLineWrap(false);
        customSkinparamArea.setToolTipText(
                Messages.get("style.tip.customSkinparam") + "\n"
                + "skinparam shadowing false\n"
                + "skinparam classBackgroundColor #EEEEEE");
        JScrollPane skinScroll = new JScrollPane(customSkinparamArea);
        skinScroll.setPreferredSize(new Dimension(400, 120));
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.weighty = 1;
        c.gridwidth = 2; c.fill = GridBagConstraints.BOTH;
        form.add(skinScroll, c);
        c.gridwidth = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weighty = 0;
        c.anchor = GridBagConstraints.WEST;
        row++;

        // 区切り線 (Sequence Diagram セクション)
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(10, 4, 4, 4);
        form.add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        // セクション見出し
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        JLabel seqHeading = new JLabel(Messages.get("style.section.sequence"));
        seqHeading.setFont(seqHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(seqHeading, c);
        c.gridwidth = 1;
        row++;

        // コメント表示 ON/OFF
        sequenceShowCommentsCheckbox.setSelected(initialSeqShowComments);
        sequenceShowCommentsCheckbox.setToolTipText(Messages.get("style.tip.showComments"));
        sequenceShowCommentsCheckbox.addActionListener(
                e -> sequenceCommentStyleCombo.setEnabled(
                        sequenceShowCommentsCheckbox.isSelected()));
        row = addLabeledRow(form, c, row, "style.label.comments",
                sequenceShowCommentsCheckbox);

        // コメントスタイル
        sequenceCommentStyleCombo.setSelectedItem(
                initialSeqCommentStyle == PlantUmlClassDiagram.CommentStyle.NOTE
                        ? "NOTE" : "INLINE");
        sequenceCommentStyleCombo.setEnabled(initialSeqShowComments);
        sequenceCommentStyleCombo.setToolTipText(Messages.get("style.tip.commentStyle"));
        row = addLabeledRow(form, c, row, "style.label.commentStyle",
                sequenceCommentStyleCombo);

        // コメント表示位置
        sequenceCommentPlacementCombo.setSelectedItem(
                initialSeqPlacement == PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP
                        ? "PARTICIPANT_TOP" : "AT_CALL_SITE");
        sequenceCommentPlacementCombo.setEnabled(initialSeqShowComments);
        sequenceCommentPlacementCombo.setToolTipText(Messages.get("style.tip.commentPlacement"));
        row = addLabeledRow(form, c, row, "style.label.commentPlacement",
                sequenceCommentPlacementCombo);

        // クラス名修飾
        sequenceQualifyMethodsCheckbox.setSelected(initialSeqQualify);
        sequenceQualifyMethodsCheckbox.setToolTipText(Messages.get("style.tip.qualify"));
        row = addLabeledRow(form, c, row, "style.label.callLabels",
                sequenceQualifyMethodsCheckbox);

        // showComments のトグルに連動して placement / qualify は別系統なので、
        // placement だけは show に依存させる (qualify は独立)。
        sequenceShowCommentsCheckbox.addActionListener(
                e -> sequenceCommentPlacementCombo.setEnabled(
                        sequenceShowCommentsCheckbox.isSelected()));

        // 呼び出しラベルの引数表示 (既定 OFF = 定数シンボルのみ)
        sequenceShowArgsCheckbox.setSelected(initialSeqShowArgs);
        sequenceShowArgsCheckbox.setToolTipText(Messages.get("style.tip.seqShowArgs"));
        row = addWideRow(form, c, row, sequenceShowArgsCheckbox);

        // 呼び出し展開の深さ (0 = 無制限)
        sequenceMaxDepthSpinner.setValue(Math.max(0, Math.min(10, initialSeqMaxDepth)));
        ((JSpinner.DefaultEditor) sequenceMaxDepthSpinner.getEditor()).getTextField()
                .setToolTipText(Messages.get("style.tip.seqMaxDepth"));
        row = addLabeledRow(form, c, row, "style.label.seqMaxDepth",
                sequenceMaxDepthSpinner);

        // ---- Activity Diagram セクション ----
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(10, 4, 4, 4);
        form.add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        JLabel actHeading = new JLabel(Messages.get("style.section.activity"));
        actHeading.setFont(actHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(actHeading, c);
        c.gridwidth = 1;
        row++;

        ActivityDiagramPrefs ap = initialActivityPrefs != null
                ? initialActivityPrefs : ActivityDiagramPrefs.defaults();
        activityExpandCallbacksCheckbox.setSelected(ap.expandInlineCallbacks);
        activityExpandCallbacksCheckbox.setToolTipText(
                Messages.get("style.tip.actExpandCallbacks"));
        activityShowLocalVarsCheckbox.setSelected(ap.showLocalVars);
        activityShowLocalVarsCheckbox.setToolTipText(
                Messages.get("style.tip.actShowLocalVars"));
        activityShowAssignmentsCheckbox.setSelected(ap.showAssignments);
        activityShowAssignmentsCheckbox.setToolTipText(
                Messages.get("style.tip.actShowAssignments"));
        activityShowCallArgsCheckbox.setSelected(ap.showCallArguments);
        activityShowCallArgsCheckbox.setToolTipText(
                Messages.get("style.tip.actShowCallArgs"));
        activityShowInlineCommentsCheckbox.setSelected(ap.showInlineComments);
        activityShowInlineCommentsCheckbox.setToolTipText(
                Messages.get("style.tip.actShowInlineComments"));

        row = addWideRow(form, c, row, activityExpandCallbacksCheckbox);
        row = addWideRow(form, c, row, activityShowLocalVarsCheckbox);
        row = addWideRow(form, c, row, activityShowAssignmentsCheckbox);
        row = addWideRow(form, c, row, activityShowCallArgsCheckbox);
        row = addWideRow(form, c, row, activityShowInlineCommentsCheckbox);

        // ---- Class Diagram セクション ----
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(10, 4, 4, 4);
        form.add(new JSeparator(SwingConstants.HORIZONTAL), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        JLabel classHeading = new JLabel(Messages.get("style.section.class"));
        classHeading.setFont(classHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(classHeading, c);
        c.gridwidth = 1;
        row++;

        ClassDiagramPrefs cp = initialClassPrefs != null
                ? initialClassPrefs : ClassDiagramPrefs.defaults();
        classShowFieldsCheckbox.setSelected(cp.showFields);
        classShowMethodsCheckbox.setSelected(cp.showMethods);
        classShowAnnotationsCheckbox.setSelected(cp.showAnnotations);
        classPublicOnlyCheckbox.setSelected(cp.publicOnly);
        classExcludeExternalCheckbox.setSelected(cp.excludeExternal);
        classMarkExternalSupertypesCheckbox.setSelected(cp.markExternalSupertypes);
        classColorCodeRelationsCheckbox.setSelected(cp.colorCodeRelations);
        classColorCodeRelationsCheckbox.setToolTipText(
                Messages.get("style.tip.colorCodeRelations"));
        classHideEmptyMembersCheckbox.setSelected(cp.hideEmptyMembers);
        classHideEmptyMembersCheckbox.setToolTipText(
                Messages.get("style.tip.hideEmptyMembers"));
        classHideUnlinkedCheckbox.setSelected(cp.hideUnlinked);
        classHideUnlinkedCheckbox.setToolTipText(
                Messages.get("style.tip.hideUnlinked"));
        classColorCodeStereotypesCheckbox.setSelected(cp.colorCodeStereotypes);
        classColorCodeStereotypesCheckbox.setToolTipText(
                Messages.get("style.tip.colorCodeStereotypes"));
        classCommentMaxLengthSpinner.setValue(cp.commentMaxLength);
        classHiddenAnnotationsField.setText(cp.hiddenAnnotationsCsv());
        classHiddenAnnotationsField.setToolTipText(Messages.get("style.tip.hiddenAnnotations"));

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get("style.label.members")), c);
        JPanel membersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        membersPanel.add(classShowFieldsCheckbox);
        membersPanel.add(classShowMethodsCheckbox);
        membersPanel.add(classShowAnnotationsCheckbox);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(membersPanel, c);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get("style.label.filters")), c);
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filterPanel.add(classPublicOnlyCheckbox);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(filterPanel, c);
        c.gridwidth = 1;
        row++;

        row = addWideRow(form, c, row, classExcludeExternalCheckbox);
        row = addWideRow(form, c, row, classMarkExternalSupertypesCheckbox);
        // 関係線の色分け (継承=緑/実装=青/利用=灰破線)。大規模図で依存線を追いやすくする。
        row = addWideRow(form, c, row, classColorCodeRelationsCheckbox);
        // 密度削減トグル: 空メンバー欄を畳む / 孤立クラスを取り除く。
        row = addWideRow(form, c, row, classHideEmptyMembersCheckbox);
        row = addWideRow(form, c, row, classHideUnlinkedCheckbox);
        // ステレオタイプ別の色分け (CarManager/Activity/aidl 等をパステルで識別)。
        row = addWideRow(form, c, row, classColorCodeStereotypesCheckbox);

        ((JSpinner.DefaultEditor) classCommentMaxLengthSpinner.getEditor()).getTextField()
                .setToolTipText(Messages.get("style.tip.commentMaxLength"));
        row = addLabeledRow(form, c, row, "style.label.commentMaxLength",
                classCommentMaxLengthSpinner);

        row = addLabeledRow(form, c, row, "style.label.hiddenAnnotations",
                classHiddenAnnotationsField);

        // ─── Call Graph ────────────────────────────────────────────────────
        c.gridx = 0; c.gridy = row; c.weightx = 1; c.gridwidth = 3;
        c.insets = new Insets(12, 4, 2, 4);
        form.add(new JSeparator(), c);
        c.insets = new Insets(4, 4, 4, 4);
        c.gridwidth = 1;
        row++;

        c.gridx = 0; c.gridy = row; c.weightx = 0; c.gridwidth = 3;
        JLabel callGraphHeading = new JLabel(Messages.get("style.section.callGraph"));
        callGraphHeading.setFont(callGraphHeading.getFont().deriveFont(java.awt.Font.BOLD));
        form.add(callGraphHeading, c);
        c.gridwidth = 1;
        row++;

        callGraphMaxDepthSpinner.setValue(Math.max(1, Math.min(10, initialCallGraphMaxDepth)));
        ((JSpinner.DefaultEditor) callGraphMaxDepthSpinner.getEditor()).getTextField()
                .setToolTipText(Messages.get("style.tip.maxDepth"));
        addLabeledRow(form, c, row, "style.label.maxDepth", callGraphMaxDepthSpinner);

        return form;
    }

    /** チェックボックス等を 2 列目に全幅で配置し、次の行番号を返す。 */
    private static int addWideRow(JPanel form, GridBagConstraints c, int row,
                                   Component comp) {
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(comp, c);
        c.gridwidth = 1;
        return row + 1;
    }

    /** 1 列目にラベル、2 列目にコンポーネントを全幅配置し、次の行番号を返す。 */
    private static int addLabeledRow(JPanel form, GridBagConstraints c, int row,
                                      String labelKey, Component comp) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(Messages.get(labelKey)), c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 2;
        form.add(comp, c);
        c.gridwidth = 1;
        return row + 1;
    }

    private static int lineTypeIndex(DiagramStyle.LineType t) {
        switch (t) {
            case POLYLINE: return 1;
            case ORTHO: return 2;
            case SPLINE: return 3;
            default: return 0;
        }
    }

    private static DiagramStyle.LineType lineTypeFromIndex(int i) {
        switch (i) {
            case 1: return DiagramStyle.LineType.POLYLINE;
            case 2: return DiagramStyle.LineType.ORTHO;
            case 3: return DiagramStyle.LineType.SPLINE;
            default: return DiagramStyle.LineType.DEFAULT;
        }
    }

    private static int shadowingIndex(DiagramStyle.Shadowing s) {
        switch (s) {
            case ON: return 1;
            case OFF: return 2;
            default: return 0;
        }
    }

    private static DiagramStyle.Shadowing shadowingFromIndex(int i) {
        switch (i) {
            case 1: return DiagramStyle.Shadowing.ON;
            case 2: return DiagramStyle.Shadowing.OFF;
            default: return DiagramStyle.Shadowing.DEFAULT;
        }
    }

    /**
     * 「可読性優先」: 影なし・直交線・余白広めの推奨スタイルを各コントロールへ適用する。
     * あわせてクラス図の関係線色分け (継承=緑/実装=青/利用=灰破線) も有効化し、
     * 大規模図で依存線を追いやすくする。
     */
    private void applyReadabilityPreset() {
        DiagramStyle r = DiagramStyle.readable();
        themeCombo.setSelectedItem(r.getTheme());
        lineTypeCombo.setSelectedIndex(lineTypeIndex(r.getLineType()));
        shadowingCombo.setSelectedIndex(shadowingIndex(r.getShadowing()));
        nodeSepSpinner.setValue(r.getNodeSep());
        rankSepSpinner.setValue(r.getRankSep());
        classColorCodeRelationsCheckbox.setSelected(true);
    }

    private JPanel buildButtons() {
        JButton reset = new JButton(Messages.get("style.btn.reset"));
        reset.setMnemonic(KeyEvent.VK_R);
        reset.setToolTipText(Messages.get("style.tip.reset"));
        reset.addActionListener(e -> resetToDefaults());
        JButton readable = new JButton(Messages.get("style.btn.optimize"));
        readable.setMnemonic(KeyEvent.VK_O);
        readable.setToolTipText(Messages.get("style.tip.optimize"));
        readable.addActionListener(e -> applyReadabilityPreset());
        JButton ok = new JButton(Messages.get("style.btn.ok"));
        ok.addActionListener(e -> {
            result = collect();
            setVisible(false);
        });
        JButton cancel = new JButton(Messages.get("style.btn.cancel"));
        cancel.addActionListener(e -> {
            result = null;
            setVisible(false);
        });
        DialogUtils.installEscapeAndDefault(this, ok);
        return DialogUtils.buildButtonPanel(ok, cancel, readable, reset);
    }

    private void resetToDefaults() {
        DiagramStyle d = DiagramStyle.defaults();
        themeCombo.setSelectedItem(d.getTheme());
        bgColorField.setText(d.getBackgroundColor());
        fontPicker.reset();
        fontSizeSpinner.setValue(d.getFontSize());
        dirDefault.setSelected(true);
        lineTypeCombo.setSelectedIndex(lineTypeIndex(d.getLineType()));
        shadowingCombo.setSelectedIndex(shadowingIndex(d.getShadowing()));
        nodeSepSpinner.setValue(d.getNodeSep());
        rankSepSpinner.setValue(d.getRankSep());
        customSkinparamArea.setText(d.getCustomSkinparam());
        sequenceShowCommentsCheckbox.setSelected(true);
        sequenceCommentStyleCombo.setSelectedItem("INLINE");
        sequenceCommentStyleCombo.setEnabled(true);
        sequenceCommentPlacementCombo.setSelectedItem("AT_CALL_SITE");
        sequenceCommentPlacementCombo.setEnabled(true);
        sequenceQualifyMethodsCheckbox.setSelected(true);
        sequenceMaxDepthSpinner.setValue(5);
        sequenceShowArgsCheckbox.setSelected(false);
        ActivityDiagramPrefs ap = ActivityDiagramPrefs.defaults();
        activityExpandCallbacksCheckbox.setSelected(ap.expandInlineCallbacks);
        activityShowLocalVarsCheckbox.setSelected(ap.showLocalVars);
        activityShowAssignmentsCheckbox.setSelected(ap.showAssignments);
        activityShowCallArgsCheckbox.setSelected(ap.showCallArguments);
        activityShowInlineCommentsCheckbox.setSelected(ap.showInlineComments);
        ClassDiagramPrefs cp = ClassDiagramPrefs.defaults();
        classShowFieldsCheckbox.setSelected(cp.showFields);
        classShowMethodsCheckbox.setSelected(cp.showMethods);
        classShowAnnotationsCheckbox.setSelected(cp.showAnnotations);
        classPublicOnlyCheckbox.setSelected(cp.publicOnly);
        classExcludeExternalCheckbox.setSelected(cp.excludeExternal);
        classMarkExternalSupertypesCheckbox.setSelected(cp.markExternalSupertypes);
        classColorCodeRelationsCheckbox.setSelected(cp.colorCodeRelations);
        classHideEmptyMembersCheckbox.setSelected(cp.hideEmptyMembers);
        classHideUnlinkedCheckbox.setSelected(cp.hideUnlinked);
        classColorCodeStereotypesCheckbox.setSelected(cp.colorCodeStereotypes);
        classCommentMaxLengthSpinner.setValue(cp.commentMaxLength);
        classHiddenAnnotationsField.setText(cp.hiddenAnnotationsCsv());
        callGraphMaxDepthSpinner.setValue(4);
    }

    private void pickBackgroundColor() {
        Color initial = parseColor(bgColorField.getText());
        Color chosen = JColorChooser.showDialog(this, Messages.get("style.colorChooser.title"),
                initial != null ? initial : Color.WHITE);
        if (chosen != null) {
            bgColorField.setText(String.format("#%02X%02X%02X",
                    chosen.getRed(), chosen.getGreen(), chosen.getBlue()));
        }
    }

    private static Color parseColor(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String s = value.trim();
        if (!s.startsWith("#")) {
            return null;
        }
        try {
            return Color.decode(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Result collect() {
        DiagramStyle s = new DiagramStyle();
        Object themeSel = themeCombo.getSelectedItem();
        s.setTheme(themeSel != null ? themeSel.toString() : "");
        s.setBackgroundColor(bgColorField.getText().trim());
        s.setFontName(fontPicker.fontName());
        s.setFontSize((Integer) fontSizeSpinner.getValue());
        if (dirLeftRight.isSelected()) {
            s.setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        } else if (dirTopBottom.isSelected()) {
            s.setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        } else {
            s.setDirection(DiagramStyle.Direction.DEFAULT);
        }
        s.setLineType(lineTypeFromIndex(lineTypeCombo.getSelectedIndex()));
        s.setShadowing(shadowingFromIndex(shadowingCombo.getSelectedIndex()));
        s.setNodeSep(((Number) nodeSepSpinner.getValue()).intValue());
        s.setRankSep(((Number) rankSepSpinner.getValue()).intValue());
        s.setCustomSkinparam(customSkinparamArea.getText());
        Object styleSel = sequenceCommentStyleCombo.getSelectedItem();
        PlantUmlClassDiagram.CommentStyle cs = "NOTE".equals(styleSel)
                ? PlantUmlClassDiagram.CommentStyle.NOTE
                : PlantUmlClassDiagram.CommentStyle.INLINE;
        Object placeSel = sequenceCommentPlacementCombo.getSelectedItem();
        PlantUmlSequenceDiagram.CommentPlacement cp =
                "PARTICIPANT_TOP".equals(placeSel)
                        ? PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP
                        : PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
        ClassDiagramPrefs classPrefs = new ClassDiagramPrefs(
                classShowFieldsCheckbox.isSelected(),
                classShowMethodsCheckbox.isSelected(),
                classShowAnnotationsCheckbox.isSelected(),
                classPublicOnlyCheckbox.isSelected(),
                classExcludeExternalCheckbox.isSelected(),
                classMarkExternalSupertypesCheckbox.isSelected(),
                classColorCodeRelationsCheckbox.isSelected(),
                ((Number) classCommentMaxLengthSpinner.getValue()).intValue(),
                ClassDiagramPrefs.parseCsv(classHiddenAnnotationsField.getText()),
                classHideEmptyMembersCheckbox.isSelected(),
                classHideUnlinkedCheckbox.isSelected(),
                classColorCodeStereotypesCheckbox.isSelected());
        ActivityDiagramPrefs activityPrefs = new ActivityDiagramPrefs(
                activityExpandCallbacksCheckbox.isSelected(),
                activityShowLocalVarsCheckbox.isSelected(),
                activityShowAssignmentsCheckbox.isSelected(),
                activityShowCallArgsCheckbox.isSelected(),
                activityShowInlineCommentsCheckbox.isSelected());
        int seqDepth = ((Number) sequenceMaxDepthSpinner.getValue()).intValue();
        int cgDepth = ((Number) callGraphMaxDepthSpinner.getValue()).intValue();
        return new Result(s, sequenceShowCommentsCheckbox.isSelected(), cs, cp,
                sequenceQualifyMethodsCheckbox.isSelected(), seqDepth,
                sequenceShowArgsCheckbox.isSelected(), activityPrefs,
                classPrefs, cgDepth);
    }

    /**
     * モーダルダイアログを開き、編集された {@link Result}
     * (Style + シーケンス図 + アクティビティ図 + クラス図設定) を返す。
     * キャンセル時は null を返す。
     */
    public static Result showDialog(Component parent, DiagramStyle currentStyle,
                                     boolean currentSeqShowComments,
                                     PlantUmlClassDiagram.CommentStyle currentSeqCommentStyle,
                                     PlantUmlSequenceDiagram.CommentPlacement currentSeqPlacement,
                                     boolean currentSeqQualify,
                                     int currentSeqMaxDepth,
                                     boolean currentSeqShowArgs,
                                     ActivityDiagramPrefs currentActivityPrefs,
                                     ClassDiagramPrefs currentClassPrefs,
                                     int currentCallGraphMaxDepth) {
        Window owner = (parent instanceof Window)
                ? (Window) parent
                : javax.swing.SwingUtilities.getWindowAncestor(parent);
        DiagramStyle initial = currentStyle != null
                ? currentStyle.copy() : DiagramStyle.defaults();
        PlantUmlClassDiagram.CommentStyle initialSeqStyle = currentSeqCommentStyle != null
                ? currentSeqCommentStyle : PlantUmlClassDiagram.CommentStyle.INLINE;
        PlantUmlSequenceDiagram.CommentPlacement initialSeqPlacement =
                currentSeqPlacement != null ? currentSeqPlacement
                        : PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
        StyleSettingsDialog dlg = new StyleSettingsDialog(owner, initial,
                currentSeqShowComments, initialSeqStyle, initialSeqPlacement,
                currentSeqQualify, currentSeqMaxDepth, currentSeqShowArgs,
                currentActivityPrefs, currentClassPrefs, currentCallGraphMaxDepth);
        dlg.setVisible(true);
        return dlg.result;
    }
}
