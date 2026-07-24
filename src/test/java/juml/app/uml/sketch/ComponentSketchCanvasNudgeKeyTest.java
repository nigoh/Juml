// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt round3 High 指摘の回帰テスト: {@link ComponentSketchCanvas} の矢印キー nudge を
 * 実 {@link KeyEvent} dispatch 経由で検証する。
 *
 * <p>{@code SketchNudge.deltaFor} は純関数として、{@code nudgeSelected} も直叩きで検証済み
 * だが、両者をつなぐ {@code keyPressed} ラッパ ({@code editable && selected != null &&
 * relationMode == null} ガード → {@code SketchNudge.deltaFor} → {@code nudgeSelected} →
 * {@code e.consume()}) は他 8 キャンバスと同様コピペで個別実装されており、モードガードの
 * 変数取り違えや {@code e.consume()} 漏れを検出できていなかった。オフスクリーンの canvas は
 * フォーカスを持てず実キー dispatch できないため、登録済み {@link KeyListener} を EDT 上で
 * 直接起動する ({@code ActivitySketchCanvasTest#pressDeleteKey} と同じ手法)。</p>
 */
public class ComponentSketchCanvasNudgeKeyTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static ComponentSketchCanvas.Listener noopListener(AtomicInteger edits) {
        return new ComponentSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(ComponentNode n) {
            }
        };
    }

    private static ComponentSketchCanvas newCanvas(ComponentSketchCanvas.Listener listener) {
        return GuiActionRunner.execute(() -> new ComponentSketchCanvas(listener));
    }

    /** 登録済み KeyListener を EDT 上で直接起動する (オフスクリーンはフォーカス不可のため)。 */
    private static KeyEvent pressArrowKey(ComponentSketchCanvas canvas, int keyCode, boolean shift) {
        int modifiers = shift ? KeyEvent.SHIFT_DOWN_MASK : 0;
        KeyEvent ke = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
        GuiActionRunner.execute(() -> {
            for (KeyListener kl : canvas.getKeyListeners()) {
                kl.keyPressed(ke);
            }
        });
        return ke;
    }

    @Test
    public void arrowKey_movesSelectedNode_rightAndDown_andConsumesEvent() {
        AtomicInteger edits = new AtomicInteger();
        ComponentSketchCanvas canvas = newCanvas(noopListener(edits));
        ComponentNode a = new ComponentNode(ComponentNode.Kind.COMPONENT, "A", null, 40, 40);
        ComponentSketchModel model = new ComponentSketchModel();
        model.getNodes().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.setSelectedForTest(a);
        });

        KeyEvent right = pressArrowKey(canvas, KeyEvent.VK_RIGHT, false);
        assertEquals("VK_RIGHT で x が 1px 動くはず", 41, a.getX());
        assertEquals(40, a.getY());
        assertTrue("矢印キー消費で e.consume() が呼ばれるはず", right.isConsumed());

        KeyEvent down = pressArrowKey(canvas, KeyEvent.VK_DOWN, true);
        assertEquals("Shift+VK_DOWN でグリッド単位 (8px) 動くはず", 48, a.getY());
        assertEquals(41, a.getX());
        assertTrue(down.isConsumed());

        assertEquals("2 回の nudge で modelEdited が 2 回飛ぶはず", 2, edits.get());
    }

    @Test
    public void arrowKey_ignoredWhenNotEditable() {
        AtomicInteger edits = new AtomicInteger();
        ComponentSketchCanvas canvas = newCanvas(noopListener(edits));
        ComponentNode a = new ComponentNode(ComponentNode.Kind.COMPONENT, "A", null, 40, 40);
        ComponentSketchModel model = new ComponentSketchModel();
        model.getNodes().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, false, List.of("未対応行"));
            canvas.setSelectedForTest(a);
        });

        pressArrowKey(canvas, KeyEvent.VK_RIGHT, false);

        assertEquals("ロック中は矢印キーで動かないはず", 40, a.getX());
        assertEquals(0, edits.get());
    }

    @Test
    public void arrowKey_ignoredWhileRelationModeActive() {
        AtomicInteger edits = new AtomicInteger();
        ComponentSketchCanvas canvas = newCanvas(noopListener(edits));
        ComponentNode a = new ComponentNode(ComponentNode.Kind.COMPONENT, "A", null, 40, 40);
        ComponentSketchModel model = new ComponentSketchModel();
        model.getNodes().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            // setRelationMode は selected をクリアするため、モード ON の後に強制選択する
            // (モードガード自体 (コピペ取り違え) を単体で検証するため)。
            canvas.setRelationMode(ComponentRelation.Kind.ARROW);
            canvas.setSelectedForTest(a);
        });

        pressArrowKey(canvas, KeyEvent.VK_RIGHT, false);

        assertEquals("関係追加モード中は矢印キーで動かないはず", 40, a.getX());
        assertEquals(0, edits.get());
    }
}
