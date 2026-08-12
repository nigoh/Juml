// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 記号リネーム ({@link PumlRename}) の純ロジックテスト (headless)。
 *
 * <p>中心の関心は「巻き込んではいけない場所を巻き込まない」こと。
 * 検索置換との違いはそこにしかない。</p>
 */
public class PumlRenameTest {

    // -------------------------------------------------------------------------
    // 語の切り出し
    // -------------------------------------------------------------------------

    @Test
    public void wordAt_findsTheIdentifierAroundTheCaret() {
        String text = "participant Alice\n";
        assertEquals("Alice", PumlRename.wordAt(text, text.indexOf("Alice")));
        assertEquals("Alice", PumlRename.wordAt(text, text.indexOf("Alice") + 3));
        assertEquals("Alice", PumlRename.wordAt(text, text.length() - 1));
    }

    @Test
    public void wordAt_returnsEmptyBetweenWords() {
        String text = "A -> B\n";
        assertEquals("", PumlRename.wordAt(text, text.indexOf("->")));
    }

    // -------------------------------------------------------------------------
    // 出現の判定
    // -------------------------------------------------------------------------

    @Test
    public void renamesOnlyWholeWordMatches() {
        String text = "@startuml\nparticipant Alice\nAlice -> AliceSmith : hi\n@enduml\n";
        String out = PumlRename.rename(text, "Alice", "Carol");
        assertEquals("@startuml\nparticipant Carol\nCarol -> AliceSmith : hi\n@enduml\n",
                out);
    }

    @Test
    public void leavesCommentsAlone() {
        // コメントの中の同名語は参照ではない。書き換えても図は変わらず、
        // 文章だけが壊れる。
        String text = "@startuml\n' Alice is the caller\n/'\nAlice legacy\n'/\n"
                + "participant Alice\n@enduml\n";
        String out = PumlRename.rename(text, "Alice", "Carol");
        assertTrue(out.contains("' Alice is the caller"));
        assertTrue(out.contains("Alice legacy"));
        assertTrue(out.contains("participant Carol"));
    }

    @Test
    public void leavesQuotedDisplayNamesAlone() {
        String text = "participant \"Alice in charge\" as Alice\nAlice -> Bob\n";
        String out = PumlRename.rename(text, "Alice", "Carol");
        assertEquals("participant \"Alice in charge\" as Carol\nCarol -> Bob\n", out);
    }

    @Test
    public void leavesMessageLabelsAlone() {
        // 「: 」より後ろはラベル文。文章に紛れた同名語の誤置換は読み直すまで
        // 気づけないので、参照の書き換え漏れより害が大きい。
        String text = "Alice -> Bob : Alice sends a note\n";
        assertEquals("Carol -> Bob : Alice sends a note\n",
                PumlRename.rename(text, "Alice", "Carol"));
    }

    @Test
    public void leavesActivityLabelsAlone() {
        String text = "start\n:Alice works;\nstop\n";
        assertEquals(text, PumlRename.rename(text, "Alice", "Carol"));
    }

    @Test
    public void renamesDottedNamesAsOneWord() {
        String text = "class app.Alice\napp.Alice --> app.Bob\n";
        String out = PumlRename.rename(text, "app.Alice", "app.Carol");
        assertEquals("class app.Carol\napp.Carol --> app.Bob\n", out);
        // 部分語 (Alice 単体) では dotted 名を巻き込まない。
        assertEquals(text, PumlRename.rename(text, "Alice", "Carol"));
    }

    @Test
    public void noOccurrences_returnsTextUnchanged() {
        String text = "participant Bob\n";
        assertEquals(text, PumlRename.rename(text, "Alice", "Carol"));
    }

    // -------------------------------------------------------------------------
    // 新しい名前の検証
    // -------------------------------------------------------------------------

    @Test
    public void newNameMustBeAPlainIdentifier() {
        assertTrue(PumlRename.isValidNewName("Bob_2"));
        assertTrue(PumlRename.isValidNewName("app.Carol"));
        assertFalse(PumlRename.isValidNewName(""));
        assertFalse(PumlRename.isValidNewName(null));
        assertFalse(PumlRename.isValidNewName("2Bob"));
        assertFalse(PumlRename.isValidNewName("Bo b"));
        assertFalse(PumlRename.isValidNewName("Bob!"));
    }

    @Test
    public void newNameMustNotBeAKeyword() {
        // Alice を end に改名できてしまうと、図が静かに壊れる。
        assertFalse(PumlRename.isValidNewName("end"));
        assertFalse(PumlRename.isValidNewName("Alt"));
        assertFalse(PumlRename.isValidNewName("participant"));
    }
}
