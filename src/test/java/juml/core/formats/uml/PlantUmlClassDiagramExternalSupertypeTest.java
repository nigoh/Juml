// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlClassDiagram} の {@code markExternalSupertypes} オプションのテスト。
 */
public class PlantUmlClassDiagramExternalSupertypeTest {

    private static String generate(String source, boolean mark, boolean distinguish) {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(source);
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.markExternalSupertypes = mark;
        o.distinguishStandardLibrary = distinguish;
        return PlantUmlClassDiagram.generate(cs, o);
    }

    @Test
    public void standardSuperclassGetsStandardStereotype() {
        String puml = generate(
                "package com.app; abstract class Child extends java.util.AbstractList {}",
                true, true);
        assertTrue(puml, puml.contains("<<standard>>"));
        assertTrue(puml, puml.contains("java.util.AbstractList"));
    }

    @Test
    public void externalSuperclassGetsExternalStereotype() {
        String puml = generate(
                "package com.app; class MyActivity extends android.app.Activity {}",
                true, true);
        assertTrue(puml, puml.contains("<<external>>"));
        assertTrue(puml, puml.contains("android.app.Activity"));
    }

    @Test
    public void implementedStandardInterfaceGetsStandardStereotype() {
        String puml = generate(
                "package com.app; class Data implements java.io.Serializable {}",
                true, true);
        assertTrue(puml, puml.contains("<<standard>>"));
        assertTrue(puml, puml.contains("java.io.Serializable"));
    }

    @Test
    public void distinguishOffMakesJdkExternal() {
        String puml = generate(
                "package com.app; abstract class Child extends java.util.AbstractList {}",
                true, false);
        assertTrue(puml, puml.contains("<<external>>"));
        assertFalse(puml, puml.contains("<<standard>>"));
    }

    @Test
    public void disabledByDefaultLeavesNoStereotype() {
        String puml = generate(
                "package com.app; abstract class Child extends java.util.AbstractList {}",
                false, true);
        assertFalse(puml, puml.contains("<<standard>>"));
        assertFalse(puml, puml.contains("<<external>>"));
    }

    @Test
    public void projectSuperclassIsNotMarked() {
        // 同一プロジェクト内の親クラスはステレオタイプ化されない
        String puml = generate(
                "package com.app; class Base {} class Sub extends Base {}",
                true, true);
        assertFalse(puml, puml.contains("<<standard>>"));
        assertFalse(puml, puml.contains("<<external>>"));
    }
}
