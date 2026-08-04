// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin の「波括弧を含む式」まわりの回帰テスト。
 *
 * <p>いずれも {@code = &#123; … &#125;} や {@code by lazy &#123; … &#125;} という、
 * Android / Compose では定型といってよい書き方で起きていた。ラムダやデリゲートの
 * 波括弧をクラス本体の波括弧と取り違えたり、デリゲート式を型名として読んだりすると、
 * クラス図に<b>存在しないメンバーが出る</b>か<b>実在するメンバーが消える</b>。</p>
 */
public class KotlinLambdaAndDelegateTest {

    private static JavaClassInfo scanOne(String src) {
        List<JavaClassInfo> out = KotlinLightScanner.scan(src, null);
        assertEquals("クラスは 1 つ抽出されること: " + out, 1, out.size());
        return out.get(0);
    }

    private static List<String> fieldNames(JavaClassInfo c) {
        return c.getFields().stream().map(JavaFieldInfo::getName).collect(Collectors.toList());
    }

    private static List<String> methodNames(JavaClassInfo c) {
        return c.getMethods().stream().map(JavaMethodInfo::getName).collect(Collectors.toList());
    }

    private static String typeOf(JavaClassInfo c, String field) {
        return c.getFields().stream().filter(f -> field.equals(f.getName()))
                .map(JavaFieldInfo::getType).findFirst().orElse(null);
    }

    /**
     * 回帰: プライマリコンストラクタの既定値に入った {@code &#123;} をクラス本体の
     * 開き波括弧と取り違えないこと。
     *
     * <p>本体の {@code &#123;} をクラス名の直後から探していたため、
     * {@code = &#123;&#125;} の波括弧に当たって本体が空のラムダになり、
     * <b>クラスのフィールドもメソッドも丸ごと消えて</b>いた。
     * Compose の「何もしない既定コールバック」は定型なので、その手のクラスが
     * 軒並み中身の無い箱として描かれる。</p>
     */
    @Test
    public void aBraceInAConstructorDefaultDoesNotSwallowTheClassBody() {
        JavaClassInfo c = scanOne("package com.x\n"
                + "class Foo(val onClick: () -> Unit = {}) {\n"
                + "    val title: String = \"t\"\n"
                + "    fun bar(): Int { return 1 }\n"
                + "}\n");

        assertTrue("コンストラクタ引数は残ること: " + fieldNames(c),
                fieldNames(c).contains("onClick"));
        assertTrue("本体のフィールドが消えないこと: " + fieldNames(c),
                fieldNames(c).contains("title"));
        assertTrue("本体のメソッドが消えないこと: " + methodNames(c),
                methodNames(c).contains("bar"));
    }

    /** 回帰: 既定値ラムダが複数あっても同じこと (Compose の典型形)。 */
    @Test
    public void severalLambdaDefaultsStillLeaveTheBodyIntact() {
        JavaClassInfo c = scanOne("package com.x\n"
                + "class Dlg(val title: String, val onOk: () -> Unit = {},\n"
                + "          val onNo: () -> Unit = {}) {\n"
                + "    var visible: Boolean = false\n"
                + "    fun show() {}\n"
                + "}\n");

        assertTrue("visible が残ること: " + fieldNames(c), fieldNames(c).contains("visible"));
        assertTrue("show() が残ること: " + methodNames(c), methodNames(c).contains("show"));
    }

    /** 非退行: 既定値に波括弧が無い普通のクラスはこれまでどおり。 */
    @Test
    public void anOrdinaryPrimaryConstructorIsUnchanged() {
        JavaClassInfo c = scanOne("package com.x\n"
                + "class Foo(val n: Int = 0) {\n"
                + "    val title: String = \"t\"\n"
                + "    fun bar(): Int { return 1 }\n"
                + "}\n");

        assertTrue(fieldNames(c).containsAll(List.of("n", "title")));
        assertTrue(methodNames(c).contains("bar"));
    }

    /**
     * 回帰: {@code by lazy} のデリゲート式をフィールドの型に混ぜないこと。
     *
     * <p>型の切り出しが {@code by} も {@code lazy} もただの語として飲み込んでいたため、
     * 図の欄が {@code cache : MutableMap&lt;String, Int&gt; by lazy} になっていた。
     * {@code by lazy} は Kotlin でもっとも普通のプロパティ形の 1 つなので、実プロジェクト
     * では型欄が軒並み壊れる。</p>
     */
    @Test
    public void aDelegateExpressionIsNotPartOfTheFieldType() {
        JavaClassInfo c = scanOne("package com.x\n"
                + "class B {\n"
                + "    val cache: MutableMap<String, Int> by lazy { mutableMapOf() }\n"
                + "    fun m() {}\n"
                + "}\n");

        assertEquals("型はデリゲート式を含まないこと",
                "MutableMap<String, Int>", typeOf(c, "cache"));
        assertTrue("同じクラスのメソッドは残ること: " + methodNames(c),
                methodNames(c).contains("m"));
    }

    /**
     * 回帰: ラムダ本体をクラス本体として走査しないこと。
     *
     * <p>マスク対象が「直前が {@code )} の波括弧」と {@code init} だけだったため、
     * {@code = &#123; … &#125;} の中身が走査され、ラムダ内のローカル変数と
     * ローカル関数が<b>クラスのメンバーとして</b>図に出ていた。実在しないメンバーが
     * 増えるのは、消えるのと同じかそれ以上に読み手を誤らせる。</p>
     */
    @Test
    public void localsInsideALambdaAreNotClassMembers() {
        JavaClassInfo c = scanOne("package com.x\n"
                + "class A {\n"
                + "    val handler: () -> Unit = {\n"
                + "        val temp: Int = 1\n"
                + "        fun helper(): Int = temp\n"
                + "    }\n"
                + "    fun m() {}\n"
                + "}\n");

        assertFalse("ラムダ内のローカル変数はメンバーではない: " + fieldNames(c),
                fieldNames(c).contains("temp"));
        assertFalse("ラムダ内のローカル関数はメンバーではない: " + methodNames(c),
                methodNames(c).contains("helper"));
        assertTrue("本物のメソッドは残ること: " + methodNames(c), methodNames(c).contains("m"));
        assertTrue("関数型のプロパティ自体は拾えること: " + fieldNames(c),
                fieldNames(c).contains("handler"));
    }

    /** 回帰: {@code by lazy} ブロックの中身もクラスのメンバーにしないこと。 */
    @Test
    public void localsInsideADelegateBlockAreNotClassMembers() {
        JavaClassInfo c = scanOne("package com.x\n"
                + "class C {\n"
                + "    val conf: String by lazy {\n"
                + "        val seed: Int = 7\n"
                + "        seed.toString()\n"
                + "    }\n"
                + "    fun m() {}\n"
                + "}\n");

        assertFalse("デリゲート内のローカルはメンバーではない: " + fieldNames(c),
                fieldNames(c).contains("seed"));
        assertTrue("委譲プロパティ自体は残ること: " + fieldNames(c), fieldNames(c).contains("conf"));
        assertTrue(methodNames(c).contains("m"));
    }

    /** 非退行: 普通のフィールドとメソッドの抽出は変わらないこと。 */
    @Test
    public void plainMembersAreUnchanged() {
        JavaClassInfo c = scanOne("package com.x\n"
                + "class P {\n"
                + "    private val name: String = \"n\"\n"
                + "    var count: Int = 0\n"
                + "    fun greet(who: String): String { return name }\n"
                + "}\n");

        assertEquals("String", typeOf(c, "name"));
        assertEquals("Int", typeOf(c, "count"));
        assertTrue(methodNames(c).contains("greet"));
    }
}
