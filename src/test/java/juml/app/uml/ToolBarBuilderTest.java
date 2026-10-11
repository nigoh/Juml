// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import org.junit.After;
import org.junit.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import java.awt.Component;

import static org.junit.Assert.*;

public class ToolBarBuilderTest {

    /** 言語を切り替えるテストがあるため、毎回既定 (日本語) へ戻す。 */
    @After
    public void resetLanguage() {
        Messages.setLanguage("ja");
    }

    private ToolBarBuilder.Result buildDefault() {
        ToolBarBuilder.Callbacks cb = new ToolBarBuilder.Callbacks();
        cb.chooseProject = () -> {};
        cb.chooseAndExport = () -> {};
        cb.refreshDiagram = () -> {};
        cb.openEntitySearch = () -> {};
        cb.selectDiagramKind = k -> {};
        return new ToolBarBuilder(DiagramKind.CLASS, cb).build();
    }

    /** ポップアップに実際に並んだメニュー項目の数。 */
    private static int menuItemCount(JPopupMenu popup) {
        int n = 0;
        for (Component c : popup.getComponents()) {
            if (c instanceof JMenuItem) {
                n++;
            }
        }
        return n;
    }

    /**
     * メソッド系図種 (SEQUENCE/ACTIVITY/CALLGRAPH) とレイアウトの画面/実寸を除く全図種が
     * 図種ドロップダウンの一覧に並ぶこと。これらは各図タブ上部の切替バーへ一本化したため、
     * 一覧には出さない (LAYOUT は入口として残す)。
     */
    @Test
    public void build_createsPopupItemForEveryNonMethodDiagramKind() {
        ToolBarBuilder.Result r = buildDefault();
        for (DiagramKind k : DiagramKind.values()) {
            if (ToolBarBuilder.DIAGRAMS_METHOD.contains(k)
                    || ToolBarBuilder.LAYOUT_VARIANT_HIDDEN.contains(k)) {
                assertNull("In-bar-only kind " + k + " should not appear in the dropdown",
                        r.diagramKindChooser.itemFor(k));
            } else {
                assertNotNull("Missing dropdown item for " + k,
                        r.diagramKindChooser.itemFor(k));
            }
        }
        assertEquals(DiagramKind.values().length - ToolBarBuilder.DIAGRAMS_METHOD.size()
                        - ToolBarBuilder.LAYOUT_VARIANT_HIDDEN.size(),
                menuItemCount(r.diagramKindChooser.popup()));
    }

    /** メソッド系図種は図種ドロップダウンの一覧に出ないこと。 */
    @Test
    public void build_omitsMethodKindsFromPopup() {
        ToolBarBuilder.Result r = buildDefault();
        assertNull(r.diagramKindChooser.itemFor(DiagramKind.SEQUENCE));
        assertNull(r.diagramKindChooser.itemFor(DiagramKind.ACTIVITY));
        assertNull(r.diagramKindChooser.itemFor(DiagramKind.CALLGRAPH));
    }

    /** ボタンのラベルは初期図種を示すこと (一覧を開かなくても現在の図種が分かる)。 */
    @Test
    public void build_buttonLabelShowsInitialKind() {
        ToolBarBuilder.Result r = buildDefault();
        String label = r.diagramKindChooser.component().getText();
        assertTrue("Button label should show the initial kind but was: " + label,
                label.contains(ToolBarBuilder.toolbarLabel(DiagramKind.CLASS)));
    }

    /** ボタンのラベルは選ばれていない図種を示さないこと。 */
    @Test
    public void build_buttonLabelOmitsNonInitialKind() {
        ToolBarBuilder.Result r = buildDefault();
        String label = r.diagramKindChooser.component().getText();
        assertFalse("Button label should not show PACKAGE but was: " + label,
                label.contains(ToolBarBuilder.toolbarLabel(DiagramKind.PACKAGE)));
    }

    @Test
    public void build_toolBarPanelIsNotNull() {
        ToolBarBuilder.Result r = buildDefault();
        assertNotNull(r.toolBarPanel);
    }

    /**
     * 上部ツールバーは 1 段だけであること。以前はアクション行の下に図種切替行を積んで
     * いたが、メニューバーと合わせて 3 段になり縦の作業領域を圧迫していたため、
     * 図種切替はアクション行末尾のドロップダウンへ畳んだ。
     */
    @Test
    public void build_toolBarPanelHasNoDiagramKindRow() {
        ToolBarBuilder.Result r = buildDefault();
        assertTrue("Tool bar should be a single JToolBar row but was: "
                + r.toolBarPanel.getClass().getName(), r.toolBarPanel instanceof JToolBar);
        for (Component c : ((JToolBar) r.toolBarPanel).getComponents()) {
            assertFalse("A nested tool bar row should not exist any more",
                    c instanceof JToolBar);
        }
    }

    /** 図種ドロップダウンのボタンがアクション行に載っていること。 */
    @Test
    public void build_toolBarContainsDiagramKindButton() {
        ToolBarBuilder.Result r = buildDefault();
        boolean found = false;
        for (Component c : ((JToolBar) r.toolBarPanel).getComponents()) {
            if (c == r.diagramKindChooser.component()) {
                found = true;
            }
        }
        assertTrue("Diagram kind dropdown should sit on the action tool bar", found);
    }

    /**
     * カテゴリ区切りは「実際に項目が並んだカテゴリ」の間にだけ入ること。メソッド系
     * カテゴリは一覧から全滅するので、素朴にカテゴリごとへ区切りを入れると空の区切りが
     * 残る。先頭/末尾の区切りと連続した区切りが無いことで検証する。
     */
    @Test
    public void build_popupHasNoStraySeparators() {
        ToolBarBuilder.Result r = buildDefault();
        Component[] cs = r.diagramKindChooser.popup().getComponents();
        assertTrue("Popup should not be empty", cs.length > 0);
        assertTrue("Popup should not start with a separator", cs[0] instanceof JMenuItem);
        assertTrue("Popup should not end with a separator",
                cs[cs.length - 1] instanceof JMenuItem);
        for (int i = 1; i < cs.length; i++) {
            assertFalse("Two separators in a row at index " + i,
                    !(cs[i] instanceof JMenuItem) && !(cs[i - 1] instanceof JMenuItem));
        }
    }

    @Test
    public void diagramsMethod_containsSequenceActivityCallgraph() {
        assertTrue(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.SEQUENCE));
        assertTrue(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.ACTIVITY));
        assertTrue(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.CALLGRAPH));
        assertFalse(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.CLASS));
    }

    /**
     * すべての図種で短く一貫したトグルラベルが付くこと。
     * 以前は NAVIGATION / MODULE だけ {@code switch} の case 漏れで
     * {@code getDisplayName()} の長いラベル ("Navigation Graph" / "Module Diagram")
     * になり、他のボタン ("Class" 等) と不揃いだった。
     */
    @Test
    public void toolbarLabel_isNonEmptyForEveryKind() {
        for (DiagramKind k : DiagramKind.values()) {
            String label = ToolBarBuilder.toolbarLabel(k);
            assertNotNull("label for " + k, label);
            assertFalse(k + " label should not be empty", label.isEmpty());
        }
    }

    /**
     * NAVIGATION / MODULE は以前 {@code switch} の case 漏れで
     * {@code getDisplayName()} の長いラベル ("Navigation Graph" / "Module Diagram")
     * になり、他のボタン ("Class" 等) と不揃いだった。短いラベルを付ける。
     *
     * <p>ラベルは i18n される (既定は日本語) ため、特定の英単語ではなく
     * 「短いラベル ≠ 長い表示名」かつ非空であることで検証する。</p>
     */
    @Test
    public void toolbarLabel_navigationAndModuleAreShort() {
        for (DiagramKind k : new DiagramKind[] {DiagramKind.NAVIGATION, DiagramKind.MODULE}) {
            String shortLabel = ToolBarBuilder.toolbarLabel(k);
            assertFalse(k + " short label should not be empty", shortLabel.isEmpty());
            assertNotEquals(k + " short label should differ from the long display name",
                    k.getDisplayName(), shortLabel);
        }
    }

    /** 図種の表示名・短ラベルが言語に追従すること。 */
    @Test
    public void diagramLabels_followLanguage() {
        Messages.setLanguage("en");
        assertEquals("Class", ToolBarBuilder.toolbarLabel(DiagramKind.CLASS));
        assertEquals("Navigation", ToolBarBuilder.toolbarLabel(DiagramKind.NAVIGATION));
        assertEquals("Class Diagram", DiagramKind.CLASS.getDisplayName());

        Messages.setLanguage("ja");
        assertEquals("クラス", ToolBarBuilder.toolbarLabel(DiagramKind.CLASS));
        assertEquals("クラス図", DiagramKind.CLASS.getDisplayName());
    }

    /**
     * ツールチップ補足は先頭の区切りスペースを保持すること。素人が図種を選びやすいよう、
     * 全図種に「何の役に立つ図か」の説明を付けたので、どの図種でも非空で返る。
     */
    @Test
    public void tooltipExtra_keepsLeadingSpaceForAllKinds() {
        for (String lang : new String[] {"en", "ja"}) {
            Messages.setLanguage(lang);
            for (DiagramKind k : DiagramKind.values()) {
                String tip = ToolBarBuilder.tooltipExtra(k);
                assertFalse(lang + ": " + k + " のツールチップ補足が空", tip.isEmpty());
                assertTrue(lang + ": " + k + " は先頭スペース区切り",
                        tip.startsWith(" "));
            }
        }
    }

    /** プリセット表示名が言語に追従すること。 */
    @Test
    public void presetDisplayName_followsLanguage() {
        Messages.setLanguage("en");
        assertEquals("Minimal", DiagramPreset.MINIMAL.getDisplayName());
        Messages.setLanguage("ja");
        assertEquals("最小限", DiagramPreset.MINIMAL.getDisplayName());
    }
}
