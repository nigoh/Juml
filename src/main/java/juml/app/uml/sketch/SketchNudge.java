// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.awt.event.KeyEvent;

/**
 * 図形デザイナー共通の矢印キー移動 (nudge) の移動量計算。
 *
 * <p>選択要素をキーボードで精密配置するための共通ロジック: 通常は 1px、
 * {@code Shift} 押下でグリッド 1 マスぶん移動する。各キャンバスが
 * {@code keyPressed} からこの計算を呼び、自身の選択要素へ適用する。</p>
 */
final class SketchNudge {

    private SketchNudge() {
    }

    /**
     * 矢印キーの移動量 {@code {dx, dy}} を返す。矢印キー以外は {@code null}。
     *
     * @param keyCode {@link KeyEvent#getKeyCode()}
     * @param shift   Shift 押下時はグリッド単位で移動する
     * @param grid    キャンバスのグリッド間隔 (px)
     */
    static int[] deltaFor(int keyCode, boolean shift, int grid) {
        int step = shift ? grid : 1;
        switch (keyCode) {
            case KeyEvent.VK_LEFT:  return new int[]{-step, 0};
            case KeyEvent.VK_RIGHT: return new int[]{step, 0};
            case KeyEvent.VK_UP:    return new int[]{0, -step};
            case KeyEvent.VK_DOWN:  return new int[]{0, step};
            default:                return null;
        }
    }
}
