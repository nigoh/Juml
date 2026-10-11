// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 一括再インデント ({@link PumlFormatter}) の純ロジックテスト (headless)。
 *
 * <p>整形は「行頭の空白しか変えない」のが契約。意味 (各行の trim 結果・行数・
 * 構文チェックの結果) が変わらないことを、雛形全件のスイープでも固定する。</p>
 */
public class PumlFormatterTest {

    private static String fmt(String... lines) {
        return PumlFormatter.format(String.join("\n", lines) + "\n");
    }

    private static String joined(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    // -------------------------------------------------------------------------
    // 字下げの形
    // -------------------------------------------------------------------------

    @Test
    public void nestedBlocksAreIndentedByTwoSpaces() {
        assertEquals(joined(
                "@startuml",
                "alt ok",
                "  loop n",
                "    A -> B : hi",
                "  end",
                "end",
                "@enduml"),
                fmt("@startuml", "alt ok", "loop n", "A -> B : hi",
                        "end", "end", "@enduml"));
    }

    @Test
    public void elseAndCaseSitOneLevelShallower() {
        assertEquals(joined(
                "@startuml",
                "alt ok",
                "  A -> B",
                "else ng",
                "  B -> A",
                "end",
                "@enduml"),
                fmt("@startuml", "alt ok", "A -> B", "  else ng", "B -> A",
                        "end", "@enduml"));
    }

    @Test
    public void classBodiesIndentTheirMembers() {
        assertEquals(joined(
                "@startuml",
                "class Foo {",
                "  +bar()",
                "  -baz: int",
                "}",
                "@enduml"),
                fmt("@startuml", "class Foo {", "+bar()", "    -baz: int",
                        "}", "@enduml"));
    }

    @Test
    public void startAndEndStayInColumnZero() {
        assertEquals(joined("@startuml", "A -> B", "@enduml"),
                fmt("  @startuml", "  A -> B", "  @enduml"));
    }

    @Test
    public void notePoseIsLeftVerbatim() {
        // 注記の本文は文章。字下げは書き手の意図なので触らない。
        assertEquals(joined(
                "@startuml",
                "note over A",
                "   keep   this",
                "      as written",
                "end note",
                "@enduml"),
                fmt("@startuml", "note over A", "   keep   this",
                        "      as written", "   end note", "@enduml"));
    }

    @Test
    public void nonUmlDiagramsAreLeftVerbatim() {
        // salt/json/mindmap は字下げ自体に意味がある。
        String salt = joined("@startsalt", "{", "   [OK] | [Cancel]", "}", "@endsalt");
        assertEquals(salt, PumlFormatter.format(salt));
        String mindmap = joined("@startmindmap", "* Root", " ** Child", "@endmindmap");
        assertEquals(mindmap, PumlFormatter.format(mindmap));
    }

    @Test
    public void blockCommentsAreLeftVerbatim() {
        assertEquals(joined(
                "@startuml",
                "/'",
                "   ascii art stays",
                "'/",
                "A -> B",
                "@enduml"),
                fmt("@startuml", "/'", "   ascii art stays", "'/",
                        "  A -> B", "@enduml"));
    }

    @Test
    public void lineCommentsFollowTheCodeIndent() {
        assertEquals(joined(
                "@startuml",
                "alt ok",
                "  ' explain",
                "  A -> B",
                "end",
                "@enduml"),
                fmt("@startuml", "alt ok", "' explain", "A -> B", "end", "@enduml"));
    }

    @Test
    public void blankLinesLoseTrailingWhitespace() {
        assertEquals("@startuml\n\nA -> B\n@enduml\n",
                PumlFormatter.format("@startuml\n   \nA -> B\n@enduml\n"));
    }

    @Test
    public void activityEndDoesNotDedent() {
        // アクティビティ図の end はフロー終端の文であって、ブロックの終端ではない。
        assertEquals(joined(
                "@startuml",
                "start",
                ":a;",
                "end",
                "@enduml"),
                fmt("@startuml", "start", ":a;", "  end", "@enduml"));
    }

    @Test
    public void unclosedBlocksNeverGoNegative() {
        assertEquals(joined("@startuml", "end", "endif", "A -> B", "@enduml"),
                fmt("@startuml", "  end", "  endif", "  A -> B", "@enduml"));
    }

    // -------------------------------------------------------------------------
    // 意味の不変条件 (雛形全件スイープ)
    // -------------------------------------------------------------------------

    private static List<String> corpus() {
        List<String> out = new ArrayList<>();
        for (PumlSnippets.Group g : PumlSnippets.Group.values()) {
            for (PumlSnippets.Snippet s : PumlSnippets.forGroup(g)) {
                String body = PumlSnippetTemplate.expand(s.body()).text();
                out.add("@startuml\n" + body
                        + (body.endsWith("\n") ? "" : "\n") + "@enduml\n");
            }
        }
        return out;
    }

    @Test
    public void formattingNeverChangesWhatEachLineSays() {
        for (String text : corpus()) {
            String[] before = text.split("\n", -1);
            String[] after = PumlFormatter.format(text).split("\n", -1);
            assertEquals("行数が変わった:\n" + text, before.length, after.length);
            for (int i = 0; i < before.length; i++) {
                assertEquals("行の中身が変わった:\n" + text,
                        before[i].strip(), after[i].strip());
            }
        }
    }

    @Test
    public void formattingNeverChangesTheDiagnostics() {
        for (String text : corpus()) {
            assertEquals("構文チェックの結果が変わった:\n" + text,
                    PumlDiagnostics.analyze(text).toString(),
                    PumlDiagnostics.analyze(PumlFormatter.format(text)).toString());
        }
    }

    @Test
    public void formattingIsIdempotent() {
        for (String text : corpus()) {
            String once = PumlFormatter.format(text);
            assertEquals("2 回目の整形で変わった:\n" + text,
                    once, PumlFormatter.format(once));
        }
    }
}
