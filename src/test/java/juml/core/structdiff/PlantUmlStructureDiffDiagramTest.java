// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.structdiff;

import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.formats.uml.PlantUmlSyntaxChecker;
import juml.core.structdiff.ClassStructureDiff.ClassDiff;
import juml.core.structdiff.PlantUmlStructureDiffDiagram.Options;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * {@link PlantUmlStructureDiffDiagram} が生成する PlantUML テキストの内容を検証する。
 * 実レンダリングはせず、テキストのマーカー・エスケープ・構文チェックを見る。
 */
public class PlantUmlStructureDiffDiagramTest {

    private static String generate(String oldSrc, String newSrc, Options opt) {
        List<ClassDiff> diffs = ClassStructureDiff.compare(
                oldSrc != null ? JavaStructureExtractor.extract(oldSrc) : List.of(),
                newSrc != null ? JavaStructureExtractor.extract(newSrc) : List.of());
        return PlantUmlStructureDiffDiagram.generate(diffs, opt);
    }

    @Test
    public void addedClass_getsStereotypeAndBackground() {
        String puml = generate("", "class B { int x; }", null);
        assertTrue(puml.contains("<<added>>"));
        assertTrue(puml.contains(PlantUmlStructureDiffDiagram.ADDED_BG));
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.trim().endsWith("@enduml"));
    }

    @Test
    public void removedClass_getsStereotypeAndBackground() {
        String puml = generate("class Gone { }", "", null);
        assertTrue(puml.contains("<<removed>>"));
        assertTrue(puml.contains(PlantUmlStructureDiffDiagram.REMOVED_BG));
    }

    @Test
    public void modifiedMember_showsOldStruckAndNewColored() {
        String puml = generate(
                "class A { int count() { return 0; } }",
                "class A { long count() { return 0L; } }", null);
        assertTrue(puml.contains("<<modified>>"));
        assertTrue("旧宣言は打ち消し線付き",
                puml.contains("<s>~ count() : int</s>"));
        assertTrue("新宣言は変更色",
                puml.contains("<color:" + PlantUmlStructureDiffDiagram.MODIFIED_FG
                        + ">~ count() : long</color>"));
    }

    @Test
    public void removedMemberInModifiedClass_isStruckThrough() {
        String puml = generate(
                "class A { void gone() {} void keep() {} }",
                "class A { void keep() {} }", null);
        assertTrue(puml.contains("<s>~ gone()"));
        assertTrue("不変メンバーは装飾なしで残る", puml.contains("  ~ keep()"));
    }

    @Test
    public void unchangedClass_isHiddenByDefaultAndShownWithOption() {
        String oldSrc = "class A { }\nclass B { int x; }";
        String newSrc = "class A { }\nclass B { long x; }";
        String hidden = generate(oldSrc, newSrc, null);
        assertFalse("不変クラス A は既定で出さない", hidden.contains("\"A\""));

        Options opt = new Options();
        opt.includeUnchangedClasses = true;
        String shown = generate(oldSrc, newSrc, opt);
        assertTrue(shown.contains("\"A\""));
    }

    @Test
    public void unchangedMembers_canBeCollapsedToCountLine() {
        Options opt = new Options();
        opt.showUnchangedMembers = false;
        String puml = generate(
                "class A { int a; int b; void gone() {} }",
                "class A { int a; int b; }", opt);
        assertTrue("省略した不変メンバー数を出す", puml.contains("2 unchanged"));
        assertFalse(puml.contains("+ a : int\n"));
    }

    @Test
    public void collapsedFields_doNotLeaveStraySeparator() {
        // フィールドは全て不変 (非表示) だがメソッドは変更あり。フィールド区画は
        // 1 行も出ないので、区切り線 "--" を孤立させてはいけない。
        Options opt = new Options();
        opt.showUnchangedMembers = false;
        String puml = generate(
                "class A { int a; int b; void keep() {} }",
                "class A { int a; int b; void keep() {} void added() {} }", opt);
        assertTrue("追加メソッドは出るはず", puml.contains("added()"));
        assertFalse("空フィールド区画の直後に孤立した -- を出さない",
                puml.contains("{\n  --"));
        assertFalse("不変メンバー非表示なので keep は本体に出ない", puml.contains("~ keep()"));
    }

    @Test
    public void genericTypes_areTildeEscaped() {
        String puml = generate(
                "import java.util.*;\nclass A { }",
                "import java.util.*;\nclass A { List<String> items; }", null);
        assertTrue("ジェネリクスの < はチルダエスケープされる",
                puml.contains("items : List~<String>"));
    }

    @Test
    public void emptyDiff_emitsEmptyMessageNote() {
        Options opt = new Options();
        opt.emptyMessage = "nothing changed";
        String puml = generate("class A { }", "class A { }", opt);
        assertTrue(puml.contains("note \"nothing changed\""));
    }

    @Test
    public void titleAndLegend_areEmitted() {
        Options opt = new Options();
        opt.title = "Foo.java abc1234 → def5678";
        String puml = generate("", "class B { }", opt);
        assertTrue(puml.contains("title Foo.java abc1234"));
        assertTrue(puml.contains("legend right"));
        assertTrue(puml.contains("| added |"));
    }

    @Test
    public void headerChange_isEmittedInsideClassBody() {
        String puml = generate(
                "class A extends Base { }",
                "class A extends NewBase { }", null);
        assertTrue(puml.contains("extends: Base → NewBase"));
    }

    @Test
    public void inheritanceEdge_isEmittedOnlyBetweenShownClasses() {
        String puml = generate(
                "class Base { }\nclass Child extends Base { }",
                "class Base { int x; }\nclass Child extends Base { int y; }", null);
        assertTrue("両方変更ありなので継承エッジが出る", puml.contains("<|--"));

        String single = generate(
                "class Child extends Base { }",
                "class Child extends Base { int y; }", null);
        assertFalse("図外の Base へはエッジを張らない", single.contains("<|--"));
    }

    @Test
    public void interfaceAndEnum_useMatchingKeywords() {
        String puml = generate("",
                "interface I { void f(); }\nenum E { ONE }", null);
        assertTrue(puml.contains("interface \"I\""));
        assertTrue(puml.contains("enum \"E\""));
    }

    @Test
    public void generatedText_passesSyntaxChecker() {
        String puml = generate(
                "import java.util.*;\n"
                        + "class A extends Base { List<String> xs; int gone() { return 1; } }\n"
                        + "class Removed { }",
                "import java.util.*;\n"
                        + "class A extends NewBase { Map<String, Integer> xs; }\n"
                        + "class Added { void hi() {} }", null);
        assertTrue("生成 PlantUML は構文チェックを通るはず: "
                        + PlantUmlSyntaxChecker.summarize(puml),
                PlantUmlSyntaxChecker.check(puml).isEmpty());
    }

    @Test
    public void generatedText_rendersToSvgWithBundledPlantUml() throws Exception {
        String puml = generate(
                "import java.util.*;\n"
                        + "class A extends Base { List<String> xs; int gone() { return 1; } }",
                "import java.util.*;\n"
                        + "class A extends NewBase { Map<String, Integer> xs; }\n"
                        + "class Added { void hi() {} }", null);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        // renderSvg はエラー画像を検出すると例外を投げるため、正常終了 = 描画成功
        juml.core.formats.uml.PlantUmlRenderer.renderSvg(puml, out);
        String svg = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("SVG 要素が出力されるはず", svg.contains("<svg"));
        assertTrue("打ち消し線付きの削除メンバーが描画されるはず",
                svg.contains("gone"));
    }

    @Test
    public void nullDiffList_producesEmptyNote() {
        String puml = PlantUmlStructureDiffDiagram.generate(null, null);
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.contains("note \"No structural changes\""));
    }
}
