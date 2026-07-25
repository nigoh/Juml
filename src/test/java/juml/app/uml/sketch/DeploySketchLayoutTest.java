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
import static org.junit.Assert.assertFalse;
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

    // --- GUI テスト監査 round3 [Medium]: 3〜5階層の深いネスト境界 ---------------------------
    //
    // 上記 round6 テスト群は DeploySketchCodec#parse を経由するため負座標が load 時に 0 へ
    // 正規化され、DeploySketchLayout 自体の枠拡張ロジック (layoutContainer の
    // minLeft/minTop/maxRight/maxBottom 更新, :96-107) が実際に発動する深いネスト経路は
    // 検証できていなかった。DeploySketchLayout は DeployNode がどう組み立てられたかを
    // 一切関知しない純粋関数 (DeployNode のコンストラクタ/setX 相当も座標を検証しない) なので、
    // 直接 DeployNode を負の相対座標で組み立てて compute()/computeContentOrigins() を
    // 深いネストで direct に検証する。期待値は算術を手計算し、本番アルゴリズムを再現した
    // 独立スクリプトで二重検算済み (テスト実装がロジックをそのまま複製して自己一致するだけ、
    // という弱いテストにならないようにするため)。

    @Test
    public void compute_fourLevelNestingWithNegativeCoordsAtEveryLevel_expandsAllAncestorsButOriginsStayFixed() {
        // 5段の入れ子鎖 (R1⊇R2⊇R3⊇R4⊇L)。各階層の相対座標に負の x/y を混在させ、最深部 (L) の
        // 負座標由来の枠拡張が R4→R3→R2→R1 と 4階層ぶん連鎖伝播することを固定する。
        DeployNode l = node("L", -10, -20);
        DeployNode r4 = node("R4", 9, -5);
        r4.setContainer(true);
        r4.getChildren().add(l);
        l.setParent(r4);
        DeployNode r3 = node("R3", -3, 7);
        r3.setContainer(true);
        r3.getChildren().add(r4);
        r4.setParent(r3);
        DeployNode r2 = node("R2", -6, -4);
        r2.setContainer(true);
        r2.getChildren().add(r3);
        r3.setParent(r2);
        DeployNode r1 = node("R1", 0, 0);
        r1.setContainer(true);
        r1.getChildren().add(r2);
        r2.setParent(r1);

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(r1), SIZER);
        Map<DeployNode, Point> origins = DeploySketchLayout.computeContentOrigins(List.of(r1), SIZER);

        // 各ノードの絶対矩形 (手計算 + 独立シミュレーションで二重検算済み)。
        assertEquals("最深部の葉ノードの絶対矩形", new Rectangle(46, 98, 60, 30), layout.get(l));
        assertEquals("R4 は L の負座標により左/上へ枠拡張されるはず",
                new Rectangle(32, 84, 150, 58), layout.get(r4));
        assertEquals("R3 は R4 の拡張後矩形を包むようさらに拡張されるはず",
                new Rectangle(18, 63, 178, 93), layout.get(r3));
        assertEquals("R2 でも拡張が連鎖するはず", new Rectangle(4, 26, 206, 144), layout.get(r2));
        assertEquals("最上位 R1 まで拡張が伝播し左へはみ出すはず",
                new Rectangle(-10, 0, 234, 184), layout.get(r1));

        // 論理 content 原点はどの祖先の枠が拡張されても不変 (bug-hunt round5 の契約)。
        assertEquals("R4 の子配置基準は自身の枠拡張の影響を受けないはず",
                new Point(56, 118), origins.get(r4));
        assertEquals(new Point(33, 93), origins.get(r3));
        assertEquals(new Point(22, 56), origins.get(r2));
        assertEquals("最上位 R1 の content 原点も祖先が無いので不変", new Point(14, 30), origins.get(r1));

        // 境界矩形は全階層で直下の子孫を包含するはず (4階層それぞれで確認)。
        assertTrue("R1 は R2 を包含するはず", layout.get(r1).contains(layout.get(r2)));
        assertTrue("R2 は R3 を包含するはず", layout.get(r2).contains(layout.get(r3)));
        assertTrue("R3 は R4 を包含するはず", layout.get(r3).contains(layout.get(r4)));
        assertTrue("R4 は L を包含するはず", layout.get(r4).contains(layout.get(l)));
        // 推移的に最上位から最深部まで包含が成立するはず。
        assertTrue("最上位 R1 は最深部 L も包含するはず", layout.get(r1).contains(layout.get(l)));

        // 葉ノードの絶対位置は「直接の親の content 原点 + 相対座標」で一意に決まり、
        // 上位階層 (R1〜R3) の枠拡張の影響を受けない。
        assertEquals("L の絶対位置は R4 の content 原点基準で決まるはず",
                layout.get(l).getLocation(),
                new Point(origins.get(r4).x + l.getX(), origins.get(r4).y + l.getY()));
    }

    @Test
    public void compute_threeLevelNestingWithMixedSiblingSigns_containsDescendantsAndKeepsOriginsFixed() {
        // 3階層 (P⊇A1⊇{B1,B2}, P の子には正座標の A2 も同居)。同一階層 (A1 の子 B1/B2) に
        // 負/正双方の相対座標を混在させつつ、さらに P の子にも符号の異なる兄弟 (A1 は負, A2 は正)
        // を混在させる。round5 のテストは 2階層止まりだったので、この 3階層版で「兄弟の符号混在」
        // と「祖先を跨いだ枠拡張の伝播」を同時に固定する。
        DeployNode b1 = node("B1", 10, -20);
        DeployNode b2 = node("B2", -25, 5);
        DeployNode a1 = node("A1", -8, 3);
        a1.setContainer(true);
        a1.getChildren().add(b1);
        b1.setParent(a1);
        a1.getChildren().add(b2);
        b2.setParent(a1);
        DeployNode a2 = node("A2", 50, 60);
        DeployNode p = node("P", 0, 0);
        p.setContainer(true);
        p.getChildren().add(a1);
        a1.setParent(p);
        p.getChildren().add(a2);
        a2.setParent(p);

        Map<DeployNode, Rectangle> layout = DeploySketchLayout.compute(List.of(p), SIZER);
        Map<DeployNode, Point> origins = DeploySketchLayout.computeContentOrigins(List.of(p), SIZER);

        assertEquals(new Rectangle(30, 43, 60, 30), layout.get(b1));
        assertEquals(new Rectangle(-5, 68, 60, 30), layout.get(b2));
        assertEquals("A1 は B1/B2 双方の負座標を包むよう上/左へ拡張されるはず",
                new Rectangle(-19, 29, 165, 83), layout.get(a1));
        assertEquals("正座標の兄弟 A2 は拡張の影響を受けないはず",
                new Rectangle(64, 90, 60, 30), layout.get(a2));
        assertEquals("P は A1 の拡張後矩形と A2 の両方を包むはず",
                new Rectangle(-33, 0, 193, 134), layout.get(p));

        assertEquals(new Point(20, 63), origins.get(a1));
        assertEquals(new Point(14, 30), origins.get(p));

        assertTrue("P は A1 を包含するはず", layout.get(p).contains(layout.get(a1)));
        assertTrue("P は A2 を包含するはず", layout.get(p).contains(layout.get(a2)));
        assertTrue("A1 は B1 を包含するはず", layout.get(a1).contains(layout.get(b1)));
        assertTrue("A1 は B2 を包含するはず", layout.get(a1).contains(layout.get(b2)));
        assertTrue("最上位 P は孫 B1 も推移的に包含するはず", layout.get(p).contains(layout.get(b1)));
        assertTrue("最上位 P は孫 B2 も推移的に包含するはず", layout.get(p).contains(layout.get(b2)));

        assertEquals("B1 の絶対位置は A1 の content 原点基準",
                layout.get(b1).getLocation(),
                new Point(origins.get(a1).x + b1.getX(), origins.get(a1).y + b1.getY()));
        assertEquals("B2 の絶対位置も同じ A1 の content 原点基準",
                layout.get(b2).getLocation(),
                new Point(origins.get(a1).x + b2.getX(), origins.get(a1).y + b2.getY()));
        assertEquals("正座標の兄弟 A2 は P の content 原点基準",
                layout.get(a2).getLocation(),
                new Point(origins.get(p).x + a2.getX(), origins.get(p).y + a2.getY()));

        // A1 自身は「アンカー位置 (P の content 原点 + A1 の相対座標)」と「実際に返る矩形の
        // 左上」が一致しない好例 (bug-hunt round3/round5 の核心)。A1 の矩形は B1/B2 の負座標
        // により左/上へ拡張されるため、アンカーそのものとはズレる。origin(A1) から
        // CONTAINER_PAD とタイトル高 (SIZER 固定値 30) を引き戻すとアンカーへ復元できる。
        Point a1Anchor = new Point(origins.get(a1).x - DeploySketchLayout.CONTAINER_PAD,
                origins.get(a1).y - 30);
        assertEquals("origin(A1) から逆算したアンカーは P の content 原点 + A1 の相対座標と一致するはず",
                new Point(origins.get(p).x + a1.getX(), origins.get(p).y + a1.getY()), a1Anchor);
        assertFalse("A1 自身の矩形左上は、その子の負座標による枠拡張でアンカーとはズレるはず",
                a1Anchor.equals(layout.get(a1).getLocation()));
    }
}
