// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import juml.core.dataflow.RoomAnalyzer;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「宣言の前置 (annotation と修飾子) をどう読むか」は<b>1 つの規則</b>である、という回帰テスト。
 *
 * <p>Kotlin の宣言は 4 か所に書ける — クラスヘッダ / クラス本体のプロパティ / 関数 /
 * プライマリコンストラクタ引数。以前はこの 4 経路 (+ 関数引数リスト + annotation 分解) が
 * それぞれ別の正規表現を持ち、annotation 引数の括弧を<b>何段まで許すか</b>が経路ごとに
 * 違っていた ({@code \([^)]*\)} = 0 段 / {@code (?:[^()]|\([^()]*\))*} = 1 段)。
 * その結果、<b>まったく同じ annotation を書く場所によって答えが変わる</b>:
 * {@code @Query("SELECT COUNT(*) …")} は DAO のメソッドを 1 件も出さず、
 * {@code @Entity(foreignKeys = [ForeignKey(… arrayOf("id") …)])} はエンティティごと
 * ER 図から消えた。ここで固定するのは件数ではなく<b>経路をまたいだ一致</b>。</p>
 */
public class KotlinDeclarationPrefixTest {

    private static JavaClassInfo scanOne(String src) {
        List<JavaClassInfo> out = KotlinLightScanner.scan(src, null);
        assertEquals("クラスは 1 つ抽出されること: " + out, 1, out.size());
        return out.get(0);
    }

    private static List<String> fieldNames(JavaClassInfo c) {
        return c.getFields().stream().map(JavaFieldInfo::getName).collect(Collectors.toList());
    }

    private static JavaFieldInfo field(JavaClassInfo c, String name) {
        return c.getFields().stream().filter(f -> name.equals(f.getName()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "フィールド " + name + " が無い: " + fieldNames(c)));
    }

    private static JavaMethodInfo method(JavaClassInfo c, String name) {
        return c.getMethods().stream().filter(m -> name.equals(m.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("メソッド " + name + " が無い"));
    }

    /**
     * 括弧を含む引数を持つ annotation は、<b>どの経路に書いても</b>同じように読めること。
     *
     * <p>これがこのテストの本体。1 経路だけ直しても他が取り残されるという失敗を、
     * 4 経路を 1 つの表にして固定する。</p>
     */
    @Test
    public void theSameAnnotationReadsTheSameWhereverItIsWritten() {
        String ann = "@Tag(sql = \"SELECT COUNT(*) FROM t\", def = \"datetime('now')\")";

        JavaClassInfo onHeader = scanOne("package p\n" + ann + "\nclass A(val id: Long)\n");
        assertEquals("クラスヘッダ", List.of(ann), onHeader.getAnnotations());

        JavaClassInfo onProperty = scanOne(
                "package p\nclass A {\n    " + ann + "\n    val id: Long = 0\n}\n");
        assertEquals("本体プロパティ", List.of(ann), field(onProperty, "id").getAnnotations());

        JavaClassInfo onFunction = scanOne(
                "package p\nclass A {\n    " + ann + "\n    fun run(): Int = 0\n}\n");
        assertEquals("関数", List.of(ann), method(onFunction, "run").getAnnotations());

        JavaClassInfo onCtorParam = scanOne(
                "package p\nclass A(\n    " + ann + "\n    val id: Long\n)\n");
        assertEquals("ctor 引数", List.of(ann), field(onCtorParam, "id").getAnnotations());
    }

    /**
     * annotation 引数の入れ子は<b>段数を数えない</b>。2 段でもクラスの annotation は残る。
     *
     * <p>{@code arrayOf(...)} を annotation の中で使うのは Kotlin 1.2 以前の書き方だが、
     * 現役の Android コードに大量にある。1 段しか許さない実装だとクラスの annotation が
     * 空になり、{@link RoomAnalyzer} がエンティティとして扱わない = ER 図からテーブルごと
     * 消える (エラーも警告も出ない)。</p>
     */
    @Test
    public void nestedAnnotationArgumentsDoNotHideTheEntity() {
        String twoLevels = "package p\n"
                + "@Entity(tableName = \"users\", indices = [Index(value = arrayOf(\"name\"), "
                + "unique = true)])\n"
                + "data class User(val id: Long)\n";
        String oneLevel = "package p\n"
                + "@Entity(tableName = \"users\", indices = [Index(value = [\"name\"], "
                + "unique = true)])\n"
                + "data class User(val id: Long)\n";

        for (String src : List.of(twoLevels, oneLevel)) {
            JavaClassInfo c = scanOne(src);
            assertTrue("@Entity が残ること: " + c.getAnnotations(),
                    c.getAnnotations().stream().anyMatch(a -> a.startsWith("@Entity")));
            assertEquals("エンティティとして数えられること",
                    1, new RoomAnalyzer().analyze(List.of(c)).getEntities().size());
        }
    }

    /**
     * SQL の中の {@code )} で annotation を切らないこと (Room の DAO で最も普通の形)。
     */
    @Test
    public void parenthesesInsideAStringDoNotEndTheAnnotation() {
        JavaClassInfo dao = scanOne("package p\n"
                + "@Dao\n"
                + "interface UserDao {\n"
                + "    @Query(\"SELECT COUNT(*) FROM user\")\n"
                + "    fun count(): Int\n"
                + "    @Query(\"SELECT * FROM user WHERE id IN (:ids)\")\n"
                + "    fun some(ids: List<Long>): List<User>\n"
                + "}\n");
        assertEquals("2 メソッドとも @Query を持つこと",
                2, dao.getMethods().stream()
                        .filter(m -> !m.getAnnotations().isEmpty()).count());
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(List.of(dao));
        assertEquals("DAO は 1 つ", 1, r.getDaos().size());
        assertEquals("2 つの操作がどちらも QUERY として出ること",
                2, r.getDaos().get(0).getOperations().size());
    }

    /**
     * プライマリコンストラクタ引数の型は、クラス本体のプロパティと<b>同じ読み方</b>であること。
     *
     * <p>ctor 側だけが「型も既定値も 1 本の正規表現で {@code matches()}」だったため、
     * 型や既定値が改行を跨いだ瞬間にそのプロパティが警告も無く消えていた。同じ宣言を
     * クラス本体に書けば通る、という書き場所依存の欠落。</p>
     */
    @Test
    public void theCtorParameterTypeIsReadLikeABodyProperty() {
        JavaClassInfo ctor = scanOne("package p\n"
                + "class Screen(\n"
                + "    val title: String,\n"
                + "    val onClick: (\n"
                + "        item: Item\n"
                + "    ) -> Unit,\n"
                + "    val id: Long\n"
                + ")\n");
        assertEquals("折り返した関数型の引数が消えないこと",
                List.of("title", "onClick", "id"), fieldNames(ctor));

        JavaClassInfo body = scanOne("package p\n"
                + "class Screen {\n"
                + "    val title: String = \"\"\n"
                + "    val onClick: (\n"
                + "        item: Item\n"
                + "    ) -> Unit = {}\n"
                + "    val id: Long = 0\n"
                + "}\n");
        assertEquals("本体と ctor で型が一致すること",
                field(body, "onClick").getType(), field(ctor, "onClick").getType());
    }

    /** 複数行のラムダ既定値でその引数が消えないこと (Compose / data class の定番)。 */
    @Test
    public void aMultiLineDefaultValueDoesNotDeleteTheParameter() {
        JavaClassInfo c = scanOne("package p\n"
                + "class Screen(\n"
                + "    val onClick: () -> Unit = {\n"
                + "        doThing()\n"
                + "    },\n"
                + "    val id: Long = 0\n"
                + ")\n");
        assertEquals(List.of("onClick", "id"), fieldNames(c));
        assertEquals("() -> Unit", field(c, "onClick").getType());
    }

    /** 行末コメントは型の一部ではない — ctor 引数でも本体プロパティと同じであること。 */
    @Test
    public void aTrailingCommentIsNotPartOfTheCtorParameterType() {
        JavaClassInfo c = scanOne("package p\n"
                + "class User(val id: Long, val name: String // display name\n"
                + ")\n");
        assertEquals("String", field(c, "name").getType());
    }

    /**
     * 引数リストのどこにコメントを書いても、引数が 1 つも消えないこと。
     *
     * <p>前の引数の行末コメントはカンマの<b>後ろ</b>に付くので次の引数の先頭に来る。
     * 消えた引数はシグネチャからも消えるため、クラス図には実在しない引数列が出る —
     * 欠損ではなく<b>誤り</b>になる。</p>
     */
    @Test
    public void aCommentInAParameterListNeverDeletesAParameter() {
        String trailing = "package p\nclass Repo {\n"
                + "    fun save(\n        id: Long,   // primary key\n"
                + "        name: String\n    ) { }\n}\n";
        String leading = "package p\nclass Repo {\n"
                + "    fun save(\n        // primary key\n        id: Long,\n"
                + "        name: String\n    ) { }\n}\n";
        for (String src : List.of(trailing, leading)) {
            JavaMethodInfo save = method(scanOne(src), "save");
            assertEquals(src, List.of("id", "name"), save.getParameterNames());
            assertEquals(src, List.of("Long", "String"), save.getParameterTypes());
        }
    }

    /** 引数名が修飾子と同じ綴りでも消えないこと (前置の走査に食われてはいけない)。 */
    @Test
    public void aParameterNamedLikeAModifierIsStillRead() {
        JavaMethodInfo m = method(scanOne(
                "package p\nclass A {\n    fun put(data: String, value: Int) { }\n}\n"), "put");
        assertEquals(List.of("data", "value"), m.getParameterNames());
        assertEquals(List.of("String", "Int"), m.getParameterTypes());
    }
}
