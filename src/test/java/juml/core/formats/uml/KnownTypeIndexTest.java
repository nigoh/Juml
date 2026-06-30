// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link KnownTypeIndex} と {@link PlantUmlClassRelations#pickUsageTarget} の型解決を検証する。
 *
 * <p>旧実装 (既知クラス集合の全走査 + {@code endsWith}) との同値性、ジェネリクス展開、
 * および曖昧な単純名での <b>決定的なタイブレーク</b> (辞書順最小) を確認する。</p>
 */
public class KnownTypeIndexTest {

    private static Set<String> known(String... names) {
        return new LinkedHashSet<>(java.util.Arrays.asList(names));
    }

    @Test
    public void exactFqnMatches() {
        KnownTypeIndex idx = new KnownTypeIndex(known("com.demo.Foo", "com.demo.Bar"));
        assertTrue(idx.containsExact("com.demo.Foo"));
        assertEquals("com.demo.Foo",
                PlantUmlClassRelations.pickUsageTarget("com.demo.Foo", idx));
    }

    @Test
    public void simpleNameResolvesToFqnViaSuffix() {
        KnownTypeIndex idx = new KnownTypeIndex(known("com.demo.Foo"));
        assertEquals("com.demo.Foo", idx.suffixMatch("Foo"));
        assertEquals("com.demo.Foo",
                PlantUmlClassRelations.pickUsageTarget("Foo", idx));
    }

    @Test
    public void multiSegmentSuffixMatches() {
        // ネスト型 Outer.Inner は ".Outer.Inner" で終わる FQN に解決する
        KnownTypeIndex idx = new KnownTypeIndex(known("com.demo.Outer.Inner"));
        assertEquals("com.demo.Outer.Inner", idx.suffixMatch("Outer.Inner"));
        assertEquals("com.demo.Outer.Inner", idx.suffixMatch("Inner"));
        // 先頭セグメントを含む全体は ".x" 接尾辞では一致しない (exact のみ)
        assertNull(idx.suffixMatch("com.demo.Outer.Inner"));
        assertTrue(idx.containsExact("com.demo.Outer.Inner"));
    }

    @Test
    public void ambiguousSimpleNamePicksLexicographicallySmallest() {
        // 同じ単純名 Foo を持つ 2 つの FQN。決定的に辞書順最小を選ぶ。
        KnownTypeIndex idx = new KnownTypeIndex(known("z.pkg.Foo", "a.pkg.Foo"));
        assertEquals("a.pkg.Foo", idx.suffixMatch("Foo"));
        assertEquals("a.pkg.Foo",
                PlantUmlClassRelations.pickUsageTarget("Foo", idx));
        // 集合の挿入順を変えても結果が変わらない (決定性)
        KnownTypeIndex idx2 = new KnownTypeIndex(known("a.pkg.Foo", "z.pkg.Foo"));
        assertEquals("a.pkg.Foo", idx2.suffixMatch("Foo"));
    }

    @Test
    public void unknownTypeReturnsNull() {
        KnownTypeIndex idx = new KnownTypeIndex(known("com.demo.Foo"));
        assertNull(PlantUmlClassRelations.pickUsageTarget("com.other.Nope", idx));
        assertNull(idx.suffixMatch("Nope"));
        assertFalse(idx.containsExact("Nope"));
    }

    @Test
    public void genericArgumentIsResolved() {
        KnownTypeIndex idx = new KnownTypeIndex(known("com.demo.Foo"));
        // 外側 (List) は未知だが内側のジェネリック引数 Foo に解決する
        assertEquals("com.demo.Foo",
                PlantUmlClassRelations.pickUsageTarget("java.util.List<Foo>", idx));
        assertEquals("com.demo.Foo",
                PlantUmlClassRelations.pickUsageTarget("Foo[]", idx));
    }

    @Test
    public void setOverloadMatchesIndexOverload() {
        // 後方互換 Set オーバーロードが索引版と同じ結果を返す
        Set<String> k = known("a.pkg.Foo", "z.pkg.Foo");
        assertEquals(PlantUmlClassRelations.pickUsageTarget("Foo", new KnownTypeIndex(k)),
                PlantUmlClassRelations.pickUsageTarget("Foo", k));
    }
}
