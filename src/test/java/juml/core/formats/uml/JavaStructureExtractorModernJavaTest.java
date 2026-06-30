// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * JavaStructureExtractor の Java 9+ / 14+ / 21+ 言語機能 (モジュール宣言・
 * switch 式・yield 文・パターン case + when ガード) のテスト。
 *
 * <p>本体テストの {@link JavaStructureExtractorTest} はファイルサイズ上限の
 * 都合で分割している。機能カテゴリで分けることで、後続フェーズ
 * (Java 23 unnamed patterns 等) の追加先も明確にする。</p>
 */
public class JavaStructureExtractorModernJavaTest {

    // -------- module-info.java (JLS §7.7) --------

    @Test
    public void testModuleDeclSimple() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "module com.example.foo { }");
        assertEquals(1, cs.size());
        JavaClassInfo m = cs.get(0);
        assertEquals(JavaClassInfo.Kind.MODULE, m.getKind());
        assertEquals("com.example.foo", m.getSimpleName());
        assertTrue(m.getModuleDirectives().isEmpty());
        assertFalse(m.getModifiers().contains("open"));
    }

    @Test
    public void testModuleDeclOpenModifier() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "open module a.b { }");
        assertEquals(1, cs.size());
        JavaClassInfo m = cs.get(0);
        assertEquals(JavaClassInfo.Kind.MODULE, m.getKind());
        assertEquals("a.b", m.getSimpleName());
        assertTrue(m.getModifiers().contains("open"));
    }

    @Test
    public void testModuleRequiresDirectives() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "module x {"
                        + "  requires java.base;"
                        + "  requires transitive java.sql;"
                        + "  requires static java.compiler;"
                        + "  requires transitive static java.desktop;"
                        + "}");
        JavaClassInfo m = cs.get(0);
        List<JavaModuleDirective> ds = m.getModuleDirectives();
        assertEquals(4, ds.size());

        JavaModuleDirective d0 = ds.get(0);
        assertEquals(JavaModuleDirective.Kind.REQUIRES, d0.getKind());
        assertEquals("java.base", d0.getName());
        assertTrue(d0.getModifiers().isEmpty());

        JavaModuleDirective d1 = ds.get(1);
        assertEquals("java.sql", d1.getName());
        assertEquals(1, d1.getModifiers().size());
        assertEquals("transitive", d1.getModifiers().get(0));

        JavaModuleDirective d2 = ds.get(2);
        assertEquals("java.compiler", d2.getName());
        assertEquals(1, d2.getModifiers().size());
        assertEquals("static", d2.getModifiers().get(0));

        JavaModuleDirective d3 = ds.get(3);
        assertEquals("java.desktop", d3.getName());
        assertEquals(2, d3.getModifiers().size());
        assertTrue(d3.getModifiers().contains("transitive"));
        assertTrue(d3.getModifiers().contains("static"));
    }

    @Test
    public void testModuleExportsAndOpens() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "module x {"
                        + "  exports a.b.api;"
                        + "  exports a.b.internal to c.d, e.f;"
                        + "  opens a.b.impl;"
                        + "  opens a.b.spi to c.d;"
                        + "}");
        List<JavaModuleDirective> ds = cs.get(0).getModuleDirectives();
        assertEquals(4, ds.size());

        assertEquals(JavaModuleDirective.Kind.EXPORTS, ds.get(0).getKind());
        assertEquals("a.b.api", ds.get(0).getName());
        assertTrue(ds.get(0).getTargets().isEmpty());

        assertEquals(JavaModuleDirective.Kind.EXPORTS, ds.get(1).getKind());
        assertEquals("a.b.internal", ds.get(1).getName());
        assertEquals(2, ds.get(1).getTargets().size());
        assertEquals("c.d", ds.get(1).getTargets().get(0));
        assertEquals("e.f", ds.get(1).getTargets().get(1));

        assertEquals(JavaModuleDirective.Kind.OPENS, ds.get(2).getKind());
        assertEquals("a.b.impl", ds.get(2).getName());

        assertEquals(JavaModuleDirective.Kind.OPENS, ds.get(3).getKind());
        assertEquals(1, ds.get(3).getTargets().size());
        assertEquals("c.d", ds.get(3).getTargets().get(0));
    }

    @Test
    public void testModuleUsesAndProvides() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "module x {"
                        + "  uses a.b.spi.Provider;"
                        + "  provides a.b.spi.Provider with a.b.impl.One, a.b.impl.Two;"
                        + "}");
        List<JavaModuleDirective> ds = cs.get(0).getModuleDirectives();
        assertEquals(2, ds.size());

        assertEquals(JavaModuleDirective.Kind.USES, ds.get(0).getKind());
        assertEquals("a.b.spi.Provider", ds.get(0).getName());

        assertEquals(JavaModuleDirective.Kind.PROVIDES, ds.get(1).getKind());
        assertEquals("a.b.spi.Provider", ds.get(1).getName());
        assertEquals(2, ds.get(1).getTargets().size());
        assertEquals("a.b.impl.One", ds.get(1).getTargets().get(0));
        assertEquals("a.b.impl.Two", ds.get(1).getTargets().get(1));
    }

    @Test
    public void testModuleAnnotation() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "@Deprecated module a { requires java.base; }");
        JavaClassInfo m = cs.get(0);
        assertEquals(JavaClassInfo.Kind.MODULE, m.getKind());
        assertTrue(m.getAnnotations().contains("Deprecated"));
        assertEquals(1, m.getModuleDirectives().size());
    }

    // -------- switch 式 / yield (Java 14+) --------

    @Test
    public void testYieldStatementInSwitchArmBlock() {
        // switch 式のアーム本体内の yield を Yield 文として認識する
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {"
                        + "  int m(int x) {"
                        + "    return switch (x) {"
                        + "      case 1 -> { yield 100; }"
                        + "      case 2 -> 200;"
                        + "      default -> 0;"
                        + "    };"
                        + "  }"
                        + "}");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        boolean foundYield = containsYield(m.getStatements(), "100");
        assertTrue("Yield(100) should be captured", foundYield);
    }

    @Test
    public void testYieldOnlyInsideSwitch() {
        // switch の外では `yield` は識別子扱いで Yield 化されない
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { int yield = 10; int m() { return yield; } }");
        assertEquals(1, cs.size());
        // フィールド `yield` がパースできていること
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals("yield", cs.get(0).getFields().get(0).getName());
        // メソッド本体に Yield が誤生成されていないこと
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        for (JavaMethodInfo.Statement s : m.getStatements()) {
            assertFalse("Yield should not be emitted outside switch",
                    s instanceof JavaMethodInfo.Yield);
        }
    }

    @Test
    public void testSwitchExpressionAsRhs() {
        // switch 式が代入 RHS でも Block.SWITCH として構造を保持する
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m(int x) {"
                        + "  int r = switch (x) {"
                        + "    case 1 -> 100;"
                        + "    case 2 -> 200;"
                        + "    default -> 0;"
                        + "  };"
                        + "} }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Block sw = findSwitchBlock(m.getStatements());
        assertNotNull("Switch expression as RHS should be a Block.SWITCH", sw);
        // head + 3 アーム = 4 ブランチ
        assertEquals(4, sw.getBranches().size());
    }

    @Test
    public void testSwitchExpressionInReturn() {
        // return switch(...) {...}; も構造化される
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { int m(int x) {"
                        + "  return switch (x) {"
                        + "    case 1 -> 100;"
                        + "    default -> 0;"
                        + "  };"
                        + "} }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Block sw = findSwitchBlock(m.getStatements());
        assertNotNull("Switch in return should be a Block.SWITCH", sw);
    }

    @Test
    public void testCasePatternWithWhenGuard() {
        // Java 21+ パターン case + when ガード
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { String m(Object o) {"
                        + "  int r = switch (o) {"
                        + "    case Integer i when i > 0 -> 1;"
                        + "    case Integer i -> -1;"
                        + "    case null -> 0;"
                        + "    default -> 2;"
                        + "  };"
                        + "  return null;"
                        + "} }");
        assertEquals(1, cs.size());
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        JavaMethodInfo.Block sw = findSwitchBlock(m.getStatements());
        assertNotNull("Switch block should be captured", sw);
        boolean foundGuard = false;
        for (JavaMethodInfo.Branch b : sw.getBranches()) {
            if (b.getLabel().contains("when")) {
                foundGuard = true;
                break;
            }
        }
        assertTrue("when-guard should be preserved in case label", foundGuard);
    }

    @Test
    public void testSealedPermittedTypesPreservedInHeadersOnly() {
        // sealed の permits は Stage A (headers-only) でも失われない
        List<JavaClassInfo> headers = JavaStructureExtractor.extractHeadersOnly(
                "public sealed class Shape permits Circle, Square {}", null);
        JavaClassInfo shape = headers.get(0);
        assertTrue(shape.getPermittedTypes().contains("Circle"));
        assertTrue(shape.getPermittedTypes().contains("Square"));
    }

    @Test
    public void testNoModuleDirectiveInExtractHeadersOnly() {
        // header-only 抽出でも module directives は保持される (モジュール解析用途)
        List<JavaClassInfo> headers = JavaStructureExtractor.extractHeadersOnly(
                "module a { requires java.base; exports a.b; }", null);
        assertEquals(1, headers.size());
        JavaClassInfo m = headers.get(0);
        assertEquals(JavaClassInfo.Kind.MODULE, m.getKind());
        assertEquals(2, m.getModuleDirectives().size());
    }

    // -------- record components (JLS §8.10) --------

    @Test
    public void testRecordComponentsExtractedAsFields() {
        // record のヘッダコンポーネントが暗黙の private final フィールドとして抽出される
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "package x; public record Point(int x, java.util.List<String> tags) {}");
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        assertEquals(JavaClassInfo.Kind.RECORD, c.getKind());
        assertEquals(2, c.getFields().size());
        JavaFieldInfo x = c.getFields().get(0);
        assertEquals("x", x.getName());
        assertEquals("int", x.getType());
        assertTrue("record component is implicitly final", x.isFinal());
        assertEquals(Visibility.PRIVATE, x.getVisibility());
        JavaFieldInfo tags = c.getFields().get(1);
        assertEquals("tags", tags.getName());
        assertTrue(tags.getType().contains("List<String>"));
    }

    @Test
    public void testRecordWithExplicitStaticFieldAndComponents() {
        // record の明示 static フィールドとコンポーネントが両方取り込まれる
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "public record R(int a) { static final int MAX = 10; }");
        JavaClassInfo c = cs.get(0);
        // MAX (FieldDeclaration) + a (component)
        assertEquals(2, c.getFields().size());
        assertTrue(c.getFields().stream().anyMatch(f -> f.getName().equals("MAX")));
        assertTrue(c.getFields().stream().anyMatch(f -> f.getName().equals("a")));
    }

    // -------- static / instance initializer blocks --------

    @Test
    public void testStaticInitializerCapturedAsClinit() {
        // static { ... } が <clinit> 擬似メソッドとして取り込まれ、内部呼び出しが見える
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Dao { static { connect(); } static void connect() {} }");
        JavaClassInfo c = cs.get(0);
        JavaMethodInfo clinit = c.getMethods().stream()
                .filter(m -> "<clinit>".equals(m.getName())).findFirst().orElse(null);
        assertNotNull("static initializer should be captured as <clinit>", clinit);
        assertTrue(clinit.isStatic());
        assertTrue(clinit.getCalls().stream()
                .anyMatch(call -> "connect".equals(call.getMethodName())));
    }

    @Test
    public void testInstanceInitializerCapturedAsInit() {
        // インスタンスイニシャライザ { ... } は <init> 擬似メソッドになる
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { { setup(); } void setup() {} }");
        JavaClassInfo c = cs.get(0);
        JavaMethodInfo init = c.getMethods().stream()
                .filter(m -> "<init>".equals(m.getName())).findFirst().orElse(null);
        assertNotNull("instance initializer should be captured as <init>", init);
        assertFalse(init.isStatic());
        assertTrue(init.getCalls().stream()
                .anyMatch(call -> "setup".equals(call.getMethodName())));
    }

    private static boolean containsYield(List<JavaMethodInfo.Statement> ss,
                                          String exprFragment) {
        for (JavaMethodInfo.Statement s : ss) {
            if (s instanceof JavaMethodInfo.Yield
                    && ((JavaMethodInfo.Yield) s).getExpression()
                            .contains(exprFragment)) {
                return true;
            }
            if (s instanceof JavaMethodInfo.Block) {
                for (JavaMethodInfo.Branch b : ((JavaMethodInfo.Block) s).getBranches()) {
                    if (containsYield(b.getBody(), exprFragment)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static JavaMethodInfo.Block findSwitchBlock(
            List<JavaMethodInfo.Statement> ss) {
        for (JavaMethodInfo.Statement s : ss) {
            if (s instanceof JavaMethodInfo.Block) {
                JavaMethodInfo.Block b = (JavaMethodInfo.Block) s;
                if (b.getKind() == JavaMethodInfo.Block.Kind.SWITCH) {
                    return b;
                }
                for (JavaMethodInfo.Branch br : b.getBranches()) {
                    JavaMethodInfo.Block found = findSwitchBlock(br.getBody());
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }
}
