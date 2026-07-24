// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;

/**
 * 配置図リンクの端点 (ハンドル) にまつわる純ジオメトリ (Swing 描画に依存しない)。
 *
 * <p>リンクの端点座標の算出 ({@link #endpointsOf}) と、ある絶対座標点がどの
 * リンクのどちら側の端点ハンドルに近いかの当たり判定 ({@link #hitTest}) を提供する。
 * 自己リンク (from == to) は付け替え対象にしないため対象外とする。</p>
 */
final class DeploySketchLinkHandles {

    /** ハンドルの当たり判定半径 (モデル座標)。 */
    static final int HANDLE_HIT_RADIUS = 8;
    /** ハンドル描画の一辺の長さ。 */
    static final int HANDLE_SIZE = 6;

    private DeploySketchLinkHandles() {
    }

    /** ヒットしたリンクとどちら側の端点か ({@code startEnd} true = from 側)。 */
    record EndpointHit(DeployLink link, boolean startEnd) {
    }

    static Point center(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** {@code r} の境界上で {@code toward} 方向を向いた点 (矢印の付け根)。 */
    static Point edgePoint(Rectangle r, Point toward) {
        Point c = center(r);
        double dx = toward.x - c.x;
        double dy = toward.y - c.y;
        if (dx == 0 && dy == 0) {
            return c;
        }
        double scaleX = dx != 0 ? (r.width / 2.0) / Math.abs(dx) : Double.MAX_VALUE;
        double scaleY = dy != 0 ? (r.height / 2.0) / Math.abs(dy) : Double.MAX_VALUE;
        double t = Math.min(scaleX, scaleY);
        return new Point((int) Math.round(c.x + dx * t), (int) Math.round(c.y + dy * t));
    }

    /**
     * リンクの端点座標 {@code [from 側, to 側]} を絶対座標で返す。
     * 自己リンク (from == to) はループの出口/戻り点 ({@link #selfLoopPoints}) を返す
     * (掴み直せるようハンドルを消さないため)。端点ノードが解決できないときは null。
     */
    static Point[] endpointsOf(DeploySketchModel model, DeployLink link,
                               Map<DeployNode, Rectangle> layout) {
        DeployNode from = model.findNode(link.getFrom());
        DeployNode to = model.findNode(link.getTo());
        if (from == null || to == null) {
            return null;
        }
        Rectangle fr = layout.get(from);
        Rectangle tr = layout.get(to);
        if (fr == null || tr == null) {
            return null;
        }
        if (from == to) {
            Point[] loop = selfLoopPoints(fr);
            return new Point[]{loop[0], loop[loop.length - 1]};
        }
        return new Point[]{edgePoint(fr, center(tr)), edgePoint(tr, center(fr))};
    }

    /**
     * 自己リンクの折れ線頂点列 (上辺→上→右→右辺へ戻る)。
     * {@link DeploySketchCanvas#paintSelfLink} の描画ジオメトリと一致させる。
     */
    static Point[] selfLoopPoints(Rectangle r) {
        int exitX = r.x + r.width - 20;
        int topY = r.y - 18;
        int rightX = r.x + r.width + 18;
        int retY = r.y + 14;
        return new Point[]{
                new Point(exitX, r.y),
                new Point(exitX, topY),
                new Point(rightX, topY),
                new Point(rightX, retY),
                new Point(r.x + r.width, retY),
        };
    }

    /**
     * {@code p} (絶対座標) の近くにある端点ハンドルを探す (無ければ null)。
     *
     * <p>{@code zoom} で当たり判定半径 ({@link #HANDLE_HIT_RADIUS}, 画面上 px 相当) を
     * {@link EndpointHitThreshold#modelRadius} によりモデル座標半径へ換算する。他 7
     * キャンバスと同様、縮小 (最小 {@link SketchViewport#MIN_ZOOM} = 0.25x) してもハンドルが
     * 画面上ではおよそ一定の大きさで掴めるようにするため (bug-hunt round4 指摘 K)。</p>
     */
    static EndpointHit hitTest(DeploySketchModel model, Map<DeployNode, Rectangle> layout,
                               Point p, double zoom) {
        double threshold = EndpointHitThreshold.modelRadius(HANDLE_HIT_RADIUS, zoom);
        for (DeployLink link : model.getLinks()) {
            Point[] eps = endpointsOf(model, link, layout);
            if (eps == null) {
                continue;
            }
            if (p.distance(eps[0]) <= threshold) {
                return new EndpointHit(link, true);
            }
            if (p.distance(eps[1]) <= threshold) {
                return new EndpointHit(link, false);
            }
        }
        return null;
    }
}
