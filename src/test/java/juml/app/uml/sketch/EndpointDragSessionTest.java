// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.awt.Point;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link EndpointDragSession} のクリック/ドラッグ判定しきい値を検証する (純ロジック、
 * Swing/Graphics2D 不要、headless でも実行できる)。
 *
 * <p>{@code CLICK_THRESHOLD_PX} (4px 相当) はモデル座標の固定値のまま比較されていたため、
 * 当たり判定 ({@link EndpointHitThreshold#modelRadius} でズーム補正済み) と意味論が食い違い、
 * 縮小時 (画面上は大きく動いてもモデル座標では小さい移動量) には「クリック」と誤判定
 * されやすく、拡大時 (画面上はわずかでもモデル座標では大きい移動量) には微小移動でも
 * ドラッグ扱いされていた (bug-hunt round5 論点4)。{@link EndpointDragSession#finish} に
 * zoom を渡してしきい値も画面 px で一定になるよう補正したことを固定する。</p>
 */
public class EndpointDragSessionTest {

    @Test
    public void finish_zoom1_matchesLegacyFourModelPxThreshold() {
        EndpointDragSession<String> session = new EndpointDragSession<>();
        session.start("A", true, new Point(0, 0));
        // モデル座標で 3px の移動 (等倍では画面上も 3px) はクリック相当。
        assertFalse("3px の移動はクリック相当のはず",
                session.finish(new Point(3, 0), "B", "A", 1.0));

        session.start("A", true, new Point(0, 0));
        // 5px の移動は従来どおりドラッグ相当。
        assertTrue("5px の移動はドラッグ相当のはず",
                session.finish(new Point(5, 0), "B", "A", 1.0));
    }

    @Test
    public void finish_zoomedOut_scalesThresholdSoScreenPxStaysConstant() {
        // 0.25x (最小ズーム) ではしきい値が画面 px 一定のため 4/0.25=16 モデル px まで
        // 「クリック」として扱うはず (修正前はモデル座標 4px 固定のままで、画面上ではごく
        // わずかな移動でも「ドラッグ」として繋ぎ替えが確定してしまっていた)。
        EndpointDragSession<String> session = new EndpointDragSession<>();
        session.start("A", true, new Point(0, 0));
        assertFalse("0.25x では 10px 移動してもクリック相当のはず (画面上は 2.5px 相当)",
                session.finish(new Point(10, 0), "B", "A", 0.25));

        session.start("A", true, new Point(0, 0));
        assertTrue("0.25x でもしきい値 (16px) を超えればドラッグ相当のはず",
                session.finish(new Point(20, 0), "B", "A", 0.25));
    }

    @Test
    public void finish_zoomedIn_shrinksThresholdSoScreenPxStaysConstant() {
        // 3.0x (最大ズーム) ではしきい値が 4/3.0 ≈ 1.33 モデル px まで縮み、修正前は逆に
        // モデル座標 4px 固定のままだったため画面上ではかなり大きく動かさないと
        // ドラッグと判定されず、拡大時に付替えづらかった。
        EndpointDragSession<String> session = new EndpointDragSession<>();
        session.start("A", true, new Point(0, 0));
        assertTrue("3.0x では 2px の移動でもドラッグ相当のはず (画面上は 6px 相当)",
                session.finish(new Point(2, 0), "B", "A", 3.0));
    }

    @Test
    public void finish_belowThreshold_doesNotClearNoOpGuard() {
        // しきい値未満の移動 (クリック相当) では、たとえ着地先が別ノードでも false を返す。
        EndpointDragSession<String> session = new EndpointDragSession<>();
        session.start("A", true, new Point(0, 0));
        assertFalse("クリック相当なら着地先が別ノードでも繋ぎ替えないはず",
                session.finish(new Point(1, 0), "C", "A", 1.0));
    }
}
