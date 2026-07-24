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
        Map<DeployNode, Point> origins = new IdentityHashMap<>();
        for (DeployNode n : roots) {
            layout(n, 0, 0, sizer, out, origins);
        }
        return out;
    }

    /**
     * {@link #compute} と同じレイアウトを行い、各コンテナ自身の論理 content 原点
     * (そのコンテナの子の相対座標 (0,0) が指す絶対位置。{@code (ax+CONTAINER_PAD,
     * ay+title.height)}) を返す。
     *
     * <p>子が負の相対座標を持つと {@link #compute} が返す containerRect は左/上へ広がる
     * (bug-hunt round3 指摘 I)。しかし子の実配置基準は常にこの論理原点のままであり、
     * 広がった後の containerRect から逆算すると (ax - minLeft) だけ原点がずれてしまう
     * (bug-hunt round5 論点1)。子ドラッグ ({@link DeploySketchCanvas#handlePress}/
     * {@code handleDrag}) や子追加 ({@link DeploySketchCanvas#addChildNode}) は
     * 必ずこちらの結果 ({@link #contentOriginOf}) を使うこと。</p>
     */
    static Map<DeployNode, Point> computeContentOrigins(List<DeployNode> roots, Sizer sizer) {
        Map<DeployNode, Rectangle> bounds = new IdentityHashMap<>();
        Map<DeployNode, Point> origins = new IdentityHashMap<>();
        for (DeployNode n : roots) {
            layout(n, 0, 0, sizer, bounds, origins);
        }
        return origins;
    }

    private static Rectangle layout(DeployNode n, int originX, int originY, Sizer sizer,
                                    Map<DeployNode, Rectangle> out, Map<DeployNode, Point> origins) {
        int ax = originX + n.getX();
        int ay = originY + n.getY();
        Dimension title = sizer.sizeOf(n);
        Rectangle r = n.isContainer()
                ? layoutContainer(n, ax, ay, title, sizer, out, origins)
                : new Rectangle(ax, ay, title.width, title.height);
        out.put(n, r);
        return r;
    }

    private static Rectangle layoutContainer(DeployNode n, int ax, int ay, Dimension title,
                                             Sizer sizer, Map<DeployNode, Rectangle> out,
                                             Map<DeployNode, Point> origins) {
        int contentX = ax + CONTAINER_PAD;
        int contentY = ay + title.height;
        // このコンテナの論理原点を記録する (枠拡張の影響を受けない、子配置の唯一の基準)。
        origins.put(n, new Point(contentX, contentY));
        // 子は '@pos の手編集で負の相対座標も持ちうる (GUI ドラッグは非負に丸めるが、
        // テキスト往復で到達しうる)。minLeft/minTop も追跡し、枠が右/下だけでなく
        // 左/上へはみ出す子も包含するようにする (bug-hunt round3 指摘 I)。ただしこれは
        // 描画用の枠 (containerRect) の拡張であり、子の配置基準 (上記 contentX/contentY)
        // 自体は変えない。
        int minLeft = ax;
        int minTop = ay;
        int maxRight = ax + Math.max(title.width + 2 * CONTAINER_PAD, MIN_CONTAINER_W);
        int maxBottom = contentY + CONTAINER_PAD;
        for (DeployNode c : n.getChildren()) {
            Rectangle cr = layout(c, contentX, contentY, sizer, out, origins);
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

    /**
     * {@code target} の絶対原点 (ドラッグ時に相対座標へ変換する基準点)。最上位なら (0,0)。
     * {@code contentOrigins} は {@link #computeContentOrigins} の結果を渡すこと
     * (containerRect ベースの逆算は負座標子があると原点がずれるため使わない。
     * bug-hunt round5 論点1)。
     */
    static Point contentOriginOf(DeployNode target, Map<DeployNode, Point> contentOrigins) {
        DeployNode parent = target.getParent();
        if (parent == null) {
            return new Point(0, 0);
        }
        Point origin = contentOrigins.get(parent);
        return origin != null ? origin : new Point(0, 0);
    }
}
