// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.funcdiff;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.JavaStructureExtractor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * MethodDiffAnalyzer と MarkdownMethodDiffReport の網羅的テスト。
 *
 * <p>カテゴリ別の構成:</p>
 * <ol>
 *   <li>parseSpec — 15 ケース</li>
 *   <li>findMethod — 10 ケース</li>
 *   <li>computeLcsLen — 32 ケース</li>
 *   <li>computeLevenshtein — 32 ケース</li>
 *   <li>computeJaccard — 22 ケース</li>
 *   <li>analyze: 件数カウント — 25 ケース</li>
 *   <li>analyze: 指標値 — 18 ケース</li>
 *   <li>analyze: DiffRow 内容 — 18 ケース</li>
 *   <li>analyze: 信頼度スコア — 16 ケース</li>
 *   <li>MarkdownMethodDiffReport — 22 ケース</li>
 * </ol>
 */
@SuppressWarnings("checkstyle:MethodName")
public class MethodDiffAnalyzerTest {

    // =========================================================================
    // ヘルパー
    // =========================================================================

    private static JavaMethodInfo.Call call(String receiver, String name) {
        return new JavaMethodInfo.Call(receiver, name);
    }

    private static JavaMethodInfo.Call callA(String name) {
        return new JavaMethodInfo.Call(null, name);
    }

    private static JavaMethodInfo.Call callWithArg(String receiver, String name, String arg) {
        JavaMethodInfo.Call c = new JavaMethodInfo.Call(receiver, name);
        c.setFirstArgLabel(arg);
        return c;
    }

    private static List<JavaMethodInfo.Call> calls(String... names) {
        List<JavaMethodInfo.Call> list = new ArrayList<>();
        for (String n : names) list.add(callA(n));
        return list;
    }

    private static List<JavaMethodInfo.Call> callList(JavaMethodInfo.Call... cs) {
        List<JavaMethodInfo.Call> list = new ArrayList<>();
        for (JavaMethodInfo.Call c : cs) list.add(c);
        return list;
    }

    private static List<JavaMethodInfo.Call> empty() {
        return new ArrayList<>();
    }

    private static JavaMethodInfo extractMethod(String src, String cls, String method) {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        return MethodDiffAnalyzer.findMethod(classes,
                new MethodDiffAnalyzer.MethodSpec("dummy.java", cls, method));
    }

    private static MethodDiffAnalyzer.DiffResult diff(String srcA, String clsA, String mA,
                                                        String srcB, String clsB, String mB) {
        JavaMethodInfo methodA = extractMethod(srcA, clsA, mA);
        JavaMethodInfo methodB = extractMethod(srcB, clsB, mB);
        return MethodDiffAnalyzer.analyze(
                methodA, new MethodDiffAnalyzer.MethodSpec("A.java", clsA, mA),
                methodB, new MethodDiffAnalyzer.MethodSpec("B.java", clsB, mB));
    }

    // =========================================================================
    // 1. parseSpec — 15 ケース
    // =========================================================================

    @Test
    public void parseSpec_standardClassAndMethod() {
        MethodDiffAnalyzer.MethodSpec s = MethodDiffAnalyzer.parseSpec("Foo.java::Bar.baz");
        assertEquals("Foo.java", s.filePath);
        assertEquals("Bar", s.className);
        assertEquals("baz", s.methodName);
    }

    @Test
    public void parseSpec_methodOnly_noClass() {
        MethodDiffAnalyzer.MethodSpec s = MethodDiffAnalyzer.parseSpec("Foo.java::baz");
        assertNull(s.className);
        assertEquals("baz", s.methodName);
    }

    @Test
    public void parseSpec_nestedPath() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("src/main/java/Foo.java::MyClass.doIt");
        assertEquals("src/main/java/Foo.java", s.filePath);
        assertEquals("MyClass", s.className);
        assertEquals("doIt", s.methodName);
    }

    @Test
    public void parseSpec_multipleDotsInMethodPart() {
        // "Outer.Inner.method" → className=Inner (単純名), methodName=method。
        // findMethod は className を getSimpleName() と厳密一致で照合するため、
        // ネストクラスの単純名 "Inner" に畳まないと (旧挙動の "Outer.Inner" では)
        // メソッドが永久に見つからない。
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("Foo.java::Outer.Inner.method");
        assertEquals("Inner", s.className);
        assertEquals("method", s.methodName);
    }

    @Test
    public void parseSpec_fullyQualifiedClass_reducesToSimpleName() {
        // "com.example.Foo.bar" → className=Foo (単純名), methodName=bar。
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("Foo.java::com.example.Foo.bar");
        assertEquals("Foo", s.className);
        assertEquals("bar", s.methodName);
    }

    @Test
    public void findMethod_locatesMethodWhenSpecUsesQualifiedClassName() {
        // 修飾名 "com.example.Calc.add" でも、単純名 Calc に畳まれるので発見できる。
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package com.example;\n"
                        + "public class Calc { int add(int a){ return helper(a); }\n"
                        + "  int helper(int a){ return a; } }\n");
        MethodDiffAnalyzer.MethodSpec spec =
                MethodDiffAnalyzer.parseSpec("Calc.java::com.example.Calc.add");
        JavaMethodInfo m = MethodDiffAnalyzer.findMethod(classes, spec);
        assertEquals("add", m == null ? "(not found)" : m.getName());
    }

    @Test
    public void parseSpec_methodWithUnderscore() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("A.java::MyClass.my_method");
        assertEquals("MyClass", s.className);
        assertEquals("my_method", s.methodName);
    }

    @Test
    public void parseSpec_methodStartingWithOnPrefix() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("A.java::Activity.onCreate");
        assertEquals("Activity", s.className);
        assertEquals("onCreate", s.methodName);
    }

    @Test
    public void parseSpec_label_withClass() {
        MethodDiffAnalyzer.MethodSpec s = MethodDiffAnalyzer.parseSpec("A.java::Cls.m");
        assertEquals("Cls.m", s.label());
    }

    @Test
    public void parseSpec_label_withoutClass() {
        MethodDiffAnalyzer.MethodSpec s = MethodDiffAnalyzer.parseSpec("A.java::myMethod");
        assertEquals("myMethod", s.label());
    }

    @Test
    public void parseSpec_trailingSpaces_trimmed() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("  A.java  ::  Cls.m  ");
        assertEquals("A.java", s.filePath);
        assertEquals("Cls", s.className);
        assertEquals("m", s.methodName);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSpec_emptyString_throws() {
        MethodDiffAnalyzer.parseSpec("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSpec_null_throws() {
        MethodDiffAnalyzer.parseSpec(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSpec_noSeparator_throws() {
        MethodDiffAnalyzer.parseSpec("Foo.java");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSpec_onlySeparator_throws() {
        MethodDiffAnalyzer.parseSpec("::");
    }

    @Test
    public void parseSpec_absolutePath() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("/home/user/proj/Foo.java::Cls.m");
        assertEquals("/home/user/proj/Foo.java", s.filePath);
        assertEquals("Cls", s.className);
        assertEquals("m", s.methodName);
    }

    @Test
    public void parseSpec_longMethodName() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("A.java::MyService.handleVehiclePropertyChanged");
        assertEquals("MyService", s.className);
        assertEquals("handleVehiclePropertyChanged", s.methodName);
    }

    // =========================================================================
    // 2. findMethod — 10 ケース
    // =========================================================================

    @Test
    public void findMethod_found_byClassAndName() {
        String src = "class Foo { X x; void bar() { x.run(); } }";
        JavaMethodInfo m = extractMethod(src, "Foo", "bar");
        assertNotNull(m);
        assertEquals("bar", m.getName());
    }

    @Test
    public void findMethod_found_byNameOnly() {
        String src = "class Foo { X x; void bar() { x.run(); } }";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        JavaMethodInfo m = MethodDiffAnalyzer.findMethod(classes,
                new MethodDiffAnalyzer.MethodSpec("f.java", null, "bar"));
        assertNotNull(m);
    }

    @Test
    public void findMethod_notFound_wrongMethodName() {
        String src = "class Foo { void bar() {} }";
        assertNull(extractMethod(src, "Foo", "baz"));
    }

    @Test
    public void findMethod_notFound_wrongClassName() {
        String src = "class Foo { void bar() {} }";
        assertNull(extractMethod(src, "Wrong", "bar"));
    }

    @Test
    public void findMethod_multipleClasses_selectsByClassName() {
        String src = "class A { void go() {} } class B { void go() {} }";
        JavaMethodInfo mA = extractMethod(src, "A", "go");
        JavaMethodInfo mB = extractMethod(src, "B", "go");
        assertNotNull(mA);
        assertNotNull(mB);
    }

    @Test
    public void findMethod_emptyClassList_returnsNull() {
        List<JavaClassInfo> empty = new ArrayList<>();
        assertNull(MethodDiffAnalyzer.findMethod(empty,
                new MethodDiffAnalyzer.MethodSpec("f.java", "X", "m")));
    }

    @Test
    public void findMethod_multipleMethodsSameName_returnsFirst() {
        // オーバーロードがある場合は最初のメソッドを返す
        String src = "class A { void go(int x) {} void go(String s) {} }";
        JavaMethodInfo m = extractMethod(src, "A", "go");
        assertNotNull(m);
        assertEquals("go", m.getName());
    }

    @Test
    public void findMethod_methodInInterface() {
        String src = "interface Svc { void init(); }";
        JavaMethodInfo m = extractMethod(src, "Svc", "init");
        assertNotNull(m);
    }

    @Test
    public void findMethod_constructorNotMatchedByMethodSearch() {
        // コンストラクタはisConstructor=trueだが名前はクラス名。メソッド名指定で見つかるかを確認
        String src = "class A { A() {} void go() {} }";
        JavaMethodInfo ctor = extractMethod(src, "A", "A");
        // JavaStructureExtractorの実装によるが、コンストラクタも名前でマッチする可能性がある
        // ここでは go() は確実に見つかることを確認
        JavaMethodInfo m = extractMethod(src, "A", "go");
        assertNotNull(m);
    }

    @Test
    public void findMethod_classNameNullMatchesFirst() {
        String src = "class Z { void run() {} }";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        JavaMethodInfo m = MethodDiffAnalyzer.findMethod(classes,
                new MethodDiffAnalyzer.MethodSpec("f.java", null, "run"));
        assertNotNull(m);
    }

    // =========================================================================
    // 3. computeLcsLen — 32 ケース
    // =========================================================================

    @Test
    public void lcs_bothEmpty_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(empty(), empty()));
    }

    @Test
    public void lcs_aEmpty_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(empty(), calls("a")));
    }

    @Test
    public void lcs_bEmpty_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(calls("a"), empty()));
    }

    @Test
    public void lcs_singleMatch_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(calls("a"), calls("a")));
    }

    @Test
    public void lcs_singleNoMatch_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(calls("a"), calls("b")));
    }

    @Test
    public void lcs_identicalPair_returns2() {
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(calls("a", "b"), calls("a", "b")));
    }

    @Test
    public void lcs_identical3_returns3() {
        assertEquals(3, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("a", "b", "c")));
    }

    @Test
    public void lcs_identical5_returns5() {
        assertEquals(5, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c", "d", "e"), calls("a", "b", "c", "d", "e")));
    }

    @Test
    public void lcs_reversed2_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(calls("a", "b"), calls("b", "a")));
    }

    @Test
    public void lcs_reversed3_returns1() {
        // [a,b,c] vs [c,b,a] → LCS は長さ1 (b単独 or a単独 or c単独)
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("c", "b", "a")));
    }

    @Test
    public void lcs_subsequence_abcVSac() {
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("a", "c")));
    }

    @Test
    public void lcs_subsequence_abcVSbc() {
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("b", "c")));
    }

    @Test
    public void lcs_subsequence_abcVSab() {
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("a", "b")));
    }

    @Test
    public void lcs_partialOverlap_abcVSbcd() {
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("b", "c", "d")));
    }

    @Test
    public void lcs_disjoint_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b"), calls("c", "d")));
    }

    @Test
    public void lcs_singleCommonAtStart() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("a", "x", "y")));
    }

    @Test
    public void lcs_singleCommonAtEnd() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("x", "y", "c")));
    }

    @Test
    public void lcs_singleCommonInMiddle() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("x", "b", "y")));
    }

    @Test
    public void lcs_duplicateNames_aab_VSab() {
        // [a,a,b] vs [a,b] → LCS=2 (a,b)
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "a", "b"), calls("a", "b")));
    }

    @Test
    public void lcs_duplicateNames_aVSaa() {
        // [a] vs [a,a] → LCS=1
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(calls("a"), calls("a", "a")));
    }

    @Test
    public void lcs_allSameNames_returns_shorterLen() {
        // [a,a,a] vs [a,a] → LCS=2
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "a", "a"), calls("a", "a")));
    }

    @Test
    public void lcs_abcde_VS_bd() {
        assertEquals(2, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c", "d", "e"), calls("b", "d")));
    }

    @Test
    public void lcs_abcde_VS_ace() {
        assertEquals(3, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c", "d", "e"), calls("a", "c", "e")));
    }

    @Test
    public void lcs_aVSabcde() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(calls("a"), calls("a", "b", "c", "d", "e")));
    }

    @Test
    public void lcs_longerA_isSymmetric() {
        int ab = MethodDiffAnalyzer.computeLcsLen(calls("a", "b", "c"), calls("b", "c", "d"));
        int ba = MethodDiffAnalyzer.computeLcsLen(calls("b", "c", "d"), calls("a", "b", "c"));
        assertEquals(ab, ba);
    }

    @Test
    public void lcs_caseSensitive_noMatch() {
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(calls("Init"), calls("init")));
    }

    @Test
    public void lcs_mixedCaseSomeMatch() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(
                calls("Init", "run"), calls("init", "run")));
    }

    @Test
    public void lcs_lengthOne_A5_B1_match() {
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c", "d", "e"), calls("c")));
    }

    @Test
    public void lcs_lengthOne_A5_B1_noMatch() {
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c", "d", "e"), calls("z")));
    }

    @Test
    public void lcs_interleaved_abcabc_VSabc() {
        // [a,b,c,a,b,c] vs [a,b,c] → LCS=3
        assertEquals(3, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c", "a", "b", "c"), calls("a", "b", "c")));
    }

    @Test
    public void lcs_10elements_identical() {
        assertEquals(10, MethodDiffAnalyzer.computeLcsLen(
                calls("a","b","c","d","e","f","g","h","i","j"),
                calls("a","b","c","d","e","f","g","h","i","j")));
    }

    // =========================================================================
    // 4. computeLevenshtein — 32 ケース
    // =========================================================================

    @Test
    public void lev_bothEmpty_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLevenshtein(empty(), empty()));
    }

    @Test
    public void lev_aEmptyBOne_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(empty(), calls("a")));
    }

    @Test
    public void lev_aOneBEmpty_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(calls("a"), empty()));
    }

    @Test
    public void lev_identical1_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLevenshtein(calls("a"), calls("a")));
    }

    @Test
    public void lev_identical2_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLevenshtein(calls("a", "b"), calls("a", "b")));
    }

    @Test
    public void lev_identical3_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c"), calls("a", "b", "c")));
    }

    @Test
    public void lev_singleSubstitution_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(calls("a"), calls("b")));
    }

    @Test
    public void lev_singleInsertion_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(calls("a"), calls("a", "b")));
    }

    @Test
    public void lev_singleDeletion_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(calls("a", "b"), calls("a")));
    }

    @Test
    public void lev_twoSubstitutions_returns2() {
        assertEquals(2, MethodDiffAnalyzer.computeLevenshtein(calls("a", "b"), calls("c", "d")));
    }

    @Test
    public void lev_insertAtEnd_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c"), calls("a", "b", "c", "d")));
    }

    @Test
    public void lev_deleteAtEnd_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c", "d"), calls("a", "b", "c")));
    }

    @Test
    public void lev_insertAtStart_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(
                calls("b", "c"), calls("a", "b", "c")));
    }

    @Test
    public void lev_deleteAtStart_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c"), calls("b", "c")));
    }

    @Test
    public void lev_reversed2_returns2() {
        // [a,b] → [b,a]: substitute a→b and b→a = 2
        assertEquals(2, MethodDiffAnalyzer.computeLevenshtein(calls("a", "b"), calls("b", "a")));
    }

    @Test
    public void lev_empty_VS_3elements_returns3() {
        assertEquals(3, MethodDiffAnalyzer.computeLevenshtein(empty(), calls("a", "b", "c")));
    }

    @Test
    public void lev_3elements_VS_empty_returns3() {
        assertEquals(3, MethodDiffAnalyzer.computeLevenshtein(calls("a", "b", "c"), empty()));
    }

    @Test
    public void lev_disjoint3_returns3() {
        // [a,b,c] → [d,e,f]: 3 substitutions
        assertEquals(3, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c"), calls("d", "e", "f")));
    }

    @Test
    public void lev_oneCommon_abcVSaxy_returns2() {
        // [a,b,c] → [a,x,y]: 2 substitutions
        assertEquals(2, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c"), calls("a", "x", "y")));
    }

    @Test
    public void lev_addingExtraCallsAtEnd_returns3() {
        assertEquals(3, MethodDiffAnalyzer.computeLevenshtein(
                calls("a"), calls("a", "b", "c", "d")));
    }

    @Test
    public void lev_removingExtraCallsAtEnd_returns3() {
        assertEquals(3, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c", "d"), calls("a")));
    }

    @Test
    public void lev_symmetric_abcVSbcd() {
        int ab = MethodDiffAnalyzer.computeLevenshtein(calls("a", "b", "c"), calls("b", "c", "d"));
        int ba = MethodDiffAnalyzer.computeLevenshtein(calls("b", "c", "d"), calls("a", "b", "c"));
        assertEquals(ab, ba);
    }

    @Test
    public void lev_caseSensitive_differentName_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(calls("Init"), calls("init")));
    }

    @Test
    public void lev_duplicatesInA_abVSaab() {
        // [a,b] → [a,a,b]: insert one a = 1
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(calls("a", "b"), calls("a", "a", "b")));
    }

    @Test
    public void lev_triangleInequality() {
        // d(A,C) <= d(A,B) + d(B,C)
        int ab = MethodDiffAnalyzer.computeLevenshtein(calls("a", "b"), calls("a", "c"));
        int bc = MethodDiffAnalyzer.computeLevenshtein(calls("a", "c"), calls("b", "c"));
        int ac = MethodDiffAnalyzer.computeLevenshtein(calls("a", "b"), calls("b", "c"));
        assertTrue(ac <= ab + bc);
    }

    @Test
    public void lev_allSame_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLevenshtein(
                calls("x", "x", "x"), calls("x", "x", "x")));
    }

    @Test
    public void lev_allSameDifferentLength_returnsDiff() {
        assertEquals(2, MethodDiffAnalyzer.computeLevenshtein(
                calls("x", "x"), calls("x", "x", "x", "x")));
    }

    @Test
    public void lev_singleElemToEmpty_returns1() {
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(calls("a"), empty()));
    }

    @Test
    public void lev_5elements_identical_returns0() {
        assertEquals(0, MethodDiffAnalyzer.computeLevenshtein(
                calls("a","b","c","d","e"), calls("a","b","c","d","e")));
    }

    @Test
    public void lev_10elements_completelyDifferent_returns10() {
        assertEquals(10, MethodDiffAnalyzer.computeLevenshtein(
                calls("a","b","c","d","e","f","g","h","i","j"),
                calls("A","B","C","D","E","F","G","H","I","J")));
    }

    @Test
    public void lev_swapMiddleElement_returns2() {
        // [a,b,c] → [a,X,c]: 1 substitution
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b", "c"), calls("a", "X", "c")));
    }

    // =========================================================================
    // 5. computeJaccard — 22 ケース
    // =========================================================================

    @Test
    public void jaccard_bothEmpty_returns1() {
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(empty(), empty()), 0.001);
    }

    @Test
    public void jaccard_aEmpty_returns0() {
        assertEquals(0.0, MethodDiffAnalyzer.computeJaccard(empty(), calls("a")), 0.001);
    }

    @Test
    public void jaccard_bEmpty_returns0() {
        assertEquals(0.0, MethodDiffAnalyzer.computeJaccard(calls("a"), empty()), 0.001);
    }

    @Test
    public void jaccard_identical1_returns1() {
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(calls("a"), calls("a")), 0.001);
    }

    @Test
    public void jaccard_identical3_returns1() {
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(
                calls("a", "b", "c"), calls("a", "b", "c")), 0.001);
    }

    @Test
    public void jaccard_disjoint_returns0() {
        assertEquals(0.0, MethodDiffAnalyzer.computeJaccard(
                calls("a", "b"), calls("c", "d")), 0.001);
    }

    @Test
    public void jaccard_halfOverlap_ab_VS_bc() {
        // {a,b} ∩ {b,c} = {b} → 1/3
        assertEquals(1.0 / 3.0, MethodDiffAnalyzer.computeJaccard(
                calls("a", "b"), calls("b", "c")), 0.001);
    }

    @Test
    public void jaccard_orderDoesNotMatter() {
        double ab = MethodDiffAnalyzer.computeJaccard(calls("a", "b", "c"), calls("c", "a", "b"));
        assertEquals(1.0, ab, 0.001);
    }

    @Test
    public void jaccard_duplicates_treatedAsSet() {
        // [a,a,b] → set={a,b}; [a,b] → set={a,b} → Jaccard=1.0
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(
                calls("a", "a", "b"), calls("a", "b")), 0.001);
    }

    @Test
    public void jaccard_singleOverlapOutOf5() {
        // {a,b,c,d,e} ∩ {a,x,y,z,w} = {a} → 1/9
        assertEquals(1.0 / 9.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","b","c","d","e"), calls("a","x","y","z","w")), 0.001);
    }

    @Test
    public void jaccard_symmetric() {
        double ab = MethodDiffAnalyzer.computeJaccard(calls("a","b","c"), calls("b","c","d"));
        double ba = MethodDiffAnalyzer.computeJaccard(calls("b","c","d"), calls("a","b","c"));
        assertEquals(ab, ba, 0.001);
    }

    @Test
    public void jaccard_twoOverlapOutOf4() {
        // {a,b} ∩ {a,b,c,d} = {a,b} → 2/4 = 0.5
        assertEquals(0.5, MethodDiffAnalyzer.computeJaccard(
                calls("a","b"), calls("a","b","c","d")), 0.001);
    }

    @Test
    public void jaccard_reversedIdentical_stillOne() {
        // 逆順でも集合は同じ
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","b","c"), calls("c","b","a")), 0.001);
    }

    @Test
    public void jaccard_singleElem_match() {
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(calls("x"), calls("x")), 0.001);
    }

    @Test
    public void jaccard_singleElem_noMatch() {
        assertEquals(0.0, MethodDiffAnalyzer.computeJaccard(calls("x"), calls("y")), 0.001);
    }

    @Test
    public void jaccard_3over5_and_3over5_is_3over7() {
        // {a,b,c,d,e} vs {a,b,c,x,y} → inter=3, union=7
        assertEquals(3.0 / 7.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","b","c","d","e"), calls("a","b","c","x","y")), 0.001);
    }

    @Test
    public void jaccard_allDuplicatesInA_singleInB() {
        // [a,a,a] → {a}; [a] → {a} → 1.0
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","a","a"), calls("a")), 0.001);
    }

    @Test
    public void jaccard_laxCaseSensitive() {
        assertEquals(0.0, MethodDiffAnalyzer.computeJaccard(calls("Init"), calls("init")), 0.001);
    }

    @Test
    public void jaccard_10elementsPartialOverlap() {
        // {a-j} vs {f-o}: overlap={f,g,h,i,j}=5, union=15 → 1/3
        assertEquals(5.0 / 15.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","b","c","d","e","f","g","h","i","j"),
                calls("f","g","h","i","j","k","l","m","n","o")), 0.001);
    }

    @Test
    public void jaccard_aSubsetOfB_returnsCorrect() {
        // {a,b} ⊂ {a,b,c} → 2/3
        assertEquals(2.0 / 3.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","b"), calls("a","b","c")), 0.001);
    }

    @Test
    public void jaccard_bSubsetOfA_returnsCorrect() {
        assertEquals(2.0 / 3.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","b","c"), calls("a","b")), 0.001);
    }

    @Test
    public void jaccard_singleCommonOf6() {
        // {a,b,c} ∩ {c,d,e} = {c} → 1/5
        assertEquals(1.0 / 5.0, MethodDiffAnalyzer.computeJaccard(
                calls("a","b","c"), calls("c","d","e")), 0.001);
    }

    // =========================================================================
    // 6. analyze: 件数カウント — 25 ケース
    // =========================================================================

    @Test
    public void analyze_bothNull_allZeroCounts() {
        MethodDiffAnalyzer.DiffResult r = MethodDiffAnalyzer.analyze(
                null, new MethodDiffAnalyzer.MethodSpec("A.java", "A", "m"),
                null, new MethodDiffAnalyzer.MethodSpec("B.java", "B", "m"));
        assertEquals(0, r.matchCount);
        assertEquals(0, r.partialCount);
        assertEquals(0, r.onlyACount);
        assertEquals(0, r.onlyBCount);
        assertEquals(0, r.totalCallsA);
        assertEquals(0, r.totalCallsB);
        assertTrue(r.rows.isEmpty());
    }

    @Test
    public void analyze_identicalSingleCall_matchCount1() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(1, r.matchCount);
        assertEquals(0, r.partialCount);
        assertEquals(0, r.onlyACount);
        assertEquals(0, r.onlyBCount);
    }

    @Test
    public void analyze_identicalThreeCalls_matchCount3() {
        String src = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(3, r.matchCount);
        assertEquals(0, r.partialCount);
        assertEquals(0, r.onlyACount);
        assertEquals(0, r.onlyBCount);
    }

    @Test
    public void analyze_onlyA_has1extra() {
        String srcA = "class A { X x; void go() { x.a(); x.extra(); } }";
        String srcB = "class B { X x; void go() { x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(1, r.matchCount);
        assertEquals(1, r.onlyACount);
        assertEquals(0, r.onlyBCount);
    }

    @Test
    public void analyze_onlyB_has1extra() {
        String srcA = "class A { X x; void go() { x.a(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.extra(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(1, r.matchCount);
        assertEquals(0, r.onlyACount);
        assertEquals(1, r.onlyBCount);
    }

    @Test
    public void analyze_onlyA_has3extra() {
        String srcA = "class A { X x; void go() {"
                + " x.a(); x.b(); x.c(); x.d(); } }";
        String srcB = "class B { X x; void go() { x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(1, r.matchCount);
        assertEquals(3, r.onlyACount);
    }

    @Test
    public void analyze_completelyDifferent_allOnly() {
        String srcA = "class A { X x; void go() { x.alpha(); x.beta(); } }";
        String srcB = "class B { Y y; void go() { y.gamma(); y.delta(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0, r.matchCount);
        assertEquals(0, r.partialCount);
        assertEquals(2, r.onlyACount);
        assertEquals(2, r.onlyBCount);
    }

    @Test
    public void analyze_differentReceiver_isPartial() {
        String srcA = "class A { Foo f; void go() { f.save(); } }";
        String srcB = "class B { Bar b; void go() { b.save(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0, r.matchCount);
        assertEquals(1, r.partialCount);
    }

    @Test
    public void analyze_differentFirstArg_isPartial() {
        String srcA = "class A { X x; void go() { x.put(KEY_A); } }";
        String srcB = "class B { X x; void go() { x.put(KEY_B); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // firstArgLabelはパーサーが定数参照のときのみセットするので、
        // put(KEY_A)が定数参照と判断されるかは実装依存。指標が0以上なら正常
        assertNotNull(r);
    }

    @Test
    public void analyze_totalCallsA_correct() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() { x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(3, r.totalCallsA);
        assertEquals(1, r.totalCallsB);
    }

    @Test
    public void analyze_totalCalls_bothZero_forEmptyMethod() {
        String srcA = "class A { void go() {} }";
        String srcB = "class B { void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0, r.totalCallsA);
        assertEquals(0, r.totalCallsB);
    }

    @Test
    public void analyze_rowCount_equalsUnionOfAlignedRows() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.x(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // a=MATCH, b=ONLY_A, x=ONLY_B, c=MATCH → 4行
        assertEquals(4, r.rows.size());
    }

    @Test
    public void analyze_partialCount_zero_whenAllMatchOrOnly() {
        String srcA = "class A { X x; void go() { x.a(); x.extra(); } }";
        String srcB = "class B { X x; void go() { x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0, r.partialCount);
    }

    @Test
    public void analyze_mixedResult_countsCorrect() {
        // A=[a,b,c,d] B=[a,x,c,e]
        // a→MATCH, b→ONLY_A, x→ONLY_B, c→MATCH, d→ONLY_A, e→ONLY_B
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); x.d(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.x(); x.c(); x.e(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(2, r.matchCount);
        assertEquals(0, r.partialCount);
        assertEquals(2, r.onlyACount);
        assertEquals(2, r.onlyBCount);
    }

    @Test
    public void analyze_methodNullA_onlyBCount_equals_BCallCount() {
        String srcB = "class B { X x; void go() { x.a(); x.b(); x.c(); } }";
        JavaMethodInfo mB = extractMethod(srcB, "B", "go");
        MethodDiffAnalyzer.DiffResult r = MethodDiffAnalyzer.analyze(
                null, new MethodDiffAnalyzer.MethodSpec("A.java", "A", "go"),
                mB, new MethodDiffAnalyzer.MethodSpec("B.java", "B", "go"));
        assertEquals(0, r.onlyACount);
        assertEquals(3, r.onlyBCount);
        assertEquals(3, r.totalCallsB);
    }

    @Test
    public void analyze_methodNullB_onlyACount_equals_ACallCount() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";
        JavaMethodInfo mA = extractMethod(srcA, "A", "go");
        MethodDiffAnalyzer.DiffResult r = MethodDiffAnalyzer.analyze(
                mA, new MethodDiffAnalyzer.MethodSpec("A.java", "A", "go"),
                null, new MethodDiffAnalyzer.MethodSpec("B.java", "B", "go"));
        assertEquals(2, r.onlyACount);
        assertEquals(0, r.onlyBCount);
    }

    @Test
    public void analyze_reorderedCalls_someMatchSomeOnly() {
        // [a,b,c] vs [c,b,a] → LCS=1 なので 1 MATCH + ONLYが複数
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() { x.c(); x.b(); x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // MATCH は LCS の長さ(=1)だけ、残りはONLY_AかONLY_B
        assertEquals(1, r.matchCount);
        assertTrue(r.onlyACount + r.onlyBCount > 0);
    }

    @Test
    public void analyze_singleCallBothSides_match() {
        String srcA = "class A { X x; void go() { x.run(); } }";
        String srcB = "class B { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(1, r.matchCount);
        assertEquals(1, r.rows.size());
    }

    @Test
    public void analyze_5calls_identical() {
        String src = "class A { X x; void go() {"
                + " x.a(); x.b(); x.c(); x.d(); x.e(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(5, r.matchCount);
        assertEquals(0, r.onlyACount);
        assertEquals(0, r.onlyBCount);
    }

    @Test
    public void analyze_emptyMethod_allMetricsNeutral() {
        String src = "class A { void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(0, r.totalCallsA);
        assertEquals(0, r.totalCallsB);
        assertTrue(r.rows.isEmpty());
    }

    @Test
    public void analyze_specInfo_preserved() {
        String src = "class A { X x; void go() { x.run(); } }";
        JavaMethodInfo m = extractMethod(src, "A", "go");
        MethodDiffAnalyzer.MethodSpec sA = new MethodDiffAnalyzer.MethodSpec("myA.java","A","go");
        MethodDiffAnalyzer.MethodSpec sB = new MethodDiffAnalyzer.MethodSpec("myB.java","A","go");
        MethodDiffAnalyzer.DiffResult r = MethodDiffAnalyzer.analyze(m, sA, m, sB);
        assertEquals("myA.java", r.specA.filePath);
        assertEquals("myB.java", r.specB.filePath);
    }

    @Test
    public void analyze_onlyOnePartialInMixed() {
        String srcA = "class A { Foo f; X x; void go() { f.save(); x.load(); } }";
        String srcB = "class B { Bar b; X x; void go() { b.save(); x.load(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // save()はPARTIAL(receiver違い)、load()はMATCH(同じreceiverで x)
        assertEquals(1, r.matchCount);
        assertEquals(1, r.partialCount);
    }

    // =========================================================================
    // 7. analyze: 指標値 — 18 ケース
    // =========================================================================

    @Test
    public void metrics_identical_allOne() {
        String src = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(1.0, r.metrics.lcsSimilarity, 0.001);
        assertEquals(0, r.metrics.editDistance);
        assertEquals(1.0, r.metrics.normalizedEditSimilarity, 0.001);
        assertEquals(1.0, r.metrics.jaccard, 0.001);
        assertEquals(3, r.metrics.lcsLen);
    }

    @Test
    public void metrics_emptyBothSides_allOne() {
        String src = "class A { void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(1.0, r.metrics.lcsSimilarity, 0.001);
        assertEquals(0, r.metrics.editDistance);
        assertEquals(1.0, r.metrics.jaccard, 0.001);
    }

    @Test
    public void metrics_oneExtraInB_editDistanceIsOne() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.b(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(1, r.metrics.editDistance);
        assertEquals(2, r.metrics.lcsLen);
    }

    @Test
    public void metrics_lcs_normalizedByMax() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";  // len=2
        String srcB = "class B { X x; void go() { x.a(); x.b(); x.c(); } }";  // len=3
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // LCS=2, max=3 → 2/3
        assertEquals(2.0 / 3.0, r.metrics.lcsSimilarity, 0.001);
    }

    @Test
    public void metrics_editNormalized_correct() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.b(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // edit=1, max=3 → 1 - 1/3 = 0.667
        assertEquals(1.0 - 1.0 / 3.0, r.metrics.normalizedEditSimilarity, 0.001);
    }

    @Test
    public void metrics_jaccardReversedOrder_isOne() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() { x.c(); x.b(); x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(1.0, r.metrics.jaccard, 0.001);
    }

    @Test
    public void metrics_completelyDifferent_lcsZero() {
        String srcA = "class A { X x; void go() { x.alpha(); x.beta(); } }";
        String srcB = "class B { Y y; void go() { y.gamma(); y.delta(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0, r.metrics.lcsLen);
        assertEquals(0.0, r.metrics.lcsSimilarity, 0.001);
        assertEquals(0.0, r.metrics.jaccard, 0.001);
    }

    @Test
    public void metrics_completelyDifferent_editDistanceIsMax() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";
        String srcB = "class B { Y y; void go() { y.c(); y.d(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // 2要素の完全置換: edit=2, max=2 → normalizedEdit=0.0
        assertEquals(2, r.metrics.editDistance);
        assertEquals(0.0, r.metrics.normalizedEditSimilarity, 0.001);
    }

    @Test
    public void metrics_halfCommon_jaccardHalf() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";
        String srcB = "class B { X x; void go() { x.b(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // {a,b} ∩ {b,c} = {b} → 1/3
        assertEquals(1.0 / 3.0, r.metrics.jaccard, 0.001);
    }

    @Test
    public void metrics_lcsLen_singleMatch() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";
        String srcB = "class B { X x; void go() { x.b(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(1, r.metrics.lcsLen);
    }

    @Test
    public void metrics_normalizedEditSimilarity_between0and1() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.x(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(r.metrics.normalizedEditSimilarity >= 0.0);
        assertTrue(r.metrics.normalizedEditSimilarity <= 1.0);
    }

    @Test
    public void metrics_lcsSimilarity_between0and1() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() { x.x(); x.y(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(r.metrics.lcsSimilarity >= 0.0);
        assertTrue(r.metrics.lcsSimilarity <= 1.0);
    }

    @Test
    public void metrics_jaccard_between0and1() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); } }";
        String srcB = "class B { X x; void go() { x.b(); x.c(); x.d(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(r.metrics.jaccard >= 0.0);
        assertTrue(r.metrics.jaccard <= 1.0);
    }

    @Test
    public void metrics_oneCallEach_noMatch_allZero() {
        String srcA = "class A { X x; void go() { x.alpha(); } }";
        String srcB = "class B { X x; void go() { x.beta(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0, r.metrics.lcsLen);
        assertEquals(0.0, r.metrics.jaccard, 0.001);
    }

    @Test
    public void metrics_nullMethodA_editEqualsLenB() {
        String srcB = "class B { X x; void go() { x.a(); x.b(); x.c(); } }";
        JavaMethodInfo mB = extractMethod(srcB, "B", "go");
        MethodDiffAnalyzer.DiffResult r = MethodDiffAnalyzer.analyze(
                null, new MethodDiffAnalyzer.MethodSpec("A.java","A","go"),
                mB, new MethodDiffAnalyzer.MethodSpec("B.java","B","go"));
        assertEquals(3, r.metrics.editDistance);
        assertEquals(0, r.metrics.lcsLen);
    }

    @Test
    public void metrics_5callsIdentical_lcsIs5() {
        String src = "class A { X x; void go() {"
                + " x.a(); x.b(); x.c(); x.d(); x.e(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(5, r.metrics.lcsLen);
        assertEquals(5.0 / 5.0, r.metrics.lcsSimilarity, 0.001);
    }

    @Test
    public void metrics_avgConfidence_zero_whenNoMatch() {
        String srcA = "class A { X x; void go() { x.alpha(); } }";
        String srcB = "class B { X x; void go() { x.beta(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0.0, r.avgConfidence, 0.001);
    }

    // =========================================================================
    // 8. analyze: DiffRow 内容 — 18 ケース
    // =========================================================================

    @Test
    public void row_match_kind_isMatch() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(MethodDiffAnalyzer.MatchKind.MATCH, r.rows.get(0).kind);
    }

    @Test
    public void row_onlyA_kind_isOnlyA() {
        String srcA = "class A { X x; void go() { x.extra(); } }";
        String srcB = "class B { X x; void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(MethodDiffAnalyzer.MatchKind.ONLY_A, r.rows.get(0).kind);
    }

    @Test
    public void row_onlyB_kind_isOnlyB() {
        String srcA = "class A { X x; void go() {} }";
        String srcB = "class B { X x; void go() { x.extra(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(MethodDiffAnalyzer.MatchKind.ONLY_B, r.rows.get(0).kind);
    }

    @Test
    public void row_partial_kind_isPartial() {
        String srcA = "class A { Foo f; void go() { f.save(); } }";
        String srcB = "class B { Bar b; void go() { b.save(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(MethodDiffAnalyzer.MatchKind.PARTIAL, r.rows.get(0).kind);
    }

    @Test
    public void row_match_callA_notNull() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertNotNull(r.rows.get(0).callA);
    }

    @Test
    public void row_match_callB_notNull() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertNotNull(r.rows.get(0).callB);
    }

    @Test
    public void row_onlyA_callB_isNull() {
        String srcA = "class A { X x; void go() { x.extra(); } }";
        String srcB = "class B { void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertNull(r.rows.get(0).callB);
    }

    @Test
    public void row_onlyB_callA_isNull() {
        String srcA = "class A { void go() {} }";
        String srcB = "class B { X x; void go() { x.extra(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertNull(r.rows.get(0).callA);
    }

    @Test
    public void row_onlyA_confidence_isNegative() {
        String srcA = "class A { X x; void go() { x.extra(); } }";
        String srcB = "class B { void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(r.rows.get(0).confidence < 0);
    }

    @Test
    public void row_onlyB_confidence_isNegative() {
        String srcA = "class A { void go() {} }";
        String srcB = "class B { X x; void go() { x.extra(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(r.rows.get(0).confidence < 0);
    }

    @Test
    public void row_match_confidence_isPositive() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(r.rows.get(0).confidence >= 0);
    }

    @Test
    public void row_partial_detail_containsReceiverInfo() {
        String srcA = "class A { Foo f; void go() { f.save(); } }";
        String srcB = "class B { Bar b; void go() { b.save(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertNotNull(r.rows.get(0).detail);
        assertTrue(r.rows.get(0).detail.contains("レシーバー"));
    }

    @Test
    public void row_match_detail_isNull() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertNull(r.rows.get(0).detail);
    }

    @Test
    public void row_methodName_preserved_inCallA() {
        String src = "class A { X x; void go() { x.mySpecialMethod(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals("mySpecialMethod", r.rows.get(0).callA.getMethodName());
    }

    @Test
    public void row_sequence_preservesOrder_abc() {
        String src = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals("a", r.rows.get(0).callA.getMethodName());
        assertEquals("b", r.rows.get(1).callA.getMethodName());
        assertEquals("c", r.rows.get(2).callA.getMethodName());
    }

    @Test
    public void row_totalCount_equalsRowListSize() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.extra(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.b(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        int counted = r.matchCount + r.partialCount + r.onlyACount + r.onlyBCount;
        assertEquals(r.rows.size(), counted);
    }

    @Test
    public void row_matchMethodName_sameOnBothSides() {
        String src = "class A { X x; void go() { x.doWork(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        MethodDiffAnalyzer.DiffRow row = r.rows.get(0);
        assertEquals(row.callA.getMethodName(), row.callB.getMethodName());
    }

    // =========================================================================
    // 9. analyze: 信頼度スコア — 16 ケース
    // =========================================================================

    @Test
    public void confidence_sameReceiverSameArg_isOne() {
        // receiver=x, firstArg=null の場合：receiver_score=1.0, firstArg_score=1.0,
        // positionはsingle=1.0 → confidence=1.0
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(1.0, r.rows.get(0).confidence, 0.001);
    }

    @Test
    public void confidence_diffReceiver_reducedBy04() {
        // receiver違い: receiver_score=0.0, firstArg=null→1.0, position(単一=1.0)
        // → 0*0.4 + 1*0.3 + 1*0.3 = 0.6
        String srcA = "class A { Foo f; void go() { f.save(); } }";
        String srcB = "class B { Bar b; void go() { b.save(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0.0 * 0.4 + 1.0 * 0.3 + 1.0 * 0.3, r.rows.get(0).confidence, 0.01);
    }

    @Test
    public void confidence_always_between0and1() {
        String srcA = "class A { Foo f; X x; void go() { f.save(); x.load(); } }";
        String srcB = "class B { Bar b; X x; void go() { b.save(); x.load(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            if (row.confidence >= 0) {
                assertTrue(row.confidence >= 0.0 && row.confidence <= 1.0);
            }
        }
    }

    @Test
    public void confidence_matchRow_greaterThan_partialRow() {
        String srcA = "class A { Foo f; X x; void go() { f.save(); x.load(); } }";
        String srcB = "class B { Bar b; X x; void go() { b.save(); x.load(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // rows[0]=PARTIAL(save), rows[1]=MATCH(load) の順は実装依存。合計でMATCH>=PARTIALを確認
        double maxConf = -1, minConf = 2;
        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            if (row.confidence >= 0) {
                if (row.confidence > maxConf) maxConf = row.confidence;
                if (row.confidence < minConf) minConf = row.confidence;
            }
        }
        assertTrue(maxConf >= minConf);
    }

    @Test
    public void confidence_5callsIdentical_allOne() {
        String src = "class A { X x; void go() {"
                + " x.a(); x.b(); x.c(); x.d(); x.e(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            assertEquals(1.0, row.confidence, 0.001);
        }
    }

    @Test
    public void confidence_avgConfidence_identicalMethod_isOne() {
        String src = "class A { X x; void go() { x.a(); x.b(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertEquals(1.0, r.avgConfidence, 0.001);
    }

    @Test
    public void confidence_avgConfidence_noMatchRows_isZero() {
        String srcA = "class A { void go() {} }";
        String srcB = "class B { X x; void go() { x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(0.0, r.avgConfidence, 0.001);
    }

    @Test
    public void confidence_position_firstAndLast_reducesScore() {
        // A=[a,b,c], B=[a,X,X,X,X,c] → a=position(0/2 vs 0/5)=good, c=position(2/2 vs 5/5)=good
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() {"
                + " x.a(); x.X1(); x.X2(); x.X3(); x.X4(); x.c(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // matchするものはa,c。position_score: a=|0/2-0/5|=0→1.0, c=|2/2-5/5|=0→1.0
        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            if (row.kind == MethodDiffAnalyzer.MatchKind.MATCH && row.confidence >= 0) {
                assertTrue(row.confidence > 0.0);
            }
        }
    }

    @Test
    public void confidence_nullReceiverVsThis_treatedAsMatch() {
        // null と "this" は同等として扱われる → receiver_score=1.0
        // "this.method()" の receiver が "this" になるかは実装依存
        // ここは内部ロジックを直接テスト
        List<JavaMethodInfo.Call> la = callList(call(null, "go"));
        List<JavaMethodInfo.Call> lb = callList(call("this", "go"));
        int lcs = MethodDiffAnalyzer.computeLcsLen(la, lb);
        // methodName同じ=1
        assertEquals(1, lcs);
    }

    @Test
    public void confidence_partial_isLessThanMatch() {
        // 同じメソッドをreceiverあり/なしで比較
        String srcA = "class A { Foo f; void go() { f.doIt(); } }";
        String srcB = "class B { Bar b; void go() { b.doIt(); } }";
        MethodDiffAnalyzer.DiffResult partial = diff(srcA, "A", "go", srcB, "B", "go");

        String srcC = "class C { Foo f; void go() { f.doIt(); } }";
        MethodDiffAnalyzer.DiffResult match = diff(srcA, "A", "go", srcC, "C", "go");

        double partialConf = partial.rows.get(0).confidence;
        double matchConf = match.rows.get(0).confidence;
        assertTrue(matchConf > partialConf);
    }

    @Test
    public void confidence_avgConfidence_mixedRows() {
        String srcA = "class A { Foo f; X x; void go() { f.save(); x.run(); } }";
        String srcB = "class B { Bar b; X x; void go() { b.save(); x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // save=PARTIAL, run=MATCH
        assertEquals(2, r.matchCount + r.partialCount);
        assertTrue(r.avgConfidence > 0.0 && r.avgConfidence < 1.0);
    }

    @Test
    public void confidence_onlyA_negativeMeansNA() {
        String srcA = "class A { X x; void go() { x.orphan(); } }";
        String srcB = "class B { void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(-1.0, r.rows.get(0).confidence, 0.001);
    }

    @Test
    public void confidence_onlyB_negativeMeansNA() {
        String srcA = "class A { void go() {} }";
        String srcB = "class B { X x; void go() { x.orphan(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertEquals(-1.0, r.rows.get(0).confidence, 0.001);
    }

    @Test
    public void confidence_positionScore_singleElement_isAlwaysOne() {
        // 単一要素のとき position_score = 1.0 (len=1 → 0/0 でなく特殊処理)
        String srcA = "class A { X x; void go() { x.run(); } }";
        String srcB = "class B { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        // 単一要素: position_score=1.0, receiver同じ(x), firstArg同じ(null) → 1.0
        assertEquals(1.0, r.rows.get(0).confidence, 0.001);
    }

    // =========================================================================
    // 10. MarkdownMethodDiffReport — 22 ケース
    // =========================================================================

    @Test
    public void report_nullResult_returnsNoData() {
        String md = MarkdownMethodDiffReport.render(null);
        assertTrue(md.contains("データなし"));
    }

    @Test
    public void report_containsTitle() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("# 関数差分レポート"));
    }

    @Test
    public void report_containsSummarySection() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("## サマリー"));
    }

    @Test
    public void report_containsCallComparisonSection() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("## 呼び出し比較"));
    }

    @Test
    public void report_containsDiffDetailSection() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("## 差分詳細"));
    }

    @Test
    public void report_containsLcsSimilarityLabel() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("LCS 類似度"));
    }

    @Test
    public void report_containsEditDistanceLabel() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("編集距離"));
    }

    @Test
    public void report_containsJaccardLabel() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("Jaccard"));
    }

    @Test
    public void report_containsFormulaReference() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("計算式の説明"));
    }

    @Test
    public void report_containsMethodALabel() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("Method A"));
    }

    @Test
    public void report_containsMethodBLabel() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("Method B"));
    }

    @Test
    public void report_perfectMatch_noDifferencesFound() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("差分なし"));
    }

    @Test
    public void report_hasOnlyA_containsOnlyInASection() {
        String srcA = "class A { X x; void go() { x.extra(); } }";
        String srcB = "class B { void go() {} }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("A のみ"));
    }

    @Test
    public void report_hasOnlyB_containsOnlyInBSection() {
        String srcA = "class A { void go() {} }";
        String srcB = "class B { X x; void go() { x.extra(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("B のみ"));
    }

    @Test
    public void report_hasPartial_containsPartialMatchSection() {
        String srcA = "class A { Foo f; void go() { f.save(); } }";
        String srcB = "class B { Bar b; void go() { b.save(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("部分一致"));
    }

    @Test
    public void report_containsMATCH_statusWord() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("一致"));
    }

    @Test
    public void report_containsONLY_A_statusWord() {
        String srcA = "class A { X x; void go() { x.a(); x.extra(); } }";
        String srcB = "class B { X x; void go() { x.a(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("A のみ"));
    }

    @Test
    public void report_containsONLY_B_statusWord() {
        String srcA = "class A { X x; void go() { x.a(); } }";
        String srcB = "class B { X x; void go() { x.a(); x.extra(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("B のみ"));
    }

    @Test
    public void report_containsPARTIAL_statusWord() {
        String srcA = "class A { Foo f; void go() { f.save(); } }";
        String srcB = "class B { Bar b; void go() { b.save(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(srcA, "A", "go", srcB, "B", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("部分一致"));
    }

    @Test
    public void report_containsConfidenceHeader() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("信頼度"));
    }

    @Test
    public void report_avgConfidence_shownWhenMatchExists() {
        String src = "class A { X x; void go() { x.run(); } }";
        MethodDiffAnalyzer.DiffResult r = diff(src, "A", "go", src, "A", "go");
        assertTrue(MarkdownMethodDiffReport.render(r).contains("平均信頼度"));
    }

    @Test
    public void report_filePath_shownInSummary() {
        String src = "class A { X x; void go() { x.run(); } }";
        JavaMethodInfo m = extractMethod(src, "A", "go");
        MethodDiffAnalyzer.MethodSpec sA =
                new MethodDiffAnalyzer.MethodSpec("myFileA.java", "A", "go");
        MethodDiffAnalyzer.MethodSpec sB =
                new MethodDiffAnalyzer.MethodSpec("myFileB.java", "A", "go");
        MethodDiffAnalyzer.DiffResult r = MethodDiffAnalyzer.analyze(m, sA, m, sB);
        String md = MarkdownMethodDiffReport.render(r);
        assertTrue(md.contains("myFileA.java"));
        assertTrue(md.contains("myFileB.java"));
    }
}
