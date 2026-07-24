// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt round10 の回帰テスト: 状態遷移図デザイナーで遷移追加モード中に始点状態を
 * 右クリック削除すると、修正前は {@code transitionSource} が削除済みインスタンスを
 * 指したまま残り、次にクリックした状態との間に削除済み状態を端点に持つ宙吊りの遷移が
 * 生成されていた (往復 PlantUML にも宣言のない幽霊状態が復活する)。
 *
 * <p>{@link StateSketchCanvas} には関係クリック用のテストシームが無いため、実際の
 * 左クリック ({@link MouseEvent#MOUSE_PRESSED}) をノード中心へディスパッチして
 * {@code handleTransitionClick} を通す (プロダクションの導線をそのまま踏む)。</p>
 */
public class StateSketchCanvasDeleteClearsPendingSourceTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成/ポップアップ表示が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static StateSketchCanvas.Listener noopListener(AtomicInteger edits) {
        return new StateSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editStateRequested(StateNode s) {
            }
        };
    }

    private static void leftClick(StateSketchCanvas canvas, Point p) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, p.x, p.y, 1, false, MouseEvent.BUTTON1)));
    }

    @Test
    public void deletingTransitionSourceViaRightClick_clearsPendingSource() {
        AtomicInteger edits = new AtomicInteger();
        StateSketchModel model = new StateSketchModel();
        StateNode a = new StateNode("A", 40, 40);
        StateNode b = new StateNode("B", 300, 40);
        StateNode c = new StateNode("C", 300, 220);
        model.getStates().add(a);
        model.getStates().add(b);
        model.getStates().add(c);

        JFrame[] frameHolder = new JFrame[1];
        StateSketchCanvas canvas = GuiActionRunner.execute(() -> {
            StateSketchCanvas cv = new StateSketchCanvas(noopListener(edits));
            cv.setModel(model, true, List.of());
            cv.setSize(600, 400);
            JFrame f = new JFrame();
            f.add(cv);
            f.setSize(620, 440);
            f.setLocation(0, 0);
            f.setVisible(true);
            frameHolder[0] = f;
            return cv;
        });
        try {
            GuiActionRunner.execute(() -> canvas.setTransitionMode(true));
            Rectangle ra = GuiActionRunner.execute(() -> canvas.boundsOf(a));
            Point centerA = new Point(ra.x + ra.width / 2, ra.y + ra.height / 2);
            // A をクリックして始点 (transitionSource) を確定する。
            leftClick(canvas, centerA);

            // A を右クリックして削除メニューを出す。
            GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                    canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    InputEvent.BUTTON3_DOWN_MASK, centerA.x, centerA.y, 1, true, MouseEvent.BUTTON3)));
            JPopupMenu popup = GuiActionRunner.execute(() -> findPopupInContainer(frameHolder[0]));
            assertNotNull("右クリックでポップアップメニューが出るはず", popup);
            JMenuItem delete = findMenuItem(popup, Messages.get("sketch.menu.delete"));
            assertNotNull("削除メニュー項目が見つかるはず", delete);
            GuiActionRunner.execute(() -> {
                delete.doClick();
                return null;
            });

            assertEquals("A が削除され B, C のみ残るはず", 2, model.getStates().size());

            // 削除済み A が pending source のまま残っていれば、次に B をクリックした瞬間に
            // A --> B の宙吊り遷移が完成してしまう (round10 の修正前バグ)。
            Rectangle rb = GuiActionRunner.execute(() -> canvas.boundsOf(b));
            Point centerB = new Point(rb.x + rb.width / 2, rb.y + rb.height / 2);
            leftClick(canvas, centerB);
            assertTrue("削除済み A を端点に持つ宙吊り遷移が生成されないはず",
                    model.getTransitions().isEmpty());

            // B が新しい始点として正しく確定していることを、C クリックでの遷移完成で確認する。
            Rectangle rc = GuiActionRunner.execute(() -> canvas.boundsOf(c));
            Point centerC = new Point(rc.x + rc.width / 2, rc.y + rc.height / 2);
            leftClick(canvas, centerC);
            assertEquals("B->C の遷移が 1 本だけ生成されるはず", 1, model.getTransitions().size());
            StateTransition created = model.getTransitions().get(0);
            assertEquals("B", created.getFrom());
            assertEquals("C", created.getTo());

            String puml = GuiActionRunner.execute(() -> StateSketchCodec.toPuml(model));
            assertFalse("再生成 PlantUML に削除済み A への参照 (幽霊状態) が出ないはず: " + puml,
                    puml.contains("A"));
        } finally {
            GuiActionRunner.execute(() -> {
                MenuSelectionManager.defaultManager().clearSelectedPath();
                frameHolder[0].dispose();
            });
        }
    }

    private static JMenuItem findMenuItem(JPopupMenu popup, String label) {
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenuItem && label.equals(((JMenuItem) c).getText())) {
                return (JMenuItem) c;
            }
        }
        return null;
    }

    private static JPopupMenu findPopupInContainer(Window w) {
        return findPopupInContainer((Container) w);
    }

    private static JPopupMenu findPopupInContainer(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JPopupMenu) {
                return (JPopupMenu) child;
            }
            if (child instanceof Container) {
                JPopupMenu p = findPopupInContainer((Container) child);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }
}
