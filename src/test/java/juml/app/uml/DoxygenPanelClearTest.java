// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.doxygen.DoxCompound;
import juml.core.formats.doxygen.DoxModel;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/**
 * プロジェクト切替で Doxygen タブが前プロジェクトの内容を残さないことの回帰テスト。
 *
 * <p>{@link DoxygenResultCache#clear()} は結果を捨てて ({@code getModel() == null})
 * リスナーへ通知する。以前 {@link DoxygenPanel} はこの null を素通りさせていたため、
 * <b>別プロジェクトを開いてもツリーに前プロジェクトの型が並んだまま</b>だった
 * (兄弟タブの Groups/TODO は当初から空へ戻していた)。</p>
 */
public class DoxygenPanelClearTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing 構築が失敗しうるためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    /** DoxygenResultCache の private publishResult(DoxModel) を呼び出す。 */
    private static void publishResult(DoxygenResultCache cache, DoxModel model) throws Exception {
        Method m = DoxygenResultCache.class.getDeclaredMethod("publishResult", DoxModel.class);
        m.setAccessible(true);
        m.invoke(cache, model);
    }

    private static DefaultMutableTreeNode rootNode(DoxygenPanel panel) throws Exception {
        Field f = DoxygenPanel.class.getDeclaredField("rootNode");
        f.setAccessible(true);
        return (DefaultMutableTreeNode) f.get(panel);
    }

    private static DoxModel modelWithTwoTypes() {
        DoxModel model = new DoxModel();
        model.addCompound(new DoxCompound("c1", "class", "com.example.Order", "brief 1"));
        model.addCompound(new DoxCompound("c2", "class", "com.example.Customer", "brief 2"));
        return model;
    }

    @Test
    public void clearingTheCacheEmptiesTheTree() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenPanel panel = GuiActionRunner.execute(
                () -> new DoxygenPanel(projectCache, cache));

        publishResult(cache, modelWithTwoTypes());
        assertEquals("前提: 解析結果がツリーに並ぶこと", 2, rootNode(panel).getChildCount());

        // プロジェクト切替相当。
        GuiActionRunner.execute(() -> {
            cache.clear();
            return null;
        });

        assertEquals("切替後は前プロジェクトの型が残らないこと",
                0, rootNode(panel).getChildCount());
    }

    @Test
    public void reRunAfterClearRepopulatesTheTree() throws Exception {
        // 非退行: 空へ戻したあとも次の解析結果はきちんと流し込まれること。
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenPanel panel = GuiActionRunner.execute(
                () -> new DoxygenPanel(projectCache, cache));

        publishResult(cache, modelWithTwoTypes());
        GuiActionRunner.execute(() -> {
            cache.clear();
            return null;
        });

        DoxModel next = new DoxModel();
        next.addCompound(new DoxCompound("c9", "class", "com.other.Only", "brief"));
        publishResult(cache, next);

        assertEquals(1, rootNode(panel).getChildCount());
    }
}
