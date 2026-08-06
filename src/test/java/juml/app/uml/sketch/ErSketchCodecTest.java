// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ErSketchCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless)。
 */
public class ErSketchCodecTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "hide circle",
            "entity \"User\" as e1 {",
            "  * id : int",
            "  --",
            "  name : varchar",
            "  email : varchar",
            "}",
            "entity \"Post\" as e2 {",
            "  * id : int",
            "  --",
            "  user_id : int",
            "  title : varchar",
            "}",
            "e1 ||--o{ e2 : has",
            "@enduml",
            "");

    @Test
    public void parse_readsEntitiesColumnsCardinalityLabel() {
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getEntities().size());
        assertEquals(1, r.model.getRelations().size());

        ErSketchModel.Entity user = r.model.findEntity("e1");
        assertNotNull(user);
        assertEquals("User", user.getDisplayName());
        assertEquals(3, user.getColumns().size());
        ErSketchModel.Column id = user.getColumns().get(0);
        assertTrue("先頭列 id は主キーのはず", id.isPrimaryKey());
        assertEquals("id", id.getName());
        assertEquals("int", id.getType());
        assertFalse("name 列は主キーでないはず", user.getColumns().get(1).isPrimaryKey());

        ErSketchModel.Relation rel = r.model.getRelations().get(0);
        assertEquals("e1", rel.getLeft());
        assertEquals("e2", rel.getRight());
        assertEquals(ErSketchModel.Cardinality.EXACTLY_ONE, rel.getLeftCard());
        assertEquals(ErSketchModel.Cardinality.ZERO_OR_MANY, rel.getRightCard());
        assertEquals("has", rel.getLabel());
        assertEquals("||--o{", rel.arrow());
    }

    @Test
    public void roundTrip_reachesFixedPointAndPreservesMeaning() {
        ErSketchCodec.ParseResult first = ErSketchCodec.parse(SAMPLE);
        String regenerated = ErSketchCodec.toPuml(first.model);
        ErSketchCodec.ParseResult second = ErSketchCodec.parse(regenerated);
        assertTrue("再生成テキストも全行対応のはず: " + second.unsupportedLines,
                second.isFullySupported());
        assertEquals("2 回目以降の再生成は固定点になるはず",
                regenerated, ErSketchCodec.toPuml(second.model));
        // 別名・表示名・主キー・crow's-foot 演算子・ラベルが往復で保全される。
        assertTrue(regenerated.contains("entity \"User\" as e1"));
        assertTrue(regenerated.contains("* id : int"));
        assertTrue(regenerated.contains("e1 ||--o{ e2 : has"));
        assertTrue("hide circle を再付与するはず", regenerated.contains("hide circle"));
    }

    @Test
    public void parse_plainEntityTemplateForm_isFullySupported() {
        // PumlTemplate.ER と同じ素の entity/PK/FK/区切り線の形も対応する。
        String plain = String.join("\n",
                "@startuml",
                "entity User {",
                "  * id : int <<PK>>",
                "  --",
                "  name : varchar",
                "}",
                "entity Order {",
                "  * id : int <<PK>>",
                "  --",
                "  user_id : int <<FK>>",
                "}",
                "User ||--o{ Order",
                "@enduml",
                "");
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(plain);
        assertTrue("PumlTemplate.ER 形も全行対応のはず: " + r.unsupportedLines,
                r.isFullySupported());
        assertEquals(2, r.model.getEntities().size());
        // 型欄に <<PK>> ステレオタイプが保全される。
        assertEquals("int <<PK>>", r.model.findEntity("User").getColumns().get(0).getType());
    }

    @Test
    public void parse_variousCardinalities_areRecognized() {
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(String.join("\n",
                "@startuml", "hide circle",
                "entity A", "entity B", "entity C", "entity D",
                "A }o--|| B", "B ||--|| C", "C }|--|{ D",
                "@enduml", ""));
        assertTrue(r.isFullySupported());
        assertEquals(ErSketchModel.Cardinality.ZERO_OR_MANY,
                r.model.getRelations().get(0).getLeftCard());
        assertEquals(ErSketchModel.Cardinality.EXACTLY_ONE,
                r.model.getRelations().get(0).getRightCard());
        assertEquals(ErSketchModel.Cardinality.ONE_OR_MANY,
                r.model.getRelations().get(2).getLeftCard());
        assertEquals(ErSketchModel.Cardinality.ONE_OR_MANY,
                r.model.getRelations().get(2).getRightCard());
    }

    @Test
    public void parse_generalComment_isReportedNotDropped() {
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(
                "@startuml\nhide circle\n' keep this\nentity User\n@enduml\n");
        assertFalse(r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("' keep this"));
    }

    @Test
    public void parse_unknownConstruct_locksEditing() {
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(
                "@startuml\nhide circle\nentity A\npackage P {\n}\nA ||--o{ B\n@enduml\n");
        assertFalse("パッケージ境界は未対応として保護されるはず", r.isFullySupported());
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        // hide circle は「読んだときだけ再付与する」。無条件に付けていたため、指令の無い
        // 図を設計器で 1 回触るだけで 1 行増え、描画が変わっていた。
        assertEquals("@startuml\n@enduml\n", ErSketchCodec.toPuml(new ErSketchModel()));

        ErSketchModel had = new ErSketchModel();
        had.setHideCircle(true);
        assertEquals("@startuml\nhide circle\n@enduml\n", ErSketchCodec.toPuml(had));
    }

    /**
     * 書き戻しで<b>元テキストに無い指令を足さない</b>こと。
     *
     * <p>「読んだものが書き戻しで出てこない」の裏返しで、こちらは「書いていないものが
     * 出てくる」。同梱の ER テンプレートに {@code hide circle} は入っていないので、
     * テンプレートから作った図を Design タブで 1 回ドラッグするだけで、エンティティの
     * 丸バッジが消えて図の見た目が黙って変わっていた。同じ ER コーデックは、区切り線が
     * {@code ==} から {@code --} へ正規化される<b>もっと小さな差分</b>に対しては編集を
     * ロックして原文を守る規則を持っている。指令の注入だけがその規則の外にあった。</p>
     */
    @Test
    public void toPuml_doesNotInjectDirectivesAbsentFromTheSource() {
        String template = juml.app.uml.PumlTemplate.ER.body();
        assertFalse("前提: ER テンプレートに hide circle は無い", template.contains("hide circle"));

        ErSketchCodec.ParseResult r = ErSketchCodec.parse(template);
        assertTrue("前提: テンプレートは設計器で編集できる", r.isFullySupported());
        assertFalse("往復で hide circle を足さないこと",
                ErSketchCodec.toPuml(r.model).contains("hide circle"));

        // 非退行: 元からあれば保つ。
        String withHide = "@startuml\nhide circle\nentity A {\n  * id : int\n}\n@enduml\n";
        assertTrue("元からある hide circle は保つこと",
                ErSketchCodec.toPuml(ErSketchCodec.parse(withHide).model).contains("hide circle"));
    }

    @Test
    public void model_renameEntity_updatesRelationEndpoints() {
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(SAMPLE);
        r.model.renameEntity(r.model.findEntity("e1"), "account");
        String puml = ErSketchCodec.toPuml(r.model);
        assertTrue(puml.contains("account ||--o{ e2 : has"));
        assertFalse(puml.contains("e1 "));
    }
}
