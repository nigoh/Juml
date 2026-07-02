// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * シーケンス図に登場する participant をフィルタするモーダルダイアログ。
 *
 * <p>{@link juml.core.formats.uml.PlantUmlSequenceDiagram#collectParticipants}
 * で抽出した participant をチェックボックスで表示し、ユーザがチェックを外した
 * ものを「隠す対象」として返す。OK 押下時は隠す集合 (Set&lt;String&gt;) を返し、
 * キャンセル時は null を返す。</p>
 */
final class SequenceParticipantFilterDialog extends JDialog {

    private final Map<String, JCheckBox> checks = new LinkedHashMap<>();
    private Set<String> result;

    private SequenceParticipantFilterDialog(Window owner, String entryLabel,
                                             Set<String> allParticipants,
                                             Set<String> initiallyHidden) {
        super(owner, Messages.get("dlg.seqFilter.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildHeader(entryLabel, allParticipants.size()), BorderLayout.NORTH);
        add(new JScrollPane(buildBody(allParticipants, initiallyHidden)),
                BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(420, 480));
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader(String entryLabel, int total) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel(Messages.get("dlg.seqFilter.entryPrefix") + " " + entryLabel),
                BorderLayout.NORTH);
        p.add(new JLabel(Messages.get("dlg.seqFilter.instruction")
                + " " + total + Messages.get("dlg.seqFilter.totalSuffix")),
                BorderLayout.CENTER);
        return p;
    }

    private JPanel buildBody(Set<String> participants, Set<String> hidden) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (String name : participants) {
            JCheckBox cb = new JCheckBox(name);
            cb.setSelected(!hidden.contains(name));
            list.add(cb);
            checks.put(name, cb);
        }
        list.add(Box.createVerticalGlue());
        return list;
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton selectAll = new JButton(Messages.get("dlg.seqFilter.selectAll"));
        selectAll.addActionListener(e -> setAll(true));
        JButton clearAll = new JButton(Messages.get("dlg.seqFilter.clearAll"));
        clearAll.addActionListener(e -> setAll(false));
        left.add(selectAll);
        left.add(clearAll);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton ok = new JButton(Messages.get("dlg.ok"));
        ok.addActionListener(e -> commit());
        JButton cancel = new JButton(Messages.get("dlg.cancel"));
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        right.add(ok);
        right.add(cancel);
        // 開いた直後に先頭チェックボックスへフォーカスし、Space で即トグルできるようにする。
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (!checks.isEmpty()) {
                checks.values().iterator().next().requestFocusInWindow();
            } else {
                ok.requestFocusInWindow();
            }
        });

        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        DialogUtils.installEscapeAndDefault(this, ok);
        return p;
    }

    private void setAll(boolean selected) {
        for (JCheckBox cb : checks.values()) {
            cb.setSelected(selected);
        }
    }

    private void commit() {
        // チェックが外れている participant = 隠す対象
        Set<String> hidden = new LinkedHashSet<>();
        for (Map.Entry<String, JCheckBox> e : checks.entrySet()) {
            if (!e.getValue().isSelected()) {
                hidden.add(e.getKey());
            }
        }
        result = hidden;
        dispose();
    }

    /**
     * モーダル表示し、隠す participant 集合を返す。キャンセル時は null。
     *
     * @param parent 親コンポーネント
     * @param entryLabel ヘッダ表示用ラベル ({@code "Class.method"})
     * @param participants 抽出した全 participant (表示順)
     * @param initiallyHidden 既に隠れている participant (チェック OFF 初期状態)
     */
    static Set<String> show(Component parent, String entryLabel,
                             Set<String> participants, Set<String> initiallyHidden) {
        Window owner = (parent instanceof Window)
                ? (Window) parent
                : javax.swing.SwingUtilities.getWindowAncestor(parent);
        SequenceParticipantFilterDialog dlg = new SequenceParticipantFilterDialog(
                owner, entryLabel, participants,
                initiallyHidden == null ? java.util.Collections.emptySet() : initiallyHidden);
        dlg.setVisible(true);
        return dlg.result;
    }
}
