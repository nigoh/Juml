// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link MarkdownScreenFlowReport} の整形テスト。遷移先は Activity / Fragment /
 * Dialog が混在するため、ノードを「Activity」と決めつけず中立に「Screen」と表記する
 * ことを検証する。
 */
public class MarkdownScreenFlowReportTest {

    @Test
    public void usesNeutralScreenWordingForMixedNodes() {
        List<ScreenTransition> transitions = Arrays.asList(
                new ScreenTransition("com.x.MainActivity", "open", "com.x.PrefActivity",
                        "MainActivity.java", 10, ScreenTransition.Kind.START_ACTIVITY),
                new ScreenTransition("com.x.MainActivity", "show", "com.x.DetailFragment",
                        "MainActivity.java", 20, ScreenTransition.Kind.FRAGMENT_TXN));
        String md = MarkdownScreenFlowReport.render(transitions);
        // Activity に限定しない中立表記
        assertTrue(md, md.contains("- Screens involved:"));
        assertTrue(md, md.contains("## Screens"));
        assertTrue(md, md.contains("| Screen | Outgoing | Incoming |"));
        // Fragment を「Activity」と呼ぶ旧表記が残っていないこと
        assertFalse(md, md.contains("Activities involved"));
        assertFalse(md, md.contains("## Activities"));
        // Fragment ノードもテーブルに含まれる
        assertTrue(md, md.contains("DetailFragment"));
    }
}
