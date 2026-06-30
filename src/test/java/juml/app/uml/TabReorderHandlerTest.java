// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * {@link TabReorderHandler#moveTab} の並び替えロジック検証 (ヘッドレスで動作)。
 */
public class TabReorderHandlerTest {

    private static JTabbedPane fivePane() {
        JTabbedPane tabs = new JTabbedPane();
        for (String name : new String[] {"A", "B", "C", "X", "Y"}) {
            JPanel content = new JPanel();
            content.setName(name);
            tabs.addTab(name, content);
            tabs.setTabComponentAt(tabs.getTabCount() - 1, new JLabel("hdr-" + name));
        }
        return tabs;
    }

    private static String order(JTabbedPane tabs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            sb.append(tabs.getComponentAt(i).getName());
        }
        return sb.toString();
    }

    @Test
    public void movesTabForwardPreservingHeaderAndSelection() {
        JTabbedPane tabs = fivePane();
        tabs.setSelectedIndex(0); // "A" selected
        TabReorderHandler.moveTab(tabs, 0, 2);
        assertEquals("BCAXY", order(tabs));
        // 移動後も選択は動いたタブ (A) を追従する
        assertEquals("A", tabs.getSelectedComponent().getName());
        // ヘッダ (tabComponent) も一緒に移動している
        assertEquals("hdr-A", ((JLabel) tabs.getTabComponentAt(2)).getText());
    }

    @Test
    public void movesTabBackwardKeepsContentObjects() {
        JTabbedPane tabs = fivePane();
        java.awt.Component cBefore = tabs.getComponentAt(2); // "C"
        TabReorderHandler.moveTab(tabs, 2, 0);
        assertEquals("CABXY", order(tabs));
        assertSame("content object should be preserved, not recreated",
                cBefore, tabs.getComponentAt(0));
    }
}
