// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 宣言の読み取りと未宣言参加者の検出 ({@link PumlSymbols}) を検証する
 * 純ロジックテスト (headless)。
 */
public class PumlSymbolsTest {

    private static String doc(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    // -------------------------------------------------------------------------
    // 宣言の読み取り
    // -------------------------------------------------------------------------

    @Test
    public void declarations_carryNameKindAndLine() {
        List<PumlSymbols.Symbol> s = PumlSymbols.declarations(doc(
                "@startuml", "participant Alice", "class Foo", "state Idle", "@enduml"));
        assertEquals(3, s.size());
        assertEquals("Alice", s.get(0).name());
        assertEquals("participant", s.get(0).kind());
        assertEquals(2, s.get(0).line());
        assertEquals("Foo", s.get(1).name());
        assertEquals(3, s.get(1).line());
        assertEquals("Idle", s.get(2).name());
        assertEquals(4, s.get(2).line());
    }

    @Test
    public void aliasIsTheReferenceName_whileTheQuotedTextIsShown() {
        // 本文からは別名で参照される。一覧には読みやすい表示名を出したい。
        List<PumlSymbols.Symbol> s = PumlSymbols.declarations(
                doc("@startuml", "participant \"Long Display Name\" as LN", "@enduml"));
        assertEquals(1, s.size());
        assertEquals("LN", s.get(0).name());
        assertEquals("Long Display Name", s.get(0).display());
    }

    @Test
    public void quotedNameWithoutAlias_isUsedAsBoth() {
        List<PumlSymbols.Symbol> s = PumlSymbols.declarations(
                doc("@startuml", "entity \"Order\"", "@enduml"));
        assertEquals("Order", s.get(0).name());
        assertEquals("Order", s.get(0).display());
    }

    @Test
    public void abstractClassIsRecognised() {
        List<PumlSymbols.Symbol> s = PumlSymbols.declarations(
                doc("@startuml", "abstract class Shape", "@enduml"));
        assertEquals(1, s.size());
        assertEquals("Shape", s.get(0).name());
    }

    @Test
    public void containersAreListedToo() {
        // package / namespace / partition も飛び先として役に立つ。
        List<PumlSymbols.Symbol> s = PumlSymbols.declarations(doc(
                "@startuml", "package \"web\" {", "  [App]", "}",
                "namespace core {", "}", "@enduml"));
        assertEquals(2, s.size());
        assertEquals("web", s.get(0).display());
        assertEquals("core", s.get(1).display());
    }

    @Test
    public void declaredNames_areUniqueAndOrdered() {
        assertEquals(List.of("A", "B"), PumlSymbols.declaredNames(
                doc("@startuml", "participant A", "participant B", "participant A",
                        "@enduml")));
    }

    // -------------------------------------------------------------------------
    // 未宣言参加者
    // -------------------------------------------------------------------------

    @Test
    public void undeclaredParticipants_areFoundInFirstAppearanceOrder() {
        assertEquals(List.of("Alice", "Bob", "Carol"), PumlSymbols.undeclaredParticipants(
                doc("@startuml", "Alice -> Bob : hi", "Bob -> Carol : relay", "@enduml")));
    }

    @Test
    public void alreadyDeclaredParticipants_areNotRepeated() {
        assertEquals(List.of("Bob"), PumlSymbols.undeclaredParticipants(
                doc("@startuml", "participant Alice", "Alice -> Bob : hi", "@enduml")));
    }

    @Test
    public void aliasCountsAsDeclared() {
        assertTrue(PumlSymbols.undeclaredParticipants(
                doc("@startuml", "participant \"Alice Smith\" as Alice",
                        "Alice -> Alice : self", "@enduml")).isEmpty());
    }

    @Test
    public void keywordsAreNotMistakenForParticipants() {
        // "note left of X" や制御語を送り手と読み違えない。
        List<String> missing = PumlSymbols.undeclaredParticipants(doc(
                "@startuml", "alt ok", "  A -> B : hi", "else", "  A -> B : no", "end",
                "@enduml"));
        assertEquals(List.of("A", "B"), missing);
    }

    @Test
    public void commentsAreIgnored() {
        assertTrue(PumlSymbols.undeclaredParticipants(
                doc("@startuml", "' Ghost -> Phantom : hi", "@enduml")).isEmpty());
        assertTrue(PumlSymbols.undeclaredParticipants(
                doc("@startuml", "/'", "Ghost -> Phantom : hi", "'/", "@enduml")).isEmpty());
    }

    @Test
    public void quotedParticipantNames_areUnquotedAndRequoted() {
        List<String> missing = PumlSymbols.undeclaredParticipants(
                doc("@startuml", "\"Web Server\" -> DB : query", "@enduml"));
        assertEquals(List.of("Web Server", "DB"), missing);
        assertEquals("participant \"Web Server\"", PumlSymbols.declarationFor("Web Server"));
        assertEquals("participant DB", PumlSymbols.declarationFor("DB"));
    }

    @Test
    public void classDiagramRelations_areNotTreatedAsMessages() {
        // クラス図の関係記法は participant を必要としない。
        assertTrue(PumlSymbols.undeclaredParticipants(
                doc("@startuml", "class Foo", "class Bar", "Foo <|-- Bar", "@enduml"))
                .isEmpty());
    }

    // -------------------------------------------------------------------------
    // 挿入位置
    // -------------------------------------------------------------------------

    @Test
    public void insertOffset_followsTheLastParticipantDeclaration() {
        String text = doc("@startuml", "participant A", "participant B", "A -> C : hi",
                "@enduml");
        int at = PumlSymbols.declarationInsertOffset(text);
        assertEquals("participant B の行末の直後", text.indexOf("A -> C"), at);
    }

    @Test
    public void insertOffset_followsHeaderLinesWhenNothingIsDeclared() {
        String text = doc("@startuml", "title My Diagram", "skinparam monochrome true",
                "A -> B : hi", "@enduml");
        assertEquals(text.indexOf("A -> B"), PumlSymbols.declarationInsertOffset(text));
    }

    @Test
    public void insertOffset_isZeroForABareFragment() {
        assertEquals(0, PumlSymbols.declarationInsertOffset("A -> B : hi\n"));
    }

    @Test
    public void emptyAndNullInput_areHandled() {
        assertTrue(PumlSymbols.declarations(null).isEmpty());
        assertTrue(PumlSymbols.declarations("").isEmpty());
        assertTrue(PumlSymbols.undeclaredParticipants(null).isEmpty());
        assertEquals(0, PumlSymbols.declarationInsertOffset(null));
    }

    @Test
    public void completionStillSeesTheSameNames() {
        // 補完とアウトラインが同じ読み取りを共有していること (規則の食い違い防止)。
        String text = doc("@startuml", "participant \"Long\" as LN", "class Foo", "@enduml");
        List<String> viaContext =
                PumlCompletionContext.at(text, text.length()).declaredNames();
        assertEquals(PumlSymbols.declaredNames(text), viaContext);
        assertFalse(viaContext.isEmpty());
    }
}
