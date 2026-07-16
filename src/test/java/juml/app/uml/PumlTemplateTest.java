// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlTemplate} の全テンプレートが正しい PlantUML スケルトンであることを検証する。
 *
 * <p>UML 系 ({@code @startuml}) だけでなく、マインドマップ / ガント / JSON など
 * 独自の {@code @start…/@end…} を持つ図種も網羅するため、開始・終了マーカーは
 * 「型が一致する対」であることを検証する (旧: {@code @startuml} 固定)。</p>
 *
 * <p>さらに、同梱エンジン (Graphviz 無し = Smetana) で全テンプレートが実際に
 * 描画できることを {@link #render_allTemplates_produceNonEmptySvgWithoutGraphviz()} で
 * 保証し、テンプレート追加・編集による描画リグレッションを防ぐ。(headless 安全)</p>
 */
public class PumlTemplateTest {

    private static final Pattern START = Pattern.compile("^@start(\\w+)");
    private static final Pattern END = Pattern.compile("@end(\\w+)\\s*$");

    @Test
    public void body_allTemplates_haveMatchingStartEndMarkers() {
        for (PumlTemplate t : PumlTemplate.values()) {
            String body = t.body();
            Matcher start = START.matcher(body);
            assertTrue(t + " のテンプレートは @start… で始まるべき", start.find());
            Matcher end = END.matcher(body.trim());
            assertTrue(t + " のテンプレートは @end… で終わるべき", end.find());
            assertEquals(t + " の @start… と @end… の型が一致するべき",
                    start.group(1), end.group(1));
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

    @Test
    public void category_allTemplates_haveResolvedName() {
        for (PumlTemplate t : PumlTemplate.values()) {
            String name = t.category().displayName();
            assertFalse(t + " のカテゴリ名が空", name == null || name.isEmpty());
            assertFalse(t + " のカテゴリ名が未解決キーのまま", name.startsWith("template."));
        }
    }

    /**
     * 全テンプレートが同梱エンジン (Graphviz 無し = Smetana) で描画でき、
     * 非空の SVG を生成すること。描画に失敗すると {@code renderSvg} が例外を投げるため、
     * テンプレートの構文リグレッションをここで検出できる。
     */
    @Test
    public void render_allTemplates_produceNonEmptySvgWithoutGraphviz() throws Exception {
        boolean prev = PlantUmlRenderer.isGraphvizAvailable();
        PlantUmlRenderer.setGraphvizAvailable(false);
        try {
            for (PumlTemplate t : PumlTemplate.values()) {
                if (t == PumlTemplate.EMPTY) {
                    continue; // 空図は描画結果も空になり得るため対象外。
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PlantUmlRenderer.renderSvg(t.body(), out);
                assertTrue(t + " は同梱エンジンで非空の SVG を描画できるべき",
                        out.size() > 0);
            }
        } finally {
            PlantUmlRenderer.setGraphvizAvailable(prev);
        }
    }
}
