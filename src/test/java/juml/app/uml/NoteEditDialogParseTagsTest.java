// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link NoteEditDialog#parseTags} の pure ロジックテスト (headless 実行可)。
 *
 * <p>{@link NoteEditDialog#parseTags} は Swing を一切使わないため、
 * Display なしの headless 環境でも実行できる。入力検証の境界・異常系をカバーする。</p>
 *
 * <p>GUI 構造テストは {@link NoteEditDialogTest} に分離してある。</p>
 */
public class NoteEditDialogParseTagsTest {

    @Test
    public void null_returnsEmpty() {
        List<String> tags = NoteEditDialog.parseTags(null);
        assertTrue("null を渡したとき空リストが返るべき", tags.isEmpty());
    }

    @Test
    public void emptyString_returnsEmpty() {
        List<String> tags = NoteEditDialog.parseTags("");
        assertTrue("空文字列のとき空リストが返るべき", tags.isEmpty());
    }

    @Test
    public void whitespaceOnly_returnsEmpty() {
        List<String> tags = NoteEditDialog.parseTags("   ");
        assertTrue("空白のみのとき空リストが返るべき", tags.isEmpty());
    }

    @Test
    public void commaSeparated_returnsList() {
        List<String> tags = NoteEditDialog.parseTags("foo, bar, baz");
        assertEquals("カンマ区切りのタグ数が一致するべき", 3, tags.size());
        assertTrue("'foo' が含まれるべき", tags.contains("foo"));
        assertTrue("'bar' が含まれるべき", tags.contains("bar"));
        assertTrue("'baz' が含まれるべき", tags.contains("baz"));
    }

    @Test
    public void spaceSeparated_returnsList() {
        List<String> tags = NoteEditDialog.parseTags("alpha beta gamma");
        assertEquals("スペース区切りのタグ数が一致するべき", 3, tags.size());
        assertEquals(Arrays.asList("alpha", "beta", "gamma"), tags);
    }

    @Test
    public void mixedSeparators_returnsList() {
        List<String> tags = NoteEditDialog.parseTags("a, b  c,d");
        assertEquals("混在区切りのタグ数が一致するべき", 4, tags.size());
    }

    @Test
    public void duplicates_deduplicated() {
        List<String> tags = NoteEditDialog.parseTags("foo, bar, foo");
        assertEquals("重複タグは除去されるべき", 2, tags.size());
        assertEquals("foo が 1 つ目", "foo", tags.get(0));
        assertEquals("bar が 2 つ目", "bar", tags.get(1));
    }

    @Test
    public void singleTag_returnsSingletonList() {
        List<String> tags = NoteEditDialog.parseTags("only");
        assertEquals("タグ 1 個のとき要素数 1 になるべき", 1, tags.size());
        assertEquals("only", tags.get(0));
    }

    @Test
    public void trailingCommaOrSpace_ignored() {
        List<String> tags = NoteEditDialog.parseTags("a, b, ");
        assertEquals("末尾カンマ/スペースは無視されるべき", 2, tags.size());
    }

    @Test
    public void leadingComma_ignored() {
        List<String> tags = NoteEditDialog.parseTags(",a,b");
        assertEquals("先頭カンマは無視されるべき", 2, tags.size());
        assertTrue("'a' が含まれるべき", tags.contains("a"));
        assertTrue("'b' が含まれるべき", tags.contains("b"));
    }

    @Test
    public void multipleSpacesBetweenTags_treated_asSingleSeparator() {
        List<String> tags = NoteEditDialog.parseTags("x   y");
        assertEquals("複数スペースは単一区切りとして扱われるべき", 2, tags.size());
        assertEquals("x", tags.get(0));
        assertEquals("y", tags.get(1));
    }
}
