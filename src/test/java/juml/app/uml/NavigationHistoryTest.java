// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NavigationHistoryTest {

    @Test
    public void back_returnsNull_whenEmpty() {
        NavigationHistory h = new NavigationHistory();
        assertFalse(h.canGoBack());
        assertNull(h.back());
    }

    @Test
    public void back_returnsPrevious_afterTwoPushes() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("B");
        assertTrue(h.canGoBack());
        assertEquals("A", h.back());
    }

    @Test
    public void forward_returnsNext_afterBack() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("B");
        h.back();
        assertTrue(h.canGoForward());
        assertEquals("B", h.forward());
    }

    @Test
    public void push_clearsForwardHistory() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("B");
        h.push("C");
        h.back(); // → B
        h.back(); // → A
        h.push("D");
        assertFalse(h.canGoForward());
        assertEquals("A", h.back());
    }

    @Test
    public void push_ignoresDuplicate_atCursor() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("A");
        assertFalse(h.canGoBack());
    }

    @Test
    public void navigating_suppressesPush() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("B");
        h.setNavigating(true);
        h.push("C");
        h.setNavigating(false);
        // C should not have been pushed; back still goes to A
        assertEquals("A", h.back());
    }

    @Test
    public void remove_removesEntry_andAdjustsCursor() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("B");
        h.push("C");
        h.remove("B");
        // history is now [A, C], cursor at C (index 1)
        assertEquals("A", h.back());
    }

    @Test
    public void remove_adjustsCursor_whenRemovingBeforeCursor() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("B");
        h.push("C");
        h.push("D");
        // cursor at D (index 3)
        h.remove("A");
        // history is [B, C, D], cursor should be at D (index 2)
        assertEquals("C", h.back());
        assertEquals("B", h.back());
    }

    @Test
    public void clear_resetsEverything() {
        NavigationHistory h = new NavigationHistory();
        h.push("A");
        h.push("B");
        h.clear();
        assertFalse(h.canGoBack());
        assertFalse(h.canGoForward());
        assertNull(h.back());
    }

    @Test
    public void maxSize_evictsOldest() {
        NavigationHistory h = new NavigationHistory();
        for (int i = 0; i < 60; i++) {
            h.push("tab" + i);
        }
        // Should not exceed 50 entries — first 10 evicted
        // Going back 49 times should work (50 entries, starting at last)
        int count = 0;
        while (h.canGoBack()) {
            h.back();
            count++;
        }
        assertEquals(49, count);
    }
}
