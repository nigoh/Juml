// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ActivityDiffHighlight} のノード色付けを純ロジックとして検証する (Swing 非依存)。
 */
public class ActivityDiffHighlightTest {

    private static final String OLD =
            "@startuml\nstart\n:load();\n:validate();\n:save();\nstop\n@enduml\n";
    private static final String NEW =
            "@startuml\nstart\n:load();\n:validate(strict);\n:audit();\n:save();\nstop\n@enduml\n";

    @Test
    public void addedNode_isGreenOnNewSideOnly() {
        String[] sides = ActivityDiffHighlight.colorize(OLD, NEW);
        String oldSide = sides[0];
        String newSide = sides[1];
        assertTrue("新側の追加ノードは緑前景",
                newSide.contains(":<color:" + ActivityDiffHighlight.ADDED_FG
                        + ">audit()</color>;"));
        assertFalse("旧側に追加ノードは無い", oldSide.contains("audit()"));
    }

    @Test
    public void changedNode_isYellowOnBothSidesWithRespectiveText() {
        String[] sides = ActivityDiffHighlight.colorize(OLD, NEW);
        // validate() -> validate(strict) は変更ペア (黄)。
        assertTrue(sides[0].contains(":<color:" + ActivityDiffHighlight.MODIFIED_FG
                + ">validate()</color>;"));
        assertTrue(sides[1].contains(":<color:" + ActivityDiffHighlight.MODIFIED_FG
                + ">validate(strict)</color>;"));
    }

    @Test
    public void unchangedNodesAndControlLinesAreNotPainted() {
        String[] sides = ActivityDiffHighlight.colorize(OLD, NEW);
        for (String side : sides) {
            assertTrue("start/stop などの制御行は原文のまま", side.contains("\nstart\n"));
            assertTrue("不変ノードは無着色のまま残る", side.contains("\n:load();\n"));
            assertTrue(side.startsWith("@startuml"));
        }
    }

    @Test
    public void removedNode_isRedStruckOnOldSideOnly() {
        String oldWithExtra =
                "@startuml\nstart\n:a();\n:gone();\n:b();\nstop\n@enduml\n";
        String newWithout =
                "@startuml\nstart\n:a();\n:b();\nstop\n@enduml\n";
        String[] sides = ActivityDiffHighlight.colorize(oldWithExtra, newWithout);
        assertTrue("旧側の削除ノードは赤打ち消し",
                sides[0].contains(":<color:" + ActivityDiffHighlight.REMOVED_FG
                        + "><s>gone()</s></color>;"));
        assertFalse("新側に削除ノードは無い", sides[1].contains("gone()"));
    }

    @Test
    public void identical_producesNoColorMarkers() {
        String[] sides = ActivityDiffHighlight.colorize(OLD, OLD);
        assertEquals(OLD.trim(), sides[0].trim());
        assertEquals(OLD.trim(), sides[1].trim());
    }
}
