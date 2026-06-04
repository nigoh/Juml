// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JOptionPane;

/**
 * 図のエントリ (シーケンス/アクティビティ/コールグラフ起点・レイアウト・ナビゲーション・
 * participant フィルタ) をダイアログで選択し、{@link DiagramController} の状態へ反映する補助クラス。
 */
final class DiagramEntryDialogs {

    private final DiagramController c;

    DiagramEntryDialogs(DiagramController c) {
        this.c = c;
    }

    public void pickSequenceEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, c.cache().getClasses());
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No methods found in this project.",
                    "Sequence diagram", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            // 起点が変わったら participant フィルタはリセットする
            // (旧起点の participant 名は新図に存在しない可能性があるため)
            c.state.sequenceHiddenParticipants.clear();
            c.openEntryDiagram(picked, DiagramKind.SEQUENCE);
        }
    }

    /**
     * 現在のシーケンス図起点に登場する participant をフィルタダイアログで選択できるようにする。
     * 選択結果は {@code state.sequenceHiddenParticipants} に保存され、再描画時の
     * {@link DiagramRequest#getSequenceHiddenParticipants()} に渡される。
     */
    public void openParticipantFilterDialog() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (c.state.sequenceEntry == null || c.state.sequenceEntry.isEmpty()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Choose a sequence entry first (Diagram → Choose Sequence Entry...).",
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int dot = c.state.sequenceEntry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String cls = c.state.sequenceEntry.substring(0, dot);
        String method = c.state.sequenceEntry.substring(dot + 1);
        java.util.Set<String> all =
                juml.core.formats.uml.PlantUmlSequenceDiagram.collectParticipants(
                        c.cache().getClasses(), cls, method, null);
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No participants found for " + c.state.sequenceEntry,
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Set<String> picked = SequenceParticipantFilterDialog.show(
                c.parentFrame, c.state.sequenceEntry, all, c.state.sequenceHiddenParticipants);
        if (picked != null) {
            c.state.sequenceHiddenParticipants.clear();
            c.state.sequenceHiddenParticipants.addAll(picked);
            int total = all.size();
            int hidden = c.state.sequenceHiddenParticipants.size();
            c.statusLabel.setText("Sequence filter: showing " + (total - hidden) + "/" + total
                    + " participants");
            c.applyStateToActiveTab();
        }
    }

    /**
     * アクティビティ図用にメソッドを選択する。SequenceEntryDialog を流用し、
     * タイトルだけ「Select activity method」に差し替える。
     */
    public void pickActivityEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, c.cache().getClasses());
        dlg.setTitle("Select activity method");
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No methods found in this project.",
                    "Activity diagram", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            c.openEntryDiagram(picked, DiagramKind.ACTIVITY);
        }
    }

    public void pickCallGraphEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, c.cache().getClasses());
        dlg.setTitle("Select call graph entry method");
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No methods found in this project.",
                    "Call graph", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            c.openEntryDiagram(picked, DiagramKind.CALLGRAPH);
        }
    }

    public void pickLayoutFile() {
        String picked = LayoutFileChooserDialog.chooseLayoutKey(c.parentFrame, c.cache());
        if (picked == null) {
            return;
        }
        c.openLayoutDiagram(picked);
    }

    public void pickLayoutScreenFile() {
        String picked = LayoutFileChooserDialog.chooseLayoutKey(c.parentFrame, c.cache());
        if (picked == null) {
            return;
        }
        c.openLayoutScreenDiagram(picked);
    }

    public void pickNavigationGraph() {
        String picked = NavigationFileChooserDialog.chooseNavigationKey(c.parentFrame, c.cache());
        if (picked == null) {
            return;
        }
        c.openNavigationDiagram(picked);
    }
}
