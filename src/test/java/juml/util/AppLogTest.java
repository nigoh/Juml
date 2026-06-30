// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AppLog のユニットテスト。
 *
 * <p>{@link AppLog#init()} は {@code System.err} のティーや未捕捉例外ハンドラなど
 * グローバルな副作用を伴うため、本テストでは呼ばない。記録・リングバッファ・
 * リスナー・レベルしきい値といった純粋なロジックのみを検証する。</p>
 */
public class AppLogTest {

    @Before
    public void reset() {
        AppLog.setMinLevel(AppLog.Level.DEBUG);
        AppLog.clearBuffer();
    }

    @Test
    public void testRecordsAppearInSnapshot() {
        AppLog.info("AppLogTest", "hello");
        AppLog.warn("AppLogTest", "careful");
        List<AppLog.Entry> snap = AppLog.snapshot();
        assertEquals(2, snap.size());
        assertEquals(AppLog.Level.INFO, snap.get(0).getLevel());
        assertTrue(snap.get(0).getMessage().contains("hello"));
        assertEquals(AppLog.Level.WARN, snap.get(1).getLevel());
    }

    @Test
    public void testSourcePrefixedIntoMessage() {
        AppLog.error("MyComp", "boom");
        AppLog.Entry e = AppLog.snapshot().get(0);
        assertEquals("MyComp: boom", e.getMessage());
    }

    @Test
    public void testNullSourceKeepsMessageOnly() {
        AppLog.info(null, "plain");
        assertEquals("plain", AppLog.snapshot().get(0).getMessage());
    }

    @Test
    public void testThrowableProducesDetail() {
        AppLog.error("X", "failed", new IllegalStateException("nope"));
        AppLog.Entry e = AppLog.snapshot().get(0);
        assertNotNull(e.getDetail());
        assertTrue(e.getDetail().contains("IllegalStateException"));
        assertTrue(e.getDetail().contains("nope"));
    }

    @Test
    public void testNoThrowableHasNullDetail() {
        AppLog.info("X", "ok");
        assertNull(AppLog.snapshot().get(0).getDetail());
    }

    @Test
    public void testMinLevelFiltersBelowThreshold() {
        AppLog.setMinLevel(AppLog.Level.WARN);
        AppLog.debug("X", "d");
        AppLog.info("X", "i");
        AppLog.warn("X", "w");
        AppLog.error("X", "e");
        List<AppLog.Entry> snap = AppLog.snapshot();
        assertEquals(2, snap.size());
        assertEquals(AppLog.Level.WARN, snap.get(0).getLevel());
        assertEquals(AppLog.Level.ERROR, snap.get(1).getLevel());
    }

    @Test
    public void testListenerReceivesLiveEntries() {
        List<AppLog.Entry> seen = new ArrayList<>();
        AppLog.Listener l = seen::add;
        AppLog.addListener(l);
        try {
            AppLog.info("X", "one");
            AppLog.warn("X", "two");
        } finally {
            AppLog.removeListener(l);
        }
        assertEquals(2, seen.size());
        AppLog.info("X", "after-remove");
        assertEquals("listener should not receive after removal", 2, seen.size());
    }

    @Test
    public void testClearBufferEmptiesSnapshot() {
        AppLog.info("X", "a");
        AppLog.clearBuffer();
        assertTrue(AppLog.snapshot().isEmpty());
    }

    @Test
    public void testSeqIsStrictlyIncreasing() {
        AppLog.info("X", "a");
        AppLog.info("X", "b");
        AppLog.info("X", "c");
        List<AppLog.Entry> snap = AppLog.snapshot();
        assertTrue(snap.get(0).getSeq() < snap.get(1).getSeq());
        assertTrue(snap.get(1).getSeq() < snap.get(2).getSeq());
    }

    @Test
    public void testFormatLineContainsLevelThreadAndMessage() {
        AppLog.warn("Comp", "msg");
        String line = AppLog.snapshot().get(0).formatLine();
        assertTrue(line, line.contains("WARN"));
        assertTrue(line, line.contains("Comp: msg"));
    }
}
