// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramRequest#withScope} / {@link DiagramRequest#withSequenceHiddenParticipants} の
 * 純関数テスト (#40)。スコープ・参加者フィルタをタブ固有状態として「アクティブタブの spec を
 * 起点に更新する」仕組みの土台。他の次元 (kind / entry / legend / links / layoutKey) を保持し、
 * 元インスタンスを変更しない (不変) ことを検証する。
 */
public class DiagramRequestWithersTest {

    private static Set<String> setOf(String... s) {
        return new LinkedHashSet<>(java.util.Arrays.asList(s));
    }

    @Test
    public void withScope_replacesScopeAndPreservesOtherDimensions() {
        Set<String> hidden = setOf("Actor");
        DiagramRequest base = new DiagramRequest(DiagramKind.SEQUENCE, "Foo", "bar", true,
                null, true, null, hidden);
        DiagramScope scope = DiagramScope.builder().build();

        DiagramRequest scoped = base.withScope(scope);

        assertNotSame("不変: 新インスタンスを返す", base, scoped);
        assertNull("元の scope は変わらない", base.getScope());
        assertEquals("scope が差し替わる", scope, scoped.getScope());
        assertEquals(DiagramKind.SEQUENCE, scoped.getKind());
        assertEquals("Foo", scoped.getSequenceEntryClass());
        assertEquals("bar", scoped.getSequenceEntryMethod());
        assertTrue(scoped.isInteractiveLinks());
        assertEquals("隠し participant は保持される", setOf("Actor"),
                scoped.getSequenceHiddenParticipants());
    }

    @Test
    public void withSequenceHiddenParticipants_replacesParticipantsAndPreservesScope() {
        DiagramScope scope = DiagramScope.builder().build();
        DiagramRequest base = new DiagramRequest(DiagramKind.SEQUENCE, "Foo", "bar", true,
                scope, false, null, setOf("Old"));

        DiagramRequest updated = base.withSequenceHiddenParticipants(setOf("A", "B"));

        assertEquals(setOf("Old"), base.getSequenceHiddenParticipants());
        assertEquals(setOf("A", "B"), updated.getSequenceHiddenParticipants());
        assertEquals("scope は保持される", scope, updated.getScope());
        assertEquals(DiagramKind.SEQUENCE, updated.getKind());
    }

    @Test
    public void withScope_null_clearsScope() {
        DiagramRequest base = new DiagramRequest(DiagramKind.CLASS, null, null, true,
                DiagramScope.builder().build());
        assertNull(base.withScope(null).getScope());
    }

    @Test
    public void withSequenceHiddenParticipants_null_clearsToEmpty() {
        DiagramRequest base = new DiagramRequest(DiagramKind.SEQUENCE, "F", "m", true,
                null, false, null, setOf("X"));
        assertTrue(base.withSequenceHiddenParticipants(null)
                .getSequenceHiddenParticipants().isEmpty());
    }

    @Test
    public void withScope_preservesLayoutKey() {
        DiagramRequest base = DiagramRequest.forLayout("mod::main::::a.xml", true);
        DiagramRequest scoped = base.withScope(null);
        assertEquals("mod::main::::a.xml", scoped.getLayoutKey());
        assertEquals(DiagramKind.LAYOUT, scoped.getKind());
    }
}
