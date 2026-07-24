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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DeploySketchLinkHandles#hitTest} のズーム換算 (bug-hunt round4 指摘 K) と、
 * 最近傍探索であること (bug-hunt round6 #1/#2) を検証する。
 *
 * <p>他 7 キャンバス (Class/State/Object/UseCase/Component/ER/Seq) は端点ハンドルの当たり
 * 判定半径を {@link EndpointHitThreshold#modelRadius} でズームに応じてモデル座標へ換算する
 * ため、縮小 (最小 {@link SketchViewport#MIN_ZOOM} = 0.25x) してもハンドルが画面上ではおよそ
 * 一定の大きさで掴める。修正前の Deploy は {@code HANDLE_HIT_RADIUS} をモデル座標の固定値の
 * まま距離比較していたため、縮小時に画面上の当たり判定が zoom 倍に縮んでしまっていた。</p>
 *
 * <p>また修正前の Deploy 独自実装は「しきい値内で最初に一致した端点を即 return」する先勝ち
 * 判定だったため、近接する 2 リンクや自己リンクの 2 端点がともにしきい値へ入ると、押下点に
 * 最も近い端点ではなく model.getLinks() の並び順で先に判定された方を誤って返していた。他
 * キャンバスと同様 {@link EndpointHitThreshold#nearestPair} による全走査の最近傍探索へ
 * 統一したことをここで固定する (bug-hunt round6)。</p>
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

    // --- bug-hunt round6: 先勝ちではなく最近傍 (EndpointHitThreshold#nearestPair) を選ぶはず ---

    @Test
    public void hitTest_twoNearbyLinks_picksNearestLinkNotFirstInList() {
        // A->B (eps = (60,15),(200,15)) と E->F (eps = (203,15),(400,15)) の 2 本を用意する。
        // A->B の "to" 側 (200,15) と E->F の "from" 側 (203,15) はわずか 3px しか離れておらず、
        // 押下点 (203,15) は両方ともしきい値 (8px) 内に入る。しかし押下点は E->F の "from" 側
        // そのもの (距離 0) であり、A->B の "to" 側 (距離 3) より明らかに近い。
        // 修正前の「しきい値内で最初に一致した端点を即 return」実装は、model.getLinks() の
        // 並び順で先に判定される A->B 側 (距離 3) を誤って返してしまう (bug-hunt round6 #1/#2)。
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 0, 0);
        DeployNode b = new DeployNode(DeployNode.Kind.NODE, "B", null, 200, 0);
        DeployNode e = new DeployNode(DeployNode.Kind.NODE, "E", null, 143, 0);
        DeployNode f = new DeployNode(DeployNode.Kind.NODE, "F", null, 400, 0);
        model.getNodes().add(a);
        model.getNodes().add(b);
        model.getNodes().add(e);
        model.getNodes().add(f);
        DeployLink farLink = new DeployLink("A", DeployLink.Kind.ARROW, "B", null);
        DeployLink nearLink = new DeployLink("E", DeployLink.Kind.ARROW, "F", null);
        // farLink (誤って選ばれてはいけない側) を先に登録し、先勝ち実装なら誤検出する並びにする。
        model.getLinks().add(farLink);
        model.getLinks().add(nearLink);
        Map<DeployNode, Rectangle> layout = layoutOf(a, b, e, f);

        Point[] nearEps = DeploySketchLinkHandles.endpointsOf(model, nearLink, layout);
        Point press = new Point(nearEps[0]); // E->F の "from" 側そのもの (距離 0)。

        DeploySketchLinkHandles.EndpointHit hit =
                DeploySketchLinkHandles.hitTest(model, layout, press, 1.0);
        assertNotNull(hit);
        assertSame("先に登録された farLink ではなく、真に最も近い nearLink が選ばれるはず",
                nearLink, hit.link());
        assertTrue("nearLink の from 側 (startEnd=true) が選ばれるはず", hit.startEnd());
    }

    @Test
    public void hitTest_selfLinkZoomedOut_pressingToEndPointStillPicksToEnd() {
        // 自己リンク (from == to) の 2 端点 (selfLoopPoints の出口/戻り点) は約 24.4px しか
        // 離れておらず、0.3x ズームではしきい値 (8/0.3 ≈ 26.7px) がこれを超える。修正前の
        // 「先勝ち」実装は、eps[0] (from 側) を先に判定してしまい、eps[1] (to 側) をそのまま
        // 押しても from 側を誤って返す (Seq キャンバスの bug-hunt round5 論点2と同型のバグ)。
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 0, 0);
        model.getNodes().add(a);
        DeployLink self = new DeployLink("A", DeployLink.Kind.ARROW, "A", null);
        model.getLinks().add(self);
        Map<DeployNode, Rectangle> layout = layoutOf(a);

        Point[] eps = DeploySketchLinkHandles.endpointsOf(model, self, layout);
        Point toPoint = new Point(eps[1]); // to 側 (ループの戻り点) そのもの。

        DeploySketchLinkHandles.EndpointHit hit =
                DeploySketchLinkHandles.hitTest(model, layout, toPoint, 0.3);
        assertNotNull("自己リンクの端点はヒットするはず", hit);
        assertSame(self, hit.link());
        assertFalse("to 側 (startEnd=false) が選ばれ、向きが反転しないはず", hit.startEnd());
    }
}
