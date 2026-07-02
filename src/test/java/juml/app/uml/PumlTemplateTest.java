// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlTemplate} の全テンプレートが正しい PlantUML スケルトンであることを検証する。
 * (headless で動く純粋なデータテスト)
 */
public class PumlTemplateTest {

    @Test
    public void body_allTemplates_startAndEndWithUmlMarkers() {
        for (PumlTemplate t : PumlTemplate.values()) {
            String body = t.body();
            assertTrue(t + " のテンプレートは @startuml で始まるべき",
                    body.startsWith("@startuml"));
            assertTrue(t + " のテンプレートは @enduml で終わるべき",
                    body.trim().endsWith("@enduml"));
        }
    }

    @Test
    public void displayName_allTemplates_notEmpty() {
        for (PumlTemplate t : PumlTemplate.values()) {
            String name = t.displayName();
            assertFalse(t + " の表示名が空", name == null || name.isEmpty());
            // i18n キー未定義のときは ResourceBundle が例外 → ここまで来れば解決済み。
            assertFalse(t + " の表示名が未解決キーのまま", name.startsWith("template."));
        }
    }
}
