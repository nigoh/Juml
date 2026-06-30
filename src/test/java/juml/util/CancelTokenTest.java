// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * CancelToken の基本動作テスト。
 */
public class CancelTokenTest {

    @Test
    public void testInitiallyNotCancelled() {
        CancelToken t = new CancelToken();
        assertFalse(t.isCancelled());
    }

    @Test
    public void testCancelFlipsState() {
        CancelToken t = new CancelToken();
        t.cancel();
        assertTrue(t.isCancelled());
    }

    @Test
    public void testCancelIsIdempotent() {
        CancelToken t = new CancelToken();
        t.cancel();
        t.cancel();
        assertTrue(t.isCancelled());
    }

    @Test(expected = InterruptedException.class)
    public void testThrowIfCancelledThrows() throws InterruptedException {
        CancelToken t = new CancelToken();
        t.cancel();
        t.throwIfCancelled();
    }

    @Test
    public void testThrowIfCancelledNoThrowWhenLive() throws InterruptedException {
        CancelToken t = new CancelToken();
        t.throwIfCancelled();
    }

    @Test
    public void testNoneIsConstantAndNotCancelled() {
        assertFalse(CancelToken.NONE.isCancelled());
    }
}
