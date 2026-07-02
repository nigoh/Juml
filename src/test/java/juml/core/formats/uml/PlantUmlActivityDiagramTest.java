// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlActivityDiagram} のユニットテスト。
 *
 * <p>1 メソッドの制御フロー (if/while/for/switch/try/return/throw) を
 * PlantUML アクティビティ図 (新構文: {@code if (...) then (yes)} / {@code while}/
 * {@code repeat} / {@code switch case} / {@code partition} / {@code stop} /
 * {@code kill}) に変換できることを確認する。</p>
 */
public class PlantUmlActivityDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        PlantUmlActivityDiagram.generate(null, "X", "m", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullEntry() {
        PlantUmlActivityDiagram.generate(
                JavaStructureExtractor.extract("class A {}"), null, null, null);
    }

    @Test
    public void testClassNotFound() {
        String puml = PlantUmlActivityDiagram.generate(
                JavaStructureExtractor.extract("class A {}"),
                "Unknown", "m", null);
        assertTrue(puml, puml.contains("Class not found"));
    }

    @Test
    public void testMethodNotFound() {
        String puml = PlantUmlActivityDiagram.generate(
                JavaStructureExtractor.extract("class A {}"),
                "A", "nope", null);
        assertTrue(puml, puml.contains("Method not found"));
    }

    @Test
    public void testBasicCallAsAction() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { foo.bar(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("start"));
        assertTrue(puml, puml.contains(":foo.bar();"));
        assertTrue(puml, puml.contains("stop"));
        assertTrue(puml, puml.contains("title A.run"));
    }

    @Test
    public void testEmptyMethodHasStartStop() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void m() {} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "m", null);
        assertTrue(puml, puml.contains("start"));
        assertTrue(puml, puml.contains("stop"));
    }

    @Test
    public void testIfElse() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { if (x>0) a(); else b(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("if (x>0) then (yes)"));
        assertTrue(puml, puml.contains(":a();"));
        assertTrue(puml, puml.contains("else (no)"));
        assertTrue(puml, puml.contains(":b();"));
        assertTrue(puml, puml.contains("endif"));
    }

    @Test
    public void testIfElseIfElse() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  if (a) { x(); }"
                        + "  else if (b) { y(); }"
                        + "  else { z(); }"
                        + "} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("if (a) then (yes)"));
        assertTrue(puml, puml.contains("elseif (b) then (yes)"));
        assertTrue(puml, puml.contains("else (no)"));
        assertTrue(puml, puml.contains("endif"));
    }

    @Test
    public void testWhileLoop() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { while (i<10) { step(); } } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        // 条件中の < はタグ誤認防止のためチルダエスケープされる
        assertTrue(puml, puml.contains("while (i~<10) is (true)"));
        assertTrue(puml, puml.contains(":step();"));
        assertTrue(puml, puml.contains("endwhile (false)"));
    }

    @Test
    public void testForLoop() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { for (int i=0; i<n; i++) { step(); } } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        // for は while に変換される
        assertTrue(puml, puml.contains("while (for:"));
        assertTrue(puml, puml.contains(":step();"));
        assertTrue(puml, puml.contains("endwhile (done)"));
    }

    @Test
    public void testDoWhileLoop() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { do { step(); } while (cond); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("repeat"));
        assertTrue(puml, puml.contains(":step();"));
        assertTrue(puml, puml.contains("repeat while (cond)"));
    }

    @Test
    public void testSwitchCase() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run(int n) {"
                        + "  switch (n) {"
                        + "    case 1: a(); break;"
                        + "    case 2: b(); break;"
                        + "    default: c();"
                        + "  }"
                        + "} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("switch (n)"));
        assertTrue(puml, puml.contains("case (1)"));
        assertTrue(puml, puml.contains("case (2)"));
        assertTrue(puml, puml.contains("case (default)"));
        assertTrue(puml, puml.contains("endswitch"));
    }

    @Test
    public void testTryCatchFinally() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  try { open(); }"
                        + "  catch (IOException e) { handle(); }"
                        + "  finally { close(); }"
                        + "} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("partition \"try\""));
        assertTrue(puml, puml.contains(":open();"));
        assertTrue(puml, puml.contains("partition \"catch (IOException e)\""));
        assertTrue(puml, puml.contains(":handle();"));
        assertTrue(puml, puml.contains("partition \"finally\""));
        assertTrue(puml, puml.contains(":close();"));
    }

    @Test
    public void testSynchronizedBlock() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Object lock = new Object();"
                        + "  void run() { synchronized (lock) { work(); } } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("partition \"synchronized(lock)\""));
        assertTrue(puml, puml.contains(":work();"));
    }

    @Test
    public void testReturnEmitsStop() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { int run() { if (x) return 42; a(); return 0; } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains(":return 42;"));
        assertTrue(puml, puml.contains(":return 0;"));
        // return の直後には stop が来る
        int firstReturn = puml.indexOf(":return 42;");
        assertTrue("stop should follow return",
                puml.indexOf("stop", firstReturn) > firstReturn);
    }

    @Test
    public void testVoidReturn() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { if (x) return; a(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains(":return;"));
    }

    @Test
    public void testThrowEmitsKill() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { if (bad) throw new IllegalStateException(\"x\"); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains(":throw"));
        assertTrue(puml, puml.contains("kill"));
    }

    @Test
    public void testBreakAndContinue() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  while (true) {"
                        + "    if (a) break;"
                        + "    if (b) continue;"
                        + "    step();"
                        + "  }"
                        + "} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("break"));
        assertTrue(puml, puml.contains("continue"));
    }

    @Test
    public void testReceiverlessCallNoLeadingDot() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { helper(); } void helper() {} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains(":helper();"));
        // receiver が無い呼び出しに先頭ドットが付かないこと
        assertFalse(puml, puml.contains(":.helper();"));
    }

    @Test
    public void testCustomTitle() {
        PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
        o.title = "Login Flow";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("title Login Flow"));
    }

    @Test
    public void testLegendIncludedByDefault() {
        String puml = PlantUmlActivityDiagram.generate(
                JavaStructureExtractor.extract("class A { void m() {} }"),
                "A", "m", null);
        assertTrue(puml, puml.contains("legend top left"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlActivityDiagram.generate(
                JavaStructureExtractor.extract("class A { void m() {} }"),
                "A", "m", o);
        assertFalse(puml, puml.contains("legend top left"));
    }

    @Test
    public void testListCandidates() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void a(){} void b(){ step(); } } class B { void c(){} }");
        List<PlantUmlActivityDiagram.Candidate> cs =
                PlantUmlActivityDiagram.listCandidates(infos);
        assertTrue("expect at least 3 candidates", cs.size() >= 3);
        // 文を含むメソッドが先頭近くに来る (b)
        boolean found = false;
        for (PlantUmlActivityDiagram.Candidate c : cs) {
            if ("A".equals(c.className) && "b".equals(c.methodName)) {
                found = true;
                break;
            }
        }
        assertTrue("A.b should be present", found);
    }

    @Test
    public void testQualifiedClassName() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package p; class A { void run() { foo(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "p.A", "run", null);
        assertTrue(puml, puml.contains(":foo();"));
    }

    // ---- yield statement (Java 14+ switch expression) ----

    @Test
    public void testYieldInSwitchExpressionRendersAsAction() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A {\n"
                + "  String m(int n) {\n"
                + "    return switch (n) {\n"
                + "      case 1 -> \"one\";\n"
                + "      default -> {\n"
                + "        String s = compute(n);\n"
                + "        yield s;\n"
                + "      }\n"
                + "    };\n"
                + "  }\n"
                + "}");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "m", null);
        assertTrue("yield should appear as action node: " + puml,
                puml.contains(":yield"));
    }

    @Test
    public void testYieldWithExpressionShowsExpression() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A {\n"
                + "  int m(int x) {\n"
                + "    return switch (x) {\n"
                + "      default -> { yield x * 2; }\n"
                + "    };\n"
                + "  }\n"
                + "}");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "m", null);
        assertTrue("yield expression should appear in diagram: " + puml,
                puml.contains(":yield"));
    }

    // ── ローカル変数宣言テスト ──

    @Test
    public void testLocalVarAppearsInDiagram() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { String result = getData(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue("LocalVar should appear as action node: " + puml,
                puml.contains("String result"));
    }

    @Test
    public void testLocalVarWithNoInit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { int count; } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue("LocalVar without init should appear: " + puml,
                puml.contains("int count"));
    }

    @Test
    public void testLocalVarHiddenWhenOptionOff() {
        PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
        o.showLocalVars = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { String result = getData(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", o);
        assertFalse("LocalVar should be hidden when showLocalVars=false: " + puml,
                puml.contains("String result"));
    }

    @Test
    public void testGenericLocalVarAppearsInDiagram() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { List<String> items = getList(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue("Generic LocalVar should appear: " + puml,
                puml.contains("List"));
        assertTrue(puml.contains("items"));
    }

    // ── インラインコメントテスト ──

    @Test
    public void testInlineLineCommentAppearsAsNote() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {\n"
                + "  // 前処理フェーズ\n"
                + "  init();\n"
                + "} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertTrue("Inline comment should appear as note: " + puml,
                puml.contains("前処理フェーズ"));
        assertTrue(puml.contains("note right"));
    }

    @Test
    public void testInlineCommentHiddenWhenOptionOff() {
        PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
        o.showInlineComments = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {\n"
                + "  // 前処理フェーズ\n"
                + "  init();\n"
                + "} }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", o);
        assertFalse("Inline comment should be hidden when showInlineComments=false",
                puml.contains("前処理フェーズ"));
    }

    // ── メソッドシグネチャテスト ──

    @Test
    public void testMethodSignatureShownForParametrizedMethod() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { String process(int count, String name) { return name; } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "process", null);
        assertTrue("Input params should appear: " + puml, puml.contains("入力:"));
        assertTrue(puml.contains("int count"));
        assertTrue(puml.contains("String name"));
        assertTrue("Return type should appear: " + puml, puml.contains("戻り値: String"));
    }

    @Test
    public void testMethodSignatureNotShownForVoidNoParam() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { foo(); } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "run", null);
        assertFalse("No signature note for void no-param method: " + puml,
                puml.contains("入力:"));
        assertFalse(puml.contains("戻り値:"));
    }

    @Test
    public void testMethodSignatureHiddenWhenShowCommentsOff() {
        PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
        o.showComments = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { String process(int x) { return null; } }");
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "process", o);
        assertFalse("Signature should be hidden when showComments=false",
                puml.contains("入力:"));
    }

    // ---- commentMaxLength=0 (既定) では切り詰めなし ----

    @Test
    public void testLongLocalVarNoEllipsisWhenCommentMaxLengthDefault() {
        // commentMaxLength が既定 (0 = 無制限) のとき、100 文字超のローカル変数宣言が
        // "…" なしで全文アクションノードに出ることを確認する。
        // 変数名を十分に長くして type + " " + name が 100 文字超になるようにする。
        String longVarName = "aVeryVeryLongVariableNameForTestingNoTruncationInActivityDiagramNodes";
        // "String " (7) + longVarName (>94) = >101 chars
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void m() { String " + longVarName + "; } }");
        // commentMaxLength 既定 = 0 (無制限)
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "m", null);
        // 変数名が全文含まれること (切り詰められていない)
        assertTrue("long variable name should appear in full without ellipsis: " + puml,
                puml.contains(longVarName));
        // 省略記号 "…" が含まれないこと
        assertFalse("ellipsis should NOT appear when commentMaxLength=0: " + puml,
                puml.contains("…"));
    }

    @Test
    public void testLongLocalVarEllipsisWhenCommentMaxLengthSet() {
        // commentMaxLength=20 のとき、20 文字超のローカル変数宣言が "…" で切り詰められることを確認する。
        String longVarName = "aVeryVeryLongVariableNameForTestingTruncationInActivityDiagramNodes";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void m() { String " + longVarName + "; } }");
        PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
        o.commentMaxLength = 20;
        String puml = PlantUmlActivityDiagram.generate(infos, "A", "m", o);
        // commentMaxLength=20 なら省略記号 "…" が含まれること
        assertTrue("ellipsis should appear when commentMaxLength=20: " + puml,
                puml.contains("…"));
        // 変数名の全文は含まれないこと
        assertFalse("full variable name should NOT appear when truncated: " + puml,
                puml.contains(longVarName));
    }
}
