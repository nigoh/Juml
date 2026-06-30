// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * ツリーアイコンの凡例パネル。
 *
 * <p>カテゴリ別にアイコンと名称を 2 列グリッドで並べる。
 * ヘッダ行をクリックすると展開/折りたたみが切り替わる。</p>
 */
final class TreeIconLegendPanel extends JPanel {

    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font CATEGORY_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);

    /** ヘッダ背景 (テーマ追従)。明色 L&F では淡いグレー、ダーク L&F では暗いトーンになる。 */
    private static Color headerBg() {
        Color c = UIManager.getColor("TableHeader.background");
        if (c == null) {
            c = UIManager.getColor("Panel.background");
        }
        return c != null ? c : new Color(0xECEFF1);
    }

    /** カテゴリ見出しの前景 (テーマ追従)。 */
    private static Color categoryFg() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : new Color(0x546E7A);
    }

    private final JPanel content;
    private final JLabel toggleLabel;
    private boolean expanded = false;

    TreeIconLegendPanel() {
        super(new BorderLayout(0, 0));
        Color sep = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                sep != null ? sep : new Color(0xBDBDBD)));

        // ── ヘッダ（クリックで展開/折りたたみ） ─────────────────
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setBackground(headerBg());
        header.setBorder(new EmptyBorder(3, 6, 3, 6));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel title = new JLabel(juml.util.Messages.get("legend.title"));
        title.setFont(CATEGORY_FONT);
        title.setForeground(categoryFg());

        toggleLabel = new JLabel("▶");
        toggleLabel.setFont(CATEGORY_FONT);
        toggleLabel.setForeground(categoryFg());

        header.add(toggleLabel, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        header.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                toggle();
            }
        });

        // ── 凡例コンテンツ ────────────────────────────────────────
        content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(4, 6, 6, 6));
        content.setBackground(UIManager.getColor("Panel.background"));

        addCategory(juml.util.Messages.get("legend.cat.structure"), "アイコン");
        addRow(TreeNodeIcon.MODULE,   "Module");
        addRow(TreeNodeIcon.PACKAGE,  "Package");

        addCategory(juml.util.Messages.get("legend.cat.javaType"), "文字バッジ");
        addRow(TreeNodeIcon.CLASS,       "Class");
        addRow(TreeNodeIcon.INTERFACE,   "Interface");
        addRow(TreeNodeIcon.ENUM,        "Enum");
        addRow(TreeNodeIcon.ANNOTATION,  "Annotation");
        addRow(TreeNodeIcon.AIDL,        "AIDL");

        addCategory(juml.util.Messages.get("legend.cat.methodDiagram"), "アイコン");
        addRow(TreeNodeIcon.METHOD,   "Method");
        addRow(TreeNodeIcon.SEQUENCE, "Sequence");
        addRow(TreeNodeIcon.ACTIVITY, "Activity");

        addCategory(juml.util.Messages.get("legend.cat.android"), "アイコン");
        addRow(TreeNodeIcon.MANIFEST,            "Manifest");
        addRow(TreeNodeIcon.COMPONENT_ACTIVITY,  "Activity");
        addRow(TreeNodeIcon.COMPONENT_SERVICE,   "Service");
        addRow(TreeNodeIcon.COMPONENT_RECEIVER,  "Receiver");
        addRow(TreeNodeIcon.COMPONENT_PROVIDER,  "Provider");
        addRow(TreeNodeIcon.PERMISSION,          "Permission");
        addRow(TreeNodeIcon.FEATURE,             "Feature");

        content.setVisible(false);

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    private void toggle() {
        expanded = !expanded;
        content.setVisible(expanded);
        toggleLabel.setText(expanded ? "▼" : "▶");
        revalidate();
        repaint();
    }

    private void addCategory(String label, String shape) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel cat = new JLabel(label);
        cat.setFont(CATEGORY_FONT);
        cat.setForeground(categoryFg());
        cat.setBorder(new EmptyBorder(4, 0, 1, 0));
        row.add(cat);

        JLabel shapeHint = new JLabel("(" + shape + ")");
        shapeHint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        Color hintFg = UIManager.getColor("Label.disabledForeground");
        shapeHint.setForeground(hintFg != null ? hintFg : new Color(0x9E9E9E));
        shapeHint.setBorder(new EmptyBorder(4, 0, 1, 0));
        row.add(shapeHint);

        content.add(row);
    }

    private void addRow(TreeNodeIcon icon, String label) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel iconLabel = new JLabel(icon);
        row.add(iconLabel);

        JLabel text = new JLabel(label);
        text.setFont(LABEL_FONT);
        row.add(text);

        content.add(row);
    }
}
