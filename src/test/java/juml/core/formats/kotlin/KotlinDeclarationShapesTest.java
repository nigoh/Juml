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
import static org.junit.Assert.assertTrue;

/**
 * 「宣言の形が変わっても答えは変わらない」ことの回帰テスト。
 *
 * <p>ここに並ぶのはすべて<b>同じ失敗</b>である — ある規則を 1 つの経路に適用して、
 * その兄弟経路に適用していない。enum の定数だけが前置を自前の正規表現で読み、
 * 関数の型パラメータだけが入れ子を数え上げ、{@code where} 節を片方の走査だけが知り、
 * 引数の名前だけが「修飾子の綴り一覧」に依存していた。いずれも図から要素が
 * <b>黙って消える</b>か、実在しないものが生えるかのどちらかになる。</p>
 */
public class KotlinDeclarationShapesTest {

    private static JavaClassInfo scanOne(String src) {
        List<JavaClassInfo> out = KotlinLightScanner.scan(src, null);
        assertEquals("クラスは 1 つ抽出されること: " + names(out), 1, out.size());
        return out.get(0);
    }

    private static List<String> names(List<JavaClassInfo> cs) {
        return cs.stream().map(JavaClassInfo::getSimpleName).collect(Collectors.toList());
    }

    private static JavaMethodInfo method(JavaClassInfo c, String name) {
        return c.getMethods().stream().filter(m -> name.equals(m.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("メソッド " + name + " が無い: "
                        + c.getMethods().stream().map(JavaMethodInfo::getName)
                                .collect(Collectors.toList())));
    }

    /**
     * enum 定数の annotation は他の宣言と同じ走査で読むこと。
     *
     * <p>enum entry だけが 0 段の数え上げを持っていて、annotation の引数に入れ子括弧や
     * 文字列中の {@code )} があると<b>失敗せずに</b> annotation 名の最後の 1 文字が
     * 定数名として採用されていた ({@code RUNNING} が {@code d} になって図に出た)。
     * {@code @Deprecated(…, ReplaceWith(…))} は Kotlin 標準のイディオムである。</p>
     */
    @Test
    public void anAnnotatedEnumConstantKeepsItsName() {
        JavaClassInfo c = scanOne("package p\n"
                + "enum class State {\n"
                + "  @Deprecated(\"use ACTIVE\", ReplaceWith(\"ACTIVE\")) RUNNING,\n"
                + "  @SerializedName(\"n/a (unknown)\") UNKNOWN,\n"
                + "  ACTIVE\n"
                + "}\n");
        assertEquals(List.of("RUNNING", "UNKNOWN", "ACTIVE"), c.getEnumConstants());
    }

    /** {@code value class} と {@code fun interface} でも annotation が落ちないこと。 */
    @Test
    public void modifierSpellingDoesNotHideTheAnnotation() {
        assertEquals(List.of("@JvmInline"),
                scanOne("package p\n@JvmInline\nvalue class Money(val amount: Long)\n")
                        .getAnnotations());
        assertEquals(List.of("@FunctionalInterface"),
                scanOne("package p\n@FunctionalInterface\n"
                        + "fun interface Handler {\n  fun handle(e: String)\n}\n")
                        .getAnnotations());
        // 非退行: fun は関数の宣言キーワードでもある。前置として食うと関数の
        // annotation が全滅するので、`fun interface` のときだけ修飾子として扱う。
        JavaClassInfo dao = scanOne("package p\n@Dao\ninterface D {\n"
                + "  @Query(\"SELECT COUNT(*) FROM u\")\n  fun count(): Int\n}\n");
        assertEquals(List.of("@Query(\"SELECT COUNT(*) FROM u\")"),
                method(dao, "count").getAnnotations());
    }

    /**
     * 引数の名前が修飾子と同じ綴りでも、annotation が付いていても消えないこと。
     *
     * <p>{@code @Body data: Payload} は Retrofit の定型。名前が消えるとシグネチャからも
     * 消えるので、クラス図には<b>実在しない引数列</b>が出る (欠損ではなく誤り)。</p>
     */
    @Test
    public void aParameterNamedLikeAModifierSurvivesItsAnnotation() {
        JavaMethodInfo upload = method(scanOne("package p\ninterface Api {\n"
                + "    @POST(\"u\")\n"
                + "    suspend fun upload(@Body data: Payload, @Query(\"t\") token: String)"
                + ": Response\n}\n"), "upload");
        assertEquals(List.of("data", "token"), upload.getParameterNames());
        assertEquals(List.of("Payload", "String"), upload.getParameterTypes());

        JavaMethodInfo insertAll = method(scanOne("package p\ninterface Dao {\n"
                + "  @Insert suspend fun insertAll(vararg data: User)\n}\n"), "insertAll");
        assertEquals(List.of("data"), insertAll.getParameterNames());
        assertEquals(List.of("User"), insertAll.getParameterTypes());
    }

    /**
     * {@code Foo::class} の {@code class} を宣言と読まないこと。
     *
     * <p>除外文字を数え上げていて {@code :} が漏れていた。直後に本物の宣言が来ると
     * 名前 {@code class} のクラスがその本体ごと吸い取り、<b>本物は二度とマッチせず
     * 図から消える</b>。</p>
     */
    @Test
    public void aClassReferenceIsNotADeclaration() {
        JavaClassInfo next = scanOne(
                "package p\nval k = Foo::class\nclass Next { val a: Int = 1 }\n");
        assertEquals("Next", next.getSimpleName());

        List<JavaClassInfo> reg = KotlinLightScanner.scan(
                "package p\nclass Reg { val m = mapOf(A::class to 1) }\n", null);
        assertEquals("空の箱が生えないこと: " + names(reg), List.of("Reg"), names(reg));
    }

    /**
     * {@code where} の型制約はスーパータイプではないこと。
     *
     * <p>読み違えると図に存在しない実装線が引かれる。{@code where K : Any} なら
     * {@code Any} を、複数制約なら {@code T : Cloneable} という架空のインタフェース名を
     * 継承リストに並べていた。</p>
     */
    @Test
    public void aTypeConstraintIsNotASupertype() {
        JavaClassInfo sorter = scanOne("package p\n"
                + "class Sorter<T> where T : Comparable<T> {\n"
                + "  fun sort(items: List<T>): List<T> = items\n}\n");
        assertTrue("where の制約を継承にしないこと: " + sorter.getInterfaces(),
                sorter.getInterfaces().isEmpty());
        assertEquals(null, sorter.getSuperClass());

        // 非退行: 本物のスーパータイプは従来どおり取れること。
        JavaClassInfo impl = scanOne("package p\nclass Impl : Base(), Runnable {\n"
                + "  fun go() {}\n}\n");
        assertEquals("Base", impl.getSuperClass());
        assertEquals(List.of("Runnable"), impl.getInterfaces());
    }

    /** 制約付きの型パラメータを持つ関数が消えないこと (入れ子は数えず走査する)。 */
    @Test
    public void aConstrainedGenericFunctionIsNotDropped() {
        JavaClassInfo util = scanOne("package p\nclass Util {\n"
                + "  fun <T : Comparable<T>> maxOf(a: T, b: T): T = a\n"
                + "  fun <T> firstOf(list: List<T>): T? = null\n"
                + "  fun plain(): Int = 1\n}\n");
        assertEquals(List.of("maxOf", "firstOf", "plain"),
                util.getMethods().stream().map(JavaMethodInfo::getName)
                        .collect(Collectors.toList()));
        assertEquals(List.of("a", "b"), method(util, "maxOf").getParameterNames());
    }

    /** 非退行: use-site target 付きの ctor 引数は従来どおり取れること。 */
    @Test
    public void aUseSiteTargetOnACtorParameterStillReads() {
        JavaClassInfo user = scanOne("package com.x\ndata class User(\n"
                + "  val id: Long,\n"
                + "  @get:NotificationMode @ColumnInfo(name = \"mode\") var mode: Int = 0\n"
                + ")\n");
        assertEquals(2, user.getFields().size());
        JavaFieldInfo mode = user.getFields().get(1);
        assertEquals("mode", mode.getName());
        assertEquals("Int", mode.getType());
        assertTrue("use-site target を名前として記録しないこと: " + mode.getAnnotations(),
                mode.getAnnotations().stream().anyMatch(s -> s.startsWith("@NotificationMode")));
    }
}
