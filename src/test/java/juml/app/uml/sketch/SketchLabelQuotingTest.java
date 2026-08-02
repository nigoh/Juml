// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 引用符付きラベルに {@code "} / {@code \} が含まれるときの往復を検証する。
 *
 * <p>配置図コーデックは既にエスケープしていたが、使用例図・コンポーネント図・ER 図の
 * コーデックはラベルを無変換で {@code "..."} に埋め込んでいた。ラベルに {@code "} が
 * 入ると宣言行の引用符の対応が崩れ ({@code component "App "Prod"" as c1})、パターンが
 * マッチせずその行は「未対応」に落ちる = <b>GUI 編集がロックされ、往復すると要素ごと
 * 消える</b>。ダイアログ側でラベル文字を制限していないため、日本語の鉤括弧感覚で
 * {@code "} を打つだけで踏める。</p>
 */
public class SketchLabelQuotingTest {

    /** 引用符・バックスラッシュ・{@code as}・末尾 {@code \} を含む、実際に打ち得るラベル。 */
    private static final String[] LABELS = {
        "App \"Prod\"", "C:\\path\\to", "say \"hi\" \\ now", "普通のラベル",
        "App as Prod", "C:\\", "Alpha\\nBeta",
    };

    /** SVG のテキスト要素をすべて連結して返す (描画結果そのものを見るため)。 */
    private static String renderedText(String puml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder t = new StringBuilder();
        int i = 0;
        while ((i = svg.indexOf("<text", i)) >= 0) {
            int gt = svg.indexOf('>', i);
            int close = svg.indexOf("</text>", gt);
            if (gt < 0 || close < 0) {
                break;
            }
            t.append(svg, gt + 1, close).append('\n');
            i = close;
        }
        return t.toString();
    }

    private static void assertRenders(String puml) throws Exception {
        assertTrue("描画できること", !renderedText(puml).isEmpty());
    }

    /** SVG のテキストは XML 数値参照になるため、比較用に主要な実体を戻す。 */
    private static String unescapeXml(String s) {
        return s.replace("&#34;", "\"").replace("&quot;", "\"")
                .replace("&#92;", "\\").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    @Test
    public void labelIsRenderedVerbatim_noBackslashLeaksIntoTheDiagram() throws Exception {
        // 回帰: ラベルを \" へエスケープして書き出していた頃は、図に逆スラッシュが
        // そのまま描画されていた (PlantUML に引用符のエスケープは無い)。
        ComponentSketchModel model = new ComponentSketchModel();
        model.getNodes().add(
                new ComponentNode(ComponentNode.Kind.COMPONENT, "c1", "App \"Prod\"", 0, 0));
        String puml = ComponentSketchCodec.toPuml(model);
        assertFalse("生成テキストに逆スラッシュを入れないこと: " + puml, puml.contains("\\\""));
        String text = unescapeXml(renderedText(puml));
        assertTrue("図に App \"Prod\" と描かれること: " + text, text.contains("App \"Prod\""));
        assertFalse("図に逆スラッシュが出ないこと: " + text, text.contains("\\\""));
    }

    @Test
    public void backslashNewlineInLabelStaysALineBreak() throws Exception {
        // 回帰: unescape が \n を n へ潰していたため、手書きの改行ラベルが 1 行に潰れていた。
        String puml = "@startuml\ncomponent \"Alpha\\nBeta\" as c1\n@enduml\n";
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(puml);
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals("ラベル中の \\n を保全すること",
                "Alpha\\nBeta", r.model.getNodes().get(0).getLabel());
        String text = renderedText(ComponentSketchCodec.toPuml(r.model));
        assertTrue("改行として描画されること: " + text,
                text.contains("Alpha") && text.contains("Beta"));
        assertFalse("1 行に潰れないこと: " + text, text.contains("AlphanBeta"));
    }

    @Test
    public void handWrittenQuotedLabelSurvivesTheDesigner() throws Exception {
        // 回帰: "([^"]*)" では宣言行がマッチせず、この行が未対応に落ちて要素ごと消えていた。
        String puml = "@startuml\ncomponent \"App \"Prod\"\" as c1\ncomponent ok\n@enduml\n";
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(puml);
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals("2 ノードとも残ること", 2, r.model.getNodes().size());
        assertEquals("App \"Prod\"", r.model.getNodes().get(0).getLabel());
    }

    @Test
    public void handWrittenTrailingBackslashLabelSurvivesTheDesigner() throws Exception {
        // 回帰: \\. を許す正規表現は閉じ引用符直前の \ を食べてしまい、行ごと未対応にしていた。
        String puml = "@startuml\ncomponent \"C:\\\" as c1\ncomponent ok\n@enduml\n";
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(puml);
        assertTrue("編集ロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals("2 ノードとも残ること", 2, r.model.getNodes().size());
        assertEquals("C:\\", r.model.getNodes().get(0).getLabel());
    }

    @Test
    public void useCaseLabelWithQuotes_roundTripsAndStaysEditable() throws Exception {
        for (String label : LABELS) {
            UseCaseSketchModel model = new UseCaseSketchModel();
            model.getNodes().add(new UseCaseNode(UseCaseNode.Kind.ACTOR, "a1", label, 0, 0));
            String puml = UseCaseSketchCodec.toPuml(model);
            UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(puml);
            assertTrue("編集ロックされないこと (" + label + "): " + r.unsupportedLines,
                    r.isFullySupported());
            assertEquals("ラベルが往復すること", label, r.model.getNodes().get(0).getLabel());
            assertEquals("2 周目は固定点", puml, UseCaseSketchCodec.toPuml(r.model));
            assertRenders(puml);
        }
    }

    @Test
    public void componentLabelWithQuotes_roundTripsAndStaysEditable() throws Exception {
        for (String label : LABELS) {
            ComponentSketchModel model = new ComponentSketchModel();
            model.getNodes().add(
                    new ComponentNode(ComponentNode.Kind.COMPONENT, "c1", label, 0, 0));
            String puml = ComponentSketchCodec.toPuml(model);
            ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(puml);
            assertTrue("編集ロックされないこと (" + label + "): " + r.unsupportedLines,
                    r.isFullySupported());
            assertEquals("ラベルが往復すること", label, r.model.getNodes().get(0).getLabel());
            assertEquals("2 周目は固定点", puml, ComponentSketchCodec.toPuml(r.model));
            assertRenders(puml);
        }
    }

    @Test
    public void erEntityLabelWithQuotes_roundTripsAndStaysEditable() throws Exception {
        for (String label : LABELS) {
            ErSketchModel model = new ErSketchModel();
            ErSketchModel.Entity e = new ErSketchModel.Entity("e1", label, 0, 0);
            model.getEntities().add(e);
            String puml = ErSketchCodec.toPuml(model);
            ErSketchCodec.ParseResult r = ErSketchCodec.parse(puml);
            assertTrue("編集ロックされないこと (" + label + "): " + r.unsupportedLines,
                    r.isFullySupported());
            assertEquals("ラベルが往復すること", label,
                    r.model.getEntities().get(0).getDisplayName());
            assertEquals("2 周目は固定点", puml, ErSketchCodec.toPuml(r.model));
            assertRenders(puml);
        }
    }

    @Test
    public void deploymentLabelWithQuotes_stillRoundTrips() throws Exception {
        // 共通ヘルパー (SketchLabelText) へ寄せた後も配置図の既存挙動が変わらないこと。
        for (String label : LABELS) {
            DeploySketchModel model = new DeploySketchModel();
            model.getNodes().add(
                    new DeploySketchModel.DeployNode(DeploySketchModel.DeployNode.Kind.NODE,
                            "n1", label, 0, 0));
            String puml = DeploySketchCodec.toPuml(model);
            DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(puml);
            assertTrue("編集ロックされないこと (" + label + "): " + r.unsupportedLines,
                    r.isFullySupported());
            assertEquals(label, r.model.getNodes().get(0).getLabel());
            assertRenders(puml);
        }
    }
}
