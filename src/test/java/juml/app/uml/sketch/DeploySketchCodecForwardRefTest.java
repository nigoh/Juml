// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DeploySketchCodec#parse(String)} が、宣言より前に書かれたリンク端点 (前方参照) を
 * 幽霊ノードの二重生成や入れ子子ノードのトップレベル残留なしに解決できることを検証する
 * (bug-hunt round 1: Deploy codec: 引用符ラベル端点の前方参照で幽霊ノードが出ない)。
 *
 * <p>修正前は 1 パス目でリンクを見つけ次第すぐ端点解決していたため、まだ宣言されていない
 * 引用符ラベル端点は暗黙のノードとして先に生成され、後から来る本来の宣言と同じ表示名の
 * 別ノード (id 違い) が重複してしまっていた。入れ子子ノードの前方参照でも同様に、暗黙生成
 * された同名トップレベルノードが後の宣言に横取りされ、宣言側の子ノードがコンテナへ入らず
 * トップレベルに取り残されていた。{@link DeploySketchCodec} は全宣言確定後の 2 パス目で
 * リンクを解決するよう修正済みで、このテストは純関数のみを対象とするため headless でも
 * 常時実行する (GUI/Assume 不要)。</p>
 */
public class DeploySketchCodecForwardRefTest {

    @Test
    public void parse_relationForwardReferencesQuotedLabelBeforeDeclaration_noGhostNode() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(
                "@startuml\napp --> \"Load Balancer\"\nnode \"Load Balancer\" as lb\n@enduml\n");
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());

        long lbCount = r.model.getNodes().stream()
                .filter(n -> "Load Balancer".equals(n.getLabel())).count();
        assertEquals("Load Balancer は 1 個だけのはず (幽霊ノードが重複生成されない)",
                1, lbCount);
        DeployNode lb = r.model.findNode("lb");
        assertNotNull("宣言どおり id=lb のノードが存在するはず", lb);
        assertEquals("Load Balancer", lb.getLabel());

        assertEquals(1, r.model.getLinks().size());
        DeployLink link = r.model.getLinks().get(0);
        assertEquals("app", link.getFrom());
        assertEquals("リンクの to は宣言済み lb ノードの id を指すはず", "lb", link.getTo());
    }

    @Test
    public void parse_relationForwardReferencesNestedChildId_childStaysNestedAndLinkResolves() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node A",
                "A --> child",
                "node Box {",
                "  artifact child",
                "}",
                "@enduml", ""));
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());

        assertEquals("トップレベルは A と Box の 2 個だけのはず (child がトップレベルへ漏れ出ない)",
                2, r.model.getNodes().size());
        DeployNode box = r.model.findNode("Box");
        assertNotNull(box);
        assertEquals(1, box.getChildren().size());
        DeployNode child = box.getChildren().get(0);
        assertEquals("child", child.getId());
        assertSame("child は Box の子として保持されるはず", box, child.getParent());

        assertEquals(1, r.model.getLinks().size());
        DeployLink link = r.model.getLinks().get(0);
        assertEquals("A", link.getFrom());
        assertEquals("リンクは入れ子の child を指すはず", "child", link.getTo());
    }

    @Test
    public void roundTrip_forwardReferencedQuotedLabel_reachesFixedPointAndRendersValidSvg()
            throws IOException {
        String text = "@startuml\napp --> \"Load Balancer\"\nnode \"Load Balancer\" as lb\n@enduml\n";
        DeploySketchCodec.ParseResult first = DeploySketchCodec.parse(text);
        assertTrue(first.isFullySupported());
        String gen1 = DeploySketchCodec.toPuml(first.model);
        DeploySketchCodec.ParseResult second = DeploySketchCodec.parse(gen1);
        assertTrue(second.isFullySupported());
        String gen2 = DeploySketchCodec.toPuml(second.model);
        assertEquals("2 回目以降の再生成は固定点になるはず", gen1, gen2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(gen1, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML が構文エラーを報告した:\n" + gen1, svg.contains("Syntax Error"));
        assertTrue("SVG が生成されるはず", svg.contains("<svg"));
    }

    @Test
    public void roundTrip_forwardReferencedNestedChild_reachesFixedPointAndRendersValidSvg()
            throws IOException {
        String text = String.join("\n",
                "@startuml",
                "node A",
                "A --> child",
                "node Box {",
                "  artifact child",
                "}",
                "@enduml", "");
        DeploySketchCodec.ParseResult first = DeploySketchCodec.parse(text);
        assertTrue(first.isFullySupported());
        String gen1 = DeploySketchCodec.toPuml(first.model);
        DeploySketchCodec.ParseResult second = DeploySketchCodec.parse(gen1);
        assertTrue(second.isFullySupported());
        String gen2 = DeploySketchCodec.toPuml(second.model);
        assertEquals("2 回目以降の再生成は固定点になるはず", gen1, gen2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(gen1, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML が構文エラーを報告した:\n" + gen1, svg.contains("Syntax Error"));
        assertTrue("SVG が生成されるはず", svg.contains("<svg"));
    }
}
