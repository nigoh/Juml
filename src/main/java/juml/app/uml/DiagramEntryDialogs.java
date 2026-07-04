// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.util.Messages;

import javax.swing.JOptionPane;
import java.util.List;
import java.util.TreeSet;

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
                    Messages.get("dlg.openProjectFirst"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LazyDetail.withDetailedClasses(c.cache(), c.parentFrame, classes -> {
            SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, classes);
            if (dlg.getCandidateCount() == 0) {
                JOptionPane.showMessageDialog(c.parentFrame,
                        Messages.get("dlg.noMethods"),
                        Messages.get("diagram.kind.SEQUENCE"), JOptionPane.INFORMATION_MESSAGE);
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
        });
    }

    /**
     * 現在のシーケンス図起点に登場する participant をフィルタダイアログで選択できるようにする。
     * 選択結果は {@code state.sequenceHiddenParticipants} に保存され、再描画時の
     * {@link DiagramRequest#getSequenceHiddenParticipants()} に渡される。
     */
    public void openParticipantFilterDialog() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    Messages.get("dlg.openProjectFirst"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (c.state.sequenceEntry == null || c.state.sequenceEntry.isEmpty()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    Messages.get("dlg.chooseSequenceEntryFirst"),
                    Messages.get("dlg.sequenceParticipants.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int dot = c.state.sequenceEntry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String cls = c.state.sequenceEntry.substring(0, dot);
        String method = c.state.sequenceEntry.substring(dot + 1);
        LazyDetail.withDetailedClasses(c.cache(), c.parentFrame, classes -> {
            java.util.Set<String> all =
                    juml.core.formats.uml.PlantUmlSequenceDiagram.collectParticipants(
                            classes, cls, method, null);
            if (all.isEmpty()) {
                JOptionPane.showMessageDialog(c.parentFrame,
                        Messages.get("dlg.noParticipantsFor") + c.state.sequenceEntry,
                        Messages.get("dlg.sequenceParticipants.title"),
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // 参加者フィルタはタブ固有。アクティブタブの spec の隠し participant を起点に開き、
            // 結果もそのタブの spec だけへ適用する。state は新規タブ生成時の下書きとして更新 (#40)。
            DiagramRequest activeSpec = c.activeTabSpec();
            java.util.Set<String> seedHidden = activeSpec != null
                    ? activeSpec.getSequenceHiddenParticipants() : c.state.sequenceHiddenParticipants;
            java.util.Set<String> picked = SequenceParticipantFilterDialog.show(
                    c.parentFrame, c.state.sequenceEntry, all, seedHidden);
            if (picked != null) {
                c.state.sequenceHiddenParticipants.clear();
                c.state.sequenceHiddenParticipants.addAll(picked);
                int total = all.size();
                int hidden = picked.size();
                c.statusLabel.setText(Messages.get("status.sequenceFilterPrefix")
                        + (total - hidden) + "/" + total
                        + Messages.get("status.sequenceFilterSuffix"));
                c.applySpecToActiveTab(activeSpec != null
                        ? activeSpec.withSequenceHiddenParticipants(picked) : null);
            }
        });
    }

    /**
     * スコープ絞り込みダイアログを開き、選択結果をアクティブタブへ適用する。スコープはタブ固有
     * 状態のため、アクティブタブの spec を起点に開き、結果もそのタブの spec だけへ適用する。
     * {@code DiagramState.currentScope} は新規タブ生成時の下書きとしてのみ更新する (#40)。
     */
    public void openScopeDialog() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    Messages.get("dlg.noProject.message"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DiagramRequest activeSpec = c.activeTabSpec();
        DiagramScope seed = activeSpec != null ? activeSpec.getScope() : c.state.currentScope;
        DiagramScopeDialog dlg = newScopeDialog(seed);
        dlg.setVisible(true);
        DiagramScope picked = dlg.getResult();
        if (picked != null) {
            DiagramScope newScope = picked.isEmpty() ? null : picked;
            c.state.currentScope = newScope;
            c.applySpecToActiveTab(activeSpec != null ? activeSpec.withScope(newScope) : null);
        }
    }

    /**
     * Scope ダイアログを表示し、ユーザーが選んだスコープを返す (アクティブタブには適用しない)。
     * 大規模図ガードで新規図のスコープを選ばせるのに使う。キャンセル/空選択時は null。
     */
    DiagramScope promptForScope() {
        if (!c.cache().isLoaded()) {
            return null;
        }
        DiagramScopeDialog dlg = newScopeDialog(c.state.currentScope);
        dlg.setVisible(true);
        DiagramScope picked = dlg.getResult();
        return (picked == null || picked.isEmpty()) ? null : picked;
    }

    /** 現在のプロジェクトのパッケージ/モジュール候補で Scope ダイアログを構築する (seed で初期化)。 */
    private DiagramScopeDialog newScopeDialog(DiagramScope seed) {
        TreeSet<String> packages = new TreeSet<>();
        TreeSet<String> modules = new TreeSet<>(c.cache().getClassToModule().values());
        for (JavaClassInfo cls : c.cache().getClasses()) {
            String p = cls.getPackageName();
            if (p != null && !p.isEmpty()) {
                packages.add(p);
            }
        }
        return new DiagramScopeDialog(c.parentFrame,
                List.copyOf(packages), List.copyOf(modules), seed);
    }

    /**
     * アクティビティ図用にメソッドを選択する。SequenceEntryDialog を流用し、
     * タイトルだけ「Select activity method」に差し替える。
     */
    public void pickActivityEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    Messages.get("dlg.openProjectFirst"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LazyDetail.withDetailedClasses(c.cache(), c.parentFrame, classes -> {
            SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, classes);
            dlg.setTitle(Messages.get("dlg.selectActivityMethod"));
            if (dlg.getCandidateCount() == 0) {
                JOptionPane.showMessageDialog(c.parentFrame,
                        Messages.get("dlg.noMethods"),
                        Messages.get("diagram.kind.ACTIVITY"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            dlg.setVisible(true);
            String picked = dlg.getSelectedEntry();
            if (picked != null) {
                c.openEntryDiagram(picked, DiagramKind.ACTIVITY);
            }
        });
    }

    public void pickCallGraphEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    Messages.get("dlg.openProjectFirst"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LazyDetail.withDetailedClasses(c.cache(), c.parentFrame, classes -> {
            SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, classes);
            dlg.setTitle(Messages.get("dlg.selectCallGraphEntry"));
            if (dlg.getCandidateCount() == 0) {
                JOptionPane.showMessageDialog(c.parentFrame,
                        Messages.get("dlg.noMethods"),
                        Messages.get("diagram.kind.CALLGRAPH"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            dlg.setVisible(true);
            String picked = dlg.getSelectedEntry();
            if (picked != null) {
                c.openEntryDiagram(picked, DiagramKind.CALLGRAPH);
            }
        });
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

    public void pickLayoutRenderFile() {
        String picked = LayoutFileChooserDialog.chooseLayoutKey(c.parentFrame, c.cache());
        if (picked == null) {
            return;
        }
        c.openLayoutRenderDiagram(picked);
    }

    public void pickNavigationGraph() {
        String picked = NavigationFileChooserDialog.chooseNavigationKey(c.parentFrame, c.cache());
        if (picked == null) {
            return;
        }
        c.openNavigationDiagram(picked);
    }
}
