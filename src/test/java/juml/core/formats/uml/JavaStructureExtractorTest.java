// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * JavaStructureExtractor のユニットテスト。
 */
public class JavaStructureExtractorTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        JavaStructureExtractor.extract(null);
    }

    @Test
    public void testEmptyInput() {
        assertTrue(JavaStructureExtractor.extract("").isEmpty());
    }

    @Test
    public void testSimpleClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "package com.x; class Foo { int a; void m() {} }");
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        assertEquals("com.x", c.getPackageName());
        assertEquals("Foo", c.getSimpleName());
        assertEquals(JavaClassInfo.Kind.CLASS, c.getKind());
        assertEquals(1, c.getFields().size());
        assertEquals("a", c.getFields().get(0).getName());
        assertEquals("int", c.getFields().get(0).getType());
        assertEquals(1, c.getMethods().size());
        assertEquals("m", c.getMethods().get(0).getName());
        assertEquals("void", c.getMethods().get(0).getReturnType());
    }

    @Test
    public void testClassStartLine() {
        // クラス宣言行 (アノテーション行ではなく名前の行) がソースジャンプ用に取れること
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "package com.x;\n"          // 1
                        + "\n"               // 2
                        + "@Deprecated\n"    // 3
                        + "class Foo {\n"    // 4
                        + "  void m() {}\n"  // 5
                        + "}\n");
        assertEquals(1, cs.size());
        assertEquals(4, cs.get(0).getStartLine());
        assertEquals(5, cs.get(0).getMethods().get(0).getStartLine());
    }

    @Test
    public void testInterface() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "interface I { void foo(); int bar(); }");
        assertEquals(1, cs.size());
        assertEquals(JavaClassInfo.Kind.INTERFACE, cs.get(0).getKind());
        assertEquals(2, cs.get(0).getMethods().size());
        assertTrue(cs.get(0).getMethods().get(0).isAbstract());
    }

    @Test
    public void testEnum() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "enum E { A, B, C; int x() { return 1; } }");
        assertEquals(JavaClassInfo.Kind.ENUM, cs.get(0).getKind());
        assertEquals(1, cs.get(0).getMethods().size());
        assertEquals("x", cs.get(0).getMethods().get(0).getName());
    }

    @Test
    public void testAnnotationDecl() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "public @interface MyAnno { String value() default \"\"; }");
        assertEquals(1, cs.size());
        assertEquals(JavaClassInfo.Kind.ANNOTATION, cs.get(0).getKind());
        assertEquals("MyAnno", cs.get(0).getSimpleName());
    }

    @Test
    public void testExtendsAndImplements() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Foo extends Bar implements I1, I2 { }");
        JavaClassInfo c = cs.get(0);
        assertEquals("Bar", c.getSuperClass());
        assertEquals(2, c.getInterfaces().size());
        assertTrue(c.getInterfaces().contains("I1"));
        assertTrue(c.getInterfaces().contains("I2"));
    }

    @Test
    public void testGenericClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Foo<T extends Comparable<T>> extends Bar<T> { T item; }");
        JavaClassInfo c = cs.get(0);
        assertEquals("Foo", c.getSimpleName());
        assertEquals("Bar<T>", c.getSuperClass());
        assertEquals("T", c.getFields().get(0).getType());
    }

    @Test
    public void testNestedClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Outer { void o() {} class Inner { void i() {} } }");
        assertEquals(2, cs.size());
        // 内側が先に処理 (parseClassBody 内で再帰されるため) されるので、Outer/Inner どちらも見つかる
        boolean foundOuter = false;
        boolean foundInner = false;
        for (JavaClassInfo c : cs) {
            if ("Outer".equals(c.getSimpleName())) {
                foundOuter = true;
            }
            if ("Inner".equals(c.getSimpleName())) {
                foundInner = true;
                assertEquals("Outer", c.getEnclosingClass());
                assertEquals("Outer.Inner", c.getQualifiedName());
            }
        }
        assertTrue(foundOuter);
        assertTrue(foundInner);
    }

    @Test
    public void testFieldVisibility() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { public int a; private String b; protected long c; int d; }");
        List<JavaFieldInfo> fs = cs.get(0).getFields();
        assertEquals(Visibility.PUBLIC, fs.get(0).getVisibility());
        assertEquals(Visibility.PRIVATE, fs.get(1).getVisibility());
        assertEquals(Visibility.PROTECTED, fs.get(2).getVisibility());
        assertEquals(Visibility.PACKAGE, fs.get(3).getVisibility());
    }

    @Test
    public void testFieldModifiers() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { public static final int X = 1; }");
        JavaFieldInfo f = cs.get(0).getFields().get(0);
        assertTrue(f.isStatic());
        assertTrue(f.isFinal());
        assertEquals(Visibility.PUBLIC, f.getVisibility());
    }

    @Test
    public void testMethodWithMultipleParams() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { int add(int a, int b, String s) { return a + b; } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(3, m.getParameterTypes().size());
        assertEquals("int", m.getParameterTypes().get(0));
        assertEquals("a", m.getParameterNames().get(0));
        assertEquals("String", m.getParameterTypes().get(2));
        assertEquals("s", m.getParameterNames().get(2));
    }

    @Test
    public void testGenericParameterTypes() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { void m(Map<String, List<Integer>> map, int x) {} }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(2, m.getParameterTypes().size());
        assertEquals("Map<String, List<Integer>>", m.getParameterTypes().get(0));
        assertEquals("int", m.getParameterTypes().get(1));
    }

    @Test
    public void testConstructor() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Foo { public Foo(int x) { this.x = x; } private Foo() {} }");
        List<JavaMethodInfo> ms = cs.get(0).getMethods();
        assertEquals(2, ms.size());
        assertTrue(ms.get(0).isConstructor());
        assertTrue(ms.get(1).isConstructor());
        assertEquals(Visibility.PUBLIC, ms.get(0).getVisibility());
        assertEquals(Visibility.PRIVATE, ms.get(1).getVisibility());
    }

    @Test
    public void testAbstractMethod() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "abstract class C { abstract void m(); void n() {} }");
        List<JavaMethodInfo> ms = cs.get(0).getMethods();
        assertEquals(2, ms.size());
        assertTrue(ms.get(0).isAbstract());
        assertFalse(ms.get(1).isAbstract());
    }

    @Test
    public void testStaticMethod() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { public static int sq(int n) { return n * n; } }");
        assertTrue(cs.get(0).getMethods().get(0).isStatic());
    }

    @Test
    public void testAnnotations() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "@Foo @Bar(value=1) class C {\n"
                        + "  @AddedIn(majorVersion=33) public void m() {}\n"
                        + "}");
        JavaClassInfo c = cs.get(0);
        assertEquals(2, c.getAnnotations().size());
        assertTrue(c.getAnnotations().contains("Foo"));
        assertTrue(c.getMethods().get(0).getAnnotations().stream()
                .anyMatch(a -> a.startsWith("AddedIn")));
    }

    @Test
    public void testCallExtraction() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { x(); foo.bar(); a.b.c(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(3, m.getCalls().size());
        assertEquals("", m.getCalls().get(0).getReceiver());
        assertEquals("x", m.getCalls().get(0).getMethodName());
        assertEquals("foo", m.getCalls().get(1).getReceiver());
        assertEquals("bar", m.getCalls().get(1).getMethodName());
        assertEquals("a.b", m.getCalls().get(2).getReceiver());
        assertEquals("c", m.getCalls().get(2).getMethodName());
    }

    @Test
    public void testCallExtractionIgnoresControlKeywords() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { if (x) a(); while (y) b(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // if/while は除外、a() と b() のみ
        assertEquals(2, m.getCalls().size());
    }

    @Test
    public void testCallExtractionIgnoresNew() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { Object o = new Object(); foo(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // new Object() は除外、foo() のみ
        assertEquals(1, m.getCalls().size());
        assertEquals("foo", m.getCalls().get(0).getMethodName());
    }

    @Test
    public void testReturnStatementRecorded() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { int m() { return 42; } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // 文ツリーに Return が含まれる
        boolean found = false;
        for (JavaMethodInfo.Statement s : m.getStatements()) {
            if (s instanceof JavaMethodInfo.Return) {
                assertEquals("42", ((JavaMethodInfo.Return) s).getExpression());
                found = true;
            }
        }
        assertTrue("Return statement should be recorded", found);
    }

    @Test
    public void testVoidReturnStatementRecorded() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { if (x) return; foo(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // Block (if) 内の Return
        JavaMethodInfo.Block block = (JavaMethodInfo.Block) m.getStatements().get(0);
        JavaMethodInfo.Branch ifBranch = block.getBranches().get(0);
        boolean found = false;
        for (JavaMethodInfo.Statement s : ifBranch.getBody()) {
            if (s instanceof JavaMethodInfo.Return) {
                assertEquals("", ((JavaMethodInfo.Return) s).getExpression());
                found = true;
            }
        }
        assertTrue("Void return should be recorded with empty expression", found);
    }

    @Test
    public void testReturnPreservesCallInExpression() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { int m() { return compute(x); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // return 内の compute() 呼び出しは Call として残る
        assertEquals(1, m.getCalls().size());
        assertEquals("compute", m.getCalls().get(0).getMethodName());
    }

    @Test
    public void testThrowStatementRecorded() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { throw new IllegalArgumentException(\"x\"); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        boolean found = false;
        for (JavaMethodInfo.Statement s : m.getStatements()) {
            if (s instanceof JavaMethodInfo.Throw) {
                String expr = ((JavaMethodInfo.Throw) s).getExpression();
                assertTrue("expression should contain 'new IllegalArgumentException'",
                        expr.contains("IllegalArgumentException"));
                found = true;
            }
        }
        assertTrue("Throw statement should be recorded", found);
    }

    @Test
    public void testBreakAndContinueRecorded() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() {"
                        + "  while (true) {"
                        + "    if (a) break;"
                        + "    if (b) continue;"
                        + "  }"
                        + "} }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Block whileBlock = (JavaMethodInfo.Block) m.getStatements().get(0);
        JavaMethodInfo.Branch whileBody = whileBlock.getBranches().get(0);
        boolean foundBreak = false;
        boolean foundContinue = false;
        for (JavaMethodInfo.Statement s : whileBody.getBody()) {
            if (s instanceof JavaMethodInfo.Block) {
                JavaMethodInfo.Block ifb = (JavaMethodInfo.Block) s;
                for (JavaMethodInfo.Statement inner : ifb.getBranches().get(0).getBody()) {
                    if (inner instanceof JavaMethodInfo.Break) {
                        foundBreak = true;
                    }
                    if (inner instanceof JavaMethodInfo.Continue) {
                        foundContinue = true;
                    }
                }
            }
        }
        assertTrue("Break should be recorded", foundBreak);
        assertTrue("Continue should be recorded", foundContinue);
    }

    @Test
    public void testGetCallsIgnoresControlFlowStatements() {
        // Return/Throw/Break/Continue は getCalls() に影響しない
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { foo(); return; bar(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // foo と bar が記録される (return; の後の bar(); は到達不能だがパーサは解析する)
        assertEquals(2, m.getCalls().size());
    }

    @Test
    public void testMultipleClassesInFile() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void a() {} } class B { void b() {} }");
        assertEquals(2, cs.size());
    }

    @Test
    public void testCommentsAreIgnored() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "// header\n/** javadoc */\nclass A {\n"
                        + "  /* block */ int x; // line\n"
                        + "  void m() {}\n}");
        assertEquals(1, cs.size());
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals(1, cs.get(0).getMethods().size());
    }

    @Test
    public void testJavadocAttachedToClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "/** クラスの説明 */\nclass A {}");
        assertEquals("クラスの説明", cs.get(0).getComment());
    }

    @Test
    public void testJavadocAttachedToFieldAndMethod() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  /** 数値フィールド */\n"
                        + "  int x;\n"
                        + "  /** 計算する */\n"
                        + "  int calc() { return 0; }\n"
                        + "}");
        JavaClassInfo c = cs.get(0);
        assertEquals("数値フィールド", c.getFields().get(0).getComment());
        assertEquals("計算する", c.getMethods().get(0).getComment());
    }

    @Test
    public void testLineCommentsMergedAndAttachedToMember() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  // 1 行目\n"
                        + "  // 2 行目\n"
                        + "  int x;\n"
                        + "}");
        // 連続行コメントが改行で結合されて取れる
        assertEquals("1 行目\n2 行目", cs.get(0).getFields().get(0).getComment());
    }

    @Test
    public void testCommentNotAttachedAcrossOtherDeclarations() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  /** doc for foo */\n"
                        + "  int foo;\n"
                        + "  int bar;\n"
                        + "}");
        assertEquals("doc for foo", cs.get(0).getFields().get(0).getComment());
        // bar には付かない
        assertNull(cs.get(0).getFields().get(1).getComment());
    }

    @Test
    public void testJavadocStripsAtTagLines() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  /**\n"
                        + "   * 引数を 2 倍にする。\n"
                        + "   * @param x 入力値\n"
                        + "   * @return 2 倍\n"
                        + "   */\n"
                        + "  int twice(int x) { return x * 2; }\n"
                        + "}");
        String c = cs.get(0).getMethods().get(0).getComment();
        assertNotNull(c);
        assertTrue(c, c.contains("引数を 2 倍にする。"));
        assertFalse(c, c.contains("@param"));
        assertFalse(c, c.contains("@return"));
    }

    @Test
    public void testMethodBodyCommentsExtracted() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  void run() {\n"
                        + "    // step1: 前処理\n"
                        + "    /* step2: 本処理 */\n"
                        + "    doIt();\n"
                        + "    // step3: 後処理\n"
                        + "  }\n"
                        + "  void doIt() {}\n"
                        + "}");
        JavaMethodInfo run = cs.get(0).getMethods().get(0);
        List<String> bc = run.getBodyComments();
        assertEquals(3, bc.size());
        assertEquals("step1: 前処理", bc.get(0));
        assertEquals("step2: 本処理", bc.get(1));
        assertEquals("step3: 後処理", bc.get(2));
        // 別メソッドの本体コメントは混ざらない
        JavaMethodInfo doIt = cs.get(0).getMethods().get(1);
        assertTrue(doIt.getBodyComments().isEmpty());
    }

    @Test
    public void testMethodBodyMultiLineBlockComment() {
        // 2行以上のブロックコメントが丸ごと拾えるかを検証
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  void run() {\n"
                        + "    /*\n"
                        + "     * 行1\n"
                        + "     * 行2\n"
                        + "     */\n"
                        + "    doIt();\n"
                        + "  }\n"
                        + "  void doIt() {}\n"
                        + "}");
        JavaMethodInfo run = cs.get(0).getMethods().get(0);
        List<String> bc = run.getBodyComments();
        assertEquals(1, bc.size());
        assertEquals("行1\n行2", bc.get(0));
    }

    @Test
    public void testMethodBodyConsecutiveLineComments() {
        // 連続する // コメントが両方拾えるかを検証
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  void run() {\n"
                        + "    // 行1\n"
                        + "    // 行2\n"
                        + "    doIt();\n"
                        + "  }\n"
                        + "  void doIt() {}\n"
                        + "}");
        JavaMethodInfo run = cs.get(0).getMethods().get(0);
        List<String> bc = run.getBodyComments();
        assertEquals(2, bc.size());
        assertEquals("行1", bc.get(0));
        assertEquals("行2", bc.get(1));
    }

    @Test
    public void testEnumConstantsCaptured() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "enum Color { RED, GREEN, BLUE }");
        assertEquals(3, cs.get(0).getEnumConstants().size());
        assertEquals("RED", cs.get(0).getEnumConstants().get(0));
        assertEquals("GREEN", cs.get(0).getEnumConstants().get(1));
        assertEquals("BLUE", cs.get(0).getEnumConstants().get(2));
    }

    @Test
    public void testEnumConstantsWithArgsAndBody() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "enum Op {\n"
                        + "  ADD(\"+\"),\n"
                        + "  SUB(\"-\") { int apply(int a, int b) { return a - b; } };\n"
                        + "  Op(String s) {}\n"
                        + "}");
        // 定数 2 つが取れる (引数や無名サブクラス body は読み飛ばす)
        assertEquals(2, cs.get(0).getEnumConstants().size());
        assertEquals("ADD", cs.get(0).getEnumConstants().get(0));
        assertEquals("SUB", cs.get(0).getEnumConstants().get(1));
        // 通常メンバー (コンストラクタ) も拾える
        assertFalse(cs.get(0).getMethods().isEmpty());
    }

    @Test
    public void testStringWithBraces() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { String s = \"{foo}\"; void m() {} }");
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals(1, cs.get(0).getMethods().size());
    }

    @Test
    public void testQualifiedName() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "package p.q; class C {}");
        assertEquals("p.q.C", cs.get(0).getQualifiedName());
    }

    @Test
    public void testTextBlockFieldDoesNotBreakParsing() {
        String src = "class A {\n"
                + "  String s = \"\"\"\n"
                + "    hello\n"
                + "    world\n"
                + "    \"\"\";\n"
                + "  void m() {}\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        assertEquals(1, cs.size());
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals("s", cs.get(0).getFields().get(0).getName());
        assertEquals(1, cs.get(0).getMethods().size());
        assertEquals("m", cs.get(0).getMethods().get(0).getName());
    }

    @Test
    public void testRecordTopLevel() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "package p; record Point(int x, int y) {}");
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        assertEquals("Point", c.getSimpleName());
        assertEquals(JavaClassInfo.Kind.RECORD, c.getKind());
        assertEquals("p", c.getPackageName());
    }

    @Test
    public void testRecordWithBody() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "record Pair(String a, String b) { int sum() { return 0; } }");
        assertEquals(1, cs.size());
        assertEquals(JavaClassInfo.Kind.RECORD, cs.get(0).getKind());
        assertEquals(1, cs.get(0).getMethods().size());
        assertEquals("sum", cs.get(0).getMethods().get(0).getName());
    }

    @Test
    public void testRecordImplementsInterface() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "record Foo(int x) implements Comparable<Foo> {}");
        assertEquals(1, cs.size());
        assertEquals(JavaClassInfo.Kind.RECORD, cs.get(0).getKind());
        assertEquals(1, cs.get(0).getInterfaces().size());
    }

    @Test
    public void testRecordNested() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { record Inner(int x) {} }");
        assertEquals(2, cs.size());
        JavaClassInfo inner = cs.stream()
                .filter(c -> c.getKind() == JavaClassInfo.Kind.RECORD)
                .findFirst().orElse(null);
        assertNotNull(inner);
        assertEquals("Inner", inner.getSimpleName());
        assertEquals("A", inner.getEnclosingClass());
    }

    @Test
    public void testMethodReferenceCall() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { list.forEach(Foo::bar); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        long barCalls = m.getStatements().stream()
                .filter(s -> s instanceof JavaMethodInfo.Call)
                .map(s -> (JavaMethodInfo.Call) s)
                .filter(c -> "bar".equals(c.getMethodName()))
                .count();
        assertEquals(1, barCalls);
    }

    @Test
    public void testMethodReferenceReceiver() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { x(a.b.c::meth); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Call methCall = m.getStatements().stream()
                .filter(s -> s instanceof JavaMethodInfo.Call)
                .map(s -> (JavaMethodInfo.Call) s)
                .filter(c -> "meth".equals(c.getMethodName()))
                .findFirst().orElse(null);
        assertNotNull(methCall);
        assertEquals("a.b.c", methCall.getReceiver());
    }

    @Test
    public void testConstructorReferenceIsExcluded() {
        // String::new はコンストラクタ参照なので Call として記録しない
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { x(String::new); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        long newCalls = m.getStatements().stream()
                .filter(s -> s instanceof JavaMethodInfo.Call)
                .map(s -> (JavaMethodInfo.Call) s)
                .filter(c -> "new".equals(c.getMethodName()))
                .count();
        assertEquals(0, newCalls);
    }

    @Test
    public void testMultiVarFieldSimple() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { int a, b; }");
        assertEquals(2, cs.get(0).getFields().size());
        assertEquals("a", cs.get(0).getFields().get(0).getName());
        assertEquals("int", cs.get(0).getFields().get(0).getType());
        assertEquals("b", cs.get(0).getFields().get(1).getName());
        assertEquals("int", cs.get(0).getFields().get(1).getType());
    }

    @Test
    public void testMultiVarFieldWithInitializers() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { int a = 1, b = 2; }");
        assertEquals(2, cs.get(0).getFields().size());
        assertEquals("a", cs.get(0).getFields().get(0).getName());
        assertEquals("b", cs.get(0).getFields().get(1).getName());
    }

    @Test
    public void testMultiVarFieldWithModifiers() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { public static final int X = 1, Y = 2, Z = 3; }");
        assertEquals(3, cs.get(0).getFields().size());
        for (JavaFieldInfo f : cs.get(0).getFields()) {
            assertTrue(f.isStatic());
            assertTrue(f.isFinal());
            assertEquals("int", f.getType());
        }
    }

    @Test
    public void testMultiVarFieldWithArrayBrackets() {
        // int a[], b: a は int[], b は int
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { int a[], b; }");
        assertEquals(2, cs.get(0).getFields().size());
        assertEquals("a", cs.get(0).getFields().get(0).getName());
        assertEquals("b", cs.get(0).getFields().get(1).getName());
        assertEquals("int", cs.get(0).getFields().get(1).getType());
    }

    @Test
    public void testSealedClassModifier() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "public sealed class Shape permits Circle, Rectangle {}");
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        assertTrue(c.getModifiers().contains("public"));
        assertTrue(c.getModifiers().contains("sealed"));
        assertEquals("Shape", c.getSimpleName());
        // permits は interfaces ではなく permittedTypes に入る (クラス図の逆向き矢印防止)
        assertTrue(c.getPermittedTypes().contains("Circle"));
        assertTrue(c.getPermittedTypes().contains("Rectangle"));
        assertTrue(c.getInterfaces().isEmpty());
    }

    @Test
    public void testSealedInterfaceModifier() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "sealed interface Result permits Ok, Err {}");
        assertEquals(1, cs.size());
        assertEquals(JavaClassInfo.Kind.INTERFACE, cs.get(0).getKind());
        assertTrue(cs.get(0).getModifiers().contains("sealed"));
    }

    @Test
    public void testNestedAnnotationInFieldType() {
        // ネストアノテーション @Foo(bar = @Baz("x")) で stripAnnotations が
        // 早期終了せず最終的に型 (String) のみが残ること
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { @Foo(bar = @Baz(\"x\")) String s; }");
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals("s", cs.get(0).getFields().get(0).getName());
        assertEquals("String", cs.get(0).getFields().get(0).getType());
    }

    @Test
    public void testDeeplyNestedGenericsTriggers3CloseAngles() {
        // Foo<Bar<Baz<Qux>>> は `>>>` 1 トークンで 3 階層閉じる。
        // skipBalanced / readTypeName が depth 補正で早期終了しないこと
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { Foo<Bar<Baz<Qux>>> field; void m() {} }");
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals("field", cs.get(0).getFields().get(0).getName());
        assertEquals(1, cs.get(0).getMethods().size());
        assertEquals("m", cs.get(0).getMethods().get(0).getName());
    }

    @Test
    public void testLocalClassInMethodBody() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Outer { void m() { class Local { int x; } } }");
        assertEquals(2, cs.size());
        JavaClassInfo local = cs.stream()
                .filter(c -> "Local".equals(c.getSimpleName()))
                .findFirst().orElse(null);
        assertNotNull(local);
        assertEquals(JavaClassInfo.Kind.CLASS, local.getKind());
        assertEquals(1, local.getFields().size());
    }

    @Test
    public void testLocalRecordWithModifier() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Outer { void m() { final record P(int x) {} } }");
        JavaClassInfo p = cs.stream()
                .filter(c -> "P".equals(c.getSimpleName()))
                .findFirst().orElse(null);
        assertNotNull(p);
        assertEquals(JavaClassInfo.Kind.RECORD, p.getKind());
        assertTrue(p.getModifiers().contains("final"));
    }

    @Test
    public void testThrowsClauseSingle() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() throws java.io.IOException {} }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(1, m.getThrowsTypes().size());
        assertEquals("java.io.IOException", m.getThrowsTypes().get(0));
    }

    @Test
    public void testThrowsClauseMultiple() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() throws IOException, SQLException, RuntimeException; }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(3, m.getThrowsTypes().size());
        assertEquals("IOException", m.getThrowsTypes().get(0));
        assertEquals("SQLException", m.getThrowsTypes().get(1));
        assertEquals("RuntimeException", m.getThrowsTypes().get(2));
    }

    @Test
    public void testThrowsClauseAbsent() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() {} }");
        assertTrue(cs.get(0).getMethods().get(0).getThrowsTypes().isEmpty());
    }

    @Test
    public void testUnicodeEscapeInClassName() {
        // Foo → Foo
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class \\u0046oo { int x; }");
        assertEquals(1, cs.size());
        assertEquals("Foo", cs.get(0).getSimpleName());
    }

    @Test
    public void testUnicodeEscapedKeyword() {
        // class → class
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "\\u0063lass Bar {}");
        assertEquals(1, cs.size());
        assertEquals("Bar", cs.get(0).getSimpleName());
    }

    @Test
    public void testTextBlockWithBracesAndQuotes() {
        // テキストブロック内の { } や " は構造を壊さない
        String src = "class A {\n"
                + "  String json = \"\"\"\n"
                + "    { \"key\": \"value\" }\n"
                + "    \"\"\";\n"
                + "  int x;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        assertEquals(1, cs.size());
        assertEquals(2, cs.get(0).getFields().size());
    }
}
