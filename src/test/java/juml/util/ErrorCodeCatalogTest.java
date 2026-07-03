// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * エラーカタログ ({@link ErrorCode}) の整合性を CI で担保するテスト。
 *
 * <p>ID の重複・形式逸脱、日英リソース ({@code errcode.*}) の欠落を検出する。
 * コードを追加したのにメッセージを足し忘れた、といった乖離をここで落とす。</p>
 */
public class ErrorCodeCatalogTest {

    /** 合意済みの ID 形式: 領域プレフィックス + 3 桁連番。 */
    private static final Pattern ID_FORMAT =
            Pattern.compile("^(UML-R|UML-E|DIAG-|PRJ-|ANA-|CACHE-|EXP-|NOTE-|CFG-|SYS-)\\d{3}$");

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("messages", locale,
                ResourceBundle.Control.getNoFallbackControl(
                        ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    @Test
    public void testIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (ErrorCode c : ErrorCode.values()) {
            if (!c.hasId()) {
                continue;
            }
            assertTrue("duplicate id: " + c.getId(), seen.add(c.getId()));
        }
    }

    @Test
    public void testIdsFollowAgreedFormat() {
        for (ErrorCode c : ErrorCode.values()) {
            if (!c.hasId()) {
                continue;
            }
            assertTrue("id format violation: " + c.getId(),
                    ID_FORMAT.matcher(c.getId()).matches());
            assertTrue("id must start with its area prefix: " + c.getId(),
                    c.getId().startsWith(c.getArea().getPrefix()));
        }
    }

    @Test
    public void testEveryCodeHasSummaryAndRemedyInBothLanguages() {
        for (Locale locale : new Locale[] {Locale.JAPANESE, Locale.ENGLISH}) {
            ResourceBundle b = bundle(locale);
            for (ErrorCode c : ErrorCode.values()) {
                if (!c.hasId()) {
                    continue;
                }
                for (String kind : new String[] {"summary", "remedy"}) {
                    String key = "errcode." + c.getId() + "." + kind;
                    try {
                        String v = b.getString(key);
                        assertFalse("empty " + key + " (" + locale + ")", v.trim().isEmpty());
                    } catch (MissingResourceException e) {
                        fail("missing " + key + " in messages (" + locale + ")");
                    }
                }
            }
        }
    }

    @Test
    public void testEveryAreaHasDisplayName() {
        for (Locale locale : new Locale[] {Locale.JAPANESE, Locale.ENGLISH}) {
            ResourceBundle b = bundle(locale);
            for (ErrorCode.Area a : ErrorCode.Area.values()) {
                String key = "errcode.area." + a.getPrefix();
                try {
                    assertFalse("empty " + key, b.getString(key).trim().isEmpty());
                } catch (MissingResourceException e) {
                    fail("missing " + key + " in messages (" + locale + ")");
                }
            }
        }
    }

    @Test
    public void testFromIdRoundTrip() {
        for (ErrorCode c : ErrorCode.values()) {
            if (!c.hasId()) {
                continue;
            }
            assertSame(c, ErrorCode.fromId(c.getId()));
            assertSame("fromId should be case-insensitive: " + c.getId(),
                    c, ErrorCode.fromId(c.getId().toLowerCase(Locale.ROOT)));
        }
        assertNull(ErrorCode.fromId(null));
        assertNull(ErrorCode.fromId(""));
        assertNull(ErrorCode.fromId("NO-SUCH-999"));
    }

    @Test
    public void testNoneSentinel() {
        assertFalse(ErrorCode.NONE.hasId());
        assertEquals("", ErrorCode.NONE.getId());
        assertEquals("[-]", ErrorCode.NONE.tag());
        assertEquals("", ErrorCode.NONE.summary());
        assertEquals("", ErrorCode.NONE.remedy());
    }

    @Test
    public void testTagFormat() {
        assertEquals("[UML-R001]", ErrorCode.UML_R001.tag());
    }

    @Test
    public void testJumlExceptionCarriesCode() {
        JumlException ex = new JumlException(ErrorCode.PRJ_001, "boom");
        assertSame(ErrorCode.PRJ_001, ex.getErrorCode());
        assertSame(ErrorCode.PRJ_001, JumlException.codeOf(ex, ErrorCode.NONE));
        assertSame(ErrorCode.UML_R007,
                JumlException.codeOf(new RuntimeException("x"), ErrorCode.UML_R007));
        // code=null は NONE に正規化される
        assertSame(ErrorCode.NONE, new JumlException(null, "x").getErrorCode());
    }
}
