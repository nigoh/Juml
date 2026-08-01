// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import juml.core.formats.uml.JavaClassInfo;
import juml.util.ErrorListener;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin 走査の 2 つの回帰を守るテスト。
 *
 * <p>1. 関数型 {@code (Int) -> Unit} の {@code ->} をジェネリクスの閉じと誤認して括弧の
 * 深さが崩れ、プライマリコンストラクタ引数の {@code 名前: 型} を継承コロンと取り違えて
 * 偽のスーパータイプ ({@code "String)"} や改行入りの {@code "String\n)"}) を生んでいた。
 * 後者は PlantUML テキストに生の改行を持つ引用名を出力するため、図全体が描画失敗した。</p>
 *
 * <p>2. ネスト型に {@code enclosingClass} を設定しておらず、{@code com.x.Outer.State} が
 * {@code com.x.State} になっていた。同名ネスト型 (画面ごとの State/UiState など) が同じ
 * 完全修飾名へ衝突し、クラス図で 1 つの箱に統合されて別クラスのメンバが混ざっていた。</p>
 */
public class KotlinLightScannerNestingAndLambdaTest {

    private static JavaClassInfo byName(List<JavaClassInfo> infos, String simpleName) {
        for (JavaClassInfo c : infos) {
            if (simpleName.equals(c.getSimpleName())) {
                return c;
            }
        }
        throw new AssertionError(simpleName + " が抽出されていない: " + infos);
    }

    // --- 1. 関数型パラメータ (`->`) の深さ計算 ------------------------------------

    @Test
    public void lambdaParamFollowedByAnotherParam_doesNotInventSupertype() {
        String src = "package com.x\n"
                + "data class User(val id: Long, val cb: (Int) -> Unit, val name: String)\n";
        JavaClassInfo c = byName(KotlinLightScanner.scan(src, ErrorListener.silent()), "User");
        assertTrue("関数型の後ろの引数から偽のインタフェースを作らないこと: " + c.getInterfaces(),
                c.getInterfaces().isEmpty());
        assertNull("偽のスーパークラスを作らないこと: " + c.getSuperClass(), c.getSuperClass());
    }

    @Test
    public void multilineLambdaParam_doesNotProduceNewlineInSupertype() {
        // 複数行整形 (data class の一般的な書き方)。以前は "String\n)" が
        // スーパータイプに入り、PlantUML の引用名に生の改行が出て図全体が描画失敗した。
        String src = "package com.x\n"
                + "data class User(\n"
                + "  val id: Long,\n"
                + "  val cb: (Int) -> Unit,\n"
                + "  val name: String\n"
                + ")\n";
        JavaClassInfo c = byName(KotlinLightScanner.scan(src, ErrorListener.silent()), "User");
        assertTrue("複数行でも偽のインタフェースを作らないこと: " + c.getInterfaces(),
                c.getInterfaces().isEmpty());
        assertNull("複数行でも偽のスーパークラスを作らないこと: " + c.getSuperClass(),
                c.getSuperClass());
    }

    @Test
    public void lambdaParamBeforeRealSupertype_keepsRealSupertype() {
        // 本物の継承が偽のスーパータイプに置き換わって黙って消えるのを防ぐ (描画は
        // 成功してしまうため気づけない類の劣化)。
        String src = "package com.x\n"
                + "open class Base\n"
                + "class Adapter(val onSelect: (Int) -> Unit, val tag: String) : Base()\n";
        JavaClassInfo c = byName(KotlinLightScanner.scan(src, ErrorListener.silent()), "Adapter");
        assertEquals("本物の継承が保たれること", "Base", c.getSuperClass());
    }

    @Test
    public void lambdaParamFields_areStillExtracted() {
        String src = "package com.x\n"
                + "class Holder(val cb: (Int) -> Unit, val label: String)\n";
        JavaClassInfo c = byName(KotlinLightScanner.scan(src, ErrorListener.silent()), "Holder");
        assertEquals("プライマリコンストラクタのプロパティが 2 つ取れること",
                2, c.getFields().size());
    }

    // --- 2. ネスト型の enclosingClass -------------------------------------------

    @Test
    public void nestedClass_getsEnclosingClassAndQualifiedName() {
        String src = "package com.x\n"
                + "class Outer {\n"
                + "  class State { val v: Int = 0 }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo outer = byName(infos, "Outer");
        JavaClassInfo state = byName(infos, "State");
        assertNull("トップレベルの enclosing は null", outer.getEnclosingClass());
        assertEquals("Outer", state.getEnclosingClass());
        assertEquals("com.x.Outer.State", state.getQualifiedName());
    }

    @Test
    public void sameNamedNestedClasses_getDistinctQualifiedNames() {
        // 回帰: どちらも com.x.State になり、クラス図で 1 つの箱へ統合されていた。
        String src = "package com.x\n"
                + "class ScreenA {\n"
                + "  class State { val v: Int = 0 }\n"
                + "}\n"
                + "class ScreenB {\n"
                + "  class State { val w: String = \"\" }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        long distinct = infos.stream()
                .filter(c -> "State".equals(c.getSimpleName()))
                .map(JavaClassInfo::getQualifiedName)
                .distinct()
                .count();
        assertEquals("同名ネスト型が別々の完全修飾名になること", 2, distinct);
        assertTrue(infos.stream().anyMatch(
                c -> "com.x.ScreenA.State".equals(c.getQualifiedName())));
        assertTrue(infos.stream().anyMatch(
                c -> "com.x.ScreenB.State".equals(c.getQualifiedName())));
    }

    @Test
    public void deeplyNestedClass_buildsDottedEnclosingChain() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  class B {\n"
                + "    class C { val v: Int = 0 }\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals("A.B", byName(infos, "C").getEnclosingClass());
        assertEquals("com.x.A.B.C", byName(infos, "C").getQualifiedName());
    }

    @Test
    public void classAfterNestedBody_isTopLevelAgain() {
        // 本体を抜けたら enclosing を捨てること (スタックの pop)。
        String src = "package com.x\n"
                + "class Outer {\n"
                + "  class Inner { val v: Int = 0 }\n"
                + "}\n"
                + "class Sibling { val w: Int = 0 }\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals("Outer", byName(infos, "Inner").getEnclosingClass());
        assertNull("本体を抜けた後のクラスはトップレベル",
                byName(infos, "Sibling").getEnclosingClass());
        assertEquals("com.x.Sibling", byName(infos, "Sibling").getQualifiedName());
    }
}
