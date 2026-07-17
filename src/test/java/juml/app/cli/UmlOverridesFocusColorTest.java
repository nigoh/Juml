// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.uml.PlantUmlClassDiagram;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link UmlOverrides} の {@code --color-relations} / {@code --focus} 反映テスト。
 */
public class UmlOverridesFocusColorTest {

    private static PlantUmlClassDiagram.Options apply(String... args) {
        CliOptions opts = new CliOptions();
        opts.parse(args);
        UmlOverrides ov = UmlOverrides.build(opts);
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        ov.applyTo(o);
        return o;
    }

    @Test
    public void defaultsLeaveColorAndFocusOff() {
        PlantUmlClassDiagram.Options o = apply("-c");
        assertFalse(o.colorCodeRelations);
        assertEquals("", o.focusClass);
        assertFalse(o.hideEmptyMembers);
        assertFalse(o.hideUnlinkedClasses);
    }

    @Test
    public void colorRelationsFlagEnablesColorCoding() {
        assertTrue(apply("-c", "--color-relations").colorCodeRelations);
    }

    @Test
    public void hideEmptyMembersFlagEnablesDirective() {
        assertTrue(apply("-c", "--hide-empty-members").hideEmptyMembers);
    }

    @Test
    public void hideUnlinkedFlagEnablesDirective() {
        assertTrue(apply("-c", "--hide-unlinked").hideUnlinkedClasses);
    }

    @Test
    public void focusFlagSetsFocusClass() {
        assertEquals("com.example.Foo",
                apply("-c", "--focus", "com.example.Foo").focusClass);
    }

    @Test
    public void excludeNameRegexFlagIsCaptured() {
        CliOptions opts = new CliOptions();
        opts.parse(new String[] { "-c", "--exclude-name-regex", ".*(Test|Impl)$" });
        UmlOverrides ov = UmlOverrides.build(opts);
        assertEquals(".*(Test|Impl)$", ov.excludeNameRegex);
    }

    @Test
    public void excludeNameRegexDefaultsEmpty() {
        CliOptions opts = new CliOptions();
        opts.parse(new String[] { "-c" });
        UmlOverrides ov = UmlOverrides.build(opts);
        assertEquals("", ov.excludeNameRegex);
    }

    @Test
    public void annotationFlagsAreNormalizedToSimpleNames() {
        CliOptions opts = new CliOptions();
        opts.parse(new String[] { "-c",
            "--annotation", "@javax.persistence.Entity, RestController",
            "--exclude-annotation", "Generated" });
        UmlOverrides ov = UmlOverrides.build(opts);
        assertTrue(ov.includedAnnotations.contains("Entity"));
        assertTrue(ov.includedAnnotations.contains("RestController"));
        assertTrue(ov.excludedAnnotations.contains("Generated"));
    }

    @Test
    public void annotationFlagsDefaultEmpty() {
        CliOptions opts = new CliOptions();
        opts.parse(new String[] { "-c" });
        UmlOverrides ov = UmlOverrides.build(opts);
        assertTrue(ov.includedAnnotations.isEmpty());
        assertTrue(ov.excludedAnnotations.isEmpty());
    }
}
