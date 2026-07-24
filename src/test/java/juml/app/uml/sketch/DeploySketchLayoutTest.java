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

    // --- bug-hunt round3 指摘 I → round6 で根本修正: 負の相対座標は DeploySketchCodec#parse が
    // load 時に 0 へ正規化するため、DeploySketchLayout に渡る時点では既に非負であり、枠拡張
    // (旧 minLeft/minTop) はもはや発動しない。手編集テキスト ('@pos L -30 -20) を実際に
    // DeploySketchCodec.parse へ通し、正規化後の値で枠計算を検証する (直接 DeployNode を
    // 負座標で組み立てる旧テストは、load を経ない到達不能な入力を検証してしまうため、
    // 実際の入力経路 (parse) を通す形に更新した)。--------------------------------------------

    @Test
    public void compute_negativeRelativeChildPos_isNormalizedAtLoadSoFrameNeedsNoExpansion() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node C {",
                "  node L",
                "}",
                "'@pos C 0 0",
                "'@pos L -30 -20",
                "@enduml", ""));
        DeployNode container = r.model.findNode("C");
        DeployNode child = r.model.findNode("L");
        assertEquals("load 時に 0 へ正規化されるはず", 0, child.getX());
        assertEquals("load 時に 0 へ正規化されるはず", 0, child.getY());

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(container), SIZER);
        Rectangle containerRect = layout.get(container);
        Rectangle childRect = layout.get(child);

        // 子: 原点 (14,30) + 正規化後の相対 (0,0)、サイズ (60,30)。
        assertEquals(new Rectangle(14, 30, 60, 30), childRect);
        // 親: 正規化により負座標の子がいなくなったので、通常の (非負専用) 自動サイズと同じ
        // (枠は左/上へ広がらない)。
        assertEquals(new Rectangle(0, 0, 140, 74), containerRect);
        assertTrue("正規化後も枠内に収まるはず", containerRect.contains(childRect));
    }

    @Test
    public void compute_mixedPositiveAndNegativeChildrenPos_bothNormalizedWithinBounds() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node C {",
                "  node L1",
                "  node L2",
                "}",
                "'@pos C 0 0",
                "'@pos L1 -30 -20",
                "'@pos L2 5 5",
                "@enduml", ""));
        DeployNode container = r.model.findNode("C");
        DeployNode negChild = r.model.findNode("L1");
        DeployNode posChild = r.model.findNode("L2");
        assertEquals("負だった子は 0 へ正規化されるはず", 0, negChild.getX());
        assertEquals(0, negChild.getY());
        assertEquals("元々正だった子はそのままのはず", 5, posChild.getX());
        assertEquals(5, posChild.getY());

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(container), SIZER);
        Rectangle containerRect = layout.get(container);

        assertTrue("正規化後の子も枠内に収まるはず",
                containerRect.contains(layout.get(negChild)));
        assertTrue("正の相対座標の子も枠内に収まるはず",
                containerRect.contains(layout.get(posChild)));
    }

    // --- bug-hunt round5 論点1 → round6 で根本修正: 負座標がそもそも load 時に消えるため、
    // containerRect が広がって contentOrigin の逆算がずれる発生条件自体が消える -------------

    @Test
    public void computeContentOrigins_negativeChildPosNormalizedAtLoad_frameNoLongerExpands() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(String.join("\n",
                "@startuml",
                "node C {",
                "  node L1",
                "  node L2",
                "}",
                "'@pos C 0 0",
                "'@pos L1 -30 -20",
                "'@pos L2 5 5",
                "@enduml", ""));
        DeployNode container = r.model.findNode("C");
        DeployNode negChild = r.model.findNode("L1");
        DeployNode posChild = r.model.findNode("L2");

        Map<DeployNode, Rectangle> bounds = DeploySketchLayout.compute(List.of(container), SIZER);
        Map<DeployNode, Point> origins =
                DeploySketchLayout.computeContentOrigins(List.of(container), SIZER);

        // 正規化により負座標の子がいなくなったので、枠 (containerRect) はもう左/上へ広がらない
        // (修正前はここが < 0 になり、それを起点に contentOrigin を逆算するとずれていた)。
        Rectangle containerRect = bounds.get(container);
        assertEquals("正規化後は枠が左へ広がらないはず", 0, containerRect.x);
        assertEquals("正規化後は枠が上へ広がらないはず", 0, containerRect.y);

        // 論理 content 原点は常にコンテナ自身の絶対位置基準 (ax+PAD, ay+title.height) = (14,30)。
        Point origin = DeploySketchLayout.contentOriginOf(negChild, origins);
        assertEquals(new Point(14, 30), origin);
        assertEquals("正座標の子から見ても同じコンテナ原点のはず",
                origin, DeploySketchLayout.contentOriginOf(posChild, origins));

        // 往復固定点: 論理原点 + 子の相対座標 == 子の絶対矩形の左上、という契約を固定する。
        // これはまさに addChildNode/子ドラッグが press 位置から相対座標を逆算する式そのもの。
        // 正規化によりモデルの実値 (negChild.getX/getY) 自体が非負になるため、この等式が
        // 常に矛盾なく成立する (旧バグは枠拡張後の containerRect から逆算してずれていた)。
        assertEquals(bounds.get(negChild).getLocation(),
                new Point(origin.x + negChild.getX(), origin.y + negChild.getY()));
        assertEquals(bounds.get(posChild).getLocation(),
                new Point(origin.x + posChild.getX(), origin.y + posChild.getY()));
    }
}
