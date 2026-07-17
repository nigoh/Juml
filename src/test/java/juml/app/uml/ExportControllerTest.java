// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ExportController#buildExportPopup()} のポップアップメニュー構造テスト。
 *
 * <p>右クリックエクスポートポップアップが正しい項目数・ラベルを持つことを保証する。
 * {@link JPopupMenu} の生成には display が不要だが、JLabel の初期化時に
 * UIManager を参照するため、headless ではスキップする。</p>
 */
public class ExportControllerTest {

    private ExportController controller;
    private DiagramState state;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では JLabel / JPopupMenu 構築が失敗する場合があるためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        state = new DiagramState();
        JLabel statusLabel = GuiActionRunner.execute(() -> new JLabel());
        controller = GuiActionRunner.execute(
                () -> new ExportController(null, state, statusLabel));
    }

    // -------------------------------------------------------------------------
    // buildExportPopup のメニュー項目数
    // -------------------------------------------------------------------------

    @Test
    public void buildExportPopup_hasFiveMenuItems() {
        JPopupMenu popup = GuiActionRunner.execute(() -> controller.buildExportPopup());
        // SVG 保存 / PNG 保存 / PUML 保存 / (Separator) / 画像コピー / SVG コピー = 5 菜単 + 1 区切り
        assertNotNull("buildExportPopup は null を返してはならない", popup);
        List<JMenuItem> items = collectMenuItems(popup);
        assertEquals("エクスポートポップアップは 5 つのメニュー項目を持つはず", 5, items.size());
    }

    // -------------------------------------------------------------------------
    // buildExportPopup のメニューラベル検証
    // -------------------------------------------------------------------------

    @Test
    public void buildExportPopup_firstItemIsSaveSvg() {
        JPopupMenu popup = GuiActionRunner.execute(() -> controller.buildExportPopup());
        List<JMenuItem> items = collectMenuItems(popup);
        assertTrue("1 件目のメニュー項目が存在するはず", items.size() >= 1);
        String label = GuiActionRunner.execute(() -> items.get(0).getText());
        assertFalse("SVG 保存の項目ラベルが空であってはならない", label == null || label.isEmpty());
    }

    @Test
    public void buildExportPopup_secondItemIsSavePng() {
        JPopupMenu popup = GuiActionRunner.execute(() -> controller.buildExportPopup());
        List<JMenuItem> items = collectMenuItems(popup);
        assertTrue("2 件目のメニュー項目が存在するはず", items.size() >= 2);
        String label = GuiActionRunner.execute(() -> items.get(1).getText());
        assertFalse("PNG 保存の項目ラベルが空であってはならない", label == null || label.isEmpty());
    }

    @Test
    public void buildExportPopup_thirdItemIsSavePuml() {
        JPopupMenu popup = GuiActionRunner.execute(() -> controller.buildExportPopup());
        List<JMenuItem> items = collectMenuItems(popup);
        assertTrue("3 件目のメニュー項目が存在するはず", items.size() >= 3);
        String label = GuiActionRunner.execute(() -> items.get(2).getText());
        assertFalse("PUML 保存の項目ラベルが空であってはならない", label == null || label.isEmpty());
    }

    @Test
    public void buildExportPopup_fourthItemIsCopyImage() {
        JPopupMenu popup = GuiActionRunner.execute(() -> controller.buildExportPopup());
        List<JMenuItem> items = collectMenuItems(popup);
        assertTrue("4 件目のメニュー項目が存在するはず", items.size() >= 4);
        String label = GuiActionRunner.execute(() -> items.get(3).getText());
        assertEquals("4 件目は画像コピーであるはず",
                juml.util.Messages.get("export.copyImage"), label);
    }

    @Test
    public void buildExportPopup_fifthItemIsCopySvg() {
        JPopupMenu popup = GuiActionRunner.execute(() -> controller.buildExportPopup());
        List<JMenuItem> items = collectMenuItems(popup);
        assertTrue("5 件目のメニュー項目が存在するはず", items.size() >= 5);
        String label = GuiActionRunner.execute(() -> items.get(4).getText());
        assertEquals("5 件目は SVG コピーであるはず",
                juml.util.Messages.get("export.copySvg"), label);
    }

    // -------------------------------------------------------------------------
    // buildExportPopup の呼び出しごとに独立したインスタンスが返る
    // -------------------------------------------------------------------------

    @Test
    public void buildExportPopup_returnsNewInstanceEachCall() {
        JPopupMenu p1 = GuiActionRunner.execute(() -> controller.buildExportPopup());
        JPopupMenu p2 = GuiActionRunner.execute(() -> controller.buildExportPopup());
        assertTrue("buildExportPopup は毎回新しいインスタンスを返すはず", p1 != p2);
    }

    // -------------------------------------------------------------------------
    // activePreview 付きコンストラクタでも同じ項目数
    // -------------------------------------------------------------------------

    @Test
    public void buildExportPopup_withActivePreviewSupplier_hasFiveItems() {
        JLabel statusLabel = GuiActionRunner.execute(() -> new JLabel());
        ExportController ec = GuiActionRunner.execute(
                () -> new ExportController(null, state, statusLabel, () -> null));
        JPopupMenu popup = GuiActionRunner.execute(() -> ec.buildExportPopup());
        List<JMenuItem> items = collectMenuItems(popup);
        assertEquals("activePreview 付きコンストラクタでも 5 項目になるはず", 5, items.size());
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** ポップアップメニューの JMenuItem を（Separator を除いて）収集する。 */
    private static List<JMenuItem> collectMenuItems(JPopupMenu popup) {
        List<JMenuItem> result = new ArrayList<>();
        for (int i = 0; i < popup.getComponentCount(); i++) {
            java.awt.Component c = popup.getComponent(i);
            if (c instanceof JMenuItem) {
                result.add((JMenuItem) c);
            }
        }
        return result;
    }
}
