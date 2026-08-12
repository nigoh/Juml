// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * 矢印ホバー ({@link PumlCompletionDictionary#arrowHover}) の純ロジックテスト
 * (headless)。誤った説明を出すくらいなら何も出さない、が方針。
 */
public class PumlArrowHoverTest {

    private static String hoverAt(String text, String token) {
        return PumlCompletionDictionary.arrowHover(text, text.indexOf(token) + 1);
    }

    @Test
    public void classArrowsExplainThemselves() {
        String text = "Foo <|-- Bar\n";
        assertNotNull(hoverAt(text, "<|--"));
        // 継承 (<|--) と合成 (*--) は別の説明が付く。
        String inherit = hoverAt("A <|-- B\n", "<|--");
        String compose = hoverAt("A *-- B\n", "*--");
        assertNotNull(compose);
        assertNotEquals(inherit, compose);
    }

    @Test
    public void sequenceArrowsExplainThemselves() {
        assertNotNull(hoverAt("A -> B : hi\n", "->"));
        assertNotNull(hoverAt("A ->> B : hi\n", "->>"));
    }

    @Test
    public void longerDashesFallBackToTheCanonicalForm() {
        // ---> は --> の長さ違い。意味は同じなので同じ説明を出す。
        assertEquals(hoverAt("A --> B\n", "-->"), hoverAt("A ---> B\n", "--->"));
        assertEquals(hoverAt("A <|-- B\n", "<|--"), hoverAt("A <|---- B\n", "<|----"));
    }

    @Test
    public void coloredArrowsFallBackToThePlainForm() {
        assertNotNull(hoverAt("A -[#blue]> B : hi\n", "-[#blue]>"));
    }

    @Test
    public void plainWordsAndNamesGetNoHover() {
        String text = "participant Alice\nA --> B\n";
        assertNull(PumlCompletionDictionary.arrowHover(text, text.indexOf("Alice") + 1));
        assertNull(PumlCompletionDictionary.arrowHover(text, text.indexOf("participant")));
    }

    @Test
    public void unknownSquigglesGetNoHover() {
        String text = "A ~~> B\n";
        assertNull(hoverAt(text, "~~>"));
    }

    @Test
    public void outOfRangeOffsetsAreSafe() {
        assertNull(PumlCompletionDictionary.arrowHover("A -> B", -1));
        assertNull(PumlCompletionDictionary.arrowHover("A -> B", 99));
        assertNull(PumlCompletionDictionary.arrowHover("", 0));
        assertNull(PumlCompletionDictionary.arrowHover(null, 0));
    }
}
