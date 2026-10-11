// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * AidlParser のユニットテスト。
 */
public class AidlParserTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        AidlParser.parse(null);
    }

    @Test
    public void testEmpty() {
        assertTrue(AidlParser.parse("").isEmpty());
    }

    @Test
    public void testBasicInterface() {
        String aidl =
                "package com.android.car;\n"
                        + "interface ICarService {\n"
                        + "    int getVersion();\n"
                        + "    void registerListener(in IListener listener);\n"
                        + "}\n";
        List<JavaClassInfo> result = AidlParser.parse(aidl);
        assertEquals(1, result.size());
        JavaClassInfo c = result.get(0);
        assertEquals("com.android.car", c.getPackageName());
        assertEquals("ICarService", c.getSimpleName());
        assertEquals(JavaClassInfo.Kind.AIDL_INTERFACE, c.getKind());
        assertEquals(2, c.getMethods().size());
        assertEquals("getVersion", c.getMethods().get(0).getName());
        assertEquals("int", c.getMethods().get(0).getReturnType());
        assertEquals("registerListener", c.getMethods().get(1).getName());
        assertEquals(1, c.getMethods().get(1).getParameterTypes().size());
        assertEquals("IListener", c.getMethods().get(1).getParameterTypes().get(0));
    }

    @Test
    public void testOnewayMethod() {
        String aidl =
                "interface I { oneway void notifyEvent(in Event e); }";
        JavaMethodInfo m = AidlParser.parse(aidl).get(0).getMethods().get(0);
        assertTrue("expected oneway annotation",
                m.getAnnotations().stream().anyMatch("oneway"::equals));
        assertEquals("notifyEvent", m.getName());
    }

    @Test
    public void testInOutInoutDirections() {
        String aidl =
                "interface I {\n"
                        + "  void a(in int x);\n"
                        + "  void b(out int x);\n"
                        + "  void c(inout int x);\n"
                        + "}";
        List<JavaMethodInfo> ms = AidlParser.parse(aidl).get(0).getMethods();
        for (JavaMethodInfo m : ms) {
            assertEquals("int", m.getParameterTypes().get(0));
        }
    }

    @Test
    public void testImports() {
        String aidl =
                "package com.x;\n"
                        + "import com.x.IListener;\n"
                        + "interface I { void register(in IListener l); }";
        List<JavaClassInfo> r = AidlParser.parse(aidl);
        assertEquals(1, r.size());
        assertEquals("I", r.get(0).getSimpleName());
    }

    @Test
    public void testParcelableForwardDecl() {
        String aidl =
                "parcelable Event;\n"
                        + "interface I { void notify(in Event e); }";
        List<JavaClassInfo> r = AidlParser.parse(aidl);
        assertEquals(1, r.size());
        assertEquals("I", r.get(0).getSimpleName());
    }

    @Test
    public void testAnnotationsOnInterface() {
        String aidl =
                "@SystemApi @JavaDerive(toString=true) interface ICar {\n"
                        + "  int getVersion();\n"
                        + "}";
        JavaClassInfo c = AidlParser.parse(aidl).get(0);
        assertEquals(2, c.getAnnotations().size());
    }

    @Test
    public void testGenericReturnType() {
        String aidl =
                "interface I {\n"
                        + "  List<String> getNames();\n"
                        + "  Map<String, Integer> getCounts();\n"
                        + "}";
        List<JavaMethodInfo> ms = AidlParser.parse(aidl).get(0).getMethods();
        assertEquals(2, ms.size());
        assertTrue(ms.get(0).getReturnType().contains("List"));
        assertTrue(ms.get(1).getReturnType().contains("Map"));
    }

    @Test
    public void testAllMethodsPublic() {
        String aidl = "interface I { void a(); int b(); }";
        for (JavaMethodInfo m : AidlParser.parse(aidl).get(0).getMethods()) {
            assertEquals(Visibility.PUBLIC, m.getVisibility());
            assertTrue(m.isAbstract());
        }
    }


    /**
     * bug-hunt R4 で発見: interface の中にネストした型宣言 (parcelable/enum/union) があると、
     * その閉じ波括弧を interface 本体の終わりと誤認し、以降のメソッドが 1 つも読めなくなっていた。
     */
    @Test
    public void testNestedDeclarationKeepsFollowingMethods() {
        List<JavaClassInfo> cs = AidlParser.parse(
                "package com.x;\ninterface IFoo {\n"
                        + "  void first();\n"
                        + "  parcelable Inner { int a; }\n"
                        + "  enum Mode { A, B }\n"
                        + "  void second();\n}\n");
        JavaClassInfo foo = cs.stream()
                .filter(c -> "IFoo".equals(c.getSimpleName())).findFirst().orElse(null);
        assertNotNull(String.valueOf(cs), foo);
        List<String> names = new java.util.ArrayList<>();
        for (JavaMethodInfo m : foo.getMethods()) {
            names.add(m.getName());
        }
        assertTrue("ネスト宣言の前後どちらのメソッドも残ること: " + names,
                names.contains("first") && names.contains("second"));
    }

    /**
     * bug-hunt R4 で発見: {@code const int M = (1 << 3);} の括弧を引数リストと誤認し、
     * 定数が偽のメソッドとして図やレポートに並んでいた。
     */
    @Test
    public void testConstWithParenthesisedInitialiserIsAField() {
        List<JavaClassInfo> cs = AidlParser.parse(
                "package com.x;\ninterface IFoo {\n"
                        + "  const int MASK = (1 << 3);\n  void ping();\n}\n");
        JavaClassInfo foo = cs.get(0);
        assertEquals("メソッドは ping() だけ", 1, foo.getMethods().size());
        assertEquals("ping", foo.getMethods().get(0).getName());
        assertEquals("const はフィールドとして取り込む", 1, foo.getFields().size());
        assertEquals("MASK", foo.getFields().get(0).getName());
    }
}
