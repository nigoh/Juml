// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.PlantUmlSequenceDiagram;

import javax.swing.JDialog;
import javax.swing.JRadioButton;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/**
 * {@link StyleSettingsDialog} の OK 確定ロジック {@code collect()} のテスト。
 *
 * <p>{@code DialogKeyboardTest} は Escape/既定ボタンのみ、{@code ClassDiagramPrefsTest} は
 * DTO 単体のみで、ウィジェット → {@link StyleSettingsDialog.Result} 組み立ては無検証だった。
 * ここでは方向ラジオ ({@code dirLeftRight}/{@code dirTopBottom}) → {@link DiagramStyle} の
 * {@code Direction} 写像という中核分岐を検証する (回帰すると設定が保存されない UX バグ)。</p>
 *
 * <p>{@code DialogKeyboardTest.createStyleSettings} と同じくリフレクションで private
 * コンストラクタを呼ぶ。{@link JDialog} 生成に display が要るためヘッドレスでは
 * {@code Assume} で skip。</p>
 */
public class StyleSettingsDialogResultTest {

    private StyleSettingsDialog dlg;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境ではスキップ (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
    }

    @After
    public void cleanup() {
        if (dlg != null) {
            GuiActionRunner.execute(() -> {
                if (dlg.isDisplayable()) {
                    dlg.dispose();
                }
            });
        }
    }

    private StyleSettingsDialog create() throws Exception {
        Constructor<StyleSettingsDialog> ctor = StyleSettingsDialog.class.getDeclaredConstructor(
                Window.class, DiagramStyle.class, boolean.class,
                PlantUmlClassDiagram.CommentStyle.class,
                PlantUmlSequenceDiagram.CommentPlacement.class,
                boolean.class, int.class, boolean.class,
                ActivityDiagramPrefs.class,
                ClassDiagramPrefs.class, int.class);
        ctor.setAccessible(true);
        dlg = GuiActionRunner.execute(() -> {
            try {
                return ctor.newInstance(
                        null, DiagramStyle.defaults(), true,
                        PlantUmlClassDiagram.CommentStyle.INLINE,
                        PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE,
                        true, 5, false,
                        ActivityDiagramPrefs.defaults(),
                        ClassDiagramPrefs.defaults(), 3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return dlg;
    }

    private void selectRadio(String fieldName) throws Exception {
        Field f = StyleSettingsDialog.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        JRadioButton rb = (JRadioButton) f.get(dlg);
        GuiActionRunner.execute(() -> {
            rb.setSelected(true);
            return null;
        });
    }

    private StyleSettingsDialog.Result collect() throws Exception {
        Method m = StyleSettingsDialog.class.getDeclaredMethod("collect");
        m.setAccessible(true);
        return GuiActionRunner.execute(() -> {
            try {
                return (StyleSettingsDialog.Result) m.invoke(dlg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** 左→右ラジオ選択が DiagramStyle の Direction に反映される。 */
    @Test
    public void leftRightRadioMapsToLeftToRightDirection() throws Exception {
        create();
        selectRadio("dirLeftRight");
        assertEquals(DiagramStyle.Direction.LEFT_TO_RIGHT, collect().style.getDirection());
    }

    /** 上→下ラジオ選択が DiagramStyle の Direction に反映される。 */
    @Test
    public void topBottomRadioMapsToTopToBottomDirection() throws Exception {
        create();
        selectRadio("dirTopBottom");
        assertEquals(DiagramStyle.Direction.TOP_TO_BOTTOM, collect().style.getDirection());
    }
}
