// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlErDiagram} のユニットテスト。
 *
 * <p>Room {@code @Entity} クラス群から ER 図 (PlantUML entity ブロック) を
 * 生成できることと、列の型表記 (特にジェネリクス) が壊れず表示されることを確認する。</p>
 */
public class PlantUmlErDiagramTest {

    private static List<JavaClassInfo> parse(String... sources) {
        List<JavaClassInfo> all = new ArrayList<>();
        for (String src : sources) {
            all.addAll(JavaStructureExtractor.extract(src, ErrorListener.silent()));
        }
        return all;
    }

    @Test
    public void testEmptyResultRendersEmptyDiagram() {
        RoomAnalyzer.Result result = new RoomAnalyzer.Result();
        String puml = PlantUmlErDiagram.render(result);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertFalse(puml, puml.contains("entity \""));
    }

    @Test
    public void testSimpleEntityRendersColumnsAndPrimaryKey() {
        String src = "package com.x;\n"
                + "@Entity(tableName = \"users\")\n"
                + "public class User {\n"
                + "  @PrimaryKey\n"
                + "  public long id;\n"
                + "  public String name;\n"
                + "}\n";
        RoomAnalyzer.Result result = new RoomAnalyzer().analyze(parse(src));
        String puml = PlantUmlErDiagram.render(result);
        assertTrue(puml, puml.contains("entity \"User"));
        assertTrue("primary key marker (*) should be present:\n" + puml,
                puml.contains("* id : long"));
        assertTrue(puml, puml.contains("name : String"));
    }

    @Test
    public void testForeignKeyEdgeRendered() {
        String src = "package com.x;\n"
                + "@Entity(tableName = \"posts\", foreignKeys = {\n"
                + "  @ForeignKey(entity = User.class, parentColumns = \"id\","
                + " childColumns = \"userId\")\n"
                + "})\n"
                + "public class Post {\n"
                + "  @PrimaryKey public long id;\n"
                + "  public long userId;\n"
                + "}\n"
                + "@Entity(tableName = \"users\")\n"
                + "class User {\n"
                + "  @PrimaryKey public long id;\n"
                + "}\n";
        RoomAnalyzer.Result result = new RoomAnalyzer().analyze(parse(src));
        String puml = PlantUmlErDiagram.render(result);
        assertTrue("FK edge should link Post and User entities:\n" + puml,
                puml.contains("}o--||"));
        assertTrue(puml, puml.contains(": FK"));
    }

    // ============================================================
    // 回帰: simpleType がジェネリクスを壊す (List<String> → String>)
    // ============================================================

    @Test
    public void testGenericFieldTypeCollapsesToSimpleGenericNotation() {
        // 修正前: simpleType は型全体の最後のドットで単純に切っていたため、
        // "java.util.List<java.lang.String>" は "java.lang.String" 内の最後のドットで
        // 切られ "String>" に壊れていた (List<...> が丸ごと失われる)。
        // 修正: <...> を分離し、生型と各型引数を再帰的に単純化してから畳む
        // (splitTopLevelArgs でカンマ分割 → List<String> のように正しく再構築)。
        String src = "package com.x;\n"
                + "@Entity(tableName = \"tags\")\n"
                + "public class Tag {\n"
                + "  @PrimaryKey\n"
                + "  public long id;\n"
                + "  public java.util.List<java.lang.String> names;\n"
                + "}\n";
        RoomAnalyzer.Result result = new RoomAnalyzer().analyze(parse(src));
        String puml = PlantUmlErDiagram.render(result);

        assertTrue("names 列は List<String> と表示されるべき:\n" + puml,
                puml.contains("names : List<String>"));
        // 修正前の壊れた表示 ("names : String>") が残っていないこと
        assertFalse("names 列が String> に壊れてはいけない:\n" + puml,
                puml.contains("names : String>"));
    }

    @Test
    public void testAliasCollisionFromUnderscoreVsDotDoesNotDropEntity() {
        // Round 3 回帰: alias() は FQN の非英数字を一律 '_' に潰す非単射写像だったため、
        // 区切り位置だけ違う 2 つの FQN (com.x_foo.Bar と com.x.foo_Bar) が同一エイリアス
        // (e_com_x_foo_Bar) へ畳まれ、entity 宣言が重複して片方が PlantUML 上で消え、FK も
        // 誤結線していた。修正: FQN ごとに連番エイリアス (e0, e1, ...) を採番して一意化する。
        String src1 = "package com.x_foo;\n"
                + "@Entity(tableName = \"t1\")\n"
                + "public class Bar { @PrimaryKey public long id; }\n";
        String src2 = "package com.x;\n"
                + "@Entity(tableName = \"t2\")\n"
                + "public class foo_Bar { @PrimaryKey public long id; }\n";
        RoomAnalyzer.Result result = new RoomAnalyzer().analyze(parse(src1, src2));
        String puml = PlantUmlErDiagram.render(result);

        // 2 つの entity が宣言され、それぞれ別エイリアスであること (衝突で畳まれない)。
        Matcher m = Pattern.compile("entity \"[^\"]*\" as (\\w+) \\{").matcher(puml);
        List<String> aliases = new ArrayList<>();
        while (m.find()) {
            aliases.add(m.group(1));
        }
        assertEquals("2 つの entity が宣言されるべき: " + aliases + "\n" + puml,
                2, aliases.size());
        assertEquals("エイリアスは一意であるべき (非単射衝突が無い): " + aliases + "\n" + puml,
                2, new HashSet<>(aliases).size());
        // 旧・非単射エイリアスがそのまま使われていないこと
        assertFalse("非単射な旧エイリアス e_com_x_foo_Bar が使われてはいけない:\n" + puml,
                puml.contains("e_com_x_foo_Bar"));
    }

    @Test
    public void testNestedGenericFieldTypePreservesStructure() {
        // 境界: 入れ子ジェネリクス (Map<String, List<Integer>>) でもトップレベルの
        // カンマだけで型引数を分割できること (splitTopLevelArgs の depth 管理)。
        String src = "package com.x;\n"
                + "@Entity(tableName = \"counters\")\n"
                + "public class Counter {\n"
                + "  @PrimaryKey\n"
                + "  public long id;\n"
                + "  public java.util.Map<java.lang.String, "
                + "java.util.List<java.lang.Integer>> counts;\n"
                + "}\n";
        RoomAnalyzer.Result result = new RoomAnalyzer().analyze(parse(src));
        String puml = PlantUmlErDiagram.render(result);

        assertTrue("counts 列は Map<String, List<Integer>> と表示されるべき:\n" + puml,
                puml.contains("counts : Map<String, List<Integer>>"));
        // 修正前の壊れた表示 (最後のドットだけで切って "Integer>>" になる) が
        // 残っていないこと
        assertFalse("counts 列が Integer>> に壊れてはいけない:\n" + puml,
                puml.contains("counts : Integer>>"));
    }
}
