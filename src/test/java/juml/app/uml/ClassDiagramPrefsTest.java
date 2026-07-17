// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassDiagramPrefsTest {

    @Test
    public void defaultsMatchBalancedPresetExpectations() {
        ClassDiagramPrefs cp =
                ClassDiagramPrefs.defaults();
        assertTrue(cp.showFields);
        assertTrue(cp.showMethods);
        assertTrue(cp.showAnnotations);
        assertFalse(cp.publicOnly);
        assertFalse(cp.excludeExternal);
        assertFalse(cp.markExternalSupertypes);
        assertFalse(cp.colorCodeRelations);
        assertEquals(80, cp.commentMaxLength);
        assertTrue(cp.hiddenAnnotations.contains("Override"));
        assertTrue(cp.hiddenAnnotations.contains("SuppressWarnings"));
    }

    @Test
    public void colorCodeRelationsConstructorSetsField() {
        ClassDiagramPrefs cp =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, true, 80, null);
        assertTrue("colorCodeRelations should be retained", cp.colorCodeRelations);
        // 後方互換 (8 引数) コンストラクタは colorCodeRelations=false で生成する
        ClassDiagramPrefs legacy =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, 80, null);
        assertFalse("legacy ctor defaults colorCodeRelations off", legacy.colorCodeRelations);
    }

    @Test
    public void densityTogglesDefaultOffAndAreRetainedBy11ArgCtor() {
        // 既定値 (8/9 引数コンストラクタ経由) は false
        ClassDiagramPrefs def =
                ClassDiagramPrefs.defaults();
        assertFalse(def.hideEmptyMembers);
        assertFalse(def.hideUnlinked);
        ClassDiagramPrefs nineArg =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, false, 80, null);
        assertFalse("9 引数コンストラクタは密度トグルを false にする", nineArg.hideEmptyMembers);
        assertFalse(nineArg.hideUnlinked);
        // 11 引数コンストラクタは両フラグを保持する
        ClassDiagramPrefs full =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, false, 80, null, true, true);
        assertTrue(full.hideEmptyMembers);
        assertTrue(full.hideUnlinked);
        // 11 引数版は colorCodeStereotypes を false に、12 引数版は保持する
        assertFalse(full.colorCodeStereotypes);
        ClassDiagramPrefs twelve =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, false, 80, null, false, false, true);
        assertTrue(twelve.colorCodeStereotypes);
        assertFalse(ClassDiagramPrefs.defaults().colorCodeStereotypes);
    }

    @Test
    public void parseCsvSplitsAndTrims() {
        Set<String> result = ClassDiagramPrefs.parseCsv(
                "Override, Nullable ,NonNull, ,Keep");
        assertEquals(4, result.size());
        assertTrue(result.contains("Override"));
        assertTrue(result.contains("Nullable"));
        assertTrue(result.contains("NonNull"));
        assertTrue(result.contains("Keep"));
    }

    @Test
    public void parseCsvHandlesNullAndEmpty() {
        assertTrue(ClassDiagramPrefs.parseCsv(null).isEmpty());
        assertTrue(ClassDiagramPrefs.parseCsv("").isEmpty());
        assertTrue(ClassDiagramPrefs.parseCsv("  ").isEmpty());
    }

    @Test
    public void hiddenAnnotationsCsvJoinsValues() {
        Set<String> input = new LinkedHashSet<>();
        input.add("Override");
        input.add("Deprecated");
        ClassDiagramPrefs cp =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, 80, input);
        assertEquals("Override,Deprecated", cp.hiddenAnnotationsCsv());
    }

    @Test
    public void commentMaxLengthClampedAtZero() {
        ClassDiagramPrefs cp =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, -10, null);
        assertEquals(0, cp.commentMaxLength);
        assertTrue(cp.hiddenAnnotations.isEmpty());
    }

    @Test
    public void hiddenAnnotationsIsImmutable() {
        Set<String> input = new LinkedHashSet<>();
        input.add("Override");
        ClassDiagramPrefs cp =
                new ClassDiagramPrefs(true, true, true,
                        false, false, false, 80, input);
        try {
            cp.hiddenAnnotations.add("NewItem");
            // 不変セットなので UnsupportedOperationException 必須
            assertFalse("hiddenAnnotations should be immutable", true);
        } catch (UnsupportedOperationException expected) {
            // OK
        }
    }
}
