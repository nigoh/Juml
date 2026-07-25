// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.MindmapNode.Side;
import org.junit.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link MindmapSketchLayout} の純ジオメトリ計算を検証する (Swing/Graphics2D 不要、
 * Robot 不要)。固定サイズのスタブ {@link #SIZER} (幅 60・高さ 20) で厳密な数値まで確認する。
 */
public class MindmapSketchLayoutTest {

    /** 全ノード共通の固定サイズ (幅 60, 高さ 20) を返すスタブ。算術を単純にする。 */
    private static final MindmapSketchLayout.Sizer SIZER = n -> new Dimension(60, 20);

    private static MindmapNode node(String text, Side side) {
        MindmapNode n = new MindmapNode(text);
        n.setOwnSide(side);
        return n;
    }

    private static void link(MindmapNode parent, MindmapNode child) {
        child.setParent(parent);
        parent.getChildren().add(child);
    }

    @Test
    public void compute_nullRoot_returnsEmptyResult() {
        MindmapSketchLayout.Result r = MindmapSketchLayout.compute(null, SIZER);
        assertTrue(r.bounds.isEmpty());
        assertTrue(r.onRight.isEmpty());
    }

    @Test
    public void compute_singleRoot_placesAtMargin() {
        MindmapNode root = node("Root", Side.AUTO);
        MindmapSketchLayout.Result r = MindmapSketchLayout.compute(root, SIZER);
        assertEquals(new Rectangle(30, 30, 60, 20), r.bounds.get(root));
    }

    @Test
    public void compute_explicitSides_placeLeftAndRight() {
        MindmapNode root = node("Root", Side.AUTO);
        MindmapNode left = node("L", Side.LEFT);
        MindmapNode right = node("R", Side.RIGHT);
        link(root, left);
        link(root, right);

        MindmapSketchLayout.Result r = MindmapSketchLayout.compute(root, SIZER);
        // rootX = MARGIN(30) + maxLeftDepth(1) * COL_W(170) = 200。
        assertEquals(new Rectangle(200, 30, 60, 20), r.bounds.get(root));
        // 左は rootX - COL_W = 30、右は rootX + COL_W = 370。
        assertEquals(new Rectangle(30, 30, 60, 20), r.bounds.get(left));
        assertEquals(new Rectangle(370, 30, 60, 20), r.bounds.get(right));
        assertFalse("LEFT 明示は左側", r.onRight.get(left));
        assertTrue("RIGHT 明示は右側", r.onRight.get(right));
    }

    @Test
    public void compute_autoChildren_balanceGreedyRightFirstOnTie() {
        MindmapNode root = node("Root", Side.AUTO);
        MindmapNode c1 = node("c1", Side.AUTO);
        MindmapNode c2 = node("c2", Side.AUTO);
        MindmapNode c3 = node("c3", Side.AUTO);
        link(root, c1);
        link(root, c2);
        link(root, c3);

        MindmapSketchLayout.Result r = MindmapSketchLayout.compute(root, SIZER);
        // 貪欲割当 (同数は右優先): c1→右, c2→左, c3→右。
        assertTrue("1 つ目は右 (同数タイは右優先)", r.onRight.get(c1));
        assertFalse("2 つ目は少ない左へ", r.onRight.get(c2));
        assertTrue("3 つ目は再びタイで右へ", r.onRight.get(c3));
    }

    @Test
    public void compute_siblingsOnSameSide_doNotOverlapVertically() {
        MindmapNode root = node("Root", Side.AUTO);
        MindmapNode a = node("A", Side.RIGHT);
        MindmapNode g1 = node("g1", Side.AUTO);
        MindmapNode g2 = node("g2", Side.AUTO);
        link(root, a);
        link(a, g1);
        link(a, g2);

        MindmapSketchLayout.Result r = MindmapSketchLayout.compute(root, SIZER);
        Rectangle rg1 = r.bounds.get(g1);
        Rectangle rg2 = r.bounds.get(g2);
        // 同じ深さ列に並ぶ (x 一致)。
        assertEquals("同じ深さは同じ列", rg1.x, rg2.x);
        // 縦に積まれ重ならない (g1 の下端 <= g2 の上端)。
        assertTrue("兄弟サブツリーが縦に重ならないこと",
                rg1.y + rg1.height <= rg2.y);
        // 親 A は子帯の中央に置かれる (子 2 つ + V_GAP の帯 56 の中央)。
        assertEquals(new Rectangle(200, 48, 60, 20), r.bounds.get(a));
    }

    @Test
    public void hitTest_insideNodeReturnsIt_outsideReturnsNull() {
        MindmapNode root = node("Root", Side.AUTO);
        MindmapNode a = node("A", Side.RIGHT);
        link(root, a);
        MindmapSketchLayout.Result r = MindmapSketchLayout.compute(root, SIZER);
        Rectangle ra = r.bounds.get(a);
        assertSame(a, MindmapSketchLayout.hitTest(r,
                new Point(ra.x + ra.width / 2, ra.y + ra.height / 2)));
        assertNull(MindmapSketchLayout.hitTest(r, new Point(-100, -100)));
    }
}
