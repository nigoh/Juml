// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * {@link SeqSketchCanvas} で端点ドラッグ中に {@code setModel} を呼ぶと、進行中のメッセージ
 * 端点ドラッグが中断されることを検証する ({@link DeploySketchCanvasSetModelCancelsEndpointDragTest}
 * と同じ観点の回帰網を Seq キャンバスへ広げたもの)。
 *
 * <p>SeqSketchCanvas は {@code dragXxxForTest()} のような端点ドラッグ状態を直接覗くシームを
 * 持たない ({@code endpointDrag} フィールドは private かつファイルが checkstyle
 * FileLength (902 行上限) に張り付いておりシーム追加の余地が無い)。そのため本テストは
 * 「setModel 後に release しても新モデルへ孤立 reattach/modelEdited が起きない」という
 * 挙動そのもので {@code setModel} 内の {@code endpointDrag.cancel()}
 * (SeqSketchCanvas.java:156) を担保する。</p>
 */
public class SeqSketchCanvasSetModelCancelsEndpointDragTest {

    // レイアウト定数 (SeqSketchCanvasEndpointReattachTest / SeqSketchCanvasPanGuardTest と同じ値)。
    private static final int MARGIN_X = 32;
    private static final int COL_W = 120;
    private static final int FIRST_ROW_Y = 12 + 48 + 30;
    private static final int CENTER_A = MARGIN_X + COL_W / 2;
    private static final int CENTER_B = CENTER_A + COL_W;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private final AtomicInteger edits = new AtomicInteger();

    private SeqSketchCanvas.Listener listener() {
        return new SeqSketchCanvas.Listener() {
            @Override public void modelEdited() {
                edits.incrementAndGet();
            }

            @Override public void editMessageRequested(SeqItem m) {
            }

            @Override public void editParticipantRequested(SeqParticipant p) {
            }
        };
    }

    private void dispatch(SeqSketchCanvas canvas, int id, int modifiersEx, int x, int y, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, 1, false, button)));
    }

    @Test
    public void setModelDuringEndpointDrag_cancelsDragAndLaterReleaseDoesNotReattach() {
        SeqSketchCanvas canvas = GuiActionRunner.execute(() -> new SeqSketchCanvas(listener()));
        SeqSketchModel oldModel = new SeqSketchModel();
        oldModel.getParticipants().add(new SeqParticipant("A", SeqParticipant.Kind.PARTICIPANT, true));
        oldModel.getParticipants().add(new SeqParticipant("B", SeqParticipant.Kind.PARTICIPANT, true));
        SeqItem oldMessage = SeqItem.message("A", SeqItem.Arrow.SYNC, "B", "hello");
        oldModel.getItems().add(oldMessage);
        GuiActionRunner.execute(() -> {
            canvas.setModel(oldModel, true, List.of());
            canvas.setSize(600, 400);
        });

        // "to" 端点 (B, x=CENTER_B) を掴んでドラッグを開始する (release はまだしない)。
        dispatch(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                CENTER_B, FIRST_ROW_Y, MouseEvent.BUTTON1);

        // 新しいモデルへ差し替える (図の再ロード相当)。旧モデルの A/B/oldMessage はもう
        // 画面上に無い。新モデルにも同じ列座標 (CENTER_A/CENTER_B) に別参加者を置く。
        SeqSketchModel newModel = new SeqSketchModel();
        newModel.getParticipants().add(new SeqParticipant("C", SeqParticipant.Kind.PARTICIPANT, true));
        newModel.getParticipants().add(new SeqParticipant("D", SeqParticipant.Kind.PARTICIPANT, true));
        SeqItem newMessage = SeqItem.message("C", SeqItem.Arrow.SYNC, "D", "world");
        newModel.getItems().add(newMessage);
        GuiActionRunner.execute(() -> canvas.setModel(newModel, true, List.of()));

        // ドラッグ中断後に、新モデルの別ライフライン (D, x=CENTER_B) 上で release しても、
        // 新モデルのメッセージへ孤立 reattach/modelEdited が起きないこと。
        dispatch(canvas, MouseEvent.MOUSE_RELEASED, 0, CENTER_B, FIRST_ROW_Y, MouseEvent.BUTTON1);

        assertEquals("setModel で端点ドラッグが中断されるはず (SeqSketchCanvas.java:156): "
                        + "旧メッセージの to は変わらないはず",
                "B", oldMessage.getTo());
        assertEquals("新モデルのメッセージも巻き込まれないはず (from)", "C", newMessage.getFrom());
        assertEquals("新モデルのメッセージも巻き込まれないはず (to)", "D", newMessage.getTo());
        assertEquals("孤立 reattach で modelEdited が飛んではならない", 0, edits.get());
    }
}
