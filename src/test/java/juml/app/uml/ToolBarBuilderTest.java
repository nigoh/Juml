// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;
import org.junit.After;
import org.junit.Test;

import javax.swing.JToggleButton;

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

    /**
     * メソッド系図種 (SEQUENCE/ACTIVITY/CALLGRAPH) とレイアウトの画面/実寸を除く全図種に
     * トグルが作られること。これらは各図タブ上部の切替バーへ一本化したため、ツールバーには
     * 出さない (LAYOUT は入口として残す)。
     */
    @Test
    public void build_createsToggleForEveryNonMethodDiagramKind() {
        ToolBarBuilder.Result r = buildDefault();
        for (DiagramKind k : DiagramKind.values()) {
            if (ToolBarBuilder.DIAGRAMS_METHOD.contains(k)
                    || ToolBarBuilder.LAYOUT_VARIANT_HIDDEN.contains(k)) {
                assertNull("In-bar-only kind " + k + " should not appear in the toolbar",
                        r.diagramToggles.get(k));
            } else {
                assertNotNull("Missing toggle for " + k, r.diagramToggles.get(k));
            }
        }
        assertEquals(DiagramKind.values().length - ToolBarBuilder.DIAGRAMS_METHOD.size()
                        - ToolBarBuilder.LAYOUT_VARIANT_HIDDEN.size(),
                r.diagramToggles.size());
    }

    /** メソッド系図種はツールバーのトグルとして生成されないこと。 */
    @Test
    public void build_omitsMethodKindToggles() {
        ToolBarBuilder.Result r = buildDefault();
        assertNull(r.diagramToggles.get(DiagramKind.SEQUENCE));
        assertNull(r.diagramToggles.get(DiagramKind.ACTIVITY));
        assertNull(r.diagramToggles.get(DiagramKind.CALLGRAPH));
    }

    @Test
    public void build_initialKindIsSelected() {
        ToolBarBuilder.Result r = buildDefault();
        JToggleButton classBtn = r.diagramToggles.get(DiagramKind.CLASS);
        assertTrue("CLASS button should be selected initially", classBtn.isSelected());
    }

    @Test
    public void build_nonInitialKindIsNotSelected() {
        ToolBarBuilder.Result r = buildDefault();
        JToggleButton pkgBtn = r.diagramToggles.get(DiagramKind.PACKAGE);
        assertFalse("PACKAGE button should not be selected initially", pkgBtn.isSelected());
    }

    @Test
    public void build_toolBarPanelIsNotNull() {
        ToolBarBuilder.Result r = buildDefault();
        assertNotNull(r.toolBarPanel);
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
