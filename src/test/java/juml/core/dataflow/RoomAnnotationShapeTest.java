// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.util.ErrorListener;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Room アノテーションを<b>書き方に依らず</b>読むことの回帰テスト。
 *
 * <p>どちらの欠陥も「よくある 1 つの書き方だけを見ていた」もの。既存のテストがその書き方
 * だけを使っていたため長く生き延びた。ここでは同じ意味を別の記法で書いて、結果が一致する
 * ことを確かめる。</p>
 */
public class RoomAnnotationShapeTest {

    private static List<JavaClassInfo> parse(String... sources) {
        List<JavaClassInfo> all = new ArrayList<>();
        for (String src : sources) {
            all.addAll(JavaStructureExtractor.extract(src, ErrorListener.silent()));
        }
        return all;
    }

    private static final String ENTITY = "package com.x;\n"
            + "@Entity public class Word { @PrimaryKey public String w; }\n";

    private static List<String> entitiesOf(String dbSource) {
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(ENTITY, dbSource));
        assertEquals("データベースは 1 件", 1, r.getDatabases().size());
        return r.getDatabases().get(0).getEntityClasses();
    }

    /**
     * 回帰: 要素 1 個の配列は Java では囲みを省略できる。
     *
     * <p>{@code entities = &#123;…&#125;} の囲みを必須にしていたため、Room 公式 codelab の
     * {@code @Database(entities = Word.class, version = 1)} で entity が 1 件も取れず、
     * ER 図のデータベース枠が空になってエンティティが枠の外に孤立していた
     * (所有関係を出すのが図の主目的なのに、それだけが失われる)。</p>
     */
    @Test
    public void aSingleEntityWithoutBracesIsFound() {
        assertEquals("囲み無しの単一要素も読めること",
                List.of("Word"),
                entitiesOf("package com.x;\n"
                        + "@Database(entities = Word.class, version = 1)\n"
                        + "public abstract class WordRoomDatabase {}\n"));
    }

    /**
     * 非退行: 囲みのある Java の書き方はこれまでどおり。
     *
     * <p>Kotlin の {@code entities = [Word::class]} は Java として解析できないので、
     * ここではなく {@code RoomAnalyzerKotlinTest} 側で押さえる。</p>
     */
    @Test
    public void theBracedFormStillWorks() {
        assertEquals(List.of("Word"), entitiesOf("package com.x;\n"
                + "@Database(entities = {Word.class}, version = 1)\n"
                + "public abstract class Db1 {}\n"));
    }

    /** 非退行: 複数エンティティも全部取れること。 */
    @Test
    public void severalEntitiesAreAllFound() {
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(ENTITY,
                "package com.x;\n@Entity public class Note { @PrimaryKey public String k; }\n",
                "package com.x;\n"
                        + "@Database(entities = {Word.class, Note.class}, version = 2)\n"
                        + "public abstract class Db4 {}\n"));

        assertEquals(List.of("Word", "Note"), r.getDatabases().get(0).getEntityClasses());
    }

    /** {@code version} が {@code entities} より前にあっても取り違えないこと。 */
    @Test
    public void memberOrderDoesNotMatter() {
        assertEquals(List.of("Word"), entitiesOf("package com.x;\n"
                + "@Database(version = 1, entities = Word.class)\n"
                + "public abstract class Db3 {}\n"));
    }

    private static String sqlOf(String daoSource, String op) {
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(daoSource));
        assertEquals(1, r.getDaos().size());
        return r.getDaos().get(0).getOperations().stream()
                .filter(o -> op.equals(o.getMethodName()))
                .map(RoomDao.Operation::getSql)
                .findFirst().orElse(null);
    }

    /**
     * 回帰: SQL は「引数に書かれた文字列リテラルの連結」であること。
     *
     * <p>「開き括弧の直後の最初のリテラル」を取っていたため、{@code value = } を付けると
     * SQL 欄が空になり (SQL の無いメソッドと見分けが付かない)、複数リテラルに分けて書いた
     * SQL は最初のリテラルで<b>黙って切れて</b>いた。どのテーブルを触るか調べる読み手に、
     * 欠損ではなく嘘を返すことになる。</p>
     */
    @Test
    public void theSqlIsEveryStringLiteralInTheAnnotation() {
        String dao = "package com.x;\n"
                + "@Dao public interface WordDao {\n"
                + "  @Query(\"SELECT * FROM word \" + \"WHERE id = :id\")\n"
                + "  Word byId(int id);\n"
                + "  @Query(value = \"SELECT * FROM word\")\n"
                + "  java.util.List<Word> all();\n"
                + "  @Query(\"SELECT * FROM word ORDER BY w\")\n"
                + "  java.util.List<Word> sorted();\n"
                + "}\n";

        assertTrue("連結した SQL が最後まで残ること: " + sqlOf(dao, "byId"),
                sqlOf(dao, "byId").contains("WHERE id = :id"));
        assertEquals("value = 付きでも SQL が取れること",
                "SELECT * FROM word", sqlOf(dao, "all"));
        assertEquals("非退行: 素の 1 リテラル",
                "SELECT * FROM word ORDER BY w", sqlOf(dao, "sorted"));
    }
}
