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
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link ActivitySketchCanvas} の Canvas レベル実操作テスト (bug-hunt round2 Critical 指摘)。
 *
 * <p>{@code ActivitySketchCodecTest} はモデル/コーデックの純ロジックのみを検証しており、
 * マウス選択・Delete 削除・右クリックメニュー (編集/削除/上へ/下へ/後に追加/then・else
 * への追加/未選択時の追加)・{@code editable=false} ガード・{@code setModel} の選択クリアなど、
 * Canvas 自体の実操作は他 8 キャンバスと異なり一切テストが無かった。ここではそれらを
 * 実マウス/キー dispatch と公開挙動 ({@code selectedForTest()} / モデル / listener 通知) で
 * 検証する。</p>
 *
 * <h2>座標について</h2>
 * <p>{@link ActivitySketchCanvas} には他キャンバス (例: {@code DeploySketchCanvas}) が持つ
 * {@code layoutForTest()} のようなノード矩形取得シームが無く、レイアウト結果
 * ({@code bounds} フィールド) も private でテストから参照できない。そのため下記
 * {@code CX} / {@code *_CY} 定数は、対応するモデル構築コードとまったく同じノード構成・
 * 同じ短いテキストで実行したときの実測レイアウト座標 (テスト作成時にリフレクションで
 * 一時的に {@code bounds} を読んで検証済み) をハードコードしたものである。
 * {@link ActivitySketchCanvas} のレイアウトは並び順から決定的に計算され、横方向の中心
 * ({@code cx}) はノード列の総幅が一定しきい値以下なら常に {@code 180} に固定され、
 * 縦方向はノード種別ごとの固定高さ (START/STOP は直径 20px, ACTION は 28px, IF は 40px)
 * と一定間隔 (26px) の積み上げで決まるため、ノード内の文字幅には依存しない
 * (ただし全体のブロック幅がしきい値を超えると cx がずれるため、ここで使うテキストは
 * 検証時のものと同じ短さを保つこと)。座標を使うテストのモデル構築テキストを変更する
 * 場合は、この節に書いた座標を再検証すること。</p>
 */
public class ActivitySketchCanvasTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成/ポップアップ表示が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    // -------------------------------------------------------------------------
    // レイアウト座標 (クラス Javadoc 参照)。
    // -------------------------------------------------------------------------

    private static final int CX = 180;
    /** 単独、または先頭の ACTION の中心 y (テキストは "A" / "Step" など短いもの)。 */
    private static final int FIRST_ACTION_CY = 42;
    /** START が先頭ノードのときの中心 y。 */
    private static final int FIRST_TERMINAL_CY = 38;
    /** 先頭が START/STOP のとき、2 番目に続く ACTION の中心 y。 */
    private static final int NODE2_AFTER_TERMINAL_CY = 88;
    /** 先頭が ACTION のとき、2 番目に続く ACTION の中心 y。 */
    private static final int SECOND_ACTION_CY = 96;
    /** START, ACTION に続く 3 番目の STOP の中心 y。 */
    private static final int THIRD_TERMINAL_CY = 138;
    /** トップレベル唯一の IF (then/else とも 1 ノード以下) の中心 y。 */
    private static final int TOP_IF_CY = 48;

    // -------------------------------------------------------------------------
    // ヘルパー: リスナー / キャンバス生成
    // -------------------------------------------------------------------------

    /** モデル編集通知・編集要求を記録するテスト用リスナー。 */
    private static final class RecordingListener implements ActivitySketchCanvas.Listener {
        final AtomicInteger edits = new AtomicInteger();
        final List<ActivityNode> editRequests = new ArrayList<>();

        @Override public void modelEdited() {
            edits.incrementAndGet();
        }

        @Override public void editRequested(ActivityNode node) {
            editRequests.add(node);
        }
    }

    private static ActivitySketchCanvas newCanvas(RecordingListener listener) {
        return GuiActionRunner.execute(() -> new ActivitySketchCanvas(listener));
    }

    // -------------------------------------------------------------------------
    // ヘルパー: 描画
    // -------------------------------------------------------------------------

    private static BufferedImage paint(ActivitySketchCanvas canvas, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(w, h);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
        return img;
    }

    // -------------------------------------------------------------------------
    // ヘルパー: マウス / キー dispatch
    // -------------------------------------------------------------------------

    private static void dispatchMouse(ActivitySketchCanvas canvas, int id, int modifiersEx,
            int x, int y, int clickCount, boolean popupTrigger, int button) {
        GuiActionRunner.execute(() -> canvas.dispatchEvent(new MouseEvent(
                canvas, id, System.currentTimeMillis(), modifiersEx, x, y, clickCount,
                popupTrigger, button)));
    }

    private static void leftPress(ActivitySketchCanvas canvas, int x, int y) {
        dispatchMouse(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK,
                x, y, 1, false, MouseEvent.BUTTON1);
    }

    private static void rightPress(ActivitySketchCanvas canvas, int x, int y) {
        dispatchMouse(canvas, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON3_DOWN_MASK,
                x, y, 1, true, MouseEvent.BUTTON3);
    }

    private static void doubleLeftClick(ActivitySketchCanvas canvas, int x, int y) {
        dispatchMouse(canvas, MouseEvent.MOUSE_CLICKED, InputEvent.BUTTON1_DOWN_MASK,
                x, y, 2, false, MouseEvent.BUTTON1);
    }

    /** 非表示/未フォーカスの Component には KeyboardFocusManager 経由で届かないため、
     * 登録済み KeyListener を EDT 上で直接起動する (他キャンバスの Delete テストと同じ手法)。 */
    private static void pressDeleteKey(ActivitySketchCanvas canvas) {
        KeyEvent del = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED);
        GuiActionRunner.execute(() -> {
            for (KeyListener kl : canvas.getKeyListeners()) {
                kl.keyPressed(del);
            }
        });
    }

    // -------------------------------------------------------------------------
    // ヘルパー: ポップアップメニュー
    // -------------------------------------------------------------------------

    private static JFrame showInFrame(ActivitySketchCanvas canvas) {
        return GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.add(canvas);
            f.setSize(700, 700);
            f.setLocation(0, 0);
            f.setVisible(true);
            return f;
        });
    }

    private static JPopupMenu openPopup(ActivitySketchCanvas canvas, JFrame frame, int x, int y) {
        rightPress(canvas, x, y);
        return GuiActionRunner.execute(() -> findPopupInContainer(frame));
    }

    private static void closePopup() {
        GuiActionRunner.execute(() -> MenuSelectionManager.defaultManager().clearSelectedPath());
    }

    private static void click(JMenuItem item) {
        GuiActionRunner.execute(() -> item.doClick());
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

    // =========================================================================
    // 1. paint smoke: 例外を投げずに描画できること
    // =========================================================================

    @Test
    public void paint_nestedIfWithElselessBranchAndTerminals_doesNotThrow() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivitySketchModel model = new ActivitySketchModel();
        ActivityNode start = ActivityNode.terminal(ActivityNode.Kind.START);
        ActivityNode outerIf = ActivityNode.branch("outer?", "yes", "no");
        ActivityNode innerIf = ActivityNode.branch("inner?", "yes", "no");
        innerIf.getThenBranch().add(ActivityNode.action("Validate"));
        // innerIf は else 節を持たない (elseBranch == null) ままにし、else 無しブランチの
        // バイパス線描画を経路に含める。
        outerIf.getThenBranch().add(ActivityNode.action("Persist"));
        outerIf.getThenBranch().add(innerIf);
        // outerIf も else 節を持たない。
        ActivityNode after = ActivityNode.action("Notify");
        ActivityNode stop = ActivityNode.terminal(ActivityNode.Kind.STOP);
        model.getNodes().add(start);
        model.getNodes().add(outerIf);
        model.getNodes().add(after);
        model.getNodes().add(stop);
        assertEquals("前提: 4 個のトップレベルノード", 4, model.getNodes().size());
        assertNull("前提: outerIf は else 無し", outerIf.getElseBranch());
        assertNull("前提: innerIf は else 無し", innerIf.getElseBranch());

        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));
        BufferedImage img = paint(canvas, 900, 800);
        assertNotNull("例外を投げずに描画できるはず", img);
    }

    @Test
    public void paint_lockedModelWithUnsupportedBanner_doesNotThrow() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(ActivityNode.action("Locked"));
        GuiActionRunner.execute(() -> canvas.setModel(model, false, List.of("fork; end fork")));
        BufferedImage img = paint(canvas, 600, 400);
        assertNotNull("ロック中バナー付き描画でも例外を投げないはず", img);
        assertFalse("ロック中は isModelEditable() が false のはず", canvas.isModelEditable());
    }

    // =========================================================================
    // 2. マウス選択
    // =========================================================================

    @Test
    public void mousePress_onSingleActionNode_selectsIt() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode action = ActivityNode.action("A");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(action);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        assertNull("前提: 初期状態では未選択のはず", canvas.selectedForTest());
        leftPress(canvas, CX, FIRST_ACTION_CY);
        assertSame("ノード矩形中心への press でそのノードが選択されるはず",
                action, canvas.selectedForTest());
    }

    @Test
    public void mousePress_amongStackedNodes_selectsExactHitAndClearsOnEmptyArea() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode start = ActivityNode.terminal(ActivityNode.Kind.START);
        ActivityNode action = ActivityNode.action("Step");
        ActivityNode stop = ActivityNode.terminal(ActivityNode.Kind.STOP);
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(start);
        model.getNodes().add(action);
        model.getNodes().add(stop);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        leftPress(canvas, CX, NODE2_AFTER_TERMINAL_CY);
        assertSame("3 ノードが縦に並ぶ中で中央 (ACTION) の矩形中心をクリックしたら"
                + " ACTION が選択されるはず (START/STOP と誤選択しない)", action,
                canvas.selectedForTest());

        // 何もないキャンバス左上遠方をクリックすると選択が外れる。
        leftPress(canvas, 5, 5);
        assertNull("ノードの無い領域を press すると選択がクリアされるはず",
                canvas.selectedForTest());
    }

    // =========================================================================
    // 3. Delete キー削除
    // =========================================================================

    @Test
    public void deleteKey_removesSelectedActionNode_clearsSelectionAndFiresModelEditedOnce() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode start = ActivityNode.terminal(ActivityNode.Kind.START);
        ActivityNode action = ActivityNode.action("Step");
        ActivityNode stop = ActivityNode.terminal(ActivityNode.Kind.STOP);
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(start);
        model.getNodes().add(action);
        model.getNodes().add(stop);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        leftPress(canvas, CX, NODE2_AFTER_TERMINAL_CY);
        assertSame("前提: ACTION が選択されているはず", action, canvas.selectedForTest());

        pressDeleteKey(canvas);

        assertEquals("ACTION が削除され START/STOP のみ残るはず", 2, model.getNodes().size());
        assertFalse("削除したノードがモデルに残っていないはず",
                model.getNodes().contains(action));
        assertNull("削除後は選択がクリアされるはず", canvas.selectedForTest());
        assertEquals("modelEdited がちょうど 1 回飛ぶはず", 1, listener.edits.get());
    }

    @Test
    public void deleteKey_withNoSelection_isNoOp() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(ActivityNode.terminal(ActivityNode.Kind.START));
        model.getNodes().add(ActivityNode.action("Step"));
        model.getNodes().add(ActivityNode.terminal(ActivityNode.Kind.STOP));

        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));
        assertNull("前提: 未選択のはず", canvas.selectedForTest());

        pressDeleteKey(canvas);

        assertEquals("未選択の Delete ではノード数が変わらないはず", 3, model.getNodes().size());
        assertEquals("未選択の Delete では modelEdited が飛ばないはず", 0, listener.edits.get());
    }

    // =========================================================================
    // 4. setModel: 選択クリア
    // =========================================================================

    @Test
    public void setModel_withDifferentModel_clearsSelection() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode a = ActivityNode.action("A");
        ActivitySketchModel model1 = new ActivitySketchModel();
        model1.getNodes().add(a);
        GuiActionRunner.execute(() -> canvas.setModel(model1, true, List.of()));
        leftPress(canvas, CX, FIRST_ACTION_CY);
        assertSame("前提: A が選択されているはず", a, canvas.selectedForTest());

        ActivitySketchModel model2 = new ActivitySketchModel();
        model2.getNodes().add(ActivityNode.action("B"));
        GuiActionRunner.execute(() -> canvas.setModel(model2, true, List.of()));

        assertNull("別モデルの setModel で選択がクリアされるはず", canvas.selectedForTest());
        assertSame("model() は新しいモデルを返すはず", model2, canvas.model());
    }

    // =========================================================================
    // 5. editable = false (ロック): press / Delete / メニューがすべて無反応
    // =========================================================================

    @Test
    public void lockedCanvas_leftPress_doesNotSelect() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(ActivityNode.action("A"));
        GuiActionRunner.execute(() -> canvas.setModel(model, false, List.of()));

        leftPress(canvas, CX, FIRST_ACTION_CY);

        assertNull("ロック中は press してもノードが選択されないはず", canvas.selectedForTest());
        assertEquals(0, listener.edits.get());
    }

    @Test
    public void lockedCanvas_deleteKey_withForcedSelection_isNoOp() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode a = ActivityNode.action("A");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, false, List.of());
            // press ではロック中に選択できないため、KeyListener 側の editable ガード単体を
            // 検証するために setSelectedForTest で強制的に選択状態を作る。
            canvas.setSelectedForTest(a);
        });

        pressDeleteKey(canvas);

        assertEquals("ロック中は Delete でノードが削除されないはず", 1, model.getNodes().size());
        assertTrue(model.getNodes().contains(a));
        assertSame("ロック中は選択も変化しないはず", a, canvas.selectedForTest());
        assertEquals("ロック中は modelEdited が飛ばないはず", 0, listener.edits.get());
    }

    @Test
    public void lockedCanvas_rightClick_showsNoPopupAndDoesNotSelect() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(ActivityNode.action("A"));
        GuiActionRunner.execute(() -> canvas.setModel(model, false, List.of()));

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu popup = openPopup(canvas, frame, CX, FIRST_ACTION_CY);
            assertNull("ロック中は右クリックしてもポップアップが出ないはず", popup);
            assertNull("ロック中は右クリックしても選択されないはず", canvas.selectedForTest());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    // =========================================================================
    // 6. ダブルクリック編集 (ACTION/IF のみ許可、START/STOP は無視)
    // =========================================================================

    @Test
    public void doubleClick_onActionNode_firesEditRequestedWithThatNode() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode action = ActivityNode.action("A");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(action);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        leftPress(canvas, CX, FIRST_ACTION_CY);
        doubleLeftClick(canvas, CX, FIRST_ACTION_CY);

        assertEquals("ACTION のダブルクリックで editRequested が 1 回飛ぶはず",
                1, listener.editRequests.size());
        assertSame(action, listener.editRequests.get(0));
    }

    @Test
    public void doubleClick_onStartTerminalNode_isIgnored() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode start = ActivityNode.terminal(ActivityNode.Kind.START);
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(start);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        leftPress(canvas, CX, FIRST_TERMINAL_CY);
        assertSame("前提: START が選択されているはず", start, canvas.selectedForTest());
        doubleLeftClick(canvas, CX, FIRST_TERMINAL_CY);

        assertTrue("START のダブルクリックは編集要求を出さないはず",
                listener.editRequests.isEmpty());
    }

    // =========================================================================
    // 7. showPopup メニューアクション
    // =========================================================================

    @Test
    public void popup_moveUp_atFirstNode_isNoOp() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode a = ActivityNode.action("A");
        ActivityNode b = ActivityNode.action("B");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(a);
        model.getNodes().add(b);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu popup = openPopup(canvas, frame, CX, FIRST_ACTION_CY);
            assertNotNull("先頭ノードの右クリックでポップアップが出るはず", popup);
            JMenuItem up = findMenuItem(popup, Messages.get("sketch.act.menu.moveUp"));
            assertNotNull("「上へ」メニュー項目が見つかるはず", up);
            click(up);

            assertEquals("先頭ノードでの「上へ」は並び順を変えない (no-op) はず",
                    List.of(a, b), model.getNodes());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    @Test
    public void popup_moveDown_atLastNode_isNoOp() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode a = ActivityNode.action("A");
        ActivityNode b = ActivityNode.action("B");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(a);
        model.getNodes().add(b);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu popup = openPopup(canvas, frame, CX, SECOND_ACTION_CY);
            assertNotNull("末尾ノードの右クリックでポップアップが出るはず", popup);
            JMenuItem down = findMenuItem(popup, Messages.get("sketch.act.menu.moveDown"));
            assertNotNull("「下へ」メニュー項目が見つかるはず", down);
            click(down);

            assertEquals("末尾ノードでの「下へ」は並び順を変えない (no-op) はず",
                    List.of(a, b), model.getNodes());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    @Test
    public void popup_addActionAfter_insertsNewActionRightAfterHit_selectionUnchanged() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode a = ActivityNode.action("A");
        ActivityNode b = ActivityNode.action("B");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(a);
        model.getNodes().add(b);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu popup = openPopup(canvas, frame, CX, FIRST_ACTION_CY);
            JMenuItem addAfter =
                    findMenuItem(popup, Messages.get("sketch.act.menu.addActionAfter"));
            assertNotNull("「直後にアクションを追加」メニュー項目が見つかるはず", addAfter);
            click(addAfter);

            assertEquals("A の直後へ 1 ノード挿入されるはず", 3, model.getNodes().size());
            assertSame(a, model.getNodes().get(0));
            assertEquals(ActivityNode.Kind.ACTION, model.getNodes().get(1).getKind());
            assertSame("B は 3 番目へ後退するはず", b, model.getNodes().get(2));
            assertSame("「後に追加」は addNode() 経由ではないため selected を"
                    + " 更新しない仕様のはず (未選択時の追加とは異なる)", a, canvas.selectedForTest());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    @Test
    public void popup_deleteMenuItem_removesHitNodeAndClearsSelection() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode a = ActivityNode.action("A");
        ActivityNode b = ActivityNode.action("B");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(a);
        model.getNodes().add(b);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu popup = openPopup(canvas, frame, CX, FIRST_ACTION_CY);
            JMenuItem delete = findMenuItem(popup, Messages.get("sketch.act.menu.delete"));
            assertNotNull("「削除」メニュー項目が見つかるはず", delete);
            click(delete);

            assertEquals("A が削除され B のみ残るはず", 1, model.getNodes().size());
            assertSame(b, model.getNodes().get(0));
            assertNull("削除後は選択がクリアされるはず", canvas.selectedForTest());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    @Test
    public void popup_editAndBranchMenuItems_gatedByNodeKind() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode start = ActivityNode.terminal(ActivityNode.Kind.START);
        ActivityNode action = ActivityNode.action("Only");
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(start);
        model.getNodes().add(action);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu startPopup = openPopup(canvas, frame, CX, FIRST_TERMINAL_CY);
            assertNotNull("START の右クリックでもポップアップは出るはず", startPopup);
            assertNull("START (端末ノード) には「編集...」項目が出ないはず",
                    findMenuItem(startPopup, Messages.get("sketch.act.menu.edit")));
            assertNotNull("START でも「削除」項目は出るはず",
                    findMenuItem(startPopup, Messages.get("sketch.act.menu.delete")));
            closePopup();

            JPopupMenu actionPopup = openPopup(canvas, frame, CX, NODE2_AFTER_TERMINAL_CY);
            assertNotNull("ACTION の右クリックでポップアップが出るはず", actionPopup);
            assertNotNull("ACTION には「編集...」項目が出るはず",
                    findMenuItem(actionPopup, Messages.get("sketch.act.menu.edit")));
            assertNull("ACTION (IF ではない) には then ブランチ追加項目が出ないはず",
                    findMenuItem(actionPopup, Messages.get("sketch.act.menu.addToThen")));
            assertNull("ACTION (IF ではない) には else ブランチ追加項目が出ないはず",
                    findMenuItem(actionPopup, Messages.get("sketch.act.menu.addToElse")));
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    @Test
    public void popup_addToThenAndAddToElse_updateIfBranchesAndCreateElseLazily() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivityNode ifNode = ActivityNode.branch("c?", "yes", "no");
        ifNode.getThenBranch().add(ActivityNode.action("T"));
        ActivitySketchModel model = new ActivitySketchModel();
        model.getNodes().add(ifNode);
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        assertEquals("前提: then ブランチに 1 ノード", 1, ifNode.getThenBranch().size());
        assertNull("前提: else 節はまだ無いはず", ifNode.getElseBranch());

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu popup1 = openPopup(canvas, frame, CX, TOP_IF_CY);
            assertNotNull("IF の右クリックでポップアップが出るはず", popup1);
            assertNotNull("IF には「編集...」項目が出るはず",
                    findMenuItem(popup1, Messages.get("sketch.act.menu.edit")));
            JMenuItem toThen = findMenuItem(popup1, Messages.get("sketch.act.menu.addToThen"));
            assertNotNull("IF には then ブランチ追加項目が出るはず", toThen);
            click(toThen);
            assertEquals("then ブランチにアクションが 1 個増えるはず",
                    2, ifNode.getThenBranch().size());
            closePopup();

            // IF ダイヤモンド自身の座標は then ブランチのノード数が変わっても揺れない
            // (blockWidth は「最大幅」であり「合計」ではないため)。
            JPopupMenu popup2 = openPopup(canvas, frame, CX, TOP_IF_CY);
            assertNotNull("2 回目の右クリックでもポップアップが出るはず", popup2);
            JMenuItem toElse = findMenuItem(popup2, Messages.get("sketch.act.menu.addToElse"));
            assertNotNull("IF には else ブランチ追加項目が出るはず", toElse);
            click(toElse);

            assertNotNull("else ブランチ追加で ensureElseBranch() が呼ばれ非 null になるはず",
                    ifNode.getElseBranch());
            assertEquals("else ブランチにアクションが 1 個作られるはず",
                    1, ifNode.getElseBranch().size());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    @Test
    public void popup_whenNothingSelected_addAction_appendsAndSelectsNewNode() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivitySketchModel model = new ActivitySketchModel();
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));
        assertTrue("前提: 空モデルのはず", model.getNodes().isEmpty());

        JFrame frame = showInFrame(canvas);
        try {
            // ノードが無いのでヒットテストは必ず外れる。座標は任意でよい。
            JPopupMenu popup = openPopup(canvas, frame, 50, 50);
            assertNotNull("未選択時でも右クリックでポップアップが出るはず", popup);
            assertNull("未選択時は「削除」項目が出ないはず",
                    findMenuItem(popup, Messages.get("sketch.act.menu.delete")));
            JMenuItem addAction = findMenuItem(popup, Messages.get("sketch.act.menu.addAction"));
            assertNotNull("未選択時は「アクション追加」項目が出るはず", addAction);
            click(addAction);

            assertEquals(1, model.getNodes().size());
            ActivityNode created = model.getNodes().get(0);
            assertEquals(ActivityNode.Kind.ACTION, created.getKind());
            assertSame("追加後は新ノードが選択されるはず (addNode() の仕様)",
                    created, canvas.selectedForTest());
            assertEquals("modelEdited が 1 回飛ぶはず", 1, listener.edits.get());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }

    @Test
    public void popup_whenNothingSelected_addIf_appendsWithElseBranchAlreadyPresent() {
        RecordingListener listener = new RecordingListener();
        ActivitySketchCanvas canvas = newCanvas(listener);
        ActivitySketchModel model = new ActivitySketchModel();
        GuiActionRunner.execute(() -> canvas.setModel(model, true, List.of()));

        JFrame frame = showInFrame(canvas);
        try {
            JPopupMenu popup = openPopup(canvas, frame, 50, 50);
            assertNotNull(popup);
            JMenuItem addIf = findMenuItem(popup, Messages.get("sketch.act.menu.addIf"));
            assertNotNull("未選択時は「IF 追加」項目が出るはず", addIf);
            click(addIf);

            assertEquals(1, model.getNodes().size());
            ActivityNode created = model.getNodes().get(0);
            assertEquals(ActivityNode.Kind.IF, created.getKind());
            assertNotNull("IF 追加は最初から ensureElseBranch() 済みで else 非 null のはず",
                    created.getElseBranch());
            assertTrue("else ブランチは空で始まるはず", created.getElseBranch().isEmpty());
            assertSame("追加後は新ノードが選択されるはず (addNode() の仕様)",
                    created, canvas.selectedForTest());
        } finally {
            closePopup();
            GuiActionRunner.execute(() -> frame.dispose());
        }
    }
}
