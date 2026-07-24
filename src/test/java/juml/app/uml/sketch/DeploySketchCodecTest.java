// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DeploySketchCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless)。
 */
public class DeploySketchCodecTest {

    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "node \"App Server\" as srv",
            "artifact webapp",
            "database \"PostgreSQL\" as db",
            "cloud CDN",
            "srv --> db : JDBC",
            "CDN --> srv",
            "@enduml",
            "");

    @Test
    public void parse_readsNodesKindsLabelsLinks() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(4, r.model.getNodes().size());
        assertEquals(2, r.model.getLinks().size());

        assertEquals(DeployNode.Kind.NODE, r.model.findNode("srv").getKind());
        assertEquals("App Server", r.model.findNode("srv").getLabel());
        assertEquals(DeployNode.Kind.ARTIFACT, r.model.findNode("webapp").getKind());
        assertEquals(DeployNode.Kind.DATABASE, r.model.findNode("db").getKind());
        assertEquals("PostgreSQL", r.model.findNode("db").getLabel());
        assertEquals(DeployNode.Kind.CLOUD, r.model.findNode("CDN").getKind());

        DeployLink jdbc = r.model.getLinks().get(0);
        assertEquals("srv", jdbc.getFrom());
        assertEquals("db", jdbc.getTo());
        assertEquals(DeployLink.Kind.ARROW, jdbc.getKind());
        assertEquals("JDBC", jdbc.getLabel());
    }

    @Test
    public void parse_allEightKinds_areSupported() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml", "node N", "artifact A", "database D", "cloud C",
                "component Cmp", "rectangle R", "folder F", "frame Fr", "@enduml", ""));
        assertTrue("8 種すべて対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(DeployNode.Kind.COMPONENT, r.model.findNode("Cmp").getKind());
        assertEquals(DeployNode.Kind.RECTANGLE, r.model.findNode("R").getKind());
        assertEquals(DeployNode.Kind.FOLDER, r.model.findNode("F").getKind());
        assertEquals(DeployNode.Kind.FRAME, r.model.findNode("Fr").getKind());
    }

    @Test
    public void parse_nestedContainer_isSupportedAndBuildsHierarchy() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml", "node \"Web Server\" {", "  artifact app.war", "}", "@enduml", ""));
        assertTrue("入れ子コンテナは対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals("トップレベルはコンテナ 1 個のみのはず", 1, r.model.getNodes().size());
        DeployNode container = r.model.getNodes().get(0);
        assertTrue(container.isContainer());
        assertEquals("Web Server", container.getLabel());
        assertEquals(1, container.getChildren().size());
        DeployNode child = container.getChildren().get(0);
        assertEquals(DeployNode.Kind.ARTIFACT, child.getKind());
        assertEquals("app.war", child.getLabel());
        assertSame(container, child.getParent());
        // 入れ子でも id はモデル全体で一意に探索できる。
        assertNotNull(r.model.findNode(container.getId()));
        assertNotNull(r.model.findNode(child.getId()));
    }

    @Test
    public void parse_explicitAliasNestedContainer_keepsIdAndMultipleChildren() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node \"App Box\" as box {",
                "  component c1",
                "  artifact \"Bundle\" as a1",
                "}",
                "@enduml", ""));
        assertTrue("明示エイリアス付き入れ子も対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        DeployNode box = r.model.findNode("box");
        assertNotNull(box);
        assertEquals("App Box", box.getLabel());
        assertEquals(2, box.getChildren().size());
        assertEquals(DeployNode.Kind.COMPONENT, r.model.findNode("c1").getKind());
        assertEquals(DeployNode.Kind.ARTIFACT, r.model.findNode("a1").getKind());
        assertEquals("Bundle", r.model.findNode("a1").getLabel());
    }

    @Test
    public void parse_deeplyNestedContainers_supportsMultipleLevels() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node Outer {",
                "  node Inner {",
                "    artifact Leaf",
                "  }",
                "}",
                "@enduml", ""));
        assertTrue("多段の入れ子も対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        DeployNode outer = r.model.findNode("Outer");
        DeployNode inner = r.model.findNode("Inner");
        DeployNode leaf = r.model.findNode("Leaf");
        assertNotNull(outer);
        assertNotNull(inner);
        assertNotNull(leaf);
        assertSame(inner, outer.getChildren().get(0));
        assertSame(leaf, inner.getChildren().get(0));
        assertSame(outer, inner.getParent());
        assertSame(inner, leaf.getParent());
    }

    @Test
    public void parse_relationWithQuotedAnonymousEndpoint_resolvesToSameNode() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "cloud Internet",
                "node \"Web Server\" {",
                "  artifact app.war",
                "}",
                "Internet --> \"Web Server\"",
                "@enduml", ""));
        assertTrue("引用符ラベル参照のリンクも対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(1, r.model.getLinks().size());
        DeployLink link = r.model.getLinks().get(0);
        assertEquals("Internet", link.getFrom());
        DeployNode webServer = r.model.getNodes().stream()
                .filter(n -> "Web Server".equals(n.getLabel())).findFirst().orElseThrow();
        assertEquals(webServer.getId(), link.getTo());
    }

    @Test
    public void parse_unknownKeywordBlock_locksButBalancesBraces() {
        // package は未対応キーワードなのでブロックごとロックするが、閉じ括弧の深さ
        // 追跡は保ち、ブロックの外にある宣言は引き続きモデル化できる。
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node A {",
                "  package \"P\" {",
                "    artifact z",
                "  }",
                "  artifact w",
                "}",
                "node B",
                "@enduml", ""));
        assertFalse("package は未対応のはず", r.isFullySupported());
        DeployNode a = r.model.findNode("A");
        assertNotNull("ブロック閉じの深さ追跡によりコンテナ A 自体は認識されるはず", a);
        assertNotNull("A の未対応ブロックの外側にある w は認識されるはず", r.model.findNode("w"));
        assertNull("未対応ブロック内の z はモデル化されないはず", r.model.findNode("z"));
        assertNotNull("B は影響を受けず認識されるはず", r.model.findNode("B"));
    }

    @Test
    public void parse_strayClosingBrace_isReportedNotEdited() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml", "node A", "}", "@enduml", ""));
        assertFalse("対応する開きの無い閉じ括弧は未対応のはず", r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("}"));
    }

    @Test
    public void roundTrip_deploymentTemplate_reachesFixedPointAndRendersValidSvg() throws IOException {
        String templateText = PumlTemplate.DEPLOYMENT.body();
        DeploySketchCodec.ParseResult first = DeploySketchCodec.parse(templateText);
        assertTrue("既定の配置図テンプレートは入れ子込みで対応構文のはず: " + first.unsupportedLines,
                first.isFullySupported());
        String gen1 = DeploySketchCodec.toPuml(first.model);
        DeploySketchCodec.ParseResult second = DeploySketchCodec.parse(gen1);
        assertTrue(second.isFullySupported());
        String gen2 = DeploySketchCodec.toPuml(second.model);
        assertEquals("2 回目以降の再生成はテンプレートでも固定点になるはず", gen1, gen2);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(gen1, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML が構文エラーを報告した:\n" + gen1, svg.contains("Syntax Error"));
        assertTrue("SVG が生成されるはず", svg.contains("<svg"));
    }

    @Test
    public void model_removeContainerNode_cascadesToChildrenAndLinks() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node Outer {",
                "  artifact Inner",
                "}",
                "node Other",
                "Inner --> Other",
                "@enduml", ""));
        assertTrue(r.isFullySupported());
        DeployNode outer = r.model.findNode("Outer");
        r.model.removeNode(outer);
        assertNull("コンテナを消せば子も消えるはず", r.model.findNode("Inner"));
        assertNull(r.model.findNode("Outer"));
        assertTrue("子に触れていたリンクも消えるはず", r.model.getLinks().isEmpty());
        assertNotNull("兄弟ノードは残るはず", r.model.findNode("Other"));
    }

    @Test
    public void parse_dependencyAndUndirectedLink() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml", "node A", "node B", "A ..> B", "A -- B", "@enduml", ""));
        assertTrue(r.isFullySupported());
        assertEquals(DeployLink.Kind.DEPENDENCY, r.model.getLinks().get(0).getKind());
        assertEquals(DeployLink.Kind.LINK, r.model.getLinks().get(1).getKind());
    }

    @Test
    public void parse_selfLink_isSupported() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml", "node srv", "srv --> srv : retry", "@enduml", ""));
        assertTrue(r.isFullySupported());
        assertEquals(1, r.model.getLinks().size());
        assertEquals("srv", r.model.getLinks().get(0).getFrom());
        assertEquals("srv", r.model.getLinks().get(0).getTo());
    }

    @Test
    public void parse_undeclaredEndpoint_isImpliedAsNode() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(
                "@startuml\nA --> B\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertNotNull(r.model.findNode("A"));
        assertEquals(DeployNode.Kind.NODE, r.model.findNode("A").getKind());
    }

    @Test
    public void roundTrip_reachesFixedPointAndPreservesMeaning() {
        DeploySketchCodec.ParseResult first = DeploySketchCodec.parse(SAMPLE);
        String regenerated = DeploySketchCodec.toPuml(first.model);
        DeploySketchCodec.ParseResult second = DeploySketchCodec.parse(regenerated);
        assertTrue(second.isFullySupported());
        assertEquals("2 回目以降の再生成は固定点になるはず",
                regenerated, DeploySketchCodec.toPuml(second.model));
        assertTrue(regenerated.contains("node \"App Server\" as srv"));
        assertTrue(regenerated.contains("database \"PostgreSQL\" as db"));
        assertTrue(regenerated.contains("srv --> db : JDBC"));
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        assertEquals("@startuml\n@enduml\n",
                DeploySketchCodec.toPuml(new DeploySketchModel()));
    }

    @Test
    public void model_renameNode_updatesLinkEndpoints() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(SAMPLE);
        r.model.renameNode(r.model.findNode("srv"), "appServer");
        String puml = DeploySketchCodec.toPuml(r.model);
        assertTrue(puml.contains("appServer --> db : JDBC"));
        assertTrue(puml.contains("CDN --> appServer"));
    }

    /** 生成した配置図 PlantUML が構文エラー無しで SVG になることを確認する。 */
    @Test
    public void generatedPuml_rendersValidSvg() throws IOException {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(SAMPLE);
        String puml = DeploySketchCodec.toPuml(r.model);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML が構文エラーを報告した:\n" + puml, svg.contains("Syntax Error"));
        assertTrue("SVG が生成されるはず", svg.contains("<svg"));
    }
}
