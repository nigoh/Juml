// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * GUI デザイナー上の状態 1 個分の可変データ (名前 + キャンバス座標)。
 *
 * <p>初期/終了の擬似状態 {@code [*]} は状態ノードとしては保持せず、
 * {@link StateTransition} の端点名 {@code "[*]"} として表す。</p>
 */
public final class StateNode {

    private String name;
    private int x;
    private int y;

    public StateNode(String name, int x, int y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void moveTo(int newX, int newY) {
        this.x = newX;
        this.y = newY;
    }
}
