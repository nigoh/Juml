// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link SequenceDiffHighlight} のメッセージ色付けを純ロジックとして検証する (Swing 非依存)。
 */
public class SequenceDiffHighlightTest {

    private static final String OLD =
            "@startuml\nCaller -> Svc : run()\nSvc -> Repo : load()\n"
            + "Svc -> Repo : save()\n@enduml\n";
    private static final String NEW =
            "@startuml\nCaller -> Svc : run()\nSvc -> Repo : load()\n"
            + "Svc -> Validator : check()\nSvc -> Repo : save()\n@enduml\n";

    @Test
    public void addedMessage_labelIsGreenOnNewSideOnly() {
        String[] sides = SequenceDiffHighlight.colorize(OLD, NEW);
        assertTrue("新側の追加メッセージのラベルが緑",
                sides[1].contains(" : <color:" + SequenceDiffHighlight.ADDED_FG
                        + ">check()</color>"));
        assertFalse("旧側に追加メッセージは無い", sides[0].contains("check()"));
    }

    @Test
    public void removedMessage_labelIsRedStruckOnOldSideOnly() {
        String oldWithExtra =
                "@startuml\nA -> B : a()\nA -> B : gone()\nA -> B : c()\n@enduml\n";
        String newWithout = "@startuml\nA -> B : a()\nA -> B : c()\n@enduml\n";
        String[] sides = SequenceDiffHighlight.colorize(oldWithExtra, newWithout);
        assertTrue("旧側の削除メッセージは赤打ち消し",
                sides[0].contains(" : <color:" + SequenceDiffHighlight.REMOVED_FG
                        + "><s>gone()</s></color>"));
        assertFalse(sides[1].contains("gone()"));
    }

    @Test
    public void modifiedMessage_labelIsYellowWithRespectiveText() {
        String oldSrc = "@startuml\nA -> B : load()\n@enduml\n";
        String newSrc = "@startuml\nA -> B : load(strict)\n@enduml\n";
        String[] sides = SequenceDiffHighlight.colorize(oldSrc, newSrc);
        assertTrue(sides[0].contains(" : <color:" + SequenceDiffHighlight.MODIFIED_FG
                + ">load()</color>"));
        assertTrue(sides[1].contains(" : <color:" + SequenceDiffHighlight.MODIFIED_FG
                + ">load(strict)</color>"));
    }

    @Test
    public void nonMessageLinesAndUnchangedMessagesAreNotColored() {
        String oldSrc = "@startuml\nparticipant A\nactivate A\nA -> B : keep()\n@enduml\n";
        String newSrc = "@startuml\nparticipant A\nactivate A\nA -> B : keep()\n"
                + "A -> C : added()\n@enduml\n";
        String[] sides = SequenceDiffHighlight.colorize(oldSrc, newSrc);
        for (String side : sides) {
            assertTrue("participant 宣言は原文のまま", side.contains("\nparticipant A\n"));
            assertTrue("activate 行は原文のまま", side.contains("\nactivate A\n"));
            assertFalse("不変メッセージには色が付かない",
                    side.contains("<color:" + SequenceDiffHighlight.ADDED_FG + ">keep()"));
        }
    }

    @Test
    public void realFormat_colonAttachedToTarget_isColored() {
        // 同梱 PlantUML の実出力は "caller -> target: label" (コロンが target に密着)。
        String oldSrc = "@startuml\nCaller -> Svc: Svc.load()\n@enduml\n";
        String newSrc = "@startuml\nCaller -> Svc: Svc.load()\nSvc -> Svc: Svc.validate()\n@enduml\n";
        String[] sides = SequenceDiffHighlight.colorize(oldSrc, newSrc);
        assertTrue("target 密着コロン形式でも追加ラベルが緑になる",
                sides[1].contains("Svc: <color:" + SequenceDiffHighlight.ADDED_FG
                        + ">Svc.validate()</color>"));
    }

    @Test
    public void identical_producesNoColorMarkers() {
        String[] sides = SequenceDiffHighlight.colorize(OLD, OLD);
        assertEquals(OLD.trim(), sides[0].trim());
        assertEquals(OLD.trim(), sides[1].trim());
    }
}
