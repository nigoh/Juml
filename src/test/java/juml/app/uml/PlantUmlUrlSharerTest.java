// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlUrlSharer} の共有 URL 生成 (純ロジック, headless)。
 *
 * <p>PlantUML 標準エンコード (deflate + 独自 base64) の round-trip と URL 形式を固定し、
 * plantuml.com のサーバーで開ける形式であることを保証する。</p>
 */
public class PlantUmlUrlSharerTest {

    private static final String PUML = "@startuml\nclass A\nA --> B : uses\n@enduml\n";

    @Test
    public void buildUrl_startsWithOfficialServerBase() throws Exception {
        String url = PlantUmlUrlSharer.buildUrl(PUML);
        assertTrue("公式サーバーの SVG エンドポイントで始まるはず: " + url,
                url.startsWith(PlantUmlUrlSharer.SERVER_BASE));
        assertTrue("エンコード部が空であってはならない",
                url.length() > PlantUmlUrlSharer.SERVER_BASE.length());
    }

    @Test
    public void buildUrl_encodedPartRoundTripsToOriginalText() throws Exception {
        String url = PlantUmlUrlSharer.buildUrl(PUML);
        String encoded = url.substring(PlantUmlUrlSharer.SERVER_BASE.length());
        // PlantUML の Transcoder は復号時に末尾改行を正規化するため、内容比較は
        // 末尾空白を除いて行う (図の意味は変わらない)。
        assertEquals("エンコード部を復号すると元の PlantUML に一致するはず",
                PUML.stripTrailing(),
                PlantUmlUrlSharer.decodeForTest(encoded).stripTrailing());
    }

    @Test
    public void buildUrl_japaneseTextSurvivesRoundTrip() throws Exception {
        String puml = "@startuml\nclass クラスA\n@enduml\n";
        String url = PlantUmlUrlSharer.buildUrl(puml);
        String encoded = url.substring(PlantUmlUrlSharer.SERVER_BASE.length());
        assertEquals(puml.stripTrailing(),
                PlantUmlUrlSharer.decodeForTest(encoded).stripTrailing());
    }
}
