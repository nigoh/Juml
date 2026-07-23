// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ObjectSketchCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless)。
 */
public class ObjectSketchCodecTest {

    /** タスクが示す代表的なコロン形式のオブジェクト図。 */
    private static final String SAMPLE = String.join("\n",
            "@startuml",
            "object User",
            "User : name = \"Alice\"",
            "User : age = 30",
            "object Post",
            "User --> Post : owns",
            "@enduml",
            "");

    @Test
    public void parse_readsObjectsAttributesAndLinks() {
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(SAMPLE);
        assertTrue("全行が対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getObjects().size());
        assertEquals(1, r.model.getLinks().size());

        ObjectInstance user = r.model.findObject("User");
        assertNotNull(user);
        assertEquals(2, user.getAttributes().size());
        assertEquals("name = \"Alice\"", user.getAttributes().get(0));
        assertEquals("age = 30", user.getAttributes().get(1));

        ObjectLink link = r.model.getLinks().get(0);
        assertEquals("User", link.getLeft());
        assertEquals("Post", link.getRight());
        assertEquals(ObjectLink.Kind.ARROW, link.getKind());
        assertEquals("owns", link.getLabel());
    }

    @Test
    public void parse_blockFormTemplate_isSupportedAndReadsAttributes() {
        // 同梱テンプレート (PumlTemplate.OBJECT) はブロック形式 object X { name = value }。
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(PumlTemplate.OBJECT.body());
        assertTrue("ブロック形式も対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals(2, r.model.getObjects().size());
        assertEquals(1, r.model.getLinks().size());
        ObjectInstance user = r.model.findObject("User");
        assertNotNull(user);
        assertEquals(2, user.getAttributes().size());
        assertEquals("id = 42", user.getAttributes().get(0));
        assertEquals("name = \"Ada\"", user.getAttributes().get(1));
        assertEquals("places", r.model.getLinks().get(0).getLabel());
    }

    @Test
    public void parse_stereotype_isCapturedAndPreserved() {
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(
                "@startuml\nobject User <<entity>>\n@enduml\n");
        assertTrue(r.isFullySupported());
        assertEquals("entity", r.model.findObject("User").getStereotype());
        assertTrue(ObjectSketchCodec.toPuml(r.model).contains("object User <<entity>>"));
    }

    @Test
    public void parse_generalComment_isReportedNotDropped() {
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(
                "@startuml\n' keep this\nobject User\n@enduml\n");
        assertFalse(r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("' keep this"));
    }

    @Test
    public void roundTrip_reachesFixedPointAndNormalizesToColonForm() {
        ObjectSketchCodec.ParseResult first = ObjectSketchCodec.parse(PumlTemplate.OBJECT.body());
        String regenerated = ObjectSketchCodec.toPuml(first.model);
        ObjectSketchCodec.ParseResult second = ObjectSketchCodec.parse(regenerated);
        assertTrue(second.isFullySupported());
        assertEquals("2 回目以降の再生成は固定点になるはず",
                regenerated, ObjectSketchCodec.toPuml(second.model));
        // ブロック形式はコロン形式へ正規化される。
        assertTrue(regenerated, regenerated.contains("User : id = 42"));
        assertTrue(regenerated, regenerated.contains("User --> Order : places"));
    }

    @Test
    public void roundTrip_colonSample_preservesMeaning() {
        ObjectSketchCodec.ParseResult first = ObjectSketchCodec.parse(SAMPLE);
        String regenerated = ObjectSketchCodec.toPuml(first.model);
        ObjectSketchCodec.ParseResult second = ObjectSketchCodec.parse(regenerated);
        assertEquals(2, second.model.getObjects().size());
        assertEquals(1, second.model.getLinks().size());
        assertEquals(2, second.model.findObject("User").getAttributes().size());
        assertEquals(regenerated, ObjectSketchCodec.toPuml(second.model));
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        assertEquals("@startuml\n@enduml\n",
                ObjectSketchCodec.toPuml(new ObjectSketchModel()));
    }

    @Test
    public void model_renameObject_updatesLinkEndpoints() {
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(SAMPLE);
        r.model.renameObject(r.model.findObject("User"), "Customer");
        String puml = ObjectSketchCodec.toPuml(r.model);
        assertTrue(puml, puml.contains("Customer --> Post : owns"));
        assertTrue(puml, puml.contains("Customer : name = \"Alice\""));
        assertFalse(puml, puml.contains("object User"));
    }

    @Test
    public void generatedPuml_rendersAsValidSvg() throws Exception {
        // GUI デザイナーが生成する PlantUML が同梱レンダラで構文エラーなく SVG になること。
        String puml = ObjectSketchCodec.toPuml(ObjectSketchCodec.parse(SAMPLE).model);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(puml, bos);
        String svg = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        assertTrue("rendered SVG expected", bos.size() > 0);
        assertFalse(svg, svg.contains("Syntax Error"));
    }
}
