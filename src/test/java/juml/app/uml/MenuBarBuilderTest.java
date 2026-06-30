// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import org.junit.Test;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import java.awt.Component;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class MenuBarBuilderTest {

    private MenuBarBuilder.Result buildDefault() {
        MenuBarBuilder.Callbacks cb = new MenuBarBuilder.Callbacks();
        cb.chooseProject = () -> {};
        cb.chooseAndExport = () -> {};
        cb.exportClassDiagramsPerFolder = () -> {};
        cb.refreshDiagram = () -> {};
        cb.cancelLoading = () -> {};
        cb.exitApp = () -> {};
        cb.loadProject = f -> {};
        cb.openEntitySearch = () -> {};
        cb.pickSequenceEntry = () -> {};
        cb.openParticipantFilterDialog = () -> {};
        cb.clearSequenceParticipants = () -> {};
        cb.pickActivityEntry = () -> {};
        cb.pickLayoutFile = () -> {};
        cb.pickNavigationGraph = () -> {};
        cb.applyPreset = p -> {};
        cb.openScopeDialog = () -> {};
        cb.clearScope = () -> {};
        cb.selectDiagramKindFromMenu = k -> {};
        cb.syncDiagramToggle = k -> {};
        cb.applyTheme = t -> {};
        cb.openStyleSettings = () -> {};
        cb.openPreferences = () -> {};
        cb.clearAnalysisCache = () -> {};
        cb.zoomIn = () -> {};
        cb.zoomOut = () -> {};
        cb.zoomReset = () -> {};
        cb.zoomToFit = () -> {};
        return new MenuBarBuilder(DiagramKind.CLASS, 0, cb, null).build();
    }

    @Test
    public void build_menuBarHasSixTopLevelMenus() {
        MenuBarBuilder.Result r = buildDefault();
        JMenuBar bar = r.menuBar;
        // File, Diagram, View, Style, Settings, Help
        assertEquals(6, bar.getMenuCount());
    }

    @Test
    public void build_settingsMenuIsBeforeHelp() {
        MenuBarBuilder.Result r = buildDefault();
        JMenuBar bar = r.menuBar;
        // ラベルは i18n される。現在の言語に対応する文言で比較する。
        assertEquals(Messages.get("menubar.settings"), bar.getMenu(4).getText());
        assertEquals(Messages.get("menubar.help"), bar.getMenu(5).getText());
    }

    @Test
    public void build_cancelLoadingItemIsInitiallyDisabled() {
        MenuBarBuilder.Result r = buildDefault();
        assertFalse("cancelLoadingItem should start disabled", r.cancelLoadingItem.isEnabled());
    }

    @Test
    public void build_diagramItemsContainsAllKinds() {
        MenuBarBuilder.Result r = buildDefault();
        for (DiagramKind k : DiagramKind.values()) {
            assertNotNull("Missing diagram item for " + k, r.diagramItems.get(k));
        }
        assertEquals(DiagramKind.values().length, r.diagramItems.size());
    }

    @Test
    public void build_initialKindIsSelectedInMenu() {
        MenuBarBuilder.Result r = buildDefault();
        JRadioButtonMenuItem classItem = r.diagramItems.get(DiagramKind.CLASS);
        assertTrue("CLASS menu item should be selected initially", classItem.isSelected());
    }

    @Test
    public void build_themeItemsNotEmpty() {
        MenuBarBuilder.Result r = buildDefault();
        assertFalse("Theme items map should not be empty", r.themeItems.isEmpty());
    }

    @Test
    public void build_reopenClosedTabItemInvokesCallback() {
        AtomicBoolean fired = new AtomicBoolean(false);
        MenuBarBuilder.Callbacks cb = new MenuBarBuilder.Callbacks();
        cb.reopenClosedTab = () -> fired.set(true);
        JMenuBar bar = new MenuBarBuilder(DiagramKind.CLASS, 0, cb, null).build().menuBar;

        JMenuItem item = findItem(bar.getMenu(0), Messages.get("menubar.file.reopenTab"));
        assertNotNull("File menu should contain a Reopen Closed Tab item", item);
        item.doClick();
        assertTrue("Reopen Closed Tab should invoke its callback", fired.get());
    }

    /** {@code menu} 直下から指定ラベルの {@link JMenuItem} を探す (見つからなければ null)。 */
    private static JMenuItem findItem(JMenu menu, String label) {
        for (Component c : menu.getMenuComponents()) {
            if (c instanceof JMenuItem && label.equals(((JMenuItem) c).getText())) {
                return (JMenuItem) c;
            }
        }
        return null;
    }
}
