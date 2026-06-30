// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SmaliParser} のユニットテスト。
 */
public class SmaliParserTest {

    private static final String SAMPLE =
            ".class public final Lcom/example/MainActivity;\n"
            + ".super Landroidx/appcompat/app/AppCompatActivity;\n"
            + ".implements Landroid/view/View$OnClickListener;\n"
            + ".source \"MainActivity.java\"\n"
            + "\n"
            + "# instance fields\n"
            + ".field private static final TAG:Ljava/lang/String; = \"Main\"\n"
            + ".field private count:I\n"
            + "\n"
            + "# direct methods\n"
            + ".method public constructor <init>()V\n"
            + "    .registers 1\n"
            + "    return-void\n"
            + ".end method\n"
            + "\n"
            + ".method public onClick(Landroid/view/View;)V\n"
            + "    .registers 2\n"
            + "    return-void\n"
            + ".end method\n"
            + "\n"
            + ".method public getCount()I\n"
            + "    .registers 2\n"
            + "    return v0\n"
            + ".end method\n";

    @Test(expected = IllegalArgumentException.class)
    public void testNullThrows() {
        SmaliParser.parse(null, "x");
    }

    @Test
    public void testNoClassReturnsNull() {
        assertNull(SmaliParser.parse("# just a comment\n.registers 1\n", "x"));
    }

    @Test
    public void testClassHeader() {
        SmaliClassInfo c = SmaliParser.parse(SAMPLE, "MainActivity.smali");
        assertEquals("com.example.MainActivity", c.getClassName());
        assertEquals("MainActivity", c.getSimpleName());
        assertEquals("com.example", c.getPackageName());
        assertEquals("androidx.appcompat.app.AppCompatActivity", c.getSuperClass());
        assertEquals(1, c.getInterfaces().size());
        assertEquals("android.view.View$OnClickListener", c.getInterfaces().get(0));
        assertEquals("MainActivity.java", c.getSourceFile());
        assertTrue(c.isPublic());
        assertFalse(c.isInterface());
    }

    @Test
    public void testFields() {
        SmaliClassInfo c = SmaliParser.parse(SAMPLE, "x");
        assertEquals(2, c.getFields().size());
        SmaliFieldInfo tag = c.getFields().get(0);
        assertEquals("TAG", tag.getName());
        assertEquals("java.lang.String", tag.getType());
        assertTrue(tag.isStatic());
        assertTrue(tag.isFinal());
        assertTrue(tag.isPrivate());
        assertEquals("\"Main\"", tag.getConstantValue());
        assertEquals('-', tag.visibilitySymbol());

        SmaliFieldInfo count = c.getFields().get(1);
        assertEquals("count", count.getName());
        assertEquals("int", count.getType());
        assertNull(count.getConstantValue());
    }

    @Test
    public void testMethods() {
        SmaliClassInfo c = SmaliParser.parse(SAMPLE, "x");
        assertEquals(3, c.getMethods().size());
        SmaliMethodInfo init = c.getMethods().get(0);
        assertEquals("<init>", init.getName());
        assertTrue(init.isConstructor());

        SmaliMethodInfo onClick = c.getMethods().get(1);
        assertEquals("onClick", onClick.getName());
        assertEquals(1, onClick.getParameterTypes().size());
        assertEquals("android.view.View", onClick.getParameterTypes().get(0));
        assertEquals("void", onClick.getReturnType());
        assertEquals("onClick(View)", onClick.displaySignature());

        SmaliMethodInfo getCount = c.getMethods().get(2);
        assertEquals("getCount(): int", getCount.displaySignature());
    }

    @Test
    public void testInterfaceModifiers() {
        String smali =
                ".class public interface abstract Lcom/example/Listener;\n"
                + ".super Ljava/lang/Object;\n"
                + ".method public abstract handle()V\n"
                + ".end method\n";
        SmaliClassInfo c = SmaliParser.parse(smali, "x");
        assertTrue(c.isInterface());
        assertTrue(c.isAbstract());
        assertEquals("interface", c.umlKind());
    }

    @Test
    public void testAnnotationBlockSkipped() {
        String smali =
                ".class public Lcom/example/Foo;\n"
                + ".super Ljava/lang/Object;\n"
                + ".annotation system Ldalvik/annotation/Signature;\n"
                + "    value = { \"a\", \"b\" }\n"
                + ".end annotation\n"
                + ".field public x:I\n"
                + ".method public foo()V\n"
                + "    .annotation runtime Lcom/example/Anno;\n"
                + "    .end annotation\n"
                + ".end method\n";
        SmaliClassInfo c = SmaliParser.parse(smali, "x");
        assertEquals(1, c.getFields().size());
        assertEquals("x", c.getFields().get(0).getName());
        assertEquals(1, c.getMethods().size());
        assertEquals("foo", c.getMethods().get(0).getName());
    }
}
