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
        Map<DeployNode, Point> origins = DeploySketchLayout.computeContentOrigins(List.of(top), SIZER);
        assertEquals(new Point(0, 0), DeploySketchLayout.contentOriginOf(top, origins));
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
        Map<DeployNode, Point> origins = DeploySketchLayout.computeContentOrigins(List.of(c), SIZER);

        assertEquals(new Point(14, 30), DeploySketchLayout.contentOriginOf(d, origins));
        assertEquals(new Point(33, 65), DeploySketchLayout.contentOriginOf(g, origins));
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

    // --- bug-hunt round3 指摘 I: 負の相対座標を持つ子でも枠がはみ出さず包含するはず ------------

    @Test
    public void compute_containerWithNegativeRelativeChild_frameContainsChild() {
        // 手編集テキスト ('@pos c -30 -20 相当) で子に負の相対座標が付いたケース。
        DeployNode container = node("C", 0, 0);
        DeployNode child = node("L", -30, -20);
        container.getChildren().add(child);
        child.setParent(container);
        container.setContainer(true);

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(container), SIZER);
        Rectangle containerRect = layout.get(container);
        Rectangle childRect = layout.get(child);

        // 子: 原点 (14,30) + 相対 (-30,-20) = (-16,10)、サイズ (60,30)。
        assertEquals(new Rectangle(-16, 10, 60, 30), childRect);
        // 親: 左/上へ minLeft/minTop まで広がり、子を完全に包含する。
        assertEquals(new Rectangle(-30, -4, 170, 58), containerRect);
        assertTrue("負の相対座標を持つ子でも枠内に収まるはず", containerRect.contains(childRect));
    }

    @Test
    public void compute_containerWithMixedPositiveAndNegativeChildren_boundsContainBoth() {
        DeployNode container = node("C", 0, 0);
        DeployNode negChild = node("L1", -30, -20);
        DeployNode posChild = node("L2", 5, 5);
        container.getChildren().add(negChild);
        container.getChildren().add(posChild);
        negChild.setParent(container);
        posChild.setParent(container);
        container.setContainer(true);

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(container), SIZER);
        Rectangle containerRect = layout.get(container);

        assertTrue("負の相対座標の子も枠内に収まるはず",
                containerRect.contains(layout.get(negChild)));
        assertTrue("正の相対座標の子も枠内に収まるはず",
                containerRect.contains(layout.get(posChild)));
    }

    // --- bug-hunt round5 論点1: 負座標の子で containerRect が広がっても、論理 content 原点
    // (子ドラッグ/子追加の逆算基準) はコンテナ自身の絶対位置基準のまま保たれるはず -------------

    @Test
    public void computeContentOrigins_withNegativeChild_isUnaffectedByFrameExpansion() {
        // 手編集テキスト ('@pos c -30 -20 相当) で子に負の相対座標が付いたケース。
        DeployNode container = node("C", 0, 0);
        DeployNode negChild = node("L1", -30, -20);
        DeployNode posChild = node("L2", 5, 5);
        container.getChildren().add(negChild);
        container.getChildren().add(posChild);
        negChild.setParent(container);
        posChild.setParent(container);
        container.setContainer(true);

        Map<DeployNode, Rectangle> bounds = DeploySketchLayout.compute(List.of(container), SIZER);
        Map<DeployNode, Point> origins =
                DeploySketchLayout.computeContentOrigins(List.of(container), SIZER);

        // 枠 (containerRect) は負座標の子を包含するため左/上へ広がる (原点 (0,0) より小さくなる)。
        Rectangle containerRect = bounds.get(container);
        assertTrue("枠は左へ広がるはず", containerRect.x < 0);
        assertTrue("枠は上へ広がるはず", containerRect.y < 0);

        // 論理 content 原点は常にコンテナ自身の絶対位置基準 (ax+PAD, ay+title.height) = (14,30)
        // のままで、枠拡張の影響を受けない。修正前は containerRect.x/y + PAD/title.height から
        // 逆算しており、この原点が (ax - minLeft) だけずれていた (子ドラッグ/子追加が press 位置
        // から (ax - minLeft) だけジャンプするバグの根本原因)。
        Point origin = DeploySketchLayout.contentOriginOf(negChild, origins);
        assertEquals(new Point(14, 30), origin);
        assertEquals("正座標の子から見ても同じコンテナ原点のはず",
                origin, DeploySketchLayout.contentOriginOf(posChild, origins));

        // 往復固定点: 論理原点 + 子の相対座標 == 子の絶対矩形の左上、という契約を固定する。
        // これはまさに addChildNode/子ドラッグが press 位置から相対座標を逆算する式そのもの。
        assertEquals(bounds.get(negChild).getLocation(),
                new Point(origin.x + negChild.getX(), origin.y + negChild.getY()));
        assertEquals(bounds.get(posChild).getLocation(),
                new Point(origin.x + posChild.getX(), origin.y + posChild.getY()));
    }
}
