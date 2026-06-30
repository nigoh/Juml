// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.util.ErrorListener;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin メソッド本体内の呼び出し検出のテスト。
 */
public class KotlinLightScannerCallsTest {

    private static JavaMethodInfo findMethod(JavaClassInfo cls, String name) {
        for (JavaMethodInfo m : cls.getMethods()) {
            if (name.equals(m.getName())) return m;
        }
        return null;
    }

    @Test
    public void detectsBasicCall() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun run() {\n"
                + "    val b = B()\n"
                + "    b.doIt()\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo run = findMethod(infos.get(0), "run");
        List<JavaMethodInfo.Call> calls = run.getCalls();
        // B(), b.doIt() の 2 つ
        assertEquals(2, calls.size());
        boolean foundDoIt = false;
        for (JavaMethodInfo.Call c : calls) {
            if ("doIt".equals(c.getMethodName()) && "b".equals(c.getReceiver())) {
                foundDoIt = true;
            }
        }
        assertTrue("b.doIt() must be detected", foundDoIt);
    }

    @Test
    public void detectsSafeCallReceiver() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun run() {\n"
                + "    user?.name()\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo run = findMethod(infos.get(0), "run");
        List<JavaMethodInfo.Call> calls = run.getCalls();
        assertEquals(1, calls.size());
        JavaMethodInfo.Call c = calls.get(0);
        assertEquals("name", c.getMethodName());
        // ?. の ? は除去されて receiver = "user"
        assertEquals("user", c.getReceiver());
    }

    @Test
    public void detectsNonNullAssertionReceiver() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun run() { listener!!.onChange() }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo run = findMethod(infos.get(0), "run");
        List<JavaMethodInfo.Call> calls = run.getCalls();
        assertEquals(1, calls.size());
        JavaMethodInfo.Call c = calls.get(0);
        assertEquals("onChange", c.getMethodName());
        assertEquals("listener", c.getReceiver());
    }

    @Test
    public void detectsImplicitThisCall() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun helper() {}\n"
                + "  fun run() { helper() }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo run = findMethod(infos.get(0), "run");
        List<JavaMethodInfo.Call> calls = run.getCalls();
        assertEquals(1, calls.size());
        JavaMethodInfo.Call c = calls.get(0);
        assertEquals("helper", c.getMethodName());
        assertEquals("", c.getReceiver());
    }

    @Test
    public void skipsControlKeywords() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun run(x: Int) {\n"
                + "    if (x > 0) {\n"
                + "      while (x < 100) { other.method() }\n"
                + "      when (x) { 1 -> first(); else -> second() }\n"
                + "      for (item in list) { item.process() }\n"
                + "    }\n"
                + "    return\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo run = findMethod(infos.get(0), "run");
        List<JavaMethodInfo.Call> calls = run.getCalls();
        // 期待される call: method (on other), first, second, process (on item)
        // if/while/when/for/return は除外
        assertTrue("Should detect 4 calls but got " + calls.size(), calls.size() >= 4);
        boolean foundOther = false, foundProcess = false;
        for (JavaMethodInfo.Call c : calls) {
            if ("method".equals(c.getMethodName())
                    && "other".equals(c.getReceiver())) foundOther = true;
            if ("process".equals(c.getMethodName())
                    && "item".equals(c.getReceiver())) foundProcess = true;
        }
        assertTrue("other.method() must be present", foundOther);
        assertTrue("item.process() must be present", foundProcess);
    }

    @Test
    public void ignoresStringLiteralsAndComments() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun run() {\n"
                + "    val s = \"obj.fake()\"\n"
                + "    // bar.skipped()\n"
                + "    /* block.also() */\n"
                + "    real.call()\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo run = findMethod(infos.get(0), "run");
        List<JavaMethodInfo.Call> calls = run.getCalls();
        // 期待: real.call() のみ (文字列とコメントの中身は無視)
        assertEquals(1, calls.size());
        assertEquals("call", calls.get(0).getMethodName());
        assertEquals("real", calls.get(0).getReceiver());
    }

    @Test
    public void expressionBodyFunctionsSkipped() {
        // PR で式本体対応した。本テストは式本体内呼び出しが拾えることを確認。
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun greet() = \"hello\".uppercase()\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo greet = findMethod(infos.get(0), "greet");
        org.junit.Assert.assertNotNull(greet);
        // 式本体内の呼び出しが検出される (リテラル "hello" 受け取りの uppercase)
        boolean found = false;
        for (JavaMethodInfo.Call c : greet.getCalls()) {
            if ("uppercase".equals(c.getMethodName())) found = true;
        }
        assertTrue("uppercase call from expression body must be detected", found);
    }

    @Test
    public void expressionBodyWithFieldReceiver() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  private val name: String = \"x\"\n"
                + "  fun shout() = name.uppercase()\n"
                + "  fun first() = name.first()\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo shout = findMethod(infos.get(0), "shout");
        JavaMethodInfo first = findMethod(infos.get(0), "first");
        // shout の本体に name.uppercase() が 1 件、first にも 1 件出ること
        assertEquals(1, shout.getCalls().size());
        assertEquals("uppercase", shout.getCalls().get(0).getMethodName());
        assertEquals("name", shout.getCalls().get(0).getReceiver());
        assertEquals(1, first.getCalls().size());
        assertEquals("first", first.getCalls().get(0).getMethodName());
        assertEquals("name", first.getCalls().get(0).getReceiver());
    }

    @Test
    public void expressionBodyDoesNotLeakIntoNextFunction() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  private val dao: Dao = TODO()\n"
                + "  fun load() = dao.findAll()\n"
                + "  fun delete(u: User) = dao.delete(u)\n"
                + "  fun update(u: User) = dao.update(u)\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        // 各 fun が exactly 1 つの call を持つこと (隣の式本体に流れない)
        for (String name : new String[]{"load", "delete", "update"}) {
            JavaMethodInfo mth = findMethod(infos.get(0), name);
            assertEquals("fun " + name + " calls", 1, mth.getCalls().size());
        }
    }

    @Test
    public void expressionBodyWithSafeCall() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun nameOf(u: User?) = u?.name()\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo nameOf = findMethod(infos.get(0), "nameOf");
        assertEquals(1, nameOf.getCalls().size());
        assertEquals("name", nameOf.getCalls().get(0).getMethodName());
        // safe-call ?. の ? は receiver から剥がされる
        assertEquals("u", nameOf.getCalls().get(0).getReceiver());
    }

    @Test
    public void detectsClassStaticCall() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun run() { Foo.bar() }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaMethodInfo run = findMethod(infos.get(0), "run");
        List<JavaMethodInfo.Call> calls = run.getCalls();
        assertEquals(1, calls.size());
        assertEquals("bar", calls.get(0).getMethodName());
        assertEquals("Foo", calls.get(0).getReceiver());
    }
}
