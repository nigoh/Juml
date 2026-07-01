// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.doxygen.DoxModel;
import juml.core.formats.doxygen.DoxXrefItem;
import juml.util.Messages;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.table.DefaultTableModel;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DoxygenTodoPanel#rebuild()} のフィルタロジックを検証する。
 *
 * <p>{@link DoxygenResultCache#publishResult} をリフレクションで呼んで結果を注入し、
 * {@link JComboBox} のフィルタ操作で {@code rebuild()} を駆動する。
 * Swing コンポーネントの構築に UIManager が必要なため headless guard を入れている。</p>
 */
public class DoxygenTodoPanelTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では JComboBox / JTable 構築が失敗する場合があるためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** DoxygenResultCache の private publishResult(DoxModel) を呼び出す。 */
    private static void publishResult(DoxygenResultCache cache, DoxModel model) throws Exception {
        Method m = DoxygenResultCache.class.getDeclaredMethod("publishResult", DoxModel.class);
        m.setAccessible(true);
        m.invoke(cache, model);
    }

    /** DoxygenTodoPanel の private field "tableModel" を取得する。 */
    private static DefaultTableModel getTableModel(DoxygenTodoPanel panel) throws Exception {
        Field f = DoxygenTodoPanel.class.getDeclaredField("tableModel");
        f.setAccessible(true);
        return (DefaultTableModel) f.get(panel);
    }

    /** DoxygenTodoPanel の private field "filter" (JComboBox) を取得する。 */
    @SuppressWarnings("unchecked")
    private static JComboBox<String> getFilter(DoxygenTodoPanel panel) throws Exception {
        Field f = DoxygenTodoPanel.class.getDeclaredField("filter");
        f.setAccessible(true);
        return (JComboBox<String>) f.get(panel);
    }

    /** テスト用の DoxModel (TODO 2件 + BUG 1件) を生成する。 */
    private static DoxModel makeModel() {
        DoxModel model = new DoxModel();
        model.addXrefItem(new DoxXrefItem(DoxXrefItem.Kind.TODO, "com.Foo.bar", "todo item 1"));
        model.addXrefItem(new DoxXrefItem(DoxXrefItem.Kind.TODO, "com.Foo.baz", "todo item 2"));
        model.addXrefItem(new DoxXrefItem(DoxXrefItem.Kind.BUG, "com.Bar.qux", "bug item 1"));
        return model;
    }

    // -------------------------------------------------------------------------
    // テスト: フィルタ「all」で全行表示
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_filterAll_showsAllRows() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        publishResult(cache, makeModel());

        DefaultTableModel tableModel = getTableModel(panel);
        JComboBox<String> filter = getFilter(panel);

        // "all" フィルタを選択する
        String allLabel = Messages.get("doxygen.todo.filter.all");
        GuiActionRunner.execute(() -> filter.setSelectedItem(allLabel));

        assertEquals("フィルタ「all」では全 3 件が表示されること", 3, tableModel.getRowCount());
    }

    // -------------------------------------------------------------------------
    // テスト: 種別フィルタで絞り込み
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_filterTodo_showsOnlyTodoRows() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        publishResult(cache, makeModel());

        DefaultTableModel tableModel = getTableModel(panel);
        JComboBox<String> filter = getFilter(panel);

        String todoLabel = Messages.get("doxygen.todo.filter.todo");
        GuiActionRunner.execute(() -> filter.setSelectedItem(todoLabel));

        assertEquals("フィルタ「todo」では TODO 2 件のみ表示されること", 2, tableModel.getRowCount());
    }

    // -------------------------------------------------------------------------
    // テスト: BUG フィルタで BUG 1件のみ
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_filterBug_showsOnlyBugRows() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        publishResult(cache, makeModel());

        DefaultTableModel tableModel = getTableModel(panel);
        JComboBox<String> filter = getFilter(panel);

        String bugLabel = Messages.get("doxygen.todo.filter.bug");
        GuiActionRunner.execute(() -> filter.setSelectedItem(bugLabel));

        assertEquals("フィルタ「bug」では BUG 1 件のみ表示されること", 1, tableModel.getRowCount());
    }

    // -------------------------------------------------------------------------
    // テスト: 空モデルで行数 0
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_emptyModel_showsZeroRows() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        publishResult(cache, new DoxModel()); // 空モデル

        DefaultTableModel tableModel = getTableModel(panel);
        assertEquals("空モデルでは行数が 0 であること", 0, tableModel.getRowCount());
    }

    // -------------------------------------------------------------------------
    // テスト: モデルなし (null) の初期状態で行数 0
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_noModel_showsZeroRows() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        // publishResult を呼ばない (キャッシュに結果なし)
        DefaultTableModel tableModel = getTableModel(panel);
        assertEquals("モデルなし時は行数が 0 であること", 0, tableModel.getRowCount());
    }

    // -------------------------------------------------------------------------
    // テスト: フィルタ後に「all」へ戻すと全件表示に戻る
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_switchFilterBackToAll_restoresAllRows() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        publishResult(cache, makeModel());

        DefaultTableModel tableModel = getTableModel(panel);
        JComboBox<String> filter = getFilter(panel);

        String todoLabel = Messages.get("doxygen.todo.filter.todo");
        String allLabel = Messages.get("doxygen.todo.filter.all");

        GuiActionRunner.execute(() -> filter.setSelectedItem(todoLabel));
        assertEquals("todo フィルタで 2 件", 2, tableModel.getRowCount());

        GuiActionRunner.execute(() -> filter.setSelectedItem(allLabel));
        assertEquals("all フィルタに戻すと全 3 件", 3, tableModel.getRowCount());
    }

    // -------------------------------------------------------------------------
    // テスト: DEPRECATED フィルタで 0 件 (モデルに DEPRECATED なし)
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_filterDeprecated_showsZeroRowsWhenNoneExist() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        publishResult(cache, makeModel()); // DEPRECATED はない

        DefaultTableModel tableModel = getTableModel(panel);
        JComboBox<String> filter = getFilter(panel);

        String depLabel = Messages.get("doxygen.todo.filter.deprecated");
        GuiActionRunner.execute(() -> filter.setSelectedItem(depLabel));

        assertEquals("DEPRECATED が存在しないとき行数は 0 であること", 0, tableModel.getRowCount());
    }

    // -------------------------------------------------------------------------
    // テスト: テーブル列数が 3 (種別/箇所/内容)
    // -------------------------------------------------------------------------

    @Test
    public void tableModel_hasThreeColumns() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        DefaultTableModel tableModel = getTableModel(panel);
        assertEquals("テーブルは 3 列 (種別/箇所/内容) であること", 3, tableModel.getColumnCount());
    }

    // -------------------------------------------------------------------------
    // テスト: rebuild 後のセル内容が正しい
    // -------------------------------------------------------------------------

    @Test
    public void rebuild_allFilter_firstRowHasTodoKind() throws Exception {
        DoxygenResultCache cache = new DoxygenResultCache();
        ProjectAnalysisCache projectCache = new ProjectAnalysisCache();
        DoxygenTodoPanel panel = GuiActionRunner.execute(
                () -> new DoxygenTodoPanel(projectCache, cache));

        DoxModel model = new DoxModel();
        model.addXrefItem(new DoxXrefItem(DoxXrefItem.Kind.TODO, "loc.Foo", "fix me"));
        publishResult(cache, model);

        DefaultTableModel tableModel = getTableModel(panel);
        assertTrue("1 件のデータが表示されること", tableModel.getRowCount() >= 1);
        Object kindCell = tableModel.getValueAt(0, 0);
        String expectedTodoLabel = Messages.get("doxygen.todo.filter.todo");
        assertEquals("1 行目の種別列が todo ラベルであること", expectedTodoLabel, kindCell);
    }
}
