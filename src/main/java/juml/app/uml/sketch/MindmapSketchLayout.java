// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * マインドマップキャンバスのレイアウト計算 (純ロジック、Swing 描画に依存しない)。
 *
 * <p>ルートを中央に置き、直下の子を左右へ振り分けて水平に伸びる縦積みツリーとして
 * 配置する (アクティビティ図の縦フローを 90 度回転させた発想)。ルート直下の子の左右は
 * LEFT/RIGHT 明示ならその側、AUTO なら現在ノード数が少ない側へ貪欲割当 (同数なら右優先)。
 * 子孫は実効 side で親と同じ側に固定し、枝の途中で左右が反転しないようにする。決定的で
 * あることを優先し、PlantUML の実描画 (AUTO は実際には全て右へ縦積み) との一致は求めない
 * (この乖離は意図的。{@link MindmapSketchCanvas} 参照)。</p>
 */
final class MindmapSketchLayout {

    /** 盤面の左上マージン。 */
    static final int MARGIN = 30;
    /** 深さ 1 段あたりの<b>最小</b>水平ストライド。実際のストライドは最も広いノードが
     * はみ出さないよう {@link #columnStride} で下限としてのみ用いる。 */
    static final int COL_W = 170;
    /** 列間に必ず残す水平余白 (隣接列の矩形が重ならないための下駄。{@link #columnStride})。 */
    static final int COL_GAP = 28;
    /** 兄弟サブツリー間の縦の間隔。 */
    static final int V_GAP = 16;

    private MindmapSketchLayout() {
    }

    /** ノードの表示サイズを返す関数 (キャンバスの FontMetrics 依存)。 */
    interface Sizer {
        Dimension sizeOf(MindmapNode n);
    }

    /** レイアウト結果 (各ノードの絶対矩形と、右側かどうか)。 */
    static final class Result {
        final Map<MindmapNode, Rectangle> bounds;
        final Map<MindmapNode, Boolean> onRight;

        Result(Map<MindmapNode, Rectangle> bounds, Map<MindmapNode, Boolean> onRight) {
            this.bounds = bounds;
            this.onRight = onRight;
        }
    }

    /** 再帰配置で持ち回る不変コンテキスト (ルート X・列送り・サイザ・出力先マップ)。 */
    private record Ctx(int rootX, int stride, Sizer sizer,
                       Map<MindmapNode, Rectangle> bounds, Map<MindmapNode, Boolean> onRight) {
    }

    /** ルートから全ノードの絶対矩形を計算する (root==null なら空の結果)。 */
    static Result compute(MindmapNode root, Sizer sizer) {
        Map<MindmapNode, Rectangle> bounds = new IdentityHashMap<>();
        Map<MindmapNode, Boolean> onRight = new IdentityHashMap<>();
        if (root == null) {
            return new Result(bounds, onRight);
        }
        List<MindmapNode> left = new ArrayList<>();
        List<MindmapNode> right = new ArrayList<>();
        assignSides(root, left, right);

        // 列送り幅は最も広いノード + 余白まで広げ、どの深さでも隣接列の矩形が重ならない
        // ようにする (COL_W は下限)。これで長いラベルでも hitTest が別ノードを誤選択しない。
        int stride = columnStride(root, sizer);
        int leftHeight = bandHeight(left, sizer);
        int rightHeight = bandHeight(right, sizer);
        Dimension rootDim = sizer.sizeOf(root);
        int total = Math.max(rootDim.height, Math.max(leftHeight, rightHeight));
        int maxLeftDepth = maxDepth(left, 1);
        int rootX = MARGIN + maxLeftDepth * stride;
        int rootY = MARGIN + (total - rootDim.height) / 2;
        bounds.put(root, new Rectangle(rootX, rootY, rootDim.width, rootDim.height));
        onRight.put(root, Boolean.TRUE);

        Ctx ctx = new Ctx(rootX, stride, sizer, bounds, onRight);
        packBand(right, true, MARGIN + (total - rightHeight) / 2, ctx);
        packBand(left, false, MARGIN + (total - leftHeight) / 2, ctx);
        return new Result(bounds, onRight);
    }

    /**
     * 列送り幅 (深さ 1 段あたりの水平距離) を決める。全ノードの最大幅 + {@link #COL_GAP} を
     * {@link #COL_W} と比べて広い方を採る。各ノード幅 ≤ stride - COL_GAP が保証されるため、
     * 深さ d のノード右端は必ず深さ d+1 の列開始より COL_GAP 手前に収まり、隣接列は重ならない。
     */
    private static int columnStride(MindmapNode root, Sizer sizer) {
        return Math.max(COL_W, maxNodeWidth(root, sizer) + COL_GAP);
    }

    private static int maxNodeWidth(MindmapNode node, Sizer sizer) {
        int max = sizer.sizeOf(node).width;
        for (MindmapNode c : node.getChildren()) {
            max = Math.max(max, maxNodeWidth(c, sizer));
        }
        return max;
    }

    /** ルート直下の子を左右へ振り分ける (明示 side 優先、AUTO は少ない側へ貪欲・同数は右)。 */
    private static void assignSides(MindmapNode root, List<MindmapNode> left,
                                    List<MindmapNode> right) {
        for (MindmapNode c : root.getChildren()) {
            MindmapNode.Side s = c.getOwnSide();
            if (s == MindmapNode.Side.LEFT) {
                left.add(c);
            } else if (s == MindmapNode.Side.RIGHT) {
                right.add(c);
            } else if (right.size() <= left.size()) {
                right.add(c);
            } else {
                left.add(c);
            }
        }
    }

    private static void packBand(List<MindmapNode> children, boolean right, int bandTop, Ctx ctx) {
        int top = bandTop;
        for (MindmapNode c : children) {
            top = placeSubtree(c, right, 1, top, ctx) + V_GAP;
        }
    }

    /**
     * {@code node} のサブツリーを配置し、その占有帯の下端 Y を返す。ノードは自分の帯の中で
     * 縦方向中央に置く。子はさらに 1 段外側 (深さ +1) の列へ再帰的に置く。
     */
    private static int placeSubtree(MindmapNode node, boolean right, int depth, int top, Ctx ctx) {
        int h = subtreeHeight(node, ctx.sizer());
        Dimension d = ctx.sizer().sizeOf(node);
        int x = right ? ctx.rootX() + depth * ctx.stride() : ctx.rootX() - depth * ctx.stride();
        int y = top + (h - d.height) / 2;
        ctx.bounds().put(node, new Rectangle(x, y, d.width, d.height));
        ctx.onRight().put(node, right);
        int childTop = top;
        for (MindmapNode c : node.getChildren()) {
            childTop = placeSubtree(c, right, depth + 1, childTop, ctx) + V_GAP;
        }
        return top + h;
    }

    /** サブツリーの占有高さ (自身の高さと、全子サブツリー高さ + 間隔の大きい方)。 */
    private static int subtreeHeight(MindmapNode node, Sizer sizer) {
        int own = sizer.sizeOf(node).height;
        List<MindmapNode> kids = node.getChildren();
        if (kids.isEmpty()) {
            return own;
        }
        int sum = 0;
        for (MindmapNode c : kids) {
            sum += subtreeHeight(c, sizer);
        }
        sum += V_GAP * (kids.size() - 1);
        return Math.max(own, sum);
    }

    /** 兄弟一覧の合計占有高さ (空なら 0)。 */
    private static int bandHeight(List<MindmapNode> children, Sizer sizer) {
        if (children.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (MindmapNode c : children) {
            sum += subtreeHeight(c, sizer);
        }
        return sum + V_GAP * (children.size() - 1);
    }

    /** {@code children} 以下の最大深さ (ルート相対、children が深さ startDepth)。空なら 0。 */
    private static int maxDepth(List<MindmapNode> children, int startDepth) {
        int max = 0;
        for (MindmapNode c : children) {
            max = Math.max(max, Math.max(startDepth, maxDepth(c.getChildren(), startDepth + 1)));
        }
        return max;
    }

    /** {@code p} (絶対座標) にあるノードを返す (無ければ null)。矩形は重ならない前提。 */
    static MindmapNode hitTest(Result layout, Point p) {
        for (Map.Entry<MindmapNode, Rectangle> e : layout.bounds.entrySet()) {
            if (e.getValue().contains(p)) {
                return e.getKey();
            }
        }
        return null;
    }
}
