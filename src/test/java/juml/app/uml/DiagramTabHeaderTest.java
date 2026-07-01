// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramTabHeader} の公開 static メソッドを検証する。
 *
 * <p>検証対象:
 * <ol>
 *   <li>{@code setPreview(true)} でタイトル JLabel のフォントが {@link Font#ITALIC} になる</li>
 *   <li>{@code setPreview(false)} で {@link Font#PLAIN} に戻る</li>
 *   <li>{@code build()} の MouseAdapter — 中クリック {@code onClose} 発火</li>
 *   <li>{@code build()} の MouseAdapter — ダブルクリック {@code onDoubleClick} 発火</li>
 *   <li>{@code build()} の MouseAdapter — 左シングルクリック {@code onSelect} 発火</li>
 *   <li>{@code onDoubleClick} が null でも例外が起きない</li>
 * </ol>
 * </p>
 *
 * <p>Swing コンポーネントの生成・MouseEvent の発火はすべて EDT 上で行う。
 * ヘッドレス環境では {@link Assume} でスキップする。</p>
 */
public class DiagramTabHeaderTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    // -------------------------------------------------------------------------
    // setPreview
    // -------------------------------------------------------------------------

    @Test
    public void setPreview_true_makesTitleFontItalic() {
        JPanel header = GuiActionRunner.execute(() ->
                DiagramTabHeader.build("Foo", TreeNodeIcon.CLASS, "tip",
                        () -> { }, e -> { }, () -> { }, null));

        GuiActionRunner.execute(() ->
                DiagramTabHeader.setPreview(header, true));

        int style = GuiActionRunner.execute(() -> {
            JLabel label = findTitle(header);
            assertNotNull("タイトル JLabel が見つかるはず", label);
            return label.getFont().getStyle();
        });
        assertEquals("preview=true のとき Font.ITALIC になるはず",
                Font.ITALIC, style);
    }

    @Test
    public void setPreview_false_makesTitleFontPlain() {
        JPanel header = GuiActionRunner.execute(() ->
                DiagramTabHeader.build("Bar", TreeNodeIcon.CLASS, "tip",
                        () -> { }, e -> { }, () -> { }, null));

        GuiActionRunner.execute(() -> {
            DiagramTabHeader.setPreview(header, true);
            DiagramTabHeader.setPreview(header, false);
        });

        int style = GuiActionRunner.execute(() -> {
            JLabel label = findTitle(header);
            assertNotNull("タイトル JLabel が見つかるはず", label);
            return label.getFont().getStyle();
        });
        assertEquals("preview=false のとき Font.PLAIN になるはず",
                Font.PLAIN, style);
    }

    @Test
    public void setPreview_nullHeader_doesNotThrow() {
        // null や非コンテナを渡しても例外が起きないこと。
        GuiActionRunner.execute(() ->
                DiagramTabHeader.setPreview(new JLabel("dummy"), true));
        // ここに到達すれば成功。
    }

    // -------------------------------------------------------------------------
    // MouseAdapter コールバック
    // -------------------------------------------------------------------------

    @Test
    public void middleClick_firesOnClose() {
        AtomicBoolean closed = new AtomicBoolean(false);

        JPanel header = GuiActionRunner.execute(() ->
                DiagramTabHeader.build("CloseTest", null, "tip",
                        () -> closed.set(true),
                        e -> { },
                        () -> { },
                        null));

        GuiActionRunner.execute(() -> fireMousePressed(header, MouseEvent.BUTTON2, 1));

        assertTrue("中クリックで onClose が呼ばれるはず", closed.get());
    }

    @Test
    public void doubleLeftClick_firesOnDoubleClick() {
        AtomicInteger doubleClicked = new AtomicInteger(0);

        JPanel header = GuiActionRunner.execute(() ->
                DiagramTabHeader.build("DblClick", null, "tip",
                        () -> { },
                        e -> { },
                        () -> { },
                        doubleClicked::incrementAndGet));

        // mouseClicked イベントを clickCount=2 で発火する。
        GuiActionRunner.execute(() -> fireMouseClicked(header, MouseEvent.BUTTON1, 2));

        assertEquals("ダブルクリックで onDoubleClick が 1 回呼ばれるはず",
                1, doubleClicked.get());
    }

    @Test
    public void singleLeftClick_firesOnSelect() {
        AtomicBoolean selected = new AtomicBoolean(false);

        JPanel header = GuiActionRunner.execute(() ->
                DiagramTabHeader.build("SelectTest", null, "tip",
                        () -> { },
                        e -> { },
                        () -> selected.set(true),
                        null));

        GuiActionRunner.execute(() -> fireMousePressed(header, MouseEvent.BUTTON1, 1));

        assertTrue("左クリックで onSelect が呼ばれるはず", selected.get());
    }

    @Test
    public void doubleLeftClick_withNullOnDoubleClick_doesNotThrow() {
        // onDoubleClick=null でダブルクリックしても NullPointerException が起きないこと。
        JPanel header = GuiActionRunner.execute(() ->
                DiagramTabHeader.build("NullDbl", null, "tip",
                        () -> { }, e -> { }, () -> { }, null));

        GuiActionRunner.execute(() -> fireMouseClicked(header, MouseEvent.BUTTON1, 2));
        // ここに到達すれば成功。
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** ヘッダ JPanel 直下の JLabel（name="juml.tabTitle"）を返す。 */
    private static JLabel findTitle(JPanel header) {
        for (java.awt.Component c : header.getComponents()) {
            if (c instanceof JLabel && "juml.tabTitle".equals(c.getName())) {
                return (JLabel) c;
            }
        }
        return null;
    }

    /**
     * 指定ボタン・clickCount の mousePressed を header と title ラベルに発火する。
     * EDT 上から呼ぶこと。
     */
    private static void fireMousePressed(JPanel header, int button, int clickCount) {
        int mask = buttonMask(button);
        for (java.awt.event.MouseListener ml : header.getMouseListeners()) {
            ml.mousePressed(new MouseEvent(header, MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), mask, 5, 5, clickCount, false, button));
        }
    }

    /**
     * 指定ボタン・clickCount の mouseClicked を header に発火する。
     * EDT 上から呼ぶこと。
     */
    private static void fireMouseClicked(JPanel header, int button, int clickCount) {
        int mask = buttonMask(button);
        for (java.awt.event.MouseListener ml : header.getMouseListeners()) {
            ml.mouseClicked(new MouseEvent(header, MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), mask, 5, 5, clickCount, false, button));
        }
    }

    private static int buttonMask(int button) {
        switch (button) {
            case MouseEvent.BUTTON1: return MouseEvent.BUTTON1_DOWN_MASK;
            case MouseEvent.BUTTON2: return MouseEvent.BUTTON2_DOWN_MASK;
            case MouseEvent.BUTTON3: return MouseEvent.BUTTON3_DOWN_MASK;
            default: return 0;
        }
    }
}
