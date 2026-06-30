// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * {@link HidlParser} のユニットテスト。
 */
public class HidlParserTest {

    @Test
    public void testEmptyInputReturnsEmpty() {
        assertTrue(HidlParser.parse("").isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        HidlParser.parse(null);
    }

    @Test
    public void testPackageWithVersion() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo { };\n");
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        assertEquals("android.hardware.foo@1.0", c.getPackageName());
        assertEquals("IFoo", c.getSimpleName());
        assertEquals(JavaClassInfo.Kind.AIDL_INTERFACE, c.getKind());
    }

    @Test
    public void testImportSkippedSafely() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "import android.hardware.bar@1.0;\n"
                        + "import android.hardware.foo@1.0::types;\n"
                        + "interface IFoo { };\n");
        assertEquals(1, cs.size());
        assertEquals("IFoo", cs.get(0).getSimpleName());
    }

    @Test
    public void testInterfaceWithMethod() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  getValue(int32_t key) generates (int32_t value);\n"
                        + "};\n");
        JavaClassInfo c = cs.get(0);
        assertEquals(1, c.getMethods().size());
        JavaMethodInfo m = c.getMethods().get(0);
        assertEquals("getValue", m.getName());
        assertEquals("int32_t", m.getReturnType());
        assertEquals(1, m.getParameterTypes().size());
        assertEquals("int32_t", m.getParameterTypes().get(0));
        assertEquals("key", m.getParameterNames().get(0));
        assertTrue(m.isAbstract());
        assertEquals(Visibility.PUBLIC, m.getVisibility());
    }

    @Test
    public void testOnewayMethodHasNoGenerates() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  oneway notify(string msg);\n"
                        + "};\n");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals("notify", m.getName());
        assertTrue(m.getAnnotations().contains("oneway"));
        assertEquals(1, m.getParameterTypes().size());
        assertEquals("string", m.getParameterTypes().get(0));
        assertEquals("msg", m.getParameterNames().get(0));
        // oneway は generates 句を持たない (戻り値型は空)
        assertTrue(m.getReturnType() == null || m.getReturnType().isEmpty());
    }

    @Test
    public void testInterfaceExtends() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFooExtra extends IFoo { };\n");
        JavaClassInfo c = cs.get(0);
        assertEquals("IFooExtra", c.getSimpleName());
        assertEquals("IFoo", c.getSuperClass());
    }

    @Test
    public void testInterfaceExtendsVersionedParent() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.1;\n"
                        + "interface IFoo extends android.hardware.foo@1.0::IFoo { };\n");
        JavaClassInfo c = cs.get(0);
        assertEquals("android.hardware.foo@1.0::IFoo", c.getSuperClass());
    }

    @Test
    public void testNestedStructIgnored() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  struct Config {\n"
                        + "    int32_t id;\n"
                        + "    string name;\n"
                        + "  };\n"
                        + "  getId() generates (int32_t id);\n"
                        + "};\n");
        JavaClassInfo c = cs.get(0);
        // struct は skip され、メソッドだけ取り込まれる
        assertEquals(1, c.getMethods().size());
        assertEquals("getId", c.getMethods().get(0).getName());
    }

    @Test
    public void testNestedEnumAndUnionIgnored() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  enum Status : int32_t { OK, ERROR };\n"
                        + "  union Payload { int32_t i; string s; };\n"
                        + "  typedef int32_t MyInt;\n"
                        + "  ping() generates ();\n"
                        + "};\n");
        JavaClassInfo c = cs.get(0);
        assertEquals(1, c.getMethods().size());
        assertEquals("ping", c.getMethods().get(0).getName());
    }

    @Test
    public void testMultipleReturnValues() {
        // HIDL は generates 句で複数戻り値を許す。簡略化して最初の 1 つを returnType に。
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  getPair() generates (int32_t a, int32_t b);\n"
                        + "};\n");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals("getPair", m.getName());
        assertEquals("int32_t", m.getReturnType());
    }

    @Test
    public void testVecTypeParam() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  setIds(vec<int32_t> ids);\n"
                        + "};\n");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(1, m.getParameterTypes().size());
        assertEquals("vec<int32_t>", m.getParameterTypes().get(0));
        assertEquals("ids", m.getParameterNames().get(0));
    }

    @Test
    public void testMultipleInterfacesInOneFile() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo { };\n"
                        + "interface IBar extends IFoo { };\n");
        assertEquals(2, cs.size());
        assertEquals("IFoo", cs.get(0).getSimpleName());
        assertEquals("IBar", cs.get(1).getSimpleName());
        assertEquals("IFoo", cs.get(1).getSuperClass());
    }

    @Test
    public void testFileLevelTypedefSkipped() {
        // ファイル先頭の typedef/struct は interface ではないので空リスト
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "typedef int32_t Handle;\n"
                        + "struct OuterStruct { int32_t x; };\n"
                        + "interface IFoo { };\n");
        assertEquals(1, cs.size());
        assertEquals("IFoo", cs.get(0).getSimpleName());
    }

    @Test
    public void testMultipleParameters() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  setBoth(int32_t a, string b) generates (bool ok);\n"
                        + "};\n");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(2, m.getParameterTypes().size());
        assertEquals("int32_t", m.getParameterTypes().get(0));
        assertEquals("a", m.getParameterNames().get(0));
        assertEquals("string", m.getParameterTypes().get(1));
        assertEquals("b", m.getParameterNames().get(1));
        assertEquals("bool", m.getReturnType());
    }

    @Test
    public void testEmptyParametersAndGenerates() {
        List<JavaClassInfo> cs = HidlParser.parse(
                "package android.hardware.foo@1.0;\n"
                        + "interface IFoo {\n"
                        + "  ping() generates ();\n"
                        + "};\n");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals("ping", m.getName());
        assertTrue(m.getParameterTypes().isEmpty());
        assertTrue(m.getReturnType() == null || m.getReturnType().isEmpty());
        assertFalse(m.getAnnotations().contains("oneway"));
    }
}
