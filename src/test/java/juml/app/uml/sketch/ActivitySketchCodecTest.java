// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ActivitySketchCodec} の解析・再生成 (round-trip) を検証する純ロジックテスト。
 */
public class ActivitySketchCodecTest {

    @Test
    public void parse_activityTemplate_isFullySupported() {
        ActivitySketchCodec.ParseResult r =
                ActivitySketchCodec.parse(PumlTemplate.ACTIVITY.body());
        assertTrue("テンプレートの全行がモデル化できるはず: " + r.unsupportedLines,
                r.isFullySupported());
        // start / action / if / stop の 4 ノード。
        assertEquals(4, r.model.getNodes().size());
        assertEquals(ActivityNode.Kind.START, r.model.getNodes().get(0).getKind());
        assertEquals(ActivityNode.Kind.ACTION, r.model.getNodes().get(1).getKind());
        ActivityNode ifNode = r.model.getNodes().get(2);
        assertEquals(ActivityNode.Kind.IF, ifNode.getKind());
        assertEquals("valid?", ifNode.getCondition());
        assertEquals("yes", ifNode.getThenLabel());
        assertEquals("no", ifNode.getElseLabel());
        assertEquals(1, ifNode.getThenBranch().size());
        assertNotNull(ifNode.getElseBranch());
        assertEquals(1, ifNode.getElseBranch().size());
        assertEquals(ActivityNode.Kind.STOP, r.model.getNodes().get(3).getKind());
    }

    @Test
    public void roundTrip_activityTemplate_isLossless() {
        String original = PumlTemplate.ACTIVITY.body();
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(original);
        assertEquals(original, ActivitySketchCodec.toPuml(r.model));
    }

    @Test
    public void roundTrip_nestedIf_isLossless() {
        String puml = "@startuml\n"
                + "start\n"
                + "if (outer?) then (yes)\n"
                + "  if (inner?) then\n"
                + "    :Deep;\n"
                + "  endif\n"
                + "else\n"
                + "  :Other;\n"
                + "endif\n"
                + "stop\n"
                + "@enduml\n";
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(puml);
        assertTrue("入れ子 if もモデル化できるはず: " + r.unsupportedLines,
                r.isFullySupported());
        assertEquals(puml, ActivitySketchCodec.toPuml(r.model));
    }

    @Test
    public void roundTrip_ifWithoutElse_emitsNoElseClause() {
        String puml = "@startuml\nif (ok?) then (yes)\n  :Go;\nendif\n@enduml\n";
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(puml);
        assertTrue(r.isFullySupported());
        assertNull("else 節が無ければ elseBranch は null",
                r.model.getNodes().get(0).getElseBranch());
        assertEquals(puml, ActivitySketchCodec.toPuml(r.model));
    }

    @Test
    public void parse_diagramName_isPreserved() {
        String puml = "@startuml flow\nstart\nstop\n@enduml\n";
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(puml);
        assertEquals("flow", r.model.getDiagramName());
        assertEquals(puml, ActivitySketchCodec.toPuml(r.model));
    }

    @Test
    public void parse_unclosedIf_locksEditing() {
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(
                "@startuml\nif (ok?) then\n  :Go;\n@enduml\n");
        assertFalse("endif の無い if は編集ロック", r.isFullySupported());
    }

    @Test
    public void parse_whileLoop_locksEditing() {
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(
                "@startuml\nstart\nwhile (more?)\n  :Work;\nendwhile\nstop\n@enduml\n");
        assertFalse("while は未対応なので編集ロック", r.isFullySupported());
    }

    @Test
    public void parse_multiLineAction_locksEditing() {
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(
                "@startuml\n:first line\nsecond line;\n@enduml\n");
        assertFalse("複数行アクションは未対応なので編集ロック", r.isFullySupported());
    }

    @Test
    public void parse_comment_locksEditing() {
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(
                "@startuml\n' memo\nstart\nstop\n@enduml\n");
        assertFalse("コメントは再生成で失われるため編集ロック", r.isFullySupported());
    }

    @Test
    public void model_removeIf_dropsWholeBlock() {
        ActivitySketchCodec.ParseResult r =
                ActivitySketchCodec.parse(PumlTemplate.ACTIVITY.body());
        ActivitySketchModel model = r.model;
        model.remove(model.getNodes().get(2)); // if ブロック
        String puml = ActivitySketchCodec.toPuml(model);
        assertFalse(puml.contains("if ("));
        assertFalse(puml.contains(":Process;"));
        assertTrue(puml.contains(":Prepare input;"));
    }

    @Test
    public void model_removeNestedNode_findsBranch() {
        ActivitySketchCodec.ParseResult r =
                ActivitySketchCodec.parse(PumlTemplate.ACTIVITY.body());
        ActivitySketchModel model = r.model;
        ActivityNode inThen = model.getNodes().get(2).getThenBranch().get(0);
        model.remove(inThen);
        assertTrue(model.getNodes().get(2).getThenBranch().isEmpty());
        assertFalse(ActivitySketchCodec.toPuml(model).contains(":Process;"));
    }

    @Test
    public void model_insertAfterAndMove_reorderWithinBranch() {
        ActivitySketchModel model = new ActivitySketchModel();
        ActivityNode first = ActivityNode.action("first");
        model.getNodes().add(first);
        ActivityNode second = ActivityNode.action("second");
        model.insertAfter(first, second);
        assertEquals(2, model.getNodes().size());
        model.move(second, -1);
        assertEquals("second", model.getNodes().get(0).getText());
        // 端を越える移動は何もしない。
        model.move(second, -1);
        assertEquals("second", model.getNodes().get(0).getText());
    }
}
