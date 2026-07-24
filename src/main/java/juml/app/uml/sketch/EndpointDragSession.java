// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.awt.Point;

/**
 * 関係線/リンクの端点ドラッグ (掴んで別ノードへ繋ぎ替える操作) に共通する状態管理と、
 * 「クリック相当 (実質移動なし)」「自ノードへの落下 (no-op)」を弾くガード判定。
 *
 * <p>端点ハンドルはノード境界上にあり、ハンドル自体はノード内側に数px 重なる
 * (矩形の当たり判定は左/上辺 inclusive) ため、ハンドルを掴んで動かさずに離す
 * だけの「クリック」でも従来は繋ぎ替えが確定してしまっていた。このクラスは
 * press 位置を保持し、release がしきい値未満の移動量であれば「クリック」として
 * 繋ぎ替えを行わない。また繋ぎ替え先が「掴んだ端の現在のノード」と同一の場合も
 * no-op として弾く (2 重の防御)。</p>
 *
 * <p>8 キャンバス (Class/State/Object/UseCase/Component/ER/Sequence/Deploy) それぞれで
 * ドラッグ対象の型 (関係/リンク) が異なるため型パラメータ {@code T} を持つ。各キャンバスの
 * finishEndpointDrag 相当のメソッドは {@link #finish} の戻り値だけで実際に繋ぎ替えて
 * よいか判定できる (自己ループ禁止などの図種固有ルールは呼び出し側が別途適用する)。</p>
 */
final class EndpointDragSession<T> {

    /** 「クリック相当」とみなす移動距離のしきい値 (モデル座標, px)。 */
    static final double CLICK_THRESHOLD_PX = 4.0;

    private T item;
    private boolean leftEnd;
    private Point pressPoint;
    private Point cursor;

    /** 端点ハンドルの press: ドラッグ対象・掴んだ側・press 位置 (モデル座標) を記録する。 */
    void start(T item, boolean leftEnd, Point at) {
        this.item = item;
        this.leftEnd = leftEnd;
        this.pressPoint = at;
        this.cursor = at;
    }

    /** ドラッグ中のカーソル移動 (ラバーバンド描画用)。 */
    void updateCursor(Point at) {
        this.cursor = at;
    }

    /** 端点ドラッグ中か。 */
    boolean isActive() {
        return item != null;
    }

    /** ドラッグ中の対象 (端点ドラッグ中でなければ null)。 */
    T item() {
        return item;
    }

    /** true なら始点側、false なら終点側を掴んでいる。 */
    boolean leftEnd() {
        return leftEnd;
    }

    /** ラバーバンド線の先端 (現在のカーソル位置、モデル座標。ドラッグ中でなければ null)。 */
    Point cursor() {
        return cursor;
    }

    /** Esc/モード切替時にモデル変更なしで端点ドラッグを中断する。 */
    void cancel() {
        item = null;
        pressPoint = null;
        cursor = null;
    }

    /**
     * リリース時、実際に繋ぎ替えるべきかを判定してドラッグ状態を終了する。
     * press からの移動量がしきい値未満 (クリック相当) の場合や、繋ぎ替え先
     * ({@code targetName}) が掴んだ端の現在のノード ({@code currentName}) と同一
     * (no-op) の場合は false を返す。呼び出し側はこの戻り値が true のときだけ
     * 実際のモデル変更 (と modelEdited 通知) を行うこと。
     *
     * <p>{@code zoom} でクリック/ドラッグしきい値もズーム補正する ({@link
     * EndpointHitThreshold#modelRadius} と同じ画面 px 意味論)。以前はモデル座標の
     * 固定値のままだったため、当たり判定 (ズーム補正済み) と意味論が食い違い、
     * 縮小時は「クリック」と判定されやすく拡大時は微小移動でもドラッグ扱いされていた
     * (bug-hunt round5 論点4)。</p>
     */
    boolean finish(Point release, String targetName, String currentName, double zoom) {
        double threshold = EndpointHitThreshold.modelRadius(CLICK_THRESHOLD_PX, zoom);
        boolean reattach = pressPoint != null && release != null
                && release.distance(pressPoint) >= threshold
                && targetName != null && !targetName.equals(currentName);
        item = null;
        pressPoint = null;
        cursor = null;
        return reattach;
    }
}
