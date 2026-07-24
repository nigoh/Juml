// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * {@link DeploySketchCanvas} の実マウス経路 (press/drag/release, {@code addChildNode}) で、
 * 負の相対座標を持つ兄弟ノードがいても子ドラッグ/子追加が押下点どおりに配置され、座標が
 * ジャンプしないことを検証する (bug-hunt round5 論点1)。
 *
 * <p>Round3 の枠拡張修正 ({@link DeploySketchLayout#compute} が負座標の子を包含するよう
 * containerRect を左/上へ広げる) 後、子の実配置基準 (論理 content 原点) は変えていないのに、
 * 修正前の {@code contentOriginOf}/{@code contentOrigin} は「広がった後」の containerRect
 * から原点を逆算していたため、負座標の兄弟がいるコンテナでは子ドラッグ/子追加が
 * (ax - minLeft) 分だけジャンプしていた。ここでは負座標の子 (-30,-60) を持つコンテナに対して
 * 実際に別の子をドラッグ/追加し、ジャンプせず press 位置どおりになることを固定する。</p>
 */
public class DeploySketchCanvasNegativeChildOriginTest {

    private final AtomicInteger edits = new AtomicInteger();
    private DeploySketchCanvas canvas;
    private DeploySketchModel model;
    private DeployNode container;
    private DeployNode negChild;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        DeploySketchCanvas.Listener listener = new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(DeployNode n) {
            }
        };
        canvas = GuiActionRunner.execute(() -> new DeploySketchCanvas(listener));
        model = new DeploySketchModel();
        container = new DeployNode(DeployNode.Kind.NODE, "C", null, 0, 0);
        container.setContainer(true);
        // 手編集テキスト ('@pos 相当) で到達しうる負の相対座標を持つ既存の子。これがあると
        // containerRect が左/上へ広がる (round3 修正) が、論理 content 原点は変わらないはず。
        negChild = new DeployNode(DeployNode.Kind.NODE, "L1", null, -30, -60);
        container.getChildren().add(negChild);
        negChild.setParent(container);
        model.getNodes().add(container);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 500);
            // グリッド吸着 (既定 ON) は本題と無関係にドラッグ結果の座標を丸めてしまうため、
            // このテストでは無効にして原点計算そのものの正しさだけを検証する。
            canvas.setSnapToGrid(false);
        });
    }

    private void dispatch(int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    @Test
    public void dragExistingNegativeChild_movesByExactDragDeltaWithoutJump() {
        Rectangle before = GuiActionRunner.execute(() -> canvas.layoutForTest().get(negChild));
        // 子の絶対矩形の左上そのものを press 位置にする (dragOffset がちょうど (0,0) になり、
        // 期待される移動量の検証を単純にする)。
        int pressX = before.x;
        int pressY = before.y;
        int dx = 50;
        int dy = 80;

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, pressX, pressY,
                MouseEvent.BUTTON1);
        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK,
                pressX + dx, pressY + dy, 0);
        dispatch(MouseEvent.MOUSE_RELEASED, 0, pressX + dx, pressY + dy, MouseEvent.BUTTON1);

        Rectangle after = GuiActionRunner.execute(() -> canvas.layoutForTest().get(negChild));
        assertEquals("押下点どおりに x 方向へ移動するはず (座標がジャンプしないはず)",
                before.x + dx, after.x);
        assertEquals("押下点どおりに y 方向へ移動するはず (座標がジャンプしないはず)",
                before.y + dy, after.y);
    }

    @Test
    public void addChildNode_intoContainerWithNegativeSibling_placesAtPressPointWithoutJump() {
        // コンテナの論理 content 原点 (14,52) から (100,100) だけ離れた絶対位置をクリックした
        // ことを模す (負座標の兄弟がいても枠拡張の影響を受けないはず)。
        Point at = new Point(114, 152);

        GuiActionRunner.execute(() -> canvas.addChildNode(DeployNode.Kind.NODE, container, at));

        assertEquals("負座標の兄弟を追加しても子は 2 個になるはず", 2, container.getChildren().size());
        DeployNode added = container.getChildren().get(1);
        Rectangle r = GuiActionRunner.execute(() -> canvas.layoutForTest().get(added));
        assertEquals("新しい子は press 位置どおりに置かれるはず (x)", at.x, r.x);
        assertEquals("新しい子は press 位置どおりに置かれるはず (y)", at.y, r.y);
    }
}
