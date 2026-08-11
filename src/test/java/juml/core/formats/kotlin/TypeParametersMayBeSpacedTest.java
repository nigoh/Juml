// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * クラス名と型パラメータリストの間に<b>空白・改行・コメントを挟める</b>ことの回帰テスト。
 *
 * <p>ラウンド 27 は「ジェネリクスの {@code <} は識別子の直後、比較の {@code <} は空白を
 * 挟む」という綴りによる判定を入れ、走査区間の<b>位置 0</b> だけを型引数の開きとして
 * 特別扱いした。Kotlin の文法は名前と {@code <T>} の間に {@code NL*} を許すので、
 * {@code class Foo <T : Any> : Base()} のように書くと {@code <} が位置 0 でなくなり、
 * 型パラメータ制約のコロンが継承コロンとして読まれる。</p>
 *
 * <p>症状は「消える」だけではない — 図には {@code Comparable<T>>} や {@code Any>} という
 * <b>存在しない箱への継承線</b>が引かれ、実在するスーパークラスへの線は 1 本も引かれない。</p>
 *
 * <p>判定は位置ではなく「まだコードを 1 文字も読んでいないか」で行う。空白を後ろ向きに
 * 読み飛ばす素朴な直し方では、ラウンド 27 が固定した
 * {@code val compact: Boolean = SDK_INT < 21} が<b>再び壊れる</b> ({@code SDK_INT} の
 * 直後の空白を飛ばすと識別子に行き着くため)。両方を同時に固定する。</p>
 */
public class TypeParametersMayBeSpacedTest {

    private static JavaClassInfo only(String source) {
        List<JavaClassInfo> cs = KotlinLightScanner.scan(source, null);
        assertEquals("クラスが 1 つだけ読めること: " + cs, 1, cs.size());
        return cs.get(0);
    }

    private static List<String> fieldNames(JavaClassInfo c) {
        List<String> out = new ArrayList<>();
        for (JavaFieldInfo f : c.getFields()) {
            out.add(f.getName());
        }
        return out;
    }

    /** 名前の後で改行してから型パラメータを書く形 (長い名前でよくある)。 */
    @Test
    public void aNewlineBeforeTheTypeParametersKeepsTheSuperclass() {
        JavaClassInfo c = only("package p\n"
                + "class VeryLongRepositoryName\n"
                + "    <T : Comparable<T>>(val a: T) : BaseRepo() {\n  fun x() {}\n}\n");
        assertEquals("BaseRepo", c.getSuperClass());
        assertEquals(List.of("a"), fieldNames(c));
    }

    /** 空白 1 つだけの形。 */
    @Test
    public void aSpaceBeforeTheTypeParametersKeepsTheSuperclass() {
        JavaClassInfo c = only("package p\n"
                + "class Foo <T : Any>(val a: T) : Base() {\n  fun x() {}\n}\n");
        assertEquals("Base", c.getSuperClass());
    }

    /** コメントを挟む形。 */
    @Test
    public void aCommentBeforeTheTypeParametersKeepsTheSuperclass() {
        JavaClassInfo c = only("package p\n"
                + "class Foo /*g*/ <T : Any>(val a: T) : Base() {\n  fun x() {}\n}\n");
        assertEquals("Base", c.getSuperClass());
    }

    /** interface の実装リストでも同じ。 */
    @Test
    public void aSpacedInterfaceKeepsItsSupertypes() {
        JavaClassInfo c = only("package p\n"
                + "interface Repo <T : Any> : Base<T> {\n  fun x() {}\n}\n");
        assertEquals(List.of("Base<T>"), c.getInterfaces());
    }

    /** 非退行: 空白なしの形 (ラウンド 27 が固定したもの) は今までどおり。 */
    @Test
    public void theUnspacedFormStillWorks() {
        JavaClassInfo c = only("package p\n"
                + "class Box2<T : Comparable<T>>(val a: T) : Base() {\n  fun x() {}\n}\n");
        assertEquals("Base", c.getSuperClass());
    }

    /**
     * 非退行: 引数の既定値に現れる比較は<b>比較のまま</b>であること。
     * 空白を後ろ向きに読み飛ばす直し方をするとここが壊れる。
     */
    @Test
    public void aComparisonInAParameterDefaultIsStillAComparison() {
        JavaClassInfo c = only("package p\n"
                + "class C(\n"
                + "  val compact: Boolean = Build.VERSION.SDK_INT < 21,\n"
                + "  val title: String,\n"
                + "  val icon: Int\n"
                + ")\n");
        assertEquals(List.of("compact", "title", "icon"), fieldNames(c));
    }
}
