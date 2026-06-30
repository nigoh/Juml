// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * {@link SmaliTypeDescriptor} の型復号ユニットテスト。
 */
public class SmaliTypeDescriptorTest {

    @Test
    public void testPrimitives() {
        assertEquals("void", SmaliTypeDescriptor.decode("V"));
        assertEquals("boolean", SmaliTypeDescriptor.decode("Z"));
        assertEquals("int", SmaliTypeDescriptor.decode("I"));
        assertEquals("long", SmaliTypeDescriptor.decode("J"));
        assertEquals("double", SmaliTypeDescriptor.decode("D"));
    }

    @Test
    public void testReferenceType() {
        assertEquals("com.example.Foo", SmaliTypeDescriptor.decode("Lcom/example/Foo;"));
        assertEquals("java.lang.String",
                SmaliTypeDescriptor.decode("Ljava/lang/String;"));
    }

    @Test
    public void testInnerClassKeepsDollar() {
        assertEquals("com.example.Foo$Bar",
                SmaliTypeDescriptor.decodeClassName("Lcom/example/Foo$Bar;"));
    }

    @Test
    public void testArrays() {
        assertEquals("int[]", SmaliTypeDescriptor.decode("[I"));
        assertEquals("byte[][]", SmaliTypeDescriptor.decode("[[B"));
        assertEquals("java.lang.String[]",
                SmaliTypeDescriptor.decode("[Ljava/lang/String;"));
    }

    @Test
    public void testSimpleNameAndPackage() {
        assertEquals("Foo$Bar", SmaliTypeDescriptor.simpleName("com.example.Foo$Bar"));
        assertEquals("com.example", SmaliTypeDescriptor.packageOf("com.example.Foo"));
        assertEquals("", SmaliTypeDescriptor.packageOf("Foo"));
    }

    @Test
    public void testMethodDescriptor() {
        SmaliTypeDescriptor.Method m =
                SmaliTypeDescriptor.parseMethodDescriptor("(ILjava/lang/String;[B)Z");
        assertEquals(List.of("int", "java.lang.String", "byte[]"), m.getParameterTypes());
        assertEquals("boolean", m.getReturnType());
    }

    @Test
    public void testMethodDescriptorNoParams() {
        SmaliTypeDescriptor.Method m = SmaliTypeDescriptor.parseMethodDescriptor("()V");
        assertEquals(List.of(), m.getParameterTypes());
        assertEquals("void", m.getReturnType());
    }

    @Test
    public void testMethodDescriptorObjectReturn() {
        SmaliTypeDescriptor.Method m =
                SmaliTypeDescriptor.parseMethodDescriptor("(II)Landroid/view/View;");
        assertEquals(List.of("int", "int"), m.getParameterTypes());
        assertEquals("android.view.View", m.getReturnType());
    }
}
