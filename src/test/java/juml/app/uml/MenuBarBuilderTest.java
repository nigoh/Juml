// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import org.junit.Test;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
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

    /**
     * メソッド系図種 (SEQUENCE/ACTIVITY/CALLGRAPH) を除く全図種にラジオ項目が作られること。
     * メソッド系の切替はメソッド図タブ上部の切替バーへ一本化したため、メニューからは外す。
     */
    @Test
    public void build_diagramItemsContainsEveryNonMethodKind() {
        MenuBarBuilder.Result r = buildDefault();
        for (DiagramKind k : DiagramKind.values()) {
            if (ToolBarBuilder.DIAGRAMS_METHOD.contains(k)) {
                assertNull("Method kind " + k + " should not appear in the Diagram menu",
                        r.diagramItems.get(k));
            } else {
                assertNotNull("Missing diagram item for " + k, r.diagramItems.get(k));
            }
        }
        assertEquals(DiagramKind.values().length - ToolBarBuilder.DIAGRAMS_METHOD.size(),
                r.diagramItems.size());
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
    public void build_viewMenuContainsNavigateBackAndForward() {
        MenuBarBuilder.Callbacks cb = new MenuBarBuilder.Callbacks();
        AtomicBoolean backFired = new AtomicBoolean(false);
        AtomicBoolean fwdFired = new AtomicBoolean(false);
        cb.navigateBack = () -> backFired.set(true);
        cb.navigateForward = () -> fwdFired.set(true);
        JMenuBar bar = new MenuBarBuilder(DiagramKind.CLASS, 0, cb, null).build().menuBar;
        // View menu is index 2
        JMenu viewMenu = bar.getMenu(2);
        JMenuItem back = findItem(viewMenu, Messages.get("menubar.view.navigateBack"));
        JMenuItem fwd = findItem(viewMenu, Messages.get("menubar.view.navigateForward"));
        assertNotNull("View menu should contain Navigate Back item", back);
        assertNotNull("View menu should contain Navigate Forward item", fwd);
        back.doClick();
        assertTrue("Navigate Back should invoke its callback", backFired.get());
        fwd.doClick();
        assertTrue("Navigate Forward should invoke its callback", fwdFired.get());
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

    // -------------------------------------------------------------------------
    // タブ系メニュー活性制御 (menuSelected フック) のテスト
    // -------------------------------------------------------------------------

    /**
     * タブ数・閉じタブ履歴・フォーカス状態の supplier を設定したビルダーを返す。
     * Close Other Tabs / Close Tabs to the Right / Close All Tabs / Reopen Closed Tab
     * の各メニュー項目が File メニューに追加されるよう、対応する callback も設定する。
     */
    private static MenuBarBuilder.Result buildWithTabSuppliers(
            int dynCount, int histSize, boolean dynFocused, boolean hasRight) {
        MenuBarBuilder.Callbacks cb = new MenuBarBuilder.Callbacks();
        cb.chooseProject = () -> {};
        cb.openArchive = () -> {};
        cb.chooseAndExport = () -> {};
        cb.exportClassDiagramsPerFolder = () -> {};
        cb.exportFunctionList = () -> {};
        cb.exportMemberList = () -> {};
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
        cb.closeActiveTab = () -> {};
        cb.reopenClosedTab = () -> {};
        cb.closeOtherTabs = () -> {};
        cb.closeTabsToRight = () -> {};
        cb.closeAllTabs = () -> {};
        cb.dynamicTabCount = () -> dynCount;
        cb.closedTabHistorySize = () -> histSize;
        cb.dynamicTabFocused = () -> dynFocused;
        cb.hasTabsToRightOfActive = () -> hasRight;
        return new MenuBarBuilder(DiagramKind.CLASS, 0, cb, null).build();
    }

    /**
     * File メニューに登録されたすべての {@link MenuListener#menuSelected} を呼び出す。
     * 実 UI での「メニューを開く」操作を最小限のコードで再現する。
     * {@link MenuEvent} は当該リスナー実装では参照されないため null でよい。
     */
    private static void fireMenuSelected(JMenu menu) {
        for (MenuListener l : menu.getListeners(MenuListener.class)) {
            l.menuSelected(null);
        }
    }

    @Test
    public void tabEnablement_noDynamicTabs_closeAllDisabled() {
        MenuBarBuilder.Result r = buildWithTabSuppliers(0, 0, true, false);
        JMenu fileMenu = r.menuBar.getMenu(0);
        fireMenuSelected(fileMenu);

        JMenuItem closeAll = findItem(fileMenu, Messages.get("menubar.file.closeAllTabs"));
        assertNotNull("File メニューに Close All Tabs 項目があるはず", closeAll);
        assertFalse("動的タブ 0 枚のとき Close All Tabs は無効のはず", closeAll.isEnabled());
    }

    @Test
    public void tabEnablement_hasDynamicTabs_closeAllEnabled() {
        MenuBarBuilder.Result r = buildWithTabSuppliers(2, 0, true, false);
        JMenu fileMenu = r.menuBar.getMenu(0);
        fireMenuSelected(fileMenu);

        JMenuItem closeAll = findItem(fileMenu, Messages.get("menubar.file.closeAllTabs"));
        assertNotNull("File メニューに Close All Tabs 項目があるはず", closeAll);
        assertTrue("動的タブ 2 枚のとき Close All Tabs は有効のはず", closeAll.isEnabled());
    }

    @Test
    public void tabEnablement_oneDynamicTab_closeAllEnabled() {
        // 境界値: 実装は dyn >= 1 で有効。dyn = 1 でも有効であることを固定する
        // (誤って >= 2 に変えられた場合に検出するため)。
        MenuBarBuilder.Result r = buildWithTabSuppliers(1, 0, true, false);
        JMenu fileMenu = r.menuBar.getMenu(0);
        fireMenuSelected(fileMenu);

        JMenuItem closeAll = findItem(fileMenu, Messages.get("menubar.file.closeAllTabs"));
        assertNotNull("File メニューに Close All Tabs 項目があるはず", closeAll);
        assertTrue("動的タブ 1 枚のとき Close All Tabs は有効のはず", closeAll.isEnabled());
    }

    @Test
    public void tabEnablement_closeTabsToRight_followsHasRightSupplier() {
        // hasTabsToRightOfActive supplier の true/false が Close Tabs to the Right の
        // 活性状態へそのまま反映されることを両分岐で検証する。
        MenuBarBuilder.Result rTrue = buildWithTabSuppliers(2, 0, true, true);
        JMenu fileMenuTrue = rTrue.menuBar.getMenu(0);
        fireMenuSelected(fileMenuTrue);
        JMenuItem closeRightTrue =
                findItem(fileMenuTrue, Messages.get("menubar.file.closeRight"));
        assertNotNull("Close Tabs to the Right 項目があるはず", closeRightTrue);
        assertTrue("右側にタブがあるとき Close Tabs to the Right は有効のはず",
                closeRightTrue.isEnabled());

        MenuBarBuilder.Result rFalse = buildWithTabSuppliers(2, 0, true, false);
        JMenu fileMenuFalse = rFalse.menuBar.getMenu(0);
        fireMenuSelected(fileMenuFalse);
        JMenuItem closeRightFalse =
                findItem(fileMenuFalse, Messages.get("menubar.file.closeRight"));
        assertNotNull("Close Tabs to the Right 項目があるはず", closeRightFalse);
        assertFalse("右側にタブがないとき Close Tabs to the Right は無効のはず",
                closeRightFalse.isEnabled());
    }

    @Test
    public void tabEnablement_noClosedHistory_reopenDisabled() {
        MenuBarBuilder.Result r = buildWithTabSuppliers(1, 0, true, false);
        JMenu fileMenu = r.menuBar.getMenu(0);
        fireMenuSelected(fileMenu);

        JMenuItem reopen = findItem(fileMenu, Messages.get("menubar.file.reopenTab"));
        assertNotNull("File メニューに Reopen Closed Tab 項目があるはず", reopen);
        assertFalse("閉じタブ履歴 0 件のとき Reopen Closed Tab は無効のはず", reopen.isEnabled());
    }

    @Test
    public void tabEnablement_hasClosedHistory_reopenEnabled() {
        MenuBarBuilder.Result r = buildWithTabSuppliers(0, 1, true, false);
        JMenu fileMenu = r.menuBar.getMenu(0);
        fireMenuSelected(fileMenu);

        JMenuItem reopen = findItem(fileMenu, Messages.get("menubar.file.reopenTab"));
        assertNotNull("File メニューに Reopen Closed Tab 項目があるはず", reopen);
        assertTrue("閉じタブ履歴 1 件のとき Reopen Closed Tab は有効のはず", reopen.isEnabled());
    }

    @Test
    public void tabEnablement_nullSuppliers_itemsKeepDefaultEnabledState() {
        // supplier が null のとき、enablement ロジックはスキップされてデフォルト(有効)のまま。
        MenuBarBuilder.Callbacks cb = new MenuBarBuilder.Callbacks();
        cb.chooseProject = () -> {};
        cb.openArchive = () -> {};
        cb.chooseAndExport = () -> {};
        cb.exportClassDiagramsPerFolder = () -> {};
        cb.exportFunctionList = () -> {};
        cb.exportMemberList = () -> {};
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
        cb.closeActiveTab = () -> {};
        cb.reopenClosedTab = () -> {};
        cb.closeOtherTabs = () -> {};
        cb.closeTabsToRight = () -> {};
        cb.closeAllTabs = () -> {};
        // dynamicTabCount / closedTabHistorySize は null のまま (後方互換)
        MenuBarBuilder.Result r = new MenuBarBuilder(DiagramKind.CLASS, 0, cb, null).build();
        JMenu fileMenu = r.menuBar.getMenu(0);
        fireMenuSelected(fileMenu); // enablement ロジックはスキップされる

        JMenuItem closeAll = findItem(fileMenu, Messages.get("menubar.file.closeAllTabs"));
        JMenuItem reopen = findItem(fileMenu, Messages.get("menubar.file.reopenTab"));
        assertNotNull("Close All Tabs 項目があるはず", closeAll);
        assertNotNull("Reopen Closed Tab 項目があるはず", reopen);
        // supplier が null のときは enablement が変更されない (デフォルト enabled=true)
        assertTrue("supplier null では Close All Tabs は常時有効のはず", closeAll.isEnabled());
        assertTrue("supplier null では Reopen Closed Tab は常時有効のはず", reopen.isEnabled());
    }

    @Test
    public void tabEnablement_closeOtherTabs_dynamicFocused_requires2Tabs() {
        // 動的タブ選択中: 「他のタブ」は 1 枚以上のとき有効 (= 2 枚以上必要)
        MenuBarBuilder.Result r1 = buildWithTabSuppliers(1, 0, true, false);
        JMenu fileMenu1 = r1.menuBar.getMenu(0);
        fireMenuSelected(fileMenu1);
        JMenuItem closeOthers1 = findItem(fileMenu1, Messages.get("menubar.file.closeOthers"));
        assertNotNull("Close Other Tabs 項目があるはず", closeOthers1);
        assertFalse("動的タブ選択中で 1 枚のとき Close Other Tabs は無効のはず",
                closeOthers1.isEnabled());

        MenuBarBuilder.Result r2 = buildWithTabSuppliers(2, 0, true, false);
        JMenu fileMenu2 = r2.menuBar.getMenu(0);
        fireMenuSelected(fileMenu2);
        JMenuItem closeOthers2 = findItem(fileMenu2, Messages.get("menubar.file.closeOthers"));
        assertNotNull("Close Other Tabs 項目があるはず", closeOthers2);
        assertTrue("動的タブ選択中で 2 枚のとき Close Other Tabs は有効のはず",
                closeOthers2.isEnabled());
    }

    @Test
    public void tabEnablement_closeOtherTabs_utilityFocused_requires1Tab() {
        // ユーティリティタブ選択中: 動的タブが 1 枚以上あれば全部「他のタブ」になる
        MenuBarBuilder.Result r = buildWithTabSuppliers(1, 0, false, false);
        JMenu fileMenu = r.menuBar.getMenu(0);
        fireMenuSelected(fileMenu);
        JMenuItem closeOthers = findItem(fileMenu, Messages.get("menubar.file.closeOthers"));
        assertNotNull("Close Other Tabs 項目があるはず", closeOthers);
        assertTrue("ユーティリティタブ選択中で動的 1 枚のとき Close Other Tabs は有効のはず",
                closeOthers.isEnabled());
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
