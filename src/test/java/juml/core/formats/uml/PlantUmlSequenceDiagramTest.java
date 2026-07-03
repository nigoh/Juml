// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlSequenceDiagram のユニットテスト。
 */
public class PlantUmlSequenceDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        PlantUmlSequenceDiagram.generate(null, "X", "m", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullEntry() {
        // String オーバーロードを明示して呼び出す (null リテラルによるオーバーロード曖昧性を回避)
        PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"), (String) null, (String) null, null);
    }

    @Test
    public void testNonexistentClass() {
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"),
                "Unknown", "m", null);
        assertTrue(puml, puml.contains("Class not found"));
    }

    @Test
    public void testNonexistentMethod() {
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"),
                "A", "nope", null);
        assertTrue(puml, puml.contains("Method not found"));
    }

    @Test
    public void testResolvedMethodCallbackBodyExpanded() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Repository repo;"
                        + " void run() { repo.findAll(item -> process(item)); }"
                        + " void process(Object item) {} }"
                        + " class Repository { void findAll(java.util.function.Consumer<Object> c) {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("Repository.findAll()"));
        assertTrue(puml, puml.contains("A.process()"));
        assertTrue("expected an inline ($) callback participant:\n" + puml, puml.contains("$"));
    }

    @Test
    public void testNoteParticipantTopSeparatesConsecutiveMethods() {
        // NOTE + PARTICIPANT_TOP で連続メソッドのコメントが --- で区切られる
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { /** doc a */ void a() { b(); }"
                        + " /** doc b */ void b() { c(); }"
                        + " /** doc c */ void c() {} }");
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        o.commentPlacement = PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "a", o);
        long seps = puml.lines().filter(l -> l.trim().equals("---")).count();
        assertTrue("expected >=2 method separators in NOTE block:\n" + puml, seps >= 2);
    }

    @Test
    public void testNoteBodyHtmlEscaped() {
        // NOTE モードの note 本文もタグ開始をチルダエスケープする (INLINE と挙動を
        // 揃え、List<String> 等が PlantUML のタグ誤認で崩れるのを防ぐ)。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { /** filter List<String> when x > 0 & ok */"
                        + " void run() { helper(); } void helper() {} }");
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("List~<String>"));
        assertTrue(puml, puml.contains("x > 0 & ok"));
        assertFalse("unescaped < must not leak into NOTE body:\n" + puml,
                puml.contains("List<String>"));
    }

    @Test
    public void testHiddenEntryParticipantYieldsFriendlyNote() {
        // 起点クラス自身を participant フィルタで隠すと、Caller だけの空図ではなく
        // 理由を説明する案内図を返す。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { foo.bar(); } }");
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.hiddenParticipants = new java.util.HashSet<>(java.util.Arrays.asList("A"));
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue("案内図には起点名が含まれる:\n" + puml, puml.contains("A"));
        assertTrue("hidden の理由が示される:\n" + puml, puml.contains("hidden"));
        // 空のリフレーン (activate A なしの本体) にならないこと
        assertFalse("本体呼び出しは描かれない:\n" + puml, puml.contains("foo.bar()"));
    }

    @Test
    public void testBasicSequence() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { foo.bar(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("participant \"Caller\""));
        assertTrue(puml, puml.contains("participant \"A\""));
        // 呼び出しラベルはクラス名修飾付き (デフォルト qualifyMethodNames=true)
        assertTrue(puml, puml.contains("Caller -> A: A.run()"));
        assertTrue(puml, puml.contains("A -> foo: foo.bar()"));
        assertTrue(puml, puml.contains("activate A"));
        assertTrue(puml, puml.contains("deactivate A"));
    }

    @Test
    public void testSelfCall() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { helper(); } void helper() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: A.helper()"));
    }

    @Test
    public void testReceiverResolvedFromFieldType() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { IAudio mAudio; void run() { mAudio.setVolume(5); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> IAudio: IAudio.setVolume()"));
        assertTrue(puml, puml.contains("participant \"IAudio\""));
    }

    @Test
    public void testReceiverGenericFieldType() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { java.util.List<String> items; void run() { items.add(\"x\"); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        // List 型へのフィールド参照は List で participant 化
        assertTrue(puml, puml.contains("A -> List: List.add()"));
    }

    @Test
    public void testLocalVarLambdaBodyExpanded() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { Runnable r = () -> svc.work(); r.run(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        // ローカル変数に代入したラムダ本体の呼び出しが取りこぼされない
        assertTrue(puml, puml.contains("svc.work()"));
    }

    @Test
    public void testReturnNoteRespectsCommentMaxLength() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentMaxLength = 20;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { String run() { return \"alpha beta gamma delta epsilon zeta\"; } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // commentMaxLength=20 で return ノートが省略される
        assertTrue(puml, puml.contains("..."));
        assertFalse(puml, puml.contains("epsilon"));
    }

    @Test
    public void testQualifiedClassName() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package p; class A { void run() { foo(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "p.A", "run", null);
        assertTrue(puml, puml.contains("Caller -> A: A.run()"));
    }

    @Test
    public void testCallLabelWithoutQualifierWhenDisabled() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.qualifyMethodNames = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { foo.bar(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // クラス名修飾を無効化 → 旧フォーマット
        assertTrue(puml, puml.contains("Caller -> A: run()"));
        assertTrue(puml, puml.contains("A -> foo: bar()"));
    }

    @Test
    public void testCustomTitle() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.title = "Login Flow";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("title Login Flow"));
    }

    @Test
    public void testCustomCallerName() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.callerName = "User";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("User -> A: A.run()"));
    }

    @Test
    public void testEmptyBodyNoCalls() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void m() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "m", null);
        // 呼び出しが無くてもエラーにならず、開始/終了のみ出力
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("Caller -> A: A.m()"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testMultipleCalls() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Service s; void run() { s.a(); s.b(); s.c(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("a()"));
        assertTrue(puml, puml.contains("b()"));
        assertTrue(puml, puml.contains("c()"));
    }

    @Test
    public void testThisExplicit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { this.helper(); } void helper() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: A.helper()"));
    }

    @Test
    public void testLegendIncludedByDefault() {
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A { void m() {} }"),
                "A", "m", null);
        assertTrue(puml, puml.contains("legend top left"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A { void m() {} }"),
                "A", "m", o);
        assertFalse(puml, puml.contains("legend top left"));
    }

    // ------------ 制御構造 ------------

    @Test
    public void testOptForSingleIf() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Service s; void run() { if (x > 0) { s.go(); } } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        // 制御フロー guard も escapeLabel を通る (> は生のままで安全に表示される)
        assertTrue(puml, puml.contains("opt x > 0"));
        assertTrue(puml, puml.contains("A -> Service: Service.go()"));
        assertTrue(puml, puml.contains("end"));
    }

    @Test
    public void testSuperCallResolvesToSuperclassNotParticipantSuper() {
        // super.onCreate() は participant "super" ではなく親クラス名に解決する
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class MyActivity extends AppCompatActivity {"
                        + " void onCreate() { super.onCreate(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "MyActivity", "onCreate", null);
        assertTrue(puml, puml.contains("AppCompatActivity"));
        assertFalse("participant 'super' は出さない:\n" + puml, puml.contains("> super"));
        assertFalse(puml, puml.contains("\"super\""));
    }

    @Test
    public void testSuperCallWithoutKnownSuperclassFallsBackToSelf() {
        // 親クラスが不明 (暗黙 Object) の super 呼び出しは自クラス扱い
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void m() { super.toString(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "m", null);
        assertFalse(puml, puml.contains("\"super\""));
    }

    @Test
    public void testAltForIfElse() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { if (x) a(); else b(); } void a() {} void b() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("alt x"));
        assertTrue(puml, puml.contains("A -> A: A.a()"));
        // else 節
        assertTrue(puml, puml.matches("(?s).*alt x.*else.*A -> A: A\\.b\\(\\).*end.*"));
    }

    @Test
    public void testAltElseIfElse() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  if (a) { x(); }"
                        + "  else if (b) { y(); }"
                        + "  else { z(); }"
                        + "} void x(){} void y(){} void z(){} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("alt a"));
        assertTrue(puml, puml.contains("else b"));
        assertTrue(puml, puml.contains("end"));
    }

    @Test
    public void testLoopForWhile() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { while (hasNext()) { next(); } } "
                        + "boolean hasNext(){return false;} void next() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("loop while"));
        // 条件式内の呼び出し (hasNext()) は loop の前に出る
        assertTrue(puml, puml.matches("(?s).*A -> A: A\\.hasNext\\(\\).*loop while.*"));
    }

    @Test
    public void testLoopForFor() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { for (int i = 0; i < 10; i++) { step(); } } void step() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("loop for"));
        assertTrue(puml, puml.contains("A -> A: A.step()"));
    }

    @Test
    public void testGroupForTry() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  try { open(); } catch (IOException e) { log(); } finally { close(); }"
                        + "} void open(){} void log(){} void close(){} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("group try"));
        assertTrue(puml, puml.contains("else catch IOException e"));
        assertTrue(puml, puml.contains("else finally"));
        assertTrue(puml, puml.contains("A -> A: A.open()"));
        assertTrue(puml, puml.contains("A -> A: A.close()"));
    }

    @Test
    public void testCriticalForSynchronized() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Object lock; void run() { synchronized (lock) { foo(); } } void foo() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("critical synchronized(lock)"));
        assertTrue(puml, puml.contains("A -> A: A.foo()"));
    }

    // ------------ 多段トレース ------------

    @Test
    public void testMultiHopRecursion() {
        // run() -> helper() -> inner()。すべて A クラス内。デフォルトの maxDepth=5 で展開される。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { "
                        + "void run() { helper(); } "
                        + "void helper() { inner(); } "
                        + "void inner() { leaf(); } "
                        + "void leaf() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: A.helper()"));
        assertTrue(puml, puml.contains("A -> A: A.inner()"));
        assertTrue(puml, puml.contains("A -> A: A.leaf()"));
    }

    @Test
    public void testMultiHopStopsAtMaxDepth() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.maxDepth = 1;  // 起点のみ展開、呼び出し先には潜らない
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { helper(); } void helper() { inner(); } void inner() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("A -> A: A.helper()"));
        assertFalse(puml, puml.contains("A -> A: A.inner()"));
    }

    @Test
    public void testMultiHopCrossClass() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; void run() { b.act(); } } "
                        + "class B { void act() { work(); } void work() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> B: B.act()"));
        assertTrue(puml, puml.contains("B -> B: B.work()"));
        // 両方の participant が宣言されている
        assertTrue(puml, puml.contains("participant \"A\""));
        assertTrue(puml, puml.contains("participant \"B\""));
    }

    @Test
    public void testCycleDetection() {
        // run() -> run() の自己呼び出しは無限再帰せず、note で打ち切る
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { run(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: A.run()"));
        assertTrue(puml, puml.contains("recursive call"));
    }

    @Test
    public void testMutualRecursionCycleDetection() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void a() { b(); } void b() { a(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "a", null);
        assertTrue(puml, puml.contains("A -> A: A.b()"));
        // a → b → a でサイクル検出
        assertTrue(puml, puml.contains("recursive call"));
    }

    @Test
    public void testNestedControlStructures() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  for (int i = 0; i < n; i++) {"
                        + "    if (i > 0) { tick(); }"
                        + "  }"
                        + "} void tick() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("loop for"));
        assertTrue(puml, puml.contains("opt i > 0"));
        assertTrue(puml, puml.contains("A -> A: A.tick()"));
    }

    // ------------ 候補リスト ------------

    @Test
    public void testListCandidatesSortedByCallCount() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void zero() {} void two() { a(); b(); } void one() { c(); } }");
        List<PlantUmlSequenceDiagram.Candidate> list =
                PlantUmlSequenceDiagram.listCandidates(infos);
        assertEquals(3, list.size());
        // 呼び出し数で降順 (two=2 > one=1 > zero=0)
        assertEquals("A.two", list.get(0).getEntry());
        assertEquals(2, list.get(0).callCount);
        assertEquals("A.one", list.get(1).getEntry());
        assertEquals("A.zero", list.get(2).getEntry());
    }

    // ------------ プロジェクト内クラスの色付け ------------

    @Test
    public void testProjectClassColoredByDefault() {
        // A, B はプロジェクト内クラス、Service は外部クラス
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; Service s; void run() { b.act(); s.go(); } } "
                        + "class B { void act() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        // 解析済みクラスは色付き
        assertTrue(puml, puml.contains("participant \"A\" #LightSkyBlue"));
        assertTrue(puml, puml.contains("participant \"B\" #LightSkyBlue"));
        // 外部クラスと Caller は色なし
        assertTrue(puml, puml.contains("participant \"Service\"\n"));
        assertTrue(puml, puml.contains("participant \"Caller\"\n"));
    }

    @Test
    public void testProjectClassColorDisabled() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.highlightProjectClasses = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // 色は出さない
        assertFalse(puml, puml.contains("#LightSkyBlue"));
        assertTrue(puml, puml.contains("participant \"A\"\n"));
    }

    @Test
    public void testProjectClassColorCustom() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.projectClassColor = "#FFE4B5";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("participant \"A\" #FFE4B5"));
    }

    @Test
    public void testProjectClassColorEmptyStringDisablesColoring() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.projectClassColor = "";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertFalse(puml, puml.contains("#LightSkyBlue"));
        assertTrue(puml, puml.contains("participant \"A\"\n"));
    }

    @Test
    public void testListCandidatesExcludesAbstract() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface I { void m(); } class C { void run() {} }");
        List<PlantUmlSequenceDiagram.Candidate> list =
                PlantUmlSequenceDiagram.listCandidates(infos);
        // abstract メソッド (interface 内) は除外
        assertEquals(1, list.size());
        assertEquals("C.run", list.get(0).getEntry());
    }

    @Test
    public void testCommentsNoteStyleAtTop() {
        // 旧動作: PARTICIPANT_TOP で集約 note を出すモード
        String src =
                "/** A クラスのトップレベル説明。 */\n"
              + "class A {\n"
              + "  B b;\n"
              + "  /** run のエントリ JavaDoc。 */\n"
              + "  void run() { b.doIt(); }\n"
              + "}\n"
              + "/** B のメイン処理 */\n"
              + "class B {\n"
              + "  /** doIt の処理 */\n"
              + "  void doIt() {}\n"
              + "}\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        o.commentPlacement = PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // skinparam が出ている
        assertTrue(puml, puml.contains("skinparam noteBorderColor #008800"));
        assertTrue(puml, puml.contains("skinparam noteFontColor #008800"));
        // note over A ブロックにクラス JavaDoc と run() が含まれる
        assertTrue(puml, puml.contains("note over \"A\""));
        assertTrue(puml, puml.contains("A クラスのトップレベル説明。"));
        assertTrue(puml, puml.contains("run(): run のエントリ JavaDoc。"));
        // note over B ブロックに doIt が含まれる
        assertTrue(puml, puml.contains("note over \"B\""));
        assertTrue(puml, puml.contains("doIt(): doIt の処理"));
        // participant 宣言の後、本体シーケンスの前に note が出ていること
        int idxParticipant = puml.indexOf("participant \"A\"");
        int idxNote = puml.indexOf("note over \"A\"");
        int idxArrow = puml.indexOf("Caller -> A: A.run()");
        assertTrue("participant should appear before note", idxParticipant < idxNote);
        assertTrue("note should appear before arrow", idxNote < idxArrow);
    }

    @Test
    public void testCommentsInlineStyleAtCallSite() {
        // 既定 AT_CALL_SITE + INLINE: 各 call の直下に 1 行 note が出る
        String src =
                "class A {\n"
              + "  /** これは run メソッドのかなり長い説明文 */\n"
              + "  void run() {}\n"
              + "}\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        // 既定で AT_CALL_SITE + INLINE
        o.commentMaxLength = 12;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // skinparam は INLINE では出さない
        assertFalse(puml, puml.contains("skinparam noteBorderColor"));
        // call 行の直後に note right of "A" : <color:...>... が出る
        assertTrue(puml, puml.contains("note right of \"A\" : <color:#008800>"));
        // commentMaxLength=12 で長文が省略記号で切られる
        assertTrue(puml, puml.contains("…"));
        // call 行のすぐ後ろに note が来ていることを確認
        int idxArrow = puml.indexOf("Caller -> A: A.run()");
        int idxNote = puml.indexOf("note right of \"A\"");
        assertTrue("call arrow should appear before note", idxArrow >= 0 && idxArrow < idxNote);
    }

    @Test
    public void testNoCommentsOption() {
        String src =
                "/** A の説明 */\n"
              + "class A {\n"
              + "  /** run の説明 */\n"
              + "  void run() {}\n"
              + "}\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.showComments = false;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertFalse(puml, puml.contains("note over \"A\""));
        assertFalse(puml, puml.contains("note right of \"A\""));
        assertFalse(puml, puml.contains("skinparam noteBorderColor"));
        // 本体シーケンスは通常通り
        assertTrue(puml, puml.contains("Caller -> A: A.run()"));
    }

    @Test
    public void testCommentsAtCallSiteNoteStyle() {
        // AT_CALL_SITE + NOTE: 呼び出し行の直下に複数行 note ブロック
        String src =
                "class A {\n"
              + "  B b;\n"
              + "  void run() { b.doIt(); }\n"
              + "}\n"
              + "class B {\n"
              + "  /** doIt の処理\n"
              + "   *  2 行目の説明 */\n"
              + "  void doIt() {}\n"
              + "}\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // 呼び出しの直下に note right of "B" ブロック
        assertTrue(puml, puml.contains("A -> B: B.doIt()"));
        assertTrue(puml, puml.contains("note right of \"B\""));
        assertTrue(puml, puml.contains("doIt の処理"));
        assertTrue(puml, puml.contains("2 行目の説明"));
        assertTrue(puml, puml.contains("end note"));
    }

    @Test
    public void testMethodBodyCommentsCollected() {
        // 旧 PARTICIPANT_TOP モードで本体内コメントが note ブロックに集約される
        String src =
                "class A {\n"
              + "  /** run JavaDoc */\n"
              + "  void run() {\n"
              + "    // step1: 前処理\n"
              + "    /* step2: 本処理 */\n"
              + "    b.doIt();\n"
              + "  }\n"
              + "  B b;\n"
              + "}\n"
              + "class B { void doIt() {} }\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        o.commentPlacement = PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("// step1: 前処理"));
        assertTrue(puml, puml.contains("// step2: 本処理"));
    }

    @Test
    public void testMultiLineBlockCommentAppearsInUml() {
        // 2行ブロックコメントが両行とも UML の note に出力されることを検証
        String src =
                "class A {\n"
              + "  /** run JavaDoc */\n"
              + "  void run() {\n"
              + "    /*\n"
              + "     * 行1\n"
              + "     * 行2\n"
              + "     */\n"
              + "    b.doIt();\n"
              + "  }\n"
              + "  B b;\n"
              + "}\n"
              + "class B { void doIt() {} }\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        o.commentPlacement = PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("// 行1"));
        assertTrue(puml, puml.contains("// 行2"));
    }

    @Test
    public void testBodyCommentsCanBeDisabled() {
        String src =
                "class A {\n"
              + "  void run() {\n"
              + "    // 内部メモ\n"
              + "    b.doIt();\n"
              + "  }\n"
              + "  B b;\n"
              + "}\n"
              + "class B { void doIt() {} }\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        o.commentPlacement = PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP;
        o.showMethodBodyComments = false;
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertFalse(puml, puml.contains("// 内部メモ"));
    }

    // ------------ 隠しパラメータ (participants filter) ------------

    @Test
    public void testHiddenParticipantSkipsArrow() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.hiddenParticipants = new java.util.HashSet<>();
        o.hiddenParticipants.add("Service");
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Service s; void run() { s.go(); other(); } void other() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // Service への呼び出しが消えている
        assertFalse(puml, puml.contains("Service"));
        // 同時に他の呼び出しは残る
        assertTrue(puml, puml.contains("A -> A: A.other()"));
    }

    @Test
    public void testHiddenParticipantSuppressesRecursion() {
        // B が隠されたら B 内部の呼び出しも展開されない
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.hiddenParticipants = new java.util.HashSet<>();
        o.hiddenParticipants.add("B");
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; void run() { b.act(); } } "
                        + "class B { void act() { work(); } void work() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertFalse(puml, puml.contains("A -> B"));
        assertFalse(puml, puml.contains("B -> B"));
        assertFalse(puml, puml.contains("participant \"B\""));
    }

    @Test
    public void testCollectParticipantsReturnsAllReachable() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; Service s; void run() { b.act(); s.go(); } } "
                        + "class B { void act() { work(); } void work() {} }");
        java.util.Set<String> ps =
                PlantUmlSequenceDiagram.collectParticipants(infos, "A", "run", null);
        // 隠しがなくても全 participant が列挙される
        assertTrue(ps.toString(), ps.contains("Caller"));
        assertTrue(ps.toString(), ps.contains("A"));
        assertTrue(ps.toString(), ps.contains("B"));
        assertTrue(ps.toString(), ps.contains("Service"));
    }

    @Test
    public void testAidlBinderImplGetsBinderStereotype() {
        // CarFooService が ICarFoo.Stub を継承している場合、参加者宣言に
        // <<binder>> ステレオタイプが付く。色付けより後に追加されるため、
        // 名前と <<binder>> が同じ participant 行にあることを確認する。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { CarFooService s; void run() { s.act(); } } "
                        + "class CarFooService extends ICarFoo.Stub {"
                        + "  public void act() { } "
                        + "}");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        boolean found = false;
        for (String line : puml.split("\n")) {
            if (line.startsWith("participant \"CarFooService\"")
                    && line.contains("<<binder>>")) {
                found = true;
                break;
            }
        }
        assertTrue(puml, found);
    }

    @Test
    public void testNonBinderParticipantDoesNotGetBinderStereotype() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; void run() { b.act(); } } "
                        + "class B { void act() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertFalse(puml, puml.contains("<<binder>>"));
    }

    @Test
    public void testFirstArgConstantSurfacedOnCallLabel() {
        // VHAL property のような定数シンボル引数がシーケンス図ラベルに添えられる
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { CarPropertyManager mgr;"
                        + "  void run() {"
                        + "    mgr.getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET);"
                        + "  } } "
                        + "class CarPropertyManager {"
                        + "  void getProperty(int id) {}"
                        + "}");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml,
                puml.contains("getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET)"));
    }

    @Test
    public void testNonConstantArgFallsBackToEmptyParens() {
        // 通常のローカル変数引数では従来通り `()` 表記
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; void run(int x) { b.act(x); } } "
                        + "class B { void act(int v) {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("act()"));
        assertFalse(puml, puml.contains("act(x)"));
    }

    // ---- return / throw in sequence diagram ----

    @Test
    public void testReturnStatementEmitsNoteOver() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; String run() { b.fetch(); return \"ok\"; } } "
                        + "class B { String fetch() { return \"\"; } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue("return should appear as note over: " + puml,
                puml.contains("note over") && puml.contains("return"));
    }

    @Test
    public void testVoidReturnEmitsReturnNote() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { return; } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue("void return should emit 'return' note: " + puml,
                puml.contains("note over") && puml.contains(": return"));
    }

    @Test
    public void testThrowStatementEmitsNoteOver() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run(String s) {\n"
                + "  if (s == null) throw new IllegalArgumentException(\"null\");\n"
                + "} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue("throw should appear as note over: " + puml,
                puml.contains("note over") && puml.contains("throw"));
    }

    // ---- [High] walkStatements が InlineComment を note として出力するテスト ----

    @Test
    public void testWalkStatementsEmitsInlineCommentAsNote() {
        // メソッド本体内のインラインコメントが walkStatements 経由で
        // note over として出力されることを確認する。
        // JavaStructureExtractor がメソッド本体のインラインコメントを
        // InlineComment Statement として格納するかを検証する。
        List<JavaMethodInfo.Statement> stmts = new java.util.ArrayList<>();
        stmts.add(new JavaMethodInfo.InlineComment("処理開始"));
        stmts.add(new JavaMethodInfo.Call(null, "helper"));

        // JavaClassInfo を手動構築
        JavaClassInfo cls = new JavaClassInfo();
        cls.setPackageName("pkg");
        cls.setSimpleName("Svc");
        cls.setKind(JavaClassInfo.Kind.CLASS);
        cls.setDetailed(true);
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("run");
        m.setReturnType("void");
        m.getStatements().addAll(stmts);
        cls.getMethods().add(m);

        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(
                java.util.Collections.singletonList(cls), "Svc", "run", opts);
        // InlineComment が note over として出力されること
        assertTrue("InlineComment should appear as note over: " + puml,
                puml.contains("note over") && puml.contains("処理開始"));
    }

    // 追加の InlineComment 出力検証・オーバーロード解決テストは
    // PlantUmlSequenceDiagramInlineCommentAndOverloadTest に分離 (FileLength 対策)。
}
