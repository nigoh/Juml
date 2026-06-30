// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link LruTabPolicy} の退避対象選定テスト。
 */
public class LruTabPolicyTest {

    @Test
    public void noEvictionWhenWithinLimit() {
        assertNull(LruTabPolicy.victim(Arrays.asList("a", "b", "c"), "c", 3, 5));
    }

    @Test
    public void noEvictionWhenLimitDisabled() {
        assertNull(LruTabPolicy.victim(Arrays.asList("a", "b", "c"), "c", 3, 0));
        assertNull(LruTabPolicy.victim(Arrays.asList("a", "b", "c"), "c", 3, -1));
    }

    @Test
    public void evictsOldestWhenOverLimit() {
        // 使用順 a(最古) < b < c。上限 2 を超過 → 最古の a を閉じる。
        assertEquals("a", LruTabPolicy.victim(Arrays.asList("a", "b", "c"), "c", 3, 2));
    }

    @Test
    public void neverEvictsActiveTab() {
        // 最古が active のときは次に古いものを選ぶ (アクティブタブは閉じない)。
        assertEquals("b", LruTabPolicy.victim(Arrays.asList("a", "b", "c"), "a", 3, 2));
    }

    @Test
    public void returnsNullWhenOnlyActiveRemains() {
        assertNull(LruTabPolicy.victim(Collections.singletonList("a"), "a", 1, 0));
        // 上限超過でも active しか候補が無ければ退避しない
        assertNull(LruTabPolicy.victim(Collections.singletonList("a"), "a", 5, 1));
    }

    @Test
    public void keysToReleaseReturnsOldestBeyondKeepWindow() {
        // 最新 2 件 (d,e) は描画保持、それ以前の a,b,c は解放対象。
        assertEquals(Arrays.asList("a", "b", "c"),
                LruTabPolicy.keysToRelease(Arrays.asList("a", "b", "c", "d", "e"), 2));
    }

    @Test
    public void keysToReleaseEmptyWhenWithinKeep() {
        assertTrue(LruTabPolicy.keysToRelease(Arrays.asList("a", "b"), 2).isEmpty());
        assertTrue(LruTabPolicy.keysToRelease(Arrays.asList("a", "b", "c"), 5).isEmpty());
    }

    @Test
    public void keysToReleaseEmptyWhenDisabled() {
        assertTrue(LruTabPolicy.keysToRelease(Arrays.asList("a", "b", "c"), 0).isEmpty());
        assertTrue(LruTabPolicy.keysToRelease(Arrays.asList("a", "b", "c"), -1).isEmpty());
    }
}
