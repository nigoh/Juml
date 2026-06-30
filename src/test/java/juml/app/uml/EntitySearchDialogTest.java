// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link EntitySearchDialog} のフィルタロジックを UI を構築せずに検証する。
 *
 * <p>{@link EntitySearchDialog#filter(List, String)} は内部で {@code collectEntries}
 * と {@code matches} の組み合わせを実行する。フィルタ条件・kind 分類・
 * inline メソッド印付けの判定がツリー UI に依存せず動くことを担保する。</p>
 */
public class EntitySearchDialogTest {

    private static final String SOURCE = ""
            + "package com.app;\n"
            + "class Foo {\n"
            + "  private OnClickListener listener = new OnClickListener() {\n"
            + "    public void onClick(View v) { doX(); }\n"
            + "  };\n"
            + "  private int counter;\n"
            + "  public void start() {}\n"
            + "  public void stop() {}\n"
            + "}\n"
            + "class Bar {\n"
            + "  Runnable task = () -> compute();\n"
            + "  public void run() {}\n"
            + "}\n";

    private static List<JavaClassInfo> sampleClasses() {
        return JavaStructureExtractor.extract(SOURCE);
    }

    @Test
    public void testCollectAllEntries() {
        List<EntitySearchDialog.Entry> all = EntitySearchDialog.filter(sampleClasses(), "");
        // CLASS=2 (Foo, Bar)
        // METHOD=3 (Foo.start, Foo.stop, Bar.run)
        // FIELD=3 (Foo.listener, Foo.counter, Bar.task)
        assertEquals(8, all.size());
    }

    @Test
    public void testFilterByClassName() {
        List<EntitySearchDialog.Entry> hits = EntitySearchDialog.filter(sampleClasses(), "foo");
        // Foo クラス本体 + Foo のメソッド 2 件 (start, stop) + フィールド 2 件 (listener, counter) = 5
        assertEquals(5, hits.size());
        // CLASS Foo は ownerQn に "Foo" を含むので入る
        boolean classHit = false;
        for (EntitySearchDialog.Entry e : hits) {
            if (e.kind == EntitySearchDialog.Kind.CLASS && "Foo".equals(e.simpleName)) {
                classHit = true;
            }
        }
        assertTrue("CLASS Foo should be present", classHit);
    }

    @Test
    public void testFilterByMethodName() {
        List<EntitySearchDialog.Entry> hits = EntitySearchDialog.filter(sampleClasses(), "start");
        // METHOD Foo.start のみ
        assertEquals(1, hits.size());
        assertEquals(EntitySearchDialog.Kind.METHOD, hits.get(0).kind);
        assertEquals("start", hits.get(0).simpleName);
    }

    @Test
    public void testFilterByFieldName() {
        List<EntitySearchDialog.Entry> hits = EntitySearchDialog.filter(sampleClasses(), "listener");
        // FIELD Foo.listener (type: OnClickListener も "listener" を含む)
        boolean fieldHit = false;
        for (EntitySearchDialog.Entry e : hits) {
            if (e.kind == EntitySearchDialog.Kind.FIELD && "listener".equals(e.simpleName)) {
                fieldHit = true;
                assertTrue("listener field should be marked inline",
                        e.hasInlineMethods);
            }
        }
        assertTrue("FIELD listener should be present", fieldHit);
    }

    @Test
    public void testInlineFieldDetection() {
        List<EntitySearchDialog.Entry> all = EntitySearchDialog.filter(sampleClasses(), "");
        EntitySearchDialog.Entry listener = null;
        EntitySearchDialog.Entry counter = null;
        EntitySearchDialog.Entry task = null;
        for (EntitySearchDialog.Entry e : all) {
            if (e.kind != EntitySearchDialog.Kind.FIELD) {
                continue;
            }
            if ("listener".equals(e.simpleName)) {
                listener = e;
            } else if ("counter".equals(e.simpleName)) {
                counter = e;
            } else if ("task".equals(e.simpleName)) {
                task = e;
            }
        }
        assertNotNull(listener);
        assertNotNull(counter);
        assertNotNull(task);
        assertTrue("listener (匿名クラス) should be inline", listener.hasInlineMethods);
        assertTrue("task (ラムダ) should be inline", task.hasInlineMethods);
        assertFalse("counter (int) should not be inline", counter.hasInlineMethods);
    }

    @Test
    public void testAbstractMethodsExcluded() {
        // インタフェースの抽象メソッドはエントリに含まれない
        String src = "interface I { void foo(); void bar(); }";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        List<EntitySearchDialog.Entry> all = EntitySearchDialog.filter(cs, "");
        // CLASS I だけが入り、抽象メソッドは抜ける
        for (EntitySearchDialog.Entry e : all) {
            assertFalse("abstract methods should not appear: " + e.simpleName,
                    e.kind == EntitySearchDialog.Kind.METHOD);
        }
    }

    @Test
    public void testEmptyClassList() {
        List<EntitySearchDialog.Entry> all = EntitySearchDialog.filter(
                java.util.Collections.emptyList(), "");
        assertTrue(all.isEmpty());
    }
}
