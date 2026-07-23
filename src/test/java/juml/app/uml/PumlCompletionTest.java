// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlCompletion} の補完ロジックを検証する純ロジックテスト (headless)。
 */
public class PumlCompletionTest {

    @Test
    public void wordPrefix_readsIdentifierBeforeCaret() {
        assertEquals("Fo", PumlCompletion.wordPrefix("class Fo", 8));
        assertEquals("", PumlCompletion.wordPrefix("class Fo", 0));
        assertEquals("@st", PumlCompletion.wordPrefix("@st", 3));
    }

    @Test
    public void candidates_matchKeywordsCaseInsensitively() {
        assertTrue(PumlCompletion.candidates("part", "").contains("participant"));
        assertTrue("大文字小文字を無視して一致",
                PumlCompletion.candidates("CLA", "").contains("class"));
        assertTrue("ディレクティブも補完",
                PumlCompletion.candidates("@st", "").contains("@startuml"));
    }

    @Test
    public void candidates_includeBufferIdentifiers() {
        List<String> c = PumlCompletion.candidates("Foo", "class Foobar\nclass Foobaz\n");
        assertTrue(c.contains("Foobar"));
        assertTrue(c.contains("Foobaz"));
    }

    @Test
    public void candidates_excludeExactPrefix() {
        assertFalse("打ち終わった語 (完全一致) は候補にしない",
                PumlCompletion.candidates("class", "").contains("class"));
    }

    @Test
    public void candidates_emptyPrefixReturnsContextCandidates() {
        // 明示起動 (Ctrl+Space) 用: 空 prefix は「その文脈の候補」を返す (上限 MAX_CANDIDATES
        // まで、キーワード → 本文識別子の順)。以前は空リストで、Ctrl+Space が無反応だった。
        List<String> c = PumlCompletion.candidates("", "class Foo");
        assertFalse("空 prefix でも候補を返すべき", c.isEmpty());
        assertTrue("キーワードを含むべき", c.contains("class"));
        assertTrue("上限 MAX_CANDIDATES を超えないこと",
                c.size() <= PumlCompletion.MAX_CANDIDATES);
        // null は従来どおり候補なし。
        assertTrue("null は候補なし", PumlCompletion.candidates(null, "class Foo").isEmpty());
    }

    @Test
    public void candidates_areCappedAndKeywordDictionaryIsNonEmpty() {
        assertTrue(PumlCompletion.keywordCountForTest() > 20);
        // 多数の識別子があっても上限件数で頭打ちになる。
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("aa").append(i).append(' ');
        }
        assertTrue(PumlCompletion.candidates("aa", sb.toString()).size()
                <= PumlCompletion.MAX_CANDIDATES);
    }
}
