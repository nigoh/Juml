// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SequenceParticipantFilterDialog} の commit() 結果ロジックのテスト。
 *
 * <p>{@code DialogKeyboardTest} は Escape/default button/ボタン順という「キーボード作法」を
 * 守っているが、中核の「チェックを外した participant を hidden として返す」ロジック
 * ({@code commit} / {@code setAll} / {@code result}) は無検証だった。ここでは modal 表示せず
 * (setVisible(true) を呼ばず) に commit/setAll/Cancel を直接叩いて結果集合を検証する。</p>
 *
 * <p>{@link javax.swing.JDialog} の生成には display が要るためヘッドレスでは {@code Assume} で
 * skip する ({@code DialogKeyboardTest} と同方針。xvfb-run でラップして実行)。</p>
 */
public class SequenceParticipantFilterDialogTest {

    private SequenceParticipantFilterDialog dlg;

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

    private SequenceParticipantFilterDialog create(Set<String> participants,
                                                   Set<String> initiallyHidden) throws Exception {
        Constructor<SequenceParticipantFilterDialog> ctor =
                SequenceParticipantFilterDialog.class.getDeclaredConstructor(
                        Window.class, String.class, Set.class, Set.class);
        ctor.setAccessible(true);
        dlg = GuiActionRunner.execute(() -> {
            try {
                return ctor.newInstance(null, "Foo.bar", participants, initiallyHidden);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return dlg;
    }

    @SuppressWarnings("unchecked")
    private Map<String, JCheckBox> checks() throws Exception {
        Field f = SequenceParticipantFilterDialog.class.getDeclaredField("checks");
        f.setAccessible(true);
        return (Map<String, JCheckBox>) f.get(dlg);
    }

    @SuppressWarnings("unchecked")
    private Set<String> result() throws Exception {
        Field f = SequenceParticipantFilterDialog.class.getDeclaredField("result");
        f.setAccessible(true);
        return (Set<String>) f.get(dlg);
    }

    private void invokeOnEdt(String method, Class<?>[] sig, Object... args) throws Exception {
        Method m = SequenceParticipantFilterDialog.class.getDeclaredMethod(method, sig);
        m.setAccessible(true);
        GuiActionRunner.execute(() -> {
            try {
                m.invoke(dlg, args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private void setSelected(String name, boolean sel) throws Exception {
        JCheckBox cb = checks().get(name);
        GuiActionRunner.execute(() -> {
            cb.setSelected(sel);
            return null;
        });
    }

    private static Set<String> abc() {
        return new LinkedHashSet<>(Arrays.asList("A", "B", "C"));
    }

    /** 初期 hidden が checkbox の非選択状態に反映される。 */
    @Test
    public void initiallyHiddenAreUnchecked() throws Exception {
        create(abc(), Collections.singleton("B"));
        Map<String, JCheckBox> checks = checks();
        assertTrue("A は表示 → チェック", GuiActionRunner.execute(checks.get("A")::isSelected));
        assertFalse("B は hidden → 非チェック", GuiActionRunner.execute(checks.get("B")::isSelected));
        assertTrue("C は表示 → チェック", GuiActionRunner.execute(checks.get("C")::isSelected));
    }

    /** 全チェックのまま commit → 隠す対象は空集合。 */
    @Test
    public void commitWithAllCheckedHidesNothing() throws Exception {
        create(abc(), Collections.emptySet());
        invokeOnEdt("commit", new Class<?>[0]);
        assertTrue("全選択なら hidden は空", result().isEmpty());
    }

    /** 一部のチェックを外して commit → 外したものだけが hidden に入る。 */
    @Test
    public void commitReturnsUncheckedAsHidden() throws Exception {
        create(abc(), Collections.emptySet());
        setSelected("A", false);
        setSelected("C", false);
        invokeOnEdt("commit", new Class<?>[0]);
        Set<String> hidden = result();
        assertEquals("外した A,C のみが hidden", new LinkedHashSet<>(Arrays.asList("A", "C")), hidden);
        assertFalse("チェック済みの B は hidden に含まない", hidden.contains("B"));
    }

    /** setAll(false) → commit で全 participant が hidden。 */
    @Test
    public void clearAllThenCommitHidesEverything() throws Exception {
        create(abc(), Collections.emptySet());
        invokeOnEdt("setAll", new Class<?>[] {boolean.class}, false);
        invokeOnEdt("commit", new Class<?>[0]);
        assertEquals("Clear All 後は全員 hidden", 3, result().size());
    }

    /** setAll(true) は初期 hidden を打ち消し、commit で hidden 空。 */
    @Test
    public void selectAllOverridesInitialHidden() throws Exception {
        create(abc(), new LinkedHashSet<>(Arrays.asList("A", "B")));
        invokeOnEdt("setAll", new Class<?>[] {boolean.class}, true);
        invokeOnEdt("commit", new Class<?>[0]);
        assertTrue("Select All 後は hidden 空", result().isEmpty());
    }

    /** Cancel ボタンは result を null のままにする (フィルタ適用しない契約)。 */
    @Test
    public void cancelLeavesResultNull() throws Exception {
        create(abc(), Collections.emptySet());
        assertNull("commit 前 / cancel 時は result は null", result());
        JButton cancel = findButton(dlg, "Cancel");
        GuiActionRunner.execute(() -> {
            cancel.doClick();
            return null;
        });
        assertNull("Cancel 後も result は null のまま", result());
    }

    private static JButton findButton(Container root, String label) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && label.equals(((JButton) c).getText())) {
                return (JButton) c;
            }
            if (c instanceof Container) {
                JButton found = findButton((Container) c, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
