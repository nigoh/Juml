// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt round10 の回帰テスト: 配置図デザイナーでリンク追加モード中に始点ノードを
 * 右クリック削除すると、修正前は {@code linkSource} が削除済みインスタンスを
 * 指したまま残り、次にクリックしたノードとの間に削除済みノードを端点に持つ宙吊りの
 * リンクが生成されていた (往復 PlantUML にも宣言のない幽霊ノードが復活する)。
 *
 * <p>配置図はコンテナ削除で子孫もまとめて消える (カスケード削除) ため、修正は
 * 削除対象の部分木に {@code linkSource} の祖先を辿って含まれるかを見る
 * ({@code for (DeployNode n = linkSource; n != null; n = n.getParent())})。
 * {@link #deletingContainerWhoseChildIsLinkSource_clearsPendingSource()} はその
 * 祖先チェーンのケースを固定する。</p>
 */
public class DeploySketchCanvasDeleteClearsPendingSourceTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成/ポップアップ表示が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static DeploySketchCanvas.Listener noopListener(AtomicInteger edits) {
        return new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editNodeRequested(DeployNode n) {
            }
        };
    }

    private static Point centerOf(DeploySketchCanvas canvas, DeployNode n) {
        Map<DeployNode, Rectangle> layout = GuiActionRunner.execute(canvas::layoutForTest);
        Rectangle r = layout.get(n);
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /**
     * コンテナのタイトル行 (子ノード領域より上、必ず子と重ならない帯) 内の 1 点。
     * コンテナ矩形の中心は子ノードの矩形と重なりうる ({@link DeploySketchLayout} は
     * コンテナが子を包むよう自動的に広がるため) ので、コンテナ自身を確実にヒットさせたい
     * 右クリックにはこちらを使う ({@code hitTest} は子を先に判定するため、中心点が子と
     * 重なっていると意図せず子がヒットしてしまう)。
     */
    private static Point titleAreaPointOf(DeploySketchCanvas canvas, DeployNode n) {
        Map<DeployNode, Rectangle> layout = GuiActionRunner.execute(canvas::layoutForTest);
        Rectangle r = layout.get(n);
        return new Point(r.x + 8, r.y + 8);
    }

    private static void rightClick(DeploySketchCanvas canvas, Point p) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.BUTTON3_DOWN_MASK, p.x, p.y, 1, true, MouseEvent.BUTTON3)));
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

    // -------------------------------------------------------------------------
    // (a) トップレベルノードを削除する基本ケース
    // -------------------------------------------------------------------------

    @Test
    public void deletingLinkSourceViaRightClick_clearsPendingSource() {
        AtomicInteger edits = new AtomicInteger();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode a = new DeployNode(DeployNode.Kind.NODE, "A", null, 40, 40);
        DeployNode b = new DeployNode(DeployNode.Kind.NODE, "B", null, 300, 40);
        DeployNode c = new DeployNode(DeployNode.Kind.NODE, "C", null, 300, 220);
        model.getNodes().add(a);
        model.getNodes().add(b);
        model.getNodes().add(c);

        JFrame[] frameHolder = new JFrame[1];
        DeploySketchCanvas canvas = GuiActionRunner.execute(() -> {
            DeploySketchCanvas cv = new DeploySketchCanvas(noopListener(edits));
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
            GuiActionRunner.execute(() -> canvas.setLinkMode(DeployLink.Kind.ARROW));
            // A をクリックして始点 (linkSource) を確定する。
            GuiActionRunner.execute(() -> canvas.linkClickForTest(a));

            // A を右クリックして削除メニューを出す。
            Point centerA = centerOf(canvas, a);
            rightClick(canvas, centerA);
            JPopupMenu popup = GuiActionRunner.execute(() -> findPopupInContainer(frameHolder[0]));
            assertNotNull("右クリックでポップアップメニューが出るはず", popup);
            JMenuItem delete = findMenuItem(popup, Messages.get("sketch.depl.menu.delete"));
            assertNotNull("削除メニュー項目が見つかるはず", delete);
            GuiActionRunner.execute(() -> {
                delete.doClick();
                return null;
            });

            assertEquals("A が削除され B, C のみ残るはず", 2, model.getNodes().size());

            // 削除済み A が pending source のまま残っていれば、次に B をクリックした瞬間に
            // A --> B の宙吊りリンクが完成してしまう (round10 の修正前バグ)。
            GuiActionRunner.execute(() -> canvas.linkClickForTest(b));
            assertTrue("削除済み A を端点に持つ宙吊りリンクが生成されないはず",
                    model.getLinks().isEmpty());

            // B が新しい始点として正しく確定していることを、C クリックでのリンク完成で確認する。
            GuiActionRunner.execute(() -> canvas.linkClickForTest(c));
            assertEquals("B->C のリンクが 1 本だけ生成されるはず", 1, model.getLinks().size());
            DeployLink created = model.getLinks().get(0);
            assertEquals("B", created.getFrom());
            assertEquals("C", created.getTo());

            String puml = GuiActionRunner.execute(() -> DeploySketchCodec.toPuml(model));
            assertFalse("再生成 PlantUML に削除済み A への参照 (幽霊ノード) が出ないはず: " + puml,
                    puml.contains("A"));
        } finally {
            GuiActionRunner.execute(() -> {
                MenuSelectionManager.defaultManager().clearSelectedPath();
                frameHolder[0].dispose();
            });
        }
    }

    // -------------------------------------------------------------------------
    // (b) カスケード削除: コンテナを削除すると子ごと消える。子が linkSource だったケース。
    // -------------------------------------------------------------------------

    @Test
    public void deletingContainerWhoseChildIsLinkSource_clearsPendingSource() {
        AtomicInteger edits = new AtomicInteger();
        DeploySketchModel model = new DeploySketchModel();
        DeployNode outer = new DeployNode(DeployNode.Kind.NODE, "Outer", null, 40, 40);
        DeployNode child = new DeployNode(DeployNode.Kind.NODE, "Child", null, 10, 10);
        model.getNodes().add(outer);
        model.addChild(outer, child); // outer をコンテナ化し child を中に入れる
        DeployNode b = new DeployNode(DeployNode.Kind.NODE, "B", null, 420, 40);
        DeployNode c = new DeployNode(DeployNode.Kind.NODE, "C", null, 420, 260);
        model.getNodes().add(b);
        model.getNodes().add(c);

        JFrame[] frameHolder = new JFrame[1];
        DeploySketchCanvas canvas = GuiActionRunner.execute(() -> {
            DeploySketchCanvas cv = new DeploySketchCanvas(noopListener(edits));
            cv.setModel(model, true, List.of());
            cv.setSize(700, 500);
            JFrame f = new JFrame();
            f.add(cv);
            f.setSize(720, 540);
            f.setLocation(0, 0);
            f.setVisible(true);
            frameHolder[0] = f;
            return cv;
        });
        try {
            GuiActionRunner.execute(() -> canvas.setLinkMode(DeployLink.Kind.ARROW));
            // child (Outer の中身) をクリックして始点 (linkSource) を確定する。
            GuiActionRunner.execute(() -> canvas.linkClickForTest(child));

            // Outer (child の親コンテナ) を右クリックして削除メニューを出す。
            // Outer を削除すると child も一緒に消える (カスケード削除)。コンテナ矩形の中心は
            // 子ノードの矩形と重なるため、確実に Outer 自身をヒットさせるタイトル行の点を使う。
            Point titleOfOuter = titleAreaPointOf(canvas, outer);
            rightClick(canvas, titleOfOuter);
            JPopupMenu popup = GuiActionRunner.execute(() -> findPopupInContainer(frameHolder[0]));
            assertNotNull("右クリックでポップアップメニューが出るはず", popup);
            JMenuItem delete = findMenuItem(popup, Messages.get("sketch.depl.menu.delete"));
            assertNotNull("削除メニュー項目が見つかるはず", delete);
            GuiActionRunner.execute(() -> {
                delete.doClick();
                return null;
            });

            assertEquals("Outer (と子の Child) が削除され B, C のみ残るはず",
                    2, model.getNodes().size());
            assertNull(model.findNode("Outer"));
            assertNull(model.findNode("Child"));

            // 削除済み child (Outer の子孫として消えた) が pending source のまま残っていれば、
            // 次に B をクリックした瞬間に Child --> B の宙吊りリンクが完成してしまう
            // (round10 の修正前バグ: カスケード削除では削除対象そのものではなく祖先を辿って
            // pending source が含まれるか確認する必要がある)。
            GuiActionRunner.execute(() -> canvas.linkClickForTest(b));
            assertTrue("削除済み Child を端点に持つ宙吊りリンクが生成されないはず",
                    model.getLinks().isEmpty());

            // B が新しい始点として正しく確定していることを、C クリックでのリンク完成で確認する。
            GuiActionRunner.execute(() -> canvas.linkClickForTest(c));
            assertEquals("B->C のリンクが 1 本だけ生成されるはず", 1, model.getLinks().size());
            DeployLink created = model.getLinks().get(0);
            assertEquals("B", created.getFrom());
            assertEquals("C", created.getTo());

            String puml = GuiActionRunner.execute(() -> DeploySketchCodec.toPuml(model));
            assertFalse("再生成 PlantUML に削除済み Child への参照 (幽霊ノード) が出ないはず: " + puml,
                    puml.contains("Child"));
        } finally {
            GuiActionRunner.execute(() -> {
                MenuSelectionManager.defaultManager().clearSelectedPath();
                frameHolder[0].dispose();
            });
        }
    }
}
