// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * {@link ExportController#suggestBaseName(String)} のファイル名整形 (純関数, headless)。
 */
public class ExportSuggestNameTest {

    @Test
    public void suggestBaseName_replacesUnsafeChars() {
        assertEquals("Foo_Bar", ExportController.suggestBaseName("Foo/Bar"));
        assertEquals("a_b_c", ExportController.suggestBaseName("a:b*c"));
        assertEquals("Class_seq_", ExportController.suggestBaseName("Class<seq>"));
    }

    @Test
    public void suggestBaseName_collapsesWhitespace() {
        assertEquals("My_Class_(クラス図)", ExportController.suggestBaseName("My Class  (クラス図)"));
    }

    @Test
    public void suggestBaseName_nullOrEmptyReturnsNull() {
        assertNull(ExportController.suggestBaseName(null));
        assertNull(ExportController.suggestBaseName("   "));
    }
}
