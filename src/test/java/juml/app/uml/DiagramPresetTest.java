// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.UmlGenerator;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DiagramPresetTest {

    @Test
    public void balancedPresetMatchesDefaultOptions() {
        // BALANCED 適用後の値が new Options() の既定値と一致することを確認。
        // 既存テストとの互換性 (BALANCED 適用がゼロ差分) を担保する柱。
        PlantUmlClassDiagram.Options expected = new PlantUmlClassDiagram.Options();
        PlantUmlClassDiagram.Options actual = new PlantUmlClassDiagram.Options();
        DiagramPreset.BALANCED.applyTo(actual);
        assertEquals(expected.showFields, actual.showFields);
        assertEquals(expected.showMethods, actual.showMethods);
        assertEquals(expected.showVisibility, actual.showVisibility);
        assertEquals(expected.showInheritance, actual.showInheritance);
        assertEquals(expected.showImplementations, actual.showImplementations);
        assertEquals(expected.showUsageRelations, actual.showUsageRelations);
        assertEquals(expected.showAnnotations, actual.showAnnotations);
        assertEquals(expected.showEnumConstants, actual.showEnumConstants);
        assertEquals(expected.showFinal, actual.showFinal);
        assertEquals(expected.showComments, actual.showComments);
        assertEquals(expected.commentStyle, actual.commentStyle);
        assertEquals(expected.commentMaxLength, actual.commentMaxLength);
        assertEquals(expected.groupByPackage, actual.groupByPackage);
        assertEquals(expected.maxClasses, actual.maxClasses);
        assertEquals(expected.maxUsagePerClass, actual.maxUsagePerClass);
        assertEquals(expected.includeLegend, actual.includeLegend);
        assertEquals(expected.publicOnly, actual.publicOnly);
        assertEquals(expected.excludeExternalLibraries, actual.excludeExternalLibraries);
    }

    @Test
    public void minimalPresetTrimsHeavyOptions() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        DiagramPreset.MINIMAL.applyTo(o);
        assertFalse("showFields", o.showFields);
        assertFalse("showVisibility", o.showVisibility);
        assertFalse("showUsageRelations", o.showUsageRelations);
        assertFalse("showAnnotations", o.showAnnotations);
        assertFalse("showComments", o.showComments);
        assertFalse("includeLegend", o.includeLegend);
        assertTrue("publicOnly", o.publicOnly);
        assertTrue("excludeExternalLibraries", o.excludeExternalLibraries);
        assertEquals(40, o.maxClasses);
        assertEquals(5, o.maxUsagePerClass);
        assertEquals(0, o.commentMaxLength);
    }

    @Test
    public void detailedPresetSwitchesToNoteStyle() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        DiagramPreset.DETAILED.applyTo(o);
        assertEquals(PlantUmlClassDiagram.CommentStyle.NOTE, o.commentStyle);
        assertEquals(200, o.commentMaxLength);
        assertEquals(50, o.maxUsagePerClass);
        assertTrue("jetpack.enabled", o.jetpack != null && o.jetpack.enabled);
        assertFalse("excludeExternalLibraries", o.excludeExternalLibraries);
    }

    @Test
    public void customPresetIsNoOp() {
        PlantUmlClassDiagram.Options before = new PlantUmlClassDiagram.Options();
        before.showFields = false;
        before.maxClasses = 7;
        DiagramPreset.CUSTOM.applyTo(before);
        assertFalse(before.showFields);
        assertEquals(7, before.maxClasses);
    }

    @Test
    public void scopeBuilderReceivesPresetFields() {
        DiagramScope.Builder b = DiagramScope.builder();
        DiagramPreset.MINIMAL.applyTo(b);
        DiagramScope scope = b.build();
        assertTrue(scope.isExcludeExternalLibraries());
        assertEquals(VisibilityFilter.PUBLIC_ONLY, scope.getVisibilityFilter());
        assertSame(UmlGenerator.ParseMode.HEADERS_ONLY, scope.getParseMode());
        EnumSet<RelationKind> kinds = scope.getRelationKinds();
        assertTrue(kinds.contains(RelationKind.INHERITANCE));
        assertTrue(kinds.contains(RelationKind.IMPLEMENTATION));
        assertFalse(kinds.contains(RelationKind.USAGE));
        assertEquals(DiagramPreset.MINIMAL, scope.getPreset());
    }

    @Test
    public void fromCliCaseInsensitive() {
        assertEquals(DiagramPreset.MINIMAL, DiagramPreset.fromCli("minimal"));
        assertEquals(DiagramPreset.BALANCED, DiagramPreset.fromCli("Balanced"));
        assertEquals(DiagramPreset.DETAILED, DiagramPreset.fromCli("DETAILED"));
    }

    @Test
    public void fromCliUnknownReturnsCustom() {
        assertEquals(DiagramPreset.CUSTOM, DiagramPreset.fromCli("bogus"));
        assertEquals(DiagramPreset.CUSTOM, DiagramPreset.fromCli(""));
        assertEquals(DiagramPreset.CUSTOM, DiagramPreset.fromCli(null));
    }

    @Test
    public void emptyScopeRecognizesDefaults() {
        DiagramScope all = DiagramScope.ALL;
        assertTrue("ALL should report isEmpty", all.isEmpty());
    }

    @Test
    public void scopeWithPresetIsNotEmpty() {
        DiagramScope.Builder b = DiagramScope.builder();
        DiagramPreset.MINIMAL.applyTo(b);
        DiagramScope scope = b.build();
        assertFalse("scope with MINIMAL preset should not be empty", scope.isEmpty());
    }

    @Test
    public void toBuilderCarriesAllFields() {
        DiagramScope original = DiagramScope.builder()
                .includePackage("a.b")
                .excludePackage("c.d")
                .excludeExternalLibraries(true)
                .visibilityFilter(VisibilityFilter.PUBLIC_ONLY)
                .relationKinds(EnumSet.of(RelationKind.INHERITANCE))
                .parseMode(UmlGenerator.ParseMode.HEADERS_ONLY)
                .preset(DiagramPreset.MINIMAL)
                .build();
        DiagramScope copy = original.toBuilder().build();
        assertEquals(original.getIncludedPackages(), copy.getIncludedPackages());
        assertEquals(original.getExcludedPackages(), copy.getExcludedPackages());
        assertEquals(original.isExcludeExternalLibraries(), copy.isExcludeExternalLibraries());
        assertEquals(original.getVisibilityFilter(), copy.getVisibilityFilter());
        assertEquals(original.getRelationKinds(), copy.getRelationKinds());
        assertEquals(original.getParseMode(), copy.getParseMode());
        assertEquals(original.getPreset(), copy.getPreset());
    }
}
