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
 * bug-hunt round10 の回帰テスト: シーケンス図デザイナーでメッセージ追加モード中に始点参加者を
 * 右クリック削除 ({@code deleteSelection()} 経由) すると、修正前は {@code messageSource} が
 * 削除済みインスタンスを指したまま残り、次にクリックした参加者との間に削除済み参加者を
 * 端点に持つ宙吊りのメッセージが生成されていた (往復 PlantUML にも宣言のない幽霊参加者が
 * 復活する)。
 *
 * <p>参加者名を 1 文字 (A/B/C) に固定し、列幅が {@code COL_MIN_W} (120px) で決まる前提で
 * ライフライン中心 X 座標を固定値として扱う ({@link SeqSketchCanvasHeaderDropReattachTest} と
 * 同じ前提)。A を削除すると B/C が 1 列ずつ詰めて並び直すため、削除後に
 * {@code CENTER_A}/{@code CENTER_B} の位置をクリックすると新しい並びの B/C に当たる。</p>
 */
public class SeqSketchCanvasDeleteClearsPendingSourceTest {

    private static final int MARGIN_X = 32;
    private static final int COL_W = 120;
    /** 参加者ヘッダー箱の可視 Y 範囲 [12,60) の中間点。 */
    private static final int HEADER_Y = 30;
    private static final int CENTER_A = MARGIN_X + COL_W / 2;
    private static final int CENTER_B = CENTER_A + COL_W;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成/ポップアップ表示が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static SeqSketchCanvas.Listener noopListener(AtomicInteger edits) {
        return new SeqSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editMessageRequested(SeqItem message) {
            }

            @Override public void editParticipantRequested(SeqParticipant participant) {
            }
        };
    }

    private static void dispatch(SeqSketchCanvas canvas, int id, int modifiersEx,
                                int x, int y, int button, boolean popupTrigger) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, popupTrigger, button)));
    }

    private static void leftClick(SeqSketchCanvas canvas, int x, int y) {
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                x, y, MouseEvent.BUTTON1, false);
    }

    private static void rightClick(SeqSketchCanvas canvas, int x, int y) {
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON3_DOWN_MASK,
                x, y, MouseEvent.BUTTON3, true);
    }

    @Test
    public void deletingMessageSourceViaRightClick_clearsPendingSource() {
        AtomicInteger edits = new AtomicInteger();
        SeqSketchModel model = new SeqSketchModel();
        model.getParticipants().add(new SeqParticipant("A", SeqParticipant.Kind.PARTICIPANT, true));
        model.getParticipants().add(new SeqParticipant("B", SeqParticipant.Kind.PARTICIPANT, true));
        model.getParticipants().add(new SeqParticipant("C", SeqParticipant.Kind.PARTICIPANT, true));

        JFrame[] frameHolder = new JFrame[1];
        SeqSketchCanvas canvas = GuiActionRunner.execute(() -> {
            SeqSketchCanvas cv = new SeqSketchCanvas(noopListener(edits));
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
            GuiActionRunner.execute(() -> canvas.setMessageMode(SeqItem.Arrow.SYNC));
            // A のヘッダーをクリックして始点 (messageSource) を確定する。
            leftClick(canvas, CENTER_A, HEADER_Y);

            // A を右クリックして削除メニューを出す (参加者ヘッダー箱への右クリック)。
            rightClick(canvas, CENTER_A, HEADER_Y);
            JPopupMenu popup = GuiActionRunner.execute(() -> findPopupInContainer(frameHolder[0]));
            assertNotNull("右クリックでポップアップメニューが出るはず", popup);
            JMenuItem delete = findMenuItem(popup, Messages.get("sketch.seq.menu.deleteParticipant"));
            assertNotNull("参加者削除メニュー項目が見つかるはず", delete);
            GuiActionRunner.execute(() -> {
                delete.doClick();
                return null;
            });

            assertEquals("A が削除され B, C のみ残るはず", 2, model.getParticipants().size());
            assertEquals("B", model.getParticipants().get(0).getName());
            assertEquals("C", model.getParticipants().get(1).getName());

            // 削除済み A が pending source のまま残っていれば、次にクリックした参加者との間に
            // 宙吊りメッセージが完成してしまう (round10 の修正前バグ)。A 削除後は B が 1 列目
            // へ詰まるため、CENTER_A の位置をクリックすると新しい並びの B に当たる。
            leftClick(canvas, CENTER_A, HEADER_Y);
            assertTrue("削除済み A を端点に持つ宙吊りメッセージが生成されないはず (B は新しい始点になっただけのはず)",
                    model.getItems().isEmpty());

            // B が新しい始点として正しく確定していることを、C クリックでのメッセージ完成で確認する。
            // 詰め直し後は 2 列目 (CENTER_B の位置) が C に当たる。
            leftClick(canvas, CENTER_B, HEADER_Y);
            assertEquals("B->C のメッセージが 1 件だけ生成されるはず", 1, model.getItems().size());
            SeqItem created = model.getItems().get(0);
            assertEquals(SeqItem.Kind.MESSAGE, created.getKind());
            assertEquals("B", created.getFrom());
            assertEquals("C", created.getTo());

            String puml = GuiActionRunner.execute(() -> SeqSketchCodec.toPuml(model));
            assertFalse("再生成 PlantUML に削除済み A への参照 (幽霊参加者) が出ないはず: " + puml,
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
