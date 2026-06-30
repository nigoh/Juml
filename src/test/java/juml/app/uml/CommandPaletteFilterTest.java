// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link CommandPalette} のあいまい (subsequence) フィルタロジックのテスト。
 *
 * <p>コマンドパレットの絞り込みは VS Code 風の subsequence マッチ ("oppr" → "Open Project")。
 * この純粋関数は {@code private static boolean matches(String, String)} で公開 seam が無いため、
 * 同一パッケージからリフレクションで直接叩いて検証する (状態を持たない安定した関数のため
 * フィールド参照のような脆さはない)。クエリ絞り込みの回帰を守るのが目的。</p>
 */
public class CommandPaletteFilterTest {

    private static Method matches;

    @BeforeClass
    public static void resolveMethod() throws Exception {
        matches = CommandPalette.class.getDeclaredMethod("matches", String.class, String.class);
        matches.setAccessible(true);
    }

    private static boolean match(String haystack, String query) throws Exception {
        return (Boolean) matches.invoke(null, haystack, query);
    }

    /** 部分文字列は subsequence の特殊形として一致する。 */
    @Test
    public void substringMatches() throws Exception {
        assertTrue("'save' は 'save as' に含まれる", match("save as", "save"));
        assertTrue("'as' は 'save as' に含まれる", match("save as", "as"));
    }

    /** 飛び飛びでも順序が保たれていれば一致 (VS Code 風 fuzzy)。 */
    @Test
    public void subsequenceMatchesAcrossWords() throws Exception {
        assertTrue("'oppr' は 'open project' に順序を保って現れる",
                match("open project", "oppr"));
        assertTrue("'opj' も順序一致", match("open project", "opj"));
    }

    /** 空クエリは常に一致 (絞り込み解除)。 */
    @Test
    public void emptyQueryMatchesEverything() throws Exception {
        assertTrue(match("anything here", ""));
    }

    /** 含まれない文字があれば不一致。 */
    @Test
    public void missingCharactersDoNotMatch() throws Exception {
        assertFalse("'xyz' は 'open project' に無い", match("open project", "xyz"));
    }

    /** 順序が逆なら不一致 (subsequence は順序依存)。 */
    @Test
    public void orderMattersForSubsequence() throws Exception {
        assertFalse("'ba' は 'abc' に順序通りには現れない", match("abc", "ba"));
    }

    /** クエリが対象より長ければ不一致。 */
    @Test
    public void queryLongerThanHaystackDoesNotMatch() throws Exception {
        assertFalse(match("ab", "abc"));
    }
}
