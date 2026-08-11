// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「入れ子の深さの規則が<b>全走査に届いている</b>」ことの回帰テスト。
 *
 * <p>ラウンド 26 は深さの数え方を {@code KotlinHeaderScan.Depth} 1 つへ統合し、
 * その javadoc に「比較が現れるのは必ず括弧の内側なので、山括弧は括弧の外にいるときだけ
 * 数える」と根拠を書いた。<b>その前提が成り立つのはヘッダ区間の走査だけ</b>だった —
 * ヘッダ区間はプライマリコンストラクタの {@code (} を含んだまま読むので比較は必ず
 * 深さ 1 以上に現れるが、{@link KotlinHeaderScan#splitTopLevelCommas} には
 * <b>括弧を先に剥がした</b>本文を渡す経路が 2 つある
 * ({@code extractPrimaryCtorFields} と {@code parseParameters})。</p>
 *
 * <p>そこでは {@code val compact: Boolean = SDK_INT < 21} の比較が深さ 0 に現れる。
 * 閉じる {@code >} は来ないので以降のトップレベル判定がすべて偽になり、後ろの引数が
 * <b>1 つ残らず消える</b>。しかも消えるだけでなく、図には {@code f(a: Int)} という
 * <b>誤ったシグネチャ</b>が出る。{@code >=} の綴りなら偶然通るという非対称も同じ。</p>
 *
 * <p>もう 1 つ、{@link KotlinBlockMask#topLevelSemicolon} はこのパッケージで唯一
 * {@code skipNonCode} を通らない走査だった。{@code codeOnly} は enum 定数の引数を
 * 保つために<b>文字列を原文のまま残す</b>ので、{@code RPAREN(")")} のような
 * トークン種別 enum の {@code ")"} が深さを狂わせ、本物の {@code ;} を見失って
 * メンバー宣言を最後の定数の引数として飲み込んでいた。</p>
 */
public class DepthRuleReachesEveryScanTest {

    private static List<String> fieldNames(JavaClassInfo c) {
        List<String> out = new ArrayList<>();
        for (JavaFieldInfo f : c.getFields()) {
            out.add(f.getName());
        }
        return out;
    }

    private static JavaClassInfo only(String source) {
        List<JavaClassInfo> cs = KotlinLightScanner.scan(source, null);
        assertEquals("クラスが 1 つだけ読めること: " + cs, 1, cs.size());
        return cs.get(0);
    }

    /** 括弧を剥がした本文に来る比較。Android のカスタム View の定型そのもの。 */
    @Test
    public void aComparisonInAParameterDefaultDoesNotDropTheRestOfTheParameters() {
        JavaClassInfo c = only("package p\n"
                + "class C(\n"
                + "  val compact: Boolean = Build.VERSION.SDK_INT < 21,\n"
                + "  val title: String,\n"
                + "  val icon: Int\n"
                + ")\n");
        assertEquals("比較の後ろの引数も残ること",
                List.of("compact", "title", "icon"), fieldNames(c));
    }

    /** 関数引数も同じ経路 (括弧を剥がして splitTopLevelCommas へ渡す)。 */
    @Test
    public void functionParametersSurviveAComparisonDefault() {
        JavaClassInfo c = only("package p\n"
                + "class D {\n"
                + "  fun f(a: Int = x < 3, b: String, c: Long) {}\n"
                + "}\n");
        JavaMethodInfo f = c.getMethods().get(0);
        assertEquals("引数名が 3 つとも残ること",
                List.of("a", "b", "c"), f.getParameterNames());
        assertEquals("引数型も 3 つとも残ること",
                List.of("Int", "String", "Long"), f.getParameterTypes());
    }

    /** {@code <=} も比較。{@code >} が来ないので同じ壊れ方をする。 */
    @Test
    public void aLessOrEqualDefaultIsAlsoAComparison() {
        assertEquals("`<=` でも分割できること", 2,
                KotlinHeaderScan.splitTopLevelCommas("a = n <= 10, b: String").size());
    }

    /**
     * 非退行: ジェネリクスのカンマは<b>今までどおり</b>守られること。
     * 比較を弾くために山括弧を数えるのをやめてしまうと、{@code Map<String, Int>} が
     * 2 つの引数に割れて型が壊れる。
     */
    @Test
    public void genericArgumentsStillProtectTheirCommas() {
        assertEquals("Map<String, Int> は 1 要素のまま", 2,
                KotlinHeaderScan.splitTopLevelCommas("m: Map<String, Int>, n: Int").size());
        assertEquals("入れ子ジェネリクスも 1 要素のまま", 2,
                KotlinHeaderScan.splitTopLevelCommas(
                        "x: List<Pair<String, Int>>, y: Int").size());

        JavaClassInfo c = only("package p\nclass G(val m: Map<String, Int>, val n: Int)\n");
        assertEquals("ジェネリクス引数の型が割れないこと",
                List.of("m", "n"), fieldNames(c));
        assertEquals("型引数がそのまま保たれること",
                "Map<String, Int>", c.getFields().get(0).getType());
    }

    /** 非退行: 括弧に包まれた比較は以前から通っていた形。 */
    @Test
    public void aParenthesizedComparisonKeepsWorking() {
        assertEquals(2, KotlinHeaderScan.splitTopLevelCommas(
                "a = (n < 10), b: String").size());
        assertEquals(2, KotlinHeaderScan.splitTopLevelCommas(
                "a = n >= 10, b: String").size());
    }

    /** enum 定数の引数に括弧文字が入ると、メンバー区切りの {@code ;} を見失っていた。 */
    @Test
    public void aBracketInsideAnEnumConstantStringDoesNotSwallowTheMembers() {
        assertTrue("文字列の中の括弧で深さが狂わないこと",
                KotlinBlockMask.topLevelSemicolon("RPAREN(\")\"), LBRACE(\"{\"); fun f() = 1") > 0);

        JavaClassInfo c = only("package p\n"
                + "enum class Tok(val t: String) {\n"
                + "  RPAREN(\")\"), LBRACE(\"{\");\n"
                + "  fun pretty() = t\n"
                + "}\n");
        assertEquals("定数が 2 つとも読めること",
                List.of("RPAREN", "LBRACE"), c.getEnumConstants());
        List<String> methods = new ArrayList<>();
        for (JavaMethodInfo m : c.getMethods()) {
            methods.add(m.getName());
        }
        assertTrue("メンバーが最後の定数の引数へ飲み込まれないこと: " + methods,
                methods.contains("pretty"));
    }
}
