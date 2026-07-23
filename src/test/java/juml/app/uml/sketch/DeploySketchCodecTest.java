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
    public void parse_nestedContainer_isReportedNotEdited() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml", "node \"Web Server\" {", "  artifact app.war", "}", "@enduml", ""));
        assertFalse("入れ子コンテナは未対応として保護されるはず", r.isFullySupported());
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
