// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import org.junit.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * {@link DeploySketchLinkHandles#hitTest} のズーム換算を検証する (bug-hunt round4 指摘 K)。
 *
 * <p>他 7 キャンバス (Class/State/Object/UseCase/Component/ER/Seq) は端点ハンドルの当たり
 * 判定半径を {@link EndpointHitThreshold#modelRadius} でズームに応じてモデル座標へ換算する
 * ため、縮小 (最小 {@link SketchViewport#MIN_ZOOM} = 0.25x) してもハンドルが画面上ではおよそ
 * 一定の大きさで掴める。修正前の Deploy は {@code HANDLE_HIT_RADIUS} をモデル座標の固定値の
 * まま距離比較していたため、縮小時に画面上の当たり判定が zoom 倍に縮んでしまっていた。</p>
 *
 * <p>{@link DeploySketchLinkHandles} は純ジオメトリのみに依存するため、Swing コンポーネントを
 * 生成せずヘッドレス環境でもスキップせずに実行できる ({@link DeploySketchLayoutTest} と同じ
 * 方針)。</p>
 */
public class DeploySketchLinkHandlesZoomTest {

    private static final DeploySketchLayout.Sizer SIZER = n -> new Dimension(60, 30);

    private static Map<DeployNode, Rectangle> layoutOf(DeployNode... nodes) {
        return DeploySketchLayout.compute(List.of(nodes), SIZER);
    }

    @Test
    public void hitTest_zoom1_missesPressTwentyModelPxFromHandle() {
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 0, 0);
        DeployNode b = new DeployNode(DeployNode.Kind.NODE, "B", null, 200, 0);
        model.getNodes().add(a);
        model.getNodes().add(b);
        DeployLink link = new DeployLink("A", DeployLink.Kind.ARROW, "B", null);
        model.getLinks().add(link);
        Map<DeployNode, Rectangle> layout = layoutOf(a, b);

        Point[] eps = DeploySketchLinkHandles.endpointsOf(model, link, layout);
        // アンカー ("to" 側, B の左辺中央) から水平に 20 モデル px 離れた press。
        Point farPress = new Point(eps[1].x - 20, eps[1].y);

        assertNull("等倍 (1.0x) では従来どおり 8px 相当を超えるとヒットしないはず",
                DeploySketchLinkHandles.hitTest(model, layout, farPress, 1.0));
    }

    @Test
    public void hitTest_zoomQuarter_catchesPressTwentyModelPxFromHandle() {
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 0, 0);
        DeployNode b = new DeployNode(DeployNode.Kind.NODE, "B", null, 200, 0);
        model.getNodes().add(a);
        model.getNodes().add(b);
        DeployLink link = new DeployLink("A", DeployLink.Kind.ARROW, "B", null);
        model.getLinks().add(link);
        Map<DeployNode, Rectangle> layout = layoutOf(a, b);

        Point[] eps = DeploySketchLinkHandles.endpointsOf(model, link, layout);
        Point farPress = new Point(eps[1].x - 20, eps[1].y);

        // 0.25x (MIN_ZOOM) では閾値が 8/0.25=32 モデル px まで広がるので、20px 離れた press も拾う
        // (bug-hunt round4 指摘 K)。
        DeploySketchLinkHandles.EndpointHit hit =
                DeploySketchLinkHandles.hitTest(model, layout, farPress, 0.25);
        assertNotNull("0.25x では画面上同じ距離でも掴めるはず", hit);
        assertSame(link, hit.link());
    }
}
