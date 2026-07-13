// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * コマンドパレット用のコマンド一覧を、メニューと同じ {@link MenuBarBuilder.Callbacks} から組み立てる。
 * メニュー項目とアクションの単一ソースを共有することで、両者の挙動がずれないようにする。
 *
 * <p>表示ラベルは {@code cmd.*} の i18n キーで引く (メニュー同様に EN/JA を切り替えるため)。</p>
 */
final class AppCommands {

    private AppCommands() {
    }

    /** プラットフォーム標準の修飾キー表記 (macOS は ⌘、その他は "Ctrl+")。 */
    private static final String MOD =
            MenuBarBuilder.menuShortcutMask() == java.awt.event.InputEvent.META_DOWN_MASK
                    ? "⌘" : "Ctrl+";
    private static final String SHIFT = "Shift+";

    /** 現在のコールバックから、null でないアクションだけをコマンド化して返す。 */
    static List<CommandPalette.Command> from(MenuBarBuilder.Callbacks cb) {
        List<CommandPalette.Command> list = new ArrayList<>();
        add(list, "cmd.file.openProject", cb.chooseProject, MOD + "O");
        add(list, "cmd.file.openArchive", cb.openArchive);
        // 新規 UML 図はテンプレートごとにコマンド化する (メニューのサブメニューと対応)。
        if (cb.newUmlDiagram != null) {
            for (PumlTemplate t : PumlTemplate.values()) {
                String shortcut = t == PumlTemplate.CLASS ? MOD + "N" : null;
                list.add(new CommandPalette.Command(
                        Messages.get("cmd.file.newUml") + t.displayName(), shortcut,
                        () -> cb.newUmlDiagram.accept(t)));
            }
        }
        add(list, "cmd.file.openPuml", cb.openPumlFile);
        add(list, "cmd.file.savePuml", cb.savePumlTab, MOD + "S");
        add(list, "cmd.file.savePumlAs", cb.savePumlTabAs);
        add(list, "cmd.file.diffSaved", cb.diffPumlVsSaved);
        // Save As (Export) の実アクセラレータは Ctrl+Shift+S (MenuBarBuilder 参照)。
        // Ctrl+S は PUML 保存用なので、パレット表示も Shift 付きに合わせる。
        add(list, "cmd.file.saveAs", cb.chooseAndExport, MOD + SHIFT + "S");
        add(list, "cmd.file.exportPerFolder", cb.exportClassDiagramsPerFolder);
        add(list, "cmd.file.exportFunctions", cb.exportFunctionList);
        add(list, "cmd.file.exportMembers", cb.exportMemberList);
        add(list, "cmd.file.refresh", cb.refreshDiagram, "F5");
        add(list, "cmd.file.closeTab", cb.closeActiveTab, MOD + "W");
        add(list, "cmd.file.closeOthers", cb.closeOtherTabs);
        add(list, "cmd.file.closeRight", cb.closeTabsToRight);
        add(list, "cmd.file.closeAllTabs", cb.closeAllTabs);
        add(list, "cmd.file.reopenTab", cb.reopenClosedTab, MOD + SHIFT + "T");
        add(list, "cmd.file.cancelLoading", cb.cancelLoading);
        add(list, "cmd.file.exit", cb.exitApp);
        add(list, "cmd.view.toggleSidebar", cb.toggleSidebar, MOD + "B");
        add(list, "cmd.view.openSource", cb.openSourceForActiveTab, MOD + "U");
        add(list, "cmd.view.addNote", cb.addNoteToActiveTab, MOD + SHIFT + "N");
        add(list, "cmd.view.notesPanel", cb.toggleNotesPanel, MOD + SHIFT + "J");
        add(list, "cmd.view.focusExplorer", cb.focusExplorer, MOD + SHIFT + "E");
        add(list, "cmd.view.navigateBack", cb.navigateBack, "Alt+Left");
        add(list, "cmd.view.navigateForward", cb.navigateForward, "Alt+Right");
        add(list, "cmd.diagram.findInDiagram", cb.findInDiagram, MOD + "F");
        add(list, "cmd.diagram.search", cb.openEntitySearch, MOD + SHIFT + "F");
        add(list, "cmd.diagram.seqEntry", cb.pickSequenceEntry);
        add(list, "cmd.diagram.filterParticipants", cb.openParticipantFilterDialog);
        add(list, "cmd.diagram.clearParticipants", cb.clearSequenceParticipants);
        add(list, "cmd.diagram.activityMethod", cb.pickActivityEntry);
        add(list, "cmd.diagram.layoutFile", cb.pickLayoutFile);
        add(list, "cmd.diagram.navGraph", cb.pickNavigationGraph);
        add(list, "cmd.diagram.scope", cb.openScopeDialog);
        add(list, "cmd.diagram.clearScope", cb.clearScope);
        // 実際のアクセラレータは VK_EQUALS (MenuBarBuilder)。"Ctrl++" と表示すると
        // ユーザーが Ctrl+Shift+= を試して動かないため、実キーに合わせて表示する。
        add(list, "cmd.view.zoomIn", cb.zoomIn, MOD + "=");
        add(list, "cmd.view.zoomOut", cb.zoomOut, MOD + "-");
        add(list, "cmd.view.zoom100", cb.zoomReset, MOD + "0");
        add(list, "cmd.view.zoomFit", cb.zoomToFit, MOD + SHIFT + "0");
        add(list, "cmd.view.moveToNewWindow", cb.moveTabToNewWindow, MOD + SHIFT + "M");
        add(list, "cmd.settings.style", cb.openStyleSettings);
        add(list, "cmd.settings.prefs", cb.openPreferences, MOD + ",");
        add(list, "cmd.settings.graphviz", cb.enableGraphviz);
        add(list, "cmd.settings.clearCache", cb.clearAnalysisCache);
        add(list, "cmd.help.logViewer", cb.openLogViewer, MOD + SHIFT + "L");
        add(list, "cmd.help.errorReference", cb.openErrorReference);
        if (cb.applyPreset != null) {
            for (DiagramPreset p : DiagramPreset.values()) {
                if (p != DiagramPreset.CUSTOM) {
                    list.add(new CommandPalette.Command(
                            Messages.get("cmd.diagram.preset") + " " + p.getDisplayName(),
                            () -> cb.applyPreset.accept(p)));
                }
            }
        }
        // 図種切替をコマンド化する (メニューの図種ラジオと対応)。メソッド系図種
        // (SEQUENCE/ACTIVITY/CALLGRAPH) とレイアウトの画面/実寸は各図タブ上部の切替バーへ
        // 一本化されておりメニューのラジオからも外れているため、パレットからも除外して揃える。
        if (cb.selectDiagramKindFromMenu != null) {
            for (DiagramKind k : DiagramKind.values()) {
                if (ToolBarBuilder.DIAGRAMS_METHOD.contains(k)
                        || ToolBarBuilder.LAYOUT_VARIANT_HIDDEN.contains(k)) {
                    continue;
                }
                final DiagramKind kind = k;
                list.add(new CommandPalette.Command(
                        Messages.get("cmd.diagram.switchTo") + " " + k.getDisplayName(),
                        () -> cb.selectDiagramKindFromMenu.accept(kind)));
            }
        }
        return list;
    }

    private static void add(List<CommandPalette.Command> list, String labelKey, Runnable action) {
        add(list, labelKey, action, null);
    }

    private static void add(List<CommandPalette.Command> list, String labelKey,
                            Runnable action, String shortcut) {
        if (action != null) {
            list.add(new CommandPalette.Command(Messages.get(labelKey), shortcut, action));
        }
    }
}
