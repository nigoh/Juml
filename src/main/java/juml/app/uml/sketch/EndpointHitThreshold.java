// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.awt.Point;

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

    /** 点 {@code p} がハンドル {@code handle} から {@code thresholdModel} 以内か。 */
    static boolean within(Point p, Point handle, double thresholdModel) {
        return Math.hypot(p.x - handle.x, p.y - handle.y) <= thresholdModel;
    }
}
