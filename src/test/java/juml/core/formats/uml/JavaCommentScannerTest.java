// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * JavaCommentScanner#cleanText / stripInlineTags のユニットテスト。
 */
public class JavaCommentScannerTest {

    private static JavaCommentScanner.Comment javadoc(String body) {
        return new JavaCommentScanner.Comment(0, 0, 1,
                JavaCommentScanner.Kind.JAVADOC, body);
    }

    @Test
    public void stripsSingleLineInlineTags() {
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * Uses {@code foo} and {@link Bar} here.\n"));
        assertEquals("Uses foo and Bar here.", cleaned);
    }

    @Test
    public void resolvesInlineTagSpanningTwoLines() {
        // {@link が行末で開き、次行で閉じる JavaDoc でも残骸 ({@link) を残さない
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * Called from the calling {@link\n"
                + " * Activity} now.\n"));
        assertFalse("must not leave {@ residue", cleaned.contains("{@"));
        assertTrue(cleaned.contains("Activity"));
    }

    @Test
    public void linkRefWithMethodSignatureIsNotMisSplit() {
        // {@link Foo#bar(int, String[])} のように参照内に空白/カンマがあっても壊さない
        String cleaned = JavaCommentScanner.cleanText(javadoc(
                " * See {@link Foo#bar(int, String[])} for details.\n"));
        assertFalse(cleaned.contains("{@"));
        assertTrue(cleaned, cleaned.contains("bar(int, String[])"));
        assertTrue(cleaned, cleaned.contains("for details."));
    }
}
