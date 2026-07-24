// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

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
 * bug-hunt round8 #1 の回帰テスト: 配置図デザイナーで入れ子コンテナを新規作成する
 * 唯一の GUI 導線 (右クリック「ここに子ノードを追加」) が {@code hit.isContainer()}
 * ガードにより葉ノード (非コンテナ) では一切出せず、テキストを手書きしない限り
 * 第1階層のネストを新規作成できなかった不具合を検証する。
 *
 * <p>{@link DeploySketchCanvas#addChildNode} 自体は葉/コンテナを問わず動作し、
 * {@link DeploySketchModel#addChild} 経由で親を {@code setContainer(true)} する。
 * (a) はその経路の結果 (コンテナ化 + 往復保存/復元) を、(b) はガードを外した後の
 * 右クリックメニューの見た目 (公開挙動、リフレクション不使用) を検証する。</p>
 */
public class DeploySketchCanvasAddChildToLeafTest {

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

    // --- (a) addChildNode は葉ノードもコンテナ化し、往復でネストとして保存/復元される ------

    @Test
    public void addChildNode_onLeafNode_makesItContainerWithOneChild() {
        AtomicInteger edits = new AtomicInteger();
        DeploySketchCanvas canvas =
                GuiActionRunner.execute(() -> new DeploySketchCanvas(noopListener(edits)));
        DeploySketchModel model = new DeploySketchModel();
        DeployNode leaf = new DeployNode(DeployNode.Kind.NODE, "Leaf", null, 40, 40);
        model.getNodes().add(leaf);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        assertFalse("追加前は葉ノード (非コンテナ) のはず", leaf.isContainer());
        GuiActionRunner.execute(() -> canvas.addChildNode(DeployNode.Kind.ARTIFACT, leaf, null));

        assertTrue("子を追加した葉ノードはコンテナ化するはず", leaf.isContainer());
        assertEquals("子が 1 個増えるはず", 1, leaf.getChildren().size());
        assertEquals("modelEdited が飛ぶはず", 1, edits.get());
    }

    @Test
    public void addChildNode_onLeafNode_roundTripsAsNestedNodeThroughPumlAndParse() {
        AtomicInteger edits = new AtomicInteger();
        DeploySketchCanvas canvas =
                GuiActionRunner.execute(() -> new DeploySketchCanvas(noopListener(edits)));
        DeploySketchModel model = new DeploySketchModel();
        DeployNode leaf = new DeployNode(DeployNode.Kind.NODE, "Leaf", null, 40, 40);
        model.getNodes().add(leaf);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, List.of());
            canvas.addChildNode(DeployNode.Kind.ARTIFACT, leaf, null);
        });

        String puml = DeploySketchCodec.toPuml(model);
        assertTrue("PlantUML に入れ子の node ブロックとして出力されるはず: " + puml,
                puml.contains("node Leaf {"));

        DeploySketchCodec.ParseResult parsed = DeploySketchCodec.parse(puml);
        DeployNode reparsedLeaf = parsed.model.findNode("Leaf");
        assertNotNull("再パース後も Leaf が見つかるはず", reparsedLeaf);
        assertTrue("再パース後もコンテナとして復元されるはず", reparsedLeaf.isContainer());
        assertEquals(1, reparsedLeaf.getChildren().size());
    }

    // --- (b) 右クリックメニューは葉ノードでも「ここに子ノードを追加」を出す (公開挙動で確認) --

    @Test
    public void rightClickPopup_onLeafNode_containsAddChildHereMenuItem() {
        popupContainsAddChildHere(new DeployNode(DeployNode.Kind.NODE, "Leaf", null, 40, 40));
    }

    /** 既存のコンテナノードでも従来どおり同項目が出続けること (回帰防止)。 */
    @Test
    public void rightClickPopup_onExistingContainer_stillContainsAddChildHereMenuItem() {
        DeployNode container = new DeployNode(DeployNode.Kind.NODE, "Outer", null, 40, 40);
        container.setContainer(true);
        popupContainsAddChildHere(container);
    }

    private void popupContainsAddChildHere(DeployNode node) {
        AtomicInteger edits = new AtomicInteger();
        DeploySketchModel model = new DeploySketchModel();
        model.getNodes().add(node);
        JFrame[] frameHolder = new JFrame[1];
        DeploySketchCanvas canvas = GuiActionRunner.execute(() -> {
            DeploySketchCanvas c = new DeploySketchCanvas(noopListener(edits));
            c.setModel(model, true, List.of());
            c.setSize(400, 300);
            JFrame f = new JFrame();
            f.add(c);
            f.setSize(420, 340);
            f.setLocation(0, 0);
            f.setVisible(true);
            frameHolder[0] = f;
            return c;
        });
        try {
            // 右クリック相当 (popupTrigger=true) をノード上へ直接ディスパッチする
            // (OS の実クリックではなく合成イベントなので Robot は不要)。
            GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                    canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    InputEvent.BUTTON3_DOWN_MASK, 60, 60, 1, true, MouseEvent.BUTTON3)));
            JPopupMenu popup = GuiActionRunner.execute(() -> findPopupInContainer(frameHolder[0]));
            assertNotNull("右クリックでポップアップメニューが出るはず", popup);
            String label = Messages.get("sketch.depl.menu.addChildHere");
            assertTrue("「" + label + "」メニュー項目が出るはず", hasMenuItem(popup, label));
        } finally {
            GuiActionRunner.execute(() -> {
                MenuSelectionManager.defaultManager().clearSelectedPath();
                frameHolder[0].dispose();
            });
        }
    }

    private static boolean hasMenuItem(JPopupMenu popup, String label) {
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenuItem && label.equals(((JMenuItem) c).getText())) {
                return true;
            }
        }
        return false;
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
