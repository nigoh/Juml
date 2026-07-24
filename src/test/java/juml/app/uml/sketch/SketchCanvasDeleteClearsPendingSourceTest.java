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
 * bug-hunt round10 の回帰テスト: クラス図デザイナーで関係追加モード中に始点クラスを
 * 右クリック削除すると、修正前は {@code relationSource} が削除済みインスタンスを
 * 指したまま残り、次にクリックしたクラスとの間に削除済みクラスを端点に持つ宙吊りの
 * 関係が生成されていた (往復 PlantUML にも宣言のない幽霊クラスが復活する)。
 *
 * <p>修正後は右クリックメニューの「削除」ラムダが削除対象 == relationSource のとき
 * source を null にするため、次のクリックは新しい始点として扱われる。</p>
 */
public class SketchCanvasDeleteClearsPendingSourceTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成/ポップアップ表示が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static SketchCanvas.Listener noopListener(AtomicInteger edits) {
        return new SketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editRequested(SketchClass c) {
            }

            @Override public void addClassRequested(Point at) {
            }
        };
    }

    @Test
    public void deletingRelationSourceViaRightClick_clearsPendingSource() {
        AtomicInteger edits = new AtomicInteger();
        SketchModel model = new SketchModel();
        SketchClass a = new SketchClass("A", SketchClass.Kind.CLASS, 40, 40);
        SketchClass b = new SketchClass("B", SketchClass.Kind.CLASS, 300, 40);
        SketchClass c = new SketchClass("C", SketchClass.Kind.CLASS, 300, 220);
        model.getClasses().add(a);
        model.getClasses().add(b);
        model.getClasses().add(c);

        JFrame[] frameHolder = new JFrame[1];
        SketchCanvas canvas = GuiActionRunner.execute(() -> {
            SketchCanvas cv = new SketchCanvas(noopListener(edits));
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
            GuiActionRunner.execute(() -> canvas.setRelationMode(SketchRelation.Kind.ASSOCIATION));
            // A をクリックして始点 (relationSource) を確定する。
            GuiActionRunner.execute(() -> canvas.relationClickForTest(a));

            // A を右クリックして削除メニューを出す。
            Rectangle ra = GuiActionRunner.execute(() -> canvas.boundsOf(a));
            Point centerA = new Point(ra.x + ra.width / 2, ra.y + ra.height / 2);
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

            assertEquals("A が削除され B, C のみ残るはず", 2, model.getClasses().size());

            // 削除済み A が pending source のまま残っていれば、次に B をクリックした瞬間に
            // A --> B の宙吊り関係が完成してしまう (round10 の修正前バグ)。修正後は削除で
            // source がリセットされているので、B クリックは新しい始点ピックになるだけのはず。
            GuiActionRunner.execute(() -> canvas.relationClickForTest(b));
            assertTrue("削除済み A を端点に持つ宙吊り関係が生成されないはず",
                    model.getRelations().isEmpty());

            // B が新しい始点として正しく確定していることを、C クリックでの関係完成で確認する。
            GuiActionRunner.execute(() -> canvas.relationClickForTest(c));
            assertEquals("B->C の関係が 1 本だけ生成されるはず", 1, model.getRelations().size());
            SketchRelation created = model.getRelations().get(0);
            assertEquals("B", created.getLeft());
            assertEquals("C", created.getRight());

            String puml = GuiActionRunner.execute(() -> SketchPumlCodec.toPuml(model));
            assertFalse("再生成 PlantUML に削除済み A への参照 (幽霊クラス) が出ないはず: " + puml,
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

    /** 表示中のウィンドウ配下から {@link JPopupMenu} を探す (UmlMainFrameRightClickIT と同じ手法)。 */
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
