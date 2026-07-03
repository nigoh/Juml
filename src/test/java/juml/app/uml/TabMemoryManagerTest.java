// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link TabMemoryManager} の協調ロジック (MRU 更新・上限超過タブのクローズ・古いタブの
 * 描画解放) のテスト。
 *
 * <p>{@link LruTabPolicy} は純関数として {@code LruTabPolicyTest} が守っているが、それを
 * 束ねる {@code onActivate} の orchestration —— MRU の更新順、victim ループ、
 * 「アクティブタブは閉じない/解放しない」ガード、{@code onClose} による除外 —— は無防備だった。
 * Swing を起動せず、{@link TabMemoryManager.Actions} をフェイクに差し替えて検証する
 * (テスト用コンストラクタ {@code TabMemoryManager(int,int)} が用意されている)。</p>
 */
public class TabMemoryManagerTest {

    /** Actions の呼び出しキーを順に記録するフェイク。 */
    private static final class RecordingActions implements TabMemoryManager.Actions {
        final List<String> closed = new ArrayList<>();
        final List<String> released = new ArrayList<>();
        final List<String> rendered = new ArrayList<>();
        /** このキーへの closeTab は拒否する (未保存編集の保護を模す)。 */
        final java.util.Set<String> refuseClose = new java.util.HashSet<>();

        @Override
        public boolean closeTab(String key) {
            if (refuseClose.contains(key)) {
                return false;
            }
            closed.add(key);
            return true;
        }

        @Override
        public void releaseRender(String key) {
            released.add(key);
        }

        @Override
        public void ensureRendered(String key) {
            rendered.add(key);
        }
    }

    /** activeKey が null なら何もしない (no-op)。 */
    @Test
    public void nullActiveKeyIsNoOp() {
        TabMemoryManager mgr = new TabMemoryManager(2, 1);
        RecordingActions act = new RecordingActions();
        mgr.onActivate(null, 5, act);
        assertTrue("close は呼ばれない", act.closed.isEmpty());
        assertTrue("release は呼ばれない", act.released.isEmpty());
        assertTrue("render も呼ばれない", act.rendered.isEmpty());
    }

    /** アクティブ化のたびに、そのタブの再描画が保証される。 */
    @Test
    public void activeTabIsAlwaysEnsuredRendered() {
        TabMemoryManager mgr = new TabMemoryManager(20, 4);
        RecordingActions act = new RecordingActions();
        mgr.onActivate("a", 1, act);
        assertEquals(List.of("a"), act.rendered);
        assertTrue(act.closed.isEmpty());
        assertTrue(act.released.isEmpty());
    }

    /** 上限超過で最古の非アクティブタブが閉じられ、アクティブタブは閉じられない。 */
    @Test
    public void evictsOldestNonActiveWhenOverLimit() {
        TabMemoryManager mgr = new TabMemoryManager(2, 10); // keepRendered 大 → release ノイズ無し
        RecordingActions act = new RecordingActions();
        mgr.onActivate("a", 1, act);
        mgr.onActivate("b", 2, act);
        mgr.onActivate("c", 3, act); // 3 > 2 → 最古 a を退避

        assertEquals("最古の非アクティブ a が閉じられる", List.of("a"), act.closed);
        assertFalse("アクティブ c は閉じられない", act.closed.contains("c"));
    }

    /** アクティブタブが最古でも閉じられず、次に古いタブが退避される。 */
    @Test
    public void activeTabIsNeverEvictedEvenIfOldest() {
        TabMemoryManager mgr = new TabMemoryManager(2, 10);
        RecordingActions act = new RecordingActions();
        mgr.onActivate("a", 1, act);
        mgr.onActivate("b", 2, act);
        mgr.onActivate("a", 3, act); // a を再アクティブ化 (最古だがアクティブ) → b が退避

        assertEquals("アクティブ a でなく b が閉じられる", List.of("b"), act.closed);
    }

    /** keepRendered を超えた古いタブの描画だけが解放され、アクティブは対象外。 */
    @Test
    public void releasesOldRenderingsBeyondKeepWindowButNotActive() {
        TabMemoryManager mgr = new TabMemoryManager(0, 2); // max 0 = 退避無効、描画解放のみ
        RecordingActions act = new RecordingActions();
        mgr.onActivate("a", 1, act);
        mgr.onActivate("b", 2, act);
        mgr.onActivate("c", 3, act); // mru=[a,b,c], keepRendered2 → a を解放
        mgr.onActivate("d", 4, act); // mru=[a,b,c,d] → a,b を解放

        assertTrue("最新を超えた古いタブ a の描画が解放される", act.released.contains("a"));
        assertTrue("b も解放対象になる", act.released.contains("b"));
        assertFalse("アクティブ c は解放されない", act.released.contains("c"));
        assertFalse("アクティブ d は解放されない", act.released.contains("d"));
        assertTrue("上限無効なのでタブは閉じられない", act.closed.isEmpty());
    }

    /**
     * closeTab が拒否されたタブ (未保存編集の保護) は帳簿から消さず、
     * 代わりに次の犠牲を探す。拒否をクローズ扱いにすると帳簿が実態とずれ、
     * 後続のアクティブ化で別の新しいタブが身代わりに退避されてしまう。
     */
    @Test
    public void refusedVictimStaysTrackedAndNextOldestIsEvicted() {
        TabMemoryManager mgr = new TabMemoryManager(2, 10);
        RecordingActions act = new RecordingActions();
        act.refuseClose.add("a"); // 最古 a は dirty エディタ想定で閉じられない
        mgr.onActivate("a", 1, act);
        mgr.onActivate("b", 2, act);
        mgr.onActivate("c", 3, act); // 3 > 2 → a は拒否され、次点 b が退避

        assertEquals("拒否された a を飛ばして b が閉じられる", List.of("b"), act.closed);

        // a は帳簿に残り続けるので、後で dirty が解消されれば通常どおり退避対象になる。
        act.refuseClose.clear();
        mgr.onActivate("d", 3, act); // 開いているのは a,c,d の 3 枚 → 最古 a を退避
        assertEquals(List.of("b", "a"), act.closed);
    }

    /**
     * rename (Save As / 図種のその場切替) 後は新キーで管理が続き、旧キーの幽霊が
     * 退避枠を浪費しない。
     */
    @Test
    public void renameKeepsTabTrackedUnderNewKey() {
        TabMemoryManager mgr = new TabMemoryManager(2, 10);
        RecordingActions act = new RecordingActions();
        mgr.onActivate("old", 1, act);
        mgr.onActivate("b", 2, act);
        mgr.rename("old", "new"); // mru=[b, new]
        mgr.onActivate("c", 3, act); // 3 > 2 → 最古 b が退避 (幽霊 "old" ではなく)

        assertEquals("幽霊キーではなく実在する最古 b が閉じられる", List.of("b"), act.closed);
    }

    /** onClose で閉じたタブは MRU から外れ、以後の退避判定に含まれない。 */
    @Test
    public void onCloseRemovesTabFromMru() {
        TabMemoryManager mgr = new TabMemoryManager(2, 10);
        RecordingActions act = new RecordingActions();
        mgr.onActivate("a", 1, act);
        mgr.onActivate("b", 2, act);
        mgr.onClose("a"); // a をユーザーが閉じた → mru=[b]
        mgr.onActivate("c", 2, act); // 開いているのは b,c の 2 枚 → 上限内、退避なし

        assertTrue("onClose 済みの a を二重に閉じない / 退避も起きない", act.closed.isEmpty());
    }
}
