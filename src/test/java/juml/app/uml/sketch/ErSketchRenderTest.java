// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ER デザイナーが生成する PlantUML が同梱エンジン (Graphviz 無し = Smetana) で
 * 有効な SVG に描画できることを検証する (headless 安全)。描画に失敗すると
 * {@link PlantUmlRenderer#renderSvg} が例外を投げるため、生成テキストの構文
 * リグレッションをここで検出できる。
 */
public class ErSketchRenderTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "hide circle",
            "entity \"User\" as e1 {",
            "  * id : int",
            "  --",
            "  name : varchar",
            "}",
            "entity \"Post\" as e2 {",
            "  * id : int",
            "  --",
            "  user_id : int",
            "}",
            "e1 ||--o{ e2 : has",
            "@enduml",
            "");

    @Test
    public void generatedPuml_rendersValidSvgWithoutGraphviz() throws Exception {
        // モデル往復後の生成テキストを描画対象にする (デザイナー出力そのものを検証)。
        String generated = ErSketchCodec.toPuml(ErSketchCodec.parse(SAMPLE).model);
        boolean prev = PlantUmlRenderer.isGraphvizAvailable();
        PlantUmlRenderer.setGraphvizAvailable(false);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PlantUmlRenderer.renderSvg(generated, out);
            String svg = out.toString(StandardCharsets.UTF_8);
            assertTrue("非空の SVG を描画できるべき", out.size() > 0);
            assertTrue("SVG 要素を含むべき", svg.contains("<svg"));
            assertFalse("PlantUML のエラー SVG であってはならない",
                    svg.contains("An error has occured"));
        } finally {
            PlantUmlRenderer.setGraphvizAvailable(prev);
        }
    }
}
