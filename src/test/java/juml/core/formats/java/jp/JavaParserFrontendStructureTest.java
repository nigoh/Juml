// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * JavaParser フロントエンドの構造抽出（P1）が既存モデル契約と一致することを検証する。
 */
public class JavaParserFrontendStructureTest {

    private JavaClassInfo only(String src) {
        List<JavaClassInfo> cs = JavaParserFrontend.parse(src, null);
        assertEquals(1, cs.size());
        return cs.get(0);
    }

    @Test
    public void simpleClassFieldMethod() {
        JavaClassInfo c = only("package com.x; class Foo { int a; void m() {} }");
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
    public void interfaceMethodsAreAbstract() {
        JavaClassInfo c = only("interface I { void a(); int b(); }");
        assertEquals(JavaClassInfo.Kind.INTERFACE, c.getKind());
        assertEquals(2, c.getMethods().size());
        assertTrue(c.getMethods().get(0).isAbstract());
    }

    @Test
    public void enumKindAndConstants() {
        JavaClassInfo c = only("enum E { RED, GREEN; void x() {} }");
        assertEquals(JavaClassInfo.Kind.ENUM, c.getKind());
        assertTrue(c.getEnumConstants().contains("RED"));
        assertTrue(c.getEnumConstants().contains("GREEN"));
        assertEquals(1, c.getMethods().size());
        assertEquals("x", c.getMethods().get(0).getName());
    }

    @Test
    public void enumConstantArguments() {
        JavaClassInfo c = only("enum Color { RED(255, 0, 0), GREEN(0, 255, 0), BLACK; "
                + "Color(int... v) {} Color() {} }");
        assertEquals(3, c.getEnumConstantArgs().size());
        // 定数名と引数文字列が添字対応し、引数なしは空文字
        assertEquals("(255, 0, 0)", c.getEnumConstantArgs().get(0));
        assertEquals("(0, 255, 0)", c.getEnumConstantArgs().get(1));
        assertEquals("", c.getEnumConstantArgs().get(2));
    }

    @Test
    public void annotationDecl() {
        JavaClassInfo c = only("@interface MyAnno { String value(); }");
        assertEquals(JavaClassInfo.Kind.ANNOTATION, c.getKind());
        assertEquals("MyAnno", c.getSimpleName());
    }

    @Test
    public void annotationMemberDefaultValue() {
        JavaClassInfo c = only("@interface Cfg { int timeout() default 30; String name(); }");
        JavaMethodInfo timeout = c.getMethods().stream()
                .filter(m -> "timeout".equals(m.getName())).findFirst().orElseThrow();
        assertEquals("30", timeout.getDefaultValue());
        // default のない属性は null
        JavaMethodInfo name = c.getMethods().stream()
                .filter(m -> "name".equals(m.getName())).findFirst().orElseThrow();
        assertEquals(null, name.getDefaultValue());
    }

    @Test
    public void javadocInlineTagsUnwrapped() {
        JavaClassInfo c = only("/** Uses {@link Foo#bar} via {@code cfg}; see {@link #self label}. */\n"
                + "class A {}");
        String doc = c.getComment();
        // インラインタグが波括弧なしの可読テキストへ展開される
        assertTrue(doc, doc.contains("Foo#bar"));
        assertTrue(doc, doc.contains("cfg"));
        assertTrue(doc, doc.contains("label"));
        assertFalse(doc, doc.contains("{@"));
    }

    @Test
    public void methodTypeParametersCaptured() {
        JavaClassInfo c = only("class U { <T> T id(T x) { return x; } void plain() {} }");
        JavaMethodInfo id = c.getMethods().stream()
                .filter(m -> "id".equals(m.getName())).findFirst().orElseThrow();
        assertEquals("<T>", id.getTypeParameters());
        // 型パラメータのないメソッドは null
        JavaMethodInfo plain = c.getMethods().stream()
                .filter(m -> "plain".equals(m.getName())).findFirst().orElseThrow();
        assertEquals(null, plain.getTypeParameters());
    }

    @Test
    public void typeParametersCaptured() {
        assertEquals("<T>", only("class Box<T> {}").getTypeParameters());
        assertEquals("<K, V extends Number>",
                only("interface Map<K, V extends Number> {}").getTypeParameters());
        // 型パラメータのない型は null
        assertEquals(null, only("class Plain {}").getTypeParameters());
    }

    @Test
    public void extendsAndImplements() {
        JavaClassInfo c = only("class Foo extends Bar implements I1, I2 {}");
        assertEquals("Bar", c.getSuperClass());
        assertEquals(2, c.getInterfaces().size());
        assertTrue(c.getInterfaces().contains("I1"));
        assertTrue(c.getInterfaces().contains("I2"));
    }

    @Test
    public void genericsPreservedOnSuperAndParams() {
        JavaClassInfo c = only(
                "class Foo extends Bar<T> { T t; void m(java.util.Map<String, java.util.List<Integer>> x) {} }");
        assertEquals("Bar<T>", c.getSuperClass());
        assertEquals("T", c.getFields().get(0).getType());
        JavaMethodInfo m = c.getMethods().get(0);
        assertEquals("Map<String, List<Integer>>",
                normalizeFqn(m.getParameterTypes().get(0)));
    }

    @Test
    public void nestedTypeBecomesSeparateEntry() {
        List<JavaClassInfo> cs = JavaParserFrontend.parse(
                "package p; class Outer { class Inner {} }", null);
        boolean outer = false;
        boolean inner = false;
        for (JavaClassInfo c : cs) {
            if ("Outer".equals(c.getSimpleName()) && c.getEnclosingClass() == null) {
                outer = true;
            }
            if ("Inner".equals(c.getSimpleName())) {
                assertEquals("Outer", c.getEnclosingClass());
                assertEquals("p.Outer.Inner", c.getQualifiedName());
                inner = true;
            }
        }
        assertTrue(outer);
        assertTrue(inner);
    }

    @Test
    public void fieldVisibilityAndModifiers() {
        JavaClassInfo c = only("class C { public static final int A = 1; private int b; }");
        List<JavaFieldInfo> fs = c.getFields();
        assertEquals(Visibility.PUBLIC, fs.get(0).getVisibility());
        assertTrue(fs.get(0).isStatic());
        assertTrue(fs.get(0).isFinal());
        assertEquals(Visibility.PRIVATE, fs.get(1).getVisibility());
    }

    @Test
    public void constructorAndThrows() {
        JavaClassInfo c = only(
                "class C { C(int a) throws java.io.IOException {} }");
        JavaMethodInfo ctor = c.getMethods().get(0);
        assertTrue(ctor.isConstructor());
        assertEquals("C", ctor.getName());
        assertEquals(1, ctor.getParameterTypes().size());
        assertTrue(ctor.getThrowsTypes().get(0).endsWith("IOException"));
    }

    @Test
    public void varargsParameterMarked() {
        JavaClassInfo c = only("class C { void m(int... xs) {} }");
        assertEquals("int...", c.getMethods().get(0).getParameterTypes().get(0));
    }

    @Test
    public void recordKindAndImplements() {
        JavaClassInfo c = only("record Point(int x, int y) implements java.io.Serializable {}");
        assertEquals(JavaClassInfo.Kind.RECORD, c.getKind());
        assertTrue(c.getInterfaces().get(0).endsWith("Serializable"));
    }

    @Test
    public void sealedPermitsGoToPermittedTypes() {
        // permits は「継承を許可された子型」なので interfaces ではなく permittedTypes に入る
        // (クラス図で逆向きの実装矢印を出さないため)。
        JavaClassInfo c = only("sealed class Shape permits Circle, Square {}");
        assertTrue(c.getModifiers().contains("sealed"));
        assertTrue(c.getPermittedTypes().contains("Circle"));
        assertTrue(c.getPermittedTypes().contains("Square"));
        assertFalse(c.getInterfaces().contains("Circle"));
        assertFalse(c.getInterfaces().contains("Square"));
    }

    @Test
    public void importsKeepRawForm() {
        JavaClassInfo c = only(
                "import java.util.*; import static java.lang.Math.PI; class C {}");
        assertTrue(c.getImports().contains("java.util.*"));
        assertTrue(c.getImports().contains("static java.lang.Math.PI"));
    }

    @Test
    public void moduleDirectivesParsed() {
        List<JavaClassInfo> cs = JavaParserFrontend.parse(
                "module com.foo { requires java.sql; exports com.foo.api; }", null);
        JavaClassInfo mod = null;
        for (JavaClassInfo c : cs) {
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                mod = c;
            }
        }
        assertNotNull(mod);
        assertEquals("com.foo", mod.getSimpleName());
        assertEquals(2, mod.getModuleDirectives().size());
    }

    /** JavaParser はジェネリクス内の型を FQN のまま出すことがあるため末尾簡易名で比較する。 */
    private static String normalizeFqn(String s) {
        return s.replace("java.util.", "").replace("java.lang.", "");
    }
}
