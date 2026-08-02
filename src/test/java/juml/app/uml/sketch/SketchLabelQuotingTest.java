// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
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

    /** 引用符・バックスラッシュ・両方を含む、実際に打ち得るラベル。 */
    private static final String[] LABELS = {
        "App \"Prod\"", "C:\\path\\to", "say \"hi\" \\ now", "普通のラベル",
    };

    private static void assertRenders(String puml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        assertTrue("描画できること", out.size() > 0);
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
