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

    /**
     * {@code where} はスーパータイプが<b>有るとき</b>も継承リストに混ざらないこと。
     *
     * <p>ラウンド 21 は「継承コロンが無く where だけ」の形しか救っていなかった。
     * スーパータイプがあると継承コロンが先に見つかるので where 判定に到達せず、
     * 最後の項が where 節を飲み込んで {@code Marker where T : Comparable} という
     * <b>存在しない箱</b>への実装線が引かれ、本物の Marker への線は 1 本も引かれない。</p>
     */
    @Test
    public void aWhereClauseAfterSupertypesIsNotAnInterface() {
        JavaClassInfo sorter = KotlinLightScanner.scan("package p\n"
                + "class Sorter<T>(val name: String) : BaseSorter<T>(), Marker "
                + "where T : Comparable<T> {\n"
                + "  fun sort(items: List<T>): List<T> = items\n}\n"
                + "class BaseSorter<T>\ninterface Marker\n", null).get(0);
        assertEquals("BaseSorter<T>", sorter.getSuperClass());
        assertEquals(List.of("Marker"), sorter.getInterfaces());
    }

    /** 関数にも {@code where} は書ける。戻り値の型に混ぜないこと。 */
    @Test
    public void aWhereClauseOnAFunctionIsNotPartOfTheReturnType() {
        JavaClassInfo a = scanOne("package p\nclass A {\n"
                + "  fun <T> sort(x: List<T>): List<T> where T : Comparable<T> {\n"
                + "    return x\n  }\n}\n");
        assertEquals("List<T>", method(a, "sort").getReturnType());
    }

    /**
     * 名前が修飾子と同じ綴りの enum 定数が消えないこと。
     *
     * <p>ラウンド 21 で enum 定数を共有の前置スキャナへ通したときの回帰。他の 5 経路は
     * 宣言キーワード ({@code val} / {@code fun} …) で必ず止まるので修飾子まで食ってよいが、
     * enum 定数には宣言キーワードが無く<b>名前が先頭のトークン</b>なので、修飾子まで
     * 食うと名前ごと消える。読み飛ばすのは annotation だけでよい。</p>
     */
    @Test
    public void anEnumConstantNamedLikeAModifierIsNotDeleted() {
        assertEquals(List.of("open", "locked", "sealed"),
                scanOne("package p\nenum class Mode { open, locked, sealed }\n")
                        .getEnumConstants());
        assertEquals(List.of("public", "private", "protected", "internal"),
                scanOne("package p\nenum class Vis { public, private, protected, internal }\n")
                        .getEnumConstants());
        JavaClassInfo withArgs = scanOne(
                "package p\nenum class M(val n: Int) { open(1), closed(2) }\n");
        assertEquals(List.of("open", "closed"), withArgs.getEnumConstants());
        assertEquals(List.of("(1)", "(2)"), withArgs.getEnumConstantArgs());
        // 非退行: annotation は従来どおり読み飛ばすこと。
        assertEquals(List.of("data", "ok"),
                scanOne("package p\nenum class E {\n  @Deprecated(\"x\") data,\n  ok\n}\n")
                        .getEnumConstants());
    }

    /**
     * スーパータイプの列にコメントが混ざっても型名として読まないこと。
     *
     * <p>「コメントは宣言ではない」規則はヘッダ検出・プロパティ・関数・引数の 4 経路に
     * 入っていて、スーパータイプの走査だけが生テキストのままだった。読み違えると
     * 改行入りの引用符付きラベルを書き出し、<b>図が 1 枚も描けなくなる</b>。</p>
     */
    @Test
    public void commentsInTheSupertypeListAreNotTypes() {
        JavaClassInfo foo = KotlinLightScanner.scan("package p\n"
                + "interface Real\nopen class Base\n"
                + "class Foo : Base(),\n    // Marker, Other も実装したい\n    Real {\n"
                + "  fun go() {}\n}\n", null).stream()
                .filter(c -> "Foo".equals(c.getSimpleName())).findFirst().orElseThrow();
        assertEquals("Base", foo.getSuperClass());
        assertEquals(List.of("Real"), foo.getInterfaces());

        JavaClassInfo blockComment = KotlinLightScanner.scan("package p\n"
                + "open class Base\nclass Foo /* : Secret, Hidden */ : Base()\n", null)
                .stream().filter(c -> "Foo".equals(c.getSimpleName())).findFirst().orElseThrow();
        assertEquals("Base", blockComment.getSuperClass());
        assertTrue("コメント中の型名を実装にしないこと: " + blockComment.getInterfaces(),
                blockComment.getInterfaces().isEmpty());
    }

    /**
     * enum 定数をコメントの中から拾わないこと。
     *
     * <p>メンバー抽出は非コードマスクで守られているのに、enum 定数だけが生の本体を
     * 読んでいた。コメント中のカンマで定数が入れ替わり、コメント中の {@code ;} で
     * 定数がゼロになる — <b>ソースに無い定数が描かれ、実在する定数が消える</b>。</p>
     */
    @Test
    public void enumConstantsAreNotReadFromComments() {
        assertEquals(List.of("ACTIVE", "CLOSED"),
                scanOne("package p\nenum class Status {\n"
                        + "    // ACTIVE, SUSPENDED は使わない\n    ACTIVE,\n    CLOSED\n}\n")
                        .getEnumConstants());
        assertEquals(List.of("A", "B"),
                scanOne("package p\nenum class E {\n  /** 使い方: E.A; 既定は A */\n"
                        + "  A, B\n}\n").getEnumConstants());
    }

    /**
     * 本体を持たないクラスが、<b>次の宣言の本体</b>を自分のものにしないこと。
     *
     * <p>本体の開き括弧は「ヘッダの後ろで最初に現れる深さ 0 の {@code &#123;}」として
     * 探されていたが、そこで打ち切る条件が無かった。だから {@code interface Listener}
     * のように本体を持たない宣言があると、次に来る関数の本体を丸ごと飲み込み、
     * <b>その関数がクラス図から消えて、無関係なメンバーが Listener の中に生える</b>。
     * 本体の無いインターフェース宣言は Kotlin では普通の書き方である。</p>
     */
    @Test
    public void aBodylessClassDoesNotSwallowTheNextDeclaration() {
        List<JavaClassInfo> out = KotlinLightScanner.scan("package p\n"
                + "interface Listener\n"
                + "class Holder {\n"
                + "  fun register(l: Listener) {}\n"
                + "  val count: Int = 0\n"
                + "}\n", null);

        JavaClassInfo listener = out.stream().filter(c -> "Listener".equals(c.getSimpleName()))
                .findFirst().orElseThrow();
        assertTrue("本体の無い宣言にメンバーを生やさないこと: "
                        + listener.getMethods().stream().map(JavaMethodInfo::getName)
                                .collect(Collectors.toList()),
                listener.getMethods().isEmpty() && listener.getFields().isEmpty());

        JavaClassInfo holder = out.stream().filter(c -> "Holder".equals(c.getSimpleName()))
                .findFirst().orElseThrow();
        assertEquals("register", method(holder, "register").getName());
        assertEquals(List.of("count"), holder.getFields().stream()
                .map(JavaFieldInfo::getName).collect(Collectors.toList()));
    }

    /**
     * 本体を持たないクラスが、<b>後続宣言の型注釈</b>をスーパータイプにしないこと。
     *
     * <p>継承リストの走査には終端が無く、本体の {@code &#123;} が来るまで読み続けていた。
     * 本体が無ければ次のトップレベル宣言まで走り、{@code val settings: Settings} の
     * 型注釈を継承として読む。図には<b>ソースに存在しない継承の矢印</b>が描かれる。</p>
     */
    @Test
    public void aBodylessClassDoesNotInventSupertypesFromWhatFollows() {
        List<JavaClassInfo> out = KotlinLightScanner.scan("package p\n"
                + "class Empty\n"
                + "val settings: Settings = Settings()\n", null);

        JavaClassInfo empty = out.stream().filter(c -> "Empty".equals(c.getSimpleName()))
                .findFirst().orElseThrow();
        assertTrue("継承を作り出さないこと: super=" + empty.getSuperClass()
                        + " ifs=" + empty.getInterfaces(),
                empty.getSuperClass() == null && empty.getInterfaces().isEmpty());
    }

    /**
     * 継承リストの打ち切り走査が<b>括弧の深さ</b>を数えること。
     *
     * <p>コメントと {@code where} は見るようになったのに、深さだけ数えていなかった。
     * 引数に無名オブジェクト式を渡す {@code Base(object : Cb() { … })} は、その
     * {@code &#123;} を本体の始まりと誤認する。{@code ListAdapter} + {@code DiffUtil.ItemCallback}
     * は RecyclerView の定型句なので、<b>普通の Android アプリで継承と本体が同時に壊れる</b>。</p>
     */
    @Test
    public void theSupertypeCutIsBracketDepthAware() {
        JavaClassInfo c = KotlinLightScanner.scan("package p\n"
                + "class TaskAdapter : ListAdapter<Task, VH>(object : DiffUtil.ItemCallback<Task>() {\n"
                + "    override fun areItemsTheSame(a: Task, b: Task) = a.id == b.id\n"
                + "}), Filterable {\n"
                + "  private val count: Int = 0\n"
                + "  fun refresh() {}\n"
                + "}\n", null).stream()
                .filter(x -> "TaskAdapter".equals(x.getSimpleName())).findFirst().orElseThrow();

        assertEquals("ListAdapter<Task, VH>", c.getSuperClass());
        assertEquals(List.of("Filterable"), c.getInterfaces());
        assertEquals("refresh", method(c, "refresh").getName());
        assertEquals(List.of("count"), c.getFields().stream()
                .map(JavaFieldInfo::getName).collect(Collectors.toList()));
    }

    /**
     * enum 定数の<b>引数の文字列</b>を空白へ潰さないこと。
     *
     * <p>コメント除去を「非コードは空白 1 つに畳む」で書いたところ、文字列リテラルまで
     * 畳んでしまった。{@code RED("#ff0000", "赤")} が {@code RED( , )} になり、
     * 引数を表示する設定で<b>図から文言が消える</b>。文字列は宣言の一部なので残す。</p>
     */
    @Test
    public void enumConstantArgumentsKeepTheirStrings() {
        JavaClassInfo c = scanOne("package p\n"
                + "enum class Color(val hex: String, val jp: String) {\n"
                + "  RED(\"#ff0000\", \"赤\"),  // 警告色\n"
                + "  BLUE(\"#0000ff\", \"青\")\n"
                + "}\n");
        assertEquals(List.of("RED", "BLUE"), c.getEnumConstants());
        assertEquals(List.of("hex", "jp"), c.getFields().stream()
                .map(JavaFieldInfo::getName).collect(Collectors.toList()));
    }

    /**
     * スーパークラスの引数に比較演算子 {@code <} が入ってもクラス本体が消えないこと。
     *
     * <p>ヘッダ終端の走査が {@code (} {@code [} と {@code <} を<b>同じ深さ</b>で数えていた。
     * {@code (} は必ず対で閉じるが {@code <} は比較演算子にもなるので、対になる {@code >} が
     * 無いと深さが 0 に戻らず、本体の {@code &#123;} を見つけられない。結果クラスの中身が
     * <b>丸ごと消える</b> — しかも {@code >} 版は偶然通るので非対称だった。
     * {@code Build.VERSION.SDK_INT < N} は Android の定型句である。</p>
     */
    @Test
    public void aComparisonInSuperArgumentsDoesNotSwallowTheBody() {
        JavaClassInfo lt = scanOne("package p\nclass Foo(n: Int) : Base(n < 10) {\n"
                + "  val x: Int = 1\n  fun go(): Int = 2\n}\n");
        assertEquals(List.of("x"), lt.getFields().stream()
                .map(JavaFieldInfo::getName).collect(Collectors.toList()));
        assertEquals("go", method(lt, "go").getName());

        JavaClassInfo android = scanOne("package p\nclass MyDialog(ctx: Context) : Dialog(ctx,\n"
                + "    if (Build.VERSION.SDK_INT < 21) R.style.Old else R.style.New) {\n"
                + "  private val title: String = \"\"\n  fun show2() {}\n}\n");
        assertEquals(List.of("title"), android.getFields().stream()
                .map(JavaFieldInfo::getName).collect(Collectors.toList()));
        assertEquals("show2", method(android, "show2").getName());

        // 非退行: 本物のジェネリクスは従来どおり読めること。
        JavaClassInfo generic = scanOne("package p\n"
                + "class Box<T : Comparable<T>>(val v: T) : Base() {\n  fun get(): T = v\n}\n");
        assertEquals("Base", generic.getSuperClass());
        assertEquals("get", method(generic, "get").getName());
    }

    /**
     * 二次コンストラクタを<b>次の宣言</b>として扱うこと。
     *
     * <p>宣言キーワードの一覧に {@code constructor} が無く、しかも
     * {@code primaryCtorParenAfter} が二次コンストラクタの {@code (} をプライマリのものと
     * 誤認していた。そのため本体を持たない宣言の直後に二次コンストラクタが並ぶと、
     * 委譲の {@code : this(…)} を継承コロンと読んで {@code "this"} という<b>存在しない箱</b>への
     * 継承線を引き、{@code constructor(…) &#123; … &#125;} の本体を取り込んで
     * ローカル変数をフィールドに仕立てていた。{@code object} / {@code interface} は
     * 文法上プライマリコンストラクタを持てないので、直後の {@code constructor} は
     * 必ず囲みクラスのものである。</p>
     */
    @Test
    public void aSecondaryConstructorIsTheNextDeclaration() {
        for (String kw : List.of("object", "class", "interface")) {
            JavaClassInfo inner = KotlinLightScanner.scan("package p\nclass Outer {\n"
                    + "  " + kw + " Inner\n\n"
                    + "  constructor(x: Int) : this(x, 0)\n\n"
                    + "  val real: String = \"\"\n}\n", null).stream()
                    .filter(c -> "Inner".equals(c.getSimpleName())).findFirst().orElseThrow();
            assertEquals(kw + ": 委譲を継承として読まないこと", null, inner.getSuperClass());
            assertTrue(kw + ": 実装線も引かないこと: " + inner.getInterfaces(),
                    inner.getInterfaces().isEmpty());
        }

        JavaClassInfo obj = KotlinLightScanner.scan("package p\nclass Outer {\n"
                + "  object Inner\n\n  constructor(x: Int) { val local: Int = x }\n\n"
                + "  val real: String = \"\"\n}\n", null).stream()
                .filter(c -> "Inner".equals(c.getSimpleName())).findFirst().orElseThrow();
        assertTrue("コンストラクタのローカル変数をフィールドにしないこと: " + obj.getFields(),
                obj.getFields().isEmpty());

        // 非退行: プライマリの `private constructor(…)` は従来どおり読めること。
        JavaClassInfo primary = scanOne(
                "package p\nclass A private constructor(val id: Long) : Base(), Runnable\n");
        assertEquals("Base", primary.getSuperClass());
        assertEquals(List.of("Runnable"), primary.getInterfaces());
        assertEquals(List.of("id"), primary.getFields().stream()
                .map(JavaFieldInfo::getName).collect(Collectors.toList()));
    }
}
