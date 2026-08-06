// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 入力中の構文チェック ({@link PumlDiagnostics}) を検証する純ロジックテスト (headless)。
 *
 * <p>検出するケースより、<b>検出してはいけないケース</b>のほうを厚く固定している。
 * 波線が出っぱなしのエディタは信用されなくなり、本物の指摘まで無視されるため。</p>
 */
public class PumlDiagnosticsTest {

    private static List<PumlDiagnostics.Diagnostic> check(String... lines) {
        return PumlDiagnostics.analyze(String.join("\n", lines));
    }

    private static void clean(String... lines) {
        List<PumlDiagnostics.Diagnostic> d = check(lines);
        assertTrue("誤検出: " + d, d.isEmpty());
    }

    // -------------------------------------------------------------------------
    // 閉じ忘れを見つける
    // -------------------------------------------------------------------------

    @Test
    public void unclosedAlt_isReportedAtTheOpeningLine() {
        // 開始行を指すのが肝心。PlantUML 自身のエラーは原因行を的確に指さない。
        List<PumlDiagnostics.Diagnostic> d = check(
                "@startuml", "A -> B", "alt ok", "  A -> B", "@enduml");
        assertEquals(1, d.size());
        assertEquals(3, d.get(0).line());
        assertTrue(d.get(0).message().contains("alt"));
        assertTrue(d.get(0).message().contains("end"));
    }

    @Test
    public void unclosedUmlBlock_isReported() {
        List<PumlDiagnostics.Diagnostic> d = check("@startuml", "A -> B");
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).line());
    }

    @Test
    public void strayEnd_isReported() {
        List<PumlDiagnostics.Diagnostic> d = check("@startuml", "A -> B", "end", "@enduml");
        assertEquals(1, d.size());
        assertEquals(3, d.get(0).line());
    }

    @Test
    public void strayEnduml_isReported() {
        List<PumlDiagnostics.Diagnostic> d = check("A -> B", "@enduml");
        assertEquals(1, d.size());
        assertEquals(2, d.get(0).line());
    }

    @Test
    public void unclosedActivityIf_isReported() {
        List<PumlDiagnostics.Diagnostic> d = check(
                "@startuml", "start", "if (ok?) then (yes)", "  :do;", "stop", "@enduml");
        assertEquals(1, d.size());
        assertEquals(3, d.get(0).line());
    }

    @Test
    public void unclosedPreprocessorIf_isReported() {
        List<PumlDiagnostics.Diagnostic> d = check(
                "@startuml", "!ifdef FLAG", "A -> B", "@enduml");
        assertEquals(1, d.size());
        assertEquals(2, d.get(0).line());
    }

    @Test
    public void severalProblems_areReportedInLineOrder() {
        List<PumlDiagnostics.Diagnostic> d = check(
                "@startuml", "alt a", "loop n", "@enduml");
        assertEquals(2, d.size());
        assertEquals(2, d.get(0).line());
        assertEquals(3, d.get(1).line());
    }

    // -------------------------------------------------------------------------
    // 正しい図を誤検出しない
    // -------------------------------------------------------------------------

    @Test
    public void wellFormedSequence_isClean() {
        clean("@startuml", "participant A", "participant B",
                "alt ok", "  A -> B", "else ng", "  B -> A", "end", "@enduml");
    }

    @Test
    public void nestedBlocks_areClean() {
        clean("@startuml", "alt a", "  loop n", "    group g", "      A -> B",
                "    end", "  end", "end", "@enduml");
    }

    @Test
    public void wellFormedActivity_isClean() {
        clean("@startuml", "start", "if (ok?) then (yes)", "  :a;", "else (no)",
                "  :b;", "endif", "while (more?) is (yes)", "  :c;", "endwhile",
                "stop", "@enduml");
    }

    @Test
    public void singleLineNote_isNotTreatedAsABlock() {
        // ":" があるノートはその行で完結している。ブロック扱いすると全部誤検出になる。
        clean("@startuml", "A -> B", "note right : hello", "note over A, B : hi",
                "note left of A : x", "@enduml");
    }

    @Test
    public void multiLineNote_isMatchedWithEndNote() {
        clean("@startuml", "note as N", "  free text", "end note", "@enduml");
        List<PumlDiagnostics.Diagnostic> d =
                check("@startuml", "note as N", "  free text", "@enduml");
        assertEquals(1, d.size());
        assertEquals(2, d.get(0).line());
    }

    @Test
    public void forkAndSplitContinuations_areNotOpeners() {
        clean("@startuml", "start", "fork", "  :a;", "fork again", "  :b;",
                "end fork", "stop", "@enduml");
        clean("@startuml", "start", "split", "  :a;", "split again", "  :b;",
                "end split", "stop", "@enduml");
    }

    @Test
    public void repeatIsClosedByRepeatWhile() {
        clean("@startuml", "start", "repeat", "  :a;", "repeat while (more?)",
                "stop", "@enduml");
    }

    @Test
    public void endSpellingVariants_areAccepted() {
        clean("@startuml", "box \"team\"", "  participant A", "endbox", "@enduml");
        clean("@startuml", "box \"team\"", "  participant A", "end box", "@enduml");
        clean("@startuml", "start", "fork", "  :a;", "endfork", "stop", "@enduml");
    }

    @Test
    public void saltAndOtherGrammars_areLeftAlone() {
        // salt の波括弧・mindmap の * ・gantt の [Task] は別文法。
        // ここへ UML 用の規則を当てると誤検出だらけになる。
        clean("@startsalt", "{", "  [OK] | [Cancel]", "}", "@endsalt");
        clean("@startmindmap", "* Root", "** Child", "@endmindmap");
        clean("@startgantt", "[Task] lasts 5 days", "@endgantt");
        clean("@startjson", "{", "  \"a\": 1", "}", "@endjson");
    }

    @Test
    public void commentsAreIgnored() {
        clean("@startuml", "' alt this is only a comment", "A -> B", "@enduml");
        clean("@startuml", "/'", "alt inside a block comment", "'/", "A -> B", "@enduml");
    }

    @Test
    public void classDiagramWithBraces_isClean() {
        // 波括弧は検査対象外 (salt/json では本文そのものなので一律には数えられない)。
        clean("@startuml", "class Foo {", "  +bar()", "}", "Foo <|-- Baz", "@enduml");
    }

    @Test
    public void ifWithoutParentheses_isNotTreatedAsAnActivityBlock() {
        // "if" が丸括弧を伴わない行は制御構造ではない。
        clean("@startuml", "A -> B : if this then that", "@enduml");
    }

    @Test
    public void classBodyMembers_areNotReadAsSyntax() {
        // 列挙定数の END / NOTE は構文語ではない。実際に Juml が出すクラス図で
        // 起きた誤検出そのもの。
        clean("@startuml", "enum \"Kind\" as C1 {", "  START", "  STOP", "  END",
                "  NOTE", "}", "@enduml");
        clean("@startuml", "class \"Foo\" as C2 {", "  +alt: int", "  +loop()", "}",
                "@enduml");
    }

    @Test
    public void freeTextBlocks_areNotReadAsSyntax() {
        // 凡例の本文には alt/opt/loop といった説明語がふつうに現れる
        // (Juml 自身が出す凡例がまさにそう)。
        clean("@startuml", "legend top left", "  alt/opt/loop   分岐とループ",
                "  note right of Y   コメント", "  group/critical  try-catch",
                "endlegend", "@enduml");
        clean("@startuml", "note as N", "  alt is a keyword", "  end", "end note",
                "@enduml");
    }

    @Test
    public void blocksInsideNonMemberBraces_areStillChecked() {
        // package / partition の中身は文なので、閉じ忘れは引き続き見たい。
        List<PumlDiagnostics.Diagnostic> d = check(
                "@startuml", "start", "partition work {", "  if (ok?) then (yes)",
                "    :a;", "}", "stop", "@enduml");
        assertEquals(1, d.size());
        assertEquals(4, d.get(0).line());
    }

    @Test
    public void generatedDiagramCatalog_producesNoFalsePositives() {
        // 同梱の雛形・スニペット・囲みは、そのまま描画できる正しい PlantUML。
        // ここが 1 件でも鳴ったら、それは規則が厳しすぎるということ。
        for (PumlTemplate t : PumlTemplate.values()) {
            List<PumlDiagnostics.Diagnostic> d = PumlDiagnostics.analyze(t.body());
            assertTrue("雛形 " + t.name() + " で誤検出: " + d, d.isEmpty());
        }
        for (PumlSnippets.Snippet s : PumlSnippets.all()) {
            String puml = "@startuml\n"
                    + PumlSnippetTemplate.expand(s.body()).text() + "@enduml\n";
            assertTrue("スニペット " + s.trigger() + " で誤検出",
                    PumlDiagnostics.analyze(puml).isEmpty());
        }
        for (PumlSurrounds.Surround s : PumlSurrounds.all()) {
            String puml = "@startuml\n"
                    + PumlSnippetTemplate.expand(s.body(), "", "A -> B : x").text()
                    + "@enduml\n";
            assertTrue("囲み " + s.displayName() + " で誤検出",
                    PumlDiagnostics.analyze(puml).isEmpty());
        }
    }

    @Test
    public void emptyAndNullInput_areClean() {
        assertTrue(PumlDiagnostics.analyze(null).isEmpty());
        assertTrue(PumlDiagnostics.analyze("").isEmpty());
        clean("");
    }

    @Test
    public void textWithoutAnyBlockDirective_isStillChecked() {
        // @startuml を省いた素の断片でも閉じ忘れは見たい。
        List<PumlDiagnostics.Diagnostic> d = check("alt ok", "A -> B");
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).line());
    }

    @Test
    public void editorShowsAndClearsSquigglesAsTheTextIsFixed() {
        org.junit.Assume.assumeFalse("ヘッドレス環境ではスキップ",
                java.awt.GraphicsEnvironment.isHeadless());
        PumlSourcePanel panel = org.assertj.swing.edt.GuiActionRunner.execute(
                PumlSourcePanel::new);
        org.assertj.swing.edt.GuiActionRunner.execute(() -> {
            panel.setEditable(true);
            panel.setText("@startuml\nalt ok\n  A -> B\n@enduml\n");
        });
        assertEquals("end 忘れが 1 件出る", 1,
                (int) org.assertj.swing.edt.GuiActionRunner.execute(
                        panel::diagnosticCountForTest));
        String hint = org.assertj.swing.edt.GuiActionRunner.execute(
                () -> panel.diagnosticAtForTest(12));
        assertTrue("理由をツールチップで読める: " + hint,
                hint != null && hint.contains("alt"));

        org.assertj.swing.edt.GuiActionRunner.execute(() ->
                panel.setText("@startuml\nalt ok\n  A -> B\nend\n@enduml\n"));
        assertEquals("直したら波線は消える", 0,
                (int) org.assertj.swing.edt.GuiActionRunner.execute(
                        panel::diagnosticCountForTest));
    }

    @Test
    public void readOnlyPanel_showsNoDiagnostics() {
        // 生成された図のソースを眺めているだけの人に指摘を出しても手がない。
        org.junit.Assume.assumeFalse("ヘッドレス環境ではスキップ",
                java.awt.GraphicsEnvironment.isHeadless());
        PumlSourcePanel panel = org.assertj.swing.edt.GuiActionRunner.execute(
                PumlSourcePanel::new);
        org.assertj.swing.edt.GuiActionRunner.execute(() ->
                panel.setText("@startuml\nalt ok\n@enduml\n"));
        assertEquals(0, (int) org.assertj.swing.edt.GuiActionRunner.execute(
                panel::diagnosticCountForTest));
    }
}
