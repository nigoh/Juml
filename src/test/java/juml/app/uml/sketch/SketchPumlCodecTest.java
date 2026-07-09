// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchPumlCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless で動く)。
 * GUI デザイナーの信頼性はこの round-trip の正しさに懸かっている。
 */
public class SketchPumlCodecTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "abstract class Base {",
            "  # id : long",
            "  + save() : void",
            "}",
            "interface Greetable {",
            "  + greet() : String",
            "}",
            "enum Color",
            "class Child",
            "",
            "Base <|-- Child",
            "Greetable <|.. Child : impl",
            "Child o-- Color",
            "",
            "'@pos Base 10 20",
            "'@pos Child 300 200",
            "@enduml",
            "");

    @Test
    public void parse_readsClassesKindsMembersAndPositions() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(4, r.model.getClasses().size());

        SketchClass base = r.model.findClass("Base");
        assertNotNull(base);
        assertEquals(SketchClass.Kind.ABSTRACT, base.getKind());
        assertEquals(java.util.List.of("# id : long"), base.getFields());
        assertEquals(java.util.List.of("+ save() : void"), base.getMethods());
        assertEquals(10, base.getX());
        assertEquals(20, base.getY());

        assertEquals(SketchClass.Kind.INTERFACE,
                r.model.findClass("Greetable").getKind());
        assertEquals(SketchClass.Kind.ENUM, r.model.findClass("Color").getKind());
    }

    @Test
    public void parse_outOfRangePosCoordinate_isReportedNotThrown() {
        // int 範囲外の '@pos 座標で NumberFormatException を投げず、未対応として扱う
        // (Design タブ切替時のクラッシュ防止 + テキスト保全)。
        String puml = "@startuml\nclass Foo\n'@pos Foo 3000000000 0\n@enduml\n";
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(puml); // 例外を投げないこと
        assertFalse("範囲外座標は未対応として編集をロックするはず", r.isFullySupported());
        assertNotNull(r.model.findClass("Foo"));
    }

    @Test
    public void parse_interleavedMembers_isReportedNotSilentlyReordered() {
        // メソッドの後にフィールドが来る (交互配置) 本体は再生成で並びが崩れるため、
        // 未対応として編集をロックし原文の並びを保全する。
        String puml = "@startuml\nclass Foo {\n  + run() : void\n  - id : int\n}\n@enduml\n";
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(puml);
        assertFalse("交互配置は往復で崩れるため編集不可にするはず", r.isFullySupported());
    }

    @Test
    public void parse_memberSeparatorLine_isReportedNotMisclassified() {
        // クラス区切り線 '--' はフィールドに誤分類され、再生成で先頭へ移動してしまうため未対応。
        String puml = "@startuml\nclass Foo {\n  - id : int\n  --\n  + run() : void\n}\n@enduml\n";
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(puml);
        assertFalse("区切り線を含む本体は編集不可にするはず", r.isFullySupported());
    }

    @Test
    public void namedStartuml_isPreservedThroughRoundTrip() {
        // @startuml <name> の図名 (出力名) はユーザー内容なので、GUI 編集の再生成でも保全する。
        String puml = "@startuml Login\nclass Foo\n@enduml\n";
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(puml);
        assertTrue("図名付きでも編集は有効", r.isFullySupported());
        assertEquals("Login", r.model.getDiagramName());
        assertTrue("再生成テキストに図名が残るはず: " + SketchPumlCodec.toPuml(r.model),
                SketchPumlCodec.toPuml(r.model).startsWith("@startuml Login\n"));
    }

    @Test
    public void unnamedStartuml_hasEmptyDiagramName() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse("@startuml\nclass Foo\n@enduml\n");
        assertEquals("", r.model.getDiagramName());
        assertTrue(SketchPumlCodec.toPuml(r.model).startsWith("@startuml\n"));
    }

    @Test
    public void parse_orphanPos_isDroppedButKeepsEditingEnabled() {
        // 存在しないクラスの '@pos (テキストでクラスを消した残骸など) は Juml 生成のレイアウト
        // メタデータの無害な掃除として意図的に破棄する。一般コメントと違い編集はロックしない。
        // (設計判断: won't-fix。ユーザー合意済み。)
        String puml = "@startuml\nclass Foo\n'@pos Foo 10 20\n'@pos Bar 30 40\n@enduml\n";
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(puml);
        assertTrue("孤立 '@pos があっても編集は有効なまま (無害なメタデータのため)",
                r.isFullySupported());
        assertEquals(1, r.model.getClasses().size());
        assertEquals(10, r.model.findClass("Foo").getX());
        // 再生成すると存在しないクラスの '@pos は落ちる (意図的な掃除)。
        assertFalse("存在しないクラスの '@pos は再生成テキストに残らない",
                SketchPumlCodec.toPuml(r.model).contains("Bar"));
    }

    @Test
    public void parse_cleanFieldsThenMethods_staysSupported() {
        // フィールド→メソッドの素直な並び (区切りなし) は従来どおり編集可能。
        String puml = "@startuml\nclass Foo {\n  - id : int\n  + run() : void\n}\n@enduml\n";
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(puml);
        assertTrue("素直な並びは編集可能なはず: " + r.unsupportedLines, r.isFullySupported());
    }

    @Test
    public void parse_readsRelationsWithKindAndLabel() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        assertEquals(3, r.model.getRelations().size());
        SketchRelation impl = r.model.getRelations().get(1);
        assertEquals(SketchRelation.Kind.IMPLEMENTS, impl.getKind());
        assertEquals("Greetable", impl.getLeft());
        assertEquals("Child", impl.getRight());
        assertEquals("impl", impl.getLabel());
        assertEquals(SketchRelation.Kind.AGGREGATION,
                r.model.getRelations().get(2).getKind());
    }

    @Test
    public void roundTrip_preservesModelSemantics() {
        SketchPumlCodec.ParseResult first = SketchPumlCodec.parse(SAMPLE);
        String regenerated = SketchPumlCodec.toPuml(first.model);
        SketchPumlCodec.ParseResult second = SketchPumlCodec.parse(regenerated);

        assertTrue("再生成テキストも全対応構文のはず", second.isFullySupported());
        assertEquals(first.model.getClasses().size(), second.model.getClasses().size());
        assertEquals(first.model.getRelations().size(), second.model.getRelations().size());
        // 2 回目以降の再生成は固定点 (テキストが完全一致) になるはず。
        assertEquals(regenerated, SketchPumlCodec.toPuml(second.model));
        // 位置も '@pos コメントで保存・復元される。
        assertEquals(300, second.model.findClass("Child").getX());
        assertEquals(200, second.model.findClass("Child").getY());
    }

    @Test
    public void parse_relationToUndeclaredClass_createsImplicitClass() {
        SketchPumlCodec.ParseResult r =
                SketchPumlCodec.parse("@startuml\nFoo --> Bar\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertNotNull(r.model.findClass("Foo"));
        assertNotNull(r.model.findClass("Bar"));
        assertEquals(SketchRelation.Kind.ASSOCIATION,
                r.model.getRelations().get(0).getKind());
    }

    @Test
    public void parse_unsupportedSyntax_isReportedNotDropped() {
        String seq = PumlTemplate.SEQUENCE.body(); // actor / participant / -> は未対応
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(seq);
        assertFalse("シーケンス図構文は未対応として報告されるはず",
                r.isFullySupported());
    }

    @Test
    public void parse_classTemplate_isFullySupported() {
        SketchPumlCodec.ParseResult r =
                SketchPumlCodec.parse(PumlTemplate.CLASS.body());
        assertTrue("クラス図テンプレートは GUI 編集可能なはず: " + r.unsupportedLines,
                r.isFullySupported());
        assertNotNull(r.model.findClass("Example"));
        assertEquals(2, r.model.getRelations().size());
    }

    @Test
    public void parse_generalComment_isReportedNotSilentlyDropped() {
        // '@pos 以外の一般コメントはモデル化できず GUI 編集で失われるため、
        // isFullySupported() は false (= デザイナー編集を無効化して保護) を返すべき。
        // (以前は黙って読み飛ばして true を返し、編集でコメントを消していた。)
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(
                "@startuml\n' IMPORTANT: keep this note\nclass Foo\n@enduml\n");
        assertFalse("一般コメントを含む図は未対応として保護されるはず",
                r.isFullySupported());
        assertTrue("コメント行が未対応として報告されるはず",
                r.unsupportedLines.contains("' IMPORTANT: keep this note"));
        // 一方 '@pos コメントは対応構文なので保護対象にはしない。
        SketchPumlCodec.ParseResult pos = SketchPumlCodec.parse(
                "@startuml\nclass Foo\n'@pos Foo 10 20\n@enduml\n");
        assertTrue("'@pos コメントだけなら編集可能のまま: " + pos.unsupportedLines,
                pos.isFullySupported());
    }

    @Test
    public void parse_unclosedBrace_doesNotSwallowEnduml() {
        // 閉じ波括弧が欠けたまま @enduml に達しても、@enduml をメンバーとして
        // 取り込んでテキストを破損させないこと。
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(
                "@startuml\nclass Foo {\n  +x: int\n@enduml\n");
        SketchClass foo = r.model.findClass("Foo");
        assertNotNull(foo);
        assertEquals("フィールドは +x: int のみ", 1, foo.getFields().size());
        assertEquals("+x: int", foo.getFields().get(0));
        assertTrue("@enduml をフィールド/メソッドとして取り込まないこと",
                foo.getMethods().isEmpty()
                        && !foo.getFields().contains("@enduml"));
        // 再生成テキストに素の @enduml 混入や壊れた本体が無いこと。
        String out = SketchPumlCodec.toPuml(r.model);
        assertFalse("本体内に @enduml が混入していないこと",
                out.contains("  @enduml"));
    }

    @Test
    public void parse_abstractInterface_isReportedNotSilentlyDowngraded() {
        // 'abstract interface' はモデル種別 (INTERFACE) では abstract を表現できず、
        // GUI 再生成で 'interface' に化けて abstract が黙って失われる。編集を無効化して
        // テキストを保護するため、未対応として報告されるべき。
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(
                "@startuml\nabstract interface Foo\n@enduml\n");
        assertFalse("abstract interface は未対応として保護されるはず",
                r.isFullySupported());
        assertTrue("abstract interface 行が未対応として報告されるはず",
                r.unsupportedLines.contains("abstract interface Foo"));

        // 'abstract enum' も同様に保護する。
        SketchPumlCodec.ParseResult en = SketchPumlCodec.parse(
                "@startuml\nabstract enum Bar\n@enduml\n");
        assertFalse("abstract enum も未対応として保護されるはず", en.isFullySupported());

        // 対照: 素の 'abstract class' は ABSTRACT 種別で表現でき、編集可能なまま。
        SketchPumlCodec.ParseResult ac = SketchPumlCodec.parse(
                "@startuml\nabstract class Baz\n@enduml\n");
        assertTrue("abstract class は編集可能のまま: " + ac.unsupportedLines,
                ac.isFullySupported());
        assertEquals(SketchClass.Kind.ABSTRACT, ac.model.findClass("Baz").getKind());
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        String puml = SketchPumlCodec.toPuml(new SketchModel());
        assertEquals("@startuml\n@enduml\n", puml);
    }

    @Test
    public void model_removeClass_dropsTouchingRelations() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        r.model.removeClass(r.model.findClass("Child"));
        assertTrue("Child に接続する関係は全て消えるはず",
                r.model.getRelations().isEmpty());
    }

    @Test
    public void model_renameClass_updatesRelationEndpoints() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(SAMPLE);
        r.model.renameClass(r.model.findClass("Child"), "Renamed");
        String puml = SketchPumlCodec.toPuml(r.model);
        assertTrue(puml.contains("Base <|-- Renamed"));
        assertFalse(puml.contains("Child"));
    }
}
