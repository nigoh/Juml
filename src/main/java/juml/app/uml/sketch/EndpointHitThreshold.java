// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.awt.Point;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * 端点ハンドルの当たり判定半径を、ズーム倍率によらず画面上でおよそ一定 px に保つための
 * 純関数群 (Class/State/Object/Sequence の 4 キャンバスが共通で使う)。
 *
 * <p>press 座標は {@link SketchViewport#toModel} でモデル座標へ逆変換されるため、
 * ヒット判定半径をモデル座標の固定値のままにすると、縮小時 (最小 {@link
 * SketchViewport#MIN_ZOOM} = 0.25x) には画面上の当たり判定が zoom 倍に縮んでしまい、
 * 端点を掴みづらくなる (bug-hunt round3 指摘 H)。ER/UseCase/Component の 3 キャンバスは
 * 各クラス内の {@code handleThresholdModel(zoom)} で既に同じ変換を行っており、
 * ここではその意味論をそのまま共通ヘルパーへ切り出す (ER/UC/Comp 自体は変更しない)。</p>
 */
final class EndpointHitThreshold {

    private EndpointHitThreshold() {
    }

    /** 画面上 {@code baseRadiusPx} のハンドル当たり判定半径を、指定ズームでのモデル座標半径へ変換する。 */
    static double modelRadius(double baseRadiusPx, double zoom) {
        return baseRadiusPx / Math.max(1e-6, zoom);
    }

    /**
     * ハンドル描画の一辺 (画面上 {@code basePx} 一定) を、指定ズームでのモデル座標長へ変換する
     * (最低 1 を保証)。
     *
     * <p>{@link #modelRadius} と同じ「画面上 px 一定」の意味論を描画側にも適用するための
     * ヘルパー。修正前は 8 キャンバスすべてがハンドルをモデル座標の固定サイズ (6px 等) で
     * 描いていたため、既に zoom 倍されている描画用 {@code Graphics2D} 上ではハンドルの見た目が
     * ズームに比例して拡縮してしまい、ヒット半径 (画面上一定) と食い違っていた
     * (拡大時は見た目 &gt; 掴める範囲、縮小時はほぼ不可視なのに掴める。bug-hunt round7 #4)。
     * ここで {@code basePx / zoom} をモデル座標として描けば、zoom 倍後の見た目は
     * {@code basePx} 一定に戻る。</p>
     */
    static int handleSizeModel(int basePx, double zoom) {
        return Math.max(1, (int) Math.round(basePx / Math.max(1e-6, zoom)));
    }

    /** 点 {@code p} がハンドル {@code handle} から {@code thresholdModel} 以内か。 */
    static boolean within(Point p, Point handle, double thresholdModel) {
        return Math.hypot(p.x - handle.x, p.y - handle.y) <= thresholdModel;
    }

    /** {@link #nearestPair} が返すヒット結果 (要素と、2 端点のうち 1 つ目 ({@code first}) を
     * 掴んだか)。 */
    record Pick<T>(T item, boolean first) { }

    /**
     * {@code items} のうち {@code eligible} を満たす要素それぞれが持つ 2 端点
     * ({@code pointOf}, 第 2 引数が true なら 1 つ目・false なら 2 つ目) のうち、
     * {@code p} から {@code threshold} 以内で最も近い 1 つを大域探索で選ぶ。
     *
     * <p>「しきい値内で最初に一致した要素」を返す先勝ち判定だと、2 端点が近接する
     * 自己ループ/自己メッセージで意図した側と違う方が選ばれてしまう
     * (例: {@link SeqSketchCanvas} の自己メッセージは 2 端点が約 14.6px しか離れず、
     * 縮小時にしきい値がこれを超えると終点を掴んでも始点が先勝ちしていた。
     * bug-hunt round5 論点2)。見つからなければ null。</p>
     */
    static <T> Pick<T> nearestPair(List<T> items, Predicate<T> eligible,
            BiFunction<T, Boolean, Point> pointOf, Point p, double threshold) {
        T bestItem = null;
        boolean bestFirst = true;
        double bestD = threshold;
        for (T item : items) {
            if (!eligible.test(item)) {
                continue;
            }
            for (boolean first : new boolean[]{true, false}) {
                double d = p.distance(pointOf.apply(item, first));
                if (d < bestD) {
                    bestD = d;
                    bestItem = item;
                    bestFirst = first;
                }
            }
        }
        return bestItem == null ? null : new Pick<>(bestItem, bestFirst);
    }
}
