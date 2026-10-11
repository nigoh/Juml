// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt R4 で発見: 単一ファイル入力の拡張子振り分けに 2 つの穴があった。
 *
 * <ul>
 *   <li>{@code .hal} (HIDL) が {@link HidlParser} へ渡らず Java 抽出器に落ちていた</li>
 *   <li>{@code toLowerCase()} がロケール依存で、トルコ語ロケールでは {@code .AIDL} が
 *       Java として解析されていた (I → ı)</li>
 * </ul>
 */
public class UmlGeneratorExtensionRoutingTest {

    private static final String HIDL =
            "package android.hardware.foo@1.0;\ninterface IFoo {\n  doThing(int32_t x);\n};\n";
    private static final String AIDL =
            "package com.example;\ninterface IBar {\n  void ping();\n}\n";

    private final Locale original = Locale.getDefault();

    @After
    public void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    public void halFilesAreParsedAsHidl() {
        List<JavaClassInfo> infos = UmlGenerator.extractFromSource(HIDL, "IFoo.hal");
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals("IFoo", c.getSimpleName());
        assertEquals("android.hardware.foo@1.0", c.getPackageName());
        assertEquals(JavaClassInfo.Kind.AIDL_INTERFACE, c.getKind());
    }

    @Test
    public void aidlRoutingDoesNotDependOnTheDefaultLocale() {
        Locale.setDefault(new Locale("tr", "TR"));
        List<JavaClassInfo> infos = UmlGenerator.extractFromSource(AIDL, "IBar.AIDL");
        assertEquals(1, infos.size());
        assertEquals(JavaClassInfo.Kind.AIDL_INTERFACE, infos.get(0).getKind());
        assertTrue("AIDL のメソッドが読めていること",
                infos.get(0).getMethods().stream().anyMatch(m -> "ping".equals(m.getName())));
    }
}
