// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.actions;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * MarkdownActionReport のユニットテスト。
 */
public class MarkdownActionReportTest {

    @Test
    public void emptyListShowsNoHandlerMessage() {
        String md = MarkdownActionReport.render(new ArrayList<>());
        assertTrue(md.contains("no UI action handlers detected"));
    }

    @Test
    public void nullListShowsNoHandlerMessage() {
        String md = MarkdownActionReport.render(null);
        assertTrue(md.contains("no UI action handlers detected"));
    }

    @Test
    public void reportContainsClickHandlerSection() {
        List<UiActionEntry> entries = new ArrayList<>();
        entries.add(new UiActionEntry("btn_login", UiActionEntry.ActionType.ON_CLICK,
                "MainActivity#onLoginClick", "MainActivity.java", 55));
        String md = MarkdownActionReport.render(entries);
        assertTrue(md.contains("Click Handlers"));
        assertTrue(md.contains("btn_login"));
        assertTrue(md.contains("MainActivity.java"));
        assertTrue(md.contains("55"));
    }

    @Test
    public void reportContainsOtherHandlersSection() {
        List<UiActionEntry> entries = new ArrayList<>();
        entries.add(new UiActionEntry("(menu)", UiActionEntry.ActionType.MENU_ITEM,
                "EditFragment#onOptionsItemSelected", "EditFragment.java", 112));
        String md = MarkdownActionReport.render(entries);
        assertTrue(md.contains("Other Handlers"));
        assertTrue(md.contains("EditFragment.java"));
    }

    @Test
    public void xmlOnClickAppearsInClickSection() {
        List<UiActionEntry> entries = new ArrayList<>();
        entries.add(new UiActionEntry("btn_save", UiActionEntry.ActionType.XML_ON_CLICK,
                "onSaveClicked", "activity_main.xml", -1));
        String md = MarkdownActionReport.render(entries);
        assertTrue(md.contains("Click Handlers"));
        assertTrue(md.contains("onSaveClicked"));
        assertFalse(md.contains("Other Handlers"));
    }

    @Test
    public void summaryCountIsCorrect() {
        List<UiActionEntry> entries = new ArrayList<>();
        entries.add(new UiActionEntry("v1", UiActionEntry.ActionType.ON_CLICK,
                "A#onClick", "A.java", 1));
        entries.add(new UiActionEntry("v2", UiActionEntry.ActionType.ON_CLICK,
                "B#onClick", "B.java", 2));
        String md = MarkdownActionReport.render(entries);
        assertTrue(md.contains("Total handlers: 2"));
    }
}
