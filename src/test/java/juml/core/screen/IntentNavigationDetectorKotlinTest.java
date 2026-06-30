// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link IntentNavigationDetector} の Kotlin 構文対応テスト。
 *
 * <p>{@code Intent(this, X::class.java)} や {@code intent.setClass(this, X::class.java)} のような
 * Kotlin 特有の構文も検出できるかを確認する。</p>
 */
public class IntentNavigationDetectorKotlinTest {

    @Test
    public void detectsKotlinNewIntent() {
        String src = "package com.x\n"
                + "class MainActivity {\n"
                + "    fun onClick() {\n"
                + "        startActivity(Intent(this, DetailActivity::class.java))\n"
                + "    }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.kt");
        assertEquals(1, hits.size());
        ScreenTransition t = hits.get(0);
        assertEquals("com.x.MainActivity", t.getFromFqn());
        assertEquals("DetailActivity", t.getTargetSimpleName());
        assertEquals(ScreenTransition.Kind.START_ACTIVITY, t.getKind());
        assertEquals("onClick", t.getFromMethod());
    }

    @Test
    public void detectsKotlinSetClass() {
        String src = "package com.x\n"
                + "class A {\n"
                + "    fun go() {\n"
                + "        val i = Intent()\n"
                + "        i.setClass(this, B::class.java)\n"
                + "        startActivity(i)\n"
                + "    }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.kt");
        boolean foundSetClass = false;
        for (ScreenTransition t : hits) {
            if (t.getKind() == ScreenTransition.Kind.SET_CLASS
                    && "B".equals(t.getTargetSimpleName())) {
                foundSetClass = true;
            }
        }
        assertTrue("Must detect Kotlin setClass transition", foundSetClass);
    }

    @Test
    public void promotesKotlinIntentToStartForResult() {
        String src = "package com.x\n"
                + "class A {\n"
                + "    fun pick() {\n"
                + "        val intent = Intent(this, PickerActivity::class.java)\n"
                + "        startActivityForResult(intent, 42)\n"
                + "    }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.kt");
        assertEquals(1, hits.size());
        assertEquals(ScreenTransition.Kind.START_FOR_RESULT, hits.get(0).getKind());
    }

    @Test
    public void readsKotlinObjectAsCallerFqn() {
        String src = "package com.x\n"
                + "object Router {\n"
                + "    fun openDetail(ctx: Context) {\n"
                + "        ctx.startActivity(Intent(ctx, DetailActivity::class.java))\n"
                + "    }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "Router.kt");
        assertEquals(1, hits.size());
        assertEquals("com.x.Router", hits.get(0).getFromFqn());
        assertEquals("openDetail", hits.get(0).getFromMethod());
    }

    @Test
    public void doesNotMisinterpretJavaIntentPattern() {
        // Java の new Intent(...) は別パターンで処理されるので Kotlin パターンに引っかからない
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void run() { startActivity(new Intent(this, B.class)); }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        // Java pattern が 1 件捕まえるだけ
        assertEquals(1, hits.size());
        assertEquals("B", hits.get(0).getTargetSimpleName());
    }
}
