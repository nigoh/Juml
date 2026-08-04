// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PumlSnippets.Group;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlCompletionContext} の図種判定・行状況の読み取りを検証する
 * 純ロジックテスト (headless)。
 */
public class PumlCompletionContextTest {

    private static Group flavorOf(String text) {
        return PumlCompletionContext.at(text, text.length()).flavor();
    }

    @Test
    public void sequenceSignatures_areDetected() {
        assertEquals(Group.SEQUENCE, flavorOf(
                "@startuml\nparticipant Alice\nparticipant Bob\nAlice -> Bob : hi\n"));
    }

    @Test
    public void classSignatures_areDetected() {
        assertEquals(Group.CLASS, flavorOf(
                "@startuml\nclass Foo\nclass Bar\nFoo <|-- Bar\n"));
    }

    @Test
    public void activitySignatures_areDetected() {
        assertEquals(Group.ACTIVITY, flavorOf(
                "@startuml\nstart\n:do it;\nif (ok?) then (yes)\nendif\nstop\n"));
    }

    @Test
    public void stateSignatures_areDetected() {
        assertEquals(Group.STATE, flavorOf(
                "@startuml\n[*] --> Idle\nstate Idle\nIdle --> Busy : go\n"));
    }

    @Test
    public void useCaseSignatures_areDetected() {
        assertEquals(Group.USECASE, flavorOf(
                "@startuml\nusecase (Login)\nusecase (Logout)\n(Login) ..> (Auth)"
                        + " : <<include>>\n"));
    }

    @Test
    public void erSignatures_areDetected() {
        assertEquals(Group.ER, flavorOf(
                "@startuml\nentity user\nentity order\nuser ||--o{ order : places\n"));
    }

    @Test
    public void blockSpecifier_beatsBodySignatures() {
        // @startmindmap の中身は「* 見出し」で、クラス図の記法と紛らわしいことがある。
        // ブロック指定子が明示されているならそちらを信じる。
        assertEquals(Group.MINDMAP, flavorOf("@startmindmap\n* Root\n** Child\n"));
        assertEquals(Group.GANTT, flavorOf("@startgantt\n[Task] lasts 5 days\n"));
        assertEquals(Group.SALT, flavorOf("@startsalt\n{\n  [OK] | [Cancel]\n}\n"));
        assertEquals(Group.JSON, flavorOf("@startjson\n{ \"a\": 1 }\n"));
    }

    @Test
    public void ambiguousOrEmptyText_fallsBackToCommon() {
        // 決め手が無いのに図種を決め打ちすると、正しい候補を沈める害のほうが大きい。
        assertEquals(Group.COMMON, flavorOf(""));
        assertEquals(Group.COMMON, flavorOf("@startuml\n@enduml\n"));
        assertEquals(Group.COMMON, flavorOf("@startuml\ntitle Hello\n"));
    }

    @Test
    public void lineStart_isTrueOnlyWhenNothingButSpacePrecedes() {
        assertTrue(PumlCompletionContext.at("  al", 4).atLineStart());
        assertTrue(PumlCompletionContext.at("x\nal", 4).atLineStart());
        assertFalse(PumlCompletionContext.at("Alice -> al", 11).atLineStart());
    }

    @Test
    public void indent_reportsLeadingWhitespaceOfCurrentLine() {
        assertEquals("    ", PumlCompletionContext.at("a\n    al", 8).indent());
        assertEquals("", PumlCompletionContext.at("al", 2).indent());
    }

    @Test
    public void linePrefix_stopsBeforeTheWordBeingTyped() {
        assertEquals("Alice -> ", PumlCompletionContext.at("Alice -> Bo", 11).linePrefix());
    }

    @Test
    public void declaredNames_prefersAliasAndKeepsDeclarationOrder() {
        String text = "@startuml\nparticipant \"Long Name\" as LN\nclass Foo\n"
                + "actor Bob\n";
        assertEquals(java.util.List.of("LN", "Foo", "Bob"),
                PumlCompletionContext.at(text, text.length()).declaredNames());
    }

    @Test
    public void declaredNames_readsQuotedNameWhenThereIsNoAlias() {
        String text = "@startuml\nentity \"Order\"\n";
        assertTrue(PumlCompletionContext.at(text, text.length())
                .declaredNames().contains("Order"));
    }
}
