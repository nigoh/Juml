// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JSpinner;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramScopeDialog} の OK 確定ロジック {@code buildScope()} のテスト。
 *
 * <p>{@code DialogKeyboardTest} は Escape/既定ボタンのみ。ここでは UI ウィジェット →
 * {@link DiagramScope} 写像の中核分岐を検証する: (1) 関係種別を全 off にすると
 * {@code allOf} へ復帰する救済、(2) 一部選択はその集合を保つ、(3) maxClasses spinner の反映、
 * (4) 選択した include パッケージの反映。</p>
 *
 * <p>不正 regex 経路 ({@code buildScope} が {@code JOptionPane.showMessageDialog} で
 * モーダル表示し null を返す) は、テストから呼ぶと EDT がブロックするため本テストでは
 * 扱わない (フォローアップ: regex 検証を非モーダルな純メソッドに切り出せば単体化可能)。</p>
 *
 * <p>{@link javax.swing.JDialog} 生成に display が要るためヘッドレスでは {@code Assume} で
 * skip ({@code DialogKeyboardTest} と同方針)。</p>
 */
public class DiagramScopeDialogResultTest {

    private DiagramScopeDialog dlg;

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

    private DiagramScopeDialog create(List<String> packages) {
        dlg = GuiActionRunner.execute(() ->
                new DiagramScopeDialog(null, packages, Collections.emptyList(), null));
        return dlg;
    }

    private <T> T field(String name) throws Exception {
        Field f = DiagramScopeDialog.class.getDeclaredField(name);
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        T v = (T) f.get(dlg);
        return v;
    }

    private DiagramScope buildScope() throws Exception {
        Method m = DiagramScopeDialog.class.getDeclaredMethod("buildScope");
        m.setAccessible(true);
        return GuiActionRunner.execute(() -> {
            try {
                return (DiagramScope) m.invoke(dlg);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void setChecks(boolean inh, boolean impl, boolean usage) throws Exception {
        JCheckBox inheritance = field("inheritanceCheckbox");
        JCheckBox implementation = field("implementationCheckbox");
        JCheckBox use = field("usageCheckbox");
        GuiActionRunner.execute(() -> {
            inheritance.setSelected(inh);
            implementation.setSelected(impl);
            use.setSelected(usage);
            return null;
        });
    }

    /** 関係種別を全部 off にすると、意味が無いので all on へ救済される。 */
    @Test
    public void allRelationsOffFallsBackToAll() throws Exception {
        create(Collections.emptyList());
        setChecks(false, false, false);
        DiagramScope scope = buildScope();
        assertEquals("全 off は allOf に復帰", EnumSet.allOf(RelationKind.class),
                scope.getRelationKinds());
    }

    /** 一部だけ選択した関係種別はその集合が保たれる。 */
    @Test
    public void selectedRelationsArePreserved() throws Exception {
        create(Collections.emptyList());
        setChecks(true, false, false);
        DiagramScope scope = buildScope();
        assertEquals("inheritance のみ選択", EnumSet.of(RelationKind.INHERITANCE),
                scope.getRelationKinds());
    }

    /** maxClasses spinner の値が scope に反映される。 */
    @Test
    public void maxClassesSpinnerReflected() throws Exception {
        create(Collections.emptyList());
        JSpinner spinner = field("maxClassesSpinner");
        GuiActionRunner.execute(() -> {
            spinner.setValue(250);
            return null;
        });
        assertEquals("maxClasses が反映される", 250, buildScope().getMaxClasses());
    }

    /** packageList で選択した include パッケージが scope に反映される。 */
    @Test
    public void selectedIncludePackagesReflected() throws Exception {
        create(Arrays.asList("com.a", "com.b", "com.c"));
        JList<String> list = field("packageList");
        GuiActionRunner.execute(() -> {
            list.setSelectedIndices(new int[] {0, 2}); // com.a, com.c
            return null;
        });
        DiagramScope scope = buildScope();
        assertTrue("com.a が include に入る", scope.getIncludedPackages().contains("com.a"));
        assertTrue("com.c が include に入る", scope.getIncludedPackages().contains("com.c"));
        assertTrue("未選択の com.b は入らない", !scope.getIncludedPackages().contains("com.b"));
    }
}
