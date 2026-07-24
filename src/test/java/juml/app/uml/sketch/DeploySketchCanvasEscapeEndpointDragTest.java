// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * {@link DeploySketchCanvas} で端点ドラッグ中に Esc を押すと繋ぎ替えずに中断されることを検証する
 * (bug-hunt round 1: Deploy: Esc で端点ドラッグが中断する)。
 *
 * <p>他 7 図種のキャンバス (Class/Object/State/UseCase/Component/ER/Seq) はいずれも Esc で
 * 端点ドラッグを {@code cancelEndpointDrag} するが、修正前の配置図キャンバスは Esc を
 * リンク作成モード解除にしか使っておらず、端点ドラッグ中は無視 (no-op) していた。そのため
 * Esc を押した後にマウスを離すと、まだドラッグ中のまま扱われて繋ぎ替えが確定してしまって
 * いた。ここでは (1) Esc 直後に {@link DeploySketchCanvas#endpointDragLinkForTest()} が
 * null に戻ること、(2) その後の release では別ノード上であっても繋ぎ替えが起きず
 * {@code modelEdited} も飛ばないこと、を実際の press/drag/[Esc]/release 経路で確認する。
 * 合成 {@link MouseEvent} と、非表示コンポーネントへ確実に届く {@link KeyListener} 直接起動は
 * {@link DeploySketchLinkReattachTest} / {@code SketchCanvasEndpointReattachTest} の作法を
 * 踏襲する。</p>
 */
public class DeploySketchCanvasEscapeEndpointDragTest {

    private final AtomicInteger edits = new AtomicInteger();
    private DeploySketchCanvas canvas;
    private DeploySketchModel model;
    private DeployNode a;
    private DeployNode b;
    private DeployNode c;
    private DeployLink link;

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
        a = new DeployNode(DeployNode.Kind.NODE, "A", null, 40, 40);
        b = new DeployNode(DeployNode.Kind.DATABASE, "B", null, 260, 40);
        c = new DeployNode(DeployNode.Kind.CLOUD, "C", null, 40, 220);
        model.getNodes().add(a);
        model.getNodes().add(b);
        model.getNodes().add(c);
        link = new DeployLink("A", DeployLink.Kind.ARROW, "B", "JDBC");
        model.getLinks().add(link);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSize(600, 500);
            canvas.requestFocusInWindow();
        });
    }

    private void dispatch(int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    @Test
    public void escapeDuringEndpointDrag_cancelsAndLaterReleaseDoesNotReattach() {
        Rectangle bRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(b));
        Rectangle cRect = GuiActionRunner.execute(() -> canvas.layoutForTest().get(c));
        // to 側 (B に近い端) のハンドル位置: B の左辺中央付近。
        int handleX = bRect.x;
        int handleY = bRect.y + bRect.height / 2;
        // Esc 後にリリースする先: 別ノード C の中央 (以前の実装ではここへ繋ぎ替わっていた)。
        int targetX = cRect.x + cRect.width / 2;
        int targetY = cRect.y + cRect.height / 2;

        dispatch(MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                handleX, handleY, MouseEvent.BUTTON1);
        assertSame("端点ドラッグが開始しているはず", link,
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));

        dispatch(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK, targetX, targetY, 0);

        // 非表示/未フォーカスのコンポーネントへの dispatchEvent は KeyboardFocusManager に
        // 横取りされて届かないため、登録済みリスナーを EDT 上で直接起動する
        // (SketchCanvasEndpointReattachTest と同じ手法)。
        GuiActionRunner.execute(() -> {
            KeyEvent ke = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                    KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            for (KeyListener kl : canvas.getKeyListeners()) {
                kl.keyPressed(ke);
            }
        });

        assertNull("Esc で端点ドラッグは中断されるはず",
                GuiActionRunner.execute(canvas::endpointDragLinkForTest));

        // Esc 後に release しても、修正前の実装ではここで C への繋ぎ替えが確定していた。
        dispatch(MouseEvent.MOUSE_RELEASED, 0, targetX, targetY, MouseEvent.BUTTON1);

        assertEquals("Esc 後の release で to 側は変わらないはず", "B", link.getTo());
        assertEquals("Esc 後の release で from 側も変わらないはず", "A", link.getFrom());
        assertEquals("繋ぎ替えは起きていないので modelEdited は飛ばないはず", 0, edits.get());
    }
}
