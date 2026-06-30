// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.*;

public class DiagramStateTest {

    @Test
    public void initialState_allEntriesAreNull() {
        DiagramState state = new DiagramState();
        assertNull(state.sequenceEntry);
        assertNull(state.activityEntry);
        assertNull(state.callGraphEntry);
        assertNull(state.currentLayoutKey);
        assertNull(state.currentNavigationKey);
        assertNull(state.currentScope);
        assertNull(state.currentPuml);
        assertNull(state.currentSvgXml);
        assertTrue(state.sequenceHiddenParticipants.isEmpty());
    }

    @Test
    public void setAllMethodEntries_synchronizesThreeEntries() {
        DiagramState state = new DiagramState();
        state.setAllMethodEntries("Foo.bar");
        assertEquals("Foo.bar", state.sequenceEntry);
        assertEquals("Foo.bar", state.activityEntry);
        assertEquals("Foo.bar", state.callGraphEntry);
    }

    @Test
    public void setAllMethodEntries_withNull_clearsAllThreeEntries() {
        DiagramState state = new DiagramState();
        state.setAllMethodEntries("Foo.bar");
        state.setAllMethodEntries(null);
        assertNull(state.sequenceEntry);
        assertNull(state.activityEntry);
        assertNull(state.callGraphEntry);
    }
}
