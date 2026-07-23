// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import org.junit.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DeploySketchLayout} の純ジオメトリ計算を検証する (Swing/Graphics2D 不要、
 * Robot 不要)。子の座標は親の内側原点からの相対値、という契約を固定サイズの
 * スタブ {@link #SIZER} で厳密な数値まで検証する。
 */
public class DeploySketchLayoutTest {

    /** 全ノード共通の固定タイトルサイズ (幅 60, 高さ 30) を返すスタブ。算術を単純にする。 */
    private static final DeploySketchLayout.Sizer SIZER = n -> new Dimension(60, 30);

    private static DeployNode node(String id, int x, int y) {
        return new DeployNode(DeployNode.Kind.NODE, id, null, x, y);
    }

    @Test
    public void compute_topLevelLeaf_usesAbsoluteCoordinates() {
        DeployNode leaf = node("A", 30, 40);
        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(leaf), SIZER);
        assertEquals(new Rectangle(30, 40, 60, 30), layout.get(leaf));
    }

    @Test
    public void compute_containerWithOneChild_autoSizesAroundChildAndPad() {
        DeployNode container = node("C", 0, 0);
        DeployNode child = node("L", 5, 5);
        container.getChildren().add(child);
        child.setParent(container);
        container.setContainer(true);

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(container), SIZER);

        // 子: 原点 (14,30) [CONTAINER_PAD, タイトル高] + 相対 (5,5) = (19,35)、サイズ (60,30)。
        assertEquals(new Rectangle(19, 35, 60, 30), layout.get(child));
        // 親: 子を包む領域 (幅 140 = 最小幅が優先、高さは子の下端+パディングまで拡張)。
        assertEquals(new Rectangle(0, 0, 140, 79), layout.get(container));
    }

    @Test
    public void compute_twoLevelNesting_propagatesAbsoluteOrigin() {
        DeployNode c = node("C", 0, 0);
        DeployNode d = node("D", 5, 5);
        DeployNode g = node("G", 3, 3);
        c.getChildren().add(d);
        d.setParent(c);
        c.setContainer(true);
        d.getChildren().add(g);
        g.setParent(d);
        d.setContainer(true);

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(c), SIZER);

        assertEquals(new Rectangle(36, 68, 60, 30), layout.get(g));
        assertEquals(new Rectangle(19, 35, 140, 77), layout.get(d));
        assertEquals(new Rectangle(0, 0, 173, 126), layout.get(c));
    }

    @Test
    public void hitTest_prefersInnermostChildOverContainer() {
        DeployNode c = node("C", 0, 0);
        DeployNode d = node("D", 5, 5);
        DeployNode g = node("G", 3, 3);
        c.getChildren().add(d);
        d.setParent(c);
        c.setContainer(true);
        d.getChildren().add(g);
        g.setParent(d);
        d.setContainer(true);
        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(c), SIZER);

        // G の内部の点は G を返す。
        assertSame(g, DeploySketchLayout.hitTest(List.of(c), layout, new Point(50, 75)));
        // D の枠内だが G の外の点は D を返す。
        assertSame(d, DeploySketchLayout.hitTest(List.of(c), layout, new Point(22, 40)));
        // C の枠内だが D の外の点は C を返す。
        assertSame(c, DeploySketchLayout.hitTest(List.of(c), layout, new Point(5, 5)));
        // どの矩形の外の点も null。
        assertNull(DeploySketchLayout.hitTest(List.of(c), layout, new Point(9999, 9999)));
    }

    @Test
    public void contentOriginOf_topLevel_isZero() {
        DeployNode top = node("T", 30, 40);
        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(top), SIZER);
        assertEquals(new Point(0, 0), DeploySketchLayout.contentOriginOf(top, layout, SIZER));
    }

    @Test
    public void contentOriginOf_nested_isParentsInnerOrigin() {
        DeployNode c = node("C", 0, 0);
        DeployNode d = node("D", 5, 5);
        DeployNode g = node("G", 3, 3);
        c.getChildren().add(d);
        d.setParent(c);
        c.setContainer(true);
        d.getChildren().add(g);
        g.setParent(d);
        d.setContainer(true);
        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(c), SIZER);

        assertEquals(new Point(14, 30), DeploySketchLayout.contentOriginOf(d, layout, SIZER));
        assertEquals(new Point(33, 65), DeploySketchLayout.contentOriginOf(g, layout, SIZER));
    }

    @Test
    public void compute_emptyContainer_stillGetsMinimumSize() {
        DeployNode empty = node("E", 10, 10);
        empty.setContainer(true);
        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(empty), SIZER);
        Rectangle r = layout.get(empty);
        assertTrue("最小幅 (140) 以上のはず", r.width >= DeploySketchLayout.MIN_CONTAINER_W);
        assertEquals(10, r.x);
        assertEquals(10, r.y);
    }
}
