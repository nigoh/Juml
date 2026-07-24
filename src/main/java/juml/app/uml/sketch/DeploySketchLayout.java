// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployNode;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置図キャンバスの入れ子対応レイアウト計算 (純ロジック、Swing 描画に依存しない)。
 *
 * <p>ノードの {@code x}/{@code y} は、最上位なら盤面の絶対座標、コンテナの子なら
 * 親の内側原点 (タイトル行の下・左パディング分内側) からの相対座標として扱う。
 * {@link #compute} は全ノードの絶対矩形を 1 回の深さ優先探索で求める。コンテナは
 * 自身のタイトル分の最小サイズと、全子孫を包む領域の大きい方に自動で広がる。</p>
 */
final class DeploySketchLayout {

    /** コンテナ内側の余白 (子ノード領域とタイトル/枠の間隔)。 */
    static final int CONTAINER_PAD = 14;
    /** コンテナが子を持たない/小さいときの最小幅。 */
    static final int MIN_CONTAINER_W = 140;

    private DeploySketchLayout() {
    }

    /** 葉ノード (非コンテナ) のタイトル込みサイズを返す関数 (キャンバスの FontMetrics 依存)。 */
    interface Sizer {
        Dimension sizeOf(DeployNode n);
    }

    /**
     * {@code roots} (トップレベルのノード群) から全ノード (入れ子含む) の絶対矩形を求める。
     * 戻り値は同一性比較のマップ (ノードインスタンスをキーに使う)。
     */
    static Map<DeployNode, Rectangle> compute(List<DeployNode> roots, Sizer sizer) {
        Map<DeployNode, Rectangle> out = new IdentityHashMap<>();
        for (DeployNode n : roots) {
            layout(n, 0, 0, sizer, out);
        }
        return out;
    }

    private static Rectangle layout(DeployNode n, int originX, int originY,
                                    Sizer sizer, Map<DeployNode, Rectangle> out) {
        int ax = originX + n.getX();
        int ay = originY + n.getY();
        Dimension title = sizer.sizeOf(n);
        Rectangle r = n.isContainer()
                ? layoutContainer(n, ax, ay, title, sizer, out)
                : new Rectangle(ax, ay, title.width, title.height);
        out.put(n, r);
        return r;
    }

    private static Rectangle layoutContainer(DeployNode n, int ax, int ay, Dimension title,
                                             Sizer sizer, Map<DeployNode, Rectangle> out) {
        int contentX = ax + CONTAINER_PAD;
        int contentY = ay + title.height;
        // 子は '@pos の手編集で負の相対座標も持ちうる (GUI ドラッグは非負に丸めるが、
        // テキスト往復で到達しうる)。minLeft/minTop も追跡し、枠が右/下だけでなく
        // 左/上へはみ出す子も包含するようにする (bug-hunt round3 指摘 I)。
        int minLeft = ax;
        int minTop = ay;
        int maxRight = ax + Math.max(title.width + 2 * CONTAINER_PAD, MIN_CONTAINER_W);
        int maxBottom = contentY + CONTAINER_PAD;
        for (DeployNode c : n.getChildren()) {
            Rectangle cr = layout(c, contentX, contentY, sizer, out);
            minLeft = Math.min(minLeft, cr.x - CONTAINER_PAD);
            minTop = Math.min(minTop, cr.y - CONTAINER_PAD);
            maxRight = Math.max(maxRight, cr.x + cr.width + CONTAINER_PAD);
            maxBottom = Math.max(maxBottom, cr.y + cr.height + CONTAINER_PAD);
        }
        return new Rectangle(minLeft, minTop, maxRight - minLeft, maxBottom - minTop);
    }

    /**
     * {@code p} (絶対座標) にあるノードを探す。同じ階層では後ろ (最近追加) のノードを
     * 手前として優先し、コンテナは中身 (子) を先に調べてから自分自身の余白領域を調べる
     * (子をクリックしたときに親コンテナではなく子が選ばれるようにするため)。
     */
    static DeployNode hitTest(List<DeployNode> siblings, Map<DeployNode, Rectangle> layout, Point p) {
        for (int i = siblings.size() - 1; i >= 0; i--) {
            DeployNode n = siblings.get(i);
            if (n.isContainer()) {
                DeployNode hit = hitTest(n.getChildren(), layout, p);
                if (hit != null) {
                    return hit;
                }
            }
            Rectangle r = layout.get(n);
            if (r != null && r.contains(p)) {
                return n;
            }
        }
        return null;
    }

    /** コンテナ矩形からその内側原点 (子ノードの相対座標 (0,0) が指す絶対位置) を求める。 */
    static Point contentOrigin(Rectangle containerRect, Dimension title) {
        return new Point(containerRect.x + CONTAINER_PAD, containerRect.y + title.height);
    }

    /** {@code target} の絶対原点 (ドラッグ時に相対座標へ変換する基準点)。最上位なら (0,0)。 */
    static Point contentOriginOf(DeployNode target, Map<DeployNode, Rectangle> layout, Sizer sizer) {
        DeployNode parent = target.getParent();
        if (parent == null) {
            return new Point(0, 0);
        }
        return contentOrigin(layout.get(parent), sizer.sizeOf(parent));
    }
}
