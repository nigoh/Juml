// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.PumlTemplate;
import juml.app.uml.sketch.MindmapNode.Side;
import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link MindmapSketchCodec} の PlantUML ⇔ モデル双方向変換を検証する (headless)。
 *
 * <p>最重要は「side 継承の正規化」: 生の {@code *} を持つ子でも祖先の {@code -} / {@code +}
 * 系へ揃えて出力し、記号ファミリ不整合 (実機の {@code error42L}) を避けること。往復固定点と
 * 実描画 (SyntaxError が出ないこと) を、テンプレートと明示 side の 3 階層ネストの両方で確認する。</p>
 */
public class MindmapSketchCodecTest {

    private static MindmapNode child(MindmapSketchModel model, MindmapNode parent,
                                     String text, Side side) {
        MindmapNode n = new MindmapNode(text);
        n.setOwnSide(side);
        model.addChild(parent, n);
        return n;
    }

    // --- 構造の解析 --------------------------------------------------------------

    @Test
    public void parse_template_buildsSingleRootTree() {
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(PumlTemplate.MINDMAP.body());
        assertTrue("テンプレートは対応構文のはず: " + r.unsupportedLines, r.isFullySupported());
        MindmapNode root = r.model.getRoot();
        assertEquals("Project", root.getText());
        assertEquals("ルート直下は Design/Build/Ship の 3 つ", 3, root.getChildren().size());
        MindmapNode design = root.getChildren().get(0);
        assertEquals("Design", design.getText());
        assertEquals("Design 配下は UI/UX の 2 つ", 2, design.getChildren().size());
        assertEquals("全ノード数は 6", 6, r.model.allNodes().size());
    }

    @Test
    public void parse_depthJump_isUnsupported() {
        // 深さ 1 の直後に深さ 3 (中間の深さ 2 を飛ばす) は往復不能。
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(String.join("\n",
                "@startmindmap", "* Root", "*** Skipped", "@endmindmap", ""));
        assertFalse("深さ跳躍は未対応のはず", r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("*** Skipped"));
    }

    @Test
    public void parse_secondRootLine_isUnsupported() {
        // 2 本目の深さ 1 行 (フォレスト) は単一ルート制約で未対応。
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(String.join("\n",
                "@startmindmap", "* First", "* Second", "@endmindmap", ""));
        assertFalse("2 本目のルートは未対応のはず", r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("* Second"));
        assertEquals("First", r.model.getRoot().getText());
    }

    @Test
    public void parse_mixedSymbolFamily_isUnsupported() {
        // 記号混在 (*-*) はファミリ不整合で PlantUML が壊れるため未対応。
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(String.join("\n",
                "@startmindmap", "* Root", "*-* Mixed", "@endmindmap", ""));
        assertFalse("記号混在は未対応のはず", r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("*-* Mixed"));
    }

    @Test
    public void parse_commentAndDecoration_isUnsupported() {
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(String.join("\n",
                "@startmindmap", "* Root", "' a comment", "title My Map", "@endmindmap", ""));
        assertFalse("コメント・装飾は未対応のはず", r.isFullySupported());
        assertTrue(r.unsupportedLines.contains("' a comment"));
        assertTrue(r.unsupportedLines.contains("title My Map"));
    }

    @Test
    public void parse_noSpaceNodeLine_isSupportedAndRoundTripsWithSpace() throws IOException {
        // 記号とテキストの間に空白が無い '**Child' も PlantUML では '** Child' と同一に描画され
        // 有効。編集ロックせず受理し、往復出力は空白付きへ正規化されること (実描画で検証)。
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(
                "@startmindmap\n*Root\n**Child\n@endmindmap\n");
        assertTrue("空白なしノードでもロックされないはず: " + r.unsupportedLines, r.isFullySupported());
        assertEquals("Root", r.model.getRoot().getText());
        assertEquals(1, r.model.getRoot().getChildren().size());
        assertEquals("Child", r.model.getRoot().getChildren().get(0).getText());

        String puml = MindmapSketchCodec.toPuml(r.model);
        assertTrue("出力は空白付きへ正規化: " + puml, puml.contains("\n* Root\n"));
        assertTrue(puml.contains("\n** Child\n"));
        assertValidSvg(puml);
    }

    @Test
    public void parse_symbolsOnlyLine_isUnsupported() {
        // 記号のみ・空白のみ (テキスト無し) の行はモデル化できず未対応。
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(
                "@startmindmap\n* Root\n**   \n@endmindmap\n");
        assertFalse("記号のみの行は未対応のはず", r.isFullySupported());
        assertEquals("Root", r.model.getRoot().getText());
        assertEquals("記号のみ行は子に取り込まない", 0, r.model.getRoot().getChildren().size());
    }

    // --- side 継承の正規化 (最重要) ----------------------------------------------

    @Test
    public void toPuml_autoChildOfLeftParent_normalizesToLeftFamily() {
        // '-' (LEFT) の子に生の '*' (AUTO) を持つモデル。継承しないと出力が '***' になり
        // ファミリ不整合 (error42L) を招く。実効 side 継承で '-' 系へ揃うことを検証する。
        MindmapSketchModel model = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        model.setRoot(root);
        MindmapNode a = child(model, root, "A", Side.LEFT);
        child(model, a, "B", Side.AUTO);

        String puml = MindmapSketchCodec.toPuml(model);
        assertTrue("深さ 2 の A は '--'", puml.contains("\n-- A\n"));
        assertTrue("深さ 3 の B は継承して '---' (LEFT 系へ正規化)", puml.contains("\n--- B\n"));
        assertFalse("生の '*' 系 (***) は出力されないはず: " + puml, puml.contains("*** B"));
    }

    @Test
    public void toPuml_explicitOppositeSideDescendant_normalizesToBranchFamilyAndRendersValidSvg()
            throws IOException {
        // 深いノードに枝と食い違う明示 side (LEFT 枝の子に RIGHT) があっても、出力は枝の
        // 系統 ('-') へ正規化されること。回帰: setSideOfSelected で深いノードを反対側にすると
        // `-- C / +++ D` を生成し、実機 renderSvg で error42L になった。
        MindmapSketchModel model = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        model.setRoot(root);
        MindmapNode c = child(model, root, "C", Side.LEFT);
        child(model, c, "D", Side.RIGHT);

        String puml = MindmapSketchCodec.toPuml(model);
        assertTrue("深さ 2 の C は '--'", puml.contains("\n-- C\n"));
        assertTrue("深さ 3 の D は枝の LEFT 系へ正規化され '---'", puml.contains("\n--- D\n"));
        assertFalse("枝と食い違う '+' 系 (+++ D) は出さない: " + puml, puml.contains("+++ D"));
        assertValidSvg(puml);
    }

    @Test
    public void toPuml_deepExplicitSideUnderAutoBranch_staysAutoFamilyAndRendersValidSvg()
            throws IOException {
        // 枝の起点 (深さ 2) が AUTO なら、深い子に明示 side があっても枝全体が '*' 系のまま。
        // 回帰: `* Root / ** C / --- D` は実機で error42L になった (深さ 2 の祖先で判定される)。
        MindmapSketchModel model = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        model.setRoot(root);
        MindmapNode c = child(model, root, "C", Side.AUTO);
        child(model, c, "D", Side.LEFT);

        String puml = MindmapSketchCodec.toPuml(model);
        assertTrue("深さ 2 の AUTO 枝は '**'", puml.contains("\n** C\n"));
        assertTrue("深さ 3 の D も枝の AUTO 系のまま '***'", puml.contains("\n*** D\n"));
        assertFalse("枝と食い違う '-' 系 (--- D) は出さない: " + puml, puml.contains("--- D"));
        assertValidSvg(puml);
    }

    @Test
    public void toPuml_emptyModel_producesMinimalDocument() {
        // 空図の toPuml は最小ドキュメント。PlantUML では "Empty description" 例外になる既知制約の
        // ため、この文字列は描画テストの対象外にする (往復描画テストは非空のみ)。
        assertEquals("@startmindmap\n@endmindmap\n",
                MindmapSketchCodec.toPuml(new MindmapSketchModel()));
    }

    // --- 往復固定点 + 実描画 ------------------------------------------------------

    @Test
    public void roundTrip_template_reachesFixedPointAndRendersValidSvg() throws IOException {
        String templateText = PumlTemplate.MINDMAP.body();
        MindmapSketchCodec.ParseResult first = MindmapSketchCodec.parse(templateText);
        assertTrue("テンプレートは対応構文のはず: " + first.unsupportedLines, first.isFullySupported());
        String gen1 = MindmapSketchCodec.toPuml(first.model);
        MindmapSketchCodec.ParseResult second = MindmapSketchCodec.parse(gen1);
        assertTrue(second.isFullySupported());
        String gen2 = MindmapSketchCodec.toPuml(second.model);
        assertEquals("2 回目以降の再生成は固定点になるはず", gen1, gen2);
        assertValidSvg(gen1);
    }

    @Test
    public void roundTrip_explicitSideThreeLevelTree_reachesFixedPointAndRendersValidSvg()
            throws IOException {
        // 明示 side + 3 階層以上のネスト (family 不整合の回帰防止・最重要)。手組みツリーで
        // AUTO の孫が親の LEFT/RIGHT 系へ揃い、往復固定点かつ SyntaxError 無しで描けること。
        MindmapSketchModel model = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        model.setRoot(root);
        MindmapNode left = child(model, root, "Left", Side.LEFT);
        child(model, left, "L1", Side.AUTO);
        MindmapNode right = child(model, root, "Right", Side.RIGHT);
        MindmapNode r1 = child(model, right, "R1", Side.AUTO);
        child(model, r1, "R1a", Side.AUTO);

        String gen1 = MindmapSketchCodec.toPuml(model);
        // 左枝は '-' 系、右枝は '+' 系で一貫していること (深さは記号連続長)。
        assertTrue(gen1.contains("\n-- Left\n"));
        assertTrue(gen1.contains("\n--- L1\n"));
        assertTrue(gen1.contains("\n++ Right\n"));
        assertTrue(gen1.contains("\n+++ R1\n"));
        assertTrue(gen1.contains("\n++++ R1a\n"));

        MindmapSketchCodec.ParseResult parsed = MindmapSketchCodec.parse(gen1);
        assertTrue("再生成テキストは対応構文のはず: " + parsed.unsupportedLines,
                parsed.isFullySupported());
        String gen2 = MindmapSketchCodec.toPuml(parsed.model);
        assertEquals("明示 side ツリーも固定点になるはず", gen1, gen2);
        assertValidSvg(gen1);
    }

    @Test
    public void model_effectiveSideOf_inheritsFromAncestor() {
        MindmapSketchModel model = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        model.setRoot(root);
        MindmapNode a = child(model, root, "A", Side.RIGHT);
        MindmapNode b = child(model, a, "B", Side.AUTO);
        assertSame(Side.RIGHT, model.effectiveSideOf(b));
        assertSame("AUTO のみの祖先鎖は AUTO", Side.AUTO, model.effectiveSideOf(root));
    }

    @Test
    public void model_effectiveSideOf_usesBranchOriginNotOwnSide() {
        // 深いノードの ownSide が枝と食い違っても、実効 side は枝の起点 (深さ 2) の side。
        // ルート自身の側は子を拘束しない (root の LEFT は子へ波及しない)。
        MindmapSketchModel model = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        root.setOwnSide(Side.LEFT);
        model.setRoot(root);
        MindmapNode c = child(model, root, "C", Side.RIGHT);   // 枝の起点 = RIGHT
        MindmapNode d = child(model, c, "D", Side.LEFT);        // 深いノードは LEFT 明示
        assertSame("D の実効 side は自身の LEFT ではなく枝の RIGHT", Side.RIGHT,
                model.effectiveSideOf(d));
        assertSame("枝の起点 C は自身の RIGHT", Side.RIGHT, model.effectiveSideOf(c));
        assertSame("ルートは自身の側 (子には波及しない)", Side.LEFT, model.effectiveSideOf(root));
    }

    @Test
    public void model_reparent_rejectsCycleAndAllowsValidMove() {
        MindmapSketchModel model = new MindmapSketchModel();
        MindmapNode root = new MindmapNode("Root");
        model.setRoot(root);
        MindmapNode a = child(model, root, "A", Side.AUTO);
        MindmapNode b = child(model, a, "B", Side.AUTO);
        MindmapNode c = child(model, root, "C", Side.AUTO);
        assertFalse("祖先を子孫へ付け替えるのは循環なので不可", model.reparent(a, b, -1));
        assertTrue("無関係なノードへの付け替えは可", model.reparent(c, a, -1));
        assertSame(a, c.getParent());
        assertFalse("ルートは全ノードの祖先なので付け替え不可", model.reparent(root, a, -1));
    }

    @Test
    public void parse_emptyDocument_hasNullRootAndIsSupported() {
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse("@startmindmap\n@endmindmap\n");
        assertTrue("空図は完全対応", r.isFullySupported());
        assertNull("ルートは無し (空図)", r.model.getRoot());
    }

    private static void assertValidSvg(String puml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML が構文エラーを報告した:\n" + puml, svg.contains("Syntax Error"));
        assertTrue("SVG が生成されるはず", svg.contains("<svg"));
    }
}
