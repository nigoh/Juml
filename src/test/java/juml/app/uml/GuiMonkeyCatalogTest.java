// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 探索ハーネス {@link GuiMonkey} のシナリオ・カタログ (説明) と実体 (ステップ) が食い違わないことを守る。
 * シナリオを片方にだけ追加すると {@code --list} と実行結果がずれるため、headless でも動く形で検査する。
 */
public class GuiMonkeyCatalogTest {

    @Test
    public void catalogAndStepsDescribeTheSameScenariosInTheSameOrder() {
        Map<String, String> catalog = GuiMonkey.catalog();
        Map<String, GuiMonkey.Step> steps = GuiMonkey.steps();
        assertEquals("--list に出る ID と実行される ID が一致すること (順序込み)",
                catalog.keySet().toString(), steps.keySet().toString());
        for (Map.Entry<String, String> e : catalog.entrySet()) {
            assertFalse("説明が空: " + e.getKey(), e.getValue().isBlank());
            assertTrue("ID は S<number> 形式: " + e.getKey(), e.getKey().matches("S\\d+"));
        }
    }

    @Test
    public void startupAndShutdownAreImplicitAndNotSelectable() {
        assertFalse(GuiMonkey.catalog().containsKey("S0"));
        assertFalse(GuiMonkey.catalog().containsKey("S12"));
        assertTrue("fuzz は S21 として選べる", GuiMonkey.catalog().containsKey("S21"));
    }
}
