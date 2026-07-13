// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AndroidProjectAnalysis} の locale 対応文字列解決
 * ({@link AndroidProjectAnalysis#resolveString(String, String)} /
 * {@link AndroidProjectAnalysis#availableStringLocales()}) を検証する。
 *
 * <p>実寸/画面図で「文言を選んだ言語で表示する」機能の土台となるロジックのテスト。</p>
 */
public class AndroidProjectAnalysisLocaleTest {

    /** qualifier 付きの strings.xml を 1 つ作る小ヘルパ。 */
    private static AndroidStringResources strings(String qualifier, String... kv) {
        AndroidStringResources sr = new AndroidStringResources();
        sr.setConfigQualifier(qualifier);
        sr.setFileName("strings.xml");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            sr.getStrings().put(kv[i], kv[i + 1]);
        }
        return sr;
    }

    private static AndroidProjectAnalysis analysisWith(AndroidStringResources... files) {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        a.getStringResourcesByModule().put(":app", java.util.Arrays.asList(files));
        return a;
    }

    @Test
    public void resolveString_withLocale_prefersRequestedLanguage() {
        AndroidProjectAnalysis a = analysisWith(
                strings("", "greeting", "Hello"),
                strings("ja", "greeting", "こんにちは"),
                strings("fr", "greeting", "Bonjour"));

        assertEquals("ja を選べば日本語の文言",
                "こんにちは", a.resolveString("@string/greeting", "ja"));
        assertEquals("fr を選べばフランス語",
                "Bonjour", a.resolveString("@string/greeting", "fr"));
    }

    @Test
    public void resolveString_withNullOrEmptyLocale_usesDefaultLocale() {
        AndroidProjectAnalysis a = analysisWith(
                strings("", "greeting", "Hello"),
                strings("ja", "greeting", "こんにちは"));

        assertEquals("null はデフォルト locale",
                "Hello", a.resolveString("@string/greeting", null));
        assertEquals("空文字はデフォルト locale",
                "Hello", a.resolveString("@string/greeting", ""));
    }

    @Test
    public void resolveString_missingInRequestedLocale_fallsBack() {
        // ja には無いキー → デフォルト優先の従来解決へフォールバックする。
        AndroidProjectAnalysis a = analysisWith(
                strings("", "only_default", "DefaultText"),
                strings("ja", "greeting", "こんにちは"));

        assertEquals("ja に無いキーはデフォルトへ",
                "DefaultText", a.resolveString("@string/only_default", "ja"));
        assertNull("存在しないキーは null",
                a.resolveString("@string/nope", "ja"));
    }

    @Test
    public void availableStringLocales_listsDefaultFirstThenSortedQualifiers() {
        AndroidProjectAnalysis a = analysisWith(
                strings("fr", "greeting", "Bonjour"),
                strings("", "greeting", "Hello"),
                strings("ja", "greeting", "こんにちは"));

        List<String> locales = a.availableStringLocales();
        assertEquals("先頭はデフォルト (空文字)",
                "", locales.get(0));
        // 以降は qualifier 昇順。
        assertEquals(java.util.Arrays.asList("", "fr", "ja"), locales);
    }

    @Test
    public void availableStringLocales_emptyWhenNoStrings() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        assertTrue("文字列リソースが無いなら空",
                a.availableStringLocales().isEmpty());
    }
}
